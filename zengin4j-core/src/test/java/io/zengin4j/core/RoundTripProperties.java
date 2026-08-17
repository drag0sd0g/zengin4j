package io.zengin4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ByteOrderMarkPolicy;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.testing.Fixtures;
import io.zengin4j.core.testing.RandomZenginFiles;
import io.zengin4j.core.testing.Seeded;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

/**
 * The round-trip invariants of §21.1 — milestone M2.
 *
 * <p>These are the load-bearing correctness guarantee (R-T7). Example-based
 * tests check the files someone thought of; these check several hundred shapes
 * per run, and the shapes that break fixed-length codecs are exactly the ones
 * nobody thinks of — a batch with no payments, a file with no separators, an
 * EOF byte with nothing before it.
 *
 * <p>The seed is fixed rather than random. A property test that flakes on a
 * schedule is worse than no property test: it teaches the team to re-run CI.
 */
class RoundTripProperties {
    private static final long SEED = 0x5A5A_2026L;

    private static final FormatDescriptor DESCRIPTOR = Fixtures.descriptor();

    /** The generator emits byte order marks occasionally, so the reader must accept them. */
    private static final ReaderOptions OPTIONS = Fixtures.optionsBuilder()
            .byteOrderMark(ByteOrderMarkPolicy.STRIP)
            .build();

    /**
     * INV-1 — for any valid file {@code f}, {@code write(read(f))} equals
     * {@code f}, byte for byte.
     *
     * <p>What this actually proves is that framing survives: the separator
     * convention, whether one followed the last record, a byte order mark, an
     * EOF byte. The record bytes themselves survive because records retain
     * them (R-D5) — which is the point of that design, not a coincidence.
     *
     * <p>The input is assembled by the generator rather than by
     * {@code ZenginWriters}, so this is not the writer agreeing with itself.
     */
    @Test
    void inv1_writingAFileJustReadReproducesItByteForByte() {
        Seeded.property("INV-1: write(read(f)) == f", Seeded.DEFAULT_CASES, SEED,
                random -> RandomZenginFiles.bytes(random, DESCRIPTOR),
                generated -> {
                    ZenginFile parsed = ZenginReaders.readFile(
                            new ByteArrayInputStream(generated.bytes()), OPTIONS);
                    byte[] written = ZenginWriters.toByteArray(parsed, WriterOptions.defaults());

                    assertThat(written).isEqualTo(generated.bytes());
                });
    }

    /**
     * INV-2 — for any file built by the builder, {@code read(write(file))}
     * produces an equal file.
     *
     * <p>The dual of INV-1, and the one that tests the encoder: if the builder
     * placed a field at the wrong offset or padded it the wrong way, the
     * record read back would not carry the same bytes.
     */
    @Test
    void inv2_readingAFileJustWrittenReproducesIt() {
        Seeded.property("INV-2: read(write(f)) equals f", Seeded.DEFAULT_CASES, SEED,
                random -> RandomZenginFiles.built(random, DESCRIPTOR),
                built -> {
                    byte[] bytes = ZenginWriters.toByteArray(built, WriterOptions.defaults());
                    ZenginFile reread = ZenginReaders.readFile(new ByteArrayInputStream(bytes), OPTIONS);

                    assertThat(reread).isEqualTo(built);
                    assertThat(reread.framing()).isEqualTo(built.framing());
                    assertThat(reread.totalRecords()).isEqualTo(built.totalRecords());
                });
    }

    /**
     * INV-6 — for any built file, the trailer's count and total agree with the
     * records actually present.
     *
     * <p>The builder computes them, so this asserts that the computation and
     * the encoding of it agree — a trailer that says 3 while carrying 2
     * payments is the kind of file a bank rejects on receipt.
     */
    @Test
    void inv6_computedTrailersAgreeWithTheirContents() {
        Seeded.property("INV-6: trailer == contents", Seeded.DEFAULT_CASES, SEED,
                random -> RandomZenginFiles.built(random, DESCRIPTOR),
                built -> {
                    for (Batch batch : built.batches()) {
                        assertThat(batch.trailer()).isPresent();
                        assertThat(batch.trailer().orElseThrow().recordCount())
                                .isEqualTo(batch.computedCount());
                        assertThat(batch.trailer().orElseThrow().totalAmount())
                                .isEqualTo(batch.computedTotal());
                    }
                });
    }

    /**
     * INV-1 again, over a file that survives a second round trip unchanged.
     * A codec that loses a byte on the second pass but not the first is a real
     * failure mode, and cheap to rule out.
     */
    @Test
    void roundTrippingIsIdempotent() {
        Seeded.property("write(read(write(read(f)))) == write(read(f))", 200, SEED + 1,
                random -> RandomZenginFiles.bytes(random, DESCRIPTOR),
                generated -> {
                    byte[] once = ZenginWriters.toByteArray(
                            ZenginReaders.readFile(new ByteArrayInputStream(generated.bytes()), OPTIONS),
                            WriterOptions.defaults());
                    byte[] twice = ZenginWriters.toByteArray(
                            ZenginReaders.readFile(new ByteArrayInputStream(once), OPTIONS),
                            WriterOptions.defaults());

                    assertThat(twice).isEqualTo(once);
                });
    }

    /** R-C19: identical input must produce identical bytes, every time. */
    @Test
    void writingIsDeterministic() {
        Seeded.property("write is deterministic", 100, SEED + 2,
                random -> RandomZenginFiles.built(random, DESCRIPTOR),
                built -> assertThat(ZenginWriters.toByteArray(built, WriterOptions.defaults()))
                        .isEqualTo(ZenginWriters.toByteArray(built, WriterOptions.defaults())));
    }

    /**
     * R-C9: the separator style is configurable on write, and choosing one
     * overrides whatever the source file used.
     */
    @Test
    void anImposedSeparatorOverridesTheFilesOwn() {
        Seeded.property("imposed framing wins", 100, SEED + 3,
                random -> RandomZenginFiles.bytes(random, DESCRIPTOR),
                generated -> {
                    ZenginFile parsed = ZenginReaders.readFile(
                            new ByteArrayInputStream(generated.bytes()), OPTIONS);

                    byte[] none = ZenginWriters.toByteArray(parsed,
                            WriterOptions.separator(SeparatorStyle.NONE));
                    byte[] crlf = ZenginWriters.toByteArray(parsed,
                            WriterOptions.separator(SeparatorStyle.CRLF));

                    int records = parsed.totalRecords();
                    assertThat(none).hasSize(records * Fixtures.RECORD_LENGTH);
                    assertThat(crlf).hasSize(records * (Fixtures.RECORD_LENGTH + 2));

                    assertThat(ZenginReaders.readFile(new ByteArrayInputStream(none), OPTIONS).allData())
                            .isEqualTo(parsed.allData());
                });
    }

    /**
     * A file the builder produced with no payments at all still round-trips:
     * header, trailer declaring zero, end record.
     */
    @Test
    void anEmptyBatchRoundTrips() {
        ZenginFile built = Fixtures.builder(DESCRIPTOR)
                .header(header -> header.set("originatorCode", "9900000001"))
                .build();

        byte[] bytes = ZenginWriters.toByteArray(built, WriterOptions.defaults());
        ZenginFile reread = ZenginReaders.readFile(new ByteArrayInputStream(bytes), OPTIONS);

        assertThat(reread).isEqualTo(built);
        assertThat(reread.batches().get(0).computedCount()).isZero();
        assertThat(reread.batches().get(0).trailer().orElseThrow().totalAmount()).isZero();
    }
}
