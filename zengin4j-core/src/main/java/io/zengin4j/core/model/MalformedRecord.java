package io.zengin4j.core.model;

import module java.base;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordKind;

/// A record the reader could not interpret.
///
/// Part of the record hierarchy rather than an exception, on purpose
/// (R-D8): one bad record in a ten-thousand-line file must not stop the caller
/// from seeing the other 9,999. Produced only in lenient mode; in strict mode
/// the same conditions raise
/// [io.zengin4j.core.error.MalformedFileException].
///
/// @param formatId     the format being read when the record was met
/// @param recordNumber the 1-based position of the record in the file
/// @param byteOffset   the record's byte offset within the file
/// @param rawBytes     the bytes as they appeared, so nothing is lost
/// @param reason       what could not be interpreted, and why
/// @since 0.1.0
public record MalformedRecord(
        FormatId formatId,
        int recordNumber,
        long byteOffset,
        byte[] rawBytes,
        String reason) implements ZenginRecord {

    /// Validates the components and copies the bytes defensively.
    public MalformedRecord {
        Objects.requireNonNull(formatId, "formatId");
        Objects.requireNonNull(reason, "reason");
        rawBytes = Objects.requireNonNull(rawBytes, "rawBytes").clone();
    }

    @Override
    public RecordKind kind() {
        return RecordKind.MALFORMED;
    }

    @Override
    public byte[] rawBytes() {
        return rawBytes.clone();
    }

    /// Two malformed records are equal when their bytes and reason match;
    /// position within the file is metadata, not identity.
    ///
    /// @param other the object to compare with
    /// @return whether the records are equal
    @Override
    public boolean equals(Object other) {
        return other instanceof MalformedRecord record
                && formatId.equals(record.formatId)
                && reason.equals(record.reason)
                && Arrays.equals(rawBytes, record.rawBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(formatId, reason, Arrays.hashCode(rawBytes));
    }

    /// Describes the record without printing its content: the bytes are
    /// unparseable, but they are still payment data (R-CLI4).
    ///
    /// @return a diagnostic description
    @Override
    public String toString() {
        return "MalformedRecord[format=" + formatId + ", recordNumber=" + recordNumber
                + ", byteOffset=" + byteOffset + ", bytes=" + rawBytes.length
                + ", reason=" + reason + "]";
    }
}
