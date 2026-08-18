package io.zengin4j.iso20022.mapping;

/**
 * Which way a mapping row applies.
 *
 * <p>Most rows apply both ways. The asymmetric ones are the interesting ones,
 * and naming the direction is what keeps them honest: a value the fixed-length
 * side cannot represent is written on the way out and dropped on the way back,
 * and a row that said "both" would be claiming a round trip it does not make.
 *
 * @since 0.5.0
 */
public enum MappingDirection {

    /** Applies to both legs. */
    BOTH,

    /** Zengin to ISO 20022 only. */
    TO_ISO,

    /** ISO 20022 to Zengin only. */
    TO_ZENGIN;

    /** @return true if this row applies when converting Zengin to ISO 20022 */
    public boolean appliesToIso() {
        return this != TO_ZENGIN;
    }

    /** @return true if this row applies when converting ISO 20022 to Zengin */
    public boolean appliesToZengin() {
        return this != TO_ISO;
    }

    /**
     * Parses the value used in the mapping declarations.
     *
     * @param declared {@code both}, {@code to-iso} or {@code to-zengin}
     * @return the direction
     * @throws IllegalArgumentException if the value is not one of those
     */
    public static MappingDirection parse(String declared) {
        return switch (declared) {
            case "both" -> BOTH;
            case "to-iso" -> TO_ISO;
            case "to-zengin" -> TO_ZENGIN;
            default -> throw new IllegalArgumentException(
                    "'" + declared + "' is not a direction: use both, to-iso or to-zengin");
        };
    }
}
