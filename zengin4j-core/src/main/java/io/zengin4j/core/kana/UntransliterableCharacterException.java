package io.zengin4j.core.kana;

import module java.base;
import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.error.ZenginException;

/// Text contains characters that cannot be written into the target field.
///
/// Three different situations reach here, and the message says which:
///
/// - **Kanji.** Readings are ambiguous — 東 is ヒガシ, トウ or
///   アズマ depending on the name — and guessing wrong on a beneficiary
///   misroutes a payment. This library does not ship a reading dictionary
///   and will not guess (R-K6, P4).
/// - **Hiragana**, when the hiragana policy is
///   [HiraganaPolicy#REJECT]. Convertible, but refused by default
///   because it usually means the wrong field arrived.
/// - **A character with no permitted form**, when the policy is
///   [UnmappableCharacterPolicy#REJECT]. A long vowel in a payroll
///   name is the case that matters: it becomes a hyphen, and payroll names
///   admit no symbols.
///
/// @since 0.4.0
public final class UntransliterableCharacterException extends ZenginException {

    private static final long serialVersionUID = 1L;

    /// How many offending characters to name before summarising the rest.
    private static final int NAMED = 8;

    private final String offendingCharacters;
    private final transient CharacterClass characterClass;

    private UntransliterableCharacterException(String messageEn, String messageJa,
            String offendingCharacters, CharacterClass characterClass) {
        super(messageEn, messageJa);
        this.offendingCharacters = offendingCharacters;
        this.characterClass = characterClass;
    }

    /// Raised for kanji, which cannot be transliterated correctly (R-K6, P4).
    ///
    /// @param offending the kanji found, in order of appearance
    /// @param source    the text they came from
    /// @return the exception
    public static UntransliterableCharacterException kanji(List<String> offending, String source) {
        String named = name(offending);
        return new UntransliterableCharacterException(
                "'" + source + "' contains kanji (" + named + "), which cannot be transliterated:"
                        + " a kanji's reading is ambiguous — 東 is ヒガシ, トウ or アズマ depending"
                        + " on the name — and guessing wrong on a payee misroutes the payment."
                        + " Supply the name in katakana, which is what the file format carries.",
                "'" + source + "' に漢字 (" + named + ") が含まれています。漢字の読みは一意ではなく"
                        + "（東はヒガシ・トウ・アズマ）、誤った読みは振込先の誤りにつながるため変換しません。"
                        + "カタカナで指定してください。",
                named, null);
    }

    /// Raised for hiragana under [HiraganaPolicy#REJECT] (R-K5).
    ///
    /// @param offending the hiragana found
    /// @param source    the text they came from
    /// @return the exception
    public static UntransliterableCharacterException hiragana(List<String> offending, String source) {
        String named = name(offending);
        return new UntransliterableCharacterException(
                "'" + source + "' contains hiragana (" + named + "). These convert to katakana"
                        + " unambiguously, but conversion is off by default because a name arriving"
                        + " in hiragana usually means the wrong field was sent."
                        + " Set HiraganaPolicy.CONVERT to convert them, and read the loss report.",
                "'" + source + "' にひらがな (" + named + ") が含まれています。カタカナへの変換は"
                        + "一意ですが、ひらがなの氏名は誤った項目が渡された兆候であることが多いため、"
                        + "既定では変換しません。変換する場合は HiraganaPolicy.CONVERT を指定し、"
                        + "損失レポートを確認してください。",
                named, null);
    }

    /// Raised for a character with no form the target field permits.
    ///
    /// @param offending      the characters with no permitted form
    /// @param source         the text they came from
    /// @param characterClass the field class that refuses them
    /// @return the exception
    public static UntransliterableCharacterException unmappable(List<String> offending,
            String source, CharacterClass characterClass) {
        Objects.requireNonNull(characterClass, "characterClass");
        String named = name(offending);
        return new UntransliterableCharacterException(
                "'" + source + "' contains " + named + ", which has no form permitted in "
                        + characterClass.nameEn() + ". A long vowel becomes a hyphen, and payroll"
                        + " names admit no symbols at all, so some names have no legal spelling in"
                        + " that field. Set UnmappableCharacterPolicy.DROP to drop the character"
                        + " and record the loss, or change the name at source.",
                "'" + source + "' に " + named + " が含まれていますが、"
                        + characterClass.nameJa() + "では使用できる字形がありません。"
                        + "長音はハイフンになりますが、給与・賞与振込の名称は記号を一切使用できません。"
                        + "文字を削除する場合は UnmappableCharacterPolicy.DROP を指定してください。",
                named, characterClass);
    }

    /// Raised for a voicing mark that no neighbouring kana can carry (R-K7).
    ///
    /// @param stranded the base-and-mark pairs that are not characters
    /// @param source   the text they came from
    /// @return the exception
    public static UntransliterableCharacterException strandedVoicingMark(List<String> stranded,
            String source) {
        String named = name(stranded);
        return new UntransliterableCharacterException(
                "'" + source + "' produces " + named + ", where a voicing mark follows a kana that"
                        + " has no voiced form. That is not a character the standard recognises,"
                        + " and writing it would produce a file this library's own V-206 rule"
                        + " rejects. ヷ and ヺ do this: Unicode decomposes them into ワ and ヲ"
                        + " plus a mark, and neither kana has a voiced form."
                        + " Set UnmappableCharacterPolicy.DROP to drop the mark and record the"
                        + " loss, or spell the name without it.",
                "'" + source + "' から " + named + " が生成されます。濁点・半濁点が、"
                        + "濁音を持たない仮名に続いています。標準では文字として成立せず、"
                        + "本ライブラリの V-206 が指摘するファイルになります。"
                        + "削除する場合は UnmappableCharacterPolicy.DROP を指定してください。",
                named, null);
    }

    /// The characters that could not be transliterated.
    ///
    /// @return the offending characters, comma-separated
    public String offendingCharacters() {
        return offendingCharacters;
    }

    /// The field class that refused them, where one was involved.
    ///
    /// @return the class, or `null` for kanji and hiragana
    public CharacterClass characterClass() {
        return characterClass;
    }

    /// Names the offenders without letting the message run away.
    ///
    /// A field of kanji would otherwise produce a message longer than the
    /// screen, and the first few are enough to find the problem (R-E5: the count
    /// is reported rather than the list silently cut).
    private static String name(List<String> offending) {
        Objects.requireNonNull(offending, "offending");
        List<String> distinct = offending.stream().distinct().toList();
        if (distinct.size() <= NAMED) {
            return String.join(", ", distinct);
        }
        return String.join(", ", distinct.subList(0, NAMED))
                + " and " + (distinct.size() - NAMED) + " more";
    }
}
