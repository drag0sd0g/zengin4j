package io.zengin4j.iso20022.pain001;

import module java.base;
import io.zengin4j.iso20022.xml.XmlElement;

/// One `PmtInf` — everything the debtor's side shares across a run of
/// payments, and the payments themselves.
///
/// A Zengin batch maps to exactly one of these. The correspondence is close:
/// both group payments under one debtor, one debit account and one execution
/// date, which is why the batch structure survives the conversion when almost
/// nothing else does.
///
/// @param paymentInformationId    `PmtInfId` — the debtor's reference for
///   this group
/// @param requestedExecutionDate  `ReqdExctnDt` — 振込指定日, with the year
///   the fixed-length file does not carry
/// @param debtor                  `Dbtr` — 委託者
/// @param debtorAccount           `DbtrAcct`
/// @param debtorAgent             `DbtrAgt` — 仕向銀行
/// @param transactions            `CdtTrfTxInf`, in order
/// @since 0.5.0
public record PaymentInstruction(
        String paymentInformationId,
        LocalDate requestedExecutionDate,
        Party debtor,
        Account debtorAccount,
        Agent debtorAgent,
        List<CreditTransferTransaction> transactions) {

    /// The element name.
    public static final String ELEMENT = "PmtInf";

    /// The only payment method a credit transfer file uses.
    public static final String PAYMENT_METHOD = "TRF";

    /// Validates the instruction.
    ///
    /// @throws NullPointerException if any component is null
    public PaymentInstruction {
        Objects.requireNonNull(paymentInformationId, "paymentInformationId");
        Objects.requireNonNull(requestedExecutionDate, "requestedExecutionDate");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(debtorAccount, "debtorAccount");
        Objects.requireNonNull(debtorAgent, "debtorAgent");
        transactions = List.copyOf(Objects.requireNonNull(transactions, "transactions"));
    }

    /// The number of payments, counted rather than read.
    ///
    /// @return the transaction count
    public int numberOfTransactions() {
        return transactions.size();
    }

    /// The sum of the payments, computed rather than read.
    ///
    /// @return the control sum
    public BigDecimal controlSum() {
        return transactions.stream()
                .map(transaction -> transaction.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /// Reads an instruction from its element.
    ///
    /// @param element the `PmtInf` element
    /// @return the instruction
    public static PaymentInstruction from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return new PaymentInstruction(
                element.textAt("PmtInfId").orElse(""),
                element.textAt("ReqdExctnDt").map(LocalDate::parse).orElse(LocalDate.EPOCH),
                element.at("Dbtr").map(Party::from).orElse(Party.named("")),
                element.at("DbtrAcct").map(Account::from).orElse(new Account("", "")),
                element.at("DbtrAgt").map(Agent::from).orElse(new Agent("", "", "")),
                element.childrenNamed(CreditTransferTransaction.ELEMENT).stream()
                        .map(CreditTransferTransaction::from)
                        .toList());
    }

    /// Renders the instruction, in the order the message definition sequences it.
    ///
    /// `NbOfTxs` and `CtrlSum` are computed from the transactions
    /// rather than carried, so a caller cannot build an instruction that
    /// disagrees with itself — the same reasoning as the Zengin trailer, which
    /// `ZenginFileBuilder` computes for the same reason.
    ///
    /// @return the `PmtInf` element
    public XmlElement toXml() {
        XmlElement.Builder builder = XmlElement.element(ELEMENT)
                .textChild("PmtInfId", paymentInformationId)
                .textChild("PmtMtd", PAYMENT_METHOD)
                .textChild("NbOfTxs", String.valueOf(numberOfTransactions()))
                .textChild("CtrlSum", controlSum().stripTrailingZeros().toPlainString())
                .textChild("ReqdExctnDt", requestedExecutionDate.toString())
                .childIfPresent(debtor.toXml("Dbtr"))
                .childIfPresent(debtorAccount.toXml("DbtrAcct"))
                .childIfPresent(debtorAgent.toXml("DbtrAgt"));
        transactions.forEach(transaction -> builder.child(transaction.toXml()));
        return builder.build();
    }
}
