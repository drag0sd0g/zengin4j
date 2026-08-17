package io.zengin4j.core.error;

import io.zengin4j.core.charset.CodeKubun;

/**
 * The header declares an encoding variant this library does not implement.
 *
 * <p>コード区分 value {@code 1} means EBCDIC. Full EBCDIC support is out of
 * scope, and the important property is that such a file is <em>rejected by
 * name</em> rather than decoded as if it were JIS (R-C14): every byte would
 * decode to a plausible-looking but wrong character, and nothing downstream
 * would indicate a problem.
 *
 * @since 0.1.0
 */
public final class UnsupportedEncodingVariantException extends ZenginException {
    private final CodeKubun found;

    /**
     * Creates a diagnostic naming the unsupported encoding variant.
     *
     * @param found      the コード区分 value read from the header
     * @param rawValue   the raw field content, as it appears in the file
     * @param byteOffset byte offset of the コード区分 field within the file
     */
    public UnsupportedEncodingVariantException(CodeKubun found, String rawValue, long byteOffset) {
        super("コード区分 at byte " + byteOffset + " is '" + rawValue + "' (" + found
                        + "). Only JIS ('0') is supported. An EBCDIC file decoded as JIS would produce"
                        + " plausible but wrong characters in every text field, so it is rejected instead.",
                "コード区分 (" + byteOffset + " バイト目) が '" + rawValue + "' (" + found
                        + ") です。対応しているのは JIS ('0') のみです。EBCDIC のファイルを JIS として"
                        + "読み込むと全ての文字項目が誤って復号されるため、処理を中止します。");
        this.found = found;
    }

    /**
     * Returns the encoding variant that was found.
     *
     * @return the コード区分 value, never {@code null}
     */
    public CodeKubun found() {
        return found;
    }
}
