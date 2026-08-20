package io.zengin4j.core.error;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.charset.ZenginCharset;
import org.junit.jupiter.api.Test;

/// The diagnostic contract: bilingual messages (R-E4), account masking (R-E6)
/// and enough context to act on (R-E3).
class DiagnosticsTest {

    /// R-E6: showing all four digits of a four-digit value would not be masking.
    @Test
    void masksIdentifiersToTheirLastFourCharacters() {
        assertThat(Diagnostics.maskIdentifier("9876543")).isEqualTo("***6543");
        assertThat(Diagnostics.maskIdentifier("1234")).isEqualTo("****");
        assertThat(Diagnostics.maskIdentifier("12")).isEqualTo("**");
        assertThat(Diagnostics.maskIdentifier("")).isEmpty();
        assertThat(Diagnostics.maskIdentifier("   ")).isEqualTo("   ");
        assertThat(Diagnostics.maskIdentifier(null)).isNull();
    }

    @Test
    void everyExceptionCarriesBothLanguages() {
        var exceptions = List.of(
                new ZenginIOException("reading", new java.io.IOException("disk gone")),
                new MalformedFileException(120, 2, "en", "ja"),
                new MalformedFieldException("amount", 80, 'X'),
                new UnsupportedFormatException("99", "21 (sougou-furikomi)"),
                new UnverifiedFormatException("sougou-furikomi"),
                new UnsupportedEncodingVariantException(CodeKubun.EBCDIC, "1", 3),
                FormatDescriptorException.forFormat("sougou-furikomi", "problem"),
                FormatDescriptorException.forResource("file.yaml", "problem"),
                new StaleRecordViewException(3),
                new AmountOverflowException(9),
                new AmbiguousFormatException("91", List.of("a", "b")),
                new CharsetUnavailableException("windows-31j", null));

        for (ZenginException exception : exceptions) {
            assertThat(exception.messageEn()).as("%s English", exception.getClass()).isNotBlank();
            assertThat(exception.messageJa()).as("%s Japanese", exception.getClass()).isNotBlank();
            assertThat(exception.getMessage()).isEqualTo(exception.messageEn());
            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void localisesToTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            ZenginException exception = new UnverifiedFormatException("sougou-furikomi");

            Locale.setDefault(Locale.JAPAN);
            assertThat(exception.getLocalizedMessage()).isEqualTo(exception.messageJa());

            Locale.setDefault(Locale.UK);
            assertThat(exception.getLocalizedMessage()).isEqualTo(exception.messageEn());
        } finally {
            Locale.setDefault(original);
        }
    }

    /// R-E3: what was expected, what was found, and where.
    @Test
    void locatesEveryProblem() {
        var file = new MalformedFileException(240, 3, "en", "ja");
        assertThat(file.byteOffset()).isEqualTo(240);
        assertThat(file.recordNumber()).isEqualTo(3);
        assertThat(file.getMessage()).contains("record 3 at byte 240");

        var field = new MalformedFieldException("amount", 80, 'X');
        assertThat(field.fieldId()).isEqualTo("amount");
        assertThat(field.byteOffset()).isEqualTo(80);
        assertThat(field.offendingByte()).isEqualTo('X');
        assertThat(field.getMessage()).contains("0x58");

        assertThat(new UnsupportedFormatException("99", "21").typeCode()).isEqualTo("99");
        assertThat(new UnverifiedFormatException("x").formatId()).isEqualTo("x");
        assertThat(new UnsupportedEncodingVariantException(CodeKubun.EBCDIC, "1", 3).found())
                .isEqualTo(CodeKubun.EBCDIC);
        assertThat(FormatDescriptorException.forFormat("x", "y").formatId()).isEqualTo("x");
        assertThat(FormatDescriptorException.forFormat("x", "y").problem()).isEqualTo("y");
        assertThat(new StaleRecordViewException(3).recordNumber()).isEqualTo(3);
        assertThat(new AmountOverflowException(9).recordNumber()).isEqualTo(9);
        assertThat(new AmbiguousFormatException("91", List.of("a", "b")).typeCode()).isEqualTo("91");
        assertThat(new AmbiguousFormatException("91", List.of("a", "b")).candidates())
                .containsExactly("a", "b");
        assertThat(new CharsetUnavailableException("windows-31j", null).charsetName())
                .isEqualTo("windows-31j");
    }

    @Test
    void resolvesTheJapaneseCharacterSets() {
        assertThat(ZenginCharset.defaultCharset()).isEqualTo(ZenginCharset.MS932);
        assertThat(ZenginCharset.MS932.charsetName()).isEqualTo("windows-31j");
        assertThat(ZenginCharset.MS932.charset().name()).isEqualTo("windows-31j");
        assertThat(ZenginCharset.SHIFT_JIS.charset()).isNotNull();
        assertThat(ZenginCharset.UTF_8.charset()).isNotNull();

        byte[] encoded = ZenginCharset.MS932.encode("ﾀﾛｳ");
        assertThat(encoded).hasSize(3);
        assertThat(ZenginCharset.MS932.decode(encoded, 0, 3)).isEqualTo("ﾀﾛｳ");
    }

    @Test
    void mapsTheEncodingIndicator() {
        assertThat(CodeKubun.of("0")).isEqualTo(CodeKubun.JIS);
        assertThat(CodeKubun.of("1")).isEqualTo(CodeKubun.EBCDIC);
        assertThat(CodeKubun.of(" 1 ")).isEqualTo(CodeKubun.EBCDIC);
        assertThat(CodeKubun.of("7")).isEqualTo(CodeKubun.UNKNOWN);
        assertThat(CodeKubun.of(null)).isEqualTo(CodeKubun.UNKNOWN);
        assertThat(CodeKubun.JIS.isSupported()).isTrue();
        assertThat(CodeKubun.EBCDIC.isSupported()).isFalse();
        assertThat(CodeKubun.UNKNOWN.isSupported()).isFalse();
        assertThat(CodeKubun.JIS.code()).isEqualTo("0");
        assertThat(CodeKubun.UNKNOWN.code()).isEmpty();
    }
}
