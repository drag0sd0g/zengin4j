package io.zengin4j.core.format;

import io.zengin4j.core.error.FormatDescriptorException;

/**
 * The two field types of the Zengin record formats (§12.8).
 *
 * <p>Both are fixed-width byte ranges. They differ in permitted content,
 * alignment and pad byte, and getting either wrong produces a file a bank will
 * reject — or worse, accept and misread.
 *
 * @since 0.1.0
 */
public enum FieldType {

    /**
     * ゾーン10進数 — ASCII digits only, right aligned, padded on the left with
     * {@code '0'}. An omitted value is all zeros.
     */
    N(Alignment.RIGHT, (byte) '0'),

    /**
     * Half-width katakana, upper-case {@code A}-{@code Z}, digits and a
     * limited symbol set, left aligned, padded on the right with a space. An
     * omitted value is all spaces.
     *
     * <p>Lower-case Latin and full-width characters are not permitted
     * (R-C16). The exact symbol subset varies by institution and is
     * unconfirmed (Q5).
     */
    C(Alignment.LEFT, (byte) ' ');

    private final Alignment alignment;
    private final byte padByte;

    FieldType(Alignment alignment, byte padByte) {
        this.alignment = alignment;
        this.padByte = padByte;
    }

    /**
     * Parses a descriptor's {@code type} value.
     *
     * @param raw    the raw value, {@code N} or {@code C}
     * @param origin the descriptor resource name, for diagnostics
     * @return the field type
     * @throws FormatDescriptorException if the value is not {@code N} or
     *                                   {@code C}
     */
    public static FieldType parse(String raw, String origin) {
        return switch (raw) {
            case "N" -> N;
            case "C" -> C;
            default -> throw FormatDescriptorException.forResource(origin,
                    "field type must be N or C, found '" + raw + "'");
        };
    }

    /**
     * Returns which end of the field the value sits against.
     *
     * @return the alignment
     */
    public Alignment alignment() {
        return alignment;
    }

    /**
     * Returns the byte used to pad the unused part of the field.
     *
     * @return the pad byte
     */
    public byte padByte() {
        return padByte;
    }
}
