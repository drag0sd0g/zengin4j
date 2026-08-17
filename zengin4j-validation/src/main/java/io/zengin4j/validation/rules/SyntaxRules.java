package io.zengin4j.validation.rules;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.CharacterViolation;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.charset.VoicingMarks;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.engine.ValidationContext;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tier 2 — does every field contain what its type permits? (§14.3,
 * {@code V-2xx})
 *
 * <p>These run per field, over every record whose length and discriminator let
 * its fields be located — including records the reader rejected. A record is
 * usually malformed <em>because</em> one of its fields contains something it
 * should not, and this is the tier that says which field and what.
 *
 * @since 0.2.0
 */
public final class SyntaxRules {

    private SyntaxRules() {
    }

    /**
     * Every rule in this tier.
     *
     * @return the rules, never {@code null}
     */
    public static List<Rule> all() {
        return List.of(
                new NumericFieldsHoldDigits(),
                new CharactersArePermitted(),
                new PaddingIsCorrect(),
                new ConstantsHold(),
                new CodeListMembership(),
                new VoicingMarksAreLegal());
    }

    /**
     * Walks every field of every record whose fields can be located.
     *
     * <p><strong>Malformed records are included</strong>, provided their length
     * and discriminator are right. Skipping them would be exactly backwards: a
     * record is usually malformed <em>because</em> a field contains something
     * it should not, and this is the tier that says which field and what. The
     * reader refusing to materialise a record does not make its byte offsets
     * unreliable — the layout is chosen by the discriminator, and the fields
     * are where the descriptor says.
     *
     * <p>What is skipped is a record whose length is wrong or whose
     * discriminator names no layout. There, the boundaries genuinely are
     * unknown, and a finding pointing at "byte 43 of 受取人名" would be pointing
     * at a field the record may not have. V-101 and V-102 cover those.
     */
    static void forEachField(ValidationContext context, FieldVisitor visitor) {
        for (ZenginRecord record : StructuralRules.inOrder(context.file())) {
            RecordDescriptor layout = layoutOf(context, record);
            if (layout == null) {
                continue;
            }
            for (FieldDescriptor field : layout.fields()) {
                visitor.visit(record, field, record.rawBytes());
            }
        }
    }

    /**
     * The layout a record's fields sit in, or {@code null} when it cannot be
     * determined.
     */
    static RecordDescriptor layoutOf(ValidationContext context, ZenginRecord record) {
        byte[] bytes = record.rawBytes();
        if (bytes.length < context.descriptor().recordLength()) {
            return null;
        }
        // By discriminator rather than by kind: a MalformedRecord reports its
        // kind as MALFORMED, which no descriptor declares.
        RecordDescriptor layout = context.descriptor().forDiscriminator(bytes[0]).orElse(null);
        return layout == null || bytes.length < layout.recordLength() ? null : layout;
    }

    /** What {@link #forEachField} hands a rule. */
    interface FieldVisitor {
        void visit(ZenginRecord record, FieldDescriptor field, byte[] bytes);
    }

    /** The field's bytes decoded as text, padding included. */
    static String raw(byte[] bytes, FieldDescriptor field) {
        return ZenginCharset.MS932.decode(bytes, field.offset(), field.length());
    }

    /** V-201. */
    static final class NumericFieldsHoldDigits extends AbstractRule {

        NumericFieldsHoldDigits() {
            super("V-201", Severity.ERROR, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            forEachField(context, (record, field, bytes) -> {
                if (field.type() != FieldType.N || field.filler()) {
                    return;
                }
                for (int i = 0; i < field.length(); i++) {
                    int value = bytes[field.offset() + i] & 0xFF;
                    if (value < '0' || value > '9') {
                        String shown = value == ' ' ? "a space" : String.format("0x%02X", value);
                        out.accept(Messages.format(id() + ".message", field.id(), shown)
                                .into(finding().at(record.recordNumber(), record.byteOffset())
                                        .field(field.id(), field.offset()))
                                .actual(context.render(raw(bytes, field), field.sensitive()))
                                .expected(field.length() + " digits")
                                .build());
                        return;
                    }
                }
            });
        }
    }

    /** V-202. */
    static final class CharactersArePermitted extends AbstractRule {

        CharactersArePermitted() {
            super("V-202", Severity.ERROR, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            forEachField(context, (record, field, bytes) -> {
                if (field.charClass() == CharacterClass.UNRESTRICTED) {
                    return;
                }
                List<CharacterViolation> violations = CharacterSet.validateField(
                        bytes, field.offset(), field.length(), field.charClass());
                for (CharacterViolation violation : violations) {
                    // Built directly rather than through Messages.format: the
                    // violation already carries its own bilingual description,
                    // including the correction, and the bundle's pattern would
                    // otherwise wrap the English one into the Japanese message.
                    out.accept(finding()
                            .at(record.recordNumber(), record.byteOffset())
                            .field(field.id(), violation.offset())
                            .message(Messages.format(id() + ".message",
                                            field.id(), violation.describeEn()).en(),
                                    Messages.format(id() + ".message",
                                            field.id(), violation.describeJa()).ja())
                            .actual(String.format("0x%02X", violation.unsignedValue()))
                            .expected(field.charClass().nameEn())
                            .build());
                }
            });
        }
    }

