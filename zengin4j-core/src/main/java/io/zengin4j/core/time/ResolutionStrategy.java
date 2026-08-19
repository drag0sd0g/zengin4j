package io.zengin4j.core.time;

/// How to choose a year for an `MMDD` field that carries none.
///
/// **Neither strategy is universally correct**, which is why
/// the choice is an explicit argument rather than a default (R-D11).
///
/// A file produced on 28 December carrying value date `0105` means
/// *next* January: [#FORWARD_LOOKING] gets that right and
/// [#NEAREST] gets it right too. A result file produced on 5 January
/// carrying `1228` means *previous* December:
/// [#FORWARD_LOOKING] places it eleven months in the future, and only
/// [#NEAREST] gets it right. Instruction files look forward; result files
/// look back.
///
/// @since 0.1.0
public enum ResolutionStrategy {

    /// The next occurrence at or after the reference date. Appropriate for
    /// instruction files, where the value date has not happened yet.
    FORWARD_LOOKING,

    /// The occurrence closest to the reference date, considering the previous,
    /// current and next year. Appropriate for result and notification files,
    /// where the date has usually just passed.
    NEAREST
}
