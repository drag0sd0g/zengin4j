package io.zengin4j.core.format;

import java.util.Objects;
import java.util.Optional;

/**
 * One entry of a code list.
 *
 * @param code     the raw field content this entry matches, for example
 *                 {@code "1"}
 * @param nameJa   the Japanese name
 * @param nameEn   the English gloss
 * @param verified whether this specific value has been confirmed against
 *                 published sources (R-0.1); values transcribed from a single
 *                 working draft are {@code false}
 * @param note     an optional remark, typically recording what remains to be
 *                 confirmed
 * @since 0.1.0
 */
public record CodeValue(String code, String nameJa, String nameEn, boolean verified, Optional<String> note) {
    /**
     * Validates the components.
     */
    public CodeValue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(nameJa, "nameJa");
        Objects.requireNonNull(nameEn, "nameEn");
        Objects.requireNonNull(note, "note");
        if (code.isEmpty()) {
            throw new IllegalArgumentException("code value must not be empty");
        }
    }
}
