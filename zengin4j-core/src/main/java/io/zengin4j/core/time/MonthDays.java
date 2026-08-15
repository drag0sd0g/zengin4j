package io.zengin4j.core.time;

import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.Optional;

/**
 * Conversion between the four-digit {@code MMDD} field encoding and
 * {@link MonthDay}.
 *
 * @since 0.1.0
 */
public final class MonthDays {

    /** Length in bytes of an {@code MMDD} field. */
    public static final int FIELD_LENGTH = 4;

    private MonthDays() {
    }

    /**
     * Parses an {@code MMDD} field.
     *
     * <p>Returns empty rather than throwing for every unusable value: the
     * all-zeros form that means "not specified", a month or day out of range,
     * and non-digit content. A date field a bank filled in wrongly is data,
     * not a programming error, and the raw content stays available on the
     * record so a validation rule can report exactly what was there.
     *
     * @param raw the raw field content
     * @return the month and day, or empty if the content is not a valid
     *         {@code MMDD} value
     */
    public static Optional<MonthDay> parse(String raw) {
        if (raw == null || raw.length() != FIELD_LENGTH) {
            return Optional.empty();
        }
        for (int i = 0; i < FIELD_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                return Optional.empty();
            }
        }
        int month = (raw.charAt(0) - '0') * 10 + (raw.charAt(1) - '0');
        int day = (raw.charAt(2) - '0') * 10 + (raw.charAt(3) - '0');
        try {
            // MonthDay.of accepts 29 February; whether a given year has one is
            // a resolution question, not a parsing question (R-D12).
            return Optional.of(MonthDay.of(month, day));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Renders a month and day as an {@code MMDD} field value.
     *
     * @param monthDay the month and day
     * @return four digits
     */
    public static String format(MonthDay monthDay) {
        return String.format("%02d%02d", monthDay.getMonthValue(), monthDay.getDayOfMonth());
    }
}
