package io.zengin4j.core.kana;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.charset.VoicingMarks;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/// Truncation at every byte length, over names built to break it (R-T11, INV-4).
///
/// The hazard is one byte wide and silent. `ｶﾞ` is `0xB6 0xDE`;
/// cut between them and ガクブチ becomes カクブチ, which is a different payee in
/// a file that records nothing about the change. So this does not test a few
/// lengths — it tests *every* length from one byte to past the end, over a
/// corpus chosen to place voicing marks at every position, including first, last
/// and adjacent.
class TruncationTest {

    /// Names placing voicing marks where they can do the most damage.
    ///
    /// Not realistic names, deliberately. A corpus of plausible ones would
    /// cluster the marks in the middle and leave the boundaries untested.
    static List<String> corpus() {
        return List.of(
                "ｶﾞｸﾌﾞﾁ",        // marks at 1 and 4
                "ｶﾞ",             // a single voiced character
                "ｶﾞｷﾞｸﾞｹﾞｺﾞ",   // every character voiced: marks at every odd index
                "ｱｶﾞ",            // mark late
                "ｶﾞｱ",            // mark early
                "ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ",   // semi-voiced throughout
                "ｱｲｳｴｵ",          // no marks at all
                "ｳﾞｧ",            // vu, the only v-sound these files can spell
                "ﾀﾞ",             // two bytes exactly
                "ｱﾞ".substring(0, 1) + "ｲ",  // a bare kana, then another
                "ﾃｽﾄｼﾞ",         // mark at the end
                "ｼﾞｮ".substring(0, 2));      // mark in the middle of three bytes
    }

    private static byte[] bytes(String text) {
        return ZenginCharset.MS932.encode(text);
    }

    // -------------------------------------------------------------- INV-4

    /// INV-4 — a truncated result never ends mid-character and never begins with
    /// a mark, at any length, for any name in the corpus.
    ///
    /// The invariant the whole engine exists to hold.
    @ParameterizedTest
    @MethodSource("corpus")
    void inv4_truncationNeverStrandsAVoicingMark(String name) {
        byte[] full = bytes(name);

        for (int max = 1; max <= full.length + 2; max++) {
            byte[] kept;
            try {
                kept = KanaTransliterator.truncateSafe(full, max);
            } catch (FieldTooSmallException tooSmall) {
                // A legitimate outcome: the field cannot hold one character.
                continue;
            }

            assertThat(kept.length)
                    .as("'%s' truncated to %d must not grow", name, max)
                    .isLessThanOrEqualTo(Math.max(max, full.length));

            if (kept.length == 0) {
                continue;
            }
            assertThat(VoicingMarks.isMark(kept[0] & 0xFF))
                    .as("'%s' truncated to %d begins with a stranded mark", name, max)
                    .isFalse();

            // Every mark that survived must still have its kana in front of it.
            for (int i = 0; i < kept.length; i++) {
                int mark = kept[i] & 0xFF;
                if (!VoicingMarks.isMark(mark)) {
                    continue;
                }
                int base = i == 0 ? -1 : kept[i - 1] & 0xFF;
                assertThat(VoicingMarks.isLegal(base, mark))
                        .as("'%s' truncated to %d left a mark at %d with no kana to modify",
                                name, max, i)
                        .isTrue();
            }
        }
    }

    /// And the kept prefix is genuinely a prefix — nothing was substituted on the
    /// way out.
    @ParameterizedTest
    @MethodSource("corpus")
    void truncationOnlyEverRemovesFromTheEnd(String name) {
        byte[] full = bytes(name);

        for (int max = 1; max <= full.length; max++) {
            byte[] kept;
            try {
                kept = KanaTransliterator.truncateSafe(full, max);
            } catch (FieldTooSmallException tooSmall) {
                continue;
            }
            for (int i = 0; i < kept.length; i++) {
                assertThat(kept[i])
                        .as("'%s' truncated to %d changed byte %d", name, max, i)
                        .isEqualTo(full[i]);
            }
        }
    }

    // ------------------------------------------------------- the exact cut

    /// The case §16.2 describes, at the byte where it happens.
    ///
    /// `ｶﾞｸﾌﾞﾁ` is `B6 DE B8 CC DE C1`. A four-byte field would cut
    /// at index 4, which is the dakuten belonging to `ﾌ` at index 3. Keeping
    /// `ﾌ` without its mark turns ブ into フ, so both go.
    ///
    /// R-K3, at the byte where it matters.
    @Test
    void aCutLandingOnAMarkTakesItsKanaWithIt() {
        byte[] full = bytes("ｶﾞｸﾌﾞﾁ");

        byte[] kept = KanaTransliterator.truncateSafe(full, 4);

        assertThat(ZenginCharset.MS932.decode(kept, 0, kept.length))
                .as("keeping ﾌ without its ﾞ would rename ブ to フ")
                .isEqualTo("ｶﾞｸ");
        assertThat(kept).hasSize(3);
    }

