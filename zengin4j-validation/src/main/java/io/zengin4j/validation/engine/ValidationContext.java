package io.zengin4j.validation.engine;

import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.time.MonthDayResolver;
import io.zengin4j.validation.calendar.BusinessCalendar;
import io.zengin4j.validation.refdata.ReferenceDataProvider;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a rule is allowed to look at.
 *
 * <p>Two of the four are optional, and that is the point of R-V5 and R-V6: the
 * library validates fully without a reference-data provider or a calendar, and
 * the rules that need them simply do not run. A consumer with no bank-code
 * dataset still gets every structural, syntax and consistency finding — they do
 * not get a degraded version of the reference-data ones, they get none, which
 * is honest.
 *
 * @since 0.2.0
 */
public final class ValidationContext {

    private final ZenginFile file;
    private final FormatDescriptor descriptor;
    private final MonthDayResolver dateResolver;
    private final Optional<BusinessCalendar> calendar;
    private final Optional<ReferenceDataProvider> referenceData;
    private final boolean unmaskSensitiveValues;

    /**
     * Builds a context.
     *
     * @param file                  the file under validation
     * @param descriptor            the layout it was read with
     * @param dateResolver          how to attach a year to MMDD dates
     * @param calendar              a business calendar, if the caller has one
     * @param referenceData         a reference-data provider, if the caller has one
     * @param unmaskSensitiveValues whether to show account numbers in full
     * @return the context
     */
    public static ValidationContext create(
            ZenginFile file,
            FormatDescriptor descriptor,
            MonthDayResolver dateResolver,
            Optional<BusinessCalendar> calendar,
            Optional<ReferenceDataProvider> referenceData,
            boolean unmaskSensitiveValues) {
        return new ValidationContext(
                file, descriptor, dateResolver, calendar, referenceData, unmaskSensitiveValues);
    }

    private ValidationContext(
            ZenginFile file,
            FormatDescriptor descriptor,
            MonthDayResolver dateResolver,
            Optional<BusinessCalendar> calendar,
            Optional<ReferenceDataProvider> referenceData,
            boolean unmaskSensitiveValues) {
        this.file = Objects.requireNonNull(file, "file");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.dateResolver = Objects.requireNonNull(dateResolver, "dateResolver");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.referenceData = Objects.requireNonNull(referenceData, "referenceData");
        this.unmaskSensitiveValues = unmaskSensitiveValues;
    }

    /**
     * The file under validation.
     *
     * @return the file, never {@code null}
     */
    public ZenginFile file() {
        return file;
    }

    /**
     * The layout the file was read with.
     *
     * @return the descriptor, never {@code null}
     */
    public FormatDescriptor descriptor() {
        return descriptor;
    }

    /**
     * How to attach a year to the yearless {@code MMDD} dates.
     *
     * <p>Needed by the calendar rules, and an explicit choice rather than a
     * default: the two reasonable strategies disagree across the
     * December–January boundary, which is exactly when a value date is most
     * likely to be near a bank holiday.
     *
     * @return the resolver, never {@code null}
     */
    public MonthDayResolver dateResolver() {
        return dateResolver;
    }

    /**
     * The business calendar, if the caller supplied one.
     *
     * @return the calendar, or empty
     */
    public Optional<BusinessCalendar> calendar() {
        return calendar;
    }

    /**
     * The reference-data provider, if the caller supplied one.
     *
     * @return the provider, or empty
     */
    public Optional<ReferenceDataProvider> referenceData() {
        return referenceData;
    }

    /**
     * Renders a value for a finding, masking it unless the caller opted out
     * (R-E6).
     *
     * <p>Account numbers reach logs, tickets and CI annotations. Masking by
     * default means the careless path is the safe one; showing them in full is
     * available and has to be asked for.
     *
     * @param value     the raw field value
     * @param sensitive whether the descriptor marks the field sensitive
     * @return the value to put in a finding, never {@code null}
     */
    public String render(String value, boolean sensitive) {
        if (value == null) {
            return "";
        }
        if (!sensitive || unmaskSensitiveValues) {
            return value;
        }
        return io.zengin4j.core.error.Diagnostics.maskIdentifier(value);
    }
}
