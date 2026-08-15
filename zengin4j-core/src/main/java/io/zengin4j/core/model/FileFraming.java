package io.zengin4j.core.model;

import java.util.Objects;

/**
 * How a file was framed, beyond its records.
 *
 * <p>Byte-exact round-tripping (INV-1) needs more than the records: a file
 * that began with a byte order mark, separated its records with CRLF and
 * ended with an EOF byte must be written back the same way. These are
 * observations, recorded so the writer can reproduce them rather than impose
 * a house style.
 *
 * @param byteOrderMarkPresent whether the file began with a UTF-8 byte order
 *                             mark, which is never valid here but does appear
 *                             (R-C10)
 * @param separator            the separator convention observed
 * @param trailingEofByte      whether the file ended with {@code 0x1A}
 *                             (R-C8)
 * @since 0.1.0
 */
public record FileFraming(boolean byteOrderMarkPresent, SeparatorStyle separator, boolean trailingEofByte) {

    /**
     * Validates the components.
     */
    public FileFraming {
        Objects.requireNonNull(separator, "separator");
    }

    /**
     * Returns the framing of a file with no byte order mark, no separators and
     * no EOF byte.
     *
     * @return the minimal framing
     */
    public static FileFraming none() {
        return new FileFraming(false, SeparatorStyle.NONE, false);
    }

    /**
     * Reports whether this framing can be reproduced exactly by a writer.
     *
     * @return {@code false} if the file mixed separator conventions
     */
    public boolean isReproducible() {
        return separator != SeparatorStyle.MIXED;
    }
}
