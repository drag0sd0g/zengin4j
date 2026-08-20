package io.zengin4j.validation.rules;

import module java.base;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.HeaderRecord;
import io.zengin4j.core.model.TrailerRecord;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.engine.ValidationContext;

/// Tier 3 — does the file agree with itself? (§14.3, §19.3, `V-3xx`)
///
/// The tier that catches the expensive mistakes. A trailer that disagrees
/// with its batch is the single most common reason a file is rejected, and the
/// arithmetic that produces it — summing amounts into a fixed-width field — is
/// the one place in this library where a silent wrap would turn a large payment
/// into a small one.
///
/// @since 0.2.0
public final class ConsistencyRules {

    /// What an `N(12)` trailer total can hold.
    static final long TRAILER_CAPACITY = 999_999_999_999L;

    private ConsistencyRules() {
    }

    /// Every rule in this tier.
    ///
    /// @return the rules, never `null`
    public static List<Rule> all() {
        return List.of(
                new TrailerTotal(),
                new TrailerCount(),
                new TypeCodeConsistent(),
                new DuplicatePayments());
    }

    /// V-301, V-303 and V-304 — one walk of the batch, because they are three
    /// answers to one question and computing the sum three times could produce
    /// three different ones (§19.3).
    static final class TrailerTotal extends AbstractRule {

        TrailerTotal() {
            super("V-301", Severity.ERROR, RuleScope.BATCH,
                    java.util.Set.of("V-301", "V-303", "V-304"));
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            for (Batch batch : context.file().batches()) {
                long sum = 0;
                boolean overflowed = false;
                for (DataRecord data : batch.data()) {
                    try {
                        sum = Math.addExact(sum, data.amount());
                    } catch (ArithmeticException wrapped) {
                        overflowed = true;
                        break;
                    }
                }

                if (overflowed) {
                    out.accept(Messages.format("V-303.message")
                            .into(Finding.of(Severity.ERROR, "V-303")
                                    .at(batch.header().recordNumber(), batch.header().byteOffset()))
                            .build());
                    continue;
                }

                if (sum > TRAILER_CAPACITY) {
                    out.accept(Messages.format("V-304.message", sum, 12, TRAILER_CAPACITY)
                            .into(Finding.of(Severity.ERROR, "V-304")
                                    .at(batch.header().recordNumber(), batch.header().byteOffset()))
                            .actual(Long.toString(sum))
                            .expected("at most " + TRAILER_CAPACITY)
                            .build());
                    continue;
                }

                TrailerRecord trailer = batch.trailer().orElse(null);
                if (trailer == null) {
                    // V-104 already reports the missing trailer; a second
                    // finding about its contents would be noise.
                    continue;
                }
                if (trailer.totalAmount() != sum) {
                    long difference = trailer.totalAmount() - sum;
                    out.accept(Messages.format(id() + ".message",
                                    trailer.totalAmount(), sum, difference)
                            .into(finding().at(trailer.recordNumber(), trailer.byteOffset())
                                    .field("totalAmount", offsetOf(context, "totalAmount")))
                            .actual(Long.toString(trailer.totalAmount()))
                            .expected(Long.toString(sum))
                            .build());
                }
            }
        }
    }

    /// V-302.
    static final class TrailerCount extends AbstractRule {

        TrailerCount() {
            super("V-302", Severity.ERROR, RuleScope.BATCH);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            for (Batch batch : context.file().batches()) {
                TrailerRecord trailer = batch.trailer().orElse(null);
                if (trailer == null) {
                    continue;
                }
                int actual = batch.data().size();
                if (trailer.recordCount() != actual) {
                    out.accept(Messages.format(id() + ".message", trailer.recordCount(), actual)
                            .into(finding().at(trailer.recordNumber(), trailer.byteOffset())
                                    .field("recordCount", offsetOf(context, "recordCount")))
                            .actual(Integer.toString(trailer.recordCount()))
                            .expected(Integer.toString(actual))
                            .build());
                }
            }
        }
    }

