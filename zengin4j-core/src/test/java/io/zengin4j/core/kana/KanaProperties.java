package io.zengin4j.core.kana;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.VoicingMarks;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.testing.Seeded;
import org.junit.jupiter.api.Test;

/// INV-4, over generated names rather than chosen ones.
///
/// `TruncationTest` walks a hand-built corpus at every length, which
/// covers the cases somebody thought of. This covers the ones nobody did: names
/// assembled at random from voiced, semi-voiced and plain kana, truncated to
/// random widths, thousands of times.
///
/// The seed is fixed. A property test that flakes on a schedule teaches the
/// team to re-run CI, which is worse than not having it.
class KanaProperties {

    private static final long SEED = 0x4E4A_2026L;

    /// Kana chosen so roughly half of any generated name carries a voicing mark.
    private static final List<String> SYLLABLES = List.of(
            "ガ", "ギ", "グ", "ゲ", "ゴ", "ザ", "ジ", "ズ", "ダ", "ヂ", "バ", "ビ", "ブ",
            "パ", "ピ", "プ", "ペ", "ポ", "ヴ",
            "ア", "イ", "ウ", "カ", "キ", "サ", "シ", "タ", "ナ", "ハ", "マ", "ヤ", "ラ", "ワ", "ン");

    private record Case(String name, byte[] halfWidth, int maxBytes) {
    }

    private static Case generate(Random random) {
        int syllables = 1 + random.nextInt(12);
        var name = new StringBuilder(syllables);
        for (int i = 0; i < syllables; i++) {
            name.append(SYLLABLES.get(random.nextInt(SYLLABLES.size())));
        }
        String text = name.toString();
        byte[] bytes = ZenginCharset.MS932.encode(
                KanaTransliterator.toHalfWidth(text).text());
        // Widths from one byte to a little past the end, so both the truncating
        // and the fits-already paths are exercised.
        return new Case(text, bytes, 1 + random.nextInt(bytes.length + 2));
    }

    /// INV-4 — `truncateSafe(toHalfWidth(s), n)` never ends with an
    /// orphaned voicing mark and never begins with one.
    @Test
    void inv4_truncationNeverStrandsAVoicingMark() {
        Seeded.property("INV-4: no stranded voicing mark", 2000, SEED,
                KanaProperties::generate,
                testCase -> {
                    byte[] kept;
                    try {
                        kept = KanaTransliterator.truncateSafe(testCase.halfWidth(),
                                testCase.maxBytes());
                    } catch (FieldTooSmallException tooSmall) {
                        return;
                    }

                    for (int i = 0; i < kept.length; i++) {
                        int mark = kept[i] & 0xFF;
                        if (!VoicingMarks.isMark(mark)) {
                            continue;
                        }
                        int base = i == 0 ? -1 : kept[i - 1] & 0xFF;
                        assertThat(VoicingMarks.isLegal(base, mark))
                                .as("'%s' cut to %d bytes stranded the mark at %d",
                                        testCase.name(), testCase.maxBytes(), i)
                                .isTrue();
                    }
                });
    }

    /// Truncation removes from the end and never rewrites what it keeps.
    @Test
    void truncationOnlyEverShortens() {
        Seeded.property("truncation is a prefix", 2000, SEED,
                KanaProperties::generate,
                testCase -> {
                    byte[] kept;
                    try {
                        kept = KanaTransliterator.truncateSafe(testCase.halfWidth(),
                                testCase.maxBytes());
                    } catch (FieldTooSmallException tooSmall) {
                        return;
                    }
                    assertThat(kept.length).isLessThanOrEqualTo(testCase.halfWidth().length);
                    for (int i = 0; i < kept.length; i++) {
                        assertThat(kept[i])
                                .as("'%s' cut to %d changed byte %d",
                                        testCase.name(), testCase.maxBytes(), i)
                                .isEqualTo(testCase.halfWidth()[i]);
                    }
                });
    }

    /// Whatever comes out is writable into the field it was converted for.
    ///
    /// The claim that makes the engine useful: a caller who transliterates for
    /// a field and then writes the result should never be told by this library's
    /// own validator that the result is invalid.
    @Test
    void everythingProducedIsWritableIntoTheFieldItWasMadeFor() {
        for (CharacterClass characterClass : List.of(CharacterClass.PARTY_NAME,
                CharacterClass.BANK_NAME, CharacterClass.PAYROLL_NAME)) {

            var options = TransliterationOptions.builder()
                    .characterClass(characterClass)
                    .unmappable(UnmappableCharacterPolicy.DROP)
                    .build();

            Seeded.property("output satisfies " + characterClass, 1000, SEED,
                    KanaProperties::generate,
                    testCase -> {
                        String text = KanaTransliterator.toHalfWidth(
                                testCase.name(), options).text();
                        byte[] bytes = ZenginCharset.MS932.encode(text);

                        assertThat(CharacterSet.validate(bytes, 0, bytes.length, characterClass))
                                .as("'%s' became '%s', which %s does not permit",
                                        testCase.name(), text, characterClass)
                                .isEmpty();
                    });
        }
    }

    /// And it stays writable after being cut to length.
    @Test
    void truncatedOutputIsStillWritable() {
        var options = TransliterationOptions.builder()
                .characterClass(CharacterClass.PARTY_NAME)
                .truncation(TruncationPolicy.TRUNCATE_SAFE)
                .build();

        Seeded.property("truncated output is still writable", 2000, SEED,
                KanaProperties::generate,
                testCase -> {
                    String text;
                    try {
                        text = KanaTransliterator.toHalfWidth(
                                testCase.name(), testCase.maxBytes(), options).text();
                    } catch (FieldTooSmallException tooSmall) {
                        return;
                    }
                    byte[] bytes = ZenginCharset.MS932.encode(text);

                    assertThat(bytes.length)
                            .as("'%s' cut to %d produced %d bytes",
                                    testCase.name(), testCase.maxBytes(), bytes.length)
                            .isLessThanOrEqualTo(testCase.maxBytes());
                    assertThat(CharacterSet.validate(bytes, 0, bytes.length,
                                    CharacterClass.PARTY_NAME))
                            .as("'%s' cut to %d became unwritable",
                                    testCase.name(), testCase.maxBytes())
                            .isEmpty();
                });
    }

    /// Narrowing then widening returns the name it started from.
    ///
    /// Only claimed for kana, and only in that direction: several full-width
    /// characters narrow to the same half-width sequence, so the other way round
    /// is informational (R-K8). For the kana in these names it is exact, and that
    /// is worth pinning — a decomposition that lost a mark would show up here.
    @Test
    void kanaSurviveNarrowingAndWideningUnchanged() {
        Seeded.property("kana round trip", 2000, SEED,
                KanaProperties::generate,
                testCase -> {
                    String narrowed = KanaTransliterator.toHalfWidth(testCase.name()).text();
                    String widened = KanaTransliterator.toFullWidth(narrowed).text();

                    assertThat(widened)
                            .as("'%s' narrowed to '%s' and came back as '%s'",
                                    testCase.name(), narrowed, widened)
                            .isEqualTo(testCase.name());
                });
    }

    /// Transliteration is deterministic: the same input always gives the same bytes.
    @Test
    void transliterationIsDeterministic() {
        Seeded.property("determinism", 1000, SEED,
                KanaProperties::generate,
                testCase -> assertThat(KanaTransliterator.toHalfWidth(testCase.name()).text())
                        .isEqualTo(KanaTransliterator.toHalfWidth(testCase.name()).text()));
    }
}
