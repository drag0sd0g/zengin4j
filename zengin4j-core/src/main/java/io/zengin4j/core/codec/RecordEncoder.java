package io.zengin4j.core.codec;

import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.CharacterViolation;
import io.zengin4j.core.charset.VoicingMarks;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.kana.KanaTransliterator;
import io.zengin4j.core.kana.Transliteration;
import io.zengin4j.core.kana.TransliterationOptions;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a record's bytes from field values.
 *
 * <p>Fields left unset take their declared constant, or the pad byte their
 * type prescribes: zeros for {@code N}, spaces for {@code C}. An unknown field
 * id is rejected rather than ignored — silently dropping a misspelled field
 * would produce a record that is quietly missing a value, which is the class
 * of defect this library exists to prevent.
 *
 * <p>Encoding is deterministic (R-C19): the same values produce the same bytes
 * on every run, on every platform.
 *
 * @since 0.1.0
 */
public final class RecordEncoder {

    private RecordEncoder() {
    }

    /**
     * Encodes one record.
     *
     * @param descriptor the record layout
     * @param charset    the encoding to write text fields in
     * @param values     field values keyed by field id
     * @return the record bytes, exactly {@code descriptor.recordLength()} long
     * @throws IllegalArgumentException if a key names no field of this record,
     *                                  or a value does not fit its field
     */
    public static byte[] encode(
            RecordDescriptor descriptor, ZenginCharset charset, Map<String, String> values) {
        return encode(descriptor, charset, values, EncodingOptions.defaults(), new LossCollector());
    }

    /**
     * Encodes a record, applying a write policy to characters the fields cannot
     * hold (R-C18).
     *
     * <p>The policy is applied per field, against that field's own character
     * class — which is the only way it can be right, since a hyphen is fine in a
     * party name and refused in a payroll one.
     *
     * @param descriptor the record layout
     * @param charset    the encoding to write text fields in
     * @param values     field values keyed by field id
     * @param options    what to do with characters a field cannot hold
     * @param loss       collects anything the policy changed
     * @return the record bytes, exactly {@code descriptor.recordLength()} long
     * @throws IllegalArgumentException if a key names no field of this record,
     *                                  or a value does not fit its field
     * @since 0.4.0
     */
    public static byte[] encode(
            RecordDescriptor descriptor, ZenginCharset charset, Map<String, String> values,
            EncodingOptions options, LossCollector loss) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(loss, "loss");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(values, "values");

        for (String id : values.keySet()) {
            if (descriptor.find(id).isEmpty()) {
                throw new IllegalArgumentException("the " + descriptor.kind() + " record of format "
                        + descriptor.formatId() + " has no field '" + id + "'; declared fields: "
                        + String.join(", ", descriptor.fields().stream().map(FieldDescriptor::id).toList()));
            }
        }

