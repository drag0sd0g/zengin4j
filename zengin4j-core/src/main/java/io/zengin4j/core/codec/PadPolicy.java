package io.zengin4j.core.codec;

import io.zengin4j.core.format.Alignment;
import io.zengin4j.core.format.FieldType;

/**
 * How a value is placed within a fixed-width field, and what fills the rest.
 *
 * @since 0.1.0
 */
public enum PadPolicy {
    /** Value first, spaces after: the convention for {@code C} fields. */
    LEFT_ALIGNED_SPACE(Alignment.LEFT, (byte) ' '),

    /** Zeros first, value last: the convention for {@code N} fields. */
    RIGHT_ALIGNED_ZERO(Alignment.RIGHT, (byte) '0');

    private final Alignment alignment;
    private final byte padByte;

    PadPolicy(Alignment alignment, byte padByte) {
        this.alignment = alignment;
        this.padByte = padByte;
    }

    /**
     * Returns the policy a field type implies.
     *
     * @param type the field type
     * @return the matching policy
     */
    public static PadPolicy of(FieldType type) {
        return type == FieldType.N ? RIGHT_ALIGNED_ZERO : LEFT_ALIGNED_SPACE;
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
     * Returns the byte that fills the unused part of the field.
     *
     * @return the pad byte
     */
    public byte padByte() {
        return padByte;
    }
}
