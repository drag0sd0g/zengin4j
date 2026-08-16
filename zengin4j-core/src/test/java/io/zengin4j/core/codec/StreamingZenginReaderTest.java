package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.error.AmbiguousFormatException;
import io.zengin4j.core.error.MalformedFileException;
import io.zengin4j.core.error.UnsupportedEncodingVariantException;
import io.zengin4j.core.error.UnsupportedFormatException;
import io.zengin4j.core.error.UnverifiedFormatException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.core.testing.Fixtures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Milestone M1 and the framing requirements of issues 1.6 and 1.8.
 */
class StreamingZenginReaderTest {

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    /** M1: parse a synthetic 総合振込 file end to end. */
    @Test
    void parsesASyntheticFileEndToEnd() {
        List<RecordKind> kinds = new ArrayList<>();
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            assertThat(reader.format().id()).isEqualTo(Fixtures.SOUGOU_FURIKOMI);
            assertThat(reader.recordLength()).isEqualTo(Fixtures.RECORD_LENGTH);

            while (reader.hasNext()) {
                RecordView view = reader.next();
                kinds.add(view.kind());
                switch (view.kind()) {
                    case HEADER -> {
                        assertThat(view.asString("originatorCode")).isEqualTo("9900000001");
                        assertThat(view.asString("originatorName")).isEqualTo("ﾃｽﾄｼﾖｳｼﾞ");
                        assertThat(view.asMonthDay(view.field("valueDate")))
                                .contains(MonthDay.of(9, 30));
                        assertThat(view.asCodeKubun(view.field("codeKubun"))).isEqualTo(CodeKubun.JIS);
                    }
                    case DATA -> {
                        assertThat(view.asLong(view.field("amount"))).isEqualTo(Fixtures.AMOUNT);
                        assertThat(view.asString("beneficiaryName")).isEqualTo(Fixtures.BENEFICIARY);
                        assertThat(view.asString("beneficiaryBankName")).isEqualTo(Fixtures.BANK_NAME);
                        assertThat(view.asString("accountNumber")).isEqualTo(Fixtures.ACCOUNT);
                        assertThat(view.asString("customerCode1")).isEqualTo("INV2026000");
                        assertThat(view.asString("customerCode2")).isEqualTo("1");
                    }
                    case TRAILER -> {
                        assertThat(view.asLong(view.field("recordCount"))).isEqualTo(1);
                        assertThat(view.asLong(view.field("totalAmount"))).isEqualTo(Fixtures.AMOUNT);
                    }
                    case END -> assertThat(view.rawBytes()).hasSize(Fixtures.RECORD_LENGTH);
                    case MALFORMED -> throw new AssertionError("unexpected malformed record");
                }
            }
        }
        assertThat(kinds).containsExactly(RecordKind.HEADER, RecordKind.DATA, RecordKind.TRAILER, RecordKind.END);
    }

    /**
     * A voicing mark is a character of its own, and a byte of its own.
     *
     * <p>ﾃｽﾄｷﾞﾝｺｳ renders as seven characters — テストギンコウ — but the ｷﾞ is
     * ｷ followed by a standalone ﾞ, so it is eight code points and eight bytes.
     * Truncating between them turns ギ into キ and the name into a different
     * one, which is the hazard §17 is about.
     *
     * <p>The same string is 24 bytes in UTF-8. A 15-byte 被仕向銀行名 field
     * holds it under MS932 and cannot hold it under UTF-8, which is why every
     * length in the codec is a byte count (R-C15).
     */
    @Test
    void voicingMarksAreSeparateCharactersAndSeparateBytes() {
        assertThat(Fixtures.BANK_NAME).hasSize(8);
        assertThat(Fixtures.BANK_NAME.charAt(4)).isEqualTo('ﾞ');
        assertThat(ZenginCharset.MS932.encode(Fixtures.BANK_NAME)).hasSize(8);
        assertThat(ZenginCharset.UTF_8.encode(Fixtures.BANK_NAME)).hasSize(24);

        String withoutTheMark = Fixtures.BANK_NAME.substring(0, 4) + Fixtures.BANK_NAME.substring(5);
        assertThat(withoutTheMark).isEqualTo("ﾃｽﾄｷﾝｺｳ").isNotEqualTo(Fixtures.BANK_NAME);

        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            reader.next();
            RecordView data = reader.next();
            // The field is 30 bytes; the name occupies eight of them.
            assertThat(data.asPaddedString(data.field("beneficiaryName")))
                    .isEqualTo(Fixtures.BENEFICIARY + " ".repeat(22));
        }
    }

    @Test
    void acceptsEverySeparatorConvention() {
        assertSeparator(Fixtures.CRLF, SeparatorStyle.CRLF);
        assertSeparator(Fixtures.LF, SeparatorStyle.LF);
        assertSeparator(Fixtures.CR, SeparatorStyle.CR);
        assertSeparator(Fixtures.NO_SEPARATOR, SeparatorStyle.NONE);
    }

    private void assertSeparator(byte[] separator, SeparatorStyle expected) {
        byte[] file = Fixtures.join(separator, Fixtures.header(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));
        try (ZenginReader reader = open(file)) {
            assertThat(drain(reader)).hasSize(4);
            assertThat(reader.framing().separator()).isEqualTo(expected);
            assertThat(reader.framing().isReproducible()).isTrue();
        }
    }

    /** R-C6: conventions may vary within one file, and that is recorded rather than normalised. */
    @Test
    void reportsMixedSeparators() {
        byte[] file = Fixtures.concat(List.of(
                Fixtures.header(descriptor), Fixtures.CRLF,
                Fixtures.data(descriptor), Fixtures.LF,
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.CRLF,
                Fixtures.end(descriptor)));
        try (ZenginReader reader = open(file)) {
            assertThat(drain(reader)).hasSize(4);
            assertThat(reader.framing().separator()).isEqualTo(SeparatorStyle.MIXED);
            assertThat(reader.framing().isReproducible()).isFalse();
            assertThat(reader.warnings()).extracting(ZenginWarning::code)
                    .contains(ZenginWarning.MIXED_SEPARATORS);
        }
    }

    /** R-C8: a trailing EOF byte is accepted and recorded. */
    @Test
    void acceptsATrailingEofByte() {
        byte[] file = Fixtures.concat(List.of(Fixtures.file(descriptor), new byte[] {0x1A}));
        try (ZenginReader reader = open(file)) {
            assertThat(drain(reader)).hasSize(4);
            assertThat(reader.framing().trailingEofByte()).isTrue();
        }
    }

    @Test
    void rejectsDataAfterTheEofByteInStrictMode() {
        byte[] file = Fixtures.concat(List.of(Fixtures.file(descriptor), new byte[] {0x1A},
                Fixtures.data(descriptor)));
        assertThatExceptionOfType(MalformedFileException.class)
                .isThrownBy(() -> {
                    try (ZenginReader reader = open(file)) {
                        drain(reader);
                    }
                })
                .withMessageContaining("end-of-file marker");
    }

    @Test
    void ignoresDataAfterTheEofByteInLenientMode() {
        byte[] file = Fixtures.concat(List.of(Fixtures.file(descriptor), new byte[] {0x1A},
                Fixtures.data(descriptor)));
        try (ZenginReader reader = open(file, Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build())) {
            assertThat(drain(reader)).hasSize(4);
            assertThat(reader.warnings()).extracting(ZenginWarning::code)
                    .contains(ZenginWarning.DATA_AFTER_EOF_BYTE);
        }
    }

    /** R-C10: a byte order mark is rejected by default. */
    @Test
    void rejectsAByteOrderMarkByDefault() {
        byte[] file = withByteOrderMark(Fixtures.file(descriptor));
        assertThatExceptionOfType(MalformedFileException.class)
                .isThrownBy(() -> open(file).close())
                .withMessageContaining("byte order mark");
    }

    @Test
    void stripsAByteOrderMarkWhenAsked() {
        byte[] file = withByteOrderMark(Fixtures.file(descriptor));
        ReaderOptions options = Fixtures.optionsBuilder().byteOrderMark(ByteOrderMarkPolicy.STRIP).build();
        try (ZenginReader reader = open(file, options)) {
            assertThat(drain(reader)).hasSize(4);
            assertThat(reader.framing().byteOrderMarkPresent()).isTrue();
            assertThat(reader.warnings()).extracting(ZenginWarning::code)
                    .contains(ZenginWarning.BYTE_ORDER_MARK_STRIPPED);
        }
    }

    /** R-0.1, issue 1.9: a provisional layout is refused unless the caller opts in. */
    @Test
    void refusesAnUnverifiedFormatUnlessAllowed() {
        ReaderOptions options = ReaderOptions.builder()
                .registry(Fixtures.registry())
                .warningListener(warning -> {
                })
                .build();
        assertThatExceptionOfType(UnverifiedFormatException.class)
                .isThrownBy(() -> ZenginReaders.open(new ByteArrayInputStream(Fixtures.file(descriptor)), options))
                .satisfies(e -> assertThat(e.formatId()).isEqualTo("sougou-furikomi"))
                .withMessageContaining("allowUnverifiedFormats");
    }

    @Test
    void warnsWhenAnUnverifiedFormatIsAllowed() {
        List<ZenginWarning> raised = new ArrayList<>();
        ReaderOptions options = ReaderOptions.builder()
                .registry(Fixtures.registry())
                .allowUnverifiedFormats(true)
                .warningListener(raised::add)
                .build();
        try (ZenginReader reader = open(Fixtures.file(descriptor), options)) {
            assertThat(raised).extracting(ZenginWarning::code).contains(ZenginWarning.UNVERIFIED_FORMAT);
            assertThat(reader.warnings()).isEqualTo(raised);
        }
    }

    @Test
    void reportsAnUnknownTypeCode() {
        byte[] file = Fixtures.patch(Fixtures.file(descriptor), 1, "99");
        assertThatExceptionOfType(UnsupportedFormatException.class)
                .isThrownBy(() -> open(file).close())
                .satisfies(e -> assertThat(e.typeCode()).isEqualTo("99"));
    }

    @Test
    void reportsAnAmbiguousTypeCode() {
        FormatRegistry registry = FormatRegistry.builder()
                .codeLists(Fixtures.registry().codeLists())
                .register(descriptor)
                .register(Fixtures.renamed(descriptor, "sougou-furikomi-variant"))
                .build();
        ReaderOptions options = Fixtures.optionsBuilder().registry(registry).build();
        assertThatExceptionOfType(AmbiguousFormatException.class)
                .isThrownBy(() -> open(Fixtures.file(descriptor), options).close())
                .satisfies(e -> assertThat(e.candidates())
                        .containsExactly("sougou-furikomi", "sougou-furikomi-variant"));
    }

    /** R-C14: EBCDIC is named and refused, never decoded as JIS. */
    @Test
    void refusesAnEbcdicFile() {
        byte[] header = Fixtures.patch(Fixtures.header(descriptor), 3, "1");
        byte[] file = Fixtures.join(Fixtures.CRLF, header, Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), Fixtures.end(descriptor));
        assertThatExceptionOfType(UnsupportedEncodingVariantException.class)
                .isThrownBy(() -> open(file).close())
                .satisfies(e -> assertThat(e.found()).isEqualTo(CodeKubun.EBCDIC));
    }

    @Test
    void refusesAFileThatDoesNotBeginWithAHeader() {
        assertThatExceptionOfType(MalformedFileException.class)
                .isThrownBy(() -> open(Fixtures.data(descriptor)).close())
                .withMessageContaining("must begin with a header record");
    }

    @Test
    void refusesAFileTooShortToIdentify() {
        assertThatExceptionOfType(MalformedFileException.class)
                .isThrownBy(() -> open(new byte[] {'1'}).close())
                .withMessageContaining("too short");
    }

    /** §12.4: the state machine rejects a record that cannot appear where it is. */
    @Test
    void rejectsAnOutOfSequenceRecordInStrictMode() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), Fixtures.header(descriptor));
        assertThatExceptionOfType(MalformedFileException.class)
                .isThrownBy(() -> {
                    try (ZenginReader reader = open(file)) {
                        drain(reader);
                    }
                })
                .withMessageContaining("cannot appear here");
    }

    /** R-C3: lenient mode resynchronises by one record length, never by scanning. */
    @Test
    void emitsMalformedRecordsAndKeepsGoingInLenientMode() {
        byte[] broken = Fixtures.patch(Fixtures.data(descriptor), 0, "5");
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), broken,
                Fixtures.data(descriptor), Fixtures.trailer(descriptor, 2, Fixtures.AMOUNT * 2),
                Fixtures.end(descriptor));
        try (ZenginReader reader = open(file, Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build())) {
            List<RecordKind> kinds = new ArrayList<>();
            List<String> reasons = new ArrayList<>();
            while (reader.hasNext()) {
                RecordView view = reader.next();
                kinds.add(view.kind());
                view.malformedReason().ifPresent(reasons::add);
            }
            assertThat(kinds).containsExactly(RecordKind.HEADER, RecordKind.MALFORMED, RecordKind.DATA,
                    RecordKind.TRAILER, RecordKind.END);
            assertThat(reasons).singleElement().asString().contains("unknown データ区分 '5'");
        }
    }

    @Test
    void reportsATruncatedFinalRecord() {
        byte[] full = Fixtures.file(descriptor);
        byte[] truncated = java.util.Arrays.copyOf(full, full.length - 60);
        assertThatExceptionOfType(MalformedFileException.class)
                .isThrownBy(() -> {
                    try (ZenginReader reader = open(truncated)) {
                        drain(reader);
                    }
                })
                .withMessageContaining("truncated");
    }

    @Test
    void surfacesATruncatedFinalRecordAsMalformedInLenientMode() {
        byte[] full = Fixtures.file(descriptor);
        byte[] truncated = java.util.Arrays.copyOf(full, full.length - 60);
        try (ZenginReader reader = open(truncated, Fixtures.optionsBuilder().mode(ParseMode.LENIENT).build())) {
            List<RecordView> views = new ArrayList<>();
            while (reader.hasNext()) {
                views.add(reader.next());
            }
            assertThat(views).hasSize(4);
            assertThat(views.get(3).isMalformed()).isTrue();
            assertThat(views.get(3).length()).isLessThan(Fixtures.RECORD_LENGTH);
        }
    }

    /** R-C2: a missing end record is a finding for the validator, not a parse failure. */
    @Test
    void acceptsAFileWithNoEndRecord() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT));
        try (ZenginReader reader = open(file)) {
            assertThat(drain(reader)).containsExactly(RecordKind.HEADER, RecordKind.DATA, RecordKind.TRAILER);
        }
    }

    /** R-C1: several batches in one file parse; whether that is allowed is a validation question. */
    @Test
    void acceptsMultipleBatches() {
        byte[] file = Fixtures.join(Fixtures.CRLF,
                Fixtures.header(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT),
                Fixtures.header(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT),
                Fixtures.end(descriptor));
        try (ZenginReader reader = open(file)) {
            assertThat(drain(reader)).hasSize(7);
        }
    }

    @Test
    void acceptsAnExplicitFormat() {
        ReaderOptions options = Fixtures.optionsBuilder().format(Fixtures.SOUGOU_FURIKOMI).build();
        try (ZenginReader reader = open(Fixtures.file(descriptor), options)) {
            assertThat(reader.format().id()).isEqualTo(Fixtures.SOUGOU_FURIKOMI);
            assertThat(drain(reader)).hasSize(4);
        }
    }

    @Test
    void reportsAnUnregisteredExplicitFormat() {
        ReaderOptions options = Fixtures.optionsBuilder().format(FormatId.of("not-registered")).build();
        assertThatExceptionOfType(io.zengin4j.core.error.FormatDescriptorException.class)
                .isThrownBy(() -> open(Fixtures.file(descriptor), options).close())
                .withMessageContaining("not registered");
    }

    /** R-MEM1: the buffer holds whole records, and small buffers still work. */
    @Test
    void readsCorrectlyWithABufferOfOneRecord() {
        byte[] file = Fixtures.join(Fixtures.CRLF, Fixtures.header(descriptor),
                Fixtures.data(descriptor), Fixtures.data(descriptor),
                Fixtures.trailer(descriptor, 2, Fixtures.AMOUNT * 2), Fixtures.end(descriptor));
        ReaderOptions options = Fixtures.optionsBuilder().bufferRecords(1).build();
        try (ZenginReader reader = open(file, options)) {
            assertThat(drain(reader)).hasSize(5);
        }
    }

    /** Reading in small chunks exercises buffer refills across record boundaries. */
    @Test
    void readsCorrectlyFromADribblingStream() {
        byte[] file = Fixtures.file(descriptor);
        long total = 0;
        try (ZenginReader reader = ZenginReaders.open(new DribblingStream(file, 7), Fixtures.options())) {
            while (reader.hasNext()) {
                RecordView view = reader.next();
                if (view.kind() == RecordKind.DATA) {
                    total += view.asLong(view.field("amount"));
                }
            }
        }
        assertThat(total).isEqualTo(Fixtures.AMOUNT);
    }

    /**
     * The framing paths — skipping a leading byte order mark, consuming a
     * separator run, consuming the EOF byte — each have to survive the buffer
     * running out mid-way. A one-record buffer fed a byte at a time puts a
     * refill at every one of those points, in every framing combination.
     *
     * <p>Round-tripping is the assertion rather than a field value, because a
     * refill that loses or duplicates a byte shows up in the output even when
     * every decoded field still looks plausible (INV-1).
     */
    @Test
    void survivesABufferRefillAtEveryFramingBoundary() {
        for (SeparatorStyle style : List.of(SeparatorStyle.NONE, SeparatorStyle.CR,
                SeparatorStyle.LF, SeparatorStyle.CRLF)) {
            for (boolean mark : List.of(true, false)) {
                for (boolean eof : List.of(true, false)) {
                    FileFraming framing = new FileFraming(mark, style, style != SeparatorStyle.NONE, eof);
                    byte[] source = Fixtures.framed(descriptor, framing);
                    ReaderOptions tiny = Fixtures.optionsBuilder()
                            .byteOrderMark(ByteOrderMarkPolicy.STRIP)
                            .bufferRecords(1)
                            .build();

                    ZenginFile file = ZenginReaders.readFile(new DribblingStream(source, 1), tiny);

                    assertThat(file.framing())
                            .as("%s mark=%s eof=%s", style, mark, eof)
                            .isEqualTo(framing);
                    assertThat(ZenginWriters.toByteArray(file, WriterOptions.defaults()))
                            .as("%s mark=%s eof=%s", style, mark, eof)
                            .isEqualTo(source);
                }
            }
        }
    }

    /**
     * A run of separators longer than whatever is left in the buffer.
     *
     * <p>{@code fill} drains the stream greedily, so feeding it slowly does not
     * produce a partial buffer — the only way to run out mid-framing is for the
     * framing itself to be longer than the buffer's tail. Blank lines between
     * records do that, and a file that has been through a text editor has them.
     *
     * <p>Such a run is reported as {@link SeparatorStyle#MIXED}, and that is the
     * right answer rather than a limitation: nothing distinguishes "one CRLF
     * plus blank lines" from "two conventions in one file", and both are
     * equally unreproducible. The reader says so and the writer refuses,
     * instead of silently normalising the blank lines away.
     */
    @Test
    void readsThroughASeparatorRunLongerThanTheBuffer() {
        byte[] blankLines = new byte[400];
        for (int i = 0; i < blankLines.length; i += 2) {
            blankLines[i] = '\r';
            blankLines[i + 1] = '\n';
        }
        byte[] file = Fixtures.concat(List.of(
                Fixtures.header(descriptor), blankLines,
                Fixtures.data(descriptor), blankLines,
                Fixtures.trailer(descriptor, 1, Fixtures.AMOUNT), blankLines,
                Fixtures.end(descriptor), blankLines,
                new byte[] {RecordFramer.EOF_BYTE}));
        ReaderOptions tiny = Fixtures.optionsBuilder().bufferRecords(1).build();

        try (ZenginReader reader = ZenginReaders.open(new ByteArrayInputStream(file), tiny)) {
            assertThat(drain(reader)).hasSize(4);
            assertThat(reader.framing().separator()).isEqualTo(SeparatorStyle.MIXED);
            assertThat(reader.framing().isReproducible()).isFalse();
            assertThat(reader.framing().trailingEofByte()).isTrue();
        }
    }

    /** A reader owns the stream it opened and must release it (R-C21). */
    @Test
    void closingTheReaderClosesTheStreamItOpened() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream source = new ByteArrayInputStream(Fixtures.file(descriptor)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };

        try (ZenginReader reader = ZenginReaders.open(source, Fixtures.options())) {
            reader.next();
        }

        assertThat(closed).isTrue();
    }

    @Test
    void materialisesTheGeneratedRecordTypes() {
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            reader.next();
            ZenginRecord record = reader.next().materialize();
            assertThat(record).isInstanceOf(DataRecord.class);
            assertThat(((DataRecord) record).amount()).isEqualTo(Fixtures.AMOUNT);
            assertThat(record.getClass().getName())
                    .isEqualTo("io.zengin4j.core.model.generated.SougouFurikomiData");
        }
    }

    @Test
    void refusesToAdvancePastTheEnd() {
        try (ZenginReader reader = open(Fixtures.file(descriptor))) {
            drain(reader);
            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(reader::next);
        }
    }

    @Test
    void reportsARecordLengthOverride() {
        ReaderOptions options = Fixtures.optionsBuilder().recordLength(122).build();
        byte[] padded = Fixtures.join(new byte[] {' ', ' '}, Fixtures.header(descriptor));
        try (ZenginReader reader = open(padded, options)) {
            assertThat(reader.recordLength()).isEqualTo(122);
            assertThat(drain(reader)).containsExactly(RecordKind.HEADER);
        }
    }

    private ZenginReader open(byte[] bytes) {
        return open(bytes, Fixtures.options());
    }

    private ZenginReader open(byte[] bytes, ReaderOptions options) {
        return ZenginReaders.open(new ByteArrayInputStream(bytes), options);
    }

    private static List<RecordKind> drain(ZenginReader reader) {
        List<RecordKind> kinds = new ArrayList<>();
        while (reader.hasNext()) {
            kinds.add(reader.next().kind());
        }
        return kinds;
    }

    private static byte[] withByteOrderMark(byte[] file) {
        return Fixtures.concat(List.of(RecordFramer.BYTE_ORDER_MARK, file));
    }

    /** Hands out a few bytes at a time, so buffer refills happen mid-record. */
    private static final class DribblingStream extends InputStream {

        private final byte[] source;
        private final int chunk;
        private int position;

        DribblingStream(byte[] source, int chunk) {
            this.source = source;
            this.chunk = chunk;
        }

        @Override
        public int read() {
            return position < source.length ? source[position++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (position >= source.length) {
                return -1;
            }
            int count = Math.min(Math.min(chunk, length), source.length - position);
            System.arraycopy(source, position, target, offset, count);
            position += count;
            return count;
        }
    }
}
