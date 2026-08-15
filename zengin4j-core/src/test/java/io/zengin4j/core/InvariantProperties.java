package io.zengin4j.core;

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
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The formal invariants of §21.1 that Epic 1 can state.
 *
 * <p>INV-1, INV-2 and INV-6 arrive with the writer in Epic 2; INV-4 with the
 * transliteration engine in Epic 6; INV-5 with the mapping layer in Epic 7.
 */
class InvariantProperties {

    /** Bound on records read, so a defect shows up as a failure rather than a hang. */
    private static final int RECORD_LIMIT = 10_000;

    /** Identifier for the throwaway layouts the properties build. */
    private static final FormatId GENERATED_ID = FormatId.of("generated");

    private static final FormatRegistry REGISTRY = FormatRegistry.defaults();
    private static final FormatDescriptor DESCRIPTOR =
            REGISTRY.byId(Fixtures.SOUGOU_FURIKOMI).orElseThrow();

    /**
     * INV-3, for arbitrary input: reading terminates and throws nothing
     * outside the declared hierarchy.
     */
    @Property(tries = 400)
    void readingArbitraryBytesStaysInsideTheExceptionHierarchy(@ForAll("arbitraryBytes") byte[] input) {
        assertReadIsWellBehaved(input, ParseMode.STRICT);
        assertReadIsWellBehaved(input, ParseMode.LENIENT);
    }

    /**
     * INV-3, for input that starts out valid: single-byte corruption,
     * truncation and both together.
     */
    @Property(tries = 400)
    void readingCorruptedFilesStaysInsideTheExceptionHierarchy(@ForAll("corruptedFiles") byte[] input) {
        assertReadIsWellBehaved(input, ParseMode.STRICT);
        assertReadIsWellBehaved(input, ParseMode.LENIENT);
    }

    /** INV-8: every shipped descriptor accounts for every byte of its record. */
    @Property(tries = 1)
    void everyShippedDescriptorAccountsForEveryByte() {
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

    /**
     * INV-8, as a property of the descriptor model: a record layout is
     * accepted exactly when its field lengths account for the declared record
     * length, and it says so either way.
     *
     * <p>The check lives here rather than in a file reader on purpose. Since
     * ADR-0016 the descriptors reach core as generated Java, so this is the
     * one gate every layout passes through — generated, hand-built, or
     * supplied at runtime by a consumer.
     */
    @Property(tries = 200)
    void aLayoutIsAcceptedExactlyWhenItsFieldsAccountForEveryByte(
            @ForAll("fieldLengths") List<Integer> lengths,
            @ForAll("recordLengths") int declaredLength) {

        int sum = lengths.stream().mapToInt(Integer::intValue).sum();
        List<FieldSpec> fields = specs(lengths);

        if (sum == declaredLength) {
            RecordDescriptor record = RecordDescriptor.of(
                    GENERATED_ID, RecordKind.HEADER, (byte) '1', declaredLength, fields);

            assertThat(record.fields()).hasSize(lengths.size());
            assertThat(record.recordLength()).isEqualTo(declaredLength);

            // R-F2: offsets are computed, contiguous, and cover the record.
            int cursor = 0;
            for (FieldDescriptor field : record.fields()) {
                assertThat(field.offset()).isEqualTo(cursor);
                cursor = field.endOffset();
            }
            assertThat(cursor).isEqualTo(declaredLength);
        } else {
            try {
                RecordDescriptor.of(GENERATED_ID, RecordKind.HEADER, (byte) '1', declaredLength, fields);
                throw new AssertionError("expected a layout summing to " + sum
                        + " to be rejected against a record length of " + declaredLength);
            } catch (FormatDescriptorException expected) {
                assertThat(expected.problem()).contains("field lengths sum to " + sum);
            }
        }
    }

    private static List<FieldSpec> specs(List<Integer> lengths) {
        List<FieldSpec> fields = new java.util.ArrayList<>(lengths.size());
        for (int i = 0; i < lengths.size(); i++) {
            fields.add(FieldSpec.of(i + 1, "field" + i, "項目" + i, "Field " + i,
                    FieldType.C, lengths.get(i)));
        }
        return fields;
    }

    private void assertReadIsWellBehaved(byte[] input, ParseMode mode) {
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

    @Provide
    Arbitrary<byte[]> arbitraryBytes() {
        return Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(400);
    }

    @Provide
    Arbitrary<byte[]> corruptedFiles() {
        byte[] valid = Fixtures.file(DESCRIPTOR);
        return Combinators.combine(
                        Arbitraries.integers().between(0, valid.length - 1),
                        Arbitraries.bytes(),
                        Arbitraries.integers().between(0, valid.length))
                .as((position, value, cut) -> {
                    byte[] copy = Arrays.copyOf(valid, cut);
                    if (position < copy.length) {
                        copy[position] = value;
                    }
                    return copy;
                });
    }

    @Provide
    Arbitrary<List<Integer>> fieldLengths() {
        return Arbitraries.integers().between(1, 20).list().ofMinSize(1).ofMaxSize(6);
    }

    @Provide
    Arbitrary<Integer> recordLengths() {
        return Arbitraries.integers().between(1, 60);
    }

}
