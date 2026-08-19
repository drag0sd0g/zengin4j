package io.zengin4j.core.error;

import module java.base;
import io.zengin4j.core.charset.CharacterViolation;

/// A record carries bytes its fields' character classes do not permit, and the
/// reader was told to refuse them (R-C13).
///
/// Raised only under `CharacterPolicy.REJECT`. Every violation in the
/// offending record is reported, not just the first: a name typed with the wrong
/// long-vowel key usually has more than one, and fixing them one build at a time
/// is a poor use of anyone's afternoon.
///
/// @since 0.1.0
public final class CharacterSetViolationException extends ZenginException {

    private final int recordNumber;
    private final transient List<CharacterViolation> violations;

    /// Creates a diagnostic naming every violation in a record.
    ///
    /// @param recordNumber the 1-based record position in the file
    /// @param byteOffset   byte offset of the record within the file
    /// @param violations   the violations, with record-relative offsets
    public CharacterSetViolationException(
            int recordNumber, long byteOffset, List<CharacterViolation> violations) {
        super("record " + recordNumber + " at byte " + byteOffset + " contains "
                        + violations.size() + " character(s) its fields do not permit: "
                        + violations.stream().map(CharacterViolation::describeEn)
                                .collect(Collectors.joining("; ")),
                "レコード " + recordNumber + "（" + byteOffset + " バイト目）に、項目で使用できない文字が "
                        + violations.size() + " 件あります: "
                        + violations.stream().map(CharacterViolation::describeJa)
                                .collect(Collectors.joining("; ")));
        this.recordNumber = recordNumber;
        this.violations = List.copyOf(violations);
    }

    /// Returns the 1-based position of the offending record.
    ///
    /// @return the record number
    public int recordNumber() {
        return recordNumber;
    }

    /// Returns every violation found in the record, with record-relative
    /// offsets.
    ///
    /// @return the violations, never `null` and never empty
    public List<CharacterViolation> violations() {
        return violations;
    }
}