        byte[] frame = new byte[descriptor.recordLength()];
        for (FieldDescriptor field : descriptor.fields()) {
            String value = values.get(field.id());
            if (value == null) {
                value = field.constant().orElse(null);
            }
            if (value == null) {
                FieldCodec.fill(frame, field.offset(), field.length(), field.type().padByte());
            } else {
                String written = applyPolicy(value, field, charset, options, loss);
                FieldCodec.encodeText(written, frame, field.offset(), field.length(), charset,
                        PadPolicy.of(field.type()));
            }
        }
        return frame;
    }

    /**
     * Applies the write policy to one field's value.
     *
     * <p>Numeric fields are left alone: their content is digits, and a digit is
     * permitted everywhere a numeric field exists. Running text through the
     * transliterator would be work with nothing to do.
     */
    private static String applyPolicy(String value, FieldDescriptor field, ZenginCharset charset,
            EncodingOptions options, LossCollector loss) {

        if (field.type() != FieldType.C || !options.checkCharacters()) {
            return value;
        }
        // Checked as JIS X 0201 bytes, not as bytes of the output encoding. A
        // character class is defined over JIS byte values, so testing a UTF-8
        // encoding against one asks whether 0xEF is a permitted kana — a
        // question with no meaning and a misleading answer. Length is measured
        // in the output encoding; permission is not.
        byte[] encoded = ZenginCharset.MS932.encode(value);
        boolean clean = CharacterSet.isClean(encoded, 0, encoded.length, field.charClass())
                && voicingMarksAreLegal(encoded);
        if (clean) {
            return value;
        }

        return switch (options.characters()) {
            case TRANSLITERATE -> transliterate(value, field, charset, options, loss);
            case REPLACE -> replace(value, field, options, loss);
            case REJECT -> throw refusal(value, field, encoded);
        };
    }

    /**
     * Refuses a replacement byte the field would not accept.
     *
     * <p>The same trap as the truncation marker, and just as quiet. {@code '?'}
     * is the obvious choice and is permitted by no name class; {@code 0xDE} is
     * a voicing mark and would strand itself after whatever kana it landed
     * behind. Either way {@code REPLACE} — a policy for salvaging a value —
     * would produce a field this library rejects.
     */
    private static void requireWritableReplacement(FieldDescriptor field, EncodingOptions options) {
        byte replacement = options.replacement();
        int unsigned = replacement & 0xFF;

        if (VoicingMarks.isMark(unsigned)) {
            throw new IllegalArgumentException(String.format(
                    "the replacement byte 0x%02X is a voicing mark, which modifies the character"
                            + " before it — substituting one would strand it after whatever kana"
                            + " it landed behind. Choose a replacement that stands alone.",
                    unsigned));
        }
        byte[] one = {replacement};
        if (!CharacterSet.isClean(one, 0, 1, field.charClass())) {
            throw new IllegalArgumentException(String.format(
                    "the replacement byte 0x%02X is not permitted in %s, so replacing with it"
                            + " would produce a field this library rejects. A space (0x20) is"
                            + " permitted by every name class and is the default.",
                    unsigned, field.charClass().nameEn()));
        }
    }

    /**
     * Whether every voicing mark has a kana in front of it that can take one.
     *
     * <p>Checked alongside the character class because the class alone cannot
     * see it: {@code ｱ} is permitted and {@code ﾞ} is permitted, so {@code ｱﾞ}
     * passes character by character while being a sequence the standard does
     * not recognise — the one validation rule {@code V-206} exists to report.
     * Writing it would mean producing a file this library rejects.
     */
    private static boolean voicingMarksAreLegal(byte[] encoded) {
        for (int i = 0; i < encoded.length; i++) {
            int mark = encoded[i] & 0xFF;
            if (!VoicingMarks.isMark(mark)) {
                continue;
            }
            if (!VoicingMarks.isLegal(i == 0 ? -1 : encoded[i - 1] & 0xFF, mark)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Refuses a value the field cannot hold, naming what is wrong with it.
     *
     * <p>Until this existed, {@code REJECT} checked only the <em>length</em> of
     * a value, so a full-width name that happened to fit the byte budget was
     * written into a field that permits only half-width — producing exactly the
     * file this library's own {@code V-202} rule rejects. The default policy has
     * to be the one that cannot do that.
     */
    private static IllegalArgumentException refusal(String value, FieldDescriptor field,
            byte[] encoded) {

        List<CharacterViolation> violations =
                CharacterSet.validate(encoded, 0, encoded.length, field.charClass());
        String problem;
        if (violations.isEmpty()) {
            problem = "a voicing mark follows a kana that cannot take one, which is not a"
                    + " character the standard recognises (R-K7)";
        } else {
            problem = violations.get(0).describeEn()
                    + (violations.size() > 1 ? " (and " + (violations.size() - 1) + " more)" : "");
        }

        return new IllegalArgumentException("'" + value + "' cannot be written to field '"
                + field.id() + "': " + problem
                + ". Set CharacterWritePolicy.TRANSLITERATE to convert it, or REPLACE to substitute"
                + " the offending bytes — both record what they changed.");
    }

    private static String transliterate(String value, FieldDescriptor field, ZenginCharset charset,
            EncodingOptions options, LossCollector loss) {

        // The charset is passed through, not defaulted. Field widths are in
        // bytes of the encoding the record is written in, and a transliterator
        // measuring MS932 while the caller writes UTF-8 would call a 45-byte
        // value a 15-byte one and let it overflow the field.
        TransliterationOptions transliteration = TransliterationOptions.builder()
                .characterClass(field.charClass())
                .charset(charset)
                .truncation(options.truncation())
                .hiragana(options.hiragana())
                .unmappable(options.unmappable())
                .build();

        Transliteration result = KanaTransliterator.toHalfWidth(value, field.length(),
                transliteration);
        result.loss().entries().forEach(entry ->
                loss.record(entry.at(field.id(), field.id())));
        return result.text();
    }

    /**
     * Replaces every character the field refuses, one byte each.
     *
     * <p>Byte for byte rather than character for character, because a voiced
     * kana is two bytes and replacing it with one would shift everything after
     * it — turning a field-width problem into a silently different name.
     */
    private static String replace(String value, FieldDescriptor field,
            EncodingOptions options, LossCollector loss) {

        requireWritableReplacement(field, options);

        byte[] encoded = ZenginCharset.MS932.encode(value);
        byte[] result = encoded.clone();
        int replaced = 0;
        for (CharacterViolation violation
                : CharacterSet.validate(encoded, 0, encoded.length, field.charClass())) {
            result[violation.offset()] = options.replacement();
            replaced++;
        }
        String written = ZenginCharset.MS932.decode(result, 0, result.length);

        loss.record(LossEntry.of(LossKind.COERCED, LossSeverity.MATERIAL, value, written,
                        replaced + " character(s) of '" + value + "' are not permitted in "
                                + field.charClass().nameEn() + " and were replaced, giving '"
                                + written + "'.",
                        "'" + value + "' のうち " + replaced + " 文字は"
                                + field.charClass().nameJa() + "で使用できないため置換し、'"
                                + written + "' としました。")
                .at(field.id(), field.id()));
        return written;
    }
}
