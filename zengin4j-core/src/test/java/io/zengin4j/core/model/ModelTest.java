package io.zengin4j.core.model;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.core.error.AmountOverflowException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.testing.Fixtures;
import org.junit.jupiter.api.Test;

/// The domain model's own contracts (§11).
class ModelTest {

    private final FormatDescriptor format = Fixtures.descriptor();
    private final RecordDescriptor dataDescriptor = format.record(RecordKind.DATA);

    /// R-D7: a total that will not fit is reported, never wrapped into a negative.
    @Test
    void reportsRatherThanWrapsAnOverflowingTotal() {
        var batch = new Batch(header(), List.of(data(Long.MAX_VALUE, 2), data(1L, 3)),
                Optional.empty(), List.of());

        assertThatExceptionOfType(AmountOverflowException.class)
                .isThrownBy(batch::computedTotal)
                .satisfies(e -> assertThat(e.recordNumber()).isEqualTo(3))
                .withMessageContaining("overflowed");
    }

    @Test
    void sumsAndCountsWhatIsActuallyPresent() {
        var batch = new Batch(header(), List.of(data(1_000L, 2), data(2_000L, 3)),
                Optional.empty(), List.of());

        assertThat(batch.computedCount()).isEqualTo(2);
        assertThat(batch.computedTotal()).isEqualTo(3_000L);
        assertThat(batch.isComplete()).isFalse();
        assertThat(batch.totalRecords()).isEqualTo(3);
    }

    @Test
    void refusesNullComponents() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Batch(null, List.of(), Optional.empty(), List.of()));
        assertThatNullPointerException()
                .isThrownBy(() -> new ZenginFile(format, null, Optional.empty(), List.of(),
                        FileFraming.none()));
    }

    @Test
    void describesFileFraming() {
        assertThat(FileFraming.none().separator()).isEqualTo(SeparatorStyle.NONE);
        assertThat(FileFraming.none().isReproducible()).isTrue();
        assertThat(FileFraming.none().byteOrderMarkPresent()).isFalse();
        assertThat(FileFraming.none().trailingSeparator()).isFalse();
        assertThat(FileFraming.none().hasSeparator()).isFalse();
        assertThat(new FileFraming(false, SeparatorStyle.MIXED, false, false).isReproducible()).isFalse();
    }

    /// OQ-4: the documented framing appends a separator to every record,
    /// including the last, so that is what a file built from scratch gets.
    @Test
    void conventionalFramingAppendsASeparatorAfterEveryRecord() {
        FileFraming conventional = FileFraming.conventional();

        assertThat(conventional.separator()).isEqualTo(SeparatorStyle.CRLF);
        assertThat(conventional.trailingSeparator()).isTrue();
        assertThat(conventional.hasSeparator()).isTrue();
        assertThat(conventional.byteOrderMarkPresent()).isFalse();
        assertThat(conventional.trailingEofByte()).isFalse();
    }

    @Test
    void mapsSeparatorBytesToStyles() {
        assertThat(SeparatorStyle.of(new byte[] {'\r', '\n'})).contains(SeparatorStyle.CRLF);
        assertThat(SeparatorStyle.of(new byte[] {'x'})).isEmpty();
        assertThat(SeparatorStyle.CRLF.bytes()).get().isEqualTo(new byte[] {'\r', '\n'});
        assertThat(SeparatorStyle.NONE.bytes()).get().isEqualTo(new byte[0]);
        assertThat(SeparatorStyle.MIXED.bytes()).isEmpty();
    }

    @Test
    void malformedRecordsCarryTheirBytesAndReason() {
        byte[] bytes = Fixtures.data(format);
        var record = new MalformedRecord(format.id(), 7, 840, bytes, "unknown データ区分");

        assertThat(record.kind()).isEqualTo(RecordKind.MALFORMED);
        assertThat(record.rawBytes()).isEqualTo(bytes).isNotSameAs(bytes);
        assertThat(record.recordNumber()).isEqualTo(7);
        assertThat(record.byteOffset()).isEqualTo(840);
        assertThat(record).isEqualTo(new MalformedRecord(format.id(), 99, 0, bytes, "unknown データ区分"))
                .hasSameHashCodeAs(new MalformedRecord(format.id(), 99, 0, bytes, "unknown データ区分"));
        assertThat(record).isNotEqualTo(new MalformedRecord(format.id(), 7, 840, bytes, "other reason"));
    }

    /// R-CLI4: unparseable bytes are still payment data, so they do not go in a log line.
    @Test
    void malformedRecordToStringDoesNotPrintTheRecord() {
        var record = new MalformedRecord(format.id(), 7, 840, Fixtures.data(format), "reason");

        assertThat(record.toString())
                .contains("recordNumber=7", "byteOffset=840", "bytes=120", "reason=reason")
                .doesNotContain(Fixtures.ACCOUNT);
    }

    @Test
    void zenginFileCountsEveryRecord() {
        var file = new ZenginFile(format,
                List.of(new Batch(header(), List.of(data(1L, 2)), Optional.empty(), List.of())),
                Optional.empty(),
                List.of(new MalformedRecord(format.id(), 4, 0, new byte[1], "stray")),
                FileFraming.none());

        assertThat(file.totalRecords()).isEqualTo(3);
        assertThat(file.format()).isEqualTo(format.id());
        assertThat(file.allData()).hasSize(1);
    }

    @Test
    void genericRecordsRejectUnknownFieldsGracefully() {
        GenericDataRecord record = data(1_000L, 1);

        assertThat(record.value("nope")).isEmpty();
        assertThat(record.values()).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> record.values().put("a", "b"));
    }

    private GenericHeaderRecord header() {
        return new GenericHeaderRecord(format.record(RecordKind.HEADER), 1, 0, Fixtures.header(format),
                Map.of(), io.zengin4j.core.charset.CodeKubun.JIS, "9900000001", "ﾃｽﾄ", Optional.empty());
    }

    private GenericDataRecord data(long amount, int recordNumber) {
        return new GenericDataRecord(dataDescriptor, recordNumber, 0, Fixtures.data(format), Map.of(), amount);
    }
}
