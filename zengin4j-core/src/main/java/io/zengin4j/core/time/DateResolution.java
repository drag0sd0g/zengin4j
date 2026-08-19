package io.zengin4j.core.time;

import module java.base;

/// The outcome of attaching a year to an `MMDD` value.
///
/// Carries the inputs alongside the result so that a caller — or a mapping
/// loss entry — can state not just which date was chosen but on what basis
/// (§20.3).
///
/// @param input          the month and day being resolved
/// @param strategy       the strategy applied
/// @param reference      the reference date the strategy was applied against
/// @param date           the resolved date, or empty if no candidate year
///   contains the month and day
/// @param candidateYears the years considered, in the order considered
/// @param reason         why resolution failed, present exactly when
///   `date` is empty
/// @since 0.1.0
public record DateResolution(
        MonthDay input,
        ResolutionStrategy strategy,
        LocalDate reference,
        Optional<LocalDate> date,
        List<Integer> candidateYears,
        Optional<UnresolvedReason> reason) {

    /// Validates and defensively copies the components.
    public DateResolution {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(reason, "reason");
        candidateYears = List.copyOf(Objects.requireNonNull(candidateYears, "candidateYears"));
        if (date.isPresent() == reason.isPresent()) {
            throw new IllegalArgumentException("a resolution carries either a date or a reason, never both or neither");
        }
    }

    /// Reports whether a year could be attached.
    ///
    /// @return `true` if [#date()] is present
    public boolean isResolved() {
        return date.isPresent();
    }

    /// Describes the outcome in English, for diagnostics and loss reports.
    ///
    /// @return a one-line explanation
    public String explain() {
        String monthDay = MonthDays.format(input);
        return date
                .map(resolved -> monthDay + " resolved to " + resolved + " by " + strategy
                        + " from reference date " + reference)
                .orElseGet(() -> monthDay + " could not be resolved by " + strategy
                        + " from reference date " + reference + ": " + reason.orElseThrow().explanation()
                        + " (candidate years " + candidateYears + ")");
    }

    /// Why an `MMDD` value could not be given a year.
    ///
    /// @since 0.1.0
    public enum UnresolvedReason {

        /// The value is 29 February and no candidate year is a leap year. The
        /// library reports this rather than silently moving the date to the
        /// 28th or the 1st (R-D12).
        LEAP_DAY_NOT_IN_CANDIDATE_YEAR("29 February does not occur in any candidate year");

        private final String explanation;

        UnresolvedReason(String explanation) {
            this.explanation = explanation;
        }

        /// Returns a human-readable explanation.
        ///
        /// @return the explanation
        public String explanation() {
            return explanation;
        }
    }
}
