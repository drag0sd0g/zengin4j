package io.zengin4j.core.kana;

import io.zengin4j.core.loss.LossSeverity;
import java.util.Objects;

/**
 * A character the field rules refuse, and what is written instead.
 *
 * <p>Declared as data in {@code zengin4j-core/kana/kana-substitutions.yaml} and
 * compiled into {@code KanaTables} at build time. These are the judgement calls
 * — somebody read the standard's field rules and decided that a long vowel is
 * written as a hyphen and a small kana full size. The mechanical
 * full-width/half-width correspondence around them is derived from Unicode and
 * is not in that file.
 *
 * @param replacement what to write instead
 * @param severity    how much the substitution matters
 * @param whyEn       why the original is refused, in English
 * @param whyJa       why the original is refused, in Japanese
 * @since 0.4.0
 */
public record KanaSubstitution(String replacement, LossSeverity severity, String whyEn, String whyJa) {

    /**
     * Validates the components.
     */
    public KanaSubstitution {
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(whyEn, "whyEn");
        Objects.requireNonNull(whyJa, "whyJa");
    }
}
