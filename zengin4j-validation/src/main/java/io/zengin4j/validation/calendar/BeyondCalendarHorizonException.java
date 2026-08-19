package io.zengin4j.validation.calendar;

import module java.base;

/// A date was asked about that lies past the calendar's data (R-V7).
///
/// Thrown by the calendar, and never by validation: the calendar rules catch
/// it and report a `V-505` finding, because R-V1 says validation returns a
/// report rather than raising. A caller using the calendar directly gets the
/// exception, which is the right shape for a question that cannot be answered.
///
/// Guessing would be the alternative, and would be worse. Japanese public
/// holidays are announced rather than computed; a calendar that extrapolated
/// would be confidently wrong about the equinoxes and about any holiday
/// legislation passed since its data was captured.
///
/// @since 0.2.0
public final class BeyondCalendarHorizonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient LocalDate requested;
    private final transient LocalDate horizon;

    /// Creates the diagnostic.
    ///
    /// @param requested the date that was asked about
    /// @param horizon   the last date the calendar covers
    public BeyondCalendarHorizonException(LocalDate requested, LocalDate horizon) {
        super("this calendar covers up to " + horizon + "; " + requested + " is beyond it."
                + " Japanese public holidays are announced in advance rather than computed,"
                + " so answering would mean guessing. Supply a calendar with later data.");
        this.requested = requested;
        this.horizon = horizon;
    }

    /// The date that was asked about.
    ///
    /// @return the date, never `null`
    public LocalDate requested() {
        return requested;
    }

    /// The last date the calendar covers.
    ///
    /// @return the horizon, never `null`
    public LocalDate horizon() {
        return horizon;
    }
}
