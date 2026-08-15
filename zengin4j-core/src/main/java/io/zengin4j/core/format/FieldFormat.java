package io.zengin4j.core.format;

import io.zengin4j.core.error.FormatDescriptorException;
import java.util.Optional;

/**
 * The optional {@code format} attribute of a field descriptor: a closed
 * vocabulary of interpretations a field may carry beyond its raw bytes.
 *
 * <p>Interpretation is declared, never inferred. A generator that decided
 * "this {@code N(10)} field is called amount, so it must be a monetary value"
 * would eventually meet a ten-digit account number and turn it into a number,
 * losing its leading zeros. Anything not declared here stays raw text.
 *
 * @since 0.1.0
 */
public enum FieldFormat {

    /**
     * Four digits, {@code MMDD}, with no year component. Decoded to
     * {@link java.time.MonthDay}; the year is never invented (R-D9).
     */
    MMDD("MMDD", 4),

    /**
     * A monetary amount in whole yen. Decoded to {@code long}: there is no
     * minor unit, so {@code BigDecimal} would add nothing (R-D6).
     */
    AMOUNT("AMOUNT", 0),

    /** A record count. Decoded to {@code int}. */
    COUNT("COUNT", 0),

    /**
     * コード区分, the encoding indicator. Decoded to
     * {@link io.zengin4j.core.charset.CodeKubun}.
     */
    CODE_KUBUN("CODE-KUBUN", 1);

    private final String descriptorValue;
    private final int requiredLength;

    FieldFormat(String descriptorValue, int requiredLength) {
        this.descriptorValue = descriptorValue;
        this.requiredLength = requiredLength;
    }

    /**
     * Parses a descriptor's {@code format} value.
     *
     * @param raw    the raw value
     * @param origin the descriptor resource name, for diagnostics
     * @return the format
     * @throws FormatDescriptorException if the value is not one of the
     *                                   supported interpretations
     */
    public static FieldFormat parse(String raw, String origin) {
        for (FieldFormat candidate : values()) {
            if (candidate.descriptorValue.equals(raw)) {
                return candidate;
            }
        }
        throw FormatDescriptorException.forResource(origin,
                "unknown field format '" + raw + "'; supported: " + supportedValues());
    }

    /**
     * Returns a comma-separated list of the supported descriptor values.
     *
     * @return the supported values, for diagnostics
     */
    public static String supportedValues() {
        StringBuilder result = new StringBuilder();
        for (FieldFormat candidate : values()) {
            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append(candidate.descriptorValue);
        }
        return result.toString();
    }

    /**
     * Returns the value this format is written as in a descriptor.
     *
     * @return the descriptor value
     */
    public String descriptorValue() {
        return descriptorValue;
    }

    /**
     * Returns the field length this interpretation requires, if it fixes one.
     *
     * @return the required length, or empty if any length is acceptable
     */
    public Optional<Integer> requiredLength() {
        return requiredLength == 0 ? Optional.empty() : Optional.of(requiredLength);
    }
}
