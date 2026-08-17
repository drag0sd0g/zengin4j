package io.zengin4j.core.kana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the transliterator does, and what it refuses to do.
 *
 * @see KanaTableTest for the tables themselves (R-T10)
 * @see TruncationTest for the dakuten hazard (R-T11, INV-4)
 */
class TransliterationTest {

    private static final TransliterationOptions PARTY = TransliterationOptions.builder()
            .characterClass(CharacterClass.PARTY_NAME).build();

    private static final TransliterationOptions PAYROLL = TransliterationOptions.builder()
            .characterClass(CharacterClass.PAYROLL_NAME).build();

    // -------------------------------------------------------------- narrowing

    @Test
    void katakanaBecomesHalfWidth() {
        assertThat(KanaTransliterator.toHalfWidth("タロウ").text()).isEqualTo("ﾀﾛｳ");
    }

    @Test
    void aVoicedNameKeepsItsVoicing() {
        assertThat(KanaTransliterator.toHalfWidth("ガクブチ ジロウ").text())
                .isEqualTo("ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ");
    }

    @Test
    void semiVoicedKanaSurviveToo() {
        assertThat(KanaTransliterator.toHalfWidth("パピプペポ").text()).isEqualTo("ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ");
    }

    @Test
    void vuIsSpelledWithTheOnlyFormTheseFilesHave() {
        assertThat(KanaTransliterator.toHalfWidth("ヴ").text()).isEqualTo("ｳﾞ");
    }

    @Test
    void fullWidthLatinAndDigitsNarrow() {
        assertThat(KanaTransliterator.toHalfWidth("ＡＢＣ１２３").text()).isEqualTo("ABC123");
    }

    @Test
    void lowercaseIsFoldedBecauseNoFieldClassPermitsIt() {
        Transliteration result = KanaTransliterator.toHalfWidth("abc");

        assertThat(result.text()).isEqualTo("ABC");
        assertThat(result.loss().bySeverity(LossSeverity.INFORMATIONAL)).isNotEmpty();
        assertThat(result.isMateriallyChanged())
                .as("case carries no meaning in a field that can only hold one of them")
                .isFalse();
    }

    @Test
    void textAlreadyInHalfWidthIsLeftAlone() {
        assertThat(KanaTransliterator.toHalfWidth("ﾀﾛｳ").text()).isEqualTo("ﾀﾛｳ");
        assertThat(KanaTransliterator.toHalfWidth("ﾀﾛｳ").isLossless()).isTrue();
    }

    // ------------------------------------------------- the corrections to R-K2

    /**
     * R-K2 says to map ー to ｰ. This library's own validator disagrees.
     *
     * <p>{@code CharacterClass} excludes the prolonged sound mark from every
     * field class, so following R-K2 would emit text that {@code V-202} rejects.
     * The standard writes a long vowel as a hyphen; see ADR-0028.
     */
    @Test
    void aLongVowelBecomesAHyphenNotAProlongedSoundMark() {
        Transliteration result = KanaTransliterator.toHalfWidth("ヨーコ", PARTY);

        assertThat(result.text()).isEqualTo("ﾖ-ｺ");
        assertThat(result.text()).doesNotContain("ｰ");
        assertThat(result.isMateriallyChanged()).isTrue();
    }

