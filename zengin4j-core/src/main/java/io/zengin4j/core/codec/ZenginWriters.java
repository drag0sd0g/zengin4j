package io.zengin4j.core.codec;

import module java.base;
import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.error.ZenginIOException;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;

/// Writes a [ZenginFile] back to bytes.
///
/// **Records are written from the bytes they carry, not re-encoded
/// from their decoded fields** (R-D5). That is the whole reason every
/// record retains its raw bytes: filler, reserved space and any content this
/// library does not understand survive a round trip untouched, rather than
/// being regenerated from what it happens to model. A parser that rebuilt
/// records from decoded values would quietly normalise away exactly the bytes
/// nobody has verified yet.
///
/// Output is deterministic (R-C19): the same file produces the same bytes on
/// every run, on every platform. Nothing here depends on a default charset, a
/// locale, a map iteration order or a timestamp.
///
/// Stateless, and therefore safe to call from any thread — R-T2's warning
/// applies to the stateful [ZenginReader] and [ZenginFileBuilder],
/// not here. A whole file is rendered in one call; there is no streaming writer,
/// because the memory bound that motivates streaming (R-P2) is a property of
/// reading files this library did not create.
///
/// @since 0.1.0
public final class ZenginWriters {

    private ZenginWriters() {
    }

    /// Writes a file to a stream.
    ///
    /// @param file    the file to write
    /// @param out     the destination; not closed by this method
    /// @param options how to write; by default the file's own framing is
    ///   reproduced
    /// @throws ZenginIOException          if writing fails
    /// @throws FormatDescriptorException  if the file's framing cannot be
    ///   reproduced, which happens only when
    ///   it mixed separator conventions
    public static void write(ZenginFile file, OutputStream out, WriterOptions options) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(options, "options");
        try {
            out.write(toByteArray(file, options));
        } catch (IOException e) {
            throw new ZenginIOException("writing " + file.format(), e);
        }
    }

    /// Writes a file to disk.
    ///
    /// @param file    the file to write
    /// @param path    the destination
    /// @param options how to write
    /// @throws ZenginIOException if writing fails
    public static void write(ZenginFile file, Path path, WriterOptions options) {
        Objects.requireNonNull(path, "path");
        try {
            Files.write(path, toByteArray(file, options));
        } catch (IOException e) {
            throw new ZenginIOException("writing " + path, e);
        }
    }

    /// Renders a file to bytes.
    ///
    /// @param file    the file to write
    /// @param options how to write
    /// @return the file's bytes
    /// @throws FormatDescriptorException if the framing cannot be reproduced
    public static byte[] toByteArray(ZenginFile file, WriterOptions options) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(options, "options");

        FileFraming framing = options.resolve(file.framing());
        if (!framing.isReproducible()) {
            throw FormatDescriptorException.forFormat(file.format().value(),
                    "the file mixed record separator conventions, so it cannot be written back byte for"
                            + " byte. Choose one explicitly with WriterOptions.separator(...).");
        }
        byte[] separator = framing.separator().bytes().orElseThrow();

        List<ZenginRecord> records = inFileOrder(file);

        byte[] out = new byte[size(records, separator.length, framing)];
        int at = 0;
        if (framing.byteOrderMarkPresent()) {
            at = append(out, at, RecordFramer.BYTE_ORDER_MARK);
        }
        for (int i = 0; i < records.size(); i++) {
            at = append(out, at, records.get(i).rawBytes());
            boolean last = i == records.size() - 1;
            if (!last || framing.trailingSeparator()) {
                at = append(out, at, separator);
            }
        }
        if (framing.trailingEofByte()) {
            out[at++] = RecordFramer.EOF_BYTE;
        }
        if (at != out.length) {
            throw new IllegalStateException(
                    "wrote " + at + " bytes into a " + out.length + "-byte file");
        }
        return out;
    }

    private static int append(byte[] out, int at, byte[] bytes) {
        System.arraycopy(bytes, 0, out, at, bytes.length);
        return at + bytes.length;
    }

    /// The exact length of the file about to be written.
    ///
    /// Exact rather than estimated, because it is the output array rather
    /// than a capacity hint: an arithmetic error here fails on the next write
    /// instead of quietly over-allocating, and there is no final copy out of a
    /// growable buffer.
    ///
    /// Record lengths are summed individually rather than multiplied out. A
    /// malformed record read in lenient mode may be shorter than the format's
    /// record length, and it still has to be written back (R-D5).
    private static int size(List<ZenginRecord> records, int separatorLength, FileFraming framing) {
        int total = framing.byteOrderMarkPresent() ? RecordFramer.BYTE_ORDER_MARK.length : 0;
        for (ZenginRecord record : records) {
            total += record.rawBytes().length + separatorLength;
        }
        if (!records.isEmpty() && !framing.trailingSeparator()) {
            total -= separatorLength;
        }
        return total + (framing.trailingEofByte() ? 1 : 0);
    }

    /// Recovers the order the records appeared in.
    ///
    /// Every record read from a file carries its 1-based position, so the
    /// original sequence can be restored exactly — including malformed records
    /// read in lenient mode, which the batch structure alone cannot place. The
    /// sort is stable, so records built rather than read (all numbered from the
    /// builder in construction order) keep the order they were added in.
    private static List<ZenginRecord> inFileOrder(ZenginFile file) {
        List<ZenginRecord> records = new ArrayList<>(file.totalRecords());
        for (Batch batch : file.batches()) {
            records.add(batch.header());
            records.addAll(batch.data());
            records.addAll(batch.malformed());
            batch.trailer().ifPresent(records::add);
        }
        file.endRecord().ifPresent(records::add);
        records.addAll(file.unbatched());
        records.sort(Comparator.comparingInt(ZenginRecord::recordNumber));
        return records;
    }


    /// Returns the bytes a separator style writes, for callers assembling files
    /// by hand.
    ///
    /// @param style the separator style
    /// @return the bytes
    /// @throws IllegalArgumentException if the style is [SeparatorStyle#MIXED],
    ///   which is an observation rather than a
    ///   setting
    public static byte[] separatorBytes(SeparatorStyle style) {
        return style.bytes().orElseThrow(() -> new IllegalArgumentException(
                "MIXED is an observation, not a separator a writer can produce"));
    }
}
