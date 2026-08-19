package io.zengin4j.core.codec;

import module java.base;
import io.zengin4j.core.error.ZenginIOException;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.ZenginFile;

/// Entry points for reading Zengin files.
///
/// Three levels, in increasing order of convenience and decreasing order of
/// speed:
///
/// - [#open(Path, ReaderOptions)] — a record at a time, zero copies,
///   views valid only until the next record;
/// - [#batches(Path, ReaderOptions)] — a batch at a time, records
///   materialised, constant memory per batch;
/// - [#readFile(Path, ReaderOptions)] — the whole file at once.
///
/// @since 0.1.0
public final class ZenginReaders {

    private ZenginReaders() {
    }

    /// Opens a streaming reader over a byte stream.
    ///
    /// @param stream  the source; closed when the reader is closed
    /// @param options how to read
    /// @return a streaming reader
    public static ZenginReader open(InputStream stream, ReaderOptions options) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(options, "options");
        return new StreamingZenginReader(stream, options);
    }

    /// Opens a streaming reader over a file.
    ///
    /// @param path    the file to read
    /// @param options how to read
    /// @return a streaming reader
    /// @throws ZenginIOException if the file cannot be opened
    public static ZenginReader open(Path path, ReaderOptions options) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        InputStream stream = openStream(path);
        try {
            return new StreamingZenginReader(stream, options);
        } catch (RuntimeException e) {
            closeQuietly(stream, e);
            throw e;
        }
    }

    /// Opens a batch reader over a byte stream.
    ///
    /// @param stream  the source; closed when the reader is closed
    /// @param options how to read
    /// @return a batch reader
    public static BatchReader batches(InputStream stream, ReaderOptions options) {
        return new MaterialisingBatchReader(open(stream, options), options.mode());
    }

    /// Opens a batch reader over a file.
    ///
    /// @param path    the file to read
    /// @param options how to read
    /// @return a batch reader
    /// @throws ZenginIOException if the file cannot be opened
    public static BatchReader batches(Path path, ReaderOptions options) {
        return new MaterialisingBatchReader(open(path, options), options.mode());
    }

    /// Reads a whole file into memory.
    ///
    /// Convenient, and bounded by the file size rather than by the buffer.
    /// For files large enough for that to matter, iterate instead.
    ///
    /// @param stream  the source; closed before returning
    /// @param options how to read
    /// @return the materialised file
    public static ZenginFile readFile(InputStream stream, ReaderOptions options) {
        try (BatchReader reader = batches(stream, options)) {
            return drain(reader);
        }
    }

    /// Reads a whole file into memory.
    ///
    /// @param path    the file to read
    /// @param options how to read
    /// @return the materialised file
    /// @throws ZenginIOException if the file cannot be read
    public static ZenginFile readFile(Path path, ReaderOptions options) {
        try (BatchReader reader = batches(path, options)) {
            return drain(reader);
        }
    }

    private static ZenginFile drain(BatchReader reader) {
        List<Batch> batches = new ArrayList<>();
        while (reader.hasNext()) {
            batches.add(reader.next());
        }
        return new ZenginFile(reader.format(), batches, reader.endRecord(), reader.unbatched(), reader.framing());
    }

    private static InputStream openStream(Path path) {
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new ZenginIOException("opening " + path, e);
        }
    }

    private static void closeQuietly(InputStream stream, RuntimeException primary) {
        try {
            stream.close();
        } catch (IOException e) {
            primary.addSuppressed(e);
        }
    }
}