    /** And R-K2's other named mapping, ャ to ｬ, is wrong for the same reason. */
    @Test
    void aSmallKanaBecomesItsFullSizeFormNotASmallHalfWidthOne() {
        Transliteration result = KanaTransliterator.toHalfWidth("キャノン", PARTY);

        assertThat(result.text()).isEqualTo("ｷﾔﾉﾝ");
        assertThat(result.text()).doesNotContain("ｬ");
        assertThat(result.isMateriallyChanged())
                .as("キャノン and キヤノン read differently to a human")
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ァ", "ィ", "ゥ", "ェ", "ォ", "ッ", "ャ", "ュ", "ョ", "ヮ", "ヵ", "ヶ"})
    void everySmallKanaIsWrittenFullSize(String small) {
        Transliteration result = KanaTransliterator.toHalfWidth(small, PARTY);

        byte[] bytes = ZenginCharset.MS932.encode(result.text());
        assertThat(CharacterSet.isClean(bytes, 0, bytes.length, CharacterClass.PARTY_NAME))
                .as("'%s' became '%s', which party names must permit", small, result.text())
                .isTrue();
    }

    /** Half-width small kana arriving as input get the same treatment. */
    @ParameterizedTest
    @ValueSource(strings = {"ｧ", "ｨ", "ｩ", "ｪ", "ｫ", "ｯ", "ｬ", "ｭ", "ｮ", "ｰ"})
    void halfWidthInputIsNormalisedTheSameWay(String small) {
        Transliteration result = KanaTransliterator.toHalfWidth(small, PARTY);

        byte[] bytes = ZenginCharset.MS932.encode(result.text());
        assertThat(CharacterSet.isClean(bytes, 0, bytes.length, CharacterClass.PARTY_NAME))
                .as("'%s' became '%s'", small, result.text())
                .isTrue();
    }

    // --------------------------------------------------------- the field class

    /**
     * The same name transliterates differently depending on the field.
     *
     * <p>The finding that shaped this API. A long vowel becomes a hyphen, and
     * payroll names admit no symbols, so ヨーコ has a spelling in a 総合振込 file
     * and none in a 給与振込 one. A transliterator taking only a string would be
     * wrong for one of them.
     */
    @Test
    void aNameWritableInOneFieldCanBeUnwritableInAnother() {
        assertThat(KanaTransliterator.toHalfWidth("ヨーコ", PARTY).text()).isEqualTo("ﾖ-ｺ");

        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ヨーコ", PAYROLL))
                .satisfies(e -> assertThat(e.characterClass())
                        .isEqualTo(CharacterClass.PAYROLL_NAME));
    }

    @Test
    void droppingIsAvailableForCallersWhoPreferItToRefusal() {
        TransliterationOptions dropping = TransliterationOptions.builder()
                .characterClass(CharacterClass.PAYROLL_NAME)
                .unmappable(UnmappableCharacterPolicy.DROP)
                .build();

        Transliteration result = KanaTransliterator.toHalfWidth("ヨーコ", dropping);

        assertThat(result.text()).isEqualTo("ﾖｺ");
        assertThat(result.loss().bySeverity(LossSeverity.MATERIAL))
                .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.DROPPED));
    }

    @Test
    void latinIsRefusedInAPayrollNameWhichAdmitsNone() {
        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ＡＢＣ", PAYROLL));
    }

    @Test
    void everyOutputSatisfiesItsOwnFieldClass() {
        for (CharacterClass characterClass : CharacterClass.values()) {
            TransliterationOptions options = TransliterationOptions.builder()
                    .characterClass(characterClass)
                    .unmappable(UnmappableCharacterPolicy.DROP)
                    .build();

            Transliteration result = KanaTransliterator.toHalfWidth("ヤマダ タロウ", options);
            byte[] bytes = ZenginCharset.MS932.encode(result.text());

            assertThat(CharacterSet.validate(bytes, 0, bytes.length, characterClass))
                    .as("%s produced '%s', which it does not permit",
                            characterClass, result.text())
                    .isEmpty();
        }
    }

    // ------------------------------------------------------------- refusals

    /** R-K6: a kanji's reading is ambiguous, so it is refused rather than guessed. */
    @Test
    void kanjiIsRefusedAndTheCharactersAreNamed() {
        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("山田太郎"))
                .satisfies(e -> assertThat(e.offendingCharacters()).contains("山", "田"))
                .withMessageContaining("reading is ambiguous")
                .withMessageContaining("katakana");
    }

    /** P4: no reading dictionary, so no guess — however common the name. */
    @Test
    void aCommonSurnameIsStillRefused() {
        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("東"));
    }

    @Test
    void aLongRunOfKanjiIsSummarisedRatherThanListedInFull() {
        String many = "一二三四五六七八九十百千万";

        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth(many))
                .withMessageContaining("more");
    }

    @Test
    void hiraganaIsRefusedByDefault() {
        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("やまだ"))
                .withMessageContaining("HiraganaPolicy.CONVERT");
    }

    @Test
    void hiraganaConvertsWhenAskedAndSaysSo() {
        TransliterationOptions converting = TransliterationOptions.builder()
                .hiragana(HiraganaPolicy.CONVERT).build();

        Transliteration result = KanaTransliterator.toHalfWidth("やまだ", converting);

        assertThat(result.text()).isEqualTo("ﾔﾏﾀﾞ");
        assertThat(result.isMateriallyChanged())
                .as("R-K5: conversion is never silent")
                .isTrue();
    }

    @Test
    void kanjiIsReportedBeforeHiraganaBecauseItIsTheHarderProblem() {
        assertThatExceptionOfType(UntransliterableCharacterException.class)
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("山だ"))
                .withMessageContaining("kanji");
    }

    // --------------------------------------------------------------- widening

    @Test
    void halfWidthWidensBackForDisplay() {
        assertThat(KanaTransliterator.toFullWidth("ｶﾞｸﾌﾞﾁ").text()).isEqualTo("ガクブチ");
    }

    @Test
    void wideningKeepsVoicingTogether() {
        assertThat(KanaTransliterator.toFullWidth("ﾊﾟ").text()).isEqualTo("パ");
        assertThat(KanaTransliterator.toFullWidth("ｳﾞ").text()).isEqualTo("ヴ");
    }

    @Test
    void wideningIsInformationalBecauseItIsNotReversible() {
        Transliteration result = KanaTransliterator.toFullWidth("ｶﾞｸ");

        assertThat(result.isMateriallyChanged()).isFalse();
        assertThat(result.loss().bySeverity(LossSeverity.INFORMATIONAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("not", "reversible"));
    }

    @Test
    void wideningTextWithNothingToWidenLosesNothing() {
        assertThat(KanaTransliterator.toFullWidth("ABC").isLossless()).isFalse();
        assertThat(KanaTransliterator.toFullWidth("").isLossless()).isTrue();
    }

    // ------------------------------------------------------------- the shape

    @Test
    void emptyTextIsNotAnError() {
        Transliteration result = KanaTransliterator.toHalfWidth("");

        assertThat(result.text()).isEmpty();
        assertThat(result.isLossless()).isTrue();
    }

    @Test
    void nullIsRejectedByName() {
        assertThatNullPointerException()
                .isThrownBy(() -> KanaTransliterator.toHalfWidth(null));
        assertThatNullPointerException()
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ア", null));
    }

    @Test
    void aNonPositiveFieldWidthIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KanaTransliterator.toHalfWidth("ア", 0,
                        TransliterationOptions.defaults()));
    }

    @ParameterizedTest
    @EnumSource(CharacterClass.class)
    void everyFieldClassCanBeAskedFor(CharacterClass characterClass) {
        TransliterationOptions options =
                TransliterationOptions.builder().characterClass(characterClass).build();

        assertThat(options.characterClass()).isEqualTo(characterClass);
    }

    @Test
    void theDefaultsRefuseRatherThanAlter() {
        TransliterationOptions defaults = TransliterationOptions.defaults();

        assertThat(defaults.truncation()).isEqualTo(TruncationPolicy.REJECT_IF_TOO_LONG);
        assertThat(defaults.hiragana()).isEqualTo(HiraganaPolicy.REJECT);
        assertThat(defaults.unmappable()).isEqualTo(UnmappableCharacterPolicy.REJECT);
    }

    @Test
    void anEmptyTruncationMarkerIsRefusedBecauseItWouldMarkNothing() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TransliterationOptions.builder().truncationMarker(""));
    }
}
