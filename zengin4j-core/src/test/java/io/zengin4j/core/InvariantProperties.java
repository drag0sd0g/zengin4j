package io.zengin4j.core;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ParseMode;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.RecordView;
import io.zengin4j.core.codec.ZenginReader;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldSpec;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.testing.Fixtures;
import io.zengin4j.core.testing.RandomZenginFiles;
import io.zengin4j.core.testing.Seeded;
import org.junit.jupiter.api.Test;

/// The structural invariants of §21.1.
///
/// Round-tripping (INV-1, INV-2, INV-6) lives in `RoundTripProperties`.
/// INV-4 arrives with the transliteration engine in Epic 6, INV-5 with the
/// mapping layer in Epic 7.
///
/// INV-3 is covered here *and* by `ReaderFuzzTest`. This version
/// runs on every build and is cheap; Jazzer's is coverage-guided, finds inputs
/// random generation never would, and runs on demand.
class InvariantProperties {

    private static final long SEED = 0x1234_2026L;

    /// Bound on records read, so a defect shows up as a failure rather than a hang.
    private static final int RECORD_LIMIT = 10_000;

    private static final FormatId GENERATED_ID = FormatId.of("generated");

    private static final FormatRegistry REGISTRY = FormatRegistry.defaults();
    private static final FormatDescriptor DESCRIPTOR =
            REGISTRY.byId(Fixtures.SOUGOU_FURIKOMI).orElseThrow();

    /// INV-3, for arbitrary input: reading terminates and throws nothing
    /// outside the declared hierarchy.
    @Test
    void inv3_readingArbitraryBytesStaysInsideTheExceptionHierarchy() {
        Seeded.property("INV-3: arbitrary bytes", Seeded.DEFAULT_CASES, SEED,
                InvariantProperties::arbitraryBytes,
                input -> {
                    assertReadIsWellBehaved(input, ParseMode.STRICT);
                    assertReadIsWellBehaved(input, ParseMode.LENIENT);
                });
    }

    /// INV-3, for input that starts out valid: single-byte corruption,
    /// truncation, and both together. More interesting than random noise,
    /// because it reaches deep into the parser before failing.
    @Test
    void inv3_readingCorruptedFilesStaysInsideTheExceptionHierarchy() {
        Seeded.property("INV-3: corrupted files", Seeded.DEFAULT_CASES, SEED + 1,
                random -> corrupt(random, RandomZenginFiles.bytes(random, DESCRIPTOR).bytes()),
                input -> {
                    assertReadIsWellBehaved(input, ParseMode.STRICT);
                    assertReadIsWellBehaved(input, ParseMode.LENIENT);
                });
    }

    /// INV-8: every shipped descriptor accounts for every byte of its record.
    @Test
    void inv8_everyShippedDescriptorAccountsForEveryByte() {
        for (FormatDescriptor format : REGISTRY.all()) {
            for (RecordDescriptor record : format.records().values()) {
                int sum = record.fields().stream().mapToInt(FieldDescriptor::length).sum();
                assertThat(sum)
                        .as("%s %s field lengths", format.id(), record.kind())
                        .isEqualTo(format.recordLength());
                int cursor = 0;
                for (FieldDescriptor field : record.fields()) {
                    assertThat(field.offset())
                            .as("%s %s field %s offset", format.id(), record.kind(), field.id())
                            .isEqualTo(cursor);
                    cursor = field.endOffset();
                }
            }
        }
    }

