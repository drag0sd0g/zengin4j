package io.zengin4j.validation;

import module java.base;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.time.MonthDayResolver;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import io.zengin4j.validation.calendar.BusinessCalendar;
import io.zengin4j.validation.engine.RuleEngine;
import io.zengin4j.validation.engine.Rules;
import io.zengin4j.validation.engine.ValidationContext;
import io.zengin4j.validation.refdata.ReferenceDataProvider;

/// Checks a Zengin file and reports what is wrong with it.
///
/// ```java
/// ValidationReport report = ZenginValidator.builder()
///         .withCalendar(JapaneseBankCalendar.bundled())
///         .suppress("V-605")
///         .build()
///         .validate(file);
///
/// if (!report.isSubmittable()) {
///     System.out.print(report.toText());
/// }
/// ```
///
/// **Never throws** (R-V1). Not for a malformed file, not for a
/// file that is not a Zengin file at all, not for a rule with a bug in it. The
/// one thing a validator cannot do is fail on bad input, because bad input is
/// the only reason anyone runs one.
///
/// Thread-safe and reusable once built: the rules are stateless and the
/// validator holds no per-file state.
///
/// @since 0.2.0
public final class ZenginValidator {

    private final RuleEngine engine;
    private final Optional<BusinessCalendar> calendar;
    private final Optional<ReferenceDataProvider> referenceData;
    private final MonthDayResolver dateResolver;
    private final boolean unmaskSensitiveValues;

    private ZenginValidator(Builder builder) {
        this.engine = new RuleEngine(builder.rules, builder.suppressed, builder.overrides, builder.failFast);
        this.calendar = Optional.ofNullable(builder.calendar);
        this.referenceData = Optional.ofNullable(builder.referenceData);
        this.dateResolver = builder.dateResolver != null
                ? builder.dateResolver
                : MonthDayResolver.forwardLooking(LocalDate.now());
        this.unmaskSensitiveValues = builder.unmaskSensitiveValues;
    }

    /// A validator with every bundled rule and no calendar or reference data.
    ///
    /// @return a validator
    public static ZenginValidator defaults() {
        return builder().build();
    }

    /// Starts configuring a validator.
    ///
    /// @return a builder
    public static Builder builder() {
        return new Builder();
    }

    /// Validates a file that has already been read.
    ///
    /// @param file the file to check
    /// @return the report, never `null`
    public ValidationReport validate(ZenginFile file) {
        Objects.requireNonNull(file, "file");
        FormatDescriptor descriptor = file.descriptor();
        ValidationContext context = ValidationContext.create(
                file, descriptor, dateResolver, calendar, referenceData, unmaskSensitiveValues);
        return new ValidationReport(engine.run(context), engine.rules());
    }

    /// Reads a file and validates it.
    ///
    /// A file that cannot be *read* — wrong record length throughout,
    /// an unknown 種別コード, an EBCDIC declaration — still produces a report
    /// rather than an exception, because "this is not a file I can parse" is
    /// exactly the answer the caller asked for (R-V1).
    ///
    /// @param path    the file to read
    /// @param options how to read it
    /// @return the report, never `null`
    public ValidationReport validate(Path path, ReaderOptions options) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        ZenginFile file;
        try {
            file = ZenginReaders.readFile(path, options);
        } catch (ZenginException unreadable) {
            // Formatted twice rather than with Bilingual.into(...): the argument
            // itself differs by language, since the exception carries its own
            // English and Japanese text (R-E4).
            return new ValidationReport(List.of(Finding.of(Severity.ERROR, "V-100")
                    .message(Messages.format("V-100.message", unreadable.messageEn()).en(),
                            Messages.format("V-100.message", unreadable.messageJa()).ja())
                    .build()), engine.rules());
        }
        return validate(file);
    }

    /// The rules this validator will run.
    ///
    /// @return the rules, never `null`
    public List<Rule> rules() {
        return engine.rules();
    }

    /// Assembles a validator.
    ///
    /// @since 0.2.0
    public static final class Builder {

        private final List<Rule> rules = new ArrayList<>(Rules.bundled());
        private final Set<String> suppressed = new HashSet<>();
        private final Map<String, Severity> overrides = new HashMap<>();
        private BusinessCalendar calendar;
        private ReferenceDataProvider referenceData;
        private MonthDayResolver dateResolver;
        private boolean failFast;
        private boolean unmaskSensitiveValues;

        private Builder() {
        }

        /// Replaces the rule set entirely.
        ///
        /// @param value the rules to run
        /// @return this builder
        public Builder withRules(List<Rule> value) {
            rules.clear();
            rules.addAll(Objects.requireNonNull(value, "rules"));
            return this;
        }

        /// Adds a rule of the caller's own.
        ///
        /// @param rule the rule
        /// @return this builder
        public Builder addRule(Rule rule) {
            rules.add(Objects.requireNonNull(rule, "rule"));
            return this;
        }

        /// Turns a rule off (R-V3).
        ///
        /// Institutional practice varies enough that some rule here will be
        /// wrong for somebody. Suppression by id is the answer, and it is why
        /// ids are stable across versions.
        ///
        /// @param ruleId the rule to suppress
        /// @return this builder
        public Builder suppress(String ruleId) {
            suppressed.add(Objects.requireNonNull(ruleId, "ruleId"));
            return this;
        }

        /// Re-ranks a rule.
        ///
        /// @param ruleId   the rule
        /// @param severity what it should report instead
        /// @return this builder
        public Builder severity(String ruleId, Severity severity) {
            overrides.put(Objects.requireNonNull(ruleId, "ruleId"),
                    Objects.requireNonNull(severity, "severity"));
            return this;
        }

        /// Supplies a business calendar, enabling the `V-5xx` rules.
        ///
        /// @param value the calendar
        /// @return this builder
        public Builder withCalendar(BusinessCalendar value) {
            this.calendar = value;
            return this;
        }

        /// Supplies reference data, enabling the `V-4xx` rules.
        ///
        /// @param value the provider
        /// @return this builder
        public Builder withReferenceData(ReferenceDataProvider value) {
            this.referenceData = value;
            return this;
        }

        /// Sets how the yearless `MMDD` dates get a year.
        ///
        /// @param value the resolver; forward-looking from today by default
        /// @return this builder
        public Builder withDateResolver(MonthDayResolver value) {
            this.dateResolver = value;
            return this;
        }

        /// Stops after tier 1 if it found errors.
        ///
        /// Worth setting when the file may not be a Zengin file at all: if
        /// the records are the wrong length, every later tier reads the wrong
        /// bytes and reports hundreds of findings about the misalignment rather
        /// than about the file.
        ///
        /// @param value whether to stop
        /// @return this builder
        public Builder failFast(boolean value) {
            this.failFast = value;
            return this;
        }

        /// Shows account numbers in full rather than masked (R-E6).
        ///
        /// Off by default, so the careless path is the safe one. Findings
        /// reach logs, tickets and CI annotations, and an account number in a
        /// CI annotation is in the CI provider's storage for ever.
        ///
        /// @param value whether to unmask
        /// @return this builder
        public Builder unmaskSensitiveValues(boolean value) {
            this.unmaskSensitiveValues = value;
            return this;
        }

        /// Builds the validator.
        ///
        /// @return the validator
        public ZenginValidator build() {
            return new ZenginValidator(this);
        }
    }
}
