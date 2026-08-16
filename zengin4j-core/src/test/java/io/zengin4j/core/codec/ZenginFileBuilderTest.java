package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.error.UnverifiedFormatException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.generated.SougouFurikomiData;
import io.zengin4j.core.model.generated.SougouFurikomiHeader;
import io.zengin4j.core.testing.Fixtures;
import java.time.MonthDay;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Issue 2.1: building files with computed trailers.
 */
class ZenginFileBuilderTest {

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    @Test
    void buildsAFileWithAComputedTrailer() {
        ZenginFile file = Fixtures.builder(descriptor)
                .header(header -> header
                        .set("originatorCode", "9900000001")
                        .set("originatorName", "ﾃｽﾄｼﾖｳｼﾞ")
                        .set("valueDate", MonthDay.of(9, 30)))
                .payment(payment -> payment
                        .set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ")
                        .set("accountNumber", "9876543")
                        .set("amount", 150_000L))
                .payment(payment -> payment
                        .set("beneficiaryName", "ﾃｽﾄ ﾊﾅｺ")
                        .set("amount", 2_500L))
                .build();

        Batch batch = file.batches().get(0);
        assertThat(batch.data()).hasSize(2);
        assertThat(batch.trailer().orElseThrow().recordCount()).isEqualTo(2);
        assertThat(batch.trailer().orElseThrow().totalAmount()).isEqualTo(152_500L);
        assertThat(file.endRecord()).isPresent();
        assertThat(file.totalRecords()).isEqualTo(5);
    }

    /** The built records are the generated, format-shaped types — not a parallel model. */
    @Test
    void buildsTheGeneratedRecordTypes() {
        ZenginFile file = simpleFile();

        assertThat(file.batches().get(0).header()).isInstanceOf(SougouFurikomiHeader.class);
        assertThat(file.allData().get(0)).isInstanceOf(SougouFurikomiData.class);

        SougouFurikomiHeader header = (SougouFurikomiHeader) file.batches().get(0).header();
        assertThat(header.originatorCode()).isEqualTo("9900000001");
        assertThat(header.valueDate()).contains(MonthDay.of(9, 30));
        assertThat(header.valueDateRaw()).isEqualTo("0930");

        SougouFurikomiData data = (SougouFurikomiData) file.allData().get(0);
        assertThat(data.beneficiaryName()).isEqualTo("ﾔﾏﾀﾞ ﾀﾛｳ");
        assertThat(data.amount()).isEqualTo(150_000L);
        assertThat(data.dataKubun()).isEqualTo("2");
    }

    /** Record numbers are assigned in order, which is what lets the writer restore it. */
    @Test
    void numbersRecordsInFileOrder() {
        ZenginFile file = simpleFile();

        assertThat(file.batches().get(0).header().recordNumber()).isEqualTo(1);
        assertThat(file.allData().get(0).recordNumber()).isEqualTo(2);
        assertThat(file.batches().get(0).trailer().orElseThrow().recordNumber()).isEqualTo(3);
        assertThat(file.endRecord().orElseThrow().recordNumber()).isEqualTo(4);
    }

    /**
     * Built records carry the byte offset they would occupy, separators
     * included — so a finding reported against a built file points at the same
     * place it would in the file once written.
     */
    @Test
    void assignsByteOffsetsThatMatchWhereTheRecordsAreWritten() {
        ZenginFile withSeparators = simpleFile();
        ZenginFile without = Fixtures.builder(descriptor)
                .framing(FileFraming.none())
                .header(header -> header.set("originatorCode", "9900000001"))
                .payment(payment -> payment.set("amount", 1L))
                .build();

        assertThat(offsets(withSeparators)).containsExactly(0L, 122L, 244L, 366L);
        assertThat(offsets(without)).containsExactly(0L, 120L, 240L, 360L);
        // The offsets are where the writer actually puts them.
        byte[] bytes = ZenginWriters.toByteArray(withSeparators, WriterOptions.defaults());
        assertThat(bytes).hasSize(4 * 122);
        assertThat((char) bytes[244]).isEqualTo('8');
    }

    /** Zero is a legitimate amount; only a negative one is a programming error. */
    @Test
    void acceptsAZeroAmount() {
        ZenginFile file = Fixtures.builder(descriptor)
                .header(header -> header.set("originatorCode", "9900000001"))
                .payment(payment -> payment.set("amount", 0L))
                .build();

        assertThat(file.allData().get(0)).isInstanceOf(SougouFurikomiData.class);
        assertThat(((SougouFurikomiData) file.allData().get(0)).amount()).isZero();
        assertThat(file.batches().get(0).trailer().orElseThrow().totalAmount()).isZero();
    }

