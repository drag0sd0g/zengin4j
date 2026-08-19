package io.zengin4j.core.format;

import module java.base;

/// A named set of permitted values for a field.
///
/// Code lists are **open by default**. Where the published
/// values are unconfirmed — which, in 0.1.0, is all of them except データ区分
/// and 種別コード — modelling the field as a closed enum would mean inventing
/// the constants that are missing (§0.6). An open list carries the values that
/// are known, admits the ones that are not, and leaves the raw content
/// available on the record.
///
/// A list carries its own citations and its own verification state, held to
/// the same bar as a format descriptor: `verified: true` requires at
/// least two independent published sources (R-0.1). A code list is as capable
/// of being wrong as a byte offset, and an invented enum constant is exactly
/// what §0.2 forbids.
///
/// @param id       the list identifier referenced from field descriptors
/// @param nameJa   the Japanese name
/// @param nameEn   the English gloss
/// @param verified whether the list as a whole has been confirmed against two
///   independent published sources (R-0.1)
/// @param open     whether values outside `values` are permitted
/// @param values   the known values, in document order
/// @param sources  citations supporting the list
/// @param note     an optional remark
/// @since 0.1.0
public record CodeList(
        String id,
        String nameJa,
        String nameEn,
        boolean verified,
        boolean open,
        List<CodeValue> values,
        List<String> sources,
        Optional<String> note) {

    /// Validates and defensively copies the components.
    ///
    /// @throws IllegalArgumentException if the list claims verification without
    ///   enough cited sources
    public CodeList {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameJa, "nameJa");
        Objects.requireNonNull(nameEn, "nameEn");
        Objects.requireNonNull(note, "note");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (verified && sources.size() < FormatDescriptor.REQUIRED_SOURCES_FOR_VERIFICATION) {
            throw new IllegalArgumentException("code list '" + id + "' claims verified: true with "
                    + sources.size() + " cited source(s); at least "
                    + FormatDescriptor.REQUIRED_SOURCES_FOR_VERIFICATION + " are required (R-0.1)");
        }
    }

    /// Looks up a value.
    ///
    /// @param code the raw field content
    /// @return the matching entry, or empty if the value is not in the list
    public Optional<CodeValue> byCode(String code) {
        for (CodeValue value : values) {
            if (value.code().equals(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /// Reports whether a value is acceptable for this list.
    ///
    /// @param code the raw field content
    /// @return `true` if the value is known, or if the list is open
    public boolean accepts(String code) {
        return open || byCode(code).isPresent();
    }
}
