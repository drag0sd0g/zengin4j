/// Structured validation of Zengin files: rules, findings and reports.
///
/// **Validation never throws** (R-V1). It returns a report,
/// because malformed third-party files are this module's expected input rather
/// than an exceptional condition. A validator that failed on a broken file would
/// be useless for the one job it has.
///
/// Depends only on `io.zengin4j.core` (R-M2) — no JSON library, which
/// is why the JSON and SARIF writers here are hand-written. Emitting a
/// well-specified format is not the same problem as parsing an arbitrary one;
/// see ADR-0022.
///
/// @since 0.2.0
module io.zengin4j.validation {

    requires transitive io.zengin4j.core;

    exports io.zengin4j.validation;
    exports io.zengin4j.validation.api;
    exports io.zengin4j.validation.calendar;
    exports io.zengin4j.validation.engine;
    exports io.zengin4j.validation.refdata;
    exports io.zengin4j.validation.rules;
}
