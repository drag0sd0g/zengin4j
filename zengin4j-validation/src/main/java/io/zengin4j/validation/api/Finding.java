package io.zengin4j.validation.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One thing wrong with a file, located exactly (R-V2).
 *
 * <p><strong>A finding says where.</strong> Not "the trailer is wrong" but
 * record 4, byte 366, field {@code totalAmount} — because the person reading it
 * has to open a 120-byte-per-line file and find the problem, and a report that
 * makes them count is a report they will stop using.
 *
 * <p>Both languages are carried at once rather than resolved against a locale
 * (R-V2, R-E4). A Japanese operations team and an English-speaking integrator
 * frequently read the same report, and a finding that picked one of them at
 * construction time could not serve both. The text comes from properties files,
 * never from inline literals, so a translation is reviewable as a diff.
 *
 * @param severity      how much this matters
 * @param ruleId        the rule that produced it, for example {@code V-301};
 *                      suppressible by this id (R-V3)
 * @param recordNumber  1-based record position in the file, if the finding
 *                      belongs to a record
 * @param byteOffset    byte offset within the file, if known
 * @param fieldOffset   byte offset within the record, if the finding belongs to
 *                      a field
 * @param fieldId       the descriptor field id, if the finding belongs to a
 *                      field
 * @param messageEn     English description
 * @param messageJa     Japanese description
 * @param actualValue   what the field contained, already masked if sensitive
 *                      (R-E6)
 * @param expectation   what would have been acceptable
 * @since 0.2.0
 */
public record Finding(
        Severity severity,
        String ruleId,
        OptionalInt recordNumber,
        OptionalInt byteOffset,
        OptionalInt fieldOffset,
        Optional<String> fieldId,
        String messageEn,
        String messageJa,
        Optional<String> actualValue,
        Optional<String> expectation) implements Comparable<Finding> {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the rule id is blank or a message is
     *                                  missing
     */
    public Finding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(recordNumber, "recordNumber");
        Objects.requireNonNull(byteOffset, "byteOffset");
        Objects.requireNonNull(fieldOffset, "fieldOffset");
        Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(actualValue, "actualValue");
        Objects.requireNonNull(expectation, "expectation");
        if (ruleId.isBlank()) {
            throw new IllegalArgumentException("a finding must name the rule that produced it");
        }
        if (messageEn == null || messageEn.isBlank() || messageJa == null || messageJa.isBlank()) {
            throw new IllegalArgumentException(
                    "finding " + ruleId + " must carry a message in both languages (R-E4);"
                            + " a missing translation is a missing message, not an empty one");
        }
    }

    /**
     * Orders findings canonically, so the same file always produces the same
     * report (INV-7).
     *
     * <p>By position first, because that is how the report will be read — down
     * the file — and by rule id within a position, so two rules firing on one
     * field come out in a fixed order rather than in whatever order the engine
     * happened to run them.
     *
     * @param other the finding to compare to
     * @return the comparison result
     */
    @Override
    public int compareTo(Finding other) {
        int result = Integer.compare(
                recordNumber.orElse(Integer.MAX_VALUE), other.recordNumber.orElse(Integer.MAX_VALUE));
        if (result != 0) {
            return result;
        }
        result = Integer.compare(
                fieldOffset.orElse(Integer.MAX_VALUE), other.fieldOffset.orElse(Integer.MAX_VALUE));
        if (result != 0) {
            return result;
        }
        result = ruleId.compareTo(other.ruleId);
        return result != 0 ? result : messageEn.compareTo(other.messageEn);
    }

    /**
     * Returns the message in the language a locale asks for.
     *
     * <p>Japanese for {@code ja}, English for everything else — not because
     * English is the default language of anything, but because it is the only
     * other language this library has, and a caller asking for German is better
     * served by text they can machine-translate than by text they cannot read
     * at all.
     *
     * @param locale the locale to render for
     * @return the message, never {@code null}
     */
    public String message(java.util.Locale locale) {
        return "ja".equals(locale.getLanguage()) ? messageJa : messageEn;
    }

    /**
     * A one-line rendering: severity, rule, position, message.
     *
     * @param locale the locale to render for
     * @return the line, never {@code null}
     */
    public String toLine(java.util.Locale locale) {
        StringBuilder out = new StringBuilder();
        out.append(severity).append(' ').append(ruleId);
        recordNumber.ifPresent(number -> out.append(" record ").append(number));
        byteOffset.ifPresent(offset -> out.append(" byte ").append(offset));
        fieldId.ifPresent(id -> out.append(" [").append(id).append(']'));
        return out.append(": ").append(message(locale)).toString();
    }

    /**
     * Starts building a finding.
     *
     * @param severity how much it matters
     * @param ruleId   the rule producing it
     * @return a builder
     */
    public static Builder of(Severity severity, String ruleId) {
        return new Builder(severity, ruleId);
    }

    /**
     * Assembles a finding.
     *
     * <p>Most of a finding's components are optional and most rules set a
     * different subset, which is a shape positional construction serves badly.
     *
     * @since 0.2.0
     */
    public static final class Builder {

        private final Severity severity;
        private final String ruleId;
        private OptionalInt recordNumber = OptionalInt.empty();
        private OptionalInt byteOffset = OptionalInt.empty();
        private OptionalInt fieldOffset = OptionalInt.empty();
        private Optional<String> fieldId = Optional.empty();
        private String messageEn;
        private String messageJa;
        private Optional<String> actualValue = Optional.empty();
        private Optional<String> expectation = Optional.empty();

        private Builder(Severity severity, String ruleId) {
            this.severity = Objects.requireNonNull(severity, "severity");
            this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        }

        /**
         * Places the finding at a record.
         *
         * @param number     1-based record position
         * @param fileOffset byte offset of the record within the file
         * @return this builder
         */
        public Builder at(int number, long fileOffset) {
            this.recordNumber = OptionalInt.of(number);
            this.byteOffset = OptionalInt.of(Math.toIntExact(fileOffset));
            return this;
        }

        /**
         * Places the finding at a field within the current record.
         *
         * @param id            the descriptor field id
         * @param offsetInRecord the field's byte offset within the record
         * @return this builder
         */
        public Builder field(String id, int offsetInRecord) {
            this.fieldId = Optional.of(id);
            this.fieldOffset = OptionalInt.of(offsetInRecord);
            return this;
        }

        /**
         * Sets both messages.
         *
         * @param en English text
         * @param ja Japanese text
         * @return this builder
         */
        public Builder message(String en, String ja) {
            this.messageEn = en;
            this.messageJa = ja;
            return this;
        }

        /**
         * Records what the field actually contained.
         *
         * @param value the value, already masked if sensitive (R-E6)
         * @return this builder
         */
        public Builder actual(String value) {
            this.actualValue = Optional.ofNullable(value);
            return this;
        }

        /**
         * Records what would have been acceptable.
         *
         * @param value the expectation
         * @return this builder
         */
        public Builder expected(String value) {
            this.expectation = Optional.ofNullable(value);
            return this;
        }

        /**
         * Builds the finding.
         *
         * @return the finding
         */
        public Finding build() {
            return new Finding(severity, ruleId, recordNumber, byteOffset, fieldOffset, fieldId,
                    messageEn, messageJa, actualValue, expectation);
        }
    }
}
