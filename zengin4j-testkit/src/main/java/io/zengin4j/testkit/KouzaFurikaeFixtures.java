package io.zengin4j.testkit;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;

/// Synthetic 預金口座振替 fixtures.
///
/// **Every value here is invented** (R-L1, P1).
///
/// **The direction is inverted from every other bundled format.**
/// The header names the account that *receives* the collected funds, and
/// each data record names an account to be *debited*. A fixture that
/// reused 総合振込's names would describe money moving the other way, with
/// nothing in the bytes to show it — which is why the descriptor uses
/// `collection*` and `payer*` and these fixtures follow it.
///
/// These produce an **instruction** file: 振替結果コード is zero on
/// every data record and the trailer's four result counters are zero, which is
/// what an instruction file contains before an institution fills them in. One
/// descriptor covers both directions (ADR-0020), so [#withResult] builds
/// the returned form when a test needs one.
///
/// @since 0.3.0
public final class KouzaFurikaeFixtures extends AbstractFormatFixtures {

    /// Id of the format these fixtures produce.
    public static final FormatId FORMAT = FormatId.of("kouza-furikae");

    /// Customer number, 顧客番号. Invented.
    public static final String CUSTOMER_NUMBER = "9900000000000000001";

    /// Amount to be collected, in yen.
    public static final long AMOUNT = Invented.AMOUNT;

    /// 振替結果コード meaning the debit succeeded.
    public static final String RESULT_COLLECTED = "0";

    private final String transferResult;

    private KouzaFurikaeFixtures(FormatDescriptor descriptor, ZenginCharset charset,
            String transferResult) {
        super(descriptor, charset);
        this.transferResult = transferResult;
    }

    /// Creates fixtures using the bundled descriptor and the default charset.
    ///
    /// @return the fixtures
    public static KouzaFurikaeFixtures create() {
        return using(FormatRegistry.defaults().byId(FORMAT).orElseThrow(),
                ZenginCharset.defaultCharset());
    }

    /// Creates fixtures using a supplied descriptor and charset.
    ///
    /// @param descriptor the format descriptor
    /// @param charset    the encoding to write text fields in
    /// @return the fixtures
    public static KouzaFurikaeFixtures using(FormatDescriptor descriptor, ZenginCharset charset) {
        return new KouzaFurikaeFixtures(descriptor, charset, RESULT_COLLECTED);
    }

    /// Returns fixtures whose data records carry a 振替結果コード.
    ///
    /// The returned form of the file, as an institution sends it back. The
    /// layout is unchanged — only values differ, which is the whole reason one
    /// descriptor covers both (ADR-0020).
    ///
    /// @param resultCode the 振替結果コード to write on every data record
    /// @return fixtures producing result records
    public KouzaFurikaeFixtures withResult(String resultCode) {
        return new KouzaFurikaeFixtures(descriptor(), charset(),
                java.util.Objects.requireNonNull(resultCode, "resultCode"));
    }

    @Override
    Map<String, String> headerValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("codeKubun", Invented.CODE_KUBUN_JIS);
        values.put("originatorCode", Invented.ORIGINATOR_CODE);
        values.put("originatorName", Invented.ORIGINATOR_NAME);
        values.put("debitDate", Invented.VALUE_DATE);
        values.put("collectionBankCode", Invented.BANK_CODE);
        values.put("collectionBankName", Invented.BANK_NAME);
        values.put("collectionBranchCode", Invented.ORIGIN_BRANCH_CODE);
        values.put("collectionBranchName", Invented.ORIGIN_BRANCH_NAME);
        values.put("collectionAccountType", Invented.ORDINARY_DEPOSIT);
        values.put("collectionAccountNumber", Invented.ORIGINATOR_ACCOUNT);
        return values;
    }

    @Override
    Map<String, String> dataValues(String payerName, long amount, String accountNumber) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("payerBankCode", Invented.BANK_CODE);
        values.put("payerBankName", Invented.BANK_NAME);
        values.put("payerBranchCode", Invented.BRANCH_CODE);
        values.put("payerBranchName", Invented.BRANCH_NAME);
        values.put("payerAccountType", Invented.ORDINARY_DEPOSIT);
        values.put("payerAccountNumber", accountNumber);
        values.put("payerName", payerName);
        values.put("debitAmount", Long.toString(amount));
        values.put("newCode", "0");
        values.put("customerNumber", CUSTOMER_NUMBER);
        values.put("transferResult", transferResult);
        return values;
    }

    @Override
    Map<String, String> trailerValues(int recordCount, long totalAmount) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("recordCount", Integer.toString(recordCount));
        values.put("totalAmount", Long.toString(totalAmount));
        // The four result counters stay zero. An instruction file has no
        // outcome to report yet, and inventing one here would make every
        // fixture look like a file that had already been through a bank.
        return values;
    }

    @Override
    String exampleName() {
        return Invented.PARTY_NAME;
    }

    @Override
    long exampleAmount() {
        return AMOUNT;
    }

    @Override
    String exampleAccount() {
        return Invented.ACCOUNT_NUMBER;
    }
}
