package io.zengin4j.core.format;

import java.util.Objects;

/**
 * Stable identifier for a format descriptor, for example
 * {@code sougou-furikomi}.
 *
 * <p>Deliberately not an enum. Consumers register descriptors for
 * institution-specific variants at runtime (R-F6, R-X1), and an enum would
 * force them to fork the library to name one.
 *
 * @param value the identifier: lower-case ASCII letters, digits and hyphens
 * @since 0.1.0
 */
public record FormatId(String value) implements Comparable<FormatId> {
    /**
     * Validates the identifier.
     *
     * @throws IllegalArgumentException if the value is blank or contains
     *                                  characters outside {@code [a-z0-9-]}
     */
    public FormatId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("format id must not be blank");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean permitted = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-';
            if (!permitted) {
                throw new IllegalArgumentException(
                        "format id '" + value + "' contains an unsupported character '" + c
                                + "'; use lower-case letters, digits and hyphens");
            }
        }
    }

    /**
     * Creates an identifier.
     *
     * @param value the identifier text
     * @return the identifier
     * @throws IllegalArgumentException if the value is not a valid identifier
     */
    public static FormatId of(String value) {
        return new FormatId(value);
    }

    /**
     * Returns the identifier in upper camel case, for use in generated type
     * names: {@code sougou-furikomi} becomes {@code SougouFurikomi}.
     *
     * @return the camel-case form
     */
    public String toTypeNamePrefix() {
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalise = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '-') {
                capitalise = true;
                continue;
            }
            result.append(capitalise ? Character.toUpperCase(c) : c);
            capitalise = false;
        }
        return result.toString();
    }

    @Override
    public int compareTo(FormatId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
