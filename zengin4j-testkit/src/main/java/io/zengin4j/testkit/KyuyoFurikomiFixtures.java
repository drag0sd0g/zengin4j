package io.zengin4j.testkit;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;

/// Synthetic 給与振込 and 賞与振込 fixtures.
///
/// **Every value here is invented** (R-L1, P1).
///
/// One class serves both formats because the standard says they are one
/// layout under two 種別コード — the same reason `shoyo-furikomi.yaml`
/// borrows its fields rather than restating them. [#kyuyo()] and
/// [#shoyo()] differ only in which descriptor they encode against.
///
/// **This is not 総合振込 with three fields renamed.** The data
/// record has fourteen fields where 総合振込 has sixteen: 社員番号 and 所属コード
/// occupy the bytes 顧客コード1 and 顧客コード2 do, and the nine bytes after them
/// are filler where 総合振込 carries 振込指定区分 and 識別表示. There is no EDI
/// overlay in this format, so these fixtures never produce one.
///
/// Names go through the `PAYROLL_NAME` character class, which admits no
/// Latin letters at all — so the customer-reference trick 総合振込's fixtures use
/// has no equivalent here, and 社員番号 carries digits written as text.
///
/// @since 0.3.0
public final class KyuyoFurikomiFixtures extends AbstractFormatFixtures {

    /// Id of the 給与振込 format.
    public static final FormatId KYUYO = FormatId.of("kyuyo-furikomi");

    /// Id of the 賞与振込 format, which shares this layout.
    public static final FormatId SHOYO = FormatId.of("shoyo-furikomi");

    /// Employee number, 社員番号. Invented, and declared `C`; see D-002.
    public static final String EMPLOYEE_NUMBER = "9000012345";

    /// Department code, 所属コード. Invented.
    public static final String DEPARTMENT_CODE = "9990000001";

    /// Payroll amount in yen.
    public static final long AMOUNT = Invented.AMOUNT;

    private KyuyoFurikomiFixtures(FormatDescriptor descriptor, ZenginCharset charset) {
        super(descriptor, charset);
    }

    /// Creates 給与振込 fixtures using the bundled descriptor and default charset.
    ///
    /// @return the fixtures
    public static KyuyoFurikomiFixtures kyuyo() {
        return using(FormatRegistry.defaults().byId(KYUYO).orElseThrow(),
                ZenginCharset.defaultCharset());
    }

    /// Creates 賞与振込 fixtures using the bundled descriptor and default charset.
    ///
    /// @return the fixtures
    public static KyuyoFurikomiFixtures shoyo() {
        return using(FormatRegistry.defaults().byId(SHOYO).orElseThrow(),
                ZenginCharset.defaultCharset());
    }

    /// Creates fixtures using a supplied descriptor and charset.
    ///
    /// @param descriptor the format descriptor; 給与振込 or 賞与振込
    /// @param charset    the encoding to write text fields in
    /// @return the fixtures
    public static KyuyoFurikomiFixtures using(FormatDescriptor descriptor, ZenginCharset charset) {
        return new KyuyoFurikomiFixtures(descriptor, charset);
    }

    @Override
    Map<String, String> headerValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("codeKubun", Invented.CODE_KUBUN_JIS);
        values.put("originatorCode", Invented.ORIGINATOR_CODE);
        values.put("originatorName", Invented.ORIGINATOR_NAME);
        values.put("valueDate", Invented.VALUE_DATE);
        values.put("originBankCode", Invented.BANK_CODE);
        values.put("originBankName", Invented.BANK_NAME);
        values.put("originBranchCode", Invented.ORIGIN_BRANCH_CODE);
        values.put("originBranchName", Invented.ORIGIN_BRANCH_NAME);
        values.put("accountType", Invented.ORDINARY_DEPOSIT);
        values.put("accountNumber", Invented.ORIGINATOR_ACCOUNT);
        return values;
    }

    @Override
    Map<String, String> dataValues(String beneficiaryName, long amount, String accountNumber) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("beneficiaryBankCode", Invented.BANK_CODE);
        values.put("beneficiaryBankName", Invented.BANK_NAME);
        values.put("beneficiaryBranchCode", Invented.BRANCH_CODE);
        values.put("beneficiaryBranchName", Invented.BRANCH_NAME);
        values.put("clearingHouseCode", "0000");
        values.put("accountType", Invented.ORDINARY_DEPOSIT);
        values.put("accountNumber", accountNumber);
        values.put("beneficiaryName", beneficiaryName);
        values.put("amount", Long.toString(amount));
        values.put("newCode", "0");
        values.put("employeeNumber", EMPLOYEE_NUMBER);
        values.put("departmentCode", DEPARTMENT_CODE);
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
