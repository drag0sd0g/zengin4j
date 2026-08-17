package io.zengin4j.testkit;

/**
 * The invented identifiers every fixture draws on (R-L1, P1).
 *
 * <p>Kept in one place so the four formats cannot drift into using different
 * "synthetic" ranges, one of which later turns out to belong to somebody. There
 * is no officially published test range for 統一金融機関番号, so this library
 * defines one and documents it: bank {@code 9999}, branch {@code 999} and
 * {@code 998}, accounts beginning with {@code 9}.
 *
 * <p>The CI identifier scan asserts that nothing outside these ranges appears
 * in the repository, so widening them here widens what that scan permits.
 *
 * @since 0.3.0
 */
final class Invented {
    /** Counterparty bank code. No institution uses it. */
    static final String BANK_CODE = "9999";

    /** Counterparty branch code. */
    static final String BRANCH_CODE = "999";

    /** The originating side's branch, distinct from the counterparty's. */
    static final String ORIGIN_BRANCH_CODE = "998";

    /**
     * Bank name: ﾃｽﾄｷﾞﾝｺｳ.
     *
     * <p>Seven characters, eight bytes: ｷﾞ is a base kana followed by a
     * standalone dakuten, and truncating between them turns ギ into キ (§17).
     */
    static final String BANK_NAME = "ﾃｽﾄｷﾞﾝｺｳ";

    /** Branch name: ﾃｽﾄｼﾃﾝ. */
    static final String BRANCH_NAME = "ﾃｽﾄｼﾃﾝ";

    /** The originating side's branch name: ﾎﾝﾃﾝ. */
    static final String ORIGIN_BRANCH_NAME = "ﾎﾝﾃﾝ";

    /** Originator code. */
    static final String ORIGINATOR_CODE = "9900000001";

    /** Originator name: ﾃｽﾄｼﾖｳｼﾞ. Note ｼﾖ, not the small ｼｮ, which is not permitted. */
    static final String ORIGINATOR_NAME = "ﾃｽﾄｼﾖｳｼﾞ";

    /** The originator's own account. */
    static final String ORIGINATOR_ACCOUNT = "9000001";

    /** Counterparty account number. */
    static final String ACCOUNT_NUMBER = "9876543";

    /** Counterparty name: ﾔﾏﾀﾞ ﾀﾛｳ. Seven characters, eight bytes. */
    static final String PARTY_NAME = "ﾔﾏﾀﾞ ﾀﾛｳ";

    /** Amount in yen used by every worked example. */
    static final long AMOUNT = 150_000L;

    /** Value date, {@code MMDD}. */
    static final String VALUE_DATE = "0930";

    /** 普通預金. Every format admits it; some admit little else. */
    static final String ORDINARY_DEPOSIT = "1";

    /** JIS, as opposed to the EBCDIC declaration the reader rejects by name. */
    static final String CODE_KUBUN_JIS = "0";

    private Invented() {
    }
}
