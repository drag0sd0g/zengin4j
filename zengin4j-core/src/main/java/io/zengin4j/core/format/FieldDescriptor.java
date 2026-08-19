package io.zengin4j.core.format;

import module java.base;
import io.zengin4j.core.charset.CharacterClass;

/// One fixed-width field within a record.
///
/// **`offset` is computed, never transcribed.** The
/// loader assigns it from the cumulative length of the preceding fields
/// (R-F2). Hand-written offsets are the largest single source of defects in
/// fixed-length parsers, and they are unnecessary: the lengths already
/// determine them.
///
/// @param sequence  1-based position within the record, as printed in the
///   source layout tables
/// @param id        the identifier used in code and in the API, for example
///   `beneficiaryName`
/// @param nameJa    the Japanese field name, for example `受取人名`
/// @param nameEn    the English gloss
/// @param type      `N` or `C`
/// @param offset    computed byte offset from the start of the record
/// @param length    field length in **bytes** (R-C15)
/// @param required  whether a value must be present; informational in 0.1.0,
///   consumed by the validation rules in Epic 4
/// @param filler    whether the field is reserved space with no meaning
/// @param sensitive whether the value must be masked in diagnostics (R-E6)
/// @param format    an optional declared interpretation
/// @param constant  an optional fixed value the field always carries
/// @param codeList  an optional code list constraining the value
/// @param note      an optional remark, typically a `[VERIFY]` caveat
/// @param charClass the character set this field's bytes must satisfy (R-C16)
/// @param codes     the subset of the code list this field admits; empty means all
/// @since 0.1.0
public record FieldDescriptor(
        int sequence,
        String id,
        String nameJa,
        String nameEn,
        FieldType type,
        int offset,
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

    /// Validates the components.
    ///
    /// @throws IllegalArgumentException if the descriptor is internally
    ///   inconsistent
    public FieldDescriptor {
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
        if (id.isBlank()) {
            throw new IllegalArgumentException("field id must not be blank");
        }
        if (length < 1) {
            throw new IllegalArgumentException("field '" + id + "' must be at least one byte, found " + length);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("field '" + id + "' has a negative offset");
        }
        if (constant.isPresent() && constant.get().length() != length) {
            throw new IllegalArgumentException("field '" + id + "' declares constant '" + constant.get()
                    + "' of " + constant.get().length() + " characters, but the field is " + length + " bytes");
        }
        if (format.isPresent()) {
            FieldFormat declared = format.get();
            if (type != FieldType.N) {
                throw new IllegalArgumentException("field '" + id + "' declares format "
                        + declared.descriptorValue() + ", which applies only to N fields");
            }
            Optional<Integer> fixedLength = declared.requiredLength();
            if (fixedLength.isPresent() && fixedLength.get() != length) {
                throw new IllegalArgumentException("field '" + id + "' declares format "
                        + declared.descriptorValue() + ", which requires length " + fixedLength.get()
                        + ", but the field is " + length + " bytes");
            }
        }
    }

    /// Returns this field as a specification, without its offset.
    ///
    /// The offset is dropped rather than carried, so a spec fed back through
    /// [RecordDescriptor#of] has its offset recomputed from the cumulative
    /// lengths like any other. R-F2 holds for a copied layout exactly as it does
    /// for a declared one.
    ///
    /// @return the specification, never `null`
    /// @since 0.1.0
    public FieldSpec toSpec() {
        return new FieldSpec(sequence, id, nameJa, nameEn, type, length,
                required, filler, sensitive, format, constant, codeList, note, charClass, codes);
    }

    /// Returns the offset one past the end of this field.
    ///
    /// @return `offset + length`
    /// @since 0.1.0
    public int endOffset() {
        return offset + length;
    }

    /// Reports whether this field declares a particular interpretation.
    ///
    /// @param candidate the interpretation to test for
    /// @return `true` if this field declares `candidate`
    public boolean hasFormat(FieldFormat candidate) {
        return format.isPresent() && format.get() == candidate;
    }
}
