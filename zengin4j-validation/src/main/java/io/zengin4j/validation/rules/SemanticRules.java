package io.zengin4j.validation.rules;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.engine.ValidationContext;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tier 6 — the file is valid, and something about it looks wrong anyway
 * (§14.3, {@code V-6xx}).
 *
 * <p>Every rule here is a warning, and every one describes something an
 * institution will accept. They exist because the expensive failures in
 * payments are rarely rejections — a rejected file gets fixed the same
 * afternoon. The expensive ones are files that are accepted and wrong.
 *
 * @since 0.2.0
 */
public final class SemanticRules {

    private SemanticRules() {
    }

    /**
     * Every rule in this tier.
     *
     * @return the rules, never {@code null}
     */
    public static List<Rule> all() {
        return List.of(
                new TruncatedThroughVoicingMark(),
                new ZeroAmount(),
                new AmountAtFieldMaximum(),
                new NameEntirelyPadding(),
                new CustomerCodesUnpopulated());
    }

    /**
     * V-601. A name cut to fit a field can be cut between a kana and its
     * voicing mark, which silently changes the character — ｶﾞ becomes ｶ, and
     * ガクブチ becomes カクブチ. This looks for a name that fills its field
     * exactly and ends on a kana that takes a mark, which is what truncation
     * through a mark leaves behind.
     */
    static final class TruncatedThroughVoicingMark extends AbstractRule {

        TruncatedThroughVoicingMark() {
            super("V-601", Severity.WARNING, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            SyntaxRules.forEachField(context, (record, field, bytes) -> {
                if (!isName(field)) {
                    return;
                }
                int last = field.offset() + field.length() - 1;
                int lastByte = bytes[last] & 0xFF;
                // A name that stops short of the field end was not truncated:
                // there was room for the mark and it is not there.
                if (lastByte == ' ') {
                    return;
                }
                if (takesVoicingMark(lastByte)) {
                    out.accept(Messages.format(id() + ".message", field.id())
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field(field.id(), last))
                            .actual(context.render(SyntaxRules.raw(bytes, field).stripTrailing(),
                                    field.sensitive()))
                            .build());
                }
            });
        }

        /** ｶ-ｺ, ｻ-ｿ, ﾀ-ﾄ, ﾊ-ﾎ, ｳ — the kana a dakuten can follow (R-K7). */
        private static boolean takesVoicingMark(int base) {
            return base == 0xB3
                    || (base >= 0xB6 && base <= 0xBA)
                    || (base >= 0xBB && base <= 0xBF)
                    || (base >= 0xC0 && base <= 0xC4)
                    || (base >= 0xCA && base <= 0xCE);
        }
    }

    /** V-602. */
    static final class ZeroAmount extends AbstractRule {

        ZeroAmount() {
            super("V-602", Severity.WARNING, RuleScope.RECORD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            for (DataRecord data : context.file().allData()) {
                if (data.amount() == 0) {
                    out.accept(Messages.format(id() + ".message")
                            .into(finding().at(data.recordNumber(), data.byteOffset()))
                            .actual("0")
                            .build());
                }
            }
        }
    }

    /**
     * V-603. An amount of exactly {@code 9,999,999,999} in an {@code N(10)}
     * field is either a genuine ten-billion-yen payment or a number that
     * overflowed on the way in. Both look identical in the file, which is why
     * this is worth a line in a report.
     */
    static final class AmountAtFieldMaximum extends AbstractRule {

        AmountAtFieldMaximum() {
            super("V-603", Severity.WARNING, RuleScope.RECORD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            RecordDescriptor layout = context.descriptor().find(RecordKind.DATA).orElse(null);
            if (layout == null) {
                return;
            }
            FieldDescriptor amount = layout.findByFormat(FieldFormat.AMOUNT).orElse(null);
            if (amount == null) {
                return;
            }
            long maximum = maximumFor(amount.length());
            for (DataRecord data : context.file().allData()) {
                if (data.amount() == maximum) {
                    out.accept(Messages.format(id() + ".message", maximum)
                            .into(finding().at(data.recordNumber(), data.byteOffset())
                                    .field(amount.id(), amount.offset()))
                            .actual(Long.toString(maximum))
                            .build());
                }
            }
        }

        private static long maximumFor(int digits) {
            long maximum = 0;
            for (int i = 0; i < digits; i++) {
                maximum = maximum * 10 + 9;
            }
            return maximum;
        }
    }

    /** V-604. */
    static final class NameEntirelyPadding extends AbstractRule {

        NameEntirelyPadding() {
            super("V-604", Severity.WARNING, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            SyntaxRules.forEachField(context, (record, field, bytes) -> {
                if (!isName(field) || field.charClass() == CharacterClass.BANK_NAME) {
                    return;
                }
                if (SyntaxRules.raw(bytes, field).isBlank()) {
                    out.accept(Messages.format(id() + ".message", field.id())
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field(field.id(), field.offset()))
                            .actual("(blank)")
                            .build());
                }
            });
        }
    }

    /**
     * V-605. Reported once per file rather than once per record: a file that
     * does not use customer codes does not use them anywhere, and one finding
     * says that as well as five thousand would.
     */
    static final class CustomerCodesUnpopulated extends AbstractRule {

        CustomerCodesUnpopulated() {
            super("V-605", Severity.INFO, RuleScope.FILE);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            RecordDescriptor layout = context.descriptor().find(RecordKind.DATA).orElse(null);
            if (layout == null || context.file().allData().isEmpty()) {
                return;
            }
            List<FieldDescriptor> codes = layout.fields().stream()
                    .filter(field -> field.id().startsWith("customerCode")
                            || field.id().equals("customerNumber"))
                    .toList();
            if (codes.isEmpty()) {
                return;
            }
            for (DataRecord data : context.file().allData()) {
                byte[] bytes = data.rawBytes();
                if (bytes.length < layout.recordLength()) {
                    continue;
                }
                for (FieldDescriptor field : codes) {
                    if (!SyntaxRules.raw(bytes, field).isBlank()) {
                        return;
                    }
                }
            }
            out.accept(Messages.format(id() + ".message").into(finding()).build());
        }
    }

    /** A text field a human reads: a name, not filler and not a code. */
    private static boolean isName(FieldDescriptor field) {
        return field.type() == FieldType.C
                && !field.filler()
                && field.constant().isEmpty()
                && (field.charClass() == CharacterClass.PARTY_NAME
                        || field.charClass() == CharacterClass.BANK_NAME
                        || field.charClass() == CharacterClass.PAYROLL_NAME);
    }
}
