package io.zengin4j.core.model;

import java.util.Optional;

/**
 * The record separator convention observed in, or to be written to, a file.
 *
 * <p>Separators are optional in these formats and are not counted in the
 * record length (R-C6, R-C7). Files arrive with all four conventions, and
 * occasionally with more than one in the same file.
 *
 * @since 0.1.0
 */
public enum SeparatorStyle {

    /** Records run together with nothing between them. */
    NONE(new byte[0]),

    /** Carriage return only, {@code 0x0D}. */
    CR(new byte[] {'\r'}),

    /** Line feed only, {@code 0x0A}. */
    LF(new byte[] {'\n'}),

    /** Carriage return and line feed, {@code 0x0D 0x0A}. The default on write (R-C9). */
    CRLF(new byte[] {'\r', '\n'}),

    /**
     * More than one convention within a single file.
     *
     * <p>Such a file cannot be reproduced byte for byte from a single
     * separator setting, so it falls outside the round-trip invariant. The
     * reader records the fact rather than quietly normalising it.
     */
    MIXED(null);

    private final byte[] bytes;

    SeparatorStyle(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Classifies a separator byte sequence.
     *
     * @param separator the bytes found between two records
     * @return the matching style, or empty if the sequence is not one of the
     *         recognised conventions
     */
    public static Optional<SeparatorStyle> of(byte[] separator) {
        for (SeparatorStyle style : values()) {
            if (style.bytes != null && java.util.Arrays.equals(style.bytes, separator)) {
                return Optional.of(style);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the bytes this style writes between records.
     *
     * @return a copy of the separator bytes, or empty for {@link #MIXED},
     *         which is an observation rather than a writable setting
     */
    public Optional<byte[]> bytes() {
        return bytes == null ? Optional.empty() : Optional.of(bytes.clone());
    }
}
