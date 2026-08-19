package io.zengin4j.core.codec;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.error.MalformedFieldException;
import io.zengin4j.core.error.ZenginIOException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.testing.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// R-MEM5: the convenient API materialises by default.
class BatchReaderTest {

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    @Test
    void groupsRecordsIntoBatches() {
        try (BatchReader reader = ZenginReaders.batches(
                new ByteArrayInputStream(Fixtures.file(descriptor)), Fixtures.options())) {
            assertThat(reader.format().id()).isEqualTo(Fixtures.SOUGOU_FURIKOMI);
            assertThat(reader.hasNext()).isTrue();

            Batch batch = reader.next();

            assertThat(batch.header().originatorCode()).isEqualTo("9900000001");
            assertThat(batch.data()).hasSize(1);
            assertThat(batch.computedCount()).isEqualTo(1);
            assertThat(batch.computedTotal()).isEqualTo(Fixtures.AMOUNT);
            assertThat(batch.isComplete()).isTrue();
            assertThat(batch.trailer()).get().extracting(t -> t.totalAmount()).isEqualTo(Fixtures.AMOUNT);
            assertThat(batch.totalRecords()).isEqualTo(3);
            assertThat(reader.hasNext()).isFalse();
            assertThat(reader.endRecord()).isPresent();
            assertThat(reader.unbatched()).isEmpty();
            assertThat(reader.framing().separator()).isEqualTo(SeparatorStyle.CRLF);
            // The only warning is the one every 0.1.0 read raises: the layout
            // is provisional and the caller opted in (R-0.3).
            assertThat(reader.warnings()).extracting(ZenginWarning::code)
                    .containsExactly(ZenginWarning.UNVERIFIED_FORMAT);
        }
    }

    @Test
    void readsAWholeFile() {
        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(Fixtures.file(descriptor)), Fixtures.options());

        assertThat(file.format()).isEqualTo(Fixtures.SOUGOU_FURIKOMI);
        assertThat(file.batches()).hasSize(1);
        assertThat(file.totalRecords()).isEqualTo(4);
        assertThat(file.endRecord()).isPresent();
        assertThat(file.allData()).hasSize(1);
        assertThat(file.allData().get(0).amount()).isEqualTo(Fixtures.AMOUNT);
        assertThat(file.framing().isReproducible()).isTrue();
    }

    @Test
    void readsAWholeFileFromDisk(@TempDir Path directory) throws IOException {
        Path path = directory.resolve("payments.txt");
        Files.write(path, Fixtures.file(descriptor));

        ZenginFile file = ZenginReaders.readFile(path, Fixtures.options());

        assertThat(file.totalRecords()).isEqualTo(4);
    }

    @Test
    void streamsFromDisk(@TempDir Path directory) throws IOException {
        Path path = directory.resolve("payments.txt");
        Files.write(path, Fixtures.file(descriptor));

        try (ZenginReader reader = ZenginReaders.open(path, Fixtures.options())) {
            int records = 0;
            while (reader.hasNext()) {
                reader.next();
                records++;
            }
            assertThat(records).isEqualTo(4);
        }
        try (BatchReader reader = ZenginReaders.batches(path, Fixtures.options())) {
            assertThat(reader.next().data()).hasSize(1);
        }
    }

    @Test
    void reportsAMissingFile(@TempDir Path directory) {
        Path missing = directory.resolve("absent.txt");

        assertThatExceptionOfType(ZenginIOException.class)
                .isThrownBy(() -> ZenginReaders.readFile(missing, Fixtures.options()))
                .withMessageContaining("opening");
    }

    @Test
    void splitsMultipleBatches() {
        byte[] file = Fixtures.join(Fixtures.CRLF,
                Fixtures.header(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT),
                Fixtures.header(descriptor), Fixtures.data(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 2, Fixtures.AMOUNT * 2),
                Fixtures.end(descriptor));

        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(file), Fixtures.options());

        assertThat(parsed.batches()).hasSize(2);
        assertThat(parsed.batches().get(0).data()).hasSize(1);
        assertThat(parsed.batches().get(1).data()).hasSize(2);
        assertThat(parsed.batches().get(1).computedTotal()).isEqualTo(Fixtures.AMOUNT * 2);
        assertThat(parsed.totalRecords()).isEqualTo(8);
    }

    /// R-C2: a truncated file yields what it has, with the gap visible.
    @Test
    void reportsABatchWithNoTrailer() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), Fixtures.data(descriptor));

        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(file), Fixtures.options());

        assertThat(parsed.batches()).hasSize(1);
        assertThat(parsed.batches().get(0).isComplete()).isFalse();
        assertThat(parsed.endRecord()).isEmpty();
    }

    /// R-D8: a bad record is data, and the rest of the file still arrives.
    @Test
    void keepsMalformedRecordsInsideTheirBatch() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor),
                Fixtures.patch(Fixtures.data(descriptor), 0, "5"),
                Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));
        ReaderOptions options = Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build();

        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(file), options);

        Batch batch = parsed.batches().get(0);
        assertThat(batch.data()).hasSize(1);
        assertThat(batch.malformed()).hasSize(1);
        assertThat(batch.malformed().get(0).reason()).contains("unknown データ区分");
        assertThat(parsed.totalRecords()).isEqualTo(5);
    }

    /// A non-numeric amount cannot be materialised; lenient mode reports it as data.
    @Test
    void turnsAnUndecodableFieldIntoAMalformedRecordInLenientMode() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor),
                Fixtures.patch(Fixtures.data(descriptor), 80, "ABCDEFGHIJ"),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));

        ZenginFile lenient = ZenginReaders.readFile(new ByteArrayInputStream(file),
                Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build());
        assertThat(lenient.batches().get(0).malformed()).hasSize(1);
        assertThat(lenient.batches().get(0).data()).isEmpty();

        assertThatExceptionOfType(MalformedFieldException.class)
                .isThrownBy(() -> ZenginReaders.readFile(new ByteArrayInputStream(file), Fixtures.options()));
    }

    @Test
    void refusesToAdvancePastTheLastBatch() {
        try (BatchReader reader = ZenginReaders.batches(
                new ByteArrayInputStream(Fixtures.file(descriptor)), Fixtures.options())) {
            reader.next();
            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(reader::next);
        }
    }

    @Test
    void collectsRecordsBeforeTheFirstHeaderAsUnbatched() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor),
                Fixtures.data(descriptor));
        ReaderOptions options = Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build();

        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(file), options);

        assertThat(parsed.unbatched()).hasSize(1);
        assertThat(parsed.unbatched().get(0).reason()).contains("cannot appear here");
    }

    @Test
    void sumsAmountsAcrossABatch() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor),
                Fixtures.data(descriptor, "ﾃｽﾄ ｲﾁ", 1_000L),
                Fixtures.data(descriptor, "ﾃｽﾄ ﾆ", 2_000L),
                Fixtures.trailer(descriptor, 2, 3_000L), Fixtures.end(descriptor));

        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(file), Fixtures.options());

        assertThat(parsed.batches().get(0).computedTotal()).isEqualTo(3_000L);
        assertThat(parsed.allData()).extracting(DataRecord::amount).containsExactly(1_000L, 2_000L);
    }
}
