package io.zengin4j.core.charset;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.generated.SougouFurikomiData;
import io.zengin4j.core.testing.Fixtures;
import org.junit.jupiter.api.Test;

/// Issue 3.6: every fixture read under every encoding, with the outcome asserted
/// for each (R-T12).
///
/// The interesting result is the negative one. For conformant content the
/// three encodings are not three different answers — two of them give byte-for-byte
/// the same answer, and the third gives a predictably broken one. Asserting that
/// is worth more than asserting each in isolation, because it is the property a
/// caller relies on when they do not know which encoding their file is in.
class EncodingMatrixTest {

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    private ReaderOptions options(ZenginCharset charset) {
        return ReaderOptions.builder()
                .registry(Fixtures.registry())
                .allowUnverifiedFormats(true)
                .charset(charset)
                .warningListener(warning -> {
                })
                .build();
    }

    // ------------------------------------------------------- the matrix itself

    /// The load-bearing claim in `docs/encoding.md`: a conformant file
    /// decodes identically under Shift_JIS and CP932, because every permitted
    /// character is single-byte and the two encodings differ only in the
    /// double-byte range.
    @Test
    void shiftJisAndMs932AgreeOnEveryConformantFile() {
        byte[] file = Fixtures.file(descriptor);

        ZenginFile viaShiftJis = ZenginReaders.readFile(
                new ByteArrayInputStream(file), options(ZenginCharset.SHIFT_JIS));
        ZenginFile viaMs932 = ZenginReaders.readFile(
                new ByteArrayInputStream(file), options(ZenginCharset.MS932));

        SougouFurikomiData fromShiftJis = (SougouFurikomiData) viaShiftJis.allData().get(0);
        SougouFurikomiData fromMs932 = (SougouFurikomiData) viaMs932.allData().get(0);

        assertThat(fromShiftJis.beneficiaryName()).isEqualTo(fromMs932.beneficiaryName())
                .isEqualTo(Fixtures.BENEFICIARY);
        assertThat(fromShiftJis.amount()).isEqualTo(fromMs932.amount());
        assertThat(viaShiftJis.batches().get(0).header().originatorName())
                .isEqualTo(viaMs932.batches().get(0).header().originatorName());
    }

