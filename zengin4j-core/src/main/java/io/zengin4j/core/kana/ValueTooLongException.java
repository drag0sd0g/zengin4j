package io.zengin4j.core.kana;

import io.zengin4j.core.error.ZenginException;

/**
 * The text is longer than the field, and the caller asked not to shorten it.
 *
 * <p>The sibling of {@link FieldTooSmallException}, and the distinction between
 * them is the whole point: there, no truncation policy can help, because the
 * field cannot hold even one character. Here a policy would help — the caller
 * has chosen {@link TruncationPolicy#REJECT_IF_TOO_LONG} instead, which is the
 * default, because shortening a payee's name is a decision and not a codec's to
 * make.
 *
 * @since 0.5.0
 */
public final class ValueTooLongException extends ZenginException {

    private static final long serialVersionUID = 1L;

    private final transient String text;
    private final int byteLength;
    private final int maxBytes;

    /**
     * Creates the exception.
     *
     * @param text       the text that did not fit
     * @param byteLength how long it is, in bytes
     * @param maxBytes   the field width in bytes
     */
    public ValueTooLongException(String text, int byteLength, int maxBytes) {
        super("'" + text + "' is " + byteLength + " bytes and does not fit a " + maxBytes
                        + "-byte field. Shorten it deliberately, or choose a TruncationPolicy —"
                        + " a payee's name is not a codec's to cut.",
                "'" + text + "' は " + byteLength + " バイトで、" + maxBytes
                        + " バイトの項目に収まりません。意図的に短縮するか、TruncationPolicy を"
                        + "指定してください。受取人名を無断で切り詰めることはしません。");
        this.text = text;
        this.byteLength = byteLength;
        this.maxBytes = maxBytes;
    }

    /**
     * The text that did not fit.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * How long the text is.
     *
     * @return the length in bytes
     */
    public int byteLength() {
        return byteLength;
    }

    /**
     * The field width that could not be met.
     *
     * @return the width in bytes
     */
    public int maxBytes() {
        return maxBytes;
    }
}
