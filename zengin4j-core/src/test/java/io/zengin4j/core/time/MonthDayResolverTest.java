package io.zengin4j.core.time;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// §11.2 and §19.4: the year the file does not carry.
class MonthDayResolverTest {

    private static final LocalDate LATE_DECEMBER = LocalDate.of(2026, 12, 28);
    private static final LocalDate EARLY_JANUARY = LocalDate.of(2027, 1, 5);

    @Test
    void parsesAndFormatsTheFieldEncoding() {
        assertThat(MonthDays.parse("0930")).contains(MonthDay.of(9, 30));
        assertThat(MonthDays.format(MonthDay.of(9, 30))).isEqualTo("0930");
        assertThat(MonthDays.format(MonthDay.of(1, 1))).isEqualTo("0101");
    }

    @Test
    void treatsUnusableFieldContentAsAbsentRatherThanGuessing() {
        assertThat(MonthDays.parse("0000")).isEmpty();
        assertThat(MonthDays.parse("1332")).isEmpty();
        assertThat(MonthDays.parse("0230")).isEmpty();
        assertThat(MonthDays.parse("09 0")).isEmpty();
        assertThat(MonthDays.parse("093")).isEmpty();
        assertThat(MonthDays.parse(null)).isEmpty();
        assertThat(MonthDays.parse("0229")).contains(MonthDay.of(2, 29));
    }

    @Test
    void forwardLookingChoosesTheNextOccurrence() {
        MonthDayResolver resolver = MonthDayResolver.forwardLooking(LocalDate.of(2026, 6, 1));

        assertThat(resolver.resolve(MonthDay.of(9, 30)).date()).contains(LocalDate.of(2026, 9, 30));
        assertThat(resolver.resolve(MonthDay.of(3, 1)).date()).contains(LocalDate.of(2027, 3, 1));
        assertThat(resolver.resolve(MonthDay.of(6, 1)).date()).contains(LocalDate.of(2026, 6, 1));
        assertThat(resolver.strategy()).isEqualTo(ResolutionStrategy.FORWARD_LOOKING);
        assertThat(resolver.reference()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    /// The December–January hazard of §19.4, in both directions. An instruction
    /// file written on 28 December for 5 January means next year, and a result
    /// file written on 5 January about 28 December means last year. No single
    /// strategy is right for both, which is why the API makes the choice
    /// unavoidable (R-D11).
    @Test
    void theTwoStrategiesDisagreeAcrossTheYearBoundary() {
        assertThat(MonthDayResolver.forwardLooking(LATE_DECEMBER).resolve(MonthDay.of(1, 5)).date())
                .contains(LocalDate.of(2027, 1, 5));
        assertThat(MonthDayResolver.nearest(LATE_DECEMBER).resolve(MonthDay.of(1, 5)).date())
                .contains(LocalDate.of(2027, 1, 5));

        assertThat(MonthDayResolver.forwardLooking(EARLY_JANUARY).resolve(MonthDay.of(12, 28)).date())
                .contains(LocalDate.of(2027, 12, 28));
        assertThat(MonthDayResolver.nearest(EARLY_JANUARY).resolve(MonthDay.of(12, 28)).date())
                .contains(LocalDate.of(2026, 12, 28));
    }

    @Test
    void nearestConsidersThreeYears() {
        MonthDayResolver resolver = MonthDayResolver.of(ResolutionStrategy.NEAREST, LocalDate.of(2026, 7, 1));

        assertThat(resolver.resolve(MonthDay.of(7, 2)).candidateYears()).containsExactly(2025, 2026, 2027);
        assertThat(resolver.resolve(MonthDay.of(7, 2)).date()).contains(LocalDate.of(2026, 7, 2));
    }

    /// R-D12: 29 February is reported, never moved to the 28th or the 1st.
    @Test
    void reportsALeapDayThatNoCandidateYearHas() {
        DateResolution resolution = MonthDayResolver.forwardLooking(LocalDate.of(2026, 1, 1))
                .resolve(MonthDay.of(2, 29));

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.date()).isEmpty();
        assertThat(resolution.reason())
                .contains(DateResolution.UnresolvedReason.LEAP_DAY_NOT_IN_CANDIDATE_YEAR);
        assertThat(resolution.candidateYears()).containsExactly(2026, 2027);
        assertThat(resolution.explain())
                .contains("0229")
                .contains("29 February does not occur in any candidate year");
    }

    @Test
    void resolvesALeapDayWhenACandidateYearHasOne() {
        assertThat(MonthDayResolver.forwardLooking(LocalDate.of(2027, 6, 1)).resolve(MonthDay.of(2, 29)).date())
                .contains(LocalDate.of(2028, 2, 29));
        assertThat(MonthDayResolver.nearest(LocalDate.of(2025, 1, 1)).resolve(MonthDay.of(2, 29)).date())
                .contains(LocalDate.of(2024, 2, 29));
    }

    /// A leap day in the past is not silently converted into a future date.
    @Test
    void doesNotResolveALeapDayBackwardsWhenLookingForward() {
        DateResolution resolution = MonthDayResolver.forwardLooking(LocalDate.of(2028, 6, 1))
                .resolve(MonthDay.of(2, 29));

        assertThat(resolution.isResolved()).isFalse();
        assertThat(resolution.reason())
                .contains(DateResolution.UnresolvedReason.LEAP_DAY_NOT_IN_CANDIDATE_YEAR);
    }

    @Test
    void explainsAResolvedDate() {
        DateResolution resolution = MonthDayResolver.forwardLooking(LocalDate.of(2026, 6, 1))
                .resolve(MonthDay.of(9, 30));

        assertThat(resolution.explain())
                .contains("0930")
                .contains("2026-09-30")
                .contains("FORWARD_LOOKING");
    }
}