    /**
     * V-203. Checked as "the pad side is padded", not by re-encoding: a value
     * that is correct but padded on the wrong side is a real defect, and one
     * that re-encoding would silently normalise away.
     */
    static final class PaddingIsCorrect extends AbstractRule {

        PaddingIsCorrect() {
            super("V-203", Severity.WARNING, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            forEachField(context, (record, field, bytes) -> {
                if (field.filler() || field.constant().isPresent()) {
                    return;
                }
                String value = raw(bytes, field);
                if (value.isBlank()) {
                    return;
                }
                boolean numeric = field.type() == FieldType.N;
                // N is right-aligned and zero-padded, so a leading space is
                // wrong. C is left-aligned and space-padded, so a leading space
                // means the value was pushed right.
                boolean misaligned = numeric
                        ? value.charAt(0) == ' '
                        : value.charAt(0) == ' ' && !value.isBlank();
                if (misaligned) {
                    out.accept(Messages.format(id() + ".message",
                                    field.id(),
                                    numeric ? "a space" : "leading spaces",
                                    field.type(),
                                    numeric ? "zeros" : "spaces",
                                    numeric ? "left" : "right")
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field(field.id(), field.offset()))
                            .actual(context.render(value, field.sensitive()))
                            .build());
                }
            });
        }
    }

    /** V-204. */
    static final class ConstantsHold extends AbstractRule {

        ConstantsHold() {
            super("V-204", Severity.ERROR, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            forEachField(context, (record, field, bytes) -> field.constant().ifPresent(constant -> {
                String value = raw(bytes, field);
                if (!value.equals(constant)) {
                    out.accept(Messages.format(id() + ".message", field.id(), constant, value)
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field(field.id(), field.offset()))
                            .actual(value)
                            .expected(constant)
                            .build());
                }
            }));
        }
    }

    /**
     * V-205. Reported as a warning, not an error: every bundled list is
     * {@code open: true}, meaning verification confirms the listed values rather
     * than the absence of others. The JBA document itself defines business
     * codes beyond what any one format needs.
     */
    static final class CodeListMembership extends AbstractRule {

        CodeListMembership() {
            super("V-205", Severity.WARNING, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            forEachField(context, (record, field, bytes) -> field.codeList().ifPresent(list -> {
                String value = raw(bytes, field).trim();
                if (value.isEmpty() || accepted(field, list, value)) {
                    return;
                }
                out.accept(Messages.format(id() + ".message",
                                field.id(), value, list.id(), knownValues(field, list))
                        .into(finding().at(record.recordNumber(), record.byteOffset())
                                .field(field.id(), field.offset()))
                        .actual(value)
                        .expected(knownValues(field, list))
                        .build());
            }));
        }

        /**
         * A field may narrow its list (OQ-9). Where it does, the narrowing
         * governs — the standard says so — and the master list is not the test.
         */
        private static boolean accepted(FieldDescriptor field, CodeList list, String value) {
            return field.codes().isEmpty()
                    ? list.byCode(value).isPresent()
                    : field.codes().contains(value);
        }

        private static String knownValues(FieldDescriptor field, CodeList list) {
            List<String> codes = field.codes().isEmpty()
                    ? list.values().stream().map(io.zengin4j.core.format.CodeValue::code).toList()
                    : field.codes();
            return String.join(", ", codes);
        }
    }

    /**
     * V-206 — R-K7. A voicing mark is a character of its own, and only certain
     * kana take one. {@code ｱﾞ} is not a typo for anything; it is a byte pair
     * that no Japanese reader can pronounce and no institution will accept.
     */
    static final class VoicingMarksAreLegal extends AbstractRule {

        VoicingMarksAreLegal() {
            super("V-206", Severity.ERROR, RuleScope.FIELD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            forEachField(context, (record, field, bytes) -> {
                if (field.type() != FieldType.C || field.filler()) {
                    return;
                }
                for (int i = 0; i < field.length(); i++) {
                    int at = field.offset() + i;
                    int mark = bytes[at] & 0xFF;
                    if (!VoicingMarks.isMark(mark)) {
                        continue;
                    }
                    int base = i == 0 ? -1 : bytes[at - 1] & 0xFF;
                    if (VoicingMarks.isLegal(base, mark)) {
                        continue;
                    }
                    out.accept(Messages.format(id() + ".message",
                                    field.id(),
                                    mark == VoicingMarks.DAKUTEN ? "dakuten" : "handakuten",
                                    describeBase(base))
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field(field.id(), at))
                            .actual(String.format("0x%02X after 0x%02X", mark, Math.max(base, 0)))
                            .build());
                }
            });
        }

        private static String describeBase(int base) {
            if (base < 0) {
                return "the start of the field";
            }
            if (base == ' ') {
                return "a space";
            }
            return String.format("0x%02X", base);
        }
    }
}
