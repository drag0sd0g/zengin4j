package io.zengin4j.iso20022.pain001;

import io.zengin4j.iso20022.xml.XmlElement;
import java.util.Objects;

/**
 * One {@code CdtTrfTxInf} — a single payment within an instruction.
 *
 * <p>This is the element a Zengin data record becomes, and the place most of
 * the loss happens: a 140-character creditor name in any script has to reach a
 * 30-byte half-width katakana field, and a 35-character {@code EndToEndId} has
 * nowhere to go at all.
 *
 * @param endToEndId   {@code PmtId/EndToEndId} — the reference the debtor and
 *                     creditor agree on, mandatory in the standard
 * @param instructionId {@code PmtId/InstrId} — the debtor's own reference,
 *                     empty when absent
 * @param amount       {@code Amt/InstdAmt}
 * @param creditorAgent {@code CdtrAgt} — the beneficiary's bank and branch
 * @param creditor     {@code Cdtr} — the beneficiary
 * @param creditorAccount {@code CdtrAcct} — the beneficiary's account
 * @param remittance   {@code RmtInf}
 * @since 0.5.0
 */
public record CreditTransferTransaction(
        String endToEndId,
        String instructionId,
        Money amount,
        Agent creditorAgent,
        Party creditor,
        Account creditorAccount,
        RemittanceInformation remittance) {

    /**
     * What the standard requires when there is no reference to supply.
     *
     * <p>{@code EndToEndId} is mandatory, so an absent reference cannot be
     * written as an empty element. {@code NOTPROVIDED} is the value the
     * standard defines for exactly this case, and it is a statement rather than
     * a placeholder: it says the debtor supplied none, which is different from
     * one having been lost.
     */
    public static final String NOT_PROVIDED = "NOTPROVIDED";

    /** The element name. */
    public static final String ELEMENT = "CdtTrfTxInf";

    /**
     * Validates the transaction.
     *
     * @throws NullPointerException if any component is null
     */
    public CreditTransferTransaction {
        Objects.requireNonNull(endToEndId, "endToEndId");
        Objects.requireNonNull(instructionId, "instructionId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(creditorAgent, "creditorAgent");
        Objects.requireNonNull(creditor, "creditor");
        Objects.requireNonNull(creditorAccount, "creditorAccount");
        Objects.requireNonNull(remittance, "remittance");
    }

    /**
     * Reads a transaction from its element.
     *
     * <p>An amount that is absent, is not a number, or is too large to render
     * becomes {@link Money#UNREADABLE} rather than zero. A payment whose amount
     * could not be read is not a payment for nothing, and the mapper reports
     * the difference.
     *
     * @param element the {@code CdtTrfTxInf} element
     * @return the transaction
     */
    public static CreditTransferTransaction from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return new CreditTransferTransaction(
                element.textAt("PmtId/EndToEndId").orElse(""),
                element.textAt("PmtId/InstrId").orElse(""),
                element.at("Amt").flatMap(Money::from).orElse(Money.UNREADABLE),
                element.at("CdtrAgt").map(Agent::from).orElse(new Agent("", "", "")),
                element.at("Cdtr").map(Party::from).orElse(Party.named("")),
                element.at("CdtrAcct").map(Account::from).orElse(new Account("", "")),
                element.at("RmtInf").map(RemittanceInformation::from)
                        .orElse(RemittanceInformation.NONE));
    }

    /**
     * Renders the transaction, in the order the message definition sequences it.
     *
     * @return the {@code CdtTrfTxInf} element
     */
    public XmlElement toXml() {
        XmlElement.Builder paymentId = XmlElement.element("PmtId")
                .textChild("InstrId", instructionId)
                .textChild("EndToEndId", endToEndId.isBlank() ? NOT_PROVIDED : endToEndId);

        return XmlElement.element(ELEMENT)
                .child(paymentId)
                .child(amount.toXml())
                .childIfPresent(creditorAgent.toXml("CdtrAgt"))
                .childIfPresent(creditor.toXml("Cdtr"))
                .childIfPresent(creditorAccount.toXml("CdtrAcct"))
                .childIfPresent(remittance.toXml())
                .build();
    }
}
