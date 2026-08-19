package io.zengin4j.core.model;

import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordKind;

/// One record of a Zengin file.
///
/// The hierarchy is sealed over the five things a record can be, so a
/// `switch` over it is exhaustive without a default branch (R-D3). The
/// four role interfaces are `non-sealed`: each format contributes its own
/// concrete types, generated from its descriptor, and consumers may add their
/// own for institution-specific variants.
///
/// **The domain model is format-shaped, not idealised** (R-D1).
/// `SougouFurikomiHeader` has exactly the fields the 総合振込 header has,
/// in the order it has them. There is deliberately no unified "payment"
/// abstraction here: that belongs in the ISO 20022 layer, where the mapping is
/// explicit, reversible and accompanied by a loss report. This is what makes
/// round-tripping provable.
///
/// @since 0.1.0
public sealed interface ZenginRecord
        permits HeaderRecord, DataRecord, TrailerRecord, EndRecord, MalformedRecord {

    /// Returns the format this record belongs to.
    ///
    /// @return the format id, never `null`
    FormatId formatId();

    /// Returns the record's role.
    ///
    /// @return the kind, never `null`
    RecordKind kind();

    /// Returns the byte offset of this record within the file it was read
    /// from.
    ///
    /// @return the offset, or `-1` for a record that was built rather
    ///   than read
    long byteOffset();

    /// Returns the 1-based position of this record within the file.
    ///
    /// @return the record number, or `0` for a record that was built
    ///   rather than read
    int recordNumber();

    /// Returns the record's bytes exactly as they appeared in the file.
    ///
    /// Retaining the raw bytes is what makes byte-exact round-tripping
    /// possible (R-D5): unknown, reserved and filler bytes are preserved
    /// verbatim rather than regenerated from the decoded fields.
    ///
    /// Returns a fresh copy on every call, so the record stays immutable.
    ///
    /// @return a copy of the record's bytes
    byte[] rawBytes();
}
