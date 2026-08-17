package io.zengin4j.validation.api;

/**
 * How much a finding matters.
 *
 * <p>Three levels, and the boundary that carries weight is between
 * {@link #ERROR} and {@link #WARNING}: a file with errors will be rejected, a
 * file with only warnings will probably be accepted and may still be wrong.
 * {@link ValidationReport#isSubmittable()} is defined on exactly that line.
 *
 * <p>Severity is a <em>default</em>. Institutional practice varies enough that
 * a rule this library calls an error may be routine somewhere, so every rule is
 * suppressible by id (R-V3) and a consumer can re-rank what they disagree with.
 *
 * @since 0.2.0
 */
public enum Severity {
    /**
     * The file will be rejected, or will do something wrong if accepted.
     *
     * <p>Wrong record lengths, a trailer disagreeing with its contents, a
     * character the receiving institution cannot accept.
     */
    ERROR,

    /**
     * Legal, and usually a mistake.
     *
     * <p>Two identical payments in one batch is the archetype: the format
     * permits it, most institutions accept it, and it is far more often a
     * duplicated row than a genuine intent to pay someone twice.
     */
    WARNING,

    /**
     * Worth knowing, not worth acting on by itself.
     *
     * <p>Used where the library has done something a reader should be aware of
     * — an assumption applied, a field it could not check.
     */
    INFO;

    /**
     * Whether this severity stops a file being submittable.
     *
     * @return {@code true} for {@link #ERROR}
     */
    public boolean blocksSubmission() {
        return this == ERROR;
    }

    /**
     * The SARIF level this maps to.
     *
     * <p>SARIF's vocabulary is {@code error} / {@code warning} / {@code note},
     * which lines up with these three exactly — one of the reasons SARIF is a
     * good fit for a report shaped like this (R-V4).
     *
     * @return the SARIF level string, never {@code null}
     */
    public String sarifLevel() {
        return switch (this) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "note";
        };
    }
}
