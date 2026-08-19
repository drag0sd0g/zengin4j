package io.zengin4j.validation.rules;

import module java.base;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.HeaderRecord;
import io.zengin4j.core.time.DateResolution;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.calendar.BeyondCalendarHorizonException;
import io.zengin4j.validation.calendar.BusinessCalendar;
import io.zengin4j.validation.calendar.NonBusinessDay;
import io.zengin4j.validation.engine.ValidationContext;

/// Tier 5 — will funds actually move on the date the file asks for? (§14.3,
/// `V-5xx`)
///
/// Every rule here is skipped when no calendar is supplied. That is R-V6's
/// design: a caller without one gets no calendar findings rather than findings
/// computed from a guess.
///
/// The dates in these files carry no year — they are four digits, `MMDD`
/// — so every rule here rests on the year the resolver attached, and a value
/// date near the December boundary is exactly where the two reasonable
/// strategies disagree. The finding says which date it judged.
///
/// @since 0.2.0
public final class CalendarRules {

    /// How far ahead institutions typically accept an instruction. A month is
    /// the common ceiling; this is a warning either way, because the real limit
    /// is contractual and varies.
    static final int FORWARD_WINDOW_DAYS = 30;

    private CalendarRules() {
    }

    /// Every rule in this tier.
    ///
    /// @return the rules, never `null`
    public static List<Rule> all() {
        return List.of(new ValueDateIsABusinessDay(), new ValueDateWithinForwardWindow());
    }

    /// Resolves a header's yearless date, or empty when there is none to
    /// resolve.
    private static Optional<LocalDate> valueDateOf(ValidationContext context, HeaderRecord header) {
        return header.effectiveDate().flatMap(monthDay -> {
            DateResolution resolution = context.dateResolver().resolve(monthDay);
            return resolution.date();
        });
    }

    /// V-501, V-502, V-503 and V-505 — one classification, four ways of
    /// reporting it. Asking the calendar once and switching on the answer keeps
    /// the rules from disagreeing with each other about the same date.
    static final class ValueDateIsABusinessDay extends AbstractRule {

        ValueDateIsABusinessDay() {
            super("V-501", Severity.ERROR, RuleScope.BATCH,
                    java.util.Set.of("V-501", "V-502", "V-503", "V-505"));
        }

        @Override
        public Severity severityOf(String emittedId) {
            // V-505 says the calendar cannot answer, which is information about
            // this library's data rather than a defect in the caller's file.
            return "V-505".equals(emittedId) ? Severity.INFO : Severity.ERROR;
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            BusinessCalendar calendar = context.calendar().orElse(null);
            if (calendar == null) {
                return;
            }
            for (Batch batch : context.file().batches()) {
                HeaderRecord header = batch.header();
                LocalDate date = valueDateOf(context, header).orElse(null);
                if (date == null) {
                    continue;
                }

                NonBusinessDay classified;
                try {
                    classified = calendar.classify(date);
                } catch (BeyondCalendarHorizonException beyond) {
                    // R-V1: validation reports, it does not raise. The caller
                    // asked what is wrong with the file, and "I cannot tell you
                    // about this date" is an answer worth having.
                    out.accept(Messages.format("V-505.message", date, beyond.horizon())
                            .into(Finding.of(Severity.INFO, "V-505")
                                    .at(header.recordNumber(), header.byteOffset()))
                            .actual(date.toString())
                            .expected("on or before " + beyond.horizon())
                            .build());
                    continue;
                }

                switch (classified.kind()) {
                    case BUSINESS_DAY -> {
                        // nothing to report
                    }
                    case WEEKEND -> out.accept(Messages.format("V-501.message",
                                    date, weekendName(date))
                            .into(Finding.of(Severity.ERROR, "V-501")
                                    .at(header.recordNumber(), header.byteOffset()))
                            .actual(date.toString())
                            .build());
                    case PUBLIC_HOLIDAY -> out.accept(Messages.format("V-502.message",
                                    date, classified.name().orElse("a public holiday"))
                            .into(Finding.of(Severity.ERROR, "V-502")
                                    .at(header.recordNumber(), header.byteOffset()))
                            .actual(date.toString())
                            .build());
                    case YEAR_END_CLOSURE -> out.accept(Messages.format("V-503.message", date)
                            .into(Finding.of(Severity.ERROR, "V-503")
                                    .at(header.recordNumber(), header.byteOffset()))
                            .actual(date.toString())
                            .build());
                    default -> {
                        // exhaustive
                    }
                }
            }
        }

        private static String weekendName(LocalDate date) {
            return date.getDayOfWeek() == DayOfWeek.SATURDAY ? "Saturday" : "Sunday";
        }
    }

    /// V-504.
    static final class ValueDateWithinForwardWindow extends AbstractRule {

        ValueDateWithinForwardWindow() {
            super("V-504", Severity.WARNING, RuleScope.BATCH);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            if (context.calendar().isEmpty()) {
                return;
            }
            LocalDate today = context.dateResolver().reference();
            for (Batch batch : context.file().batches()) {
                HeaderRecord header = batch.header();
                LocalDate date = valueDateOf(context, header).orElse(null);
                if (date == null || !date.isAfter(today)) {
                    continue;
                }
                long ahead = ChronoUnit.DAYS.between(today, date);
                if (ahead > FORWARD_WINDOW_DAYS) {
                    out.accept(Messages.format(id() + ".message",
                                    date, ahead, today, FORWARD_WINDOW_DAYS)
                            .into(finding().at(header.recordNumber(), header.byteOffset()))
                            .actual(date.toString())
                            .expected("within " + FORWARD_WINDOW_DAYS + " days of " + today)
                            .build());
                }
            }
        }
    }
}
