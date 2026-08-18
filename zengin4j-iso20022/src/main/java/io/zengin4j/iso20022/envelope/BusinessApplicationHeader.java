package io.zengin4j.iso20022.envelope;

import io.zengin4j.iso20022.xml.IsoDateTime;
import io.zengin4j.iso20022.xml.XmlElement;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code head.001} Business Application Header that precedes every message
 * in the profile.
 *
 * <p>The header answers routing questions the message body does not: who sent
 * it, who it is for, what it is, and when it was created. In this profile it is
 * a separate XML document concatenated ahead of the body — see
 * {@link ZediEnvelopeReader} for why that matters more than it sounds.
 *
 * <p><strong>Provisional.</strong> No row of this model has been confirmed
 * against two independent published sources (R-0.1), so like every format
 * descriptor in this release it is {@code verified: false}. The element names
 * are those of {@code head.001.001.01}; which of them the profile requires, and
 * what it expects in {@code Fr} and {@code To} for a corporate sender, is
 * recorded as unsettled in {@code docs/OPEN_QUESTIONS.md}.
 *
 * @param from                        {@code Fr} — the sender's identifier
 * @param to                          {@code To} — the recipient's identifier
 * @param businessMessageIdentifier   {@code BizMsgIdr} — the sender's reference
 *                                    for this message
 * @param messageDefinitionIdentifier {@code MsgDefIdr} — what the body is
 * @param creationDate                {@code CreDt} — when the header was made
 * @since 0.5.0
 */
public record BusinessApplicationHeader(
        String from,
        String to,
        String businessMessageIdentifier,
        MessageId messageDefinitionIdentifier,
        OffsetDateTime creationDate) {

    /** The root element of a {@code head.001} document. */
    public static final String ROOT = "AppHdr";

    /**
     * What an unreadable {@code CreDt} becomes.
     *
     * <p>A real instant rather than {@link OffsetDateTime#MIN}, which renders as
     * a year with nine digits and looks like memory corruption in a log.
     */
    private static final OffsetDateTime EPOCH =
            java.time.Instant.EPOCH.atOffset(java.time.ZoneOffset.UTC);

    /**
     * Validates the header.
     *
     * @throws NullPointerException if any component is null
     */
    public BusinessApplicationHeader {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(businessMessageIdentifier, "businessMessageIdentifier");
        Objects.requireNonNull(messageDefinitionIdentifier, "messageDefinitionIdentifier");
        Objects.requireNonNull(creationDate, "creationDate");
    }

    /**
     * Reads a header from a parsed {@code head.001} document.
     *
     * <p>Absent elements become empty strings rather than a refusal. A header
     * this library cannot fully read is still a header whose body is worth
     * mapping, and the mapping reports what it could not use — refusing the
     * whole file over a missing {@code To} would be the wrong trade in a
     * profile no row of which is confirmed.
     *
     * @param root the {@code AppHdr} element
     * @return the header
     */
    public static BusinessApplicationHeader from(XmlElement root) {
        Objects.requireNonNull(root, "root");
        return new BusinessApplicationHeader(
                identifier(root.at("Fr")),
                identifier(root.at("To")),
                root.textAt("BizMsgIdr").orElse(""),
                root.textAt("MsgDefIdr").map(MessageId::new).orElse(MessageId.PAIN_001_001_03),
                root.textAt("CreDt").flatMap(BusinessApplicationHeader::parseDate)
                        .orElse(EPOCH));
    }

    /**
     * Renders the header as a {@code head.001} document.
     *
     * @return the {@code AppHdr} element, ready to serialise
     */
    public XmlElement toXml() {
        return XmlElement.element(ROOT)
                .namespace(MessageId.HEAD_001_001_01.namespace())
                .childIfPresent(party("Fr", from))
                .childIfPresent(party("To", to))
                .textChild("BizMsgIdr", businessMessageIdentifier)
                .textChild("MsgDefIdr", messageDefinitionIdentifier.value())
                .textChild("CreDt", IsoDateTime.format(creationDate))
                .build();
    }

    /**
     * {@code Fr} and {@code To} nest an identifier several levels down, and the
     * profile may use either the organisation or the financial-institution
     * branch. Both are read; whichever is present wins.
     */
    private static String identifier(Optional<XmlElement> party) {
        return party.flatMap(element -> element.textAt("OrgId/Id/OrgId/Othr/Id")
                        .or(() -> element.textAt("FIId/FinInstnId/Othr/Id"))
                        .or(() -> element.textAt("FIId/FinInstnId/BICFI")))
                .orElse("");
    }

    /**
     * A party element, or nothing.
     *
     * <p>{@code Fr} and {@code To} are both mandatory in {@code head.001}, so an
     * absent one produces a header a schema rejects. That is the intended
     * behaviour: a missing recipient is a fact about the conversion, and writing
     * an empty {@code <Othr/>} to keep the shape would hide it behind something
     * that looks structurally fine and means nothing.
     */
    private static Optional<XmlElement> party(String name, String id) {
        if (id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(XmlElement.element(name)
                .child(XmlElement.element("OrgId")
                        .child(XmlElement.element("Id")
                                .child(XmlElement.element("OrgId")
                                        .child(XmlElement.element("Othr")
                                                .textChild("Id", id)))))
                .build());
    }

    private static Optional<OffsetDateTime> parseDate(String text) {
        try {
            return Optional.of(OffsetDateTime.parse(text));
        } catch (java.time.format.DateTimeParseException notADate) {
            return Optional.empty();
        }
    }
}
