package io.zengin4j.validation.api;

/// How much a finding matters.
///
/// Three levels, and the boundary that carries weight is between
/// [#ERROR] and [#WARNING]: a file with errors will be rejected, a
/// file with only warnings will probably be accepted and may still be wrong.
/// [ValidationReport#isSubmittable()] is defined on exactly that line.
///
/// Severity is a *default*. Institutional practice varies enough that
/// a rule this library calls an error may be routine somewhere, so every rule is
/// suppressible by id (R-V3) and a consumer can re-rank what they disagree with.
///
/// @since 0.2.0
public enum Severity {

    /// The file will be rejected, or will do something wrong if accepted.
    ///
    /// Wrong record lengths, a trailer disagreeing with its contents, a
    /// character the receiving institution cannot accept.
    ERROR,

    /// Legal, and usually a mistake.
    ///
    /// Two identical payments in one batch is the archetype: the format
    /// permits it, most institutions accept it, and it is far more often a
    /// duplicated row than a genuine intent to pay someone twice.
    WARNING,

    /// Worth knowing, not worth acting on by itself.
    ///
    /// Used where the library has done something a reader should be aware of
    /// — an assumption applied, a field it could not check.
    INFO;

    /// Whether this severity stops a file being submittable.
    ///
    /// @return `true` for [#ERROR]
    public boolean blocksSubmission() {
        return this == ERROR;
    }

    /// The SARIF level this maps to.
    ///
    /// SARIF's vocabulary is `error` / `warning` / `note`,
    /// which lines up with these three exactly — one of the reasons SARIF is a
    /// good fit for a report shaped like this (R-V4).
    ///
    /// @return the SARIF level string, never `null`
    public String sarifLevel() {
        return switch (this) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "note";
        };
    }
}