    /// V-305.
    static final class TypeCodeConsistent extends AbstractRule {

        TypeCodeConsistent() {
            super("V-305", Severity.ERROR, RuleScope.FILE);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            String first = null;
            for (Batch batch : context.file().batches()) {
                HeaderRecord header = batch.header();
                String typeCode = typeCodeOf(context, header);
                if (typeCode == null) {
                    continue;
                }
                if (first == null) {
                    first = typeCode;
                } else if (!first.equals(typeCode)) {
                    out.accept(Messages.format(id() + ".message", typeCode, first)
                            .into(finding().at(header.recordNumber(), header.byteOffset())
                                    .field("typeCode", offsetOf(context, RecordKind.HEADER, "typeCode")))
                            .actual(typeCode)
                            .expected(first)
                            .build());
                }
            }
        }

        private static String typeCodeOf(ValidationContext context, HeaderRecord header) {
            RecordDescriptor layout = context.descriptor().find(RecordKind.HEADER).orElse(null);
            if (layout == null || layout.find("typeCode").isEmpty()) {
                return null;
            }
            FieldDescriptor field = layout.field("typeCode");
            byte[] bytes = header.rawBytes();
            return bytes.length < field.endOffset() ? null : SyntaxRules.raw(bytes, field);
        }
    }

    /// V-306 — a warning, deliberately. Two identical payments in one batch is
    /// legal, occasionally intended, and far more often a duplicated row in a
    /// spreadsheet. Reporting it as an error would make people suppress the
    /// rule; reporting it as a warning makes them look.
    static final class DuplicatePayments extends AbstractRule {

        DuplicatePayments() {
            super("V-306", Severity.WARNING, RuleScope.BATCH);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            RecordDescriptor layout = context.descriptor().find(RecordKind.DATA).orElse(null);
            if (layout == null) {
                return;
            }
            for (Batch batch : context.file().batches()) {
                Map<String, Integer> seen = new HashMap<>();
                for (DataRecord data : batch.data()) {
                    String key = identityOf(layout, data);
                    if (key == null) {
                        continue;
                    }
                    Integer previous = seen.putIfAbsent(key, data.recordNumber());
                    if (previous != null) {
                        out.accept(Messages.format(id() + ".message", previous)
                                .into(finding().at(data.recordNumber(), data.byteOffset()))
                                .build());
                    }
                }
            }
        }

        /// Bank, branch, account and amount — what §14.3 names. Built from the
        /// descriptor rather than from accessors, so it works for any format
        /// whose data record carries those field ids.
        private static String identityOf(RecordDescriptor layout, DataRecord data) {
            byte[] bytes = data.rawBytes();
            if (bytes.length < layout.recordLength()) {
                return null;
            }
            var key = new StringBuilder();
            for (String id : List.of("beneficiaryBankCode", "payerBankCode",
                    "beneficiaryBranchCode", "payerBranchCode",
                    "accountNumber", "payerAccountNumber")) {
                layout.find(id).ifPresent(field -> key.append(SyntaxRules.raw(bytes, field)).append('|'));
            }
            if (key.isEmpty()) {
                return null;
            }
            return key.append(data.amount()).toString();
        }
    }

    private static int offsetOf(ValidationContext context, String fieldId) {
        return offsetOf(context, RecordKind.TRAILER, fieldId);
    }

    private static int offsetOf(ValidationContext context, RecordKind kind, String fieldId) {
        return context.descriptor().find(kind)
                .flatMap(record -> record.find(fieldId))
                .map(FieldDescriptor::offset)
                .orElse(0);
    }

    /// Whether the format's trailer declares an amount field at all.
    static boolean hasTrailerTotal(ValidationContext context) {
        return context.descriptor().find(RecordKind.TRAILER)
                .flatMap(record -> record.findByFormat(FieldFormat.AMOUNT))
                .isPresent();
    }
}
