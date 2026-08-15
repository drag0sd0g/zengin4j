package io.zengin4j.testkit;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.SeparatorStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synthetic 総合振込 fixtures.
 *
 * <p><strong>Every value here is invented</strong> (R-L1, P1). The bank,
 * branch and account numbers are outside the ranges any Japanese institution
 * uses — there is no officially published test range, so this library defines
 * one and documents it: bank {@code 9999}, branch {@code 999}, accounts
 * beginning with {@code 9}.
 *
 * <p>The data record reproduces the structure of the worked example in §20.1
 * of the build specification, which was chosen to exercise four things at
 * once: a name field containing a standalone dakuten byte, a name shorter in
 * characters than in bytes, a customer reference that overflows one field into
 * the next, and two different padding rules in the same record.
 *
 * @since 0.1.0
 */
public final class SougouFurikomiFixtures {

    /** Id of the format these fixtures produce. */
    public static final FormatId FORMAT = FormatId.of("sougou-furikomi");

    /** Beneficiary bank code. Invented; no institution uses it. */
    public static final String BENEFICIARY_BANK_CODE = "9999";

    /**
     * Beneficiary bank name: ﾃｽﾄｷﾞﾝｺｳ.
     *
     * <p>Renders as seven characters, テストギンコウ, but occupies eight bytes:
     * the ｷﾞ is a base kana followed by a standalone dakuten. Truncating
     * between them turns ギ into キ (§17).
     */
    public static final String BENEFICIARY_BANK_NAME = "ﾃｽﾄｷﾞﾝｺｳ";

    /** Beneficiary branch code. Invented. */
    public static final String BENEFICIARY_BRANCH_CODE = "999";

    /** Beneficiary branch name: ﾃｽﾄｼﾃﾝ. */
    public static final String BENEFICIARY_BRANCH_NAME = "ﾃｽﾄｼﾃﾝ";

    /** Beneficiary account number. Invented. */
    public static final String BENEFICIARY_ACCOUNT = "9876543";

    /**
     * Beneficiary name: ﾔﾏﾀﾞ ﾀﾛｳ.
     *
     * <p>Renders as seven characters, ヤマダ タロウ, and occupies eight bytes:
     * ﾀﾞ is two.
     */
    public static final String BENEFICIARY_NAME = "ﾔﾏﾀﾞ ﾀﾛｳ";

    /** Transfer amount in yen. */
    public static final long AMOUNT = 150_000L;

    /**
     * Customer reference, eleven bytes against a ten-byte field.
     *
     * <p>Split across 顧客コード1 and 顧客コード2 exactly as §20.1 shows. Whether
     * an institution permits that spill is unconfirmed; the library's job is to
     * represent what the file says.
     */
    public static final String CUSTOMER_REFERENCE = "INV20260001";

    /** Originating institution code. Invented. */
    public static final String ORIGIN_BANK_CODE = "9999";

    /** Originating branch code. Invented. */
    public static final String ORIGIN_BRANCH_CODE = "998";

    /** Originator code. Invented. */
    public static final String ORIGINATOR_CODE = "9900000001";

    /** Originator name: ﾃｽﾄｼｮｳｼﾞ. */
    public static final String ORIGINATOR_NAME = "ﾃｽﾄｼｮｳｼﾞ";

    /** Value date, {@code MMDD}. */
    public static final String VALUE_DATE = "0930";

    private final FormatDescriptor descriptor;
    private final ZenginCharset charset;

