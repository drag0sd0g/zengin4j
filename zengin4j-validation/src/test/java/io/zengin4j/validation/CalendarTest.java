package io.zengin4j.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.time.MonthDayResolver;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import io.zengin4j.validation.calendar.BeyondCalendarHorizonException;
import io.zengin4j.validation.calendar.JapaneseBankCalendar;
import io.zengin4j.validation.calendar.NonBusinessDay;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The business calendar (R-V6, R-V7) and the {@code V-5xx} rules.
 *
 * <p>The dates asserted here are the ones a formula gets wrong. The equinoxes
 * move, the substitute holidays depend on which weekday a fixed holiday fell
 * on, and 2 January is closed without being a holiday at all.
 */
class CalendarTest {
    private final JapaneseBankCalendar calendar = JapaneseBankCalendar.bundled();

    /**
     * The two holidays no algorithm should be trusted with. Their dates come
     * from an astronomical determination the Cabinet Office publishes in
     * February of the preceding year.
     */
    @Test
    void knowsTheMovingEquinoxHolidays() {
        assertThat(calendar.holidayName(LocalDate.of(2026, 3, 20))).contains("春分の日");
        assertThat(calendar.holidayName(LocalDate.of(2026, 9, 23))).contains("秋分の日");
        assertThat(calendar.holidayName(LocalDate.of(2027, 3, 21))).contains("春分の日");
        assertThat(calendar.holidayName(LocalDate.of(2027, 9, 23))).contains("秋分の日");

        assertThat(LocalDate.of(2026, 3, 20).getDayOfMonth())
                .isNotEqualTo(LocalDate.of(2027, 3, 21).getDayOfMonth());
    }

    /** 振替休日: 2027-03-21 is a Sunday, so the Monday after is closed too. */
    @Test
    void knowsSubstituteHolidays() {
        assertThat(LocalDate.of(2027, 3, 21).getDayOfWeek())
                .isEqualTo(java.time.DayOfWeek.SUNDAY);

        assertThat(calendar.classify(LocalDate.of(2027, 3, 22)).kind())
                .isEqualTo(NonBusinessDay.Kind.PUBLIC_HOLIDAY);
        assertThat(calendar.isBankBusinessDay(LocalDate.of(2027, 3, 22))).isFalse();
    }

    /** 国民の休日: the weekday caught between two holidays. */
    @Test
    void knowsBridgeHolidays() {
        assertThat(calendar.classify(LocalDate.of(2026, 9, 22)).kind())
                .isEqualTo(NonBusinessDay.Kind.PUBLIC_HOLIDAY);
    }

    /**
     * The year-end closure. 2 and 3 January are not public holidays and
     * financial institutions are shut anyway — which is the part a
     * holidays-only calendar gets wrong.
     */
    @Test
    void knowsTheYearEndClosureIsNotAHoliday() {
        assertThat(calendar.holidayName(LocalDate.of(2026, 1, 2)))
                .as("2 January is not a public holiday")
                .isEmpty();

        assertThat(calendar.classify(LocalDate.of(2026, 1, 2)).kind())
                .isEqualTo(NonBusinessDay.Kind.YEAR_END_CLOSURE);
        assertThat(calendar.classify(LocalDate.of(2025, 12, 31)).kind())
                .isEqualTo(NonBusinessDay.Kind.YEAR_END_CLOSURE);
        assertThat(calendar.isBankBusinessDay(LocalDate.of(2026, 1, 5)))
                .as("5 January 2026 is a Monday and open")
                .isTrue();
    }

    @Test
    void knowsWeekendsAndOrdinaryDays() {
        assertThat(calendar.classify(LocalDate.of(2026, 8, 15)).kind())
                .isEqualTo(NonBusinessDay.Kind.WEEKEND);
        assertThat(calendar.isBankBusinessDay(LocalDate.of(2026, 8, 17))).isTrue();
    }

    @Test
    void findsTheNextBusinessDayAcrossAHolidayWeekend() {
        assertThat(calendar.nextBusinessDay(LocalDate.of(2026, 5, 3)))
                .isEqualTo(LocalDate.of(2026, 5, 7));
    }

    @Test
    void refusesToAnswerPastItsHorizon() {
        LocalDate horizon = calendar.validUntil();
        assertThat(horizon).isAfterOrEqualTo(LocalDate.of(2027, 12, 31));

        assertThatExceptionOfType(BeyondCalendarHorizonException.class)
                .isThrownBy(() -> calendar.isBankBusinessDay(horizon.plusDays(1)))
                .satisfies(thrown -> {
                    assertThat(thrown.horizon()).isEqualTo(horizon);
                    assertThat(thrown.getMessage())
                            .contains("announced in advance rather than computed");
                });
    }

    private ValidationReport validateWithValueDate(int month, int day, LocalDate today) {
        byte[] header = KIT_HEADER(month, day);
        byte[] file = SyntheticRecords.file(
                List.of(header, Fixtures.TESTKIT.data(),
                        Fixtures.TESTKIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                        Fixtures.TESTKIT.end()),
                SeparatorStyle.CRLF, false);
        return ZenginValidator.builder()
                .withCalendar(calendar)
                .withDateResolver(MonthDayResolver.forwardLooking(today))
                .build()
                .validate(Fixtures.read(file));
    }

