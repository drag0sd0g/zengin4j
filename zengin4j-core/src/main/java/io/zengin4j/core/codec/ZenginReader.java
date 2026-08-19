package io.zengin4j.core.codec;

import module java.base;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.FileFraming;

/// Reads a Zengin file one record at a time.
///
/// **Stateful and not thread-safe** (R-T2). One instance per
/// thread. The library never spawns a thread of its own (R-T5); a caller who
/// wants parallelism splits the input at record boundaries — which is trivial,
/// because records are fixed width — and reads each part with its own reader.
///
/// The views this reader returns are windows onto a recycled buffer and are
/// valid only until the next call to [#next()]. See [RecordView].
/// For a safer, slower API, use [BatchReader].
///
/// @since 0.1.0
public interface ZenginReader extends AutoCloseable {

    /// Returns the format the file is being read as.
    ///
    /// @return the format descriptor
    FormatDescriptor format();

    /// Returns the record length in use, which may be an override rather than
    /// the format's own (R-C5).
    ///
    /// @return the record length in bytes
    int recordLength();

    /// Reports whether another record is available.
    ///
    /// @return `true` if [#next()] will return a record
    boolean hasNext();

    /// Returns the next record.
    ///
    /// Invalidates any view returned previously.
    ///
    /// @return a view onto the next record
    /// @throws NoSuchElementException                        if no record
    ///   remains
    /// @throws io.zengin4j.core.error.MalformedFileException in strict mode,
    ///   if the record does
    ///   not fit the format
    RecordView next();

    /// Returns how the file has been framed so far.
    ///
    /// Complete only once iteration has finished: a separator convention
    /// cannot be reported before the separators have been read.
    ///
    /// @return the framing observed up to the current position
    FileFraming framing();

    /// Returns the warnings raised so far.
    ///
    /// @return an unmodifiable list, oldest first
    List<ZenginWarning> warnings();

    /// Closes the underlying stream.
    ///
    /// @throws io.zengin4j.core.error.ZenginIOException if closing fails
    @Override
    void close();
}
