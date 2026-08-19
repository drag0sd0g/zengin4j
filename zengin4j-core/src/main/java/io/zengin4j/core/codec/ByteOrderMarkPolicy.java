package io.zengin4j.core.codec;

/// What to do about a UTF-8 byte order mark at the start of a file (R-C10).
///
/// A byte order mark is never valid in a Zengin file. It appears when a file
/// has been opened and re-saved by a text editor, which usually means the
/// content was re-encoded too — so the mark is a symptom worth surfacing rather
/// than a nuisance worth hiding.
///
/// @since 0.1.0
public enum ByteOrderMarkPolicy {

    /// Fail with a located diagnostic. The default: the mark's presence
    /// suggests the file has been through tooling that may also have changed
    /// its encoding.
    REJECT,

    /// Skip the three bytes and emit a warning.
    STRIP
}
