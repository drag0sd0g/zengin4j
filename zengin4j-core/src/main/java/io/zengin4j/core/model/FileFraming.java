package io.zengin4j.core.model;

import java.util.Objects;

/**
 * How a file was framed, beyond its records.
 *
 * <p>Byte-exact round-tripping (INV-1) needs more than the records: a file
 * that began with a byte order mark, separated its records with CRLF, ended
 * with a separator after the last one and then an EOF byte must be written
 * back the same way. These are observations, recorded so the writer can
 * reproduce them rather than impose a house style.
 *
 * @param byteOrderMarkPresent whether the file began with a UTF-8 byte order
 *                             mark, which is never valid here but does appear
 *                             (R-C10)
 * @param separator            the separator convention observed
 * @param trailingSeparator    whether a separator followed the <em>last</em>
 *                             record. The documented framing appends one to
 *                             every record, but files omitting it exist, and
 *                             INV-1 has to reproduce whichever arrived — see
 *                             OQ-4
 * @param trailingEofByte      whether the file ended with {@code 0x1A}
 *                             (R-C8)
 * @since 0.1.0
 */
public record FileFraming(
        boolean byteOrderMarkPresent,
        SeparatorStyle separator,
        boolean trailingSeparator,
        boolean trailingEofByte) {
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
        return new FileFraming(false, SeparatorStyle.NONE, false, false);
    }

    /**
     * Returns conventional framing: CRLF after every record, including the
     * last, which is what the published record-length statements describe
     * (OQ-4).
     *
     * @return the default framing for a file built from scratch
     */
    public static FileFraming conventional() {
        return new FileFraming(false, SeparatorStyle.CRLF, true, false);
    }

    /**
     * Reports whether this framing can be reproduced exactly by a writer.
     *
     * @return {@code false} if the file mixed separator conventions
     */
    public boolean isReproducible() {
        return separator != SeparatorStyle.MIXED;
    }

    /**
     * Reports whether any bytes separate the records.
     *
     * @return {@code false} for {@link SeparatorStyle#NONE}
     */
    public boolean hasSeparator() {
        return separator != SeparatorStyle.NONE;
    }
}
