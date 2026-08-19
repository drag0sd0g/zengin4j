package io.zengin4j.testkit;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;

/// Synthetic 総合振込 fixtures.
///
/// **Every value here is invented** (R-L1, P1). The bank,
/// branch and account numbers are outside the ranges any Japanese institution
/// uses — there is no officially published test range, so this library defines
/// one and documents it: bank `9999`, branch `999`, accounts
/// beginning with `9`.
///
/// The data record reproduces the structure of the worked example in §20.1
/// of the build specification, which was chosen to exercise four things at
/// once: a name field containing a standalone dakuten byte, a name shorter in
/// characters than in bytes, a customer reference that overflows one field into
/// the next, and two different padding rules in the same record.
///
/// @since 0.1.0
public final class SougouFurikomiFixtures extends AbstractFormatFixtures {

    /// Id of the format these fixtures produce.
    public static final FormatId FORMAT = FormatId.of("sougou-furikomi");

    /// Beneficiary bank code. Invented; no institution uses it.
    public static final String BENEFICIARY_BANK_CODE = Invented.BANK_CODE;

    /// Beneficiary bank name: ﾃｽﾄｷﾞﾝｺｳ.
    ///
    /// Renders as seven characters, テストギンコウ, but occupies eight bytes:
    /// the ｷﾞ is a base kana followed by a standalone dakuten. Truncating
    /// between them turns ギ into キ (§17).
    public static final String BENEFICIARY_BANK_NAME = Invented.BANK_NAME;

    /// Beneficiary branch code. Invented.
    public static final String BENEFICIARY_BRANCH_CODE = Invented.BRANCH_CODE;

    /// Beneficiary branch name: ﾃｽﾄｼﾃﾝ.
    public static final String BENEFICIARY_BRANCH_NAME = Invented.BRANCH_NAME;

    /// Beneficiary account number. Invented.
    public static final String BENEFICIARY_ACCOUNT = Invented.ACCOUNT_NUMBER;

    /// Beneficiary name: ﾔﾏﾀﾞ ﾀﾛｳ.
    ///
    /// Renders as seven characters, ヤマダ タロウ, and occupies eight bytes:
    /// ﾀﾞ is two.
    public static final String BENEFICIARY_NAME = Invented.PARTY_NAME;

    /// Transfer amount in yen.
    public static final long AMOUNT = Invented.AMOUNT;

    /// Customer reference, eleven bytes against a ten-byte field.
    ///
    /// Split across 顧客コード1 and 顧客コード2 exactly as §20.1 shows. Whether
    /// an institution permits that spill is unconfirmed; the library's job is to
    /// represent what the file says.
    public static final String CUSTOMER_REFERENCE = "INV20260001";

    /// Originating institution code. Invented.
    public static final String ORIGIN_BANK_CODE = Invented.BANK_CODE;

    /// Originating branch code. Invented.
    public static final String ORIGIN_BRANCH_CODE = Invented.ORIGIN_BRANCH_CODE;

    /// Originator code. Invented.
    public static final String ORIGINATOR_CODE = Invented.ORIGINATOR_CODE;

    /// Originator name: ﾃｽﾄｼﾖｳｼﾞ.
    public static final String ORIGINATOR_NAME = Invented.ORIGINATOR_NAME;

    /// Value date, `MMDD`.
    public static final String VALUE_DATE = Invented.VALUE_DATE;

    private SougouFurikomiFixtures(FormatDescriptor descriptor, ZenginCharset charset) {
        super(descriptor, charset);
    }

    /// Creates fixtures using the bundled descriptor and the default charset.
    ///
    /// @return the fixtures
    public static SougouFurikomiFixtures create() {
        return using(FormatRegistry.defaults().byId(FORMAT).orElseThrow(),
                ZenginCharset.defaultCharset());
    }

    /// Creates fixtures using a supplied descriptor and charset.
    ///
    /// @param descriptor the format descriptor
    /// @param charset    the encoding to write text fields in
    /// @return the fixtures
    public static SougouFurikomiFixtures using(FormatDescriptor descriptor, ZenginCharset charset) {
        return new SougouFurikomiFixtures(descriptor, charset);
    }

    @Override
    Map<String, String> headerValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("codeKubun", Invented.CODE_KUBUN_JIS);
        values.put("originatorCode", ORIGINATOR_CODE);
        values.put("originatorName", ORIGINATOR_NAME);
        values.put("valueDate", VALUE_DATE);
        values.put("originBankCode", ORIGIN_BANK_CODE);
        values.put("originBankName", Invented.BANK_NAME);
        values.put("originBranchCode", ORIGIN_BRANCH_CODE);
        values.put("originBranchName", Invented.ORIGIN_BRANCH_NAME);
        values.put("accountType", Invented.ORDINARY_DEPOSIT);
        values.put("accountNumber", Invented.ORIGINATOR_ACCOUNT);
        return values;
    }

    @Override
    Map<String, String> dataValues(String beneficiaryName, long amount, String accountNumber) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("beneficiaryBankCode", BENEFICIARY_BANK_CODE);
        values.put("beneficiaryBankName", BENEFICIARY_BANK_NAME);
        values.put("beneficiaryBranchCode", BENEFICIARY_BRANCH_CODE);
        values.put("beneficiaryBranchName", BENEFICIARY_BRANCH_NAME);
        values.put("clearingHouseCode", "0000");
        values.put("accountType", Invented.ORDINARY_DEPOSIT);
        values.put("accountNumber", accountNumber);
        values.put("beneficiaryName", beneficiaryName);
        values.put("amount", Long.toString(amount));
        values.put("newCode", "0");
        values.put("customerCode1", CUSTOMER_REFERENCE.substring(0, 10));
        values.put("customerCode2", CUSTOMER_REFERENCE.substring(10));
        values.put("transferCategory", "7");
        values.put("identification", " ");
        return values;
    }

    @Override
    Map<String, String> trailerValues(int recordCount, long totalAmount) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("recordCount", Integer.toString(recordCount));
        values.put("totalAmount", Long.toString(totalAmount));
        return values;
    }

    @Override
    String exampleName() {
        return BENEFICIARY_NAME;
    }

    @Override
    long exampleAmount() {
        return AMOUNT;
    }

    @Override
    String exampleAccount() {
        return BENEFICIARY_ACCOUNT;
    }
}
