package io.zengin4j.iso20022.envelope;

import io.zengin4j.core.error.ZenginIOException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes ZEDI files back out, byte for byte.
 *
 * <p>R-I6 asks for byte-identical framing. This achieves it by not having a
 * framing model at all: a message read from a file kept the slice it was cut
 * from, separator bytes and all, so writing is concatenation and identity
 * follows from that rather than from getting CRLF placement right.
 *
 * <p>A message built from a mapping serialises itself, and every document
 * {@code XmlSerializer} produces ends in CRLF — so a header followed by a body
 * lands as {@code …&lt;/AppHdr&gt;\r\n&lt;?xml …}, which is the concatenation
 * the profile specifies.
 *
 * @since 0.5.0
 */
public final class ZediEnvelopeWriter {

    private ZediEnvelopeWriter() {
    }

    /**
     * Writes a file.
     *
     * @param file the messages to write
     * @param path where to write them
     * @throws ZenginIOException if the file cannot be written
     */
    public static void write(ZediFile file, Path path) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(path, "path");
        try {
            Files.write(path, toByteArray(file));
        } catch (IOException e) {
            throw new ZenginIOException("writing " + path, e);
        }
    }

    /**
     * Writes a file to a stream.
     *
     * @param file   the messages to write
     * @param output the stream; the caller closes it
     * @throws ZenginIOException if the stream cannot be written
     */
    public static void write(ZediFile file, OutputStream output) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(output, "output");
        try {
            output.write(toByteArray(file));
        } catch (IOException e) {
            throw new ZenginIOException("writing a ZEDI stream", e);
        }
    }

    /**
     * Renders a file to bytes.
     *
     * @param file the messages to write
     * @return the file's bytes
     */
    public static byte[] toByteArray(ZediFile file) {
        Objects.requireNonNull(file, "file");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(file.preamble());
        for (ZediMessage message : file.messages()) {
            out.writeBytes(message.bytes());
        }
        return out.toByteArray();
    }
}
