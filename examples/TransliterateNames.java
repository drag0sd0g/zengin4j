import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.CharacterWritePolicy;
import io.zengin4j.core.codec.EncodingOptions;
import io.zengin4j.core.codec.RecordEncoder;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.kana.KanaTransliterator;
import io.zengin4j.core.kana.Transliteration;
import io.zengin4j.core.kana.TransliterationOptions;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.kana.UntransliterableCharacterException;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.loss.LossEntry;

/// Getting names out of a source system and into a payment file (§16).
///
/// Run it with:
///
/// ```
/// ./gradlew runExamples
/// ```
///
/// Names in a CRM or payroll system are full-width, mixed case, and sometimes
/// kanji. A Zengin file carries half-width katakana in a fixed number of bytes.
/// Everything between those two facts is this example.
///
/// **Every name here is invented** (R-L1).
void main() {
    theEasyCases();
    theOnesThatChangeTheName();
    theOneThatDependsOnTheField();
    whatIsRefused();
    fittingAField();
    throughTheEncoder();
}

/// Narrowing, decomposition and case folding: nothing a reader must act on.
private static void theEasyCases() {
    System.out.println("== conversions that cost nothing ==");

    for (String name : new String[] {"タロウ", "ガクブチ ジロウ", "パピプペポ", "ヴァイオリン",
            "ＡＢＣ１２３", "abc"}) {
        Transliteration result = KanaTransliterator.toHalfWidth(name);
        System.out.printf("  %-14s -> %-16s %s%n", name, result.text(),
                result.isMateriallyChanged() ? "(name reads differently)" : "");
    }
    System.out.println("  ヴ is ｳ+ﾞ — the only way these files can spell a v sound.");
}

/// The two mappings the build specification gets wrong.
///
/// R-K2 says ー becomes ｰ and ャ becomes ｬ. Neither is permitted in any
/// field, so following it would produce files this library rejects. See
/// ADR-0028.
private static void theOnesThatChangeTheName() {
    System.out.println();
    System.out.println("== conversions that change how a name reads ==");

    for (String name : new String[] {"ヨーコ", "キャノン", "サッポロ"}) {
        Transliteration result = KanaTransliterator.toHalfWidth(name);
        System.out.printf("  %-10s -> %-10s%n", name, result.text());
        result.loss().atLeast(io.zengin4j.core.loss.LossSeverity.MATERIAL)
                .forEach(entry -> System.out.println("        " + entry.explanationEn()));
    }
}

/// The same name, two fields, two answers.
///
/// A long vowel becomes a hyphen, and payroll names admit no symbols — so
/// ヨーコ has a spelling in a 総合振込 file and none in a 給与振込 one. This is
/// why transliteration takes a character class.
private static void theOneThatDependsOnTheField() {
    System.out.println();
    System.out.println("== the same name, two different fields ==");

    TransliterationOptions party = TransliterationOptions.builder()
            .characterClass(CharacterClass.PARTY_NAME).build();
    TransliterationOptions payroll = TransliterationOptions.builder()
            .characterClass(CharacterClass.PAYROLL_NAME).build();

    System.out.println("  ヨーコ into a 総合振込 name:  "
            + KanaTransliterator.toHalfWidth("ヨーコ", party).text());
    try {
        KanaTransliterator.toHalfWidth("ヨーコ", payroll);
    } catch (UntransliterableCharacterException refused) {
        System.out.println("  ヨーコ into a 給与振込 name:  refused");
        System.out.println("        payroll names admit no symbols, so the hyphen "
                + "has nowhere to go");
    }

    TransliterationOptions dropping = TransliterationOptions.builder()
            .characterClass(CharacterClass.PAYROLL_NAME)
            .unmappable(UnmappableCharacterPolicy.DROP)
            .build();
    System.out.println("  ... or, if you would rather send something: "
            + KanaTransliterator.toHalfWidth("ヨーコ", dropping).text());
}

/// Kanji and hiragana, and why they are treated differently.
private static void whatIsRefused() {
    System.out.println();
    System.out.println("== what will not be guessed at ==");

    try {
        KanaTransliterator.toHalfWidth("山田太郎");
    } catch (UntransliterableCharacterException refused) {
        System.out.println("  山田太郎 -> refused");
        System.out.println("        東 is ヒガシ, トウ or アズマ depending on whose name it is.");
        System.out.println("        A wrong reading sends the money to the wrong place, so "
                + "there is no dictionary here.");
    }

    try {
        KanaTransliterator.toHalfWidth("やまだ");
    } catch (UntransliterableCharacterException refused) {
        System.out.println("  やまだ   -> refused by default (the conversion is unambiguous,");
        System.out.println("             but hiragana usually means the wrong field arrived)");
    }

    TransliterationOptions converting = TransliterationOptions.builder()
            .hiragana(io.zengin4j.core.kana.HiraganaPolicy.CONVERT).build();
    System.out.println("  やまだ   -> " + KanaTransliterator.toHalfWidth("やまだ", converting).text()
            + "  when asked explicitly");
}

/// Truncation, which is where the bytes bite.
///
/// ｶﾞ is two bytes with one glyph. A cut between them turns ガ into カ and
/// nothing in the file says so.
private static void fittingAField() {
    System.out.println();
    System.out.println("== fitting a field, without renaming anybody ==");

    String name = "ガクブチ";
    System.out.println("  " + name + " is " + KanaTransliterator.toHalfWidth(name).text()
            + " — " + ZenginCharset.MS932.encode(
                    KanaTransliterator.toHalfWidth(name).text()).length + " bytes");

    for (TruncationPolicy policy : TruncationPolicy.values()) {
        TransliterationOptions options =
                TransliterationOptions.builder().truncation(policy).build();
        try {
            Transliteration result = KanaTransliterator.toHalfWidth(name, 4, options);
            System.out.printf("    %-20s into 4 bytes -> %s%n", policy, result.text());
        } catch (RuntimeException refused) {
            System.out.printf("    %-20s into 4 bytes -> refused%n", policy);
        }
    }
    System.out.println("    the cut at byte 4 would land on ﾌ's voicing mark,");
    System.out.println("    so ﾌ goes too rather than ブ silently becoming フ");
}

/// And the same thing on the write path, where most callers will meet it (R-C18).
private static void throughTheEncoder() {
    System.out.println();
    System.out.println("== writing a record, with the policy doing the work ==");

    var data = FormatRegistry.defaults()
            .byId(FormatId.of("sougou-furikomi")).orElseThrow()
            .record(RecordKind.DATA);

    Map<String, String> values = new LinkedHashMap<>();
    values.put("beneficiaryName", "ガクブチ ジロウ");

    try {
        RecordEncoder.encode(data, ZenginCharset.MS932, values);
    } catch (IllegalArgumentException refused) {
        System.out.println("  default policy      -> refused, because the value is full width");
    }

    LossCollector loss = new LossCollector();
    byte[] frame = RecordEncoder.encode(data, ZenginCharset.MS932, values,
            EncodingOptions.builder().characters(CharacterWritePolicy.TRANSLITERATE).build(),
            loss);
    var field = data.field("beneficiaryName");
    System.out.println("  TRANSLITERATE       -> '"
            + ZenginCharset.MS932.decode(frame, field.offset(), field.length()).strip() + "'");
    for (LossEntry entry : loss.build().entries()) {
        System.out.println("        " + entry.toLine());
    }
}
