package io.zengin4j.core.kana;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.VoicingMarks;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.kana.generated.KanaTables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/// The transliteration tables, checked exhaustively (R-T10).
///
/// **Anchored to the byte values, not to the generator.** The
/// table is derived from Unicode at build time, so a test that re-derived it
/// would agree with itself and prove nothing. §16.1 of the build specification
/// gives the JIS X 0201 layout instead — `ｱ` at `0xB1` through
/// `ﾝ` at `0xDD`, in gojūon order — and that is an independent fact
/// this test holds the table to.
///
/// The gojūon string below is transcribed by hand, which is the one place
/// transcription belongs: a slip in it fails against the derived table
/// immediately, whereas a slip in the table itself would ship a plausible-looking
/// wrong name.
class KanaTableTest {

    /// The 45 katakana of the gojūon, in the order JIS X 0201 encodes them.
    ///
    /// Deliberately written out rather than generated. This is the assertion.
    private static final String GOJUON =
            "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン";

    /// `ｱ` sits here, and the rest follow in order (§16.1).
    private static final int FIRST_KANA_BYTE = 0xB1;

    private static final int DAKUTEN_BYTE = 0xDE;
    private static final int HANDAKUTEN_BYTE = 0xDF;

    private static byte[] narrowedBytes(String fullWidth) {
        String narrowed = KanaTables.narrow(fullWidth);
        assertThat(narrowed).as("no narrowing for '%s'", fullWidth).isNotNull();
        return ZenginCharset.MS932.encode(narrowed);
    }

    // ------------------------------------------------------------- the gojūon

    @Test
    void theGojuonNarrowsToConsecutiveBytesStartingAtTheDocumentedOne() {
        assertThat(GOJUON).hasSize(45);

        for (int i = 0; i < GOJUON.length(); i++) {
            String kana = GOJUON.substring(i, i + 1);
            byte[] bytes = narrowedBytes(kana);

            assertThat(bytes).as("%s should narrow to one byte", kana).hasSize(1);
            assertThat(bytes[0] & 0xFF)
                    .as("%s is number %d of the gojūon, so JIS X 0201 puts it at 0x%02X",
                            kana, i + 1, FIRST_KANA_BYTE + i)
                    .isEqualTo(FIRST_KANA_BYTE + i);
        }
    }

    /// ｦ sits below the run, at its own byte (§16.1).
    @Test
    void woSitsAtItsOwnByte() {
        assertThat(narrowedBytes("ヲ")[0] & 0xFF).isEqualTo(0xA6);
    }

    // ------------------------------------------------------------- voicing

    /// Every voiced kana decomposes to its base plus `0xDE` (R-K1).
    ///
    /// The expected base is stated per entry rather than computed, so this
    /// checks the decomposition rather than restating it.
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> voicedPairs() {
        return voiced().entrySet().stream()
                .map(e -> org.junit.jupiter.params.provider.Arguments.of(e.getKey(), e.getValue()));
    }

