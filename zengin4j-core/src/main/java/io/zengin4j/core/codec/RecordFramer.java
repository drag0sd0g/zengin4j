package io.zengin4j.core.codec;

import io.zengin4j.core.model.SeparatorStyle;
import java.util.Arrays;
import java.util.Optional;

/**
 * Finds record boundaries in a byte stream.
 *
 * <p>Records are fixed width, so framing is arithmetic rather than scanning.
 * The only variable is what sits between them: nothing, {@code CR},
 * {@code LF} or {@code CRLF}, possibly inconsistently within one file (R-C6).
 * Separator bytes are never counted in the record length (R-C7).
 *
 * <p>Immutable and thread-safe.
 *
 * @since 0.1.0
 */
public final class RecordFramer {

    /** {@code 0x1A}, accepted as a trailing end-of-file marker (R-C8). */
    public static final byte EOF_BYTE = 0x1A;

    /** Carriage return. */
    public static final byte CR = '\r';

    /** Line feed. */
    public static final byte LF = '\n';

    /** The UTF-8 byte order mark: never valid in a Zengin file, but it appears (R-C10). */
    public static final byte[] BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final int recordLength;

    /**
     * Creates a framer for a record length.
     *
     * @param recordLength the record length in bytes
     * @throws IllegalArgumentException if the length is not positive
     */
    public RecordFramer(int recordLength) {
        if (recordLength < 1) {
            throw new IllegalArgumentException("record length must be positive, found " + recordLength);
        }
        this.recordLength = recordLength;
    }

    /**
     * Returns the record length this framer works with.
     *
     * @return the record length in bytes
     */
    public int recordLength() {
        return recordLength;
    }

    /**
     * Reports whether a byte is a record separator.
     *
     * @param value the byte to test
     * @return {@code true} for {@code CR} and {@code LF}
     */
    public static boolean isSeparator(byte value) {
        return value == CR || value == LF;
    }

    /**
     * Advances past any separator bytes.
     *
     * @param buffer   the buffer
     * @param position the current position
     * @param limit    one past the last valid byte
     * @return the position of the first non-separator byte, or {@code limit}
     */
    public int skipSeparators(byte[] buffer, int position, int limit) {
        int cursor = position;
        while (cursor < limit && isSeparator(buffer[cursor])) {
            cursor++;
        }
        return cursor;
    }

    /**
     * Returns the offset at which the record after the one at {@code position}
     * begins.
     *
     * @param buffer   the buffer
     * @param position offset of the current record
     * @param limit    one past the last valid byte
     * @return the offset of the next record, or {@code limit} if the buffer
     *         ends first
     */
    public int nextRecordOffset(byte[] buffer, int position, int limit) {
        int cursor = Math.min(position + recordLength, limit);
        return skipSeparators(buffer, cursor, limit);
    }

    /**
     * Reports whether a buffer begins with a UTF-8 byte order mark.
     *
     * @param buffer   the buffer
     * @param position the position to test at
     * @param limit    one past the last valid byte
     * @return {@code true} if the three byte order mark bytes are present
     */
    public static boolean startsWithByteOrderMark(byte[] buffer, int position, int limit) {
        if (limit - position < BYTE_ORDER_MARK.length) {
            return false;
        }
        for (int i = 0; i < BYTE_ORDER_MARK.length; i++) {
            if (buffer[position + i] != BYTE_ORDER_MARK[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Classifies a run of separator bytes.
     *
     * @param separator the bytes found between two records
     * @return the convention they represent, or empty if the run is not one of
     *         the four recognised conventions — a blank line between records,
     *         for example
     */
    public static Optional<SeparatorStyle> classify(byte[] separator) {
        return SeparatorStyle.of(Arrays.copyOf(separator, separator.length));
    }
}
