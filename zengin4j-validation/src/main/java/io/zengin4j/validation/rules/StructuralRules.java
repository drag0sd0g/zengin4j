package io.zengin4j.validation.rules;

import module java.base;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.MalformedRecord;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.engine.ValidationContext;

/// Tier 1 — is this a Zengin file at all? (§14.3, `V-1xx`)
///
/// These run first and, under fail-fast, alone. If the records are not the
/// length the format declares, every field offset in every later tier is reading
/// the wrong bytes, and the hundreds of findings that would follow describe the
/// misalignment rather than the file.
///
/// @since 0.2.0
public final class StructuralRules {

    private StructuralRules() {
    }

    /// Every rule in this tier.
    ///
    /// @return the rules, never `null`
    public static List<Rule> all() {
        return List.of(
                new RecordLength(),
                new KnownDataKubun(),
                new DataFollowsHeader(),
                new OneTrailerPerHeader(),
                new EndRecordPresent(),
                new NothingAfterEnd(),
                new FileNotEmpty());
    }

    /// Every record in the file, in position order.
    static List<ZenginRecord> inOrder(ZenginFile file) {
        return file.recordsInOrder();
    }

    /// V-101.
    static final class RecordLength extends AbstractRule {

        RecordLength() {
            super("V-101", Severity.ERROR, RuleScope.RECORD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            int expected = context.descriptor().recordLength();
            for (ZenginRecord record : inOrder(context.file())) {
                int actual = record.rawBytes().length;
                if (actual != expected) {
                    out.accept(Messages.format(id() + ".message",
                                    actual, context.descriptor().id().value(), expected)
                            .into(finding().at(record.recordNumber(), record.byteOffset()))
                            .actual(actual + " bytes")
                            .expected(expected + " bytes")
                            .build());
                }
            }
        }
    }

    /// V-102: the discriminator byte names a record kind this format declares.
    static final class KnownDataKubun extends AbstractRule {

        KnownDataKubun() {
            super("V-102", Severity.ERROR, RuleScope.RECORD);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            String known = context.descriptor().records().values().stream()
                    .map(record -> "'" + (char) record.discriminator() + "'")
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none");

            for (ZenginRecord record : inOrder(context.file())) {
                if (!(record instanceof MalformedRecord) || record.rawBytes().length == 0) {
                    continue;
                }
                byte first = record.rawBytes()[0];
                if (context.descriptor().forDiscriminator(first).isEmpty()) {
                    out.accept(Messages.format(id() + ".message",
                                    printable(first), context.descriptor().id().value(), known)
                            .into(finding().at(record.recordNumber(), record.byteOffset())
                                    .field("dataKubun", 0))
                            .actual(printable(first))
                            .expected(known)
                            .build());
                }
            }
        }

        private static String printable(byte value) {
            int unsigned = value & 0xFF;
            return unsigned >= 0x20 && unsigned < 0x7F
                    ? "'" + (char) unsigned + "'"
                    : String.format("0x%02X", unsigned);
        }
    }

    /// V-103. The reader already places data records inside a batch, so a data
    /// record preceding every header shows up as an unbatched record rather than
    /// as a batch member.
    static final class DataFollowsHeader extends AbstractRule {

        DataFollowsHeader() {
            super("V-103", Severity.ERROR, RuleScope.FILE);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            for (ZenginRecord record : context.file().unbatched()) {
                if (record.kind() == RecordKind.DATA) {
                    out.accept(Messages.format(id() + ".message")
                            .into(finding().at(record.recordNumber(), record.byteOffset()))
                            .build());
                }
            }
        }
    }

    /// V-104.
    static final class OneTrailerPerHeader extends AbstractRule {

        OneTrailerPerHeader() {
            super("V-104", Severity.ERROR, RuleScope.BATCH);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            for (Batch batch : context.file().batches()) {
                if (batch.trailer().isEmpty()) {
                    out.accept(Messages.format(id() + ".message", batch.header().recordNumber(), 0)
                            .into(finding().at(batch.header().recordNumber(),
                                    batch.header().byteOffset()))
                            .actual("0")
                            .expected("1")
                            .build());
                }
            }
        }
    }

    /// V-105.
    static final class EndRecordPresent extends AbstractRule {

        EndRecordPresent() {
            super("V-105", Severity.ERROR, RuleScope.FILE);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            // A format that declares no end record cannot be missing one.
            if (context.descriptor().find(RecordKind.END).isEmpty()) {
                return;
            }
            if (context.file().endRecord().isEmpty()) {
                out.accept(Messages.format(id() + ".message").into(finding()).build());
            }
        }
    }

    /// V-106.
    static final class NothingAfterEnd extends AbstractRule {

        NothingAfterEnd() {
            super("V-106", Severity.ERROR, RuleScope.FILE);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            ZenginFile file = context.file();
            if (file.endRecord().isEmpty()) {
                return;
            }
            int endAt = file.endRecord().orElseThrow().recordNumber();
            List<ZenginRecord> after = inOrder(file).stream()
                    .filter(record -> record.recordNumber() > endAt)
                    .toList();
            if (!after.isEmpty()) {
                ZenginRecord first = after.getFirst();
                out.accept(Messages.format(id() + ".message", after.size())
                        .into(finding().at(first.recordNumber(), first.byteOffset()))
                        .actual(after.size() + " record(s)")
                        .expected("0")
                        .build());
            }
        }
    }

    /// V-107.
    static final class FileNotEmpty extends AbstractRule {

        FileNotEmpty() {
            super("V-107", Severity.ERROR, RuleScope.FILE);
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            if (context.file().totalRecords() == 0) {
                out.accept(Messages.format(id() + ".message").into(finding()).build());
            }
        }
    }
}
