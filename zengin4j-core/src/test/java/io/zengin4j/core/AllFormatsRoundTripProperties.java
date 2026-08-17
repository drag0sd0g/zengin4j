package io.zengin4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ByteOrderMarkPolicy;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.testing.Fixtures;
import io.zengin4j.core.testing.RandomZenginFiles;
import io.zengin4j.core.testing.Seeded;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The load-bearing invariants, for every bundled format.
 *
 * <p>{@link RoundTripProperties} runs these against 総合振込, in more shapes and
 * with more cases. This runs the same claims against all four, because until
 * now they were proven for one format of four and nothing said so — the
 * generator took a {@code FormatDescriptor} parameter while hard-coding
 * 総合振込's field ids, so passing any other descriptor failed outright. That
 * made the parameter a false generality, and R-T7 calls these the load-bearing
 * correctness guarantee.
 *
 * <p>The generator now derives its values from the descriptor, which is what
 * makes this file possible — and means these properties hold for a consumer's
 * own format too, not only the bundled ones.
 *
 * <p>給与振込 is not 総合振込 with three fields renamed, and 預金口座振替 moves
 * money the other way. Those differences are exactly why "it works for
 * 総合振込" was not evidence about the rest.
 */
class AllFormatsRoundTripProperties {

    private static final long SEED = 0x5A5A_2026L;

    private static final ReaderOptions OPTIONS = Fixtures.optionsBuilder()
            .byteOrderMark(ByteOrderMarkPolicy.STRIP)
            .build();

    static List<FormatId> formats() {
        return FormatRegistry.defaults().all().stream()
                .map(FormatDescriptor::id)
                .sorted(java.util.Comparator.comparing(FormatId::value))
                .toList();
    }

    private static FormatDescriptor descriptor(FormatId id) {
        return FormatRegistry.defaults().byId(id).orElseThrow();
    }

    /** INV-1 — {@code write(read(f))} equals {@code f}, byte for byte. */
    @ParameterizedTest
    @MethodSource("formats")
    void inv1_roundTripsByteForByte(FormatId id) {
        FormatDescriptor descriptor = descriptor(id);

        Seeded.property("INV-1 for " + id.value(), 120, SEED,
                random -> RandomZenginFiles.bytes(random, descriptor),
                generated -> {
                    ZenginFile parsed = ZenginReaders.readFile(
                            new ByteArrayInputStream(generated.bytes()), OPTIONS);
                    assertThat(ZenginWriters.toByteArray(parsed, WriterOptions.defaults()))
                            .isEqualTo(generated.bytes());
                });
    }

    /** INV-2 — {@code read(write(f))} produces an equal file. */
    @ParameterizedTest
    @MethodSource("formats")
    void inv2_readingAFileJustWrittenReproducesIt(FormatId id) {
        FormatDescriptor descriptor = descriptor(id);

        Seeded.property("INV-2 for " + id.value(), 120, SEED,
                random -> RandomZenginFiles.built(random, descriptor),
                built -> {
                    byte[] written = ZenginWriters.toByteArray(built, WriterOptions.defaults());
                    ZenginFile reparsed = ZenginReaders.readFile(
                            new ByteArrayInputStream(written), OPTIONS);

                    assertThat(ZenginWriters.toByteArray(reparsed, WriterOptions.defaults()))
                            .isEqualTo(written);
                    assertThat(reparsed.totalRecords()).isEqualTo(built.totalRecords());
                });
    }

    /**
     * INV-6 — a built file's trailer agrees with the records it summarises.
     *
     * <p>Worth running per format rather than once: 預金口座振替's trailer has
     * four counters the others do not, and 給与振込's data record has fourteen
     * fields where 総合振込 has sixteen. A trailer computed against the wrong
     * layout is the failure this catches.
     */
    @ParameterizedTest
    @MethodSource("formats")
    void inv6_theTrailerAgreesWithItsBatch(FormatId id) {
        FormatDescriptor descriptor = descriptor(id);

        Seeded.property("INV-6 for " + id.value(), 120, SEED,
                random -> RandomZenginFiles.built(random, descriptor),
                built -> {
                    for (Batch batch : built.batches()) {
                        assertThat(batch.trailer()).isPresent();
                        assertThat(batch.trailer().orElseThrow().recordCount())
                                .isEqualTo(batch.data().size());
                        assertThat(batch.trailer().orElseThrow().totalAmount())
                                .isEqualTo(batch.computedTotal());
                    }
                });
    }

    /**
     * INV-8 — every record type's field lengths sum to the record length.
     *
     * <p>Enforced at build time for the bundled descriptors, and restated here
     * per format so a hand-built descriptor cannot slip past.
     */
    @ParameterizedTest
    @MethodSource("formats")
    void inv8_fieldLengthsSumToTheRecordLength(FormatId id) {
        FormatDescriptor descriptor = descriptor(id);

        descriptor.records().forEach((kind, record) -> {
            int sum = record.fields().stream()
                    .mapToInt(io.zengin4j.core.format.FieldDescriptor::length)
                    .sum();
            assertThat(sum)
                    .as("%s %s field lengths must sum to the record length", id.value(), kind)
                    .isEqualTo(descriptor.recordLength());
        });
    }
}
