package io.zengin4j.validation.calendar;

import module java.base;

/// Which days financial institutions move money on (R-V6).
///
/// Optional. Every rule that needs one is skipped when none is supplied, and
/// the rest of validation is unaffected — a caller with no calendar gets no
/// calendar findings rather than unreliable ones.
///
/// @since 0.2.0
public interface BusinessCalendar {

    /// Whether funds move on a date.
    ///
    /// @param date the date to test
    /// @return `true` if it is a financial-institution business day
    /// @throws BeyondCalendarHorizonException if the date is past
    ///   [#validUntil()]
    boolean isBankBusinessDay(LocalDate date);

    /// The next business day on or after a date.
    ///
    /// @param date the date to start from
    /// @return the first business day on or after it
    /// @throws BeyondCalendarHorizonException if the search passes
    ///   [#validUntil()]
    LocalDate nextBusinessDay(LocalDate date);

    /// The last date this calendar can answer for (R-V7).
    ///
    /// Japanese public holidays are **announced, not computed**.
    /// The equinox holidays depend on an astronomical determination published by
    /// the Cabinet Office in February of the preceding year, so no algorithm can
    /// produce them reliably for an arbitrary future year. A calendar that
    /// extrapolated past its data would be confidently wrong on exactly the days
    /// a payment file is most likely to be scheduled near.
    ///
    /// @return the last date this calendar covers, never `null`
    LocalDate validUntil();

    /// Why a date is not a business day, for a finding that says something
    /// useful.
    ///
    /// @param date the date to describe
    /// @return the reason, never `null`
    NonBusinessDay classify(LocalDate date);
}