    /// And both round-trip byte for byte, because the writer never re-encodes (R-D5).
    @Test
    void everyEncodingRoundTripsTheSameBytes() {
        byte[] file = Fixtures.file(descriptor);

        for (ZenginCharset charset : ZenginCharset.values()) {
            ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(file), options(charset));

            assertThat(ZenginWriters.toByteArray(parsed, WriterOptions.defaults()))
                    .as("round trip under %s", charset)
                    .isEqualTo(file);
        }
    }

    /// UTF-8 is the expected failure. The bytes are single-byte katakana, so
    /// decoding them as UTF-8 cannot produce the name — and the library does not
    /// pretend otherwise.
    @Test
    void utf8MisreadsASingleByteFileRatherThanFailing() {
        byte[] file = Fixtures.file(descriptor);

        ZenginFile parsed = ZenginReaders.readFile(
                new ByteArrayInputStream(file), options(ZenginCharset.UTF_8));

        // Structure survives: framing and numerics are ASCII either way.
        assertThat(parsed.totalRecords()).isEqualTo(4);
        assertThat(parsed.allData().get(0).amount()).isEqualTo(Fixtures.AMOUNT);
        // Text does not.
        assertThat(((SougouFurikomiData) parsed.allData().get(0)).beneficiaryName())
                .isNotEqualTo(Fixtures.BENEFICIARY);
    }

    /// A UTF-8 name does not fit the field it fits under MS932 — the one-byte assumption.
    @Test
    void aNameIsThreeTimesLongerInUtf8() {
        assertThat(ZenginCharset.MS932.encode(Fixtures.BANK_NAME)).hasSize(8);
        assertThat(ZenginCharset.SHIFT_JIS.encode(Fixtures.BANK_NAME)).hasSize(8);
        assertThat(ZenginCharset.UTF_8.encode(Fixtures.BANK_NAME)).hasSize(24);

        // A 15-byte 被仕向銀行名 holds it under the Japanese encodings and cannot
        // hold it under UTF-8.
        assertThat(ZenginCharset.MS932.encode(Fixtures.BANK_NAME).length).isLessThanOrEqualTo(15);
        assertThat(ZenginCharset.UTF_8.encode(Fixtures.BANK_NAME).length).isGreaterThan(15);
    }

    // --------------------------------------------- the divergence, pinned exactly

    /// Single-byte katakana is identical under both Japanese encodings. This is
    /// why the encoding choice does not matter for conformant content, so it is
    /// asserted rather than assumed.
    @Test
    void halfWidthKatakanaIsIdenticalUnderBothJapaneseEncodings() {
        Charset shiftJis = ZenginCharset.SHIFT_JIS.charset();
        Charset ms932 = ZenginCharset.MS932.charset();

        for (int code = 0xA1; code <= 0xDF; code++) {
            byte[] one = {(byte) code};
            assertThat(new String(one, shiftJis))
                    .as("byte 0x%02X", code)
                    .isEqualTo(new String(one, ms932));
        }
    }

    /// The double-byte divergence, byte pair by byte pair — the table in
    /// `docs/encoding.md`, asserted so it cannot drift.
    ///
    /// None of these byte pairs may appear in a conformant field. They are
    /// pinned because the difference is real and because a reader of that
    /// document deserves a table that is checked rather than remembered.
    @Test
    void theDoubleByteDivergenceIsExactlyWhatTheDocumentationSays() {
        assertDivergence(0x81, 0x60, '〜', '～');   // wave dash / fullwidth tilde
        assertDivergence(0x81, 0x61, '‖', '∥');   // double vertical line / parallel to
        assertDivergence(0x81, 0x7C, '−', '－');   // minus sign / fullwidth hyphen-minus

        // NEC and IBM extensions: CP932 has them, Shift_JIS does not.
        assertOnlyInMs932(0x87, 0x40, '①');            // ①
        assertOnlyInMs932(0x87, 0x54, 'Ⅰ');            // Ⅰ
        assertOnlyInMs932(0x87, 0x82, '№');            // №
    }

    private static void assertDivergence(int first, int second, char shiftJis, char ms932) {
        byte[] bytes = {(byte) first, (byte) second};

        assertThat(new String(bytes, ZenginCharset.SHIFT_JIS.charset()))
                .as("0x%02X%02X under Shift_JIS", first, second)
                .isEqualTo(String.valueOf(shiftJis));
        assertThat(new String(bytes, ZenginCharset.MS932.charset()))
                .as("0x%02X%02X under CP932", first, second)
                .isEqualTo(String.valueOf(ms932));
        assertThat(shiftJis).isNotEqualTo(ms932);
    }

    private static void assertOnlyInMs932(int first, int second, char ms932) {
        byte[] bytes = {(byte) first, (byte) second};

        assertThat(new String(bytes, ZenginCharset.MS932.charset()))
                .as("0x%02X%02X under CP932", first, second)
                .isEqualTo(String.valueOf(ms932));
        assertThat(new String(bytes, ZenginCharset.SHIFT_JIS.charset()))
                .as("0x%02X%02X should not map under Shift_JIS", first, second)
                .isNotEqualTo(String.valueOf(ms932));
    }

    /// Encoding a character the target cannot represent substitutes `?`, silently, both ways.
    @Test
    void anUnmappableCharacterBecomesAQuestionMark() {
        assertThat(ZenginCharset.SHIFT_JIS.encode("～")).containsExactly('?');
        assertThat(ZenginCharset.MS932.encode("〜")).containsExactly('?');
    }

    /// Every charset the library offers resolves on this JVM.
    @Test
    void everyDeclaredCharsetIsAvailable() {
        for (ZenginCharset charset : ZenginCharset.values()) {
            assertThat(charset.charset()).as("%s", charset).isNotNull();
            assertThat(charset.charsetName()).isNotBlank();
        }
        assertThat(ZenginCharset.defaultCharset()).isEqualTo(ZenginCharset.MS932);
        assertThat(List.of(ZenginCharset.values()))
                .containsExactly(ZenginCharset.SHIFT_JIS, ZenginCharset.MS932, ZenginCharset.UTF_8);
    }
}