    /** A header whose 振込指定日 is the given month and day. */
    private static byte[] KIT_HEADER(int month, int day) {
        byte[] header = Fixtures.TESTKIT.header();
        String mmdd = String.format("%02d%02d", month, day);
        for (int i = 0; i < 4; i++) {
            header[54 + i] = (byte) mmdd.charAt(i);
        }
        return header;
    }

    @Test
    void v501_reportsAValueDateOnAWeekend() {
        ValidationReport report = validateWithValueDate(8, 15, LocalDate.of(2026, 8, 1));

        assertThat(report.findingsOf("V-501")).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.ERROR);
            assertThat(finding.messageEn()).contains("Saturday");
        });
    }

    @Test
    void v502_reportsAValueDateOnAPublicHoliday() {
        ValidationReport report = validateWithValueDate(11, 3, LocalDate.of(2026, 10, 20));

        assertThat(report.findingsOf("V-502")).singleElement().satisfies(finding ->
                assertThat(finding.messageEn()).contains("文化の日"));
    }

    /**
     * 2 January 2026 is a Friday — a working day by every ordinary measure, and
     * closed. A calendar that only knew about weekends and public holidays
     * would pass this date, which is the whole reason the closure is modelled
     * separately.
     */
    @Test
    void v503_reportsAValueDateInTheYearEndClosure() {
        assertThat(LocalDate.of(2026, 1, 2).getDayOfWeek())
                .isEqualTo(java.time.DayOfWeek.FRIDAY);

        ValidationReport report = validateWithValueDate(1, 2, LocalDate.of(2025, 12, 20));

        assertThat(report.findingsOf("V-503")).hasSize(1);
        assertThat(report.findingsOf("V-501")).as("not reported as a weekend").isEmpty();
    }

    @Test
    void staysQuietOnAnOrdinaryBusinessDay() {
        ValidationReport report = validateWithValueDate(8, 17, LocalDate.of(2026, 8, 1));

        assertThat(report.findingsOf("V-501")).isEmpty();
        assertThat(report.findingsOf("V-502")).isEmpty();
        assertThat(report.findingsOf("V-503")).isEmpty();
    }

    @Test
    void v504_warnsAboutADateTooFarAhead() {
        ValidationReport report = validateWithValueDate(12, 15, LocalDate.of(2026, 8, 17));

        assertThat(report.findingsOf("V-504")).singleElement().satisfies(finding ->
                assertThat(finding.severity()).isEqualTo(Severity.WARNING));
    }

    /** R-V1: past the horizon the rule reports, and validation still returns. */
    @Test
    void v505_reportsRatherThanThrowsPastTheHorizon() {
        LocalDate afterHorizon = calendar.validUntil().plusYears(1);
        ValidationReport report = validateWithValueDate(6, 15, afterHorizon);

        assertThat(report.findingsOf("V-505")).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.INFO);
            assertThat(finding.messageEn()).contains("horizon");
        });
        assertThat(report.findingsOf("V-501")).isEmpty();
    }

    /**
     * R-V3, on a composite rule. V-501 and V-502 come from one classification;
     * suppressing the holiday finding must leave the weekend one working.
     */
    @Test
    void oneOfTheCalendarFindingsCanBeSuppressedWithoutTheOthers() {
        byte[] holiday = SyntheticRecords.file(
                List.of(KIT_HEADER(11, 3), Fixtures.TESTKIT.data(),
                        Fixtures.TESTKIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                        Fixtures.TESTKIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport suppressed = ZenginValidator.builder()
                .withCalendar(calendar)
                .withDateResolver(MonthDayResolver.forwardLooking(LocalDate.of(2026, 10, 20)))
                .suppress("V-502")
                .build()
                .validate(Fixtures.read(holiday));

        assertThat(suppressed.findingsOf("V-502")).isEmpty();

        ValidationReport weekend = ZenginValidator.builder()
                .withCalendar(calendar)
                .withDateResolver(MonthDayResolver.forwardLooking(LocalDate.of(2026, 8, 1)))
                .suppress("V-502")
                .build()
                .validate(Fixtures.read(SyntheticRecords.file(
                        List.of(KIT_HEADER(8, 15), Fixtures.TESTKIT.data(),
                                Fixtures.TESTKIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                                Fixtures.TESTKIT.end()),
                        SeparatorStyle.CRLF, false)));

        assertThat(weekend.findingsOf("V-501"))
                .as("suppressing V-502 must not disable V-501")
                .isNotEmpty();
    }

    /** R-V6: with no calendar, no calendar findings — not unreliable ones. */
    @Test
    void withoutACalendarNoCalendarRulesRun() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT_HEADER(8, 15), Fixtures.TESTKIT.data(),
                        Fixtures.TESTKIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                        Fixtures.TESTKIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = ZenginValidator.defaults().validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-501")).isEmpty();
        assertThat(report.findingsOf("V-504")).isEmpty();
    }
}
