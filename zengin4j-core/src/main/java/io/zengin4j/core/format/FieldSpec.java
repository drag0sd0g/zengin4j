package io.zengin4j.core.format;

import io.zengin4j.core.charset.CharacterClass;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A field described without its byte offset.
 *
 * <p>This type exists so that <strong>nothing outside this class ever writes a
 * byte offset</strong> (R-F2). A caller states each field's length in layout
 * order and {@link RecordDescriptor#of} assigns the offsets from the
 * cumulative total — the same guarantee that used to come from loading a
 * descriptor file, now available to generated code and to consumers building
 * an institution-specific variant by hand (R-F6, R-X1).
 *
 * <p>Hand-written offsets are the largest single source of defects in
 * fixed-length parsers, and an API that accepts them is an API that invites
 * them.
 *
 * @param sequence  1-based position within the record
 * @param id        the identifier used in code and in the API
 * @param nameJa    the Japanese field name
 * @param nameEn    the English gloss
 * @param type      {@code N} or {@code C}
 * @param length    field length in <strong>bytes</strong> (R-C15)
 * @param required  whether a value must be present
 * @param filler    whether the field is reserved space with no meaning
 * @param sensitive whether the value must be masked in diagnostics (R-E6)
 * @param format    an optional declared interpretation
 * @param constant  an optional fixed value the field always carries
 * @param codeList  an optional code list constraining the value
 * @param note      an optional remark
 * @param charClass the character set the field's bytes must satisfy (R-C16)
 * @param codes     the subset of the code list this field admits; empty means all
 * @since 0.1.0
 */
public record FieldSpec(
        int sequence,
        String id,
        String nameJa,
        String nameEn,
        FieldType type,
        int length,
        boolean required,
        boolean filler,
        boolean sensitive,
        Optional<FieldFormat> format,
        Optional<String> constant,
        Optional<CodeList> codeList,
        Optional<String> note,
        CharacterClass charClass,
        List<String> codes) {

    /**
     * Validates the components.
     */
    public FieldSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameJa, "nameJa");
        Objects.requireNonNull(nameEn, "nameEn");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(constant, "constant");
        Objects.requireNonNull(codeList, "codeList");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(charClass, "charClass");
        codes = List.copyOf(codes);
    }

    /**
     * Describes a field with no optional attributes set.
     *
     * @param sequence 1-based position within the record
     * @param id       the identifier used in code and in the API
     * @param nameJa   the Japanese field name
     * @param nameEn   the English gloss
     * @param type     {@code N} or {@code C}
     * @param length   field length in bytes
     * @return the specification
     */
    public static FieldSpec of(
            int sequence, String id, String nameJa, String nameEn, FieldType type, int length) {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                false, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                defaultCharacterClass(type), List.of());
    }

    /**
     * Returns a copy marked as required.
     *
     * @return the derived specification
     */
    public FieldSpec withRequired() {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                true, filler, sensitive, format, constant, codeList, note, charClass, codes);
    }

    /**
     * Returns a copy marked as reserved space with no meaning.
     *
     * @return the derived specification
     */
    public FieldSpec withFiller() {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, true, sensitive, format, constant, codeList, note, charClass, codes);
    }

    /**
     * Returns a copy whose value is masked in diagnostics (R-E6).
     *
     * @return the derived specification
     */
    public FieldSpec withSensitive() {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, true, format, constant, codeList, note, charClass, codes);
    }

    /**
     * Returns a copy declaring an interpretation.
     *
     * @param value the interpretation
     * @return the derived specification
     */
    public FieldSpec withFormat(FieldFormat value) {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, sensitive, Optional.of(value), constant, codeList, note, charClass, codes);
    }

    /**
     * Returns a copy carrying a fixed value.
     *
     * @param value the constant
     * @return the derived specification
     */
    public FieldSpec withConstant(String value) {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, sensitive, format, Optional.of(value), codeList, note, charClass, codes);
    }

    /**
     * Returns a copy constrained by a code list.
     *
     * @param value the code list
     * @return the derived specification
     */
    public FieldSpec withCodeList(CodeList value) {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, sensitive, format, constant, Optional.of(value), note, charClass, codes);
    }

    /**
     * Returns a copy carrying a remark.
     *
     * @param value the note
     * @return the derived specification
     */
    public FieldSpec withNote(String value) {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, sensitive, format, constant, codeList, Optional.of(value), charClass, codes);
    }

    /**
     * Returns a copy whose bytes must satisfy a character class (R-C16).
     *
     * <p>Declared per field rather than per format, because the standard
     * narrows the permitted set by what the field <em>is</em>: a branch name
     * admits one symbol, a party name four.
     *
     * @param value the character class
     * @return the derived specification
     */
    public FieldSpec withCharacterClass(CharacterClass value) {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, sensitive, format, constant, codeList, note,
                Objects.requireNonNull(value, "characterClass"), codes);
    }

    /**
     * Returns a copy admitting only part of its code list (OQ-9).
     *
     * <p>付録3 states the master list once and then says that not every code is
     * valid for every business, and that where a format enumerates a subset the
     * subset governs. So a field narrows the shared list rather than the list
     * being split per format — which keeps one place to look up what a code
     * means, and one place to record where it may appear.
     *
     * @param values the permitted codes; empty means the whole list
     * @return the derived specification
     */
    public FieldSpec withCodes(List<String> values) {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, sensitive, format, constant, codeList, note, charClass,
                List.copyOf(Objects.requireNonNull(values, "codes")));
    }

    /**
     * The class a field takes when its descriptor does not declare one.
     *
     * <p>{@code N} means digits, which is a character rule and not merely a
     * hint. {@code C} defaults to unrestricted rather than to a name class:
     * guessing that an undeclared text field is a party name would invent a
     * constraint the sources do not state for it, and a validator that invents
     * constraints is worse than one that omits them.
     *
     * @param type the field type
     * @return the default class for that type
     */
    public static CharacterClass defaultCharacterClass(FieldType type) {
        return type == FieldType.N ? CharacterClass.NUMERIC : CharacterClass.UNRESTRICTED;
    }

    /**
     * Places this field at a byte offset.
     *
     * @param offset the computed offset
     * @return the field descriptor
     */
    FieldDescriptor at(int offset) {
        return new FieldDescriptor(sequence, id, nameJa, nameEn, type, offset, length,
                required, filler, sensitive, format, constant, codeList, note, charClass, codes);
    }
}
