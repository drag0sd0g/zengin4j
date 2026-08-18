package io.zengin4j.iso20022.pain001;

import io.zengin4j.iso20022.xml.IsoDateTime;
import io.zengin4j.iso20022.xml.XmlElement;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code GrpHdr} — what the whole message is, and who sent it.
 *
 * <p>{@code NbOfTxs} and {@code CtrlSum} are not held here. They are counted
 * and summed from the instructions when the document is written, because a
 * header that can disagree with its own contents is a header somebody will
 * eventually have to reconcile — which is the exact failure the Zengin trailer
 * rules (V-301, V-302) exist to catch on the other side.
 *
 * @param messageId        {@code MsgId} — the sender's reference for the message
 * @param creationDateTime {@code CreDtTm}
 * @param initiatingParty  {@code InitgPty} — 委託者
 * @since 0.5.0
 */
public record GroupHeader(
        String messageId,
        OffsetDateTime creationDateTime,
        Party initiatingParty) {

    /** The element name. */
    public static final String ELEMENT = "GrpHdr";

    /** What an unreadable {@code CreDtTm} becomes; see the envelope header. */
    private static final OffsetDateTime EPOCH =
            java.time.Instant.EPOCH.atOffset(java.time.ZoneOffset.UTC);

    /**
     * Validates the header.
     *
     * @throws NullPointerException if any component is null
     */
    public GroupHeader {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(creationDateTime, "creationDateTime");
        Objects.requireNonNull(initiatingParty, "initiatingParty");
    }

    /**
     * Reads a header from its element.
     *
     * @param element the {@code GrpHdr} element
     * @return the header
     */
    public static GroupHeader from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return new GroupHeader(
                element.textAt("MsgId").orElse(""),
                element.textAt("CreDtTm").map(OffsetDateTime::parse).orElse(EPOCH),
                element.at("InitgPty").map(Party::from).orElse(Party.named("")));
    }

    /**
     * What the document <em>claimed</em> its transaction count was.
     *
     * <p>Not held on this record, because writing it is computed. Reading it is
     * a different job: a document whose {@code GrpHdr} disagrees with its own
     * payments is exactly as suspect as a Zengin file whose trailer does, and
     * the conversion cross-checks both.
     *
     * @param element the {@code GrpHdr} element
     * @return the declared count, or empty when absent or not a number
     */
    public static Optional<Long> declaredNumberOfTransactions(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return element.textAt("NbOfTxs").flatMap(GroupHeader::parseLong);
    }

    /**
     * What the document claimed its control sum was.
     *
     * @param element the {@code GrpHdr} element
     * @return the declared sum, or empty when absent or not a number
     */
    public static Optional<BigDecimal> declaredControlSum(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return element.textAt("CtrlSum").flatMap(text -> {
            try {
                return Optional.of(new BigDecimal(text));
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Long> parseLong(String text) {
        try {
            return Optional.of(Long.parseLong(text.trim()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /**
     * Renders the header.
     *
     * @param numberOfTransactions the count, taken from the instructions
     * @param controlSum           the sum, taken from the instructions
     * @return the {@code GrpHdr} element
     */
    public XmlElement toXml(int numberOfTransactions, BigDecimal controlSum) {
        return XmlElement.element(ELEMENT)
                .textChild("MsgId", messageId)
                .textChild("CreDtTm", IsoDateTime.format(creationDateTime))
                .textChild("NbOfTxs", String.valueOf(numberOfTransactions))
                .textChild("CtrlSum", controlSum.stripTrailingZeros().toPlainString())
                .childIfPresent(initiatingParty.toXml("InitgPty"))
                .build();
    }
}