    @Test
    void aCutThatFallsBetweenCharactersKeepsEverythingItCan() {
        byte[] full = bytes("ｶﾞｸﾌﾞﾁ");

        assertThat(KanaTransliterator.truncateSafe(full, 3)).hasSize(3);
        assertThat(KanaTransliterator.truncateSafe(full, 5)).hasSize(5);
    }

    @Test
    void textThatAlreadyFitsIsReturnedUnchanged() {
        byte[] full = bytes("ｱｲｳ");

        assertThat(KanaTransliterator.truncateSafe(full, 3)).isSameAs(full);
        assertThat(KanaTransliterator.truncateSafe(full, 99)).isSameAs(full);
    }

    // ------------------------------------------------------------- refusals

    @Test
    void aFieldTooSmallForOneCharacterIsRefused() {
        assertThatExceptionOfType(FieldTooSmallException.class)
                .isThrownBy(() -> KanaTransliterator.truncateSafe(bytes("ｶﾞｸ"), 1))
                .satisfies(e -> assertThat(e.maxBytes()).isEqualTo(1));
    }

    /// A leading voicing mark is refused whatever the length.
    ///
    /// §16.3's reference implementation checks this only on the truncating
    /// path, so a short input beginning with a stray mark passes and a long one
    /// does not. The input is equally damaged either way, and text in that state
    /// has usually been cut at a byte boundary somewhere upstream.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 10})
    void aLeadingVoicingMarkIsRefusedAtEveryLength(int maxBytes) {
        byte[] damaged = {(byte) 0xDE, (byte) 0xB6};

        assertThatExceptionOfType(OrphanedVoicingMarkException.class)
                .isThrownBy(() -> KanaTransliterator.truncateSafe(damaged, maxBytes));
    }

