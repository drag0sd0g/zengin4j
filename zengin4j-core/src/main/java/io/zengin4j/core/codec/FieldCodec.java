package io.zengin4j.core.codec;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.error.MalformedFieldException;
import io.zengin4j.core.format.Alignment;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldType;

/// Byte-level field encoding and decoding.
///
/// Numeric decoding runs a hand-written digit loop over the bytes: no
/// intermediate `String`, no `Integer.parseInt`, no boxing
/// (R-MEM3, §19.2). At the performance budget in §22 — roughly 2.3 µs per
/// record — allocating a `String` per field to throw it away is the
/// difference between meeting the target and not.
///
/// All lengths here are byte counts. A `String.length()` anywhere in
/// this class would be a defect (R-C15): `ｶﾞ` is one character and two
/// bytes.
///
/// @since 0.1.0
public final class FieldCodec {

    /// The `N(12)` trailer total ceiling: ¥999,999,999,999.
    public static final long MAX_TRAILER_TOTAL = 999_999_999_999L;

    private static final char ZERO = '0';
    private static final char NINE = '9';

    private FieldCodec() {
    }

    /// Decodes a zoned-decimal field to a `long`.
    ///
    /// @param buffer the source buffer
    /// @param offset start offset within the buffer
    /// @param length field length in bytes
    /// @return the decoded value
    /// @throws MalformedFieldException if any byte is not an ASCII digit
    public static long decodeNumeric(byte[] buffer, int offset, int length) {
        return decodeNumeric(buffer, offset, length, "<unnamed>", offset);
    }

    /// Decodes a zoned-decimal field to a `long`, naming the field in any
    /// diagnostic.
    ///
    /// @param buffer         the source buffer
    /// @param offset         start offset within the buffer
    /// @param length         field length in bytes
    /// @param fieldId        the field id, for diagnostics
    /// @param fileByteOffset the field's byte offset within the file, for
    ///   diagnostics
    /// @return the decoded value
    /// @throws MalformedFieldException if any byte is not an ASCII digit
    public static long decodeNumeric(byte[] buffer, int offset, int length, String fieldId, long fileByteOffset) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            int digit = buffer[offset + i] - ZERO;
            if (digit < 0 || digit > NINE - ZERO) {
                throw new MalformedFieldException(fieldId, fileByteOffset + i, buffer[offset + i]);
            }
            value = value * 10 + digit;
        }
        return value;
    }

    /// Decodes a zoned-decimal field without failing on non-digit content.
    ///
    /// For callers that expect to meet malformed data and want to report it
    /// rather than catch it — validation rules, diagnostic tools.
    ///
    /// @param buffer the source buffer
    /// @param offset start offset within the buffer
    /// @param length field length in bytes
    /// @return the decoded value, or empty if any byte is not an ASCII digit
    public static OptionalLong tryDecodeNumeric(byte[] buffer, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            int digit = buffer[offset + i] - ZERO;
            if (digit < 0 || digit > NINE - ZERO) {
                return OptionalLong.empty();
            }
            value = value * 10 + digit;
        }
        return OptionalLong.of(value);
    }

    /// Decodes a byte range as text, verbatim, including any padding.
    ///
    /// @param buffer  the source buffer
    /// @param offset  start offset within the buffer
    /// @param length  field length in bytes
    /// @param charset the encoding to decode with
    /// @return the decoded text
    public static String decodeText(byte[] buffer, int offset, int length, ZenginCharset charset) {
        return charset.decode(buffer, offset, length);
    }

    /// Decodes a field, removing padding that the field type defines as
    /// padding.
    ///
    /// A `C` field loses its trailing spaces, because those are the
    /// pad bytes the format prescribes. An `N` field keeps its leading
    /// zeros, because a ten-digit originator code beginning with a zero is a
    /// different code from the nine-digit one that remains after trimming.
    ///
    /// Trailing spaces are removed at the byte level before decoding, which
    /// is safe for every supported encoding: `0x20` cannot appear as a
    /// trailing byte of a multi-byte Shift_JIS character, nor as a UTF-8
    /// continuation byte.
    ///
    /// @param buffer       the source buffer
    /// @param recordOffset offset of the record's first byte within the buffer
    /// @param field        the field to decode
    /// @param charset      the encoding to decode with
    /// @return the decoded value
    public static String decodeField(byte[] buffer, int recordOffset, FieldDescriptor field, ZenginCharset charset) {
        int offset = recordOffset + field.offset();
        int length = field.length();
        if (field.type() == FieldType.C) {
            byte pad = field.type().padByte();
            while (length > 0 && buffer[offset + length - 1] == pad) {
                length--;
            }
        }
        return charset.decode(buffer, offset, length);
    }

    /// Encodes a value into a zoned-decimal field, zero padded on the left.
    ///
    /// @param value  the value to encode; must not be negative
    /// @param buffer the target buffer
    /// @param offset start offset within the buffer
    /// @param length field length in bytes
    /// @throws IllegalArgumentException if the value is negative or needs more
    ///   digits than the field holds
    public static void encodeNumeric(long value, byte[] buffer, int offset, int length) {
        if (value < 0) {
            throw new IllegalArgumentException("an N field cannot carry a negative value: " + value);
        }
        long remaining = value;
        for (int i = length - 1; i >= 0; i--) {
            buffer[offset + i] = (byte) (ZERO + (int) (remaining % 10));
            remaining /= 10;
        }
        if (remaining != 0) {
            throw new IllegalArgumentException(
                    "value " + value + " does not fit in an N(" + length + ") field");
        }
    }

    /// Encodes text into a field, padding it to the field width.
    ///
    /// Rejects text that does not fit rather than truncating it (R-C18,
    /// P5). Truncating a beneficiary name at a byte boundary can change the
    /// name — see §17 — and no encoder should do that silently.
    ///
    /// @param text    the text to encode
    /// @param buffer  the target buffer
    /// @param offset  start offset within the buffer
    /// @param length  field length in bytes
    /// @param charset the encoding to encode with
    /// @param policy  alignment and pad byte
    /// @throws IllegalArgumentException if the encoded text exceeds the field
    ///   width
    public static void encodeText(
            String text, byte[] buffer, int offset, int length, ZenginCharset charset, PadPolicy policy) {
        byte[] encoded = charset.encode(text);
        if (encoded.length > length) {
            throw new IllegalArgumentException("'" + text + "' encodes to " + encoded.length
                    + " bytes in " + charset + " and does not fit a " + length + "-byte field."
                    + " Shorten it deliberately rather than letting the codec truncate it.");
        }
        int padding = length - encoded.length;
        if (policy.alignment() == Alignment.LEFT) {
            System.arraycopy(encoded, 0, buffer, offset, encoded.length);
            fill(buffer, offset + encoded.length, padding, policy.padByte());
        } else {
            fill(buffer, offset, padding, policy.padByte());
            System.arraycopy(encoded, 0, buffer, offset + padding, encoded.length);
        }
    }

    /// Fills a byte range with a single value.
    ///
    /// @param buffer the target buffer
    /// @param offset start offset within the buffer
    /// @param length number of bytes to fill
    /// @param value  the byte to write
    public static void fill(byte[] buffer, int offset, int length, byte value) {
        java.util.Arrays.fill(buffer, offset, offset + length, value);
    }
}
