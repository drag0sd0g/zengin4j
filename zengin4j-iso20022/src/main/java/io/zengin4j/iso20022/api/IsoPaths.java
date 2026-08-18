package io.zengin4j.iso20022.api;

/**
 * The ISO 20022 element paths a loss entry can name.
 *
 * <p>Full paths from {@code Document}, which is what the mapping declarations
 * use and therefore what {@code docs/mapping.md} prints. That correspondence is
 * the whole point: a report saying {@code [CdtTrfTxInf/Cdtr/Nm]} sends a reader
 * to the reference page to find out what the row costs, and they find nothing,
 * because the page says
 * {@code CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Cdtr/Nm}. Every path here appears
 * verbatim in a declared row, and {@code MappingDeclarationTest} fails if one
 * stops doing so.
 */
final class IsoPaths {

    private static final String INITIATION = "CstmrCdtTrfInitn/";
    private static final String GROUP = INITIATION + "GrpHdr/";
    private static final String PAYMENT = INITIATION + "PmtInf/";
    private static final String TRANSACTION = PAYMENT + "CdtTrfTxInf/";

    /** {@code GrpHdr/NbOfTxs} — recomputed, cross-checked against the trailer. */
    static final String NUMBER_OF_TRANSACTIONS = GROUP + "NbOfTxs";

    /** {@code GrpHdr/CtrlSum} — recomputed, cross-checked against the trailer. */
    static final String CONTROL_SUM = GROUP + "CtrlSum";

    /** {@code GrpHdr/InitgPty/Nm} — 委託者名. */
    static final String INITIATING_PARTY_NAME = GROUP + "InitgPty/Nm";

    /** {@code GrpHdr/InitgPty/Id/OrgId/Othr/Id} — 委託者コード, on the way up only. */
    static final String INITIATING_PARTY_ID = GROUP + "InitgPty/Id/OrgId/Othr/Id";

    /** {@code PmtInf/ReqdExctnDt} — 振込指定日, with the year the file lacks. */
    static final String EXECUTION_DATE = PAYMENT + "ReqdExctnDt";

    /** {@code PmtInf/Dbtr/Nm} — 委託者名 again, as the debtor. */
    static final String DEBTOR_NAME = PAYMENT + "Dbtr/Nm";

    /** {@code PmtInf/DbtrAcct/Tp/Prtry} — 預金種目 as a proprietary code. */
    static final String DEBTOR_ACCOUNT_TYPE = PAYMENT + "DbtrAcct/Tp/Prtry";

    /** {@code PmtInf/DbtrAgt/FinInstnId/Nm} — 仕向銀行名. */
    static final String DEBTOR_AGENT_NAME = PAYMENT + "DbtrAgt/FinInstnId/Nm";

    /** {@code PmtInf/DbtrAgt/…/MmbId} — 仕向銀行番号 followed by 仕向支店番号. */
    static final String DEBTOR_AGENT_MEMBER = PAYMENT + "DbtrAgt/FinInstnId/ClrSysMmbId/MmbId";

    /** {@code CdtTrfTxInf/PmtId/EndToEndId} — the creditor's reconciliation reference. */
    static final String END_TO_END_ID = TRANSACTION + "PmtId/EndToEndId";

    /** {@code CdtTrfTxInf/PmtId/InstrId} — the debtor's own reference; not carried. */
    static final String INSTRUCTION_ID = TRANSACTION + "PmtId/InstrId";

    /** {@code CdtTrfTxInf/Amt/InstdAmt} — 振込金額. */
    static final String INSTRUCTED_AMOUNT = TRANSACTION + "Amt/InstdAmt";

    /** {@code CdtTrfTxInf/CdtrAgt/FinInstnId/Nm} — 被仕向銀行名. */
    static final String CREDITOR_AGENT_NAME = TRANSACTION + "CdtrAgt/FinInstnId/Nm";

    /** {@code CdtTrfTxInf/CdtrAgt/…/MmbId} — 被仕向銀行番号 followed by 被仕向支店番号. */
    static final String CREDITOR_AGENT_MEMBER =
            TRANSACTION + "CdtrAgt/FinInstnId/ClrSysMmbId/MmbId";

    /** {@code CdtTrfTxInf/Cdtr/Nm} — 受取人名, where the conversion does its real damage. */
    static final String CREDITOR_NAME = TRANSACTION + "Cdtr/Nm";

    /** {@code CdtTrfTxInf/CdtrAcct/Tp/Prtry} — the beneficiary's 預金種目. */
    static final String CREDITOR_ACCOUNT_TYPE = TRANSACTION + "CdtrAcct/Tp/Prtry";

    /** {@code CdtTrfTxInf/RmtInf/Ustrd} — 顧客コード2, or an encoded EDI payload. */
    static final String REMITTANCE = TRANSACTION + "RmtInf/Ustrd";

    private IsoPaths() {
    }
}