    @Test
    void aNonPositiveWidthIsARequestThatMakesNoSense() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KanaTransliterator.truncateSafe(bytes("ｱ"), 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KanaTransliterator.truncateSafe(bytes("ｱ"), -1));
    }

    // ------------------------------------------------------------- policies

    /// R-K4: the three policies, of which the default is to refuse.
    @Test
    void theDefaultPolicyRefusesRatherThanShortenAName() {
        TransliterationOptions options = TransliterationOptions.defaults();

        assertThatExceptionOfType(ValueTooLongException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ガクブチ", 4, options))
                .withMessageContaining("does not fit")
                .withMessageContaining("not a codec's to cut")
                .satisfies(refused -> {
                    assertThat(refused.maxBytes()).isEqualTo(4);
                    assertThat(refused.byteLength()).isEqualTo(6);
                    assertThat(refused.text()).isEqualTo("ｶﾞｸﾌﾞﾁ");
                });
    }

    @Test
    void truncateSafeShortensAndSaysSo() {
        TransliterationOptions options = TransliterationOptions.builder()
                .truncation(TruncationPolicy.TRUNCATE_SAFE)
                .build();

        Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ", 4, options);

        assertThat(result.text()).isEqualTo("ｶﾞｸ");
        assertThat(result.isMateriallyChanged()).isTrue();
        assertThat(result.loss().bySeverity(LossSeverity.MATERIAL))
                .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.TRUNCATED));
    }

    @Test
    void truncateWithMarkerLeavesASignThatSomethingWasRemoved() {
        TransliterationOptions options = TransliterationOptions.builder()
                .truncation(TruncationPolicy.TRUNCATE_WITH_MARKER)
                .build();

        Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ", 4, options);

        assertThat(result.text()).isEqualTo("ｶﾞｸ-");
        assertThat(bytes(result.text())).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void theMarkerCostsAByteSoTheTextIsShortenedFurther() {
        TransliterationOptions marked = TransliterationOptions.builder()
                .truncation(TruncationPolicy.TRUNCATE_WITH_MARKER).build();
        TransliterationOptions plain = TransliterationOptions.builder()
                .truncation(TruncationPolicy.TRUNCATE_SAFE).build();

        String withMarker = KanaTransliterator.toHalfWidth("ガクブチ", 5, marked).text();
        String withoutMarker = KanaTransliterator.toHalfWidth("ガクブチ", 5, plain).text();

        assertThat(bytes(withMarker)).hasSizeLessThanOrEqualTo(5);
        assertThat(bytes(withoutMarker)).hasSizeLessThanOrEqualTo(5);
        assertThat(withMarker).endsWith("-");
        assertThat(withoutMarker).doesNotEndWith("-");
    }

    /// A marker the field would refuse is refused before it is written.
    ///
    /// Easy to get wrong and silent when wrong: `*` — the obvious
    /// marker, and this library's original default — is permitted by *no* name
    /// class, so marked truncation produced a shortened name that `V-202`
    /// then rejected. A policy whose purpose is to make a change visible was
    /// making the file invalid instead.
    @Test
    void aMarkerTheFieldWouldRefuseIsRefused() {
        TransliterationOptions starred = TransliterationOptions.builder()
                .truncation(TruncationPolicy.TRUNCATE_WITH_MARKER)
                .truncationMarker("*")
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ガクブチ", 4, starred))
                .withMessageContaining("not permitted");
    }

    /// And payroll names cannot be marked at all, because they admit no symbol.
    @Test
    void markedTruncationIsImpossibleInAPayrollNameAndSaysSo() {
        TransliterationOptions payroll = TransliterationOptions.builder()
                .characterClass(io.zengin4j.core.charset.CharacterClass.PAYROLL_NAME)
                .truncation(TruncationPolicy.TRUNCATE_WITH_MARKER)
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ガクブチ", 4, payroll))
                .withMessageContaining("TRUNCATE_SAFE");
    }

    /// Whatever the marker, the result is writable into the field.
    @Test
    void aMarkedResultIsAlwaysWritable() {
        for (io.zengin4j.core.charset.CharacterClass characterClass
                : new io.zengin4j.core.charset.CharacterClass[] {
                    io.zengin4j.core.charset.CharacterClass.BANK_NAME,
                    io.zengin4j.core.charset.CharacterClass.PARTY_NAME}) {

            TransliterationOptions options = TransliterationOptions.builder()
                    .characterClass(characterClass)
                    .truncation(TruncationPolicy.TRUNCATE_WITH_MARKER)
                    .build();

            String text = KanaTransliterator.toHalfWidth("ガクブチ", 4, options).text();
            byte[] jis = bytes(text);

            assertThat(io.zengin4j.core.charset.CharacterSet.validate(jis, 0, jis.length,
                            characterClass))
                    .as("%s produced '%s', which it does not permit", characterClass, text)
                    .isEmpty();
        }
    }

    @Test
    void aMarkerThatWouldNotItselfFitIsRefused() {
        TransliterationOptions options = TransliterationOptions.builder()
                .truncation(TruncationPolicy.TRUNCATE_WITH_MARKER)
                .truncationMarker("---")
                .build();

        assertThatExceptionOfType(FieldTooSmallException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ガクブチ", 2, options));
    }

    // ------------------------------------------------- measured in the output

    /// Truncation counts bytes of the encoding the file will be written in.
    ///
    /// A half-width kana is one byte in JIS X 0201 and three in UTF-8. The
    /// string-level methods used to hand UTF-8 bytes to {@link
    /// KanaTransliterator#truncateSafe(byte[], int)}, which reads JIS and so
    /// looked for `0xDE` where the mark's first byte is `0xEF` — it
    /// kept the base and dropped the mark, turning ﾌﾞ into ﾌ. The silent rename
    /// the engine exists to prevent, reintroduced by measuring in the wrong
    /// units.
    @Test
    void truncationIsSafeWhateverEncodingTheFileUses() {
        TransliterationOptions utf8 = TransliterationOptions.builder()
                .charset(ZenginCharset.UTF_8)
                .truncation(TruncationPolicy.TRUNCATE_SAFE)
                .build();

        // ｶﾞｸﾌﾞﾁ is 18 UTF-8 bytes. A 12-byte budget reaches four characters,
        // and the fifth is ﾌ's voicing mark — so ﾌ goes too.
        Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ", 12, utf8);

        assertThat(result.text())
                .as("keeping ﾌ without its ﾞ would rename ブ to フ")
                .isEqualTo("ｶﾞｸ");
        assertThat(ZenginCharset.UTF_8.encode(result.text())).hasSizeLessThanOrEqualTo(12);
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void noEncodingLetsAMarkBeSeparatedFromItsKana(String name) {
        for (ZenginCharset charset : ZenginCharset.values()) {
            TransliterationOptions options = TransliterationOptions.builder()
                    .charset(charset)
                    .truncation(TruncationPolicy.TRUNCATE_SAFE)
                    .build();
            byte[] full = charset.encode(name);

            for (int max = 1; max <= full.length + 2; max++) {
                String shortened;
                try {
                    shortened = KanaTransliterator.toHalfWidth(name, max, options).text();
                } catch (FieldTooSmallException | OrphanedVoicingMarkException expected) {
                    continue;
                }
                assertThat(charset.encode(shortened))
                        .as("'%s' cut to %d bytes in %s overflowed", name, max, charset)
                        .hasSizeLessThanOrEqualTo(max);

                byte[] jis = ZenginCharset.MS932.encode(shortened);
                for (int i = 0; i < jis.length; i++) {
                    int mark = jis[i] & 0xFF;
                    if (!VoicingMarks.isMark(mark)) {
                        continue;
                    }
                    assertThat(VoicingMarks.isLegal(i == 0 ? -1 : jis[i - 1] & 0xFF, mark))
                            .as("'%s' cut to %d bytes in %s stranded a mark", name, max, charset)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void textThatFitsIsNotTouchedByAnyPolicy() {
        for (TruncationPolicy policy : TruncationPolicy.values()) {
            TransliterationOptions options =
                    TransliterationOptions.builder().truncation(policy).build();

            Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ", 20, options);

            assertThat(result.text()).as("%s", policy).isEqualTo("ｶﾞｸﾌﾞﾁ");
            assertThat(result.loss().bySeverity(LossSeverity.MATERIAL))
                    .as("%s should record no material loss for text that fits", policy)
                    .isEmpty();
        }
    }
}