    /// INV-8, as a property of the descriptor model: a record layout is
    /// accepted exactly when its field lengths account for the declared record
    /// length, and it says so either way.
    ///
    /// The check lives in the model rather than in a file reader on purpose.
    /// Since ADR-0016 the descriptors reach core as generated Java, so this is
    /// the one gate every layout passes through — generated, hand-built, or
    /// supplied at runtime by a consumer.
    @Test
    void inv8_aLayoutIsAcceptedExactlyWhenItsFieldsAccountForEveryByte() {
        Seeded.property("INV-8: lengths account for the record", Seeded.DEFAULT_CASES, SEED + 2,
                InvariantProperties::randomLayout,
                layout -> {
                    int sum = layout.lengths.stream().mapToInt(Integer::intValue).sum();
                    List<FieldSpec> fields = specs(layout.lengths);

                    if (sum == layout.declaredLength) {
                        RecordDescriptor record = RecordDescriptor.of(
                                GENERATED_ID, RecordKind.HEADER, (byte) '1', layout.declaredLength, fields);

                        assertThat(record.fields()).hasSize(layout.lengths.size());
                        int cursor = 0;
                        for (FieldDescriptor field : record.fields()) {
                            assertThat(field.offset()).isEqualTo(cursor);
                            cursor = field.endOffset();
                        }
                        assertThat(cursor).isEqualTo(layout.declaredLength);
                    } else {
                        try {
                            RecordDescriptor.of(GENERATED_ID, RecordKind.HEADER, (byte) '1',
                                    layout.declaredLength, fields);
                            throw new AssertionError("expected a layout summing to " + sum
                                    + " to be rejected against a record length of " + layout.declaredLength);
                        } catch (FormatDescriptorException expected) {
                            assertThat(expected.problem()).contains("field lengths sum to " + sum);
                        }
                    }
                });
    }

    // ------------------------------------------------------------ generators

    private static byte[] arbitraryBytes(Random random) {
        byte[] bytes = new byte[random.nextInt(400)];
        random.nextBytes(bytes);
        return bytes;
    }

    /// Flips a byte, truncates, or both.
    private static byte[] corrupt(Random random, byte[] valid) {
        byte[] copy = Arrays.copyOf(valid, valid.length);
        if (random.nextBoolean() && copy.length > 0) {
            copy[random.nextInt(copy.length)] = (byte) random.nextInt(256);
        }
        return random.nextBoolean() ? Arrays.copyOf(copy, random.nextInt(copy.length + 1)) : copy;
    }

    private record Layout(List<Integer> lengths, int declaredLength) {
        @Override
        public String toString() {
            return "lengths " + lengths + " against record length " + declaredLength;
        }
    }

    private static Layout randomLayout(Random random) {
        int count = 1 + random.nextInt(6);
        List<Integer> lengths = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lengths.add(1 + random.nextInt(20));
        }
        return new Layout(lengths, 1 + random.nextInt(60));
    }

    private static List<FieldSpec> specs(List<Integer> lengths) {
        List<FieldSpec> fields = new ArrayList<>(lengths.size());
        for (int i = 0; i < lengths.size(); i++) {
            fields.add(FieldSpec.of(i + 1, "field" + i, "項目" + i, "Field " + i,
                    FieldType.C, lengths.get(i)));
        }
        return fields;
    }

    // --------------------------------------------------------------- helpers

    private static void assertReadIsWellBehaved(byte[] input, ParseMode mode) {
        ReaderOptions options = ReaderOptions.builder()
                .registry(REGISTRY)
                .allowUnverifiedFormats(true)
                .mode(mode)
                .warningListener(warning -> {
                })
                .build();
        try (ZenginReader reader = ZenginReaders.open(new ByteArrayInputStream(input), options)) {
            int records = 0;
            while (reader.hasNext() && records < RECORD_LIMIT) {
                RecordView view = reader.next();
                // Touching the record must not misbehave either.
                view.kind();
                view.rawBytes();
                records++;
            }
            assertThat(records).as("reading %d bytes should terminate", input.length)
                    .isLessThan(RECORD_LIMIT);
        } catch (ZenginException expected) {
            // Declared, located and bilingual: exactly what the contract allows.
            assertThat(expected.messageEn()).isNotBlank();
            assertThat(expected.messageJa()).isNotBlank();
        }
    }
}
