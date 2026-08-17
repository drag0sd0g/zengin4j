package io.zengin4j.core.kana;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.VoicingMarks;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.kana.generated.KanaTables;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Converts text into the half-width katakana these files carry (§16).
 *
 * <pre>{@code
 * Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ ジロウ",
 *         TransliterationOptions.defaults());
 * result.text();   // ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ
 * }</pre>
 *
 * <p><strong>Nothing here happens silently.</strong> Every conversion that
 * changes what a name says produces a {@link LossEntry}, and every conversion
 * that cannot be made at all raises rather than guesses. The reason is §16.2:
 * a voiced character is a base kana plus a separate mark, so an operation that
 * loses the mark turns ガクブチ into カクブチ and addresses the payment to
 * somebody else, with nothing in the file to show for it.
 *
 * <p><strong>The target field is an argument, not a detail.</strong> A long
 * vowel becomes a hyphen, and {@link CharacterClass#PAYROLL_NAME} admits no
 * symbols at all — so ヨーコ has a half-width spelling in a 総合振込 file and
 * none in a 給与振込 one. A transliterator that did not know which field it was
 * writing into would be wrong for one of them.
 *
 * <p>Stateless and thread-safe: every method takes what it needs.
 *
 * @since 0.4.0
 */
public final class KanaTransliterator {

    /** {@code ﾞ}, which modifies the kana before it (§16.1). */
    public static final char DAKUTEN = 0xFF9E;

    /** {@code ﾟ}, likewise. */
    public static final char HANDAKUTEN = 0xFF9F;

    private static final byte DAKUTEN_BYTE = (byte) 0xDE;
    private static final byte HANDAKUTEN_BYTE = (byte) 0xDF;

    private KanaTransliterator() {
    }

    // ------------------------------------------------------------ narrowing

    /**
     * Converts text to half-width, for the default field class.
     *
     * @param text the text to convert
     * @return the result and what it cost
     * @throws UntransliterableCharacterException if the text contains kanji, or
     *                                            hiragana or an unmappable
     *                                            character that the policies
     *                                            refuse
     */
    public static Transliteration toHalfWidth(String text) {
        return toHalfWidth(text, TransliterationOptions.defaults());
    }

    /**
     * Converts text to half-width.
     *
     * @param text    the text to convert
     * @param options the field class and the policies
     * @return the result and what it cost
     * @throws UntransliterableCharacterException if the text contains kanji, or
     *                                            hiragana or an unmappable
     *                                            character that the policies
     *                                            refuse
     */
    public static Transliteration toHalfWidth(String text, TransliterationOptions options) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(options, "options");

        LossCollector loss = new LossCollector();
        String narrowed = narrow(text, options, loss);
        String uppercased = uppercase(narrowed, loss);
        String voiced = enforceVoicingMarks(uppercased, text, options, loss);
        String permitted = enforceCharacterClass(voiced, text, options, loss);
        return new Transliteration(permitted, loss.build());
    }

    /**
     * Converts text to half-width and fits it to a field.
     *
     * <p>The two steps belong together: how long the text is can only be known
     * after it has been narrowed, and whether it fits is measured in bytes of
     * the target encoding rather than in characters.
     *
     * @param text     the text to convert
     * @param maxBytes the field width in bytes
     * @param options  the field class and the policies
     * @return the result and what it cost
     * @throws UntransliterableCharacterException if a character cannot be written
     * @throws FieldTooSmallException             if the field cannot hold even
     *                                            one character
     * @throws IllegalArgumentException           if the text overflows and the
     *                                            policy is to refuse
     */
    public static Transliteration toHalfWidth(String text, int maxBytes,
            TransliterationOptions options) {
        Objects.requireNonNull(options, "options");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive, found " + maxBytes);
        }

        Transliteration narrowed = toHalfWidth(text, options);
        LossCollector loss = new LossCollector();
        narrowed.loss().entries().forEach(loss::record);

        byte[] bytes = options.charset().encode(narrowed.text());
        if (bytes.length <= maxBytes) {
            return new Transliteration(narrowed.text(), loss.build());
        }

        return switch (options.truncation()) {
            case REJECT_IF_TOO_LONG -> throw new IllegalArgumentException(
                    "'" + narrowed.text() + "' is " + bytes.length + " bytes and does not fit a "
                            + maxBytes + "-byte field. Shorten it deliberately, or choose a"
                            + " TruncationPolicy — a payee's name is not a codec's to cut.");
            case TRUNCATE_SAFE -> truncated(narrowed.text(), maxBytes, "", options, loss);
            case TRUNCATE_WITH_MARKER -> truncated(narrowed.text(), maxBytes,
                    options.truncationMarker(), options, loss);
        };
    }

    private static Transliteration truncated(String text, int maxBytes,
            String marker, TransliterationOptions options, LossCollector loss) {

        byte[] markerBytes = options.charset().encode(marker);
        if (markerBytes.length >= maxBytes) {
            throw new FieldTooSmallException(text, maxBytes);
        }
        requireWritableMarker(marker, options);
        String shortened =
                fitToBudget(text, maxBytes - markerBytes.length, maxBytes, options) + marker;

        loss.record(LossEntry.of(LossKind.TRUNCATED, LossSeverity.MATERIAL, text, shortened,
                "'" + text + "' was shortened to '" + shortened + "' to fit " + maxBytes
                        + " bytes. The name now reads differently; a voicing mark was never"
                        + " separated from its kana.",
                "'" + text + "' を " + maxBytes + " バイトに収めるため '" + shortened
                        + "' に切り詰めました。濁点・半濁点が基底文字から分離されることはありません。"));
        return new Transliteration(shortened, loss.build());
    }

    /**
     * Keeps as much of the text as fits, measured in the output encoding.
     *
     * <p>Character by character rather than byte by byte, because
     * {@link #truncateSafe(byte[], int)} reads JIS X 0201 — one byte per
     * half-width character — and a caller writing UTF-8 has three. Handing it
     * UTF-8 bytes made it look for {@code 0xDE} where the mark's first byte is
     * {@code 0xEF}: it kept the base and dropped the mark, turning ﾌﾞ into ﾌ.
     * The silent rename the whole engine exists to prevent, reintroduced by
     * measuring in the wrong units.
     */
    private static String fitToBudget(String text, int budget, int fieldWidth,
            TransliterationOptions options) {
        if (!text.isEmpty() && isVoicingMark(text.charAt(0))) {
            throw new OrphanedVoicingMarkException(text);
        }

        int kept = 0;
        int used = 0;
        while (kept < text.length()) {
            int width = options.charset().encode(text.substring(kept, kept + 1)).length;
            if (used + width > budget) {
                break;
            }
            used += width;
            kept++;
        }

        // If the first character left behind is a voicing mark, the one before
        // it is the kana it modifies — and keeping that alone is the rename.
        if (kept < text.length() && kept > 0 && isVoicingMark(text.charAt(kept))) {
            kept--;
        }
        if (kept <= 0) {
            // The field width the caller asked for, not the budget left after
            // the marker: being told a 2-byte field cannot hold 1 byte is a
            // diagnostic about this method's arithmetic rather than about the
            // field (R-E3).
            throw new FieldTooSmallException(text, fieldWidth);
        }
        return text.substring(0, kept);
    }

    /**
     * Refuses a truncation marker the target field would not accept.
     *
     * <p>Easy to get wrong, and silent when it goes wrong: the obvious marker
     * {@code *} is permitted by <em>no</em> name class, so marked truncation
     * used to produce a shortened name that {@code V-202} then rejected — a
     * policy whose whole purpose is to make a change visible, making the file
     * invalid instead.
     *
     * <p>{@code PAYROLL_NAME} admits no symbol at all, so marked truncation is
     * impossible there whatever marker is chosen. Better said out loud than
     * worked around.
     */
    private static void requireWritableMarker(String marker, TransliterationOptions options) {
        byte[] jis = ZenginCharset.MS932.encode(marker);
        if (CharacterSet.isClean(jis, 0, jis.length, options.characterClass())) {
            return;
        }
        throw new IllegalArgumentException("the truncation marker '" + marker + "' is not permitted"
                + " in " + options.characterClass().nameEn() + ", so marking a shortened value"
                + " would produce a field this library rejects."
                + (options.characterClass() == CharacterClass.PAYROLL_NAME
                        ? " Payroll names admit no symbol at all, so TRUNCATE_WITH_MARKER cannot be"
                                + " used for them — use TRUNCATE_SAFE and read the loss report."
                        : " Choose a marker the field admits."));
    }

    // ----------------------------------------------------------- truncation

    /**
     * Shortens half-width bytes without severing a voicing mark from its kana
     * (R-K3, §16.3).
     *
     * <p>The hazard is one byte wide. {@code ｶﾞ} is {@code 0xB6 0xDE}, and a cut
     * between them leaves {@code ｶ} — a different character, in a file that
     * records no sign of the change. So a cut that would land on a mark takes
     * the base kana with it.
     *
     * <p>Operates on <strong>JIS X 0201 bytes</strong>, one per half-width
     * character. In UTF-8 a half-width kana is three bytes and the byte
     * arithmetic here would be wrong, which is why the string-level methods
     * measure with the configured charset and this one does not guess.
     *
     * @param bytes    the half-width bytes
     * @param maxBytes the field width
     * @return the kept bytes; the input array itself when it already fits
     * @throws OrphanedVoicingMarkException if the input begins with a voicing
     *                                      mark, which means it arrived already
     *                                      cut
     * @throws FieldTooSmallException       if nothing can be kept
     */
    public static byte[] truncateSafe(byte[] bytes, int maxBytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive, found " + maxBytes);
        }

        // Checked before the length test, not after. §16.3's reference
        // implementation checks it only on the truncating path, so a short
        // input beginning with a stray mark passes and a long one does not —
        // and the input is equally damaged either way.
        if (bytes.length > 0 && isVoicingMark(bytes[0])) {
            throw new OrphanedVoicingMarkException(describe(bytes));
        }
        if (bytes.length <= maxBytes) {
            return bytes;
        }

        int cut = maxBytes;
        if (isVoicingMark(bytes[cut])) {
            // The byte at the cut is a mark, so the kana it modifies is the
            // last one we were going to keep. Keeping the base without its mark
            // is the silent rename; drop them together.
            cut--;
        }
        if (cut <= 0) {
            throw new FieldTooSmallException(describe(bytes), maxBytes);
        }
        return Arrays.copyOf(bytes, cut);
    }

    /**
     * Whether a byte is a voicing mark.
     *
     * @param b the byte
     * @return {@code true} for {@code 0xDE} or {@code 0xDF}
     */
    public static boolean isVoicingMark(byte b) {
        return b == DAKUTEN_BYTE || b == HANDAKUTEN_BYTE;
    }

    /**
     * Whether a character is a half-width voicing mark.
     *
     * @param c the character
     * @return {@code true} for {@code ﾞ} or {@code ﾟ}
     */
    public static boolean isVoicingMark(char c) {
        return c == DAKUTEN || c == HANDAKUTEN;
    }

    // -------------------------------------------------------------- widening

    /**
     * Converts half-width text back to full width, for display (R-K8).
     *
     * <p>Marked informational because it is not reliably reversible: several
     * full-width characters narrow to the same half-width sequence, so widening
     * picks one. Useful when presenting a file's contents to a person; not a
     * round trip.
     *
     * @param text the half-width text
     * @return the widened text and an informational loss entry
     */
    public static Transliteration toFullWidth(String text) {
        Objects.requireNonNull(text, "text");
        LossCollector loss = new LossCollector();
        StringBuilder out = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); ) {
            // Greedy: a base followed by a mark is one character, and matching
            // the base alone would drop the voicing.
            if (i + 1 < text.length() && isVoicingMark(text.charAt(i + 1))) {
                String pair = text.substring(i, i + 2);
                String widened = KanaTables.widen(pair);
                if (widened != null) {
                    out.append(widened);
                    i += 2;
                    continue;
                }
            }
            String one = text.substring(i, i + 1);
            String widened = KanaTables.widen(one);
            out.append(widened != null ? widened : one);
            i++;
        }

        String result = out.toString();
        if (!result.equals(text)) {
            loss.record(LossEntry.of(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL,
                    text, result,
                    "'" + text + "' was widened to '" + result + "' for display. Widening is not"
                            + " reversible: several full-width characters narrow to the same"
                            + " half-width sequence, so this is one reading of the bytes rather"
                            + " than the only one.",
                    "'" + text + "' を表示用に '" + result + "' へ全角化しました。"
                            + "全角化は可逆ではありません。複数の全角文字が同一の半角列になるため、"
                            + "これは解釈のひとつです。"));
        }
        return new Transliteration(result, loss.build());
    }

    // ---------------------------------------------------------------- passes

    /** Substitution, hiragana and narrowing, in one walk. */
    private static String narrow(String text, TransliterationOptions options, LossCollector loss) {
        StringBuilder out = new StringBuilder(text.length());
        List<String> kanji = new ArrayList<>();
        List<String> hiragana = new ArrayList<>();
        List<String> converted = new ArrayList<>();
        boolean narrowedAnything = false;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            String character = new String(Character.toChars(codePoint));

            KanaSubstitution substitution = KanaTables.substitution(character);
            if (substitution != null) {
                loss.record(LossEntry.of(LossKind.TRANSLITERATED, substitution.severity(),
                        character, substitution.replacement(),
                        "'" + character + "' was written as '" + substitution.replacement()
                                + "': " + substitution.whyEn() + ".",
                        "'" + character + "' を '" + substitution.replacement()
                                + "' と表記しました。" + substitution.whyJa() + "。"));
                narrowedAnything |= appendNarrowed(out, substitution.replacement());
                continue;
            }

            if (KanaTables.isHiragana(codePoint)) {
                if (options.hiragana() == HiraganaPolicy.REJECT) {
                    hiragana.add(character);
                    continue;
                }
                String katakana = new String(Character.toChars(KanaTables.katakanaFor(codePoint)));
                converted.add(character);
                narrowedAnything |= appendNarrowed(out, katakana);
                continue;
            }

            if (isKanji(codePoint)) {
                kanji.add(character);
                continue;
            }

            narrowedAnything |= appendNarrowed(out, character);
        }

        // Kanji first: it is the refusal the caller can do least about, and
        // reporting it alongside a hiragana complaint would bury it.
        if (!kanji.isEmpty()) {
            throw UntransliterableCharacterException.kanji(kanji, text);
        }
        if (!hiragana.isEmpty()) {
            throw UntransliterableCharacterException.hiragana(hiragana, text);
        }

        // One entry for the whole conversion rather than one per character. A
        // thirty-character name would otherwise produce thirty entries saying
        // the same thing, and the report a person reads before sending a file
        // has to stay readable to be read.
        // Likewise one entry for the narrowing, not one per kana. Narrowing is
        // the operation the caller asked for, and タ to ﾀ loses nothing that can
        // be recovered from the half-width form; it is recorded because P5 says
        // to record it, not because anyone needs it itemised.
        if (narrowedAnything) {
            loss.record(LossEntry.of(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL,
                    text, out.toString(),
                    "'" + text + "' was narrowed to '" + out
                            + "', which is what a fixed-length field carries.",
                    "'" + text + "' を半角 '" + out + "' に変換しました。"
                            + "固定長ファイルは半角で記録します。"));
        }
        if (!converted.isEmpty()) {
            loss.record(LossEntry.of(LossKind.TRANSLITERATED, LossSeverity.MATERIAL,
                    text, out.toString(),
                    "hiragana (" + String.join(", ", converted.stream().distinct().toList())
                            + ") was converted to katakana. Each conversion is unambiguous, but"
                            + " the name reads differently on the page.",
                    "ひらがな (" + String.join("、", converted.stream().distinct().toList())
                            + ") をカタカナに変換しました。変換自体は一意ですが、表記は変わります。"));
        }
        return out.toString();
    }

    /**
     * Narrows one character, which a substitution may have left full width.
     *
     * @return {@code true} if anything was narrowed
     */
    private static boolean appendNarrowed(StringBuilder out, String text) {
        boolean narrowedAnything = false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            String character = new String(Character.toChars(codePoint));

            String narrowed = KanaTables.narrow(character);
            if (narrowed == null) {
                out.append(character);
                continue;
            }
            out.append(narrowed);
            narrowedAnything = true;
        }
        return narrowedAnything;
    }

    /**
     * Upper-cases ASCII letters.
     *
     * <p>Not a stylistic choice: no field class permits a lowercase letter, so
     * the alternative to folding is refusing. Informational, because case
     * carries no meaning in a field that can only hold one of them.
     */
    private static String uppercase(String text, LossCollector loss) {
        StringBuilder out = new StringBuilder(text.length());
        boolean changed = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') {
                out.append((char) (c - 'a' + 'A'));
                changed = true;
            } else {
                out.append(c);
            }
        }
        String result = out.toString();
        if (changed) {
            loss.record(LossEntry.of(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL,
                    text, result,
                    "'" + text + "' was upper-cased to '" + result
                            + "'. No field class permits a lowercase letter.",
                    "'" + text + "' を大文字化して '" + result + "' としました。"
                            + "小文字を使用できる項目種別はありません。"));
        }
        return result;
    }

    /**
     * Applies the field's character class to the narrowed text.
     *
     * <p>The last pass, and the one that catches the case the others cannot:
     * every character is now a legal half-width character, and some of them are
     * still not permitted <em>here</em>. A hyphen is fine in a party name and
     * refused in a payroll name, so the same input succeeds for one field and
     * not the other.
     */
    private static String enforceCharacterClass(String text, String original,
            TransliterationOptions options, LossCollector loss) {

        CharacterClass characterClass = options.characterClass();
        StringBuilder kept = new StringBuilder(text.length());
        List<String> refused = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            String character = text.substring(i, i + 1);
            // Checked as JIS bytes because that is what a character class is
            // defined over, whatever encoding the output will be written in.
            byte[] encoded = ZenginCharset.MS932.encode(character);
            if (CharacterSet.isClean(encoded, 0, encoded.length, characterClass)) {
                kept.append(character);
                continue;
            }
            if (options.unmappable() == UnmappableCharacterPolicy.REJECT) {
                refused.add(character);
                continue;
            }
            loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL, character, "",
                    "'" + character + "' was dropped: " + characterClass.nameEn()
                            + " does not permit it, and there is no other form to write it in.",
                    "'" + character + "' を削除しました。" + characterClass.nameJa()
                            + "では使用できず、代替表記もありません。"));
        }

        if (!refused.isEmpty()) {
            throw UntransliterableCharacterException.unmappable(refused, original, characterClass);
        }
        return kept.toString();
    }

    /**
     * Refuses to emit a voicing mark after a kana that cannot take one (R-K7).
     *
     * <p>Not a hypothetical. {@code ヷ} — katakana VA — narrows mechanically to
     * {@code ﾜﾞ}, and {@code ﾜ} has no voiced form, so the mark is stranded.
     * Unicode is happy to decompose it; the standard has nowhere to put it, and
     * validation rule {@code V-206} would report the result. Producing a file
     * this library then rejects is the one outcome worth engineering against,
     * so the pair is refused here rather than written and complained about
     * later.
     *
     * <p>Catches half-width input the caller supplied directly, too: a stray
     * mark arriving in already-narrow text is the same defect.
     */
    private static String enforceVoicingMarks(String text, String original,
            TransliterationOptions options, LossCollector loss) {

        StringBuilder kept = new StringBuilder(text.length());
        List<String> stranded = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isVoicingMark(c)) {
                kept.append(c);
                continue;
            }

            int base = kept.isEmpty() ? -1
                    : ZenginCharset.MS932.encode(String.valueOf(kept.charAt(kept.length() - 1)))[0]
                            & 0xFF;
            int mark = ZenginCharset.MS932.encode(String.valueOf(c))[0] & 0xFF;
            if (VoicingMarks.isLegal(base, mark)) {
                kept.append(c);
                continue;
            }

            String pair = kept.isEmpty() ? String.valueOf(c)
                    : kept.charAt(kept.length() - 1) + String.valueOf(c);
            if (options.unmappable() == UnmappableCharacterPolicy.REJECT) {
                stranded.add(pair);
                continue;
            }
            loss.record(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL, pair,
                    kept.isEmpty() ? "" : String.valueOf(kept.charAt(kept.length() - 1)),
                    "the voicing mark in '" + pair + "' was dropped: no kana there can carry one,"
                            + " so the sequence is not a character the standard recognises.",
                    "'" + pair + "' の濁点・半濁点を削除しました。"
                            + "その基底文字は濁点・半濁点を取れないため、標準では文字として成立しません。"));
        }

        if (!stranded.isEmpty()) {
            throw UntransliterableCharacterException.strandedVoicingMark(stranded, original);
        }
        return kept.toString();
    }

    /** CJK ideographs, whose readings are ambiguous (R-K6, P4). */
    private static boolean isKanji(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    /** Bytes rendered for a message, without assuming they decode cleanly. */
    private static String describe(byte[] bytes) {
        return ZenginCharset.MS932.decode(bytes, 0, bytes.length);
    }
}
