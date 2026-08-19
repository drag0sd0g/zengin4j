package io.zengin4j.core.error;

/// The byte stream cannot be framed into records at all, or violates the record
/// sequence while the reader is in strict mode.
///
/// In lenient mode the same conditions produce a
/// [MalformedRecord][io.zengin4j.core.model.MalformedRecord] and parsing
/// continues, so that one bad record does not hide the other 9,999 (R-D8).
///
/// @since 0.1.0
public final class MalformedFileException extends ZenginException {

    private final long byteOffset;
    private final int recordNumber;

    /// Creates a malformed-file diagnostic.
    ///
    /// @param byteOffset   byte offset in the file at which the problem was
    ///   detected
    /// @param recordNumber 1-based record number at which the problem was
    ///   detected, or `0` before the first record
    /// @param messageEn    the English diagnostic
    /// @param messageJa    the Japanese diagnostic
    public MalformedFileException(long byteOffset, int recordNumber, String messageEn, String messageJa) {
        super("record " + recordNumber + " at byte " + byteOffset + ": " + messageEn,
                "レコード " + recordNumber + " (" + byteOffset + " バイト目): " + messageJa);
        this.byteOffset = byteOffset;
        this.recordNumber = recordNumber;
    }

    /// Returns the byte offset in the file at which the problem was detected.
    ///
    /// @return a non-negative byte offset
    public long byteOffset() {
        return byteOffset;
    }

    /// Returns the 1-based record number at which the problem was detected.
    ///
    /// @return the record number, or `0` if the problem precedes the
    ///   first record
    public int recordNumber() {
        return recordNumber;
    }
}
