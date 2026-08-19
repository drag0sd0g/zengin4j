package io.zengin4j.iso20022.api;

/// The ISO 20022 element paths a loss entry can name.
///
/// Full paths from `Document`, which is what the mapping declarations
/// use and therefore what `docs/mapping.md` prints. That correspondence is
/// the whole point: a report saying `[CdtTrfTxInf/Cdtr/Nm]` sends a reader
/// to the reference page to find out what the row costs, and they find nothing,
/// because the page says
/// `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Cdtr/Nm`. Every path here appears
/// verbatim in a declared row, and `MappingDeclarationTest` fails if one
/// stops doing so.
final class IsoPaths {

    private static final String INITIATION = "CstmrCdtTrfInitn/";
    private static final String GROUP = INITIATION + "GrpHdr/";
    private static final String PAYMENT = INITIATION + "PmtInf/";
    private static final String TRANSACTION = PAYMENT + "CdtTrfTxInf/";

    /// `GrpHdr/NbOfTxs` — recomputed, cross-checked against the trailer.
    static final String NUMBER_OF_TRANSACTIONS = GROUP + "NbOfTxs";

    /// `GrpHdr/CtrlSum` — recomputed, cross-checked against the trailer.
    static final String CONTROL_SUM = GROUP + "CtrlSum";

    /// `GrpHdr/InitgPty/Nm` — 委託者名.
    static final String INITIATING_PARTY_NAME = GROUP + "InitgPty/Nm";

    /// `GrpHdr/InitgPty/Id/OrgId/Othr/Id` — 委託者コード, on the way up only.
    static final String INITIATING_PARTY_ID = GROUP + "InitgPty/Id/OrgId/Othr/Id";

    /// `PmtInf/ReqdExctnDt` — 振込指定日, with the year the file lacks.
    static final String EXECUTION_DATE = PAYMENT + "ReqdExctnDt";

    /// `PmtInf/Dbtr/Nm` — 委託者名 again, as the debtor.
    static final String DEBTOR_NAME = PAYMENT + "Dbtr/Nm";

    /// `PmtInf/DbtrAcct/Tp/Prtry` — 預金種目 as a proprietary code.
    static final String DEBTOR_ACCOUNT_TYPE = PAYMENT + "DbtrAcct/Tp/Prtry";

    /// `PmtInf/DbtrAgt/FinInstnId/Nm` — 仕向銀行名.
    static final String DEBTOR_AGENT_NAME = PAYMENT + "DbtrAgt/FinInstnId/Nm";

    /// `PmtInf/DbtrAgt/…/MmbId` — 仕向銀行番号 followed by 仕向支店番号.
    static final String DEBTOR_AGENT_MEMBER = PAYMENT + "DbtrAgt/FinInstnId/ClrSysMmbId/MmbId";

    /// `CdtTrfTxInf/PmtId/EndToEndId` — the creditor's reconciliation reference.
    static final String END_TO_END_ID = TRANSACTION + "PmtId/EndToEndId";

    /// `CdtTrfTxInf/PmtId/InstrId` — the debtor's own reference; not carried.
    static final String INSTRUCTION_ID = TRANSACTION + "PmtId/InstrId";

    /// `CdtTrfTxInf/Amt/InstdAmt` — 振込金額.
    static final String INSTRUCTED_AMOUNT = TRANSACTION + "Amt/InstdAmt";

    /// `CdtTrfTxInf/CdtrAgt/FinInstnId/Nm` — 被仕向銀行名.
    static final String CREDITOR_AGENT_NAME = TRANSACTION + "CdtrAgt/FinInstnId/Nm";

    /// `CdtTrfTxInf/CdtrAgt/…/MmbId` — 被仕向銀行番号 followed by 被仕向支店番号.
    static final String CREDITOR_AGENT_MEMBER =
            TRANSACTION + "CdtrAgt/FinInstnId/ClrSysMmbId/MmbId";

    /// `CdtTrfTxInf/Cdtr/Nm` — 受取人名, where the conversion does its real damage.
    static final String CREDITOR_NAME = TRANSACTION + "Cdtr/Nm";

    /// `CdtTrfTxInf/CdtrAcct/Tp/Prtry` — the beneficiary's 預金種目.
    static final String CREDITOR_ACCOUNT_TYPE = TRANSACTION + "CdtrAcct/Tp/Prtry";

    /// `CdtTrfTxInf/RmtInf/Ustrd` — 顧客コード2, or an encoded EDI payload.
    static final String REMITTANCE = TRANSACTION + "RmtInf/Ustrd";

    private IsoPaths() {
    }
}
