package io.zengin4j.core.charset;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/// Issue 3.2: the permitted character set is per field class (R-C16, R-C17).
///
/// The sets are asserted against what the sources say, byte by byte, rather
/// than against the implementation's own idea of them. A test that reads the
/// table it is testing proves nothing.
class CharacterSetTest {

    private static byte[] ms932(String text) {
        return ZenginCharset.MS932.encode(text);
    }

    // ------------------------------------------------------------ the base set

    @Test
    void permitsFullSizeKanaVoicingMarksDigitsAndUppercaseLatin() {
        for (CharacterClass permitted : List.of(CharacterClass.BANK_NAME, CharacterClass.PARTY_NAME)) {
            assertThat(CharacterSet.validate(ms932("ﾔﾏﾀﾞ ﾀﾛｳ"), permitted)).isEmpty();
            assertThat(CharacterSet.validate(ms932("ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ"), permitted)).isEmpty();
            assertThat(CharacterSet.validate(ms932("ABC123"), permitted)).isEmpty();
            // ｱ through ﾝ, the whole run.
            assertThat(CharacterSet.validate(kanaRun(), permitted)).isEmpty();
        }
    }

    /// The finding that matters most, because the file looks correct.
    ///
    /// `ｰ` (0xB0) and `-` (0x2D) are near-identical glyphs. The
    /// standard permits only the hyphen; three sources say so, one of them
    /// warning about the confusion explicitly. A name carrying `ｰ` reads
    /// perfectly to a human and is rejected by the bank.
    @Test
    void rejectsTheLongVowelMarkAndNamesTheFix() {
        byte[] wrong = ms932("ﾃｽﾄｰ");
        byte[] right = ms932("ﾃｽﾄ-");

        assertThat(wrong[3] & 0xFF).isEqualTo(0xB0);
        assertThat(right[3] & 0xFF).isEqualTo(0x2D);

        assertThat(CharacterSet.validate(right, CharacterClass.BANK_NAME)).isEmpty();

        List<CharacterViolation> violations = CharacterSet.validate(wrong, CharacterClass.BANK_NAME);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).offset()).isEqualTo(3);
        assertThat(violations.get(0).isProlongedSoundMark()).isTrue();
        assertThat(violations.get(0).describeEn()).contains("long vowel mark").contains("0x2D");
        assertThat(violations.get(0).describeJa()).contains("長音");
    }

    @Test
    void rejectsSmallKanaEverywhere() {
        for (CharacterClass permitted : CharacterClass.values()) {
            if (permitted == CharacterClass.UNRESTRICTED) {
                continue;
            }
            // ｧ ｨ ｩ ｪ ｫ ｬ ｭ ｮ ｯ — 0xA7 through 0xAF.
            for (int code = 0xA7; code <= 0xAF; code++) {
                assertThat(permitted.permits(code))
                        .as("%s permits 0x%02X", permitted, code)
                        .isFalse();
            }
        }
    }

    @Test
    void rejectsLowercaseLatinAndFullWidthCharacters() {
        assertThat(CharacterSet.validate(ms932("abc"), CharacterClass.PARTY_NAME)).hasSize(3);
        assertThat(CharacterSet.validate(ms932("ヤマダ"), CharacterClass.PARTY_NAME)).isNotEmpty();
        assertThat(CharacterSet.validate(ms932("１２３"), CharacterClass.PARTY_NAME)).isNotEmpty();

        assertThat(CharacterSet.validate(ms932("abc"), CharacterClass.PARTY_NAME).get(0).describeEn())
                .contains("lowercase Latin");
    }

    // ------------------------------------------------- the per-class narrowing

    /// 注1: a branch name permits exactly one symbol.
    @Test
    void aBankNamePermitsOnlyTheHyphen() {
        assertThat(CharacterSet.validate(ms932("ﾃｽﾄ-ｼﾃﾝ"), CharacterClass.BANK_NAME)).isEmpty();

        for (String symbol : List.of("(", ")", ".", "/", "\\")) {
            assertThat(CharacterSet.validate(ms932(symbol), CharacterClass.BANK_NAME))
                    .as("branch name should reject %s", symbol)
                    .hasSize(1);
        }
    }

    /// 注2: a party name permits four.
    @Test
    void aPartyNamePermitsFourSymbols() {
        assertThat(CharacterSet.validate(ms932("ﾔﾏﾀﾞ(ｶ)-."), CharacterClass.PARTY_NAME)).isEmpty();
        assertThat(CharacterClass.PARTY_NAME.symbols()).isEqualTo("()-.");

        for (String symbol : List.of("/", "\\", ",")) {
            assertThat(CharacterSet.validate(ms932(symbol), CharacterClass.PARTY_NAME))
                    .as("party name should reject %s", symbol)
                    .hasSize(1);
        }
    }

    /// 給与振込 forbids Latin letters entirely — a rule 総合振込 would never surface.
    @Test
    void aPayrollNamePermitsNoLatinLetters() {
        assertThat(CharacterSet.validate(ms932("ﾔﾏﾀﾞ ﾀﾛｳ"), CharacterClass.PAYROLL_NAME)).isEmpty();
        assertThat(CharacterSet.validate(ms932("123"), CharacterClass.PAYROLL_NAME)).isEmpty();

        assertThat(CharacterSet.validate(ms932("ABC"), CharacterClass.PAYROLL_NAME)).hasSize(3);
        // And the same bytes are fine in a 総合振込 party name, which is the point.
        assertThat(CharacterSet.validate(ms932("ABC"), CharacterClass.PARTY_NAME)).isEmpty();
    }

    /// EDI is the only class admitting ｦ, and the only one admitting ｢ ｣ / and the yen sign.
    @Test
    void ediInformationIsTheWidestClass() {
        assertThat(CharacterSet.validate(ms932("ｦ"), CharacterClass.EDI_INFORMATION)).isEmpty();
        assertThat(CharacterSet.validate(ms932("ｦ"), CharacterClass.PARTY_NAME)).hasSize(1);
        assertThat(CharacterSet.validate(ms932("ｦ"), CharacterClass.PARTY_NAME).get(0).describeEn())
                .contains("only in EDI");

        assertThat(CharacterSet.validate(ms932("ｱ｢ｲ｣/\\().-1A"), CharacterClass.EDI_INFORMATION))
                .isEmpty();
        // Never a comma, in any class.
        assertThat(CharacterSet.validate(ms932(","), CharacterClass.EDI_INFORMATION)).hasSize(1);
    }

    @Test
    void numericPermitsDigitsOnly() {
        assertThat(CharacterSet.validate(ms932("0123456789"), CharacterClass.NUMERIC)).isEmpty();
        assertThat(CharacterSet.validate(ms932(" "), CharacterClass.NUMERIC)).hasSize(1);
        assertThat(CharacterSet.validate(ms932("A"), CharacterClass.NUMERIC)).hasSize(1);
        assertThat(CharacterSet.validate(ms932("ｱ"), CharacterClass.NUMERIC)).hasSize(1);
    }

    /// Filler is not this library's business to police (R-D5).
    @Test
    void unrestrictedPermitsEveryByte() {
        byte[] everything = new byte[256];
        for (int i = 0; i < 256; i++) {
            everything[i] = (byte) i;
        }
        assertThat(CharacterSet.validate(everything, CharacterClass.UNRESTRICTED)).isEmpty();
    }

    // ---------------------------------------------------------- the API itself

    /// R-C17: offsets, not a verdict.
    @Test
    void reportsEveryViolationInOrderWithItsOffset() {
        byte[] name = ms932("ｱaｲbｳ");

        List<CharacterViolation> violations = CharacterSet.validate(name, CharacterClass.PARTY_NAME);

        assertThat(violations).extracting(CharacterViolation::offset).containsExactly(1, 3);
        assertThat(violations).extracting(CharacterViolation::unsignedValue)
                .containsExactly((int) 'a', (int) 'b');
        assertThat(violations).allSatisfy(violation ->
                assertThat(violation.permitted()).isEqualTo(CharacterClass.PARTY_NAME));
    }

    /// Offsets are relative to the record, so they compose with the record's own position.
    @Test
    void validatesAFieldWithinARecordUsingRecordRelativeOffsets() {
        byte[] record = new byte[120];
        java.util.Arrays.fill(record, (byte) ' ');
        System.arraycopy(ms932("ｱaｲ"), 0, record, 50, 3);

        List<CharacterViolation> violations =
                CharacterSet.validateField(record, 50, 30, CharacterClass.PARTY_NAME);

        assertThat(violations).extracting(CharacterViolation::offset).containsExactly(51);
    }

    @Test
    void isCleanAgreesWithValidate() {
        byte[] good = ms932("ﾔﾏﾀﾞ");
        byte[] bad = ms932("ﾔﾏﾀﾞｰ");

        assertThat(CharacterSet.isClean(good, 0, good.length, CharacterClass.PARTY_NAME)).isTrue();
        assertThat(CharacterSet.isClean(bad, 0, bad.length, CharacterClass.PARTY_NAME)).isFalse();
        assertThat(CharacterSet.validate(good, CharacterClass.PARTY_NAME)).isEmpty();
        assertThat(CharacterSet.validate(bad, CharacterClass.PARTY_NAME)).isNotEmpty();
    }

    /// A UTF-8 file is not conformant, and validation says so rather than
    /// pretending: every katakana character becomes three bytes, none of which
    /// is a permitted single-byte kana.
    @Test
    void aUtf8EncodedNameViolatesThroughout() {
        byte[] utf8 = "ﾔﾏﾀﾞ".getBytes(StandardCharsets.UTF_8);

        assertThat(utf8).hasSize(12);
        assertThat(CharacterSet.validate(utf8, CharacterClass.PARTY_NAME)).isNotEmpty();
    }

    @Test
    void rejectsBadArguments() {
        byte[] bytes = new byte[10];

        assertThatNullPointerException()
                .isThrownBy(() -> CharacterSet.validate(null, CharacterClass.PARTY_NAME));
        assertThatNullPointerException()
                .isThrownBy(() -> CharacterSet.validate(bytes, 0, 1, null));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> CharacterSet.validate(bytes, 5, 10, CharacterClass.PARTY_NAME));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> CharacterSet.isClean(bytes, -1, 2, CharacterClass.PARTY_NAME));
    }

    @Test
    void violationRejectsANegativeOffset() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new CharacterViolation(-1, (byte) 'a', CharacterClass.PARTY_NAME))
                .withMessageContaining("cannot be negative");
        assertThatNullPointerException()
                .isThrownBy(() -> new CharacterViolation(0, (byte) 'a', null));
    }

    @Test
    void classesCarryTheirNamesForReporting() {
        assertThat(CharacterClass.BANK_NAME.nameJa()).isEqualTo("店舗名");
        assertThat(CharacterClass.BANK_NAME.nameEn()).isEqualTo("bank and branch names");
        assertThat(CharacterClass.BANK_NAME.symbols()).isEqualTo("-");
        assertThat(CharacterClass.PAYROLL_NAME.symbols()).isEmpty();
        assertThat(CharacterClass.PARTY_NAME.permits(-1)).isFalse();
        assertThat(CharacterClass.PARTY_NAME.permits(0x100)).isFalse();
    }

    /// ｱ (0xB1) through ﾝ (0xDD), which is the whole permitted kana run.
    private static byte[] kanaRun() {
        byte[] kana = new byte[0xDD - 0xB1 + 1];
        for (int i = 0; i < kana.length; i++) {
            kana[i] = (byte) (0xB1 + i);
        }
        return kana;
    }
}
