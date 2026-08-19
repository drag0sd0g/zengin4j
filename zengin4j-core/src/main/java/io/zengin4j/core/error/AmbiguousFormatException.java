package io.zengin4j.core.error;

import module java.base;

/// More than one registered format declares the 種別コード found in the header,
/// so the format cannot be determined from the file alone.
///
/// This is a real property of the formats, not a registry defect: 預金口座振替
/// and 口座振替結果 both use 種別コード `91` and differ only in whether the
/// result code is populated (§13.1). Guessing between an instruction file and a
/// result file would be a guess about payment direction, so the reader refuses
/// and asks the caller to name the format.
///
/// @since 0.1.0
public final class AmbiguousFormatException extends ZenginException {

    private final String typeCode;
    private final List<String> candidates;

    /// Creates a diagnostic naming the ambiguity.
    ///
    /// @param typeCode   the 種別コード read from the header
    /// @param candidates ids of the formats that declare it
    public AmbiguousFormatException(String typeCode, List<String> candidates) {
        super("種別コード '" + typeCode + "' is declared by more than one registered format ("
                        + String.join(", ", candidates) + "), so it cannot identify the layout on its own."
                        + " Name the format explicitly via ReaderOptions.builder().format(...).",
                "種別コード '" + typeCode + "' は複数のフォーマット (" + String.join(", ", candidates)
                        + ") が宣言しているため、レイアウトを一意に決定できません。"
                        + "ReaderOptions.builder().format(...) でフォーマットを明示してください。");
        this.typeCode = typeCode;
        this.candidates = List.copyOf(candidates);
    }

    /// Returns the ambiguous 種別コード.
    ///
    /// @return the two-character business type code
    public String typeCode() {
        return typeCode;
    }

    /// Returns the ids of the formats that declare the code.
    ///
    /// @return an unmodifiable list of format ids
    public List<String> candidates() {
        return candidates;
    }
}
