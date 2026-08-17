package io.zengin4j.core.kana;

import io.zengin4j.core.error.ZenginException;

/**
 * The field cannot hold even one character of the text.
 *
 * <p>Reached when truncation would have to cut to nothing — a one-byte field
 * offered a voiced kana, for instance, where the character itself is two bytes.
 * Distinct from ordinary overflow because no truncation policy can help: the
 * text is not too long, the field is too small.
 *
 * @since 0.4.0
 */
public final class FieldTooSmallException extends ZenginException {
    private static final long serialVersionUID = 1L;

    private final int maxBytes;

    /**
     * Creates the exception.
     *
     * @param text     the text that would not fit
     * @param maxBytes the field width in bytes
     */
    public FieldTooSmallException(String text, int maxBytes) {
        super("'" + text + "' cannot be shortened to fit " + maxBytes + " byte(s): truncating that"
                        + " far would leave nothing, or would leave a voicing mark with no kana in"
                        + " front of it. The field is too small for this text at any length.",
                "'" + text + "' を " + maxBytes + " バイトに収めることはできません。"
                        + "その長さまで切り詰めると何も残らないか、濁点・半濁点だけが残ります。");
        this.maxBytes = maxBytes;
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