    /** Every setter returns the collector, so any of them can be chained from. */
    @Test
    void everySetterIsChainable() {
        ZenginFile file = Fixtures.builder(descriptor)
                .header(header -> header
                        .set("valueDate", MonthDay.of(9, 30))
                        .set("originatorCode", "9900000001")
                        .set("originatorName", "ﾃｽﾄｼﾖｳｼﾞ"))
                .payment(payment -> payment
                        .set("amount", 150_000L)
                        .set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ"))
                .build();

        SougouFurikomiHeader header = (SougouFurikomiHeader) file.batches().get(0).header();
        assertThat(header.originatorCode()).isEqualTo("9900000001");
        assertThat(header.valueDateRaw()).isEqualTo("0930");
        assertThat(((SougouFurikomiData) file.allData().get(0)).beneficiaryName()).isEqualTo("ﾔﾏﾀﾞ ﾀﾛｳ");
    }

    @Test
    void supportsMultipleBatches() {
        ZenginFile file = Fixtures.builder(descriptor)
                .header(header -> header.set("originatorCode", "9900000001"))
                .payment(payment -> payment.set("amount", 100L))
                .header(header -> header.set("originatorCode", "9900000002"))
                .payment(payment -> payment.set("amount", 200L))
                .payment(payment -> payment.set("amount", 300L))
                .build();

        assertThat(file.batches()).hasSize(2);
        assertThat(file.batches().get(0).trailer().orElseThrow().totalAmount()).isEqualTo(100L);
        assertThat(file.batches().get(1).trailer().orElseThrow().recordCount()).isEqualTo(2);
        assertThat(file.batches().get(1).trailer().orElseThrow().totalAmount()).isEqualTo(500L);
        assertThat(file.totalRecords()).isEqualTo(8);
    }

    /**
     * Overriding the computed trailer is how you build a fixture for the
     * validation rule that has to catch a trailer disagreeing with its
     * contents (V-301, V-302).
     */
    @Test
    void anExplicitTrailerOverridesTheComputedOne() {
        ZenginFile file = Fixtures.builder(descriptor)
                .header(header -> header.set("originatorCode", "9900000001"))
                .payment(payment -> payment.set("amount", 100L))
                .trailer(trailer -> trailer.set("recordCount", 99L).set("totalAmount", 12_345L))
                .build();

        Batch batch = file.batches().get(0);
        assertThat(batch.trailer().orElseThrow().recordCount()).isEqualTo(99);
        assertThat(batch.trailer().orElseThrow().totalAmount()).isEqualTo(12_345L);
        assertThat(batch.computedCount()).isEqualTo(1);
        assertThat(batch.computedTotal()).isEqualTo(100L);
    }

    @Test
    void theEndRecordCanBeCustomised() {
        ZenginFile file = Fixtures.builder(descriptor)
                .header(header -> header.set("originatorCode", "9900000001"))
                .endRecord(end -> end.set("dummy", "X"))
                .build();

        assertThat(new String(file.endRecord().orElseThrow().rawBytes(),
                java.nio.charset.StandardCharsets.US_ASCII)).startsWith("9X");
    }

    @Test
    void framingAndCharsetAreConfigurable() {
        ZenginFile file = Fixtures.builder(descriptor)
                .charset(ZenginCharset.MS932)
                .framing(new FileFraming(false, SeparatorStyle.LF, false, true))
                .header(header -> header.set("originatorCode", "9900000001"))
                .build();

        assertThat(file.framing().separator()).isEqualTo(SeparatorStyle.LF);
        assertThat(file.framing().trailingSeparator()).isFalse();
        assertThat(file.framing().trailingEofByte()).isTrue();
        assertThat(ZenginWriters.toByteArray(file, WriterOptions.defaults()))
                .hasSize(3 * Fixtures.RECORD_LENGTH + 2 + 1);
    }

    /** Defaults to the framing the published record-length statements describe (OQ-4). */
    @Test
    void defaultsToConventionalFraming() {
        assertThat(simpleFile().framing()).isEqualTo(FileFraming.conventional());
    }

    /**
     * OQ-10: building on a provisional layout requires saying so.
     *
     * <p>Every other test here goes through {@code Fixtures.builder}, which
     * opts in. This is the one place the raw entry point is used, because this
     * is the behaviour being asserted.
     */
    @Test
    void refusesToBuildOnAnUnverifiedFormatWithoutAnExplicitOptIn() {
        assertThat(descriptor.verified()).isFalse();

        assertThatExceptionOfType(UnverifiedFormatException.class)
                .isThrownBy(() -> ZenginFileBuilder.forFormat(descriptor)
                        .header(header -> header.set("originatorCode", "9900000001"))
                        .build())
                .satisfies(thrown -> {
                    assertThat(thrown.formatId()).isEqualTo("sougou-furikomi");
                    assertThat(thrown.operation())
                            .isEqualTo(UnverifiedFormatException.Operation.BUILDING);
                    // The remedy named must be the one that actually applies here.
                    assertThat(thrown.messageEn())
                            .contains("ZenginFileBuilder.forFormat(...).allowUnverifiedFormats(true)")
                            .doesNotContain("ReaderOptions");
                    assertThat(thrown.messageJa()).isNotBlank();
                });

        // And the opt-in is what makes it work.
        assertThat(ZenginFileBuilder.forFormat(descriptor)
                .allowUnverifiedFormats(true)
                .header(header -> header.set("originatorCode", "9900000001"))
                .build()
                .totalRecords()).isEqualTo(3);
    }

    /** Reading and building name different remedies, because they need different ones. */
    @Test
    void theReadingRemedyIsStillNamedWhenReadingIsWhatFailed() {
        UnverifiedFormatException reading = new UnverifiedFormatException("sougou-furikomi");

        assertThat(reading.operation()).isEqualTo(UnverifiedFormatException.Operation.READING);
        assertThat(reading.messageEn())
                .contains("ReaderOptions.builder().allowUnverifiedFormats(true)")
                .doesNotContain("ZenginFileBuilder");
    }

    @Test
    void rejectsMisuse() {
        assertThatIllegalStateException()
                .isThrownBy(() -> Fixtures.builder(descriptor).build())
                .withMessageContaining("at least one header");
        assertThatIllegalStateException()
                .isThrownBy(() -> Fixtures.builder(descriptor)
                        .payment(payment -> payment.set("amount", 1L)))
                .withMessageContaining("must follow a header");
        assertThatIllegalStateException()
                .isThrownBy(() -> Fixtures.builder(descriptor)
                        .trailer(trailer -> trailer.set("recordCount", 1L)))
                .withMessageContaining("must follow a header");
    }

    @Test
    void rejectsUnknownFieldsAndNegativeValues() {
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> Fixtures.builder(descriptor)
                        .header(header -> header.set("noSuchField", "x")))
                .withMessageContaining("has no field 'noSuchField'");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Fixtures.builder(descriptor)
                        .header(header -> header.set("originatorCode", -1L)))
                .withMessageContaining("cannot carry a negative value");
        // Every overload validates the id, not just the String one.
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> Fixtures.builder(descriptor)
                        .header(header -> header.set("noSuchField", 1L)))
                .withMessageContaining("has no field 'noSuchField'");
        assertThatExceptionOfType(FormatDescriptorException.class)
                .isThrownBy(() -> Fixtures.builder(descriptor)
                        .header(header -> header.set("noSuchField", MonthDay.of(9, 30))))
                .withMessageContaining("has no field 'noSuchField'");
    }

    @Test
    void rejectsValuesThatDoNotFitTheirField() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Fixtures.builder(descriptor)
                        .header(header -> header.set("originatorCode", "999999999999999"))
                        .build())
                .withMessageContaining("does not fit");
    }

    @Test
    void encoderFillsConstantsAndPadsTheRest() {
        byte[] frame = RecordEncoder.encode(descriptor.record(RecordKind.DATA),
                ZenginCharset.MS932, Map.of("amount", "150000"));

        assertThat(frame).hasSize(120);
        // データ区分 constant, then zero-padded numerics and space-padded text.
        assertThat((char) frame[0]).isEqualTo('2');
        assertThat(new String(frame, 80, 10, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("0000150000");
        assertThat(new String(frame, 50, 30, java.nio.charset.StandardCharsets.US_ASCII)).isBlank();
    }

    @Test
    void encoderRejectsUnknownFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RecordEncoder.encode(descriptor.record(RecordKind.END),
                        ZenginCharset.MS932, Map.of("nope", "x")))
                .withMessageContaining("has no field 'nope'");
    }

    private static java.util.List<Long> offsets(ZenginFile file) {
        java.util.List<Long> result = new java.util.ArrayList<>();
        Batch batch = file.batches().get(0);
        result.add(batch.header().byteOffset());
        batch.data().forEach(record -> result.add(record.byteOffset()));
        batch.trailer().ifPresent(record -> result.add(record.byteOffset()));
        file.endRecord().ifPresent(record -> result.add(record.byteOffset()));
        return result;
    }

    private ZenginFile simpleFile() {
        return Fixtures.builder(descriptor)
                .header(header -> header
                        .set("originatorCode", "9900000001")
                        .set("valueDate", MonthDay.of(9, 30)))
                .payment(payment -> payment
                        .set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ")
                        .set("amount", 150_000L))
                .build();
    }
}
