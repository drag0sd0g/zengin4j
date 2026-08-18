package io.zengin4j.iso20022.pain001;

import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.xml.XmlElement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * A {@code pain.001.001.03} customer credit transfer initiation.
 *
 * <p>A hand-written subset, not a generated binding. The profile uses a small
 * part of a large message definition, the part it uses is stable because the
 * version is pinned (R-I3), and generating bindings would require shipping
 * schemas this repository does not redistribute — see
 * {@code docs/adr/0031-hand-written-iso20022-xml.md}.
 *
 * <p>What that costs: elements outside the subset are not modelled. They are
 * not lost, though — {@link #toXml()} and the reader work on
 * {@link XmlElement}, so a caller who needs {@code Purp} or a postal address
 * can reach it through the tree.
 *
 * @param groupHeader {@code GrpHdr}
 * @param payments    {@code PmtInf}, in order; at least one in a valid message
 * @since 0.5.0
 */
public record Pain001Document(GroupHeader groupHeader, List<PaymentInstruction> payments) {

    /** The root element of any ISO 20022 message document. */
    public static final String ROOT = "Document";

    /** The element that names this particular message. */
    public static final String ELEMENT = "CstmrCdtTrfInitn";

    /** What this document is (R-I3). */
    public static final MessageId MESSAGE_ID = MessageId.PAIN_001_001_03;

    /**
     * Validates the document.
     *
     * @throws NullPointerException if either component is null
     */
    public Pain001Document {
        Objects.requireNonNull(groupHeader, "groupHeader");
        payments = List.copyOf(Objects.requireNonNull(payments, "payments"));
    }

    /**
     * The number of payments across every instruction.
     *
     * @return the transaction count
     */
    public int numberOfTransactions() {
        return payments.stream().mapToInt(PaymentInstruction::numberOfTransactions).sum();
    }

    /**
     * The sum of every payment.
     *
     * @return the control sum
     */
    public BigDecimal controlSum() {
        return payments.stream()
                .map(PaymentInstruction::controlSum)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Every transaction in the document, in order.
     *
     * @return the transactions
     */
    public List<CreditTransferTransaction> transactions() {
        return payments.stream().flatMap(payment -> payment.transactions().stream()).toList();
    }

    /**
     * Reads a document from a parsed message body.
     *
     * @param root the {@code Document} element
     * @return the document
     * @throws IllegalArgumentException if the element is not a
     *                                  {@code CstmrCdtTrfInitn} document
     */
    public static Pain001Document from(XmlElement root) {
        Objects.requireNonNull(root, "root");
        XmlElement initiation = root.child(ELEMENT).orElseThrow(() ->
                new IllegalArgumentException("<" + root.name() + "> does not contain <" + ELEMENT
                        + ">, so it is not a customer credit transfer initiation. Check "
                        + "ZediMessage.messageId() before mapping — this library maps "
                        + MESSAGE_ID + " and nothing else (R-I3)."));
        return new Pain001Document(
                initiation.child(GroupHeader.ELEMENT).map(GroupHeader::from)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "<" + ELEMENT + "> has no <" + GroupHeader.ELEMENT + ">")),
                initiation.childrenNamed(PaymentInstruction.ELEMENT).stream()
                        .map(PaymentInstruction::from)
                        .toList());
    }

    /**
     * Renders the document.
     *
     * @return the {@code Document} element, ready to serialise
     */
    public XmlElement toXml() {
        XmlElement.Builder initiation = XmlElement.element(ELEMENT)
                .child(groupHeader.toXml(numberOfTransactions(), controlSum()));
        payments.forEach(payment -> initiation.child(payment.toXml()));

        return XmlElement.element(ROOT)
                .namespace(MESSAGE_ID.namespace())
                .child(initiation)
                .build();
    }
}