    private SougouFurikomiFixtures(FormatDescriptor descriptor, ZenginCharset charset) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.charset = Objects.requireNonNull(charset, "charset");
    }

    /**
     * Creates fixtures using the bundled descriptor and the default charset.
     *
     * @return the fixtures
     */
    public static SougouFurikomiFixtures create() {
        return using(FormatRegistry.defaults().byId(FORMAT).orElseThrow(), ZenginCharset.defaultCharset());
    }

    /**
     * Creates fixtures using a supplied descriptor and charset.
     *
     * @param descriptor the format descriptor
     * @param charset    the encoding to write text fields in
     * @return the fixtures
     */
    public static SougouFurikomiFixtures using(FormatDescriptor descriptor, ZenginCharset charset) {
        return new SougouFurikomiFixtures(descriptor, charset);
    }

    /**
     * Returns the descriptor these fixtures encode against.
     *
     * @return the format descriptor
     */
    public FormatDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Returns reader options that will accept these fixtures.
     *
     * <p>Includes {@code allowUnverifiedFormats(true)}, because the bundled
     * 総合振込 descriptor is provisional and the reader refuses it otherwise.
     *
     * @return reader options
     */
    public ReaderOptions readerOptions() {
        return ReaderOptions.builder()
                .charset(charset)
                .allowUnverifiedFormats(true)
                .warningListener(warning -> {
                    // Fixtures are used in tests; the unverified-format warning
                    // is expected and would only add noise.
                })
                .build();
    }

    /**
     * Builds a header record.
     *
     * @return the record bytes
     */
    public byte[] header() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("codeKubun", "0");
        values.put("originatorCode", ORIGINATOR_CODE);
        values.put("originatorName", ORIGINATOR_NAME);
        values.put("valueDate", VALUE_DATE);
        values.put("originBankCode", ORIGIN_BANK_CODE);
        values.put("originBankName", "ﾃｽﾄｷﾞﾝｺｳ");
        values.put("originBranchCode", ORIGIN_BRANCH_CODE);
        values.put("originBranchName", "ﾎﾝﾃﾝ");
        values.put("accountType", "1");
        values.put("accountNumber", "9000001");
        return SyntheticRecords.encode(descriptor.record(RecordKind.HEADER), charset, values);
    }

    /**
     * Builds the worked-example data record.
     *
     * @return the record bytes
     */
    public byte[] data() {
        return data(BENEFICIARY_NAME, AMOUNT, BENEFICIARY_ACCOUNT);
    }

    /**
     * Builds a data record with a chosen beneficiary, amount and account.
     *
     * @param beneficiaryName the 受取人名 value
     * @param amount          the amount in yen
     * @param accountNumber   the seven-digit account number
     * @return the record bytes
     */
    public byte[] data(String beneficiaryName, long amount, String accountNumber) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("beneficiaryBankCode", BENEFICIARY_BANK_CODE);
        values.put("beneficiaryBankName", BENEFICIARY_BANK_NAME);
        values.put("beneficiaryBranchCode", BENEFICIARY_BRANCH_CODE);
        values.put("beneficiaryBranchName", BENEFICIARY_BRANCH_NAME);
        values.put("clearingHouseCode", "0000");
        values.put("accountType", "1");
        values.put("accountNumber", accountNumber);
        values.put("beneficiaryName", beneficiaryName);
        values.put("amount", Long.toString(amount));
        values.put("newCode", "0");
        values.put("customerCode1", CUSTOMER_REFERENCE.substring(0, 10));
        values.put("customerCode2", CUSTOMER_REFERENCE.substring(10));
        values.put("transferCategory", "7");
        values.put("identification", " ");
        return SyntheticRecords.encode(descriptor.record(RecordKind.DATA), charset, values);
    }

    /**
     * Builds a trailer record.
     *
     * @param recordCount the 合計件数 value
     * @param totalAmount the 合計金額 value
     * @return the record bytes
     */
    public byte[] trailer(int recordCount, long totalAmount) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("recordCount", Integer.toString(recordCount));
        values.put("totalAmount", Long.toString(totalAmount));
        return SyntheticRecords.encode(descriptor.record(RecordKind.TRAILER), charset, values);
    }

    /**
     * Builds an end record.
     *
     * @return the record bytes
     */
    public byte[] end() {
        return SyntheticRecords.encode(descriptor.record(RecordKind.END), charset, Map.of());
    }

    /**
     * Builds a one-batch, one-payment file with CRLF separators.
     *
     * @return the file bytes
     */
    public byte[] file() {
        return file(SeparatorStyle.CRLF, false);
    }

    /**
     * Builds a one-batch, one-payment file.
     *
     * @param separator       what to write between records
     * @param trailingEofByte whether to append {@code 0x1A}
     * @return the file bytes
     */
    public byte[] file(SeparatorStyle separator, boolean trailingEofByte) {
        return SyntheticRecords.file(List.of(header(), data(), trailer(1, AMOUNT), end()),
                separator, trailingEofByte);
    }

    /**
     * Builds a file with a chosen number of identical payments.
     *
     * @param payments        how many data records to write
     * @param separator       what to write between records
     * @param trailingEofByte whether to append {@code 0x1A}
     * @return the file bytes
     */
    public byte[] file(int payments, SeparatorStyle separator, boolean trailingEofByte) {
        List<byte[]> records = new ArrayList<>();
        records.add(header());
        for (int i = 0; i < payments; i++) {
            records.add(data());
        }
        records.add(trailer(payments, AMOUNT * payments));
        records.add(end());
        return SyntheticRecords.file(records, separator, trailingEofByte);
    }
}