    static Map<String, String> voiced() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("ガ", "カ");
        pairs.put("ギ", "キ");
        pairs.put("グ", "ク");
        pairs.put("ゲ", "ケ");
        pairs.put("ゴ", "コ");
        pairs.put("ザ", "サ");
        pairs.put("ジ", "シ");
        pairs.put("ズ", "ス");
        pairs.put("ゼ", "セ");
        pairs.put("ゾ", "ソ");
        pairs.put("ダ", "タ");
        pairs.put("ヂ", "チ");
        pairs.put("ヅ", "ツ");
        pairs.put("デ", "テ");
        pairs.put("ド", "ト");
        pairs.put("バ", "ハ");
        pairs.put("ビ", "ヒ");
        pairs.put("ブ", "フ");
        pairs.put("ベ", "ヘ");
        pairs.put("ボ", "ホ");
        pairs.put("ヴ", "ウ");
        return pairs;
    }

    @ParameterizedTest
    @MethodSource("voicedPairs")
    void everyVoicedKanaBecomesItsBasePlusADakuten(String voiced, String base) {
        byte[] bytes = narrowedBytes(voiced);
        byte[] baseBytes = narrowedBytes(base);

        assertThat(bytes).as("%s is a base and a mark, so two bytes", voiced).hasSize(2);
        assertThat(bytes[0]).as("%s should start with %s", voiced, base).isEqualTo(baseBytes[0]);
        assertThat(bytes[1] & 0xFF).isEqualTo(DAKUTEN_BYTE);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> semiVoicedPairs() {
        return semiVoiced().entrySet().stream()
                .map(e -> org.junit.jupiter.params.provider.Arguments.of(e.getKey(), e.getValue()));
    }

    static Map<String, String> semiVoiced() {
        Map<String, String> pairs = new LinkedHashMap<>();
        pairs.put("パ", "ハ");
        pairs.put("ピ", "ヒ");
        pairs.put("プ", "フ");
        pairs.put("ペ", "ヘ");
        pairs.put("ポ", "ホ");
        return pairs;
    }

    @ParameterizedTest
    @MethodSource("semiVoicedPairs")
    void everySemiVoicedKanaBecomesItsBasePlusAHandakuten(String semiVoiced, String base) {
        byte[] bytes = narrowedBytes(semiVoiced);

        assertThat(bytes).hasSize(2);
        assertThat(bytes[0]).isEqualTo(narrowedBytes(base)[0]);
        assertThat(bytes[1] & 0xFF).isEqualTo(HANDAKUTEN_BYTE);
    }

    /// The table's illegal decompositions are known, named, and refused.
    ///
    /// Four entries decompose to a stranded mark. ヷ and ヺ are the archaic
    /// VA and VO: Unicode splits them into ワ/ヲ plus a voicing mark, and neither
    /// kana has a voiced form the standard recognises. ゙ and ゚ are the bare
    /// combining marks, which narrow to bare ﾞ and ﾟ and strand by definition.
    ///
    /// So the derived table faithfully contains mappings that must never be
    /// written, and the engine refuses them at the voicing-mark pass rather than
    /// the table pretending they do not exist. This test names them; a fifth
    /// would appear here rather than in somebody's rejected file.
    ///
    /// ヸ and ヹ are absent for a different reason — ヰ and ヱ have no
    /// half-width form at all, so those two never reach the narrowing table and
    /// are refused by the character-class pass instead.
    @Test
    void theOnlyDecompositionsThatStrandAMarkAreTheFourArchaicOnes() {
        List<String> stranding = new ArrayList<>();

        for (Map.Entry<String, String> entry : KanaTables.narrowings().entrySet()) {
            byte[] bytes = ZenginCharset.MS932.encode(entry.getValue());
            for (int i = 0; i < bytes.length; i++) {
                int mark = bytes[i] & 0xFF;
                if (!VoicingMarks.isMark(mark)) {
                    continue;
                }
                int base = i == 0 ? -1 : bytes[i - 1] & 0xFF;
                if (!VoicingMarks.isLegal(base, mark)) {
                    stranding.add(entry.getKey());
                }
            }
        }

        assertThat(stranding)
                .as("a decomposition that strands a voicing mark must be known about")
                .containsExactlyInAnyOrder("\u30F7", "\u30FA", "\u3099", "\u309A");
    }

    /// None of the archaic V-kana or bare marks is ever written.
    ///
    /// Refused by two different routes — ヷ and ヺ at the voicing-mark pass,
    /// ヸ and ヹ at the character-class pass because they have no half-width form
    /// — and the route does not matter. What matters is that none of them
    /// reaches a file.
    @ParameterizedTest
    @ValueSource(strings = {"\u30F7", "\u30F8", "\u30F9", "\u30FA", "\u3099", "\u309A"})
    void aCharacterThatCannotBeWrittenIsRefused(String archaic) {
        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .as("'%s' must not be written into a field", archaic)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth(archaic));
    }

    /// ヷ and ヺ specifically fail as stranded marks, and say so.
    @ParameterizedTest
    @ValueSource(strings = {"\u30F7", "\u30FA"})
    void theArchaicVKanaAreRefusedAsStrandedMarks(String archaic) {
        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth(archaic))
                .withMessageContaining("voicing mark");
    }

    /// Every legal decomposition puts its mark after a kana that can take one.
    ///
    /// The complement of the test above: once the four known offenders are
    /// set aside, nothing else in the table strands a mark.
    @Test
    void everyOtherDecompositionPutsItsMarkOnALegalBase() {
        List<String> known = List.of("\u30F7", "\u30FA", "\u3099", "\u309A");

        KanaTables.narrowings().forEach((full, half) -> {
            if (known.contains(full)) {
                return;
            }
            byte[] bytes = ZenginCharset.MS932.encode(half);
            for (int i = 0; i < bytes.length; i++) {
                int mark = bytes[i] & 0xFF;
                if (!VoicingMarks.isMark(mark)) {
                    continue;
                }
                int base = i == 0 ? -1 : bytes[i - 1] & 0xFF;
                assertThat(VoicingMarks.isLegal(base, mark))
                        .as("%s narrows to %s, stranding a voicing mark (R-K7)", full, half)
                        .isTrue();
            }
        });
    }

    // ------------------------------------------------------- shape invariants

    /// Every narrowed form is one byte per character in JIS X 0201.
    ///
    /// A fixed-length field counts bytes. A mapping producing a two-byte
    /// character would silently consume twice the room the caller budgeted.
    @Test
    void everyNarrowedFormIsSingleByte() {
        assertThat(KanaTables.narrowings()).isNotEmpty();

        KanaTables.narrowings().forEach((full, half) ->
                assertThat(ZenginCharset.MS932.encode(half))
                        .as("%s -> %s must be one byte per character", full, half)
                        .hasSize(half.length()));
    }

    /// Nothing narrows to a character no field class admits.
    @Test
    void narrowedFormsAreCharactersTheFormatCanCarry() {
        KanaTables.narrowings().forEach((full, half) -> {
            byte[] bytes = ZenginCharset.MS932.encode(half);
            assertThat(CharacterSet.isClean(bytes, 0, bytes.length, CharacterClass.UNRESTRICTED))
                    .as("%s narrows to %s, which is outside the single-byte range entirely",
                            full, half)
                    .isTrue();
        });
    }

    /// The substituted characters are exactly the ones no class admits.
    ///
    /// This is the check that ties the table to the reason it exists. If a
    /// future character class started permitting small kana, this test would
    /// fail and the substitution would be revisited rather than left in place
    /// out of habit.
    ///
    /// R-K9: the substitutions are data, and this holds the data to its
    /// reason.
    @Test
    void everySubstitutedCharacterIsOneNoFieldClassPermits() {
        KanaTables.substitutions().forEach((character, substitution) -> {
            String narrowed = KanaTables.narrow(character);
            String halfWidth = narrowed != null ? narrowed : character;
            byte[] bytes = ZenginCharset.MS932.encode(halfWidth);

            boolean permittedSomewhere = false;
            for (CharacterClass characterClass : CharacterClass.values()) {
                if (characterClass == CharacterClass.UNRESTRICTED) {
                    continue;
                }
                if (CharacterSet.isClean(bytes, 0, bytes.length, characterClass)) {
                    permittedSomewhere = true;
                    break;
                }
            }
            assertThat(permittedSomewhere)
                    .as("'%s' is substituted, but some field class permits it — so the"
                            + " substitution may no longer be warranted", character)
                    .isFalse();
        });
    }

    /// And every replacement is one that at least one class does admit.
    @Test
    void everyReplacementIsPermittedSomewhere() {
        KanaTables.substitutions().forEach((character, substitution) -> {
            String narrowed = KanaTables.narrow(substitution.replacement());
            String halfWidth = narrowed != null ? narrowed : substitution.replacement();
            byte[] bytes = ZenginCharset.MS932.encode(halfWidth);

            assertThat(CharacterSet.isClean(bytes, 0, bytes.length, CharacterClass.PARTY_NAME))
                    .as("'%s' is replaced by '%s', which party names do not permit either",
                            character, substitution.replacement())
                    .isTrue();
        });
    }

    // ------------------------------------------------------------- the ASCII

    @Test
    void fullWidthLatinAndDigitsNarrowToAscii() {
        assertThat(KanaTables.narrow("Ａ")).isEqualTo("A");
        assertThat(KanaTables.narrow("Ｚ")).isEqualTo("Z");
        assertThat(KanaTables.narrow("０")).isEqualTo("0");
        assertThat(KanaTables.narrow("９")).isEqualTo("9");
        assertThat(KanaTables.narrow("　")).isEqualTo(" ");
        assertThat(KanaTables.narrow("（")).isEqualTo("(");
        assertThat(KanaTables.narrow("．")).isEqualTo(".");
    }

    @Test
    void punctuationNarrowsToItsHalfWidthForm() {
        assertThat(KanaTables.narrow("。")).isEqualTo("｡");
        assertThat(KanaTables.narrow("、")).isEqualTo("､");
        assertThat(KanaTables.narrow("「")).isEqualTo("｢");
        assertThat(KanaTables.narrow("」")).isEqualTo("｣");
        assertThat(KanaTables.narrow("・")).isEqualTo("･");
    }

    // ------------------------------------------------------------- widening

    /// Widening inverts narrowing for every kana.
    ///
    /// Only claimed for kana. Several full-width characters narrow to the
    /// same half-width sequence, so widening is one reading of the bytes rather
    /// than the only one (R-K8) — which is why it is informational.
    @Test
    void everyKanaWidensBackToWhatItNarrowedFrom() {
        for (int i = 0; i < GOJUON.length(); i++) {
            String kana = GOJUON.substring(i, i + 1);
            assertThat(KanaTables.widen(KanaTables.narrow(kana)))
                    .as("%s should widen back to itself", kana)
                    .isEqualTo(kana);
        }
        voiced().forEach((full, base) ->
                assertThat(KanaTables.widen(KanaTables.narrow(full))).isEqualTo(full));
        semiVoiced().forEach((full, base) ->
                assertThat(KanaTables.widen(KanaTables.narrow(full))).isEqualTo(full));
    }

    // ------------------------------------------------------------- hiragana

    @Test
    void hiraganaMapsToKatakanaAcrossTheWholeRange() {
        for (char hiragana = 0x3041; hiragana <= 0x3096; hiragana++) {
            assertThat(KanaTables.isHiragana(hiragana))
                    .as("0x%04X should be hiragana", (int) hiragana)
                    .isTrue();

            int katakana = KanaTables.katakanaFor(hiragana);
            assertThat(Character.UnicodeBlock.of(katakana))
                    .as("%c should map to katakana", hiragana)
                    .isEqualTo(Character.UnicodeBlock.KATAKANA);
        }
        assertThat(KanaTables.katakanaFor('あ')).isEqualTo((int) 'ア');
        assertThat(KanaTables.katakanaFor('が')).isEqualTo((int) 'ガ');
        assertThat(KanaTables.katakanaFor('ぽ')).isEqualTo((int) 'ポ');
    }

    @Test
    void katakanaIsNotMistakenForHiragana() {
        assertThat(KanaTables.isHiragana('ア')).isFalse();
        assertThat(KanaTables.isHiragana('A')).isFalse();
        assertThat(KanaTables.isHiragana('ｱ')).isFalse();
    }
}
