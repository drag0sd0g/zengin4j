package io.zengin4j.core.loss;

import java.util.Objects;
import java.util.Optional;

/**
 * One thing a conversion changed or discarded, and why (P5).
 *
 * <p>Every lossy operation in this library produces one of these. That is the
 * whole point of the type: a conversion that quietly shortened a name is
 * indistinguishable, in its output, from one that did not — so the record of
 * what happened has to travel alongside the result rather than in a log
 * somebody may not read.
 *
 * <p>Carries both the original and the resulting value, because "truncated" on
 * its own does not tell a reader whether the part that went missing mattered.
 *
 * @param kind            what sort of loss it was
 * @param severity        how much it matters
 * @param sourcePath      where the value came from, where that is meaningful
 * @param targetField     where it was going, where that is meaningful
 * @param originalValue   the value before the operation
 * @param resultingValue  the value after it, empty when the value was dropped
 * @param explanationEn   what happened, in English
 * @param explanationJa   what happened, in Japanese
 * @since 0.4.0
 */
public record LossEntry(
        LossKind kind,
        LossSeverity severity,
        Optional<String> sourcePath,
        Optional<String> targetField,
        String originalValue,
        String resultingValue,
        String explanationEn,
        String explanationJa) {
    /**
     * Validates the components.
     */
    public LossEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(targetField, "targetField");
        Objects.requireNonNull(originalValue, "originalValue");
        Objects.requireNonNull(resultingValue, "resultingValue");
        Objects.requireNonNull(explanationEn, "explanationEn");
        Objects.requireNonNull(explanationJa, "explanationJa");
    }

    /**
     * Creates an entry with no source or target path.
     *
     * <p>The shape transliteration uses: it converts a string, and there is no
     * document path involved. The mapping layer supplies paths through
     * {@link #at(String, String)}.
     *
     * @param kind           what sort of loss it was
     * @param severity       how much it matters
     * @param originalValue  the value before
     * @param resultingValue the value after
     * @param explanationEn  what happened, in English
     * @param explanationJa  what happened, in Japanese
     * @return the entry
     */
    public static LossEntry of(LossKind kind, LossSeverity severity, String originalValue,
            String resultingValue, String explanationEn, String explanationJa) {
        return new LossEntry(kind, severity, Optional.empty(), Optional.empty(),
                originalValue, resultingValue, explanationEn, explanationJa);
    }

    /**
     * Returns a copy of this entry located at a source and target path.
     *
     * @param source the path the value came from
     * @param target the field it was going to
     * @return a located copy
     */
    public LossEntry at(String source, String target) {
        return new LossEntry(kind, severity,
                Optional.ofNullable(source), Optional.ofNullable(target),
                originalValue, resultingValue, explanationEn, explanationJa);
    }

    /**
     * Whether this entry is at least as severe as the given level.
     *
     * @param threshold the level to compare against
     * @return {@code true} if this entry meets or exceeds it
     */
    public boolean isAtLeast(LossSeverity threshold) {
        Objects.requireNonNull(threshold, "threshold");
        return severity.compareTo(threshold) >= 0;
    }

    /**
     * A one-line rendering, in English.
     *
     * @return the line, never {@code null}
     */
    public String toLine() {
        StringBuilder line = new StringBuilder()
                .append(severity).append(' ').append(kind);
        targetField.ifPresent(field -> line.append(" [").append(field).append(']'));
        return line.append(": ").append(explanationEn).toString();
    }

    @Override
    public String toString() {
        return toLine();
    }
}
