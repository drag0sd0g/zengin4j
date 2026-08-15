package io.zengin4j.core.error;

/**
 * A field could not be decoded as the type its descriptor declares — most
 * often a non-digit byte inside an {@code N} field.
 *
 * <p>Thrown by the eager accessors. Callers that expect to meet malformed data
 * (a validator, a diagnostic tool) should use the {@code Optional}-returning
 * accessors instead and report a finding rather than catch this.
 *
 * @since 0.1.0
 */
public final class MalformedFieldException extends ZenginException {

    private final long byteOffset;
    private final int offendingByte;
    private final String fieldId;

    /**
     * Creates a malformed-field diagnostic.
     *
     * @param fieldId       descriptor id of the field, for example
     *                      {@code "amount"}
     * @param byteOffset    absolute byte offset of the offending byte within
     *                      the file, or within the record when the record's
     *                      own offset is unknown
     * @param offendingByte the offending byte, as an unsigned value
     */
    public MalformedFieldException(String fieldId, long byteOffset, int offendingByte) {
        super("field '" + fieldId + "' at byte " + byteOffset + ": expected an ASCII digit '0'-'9', found 0x"
                        + hex(offendingByte) + ". An N field must contain digits only, zero padded on the left.",
                "項目 '" + fieldId + "' (" + byteOffset + " バイト目): 数字 '0'-'9' が必要ですが 0x"
                        + hex(offendingByte) + " が見つかりました。N 項目は左ゼロ埋めの数字のみで構成されます。");
        this.byteOffset = byteOffset;
        this.offendingByte = offendingByte;
        this.fieldId = fieldId;
    }

    private static String hex(int b) {
        return String.format("%02X", b & 0xFF);
    }

    /**
     * Returns the absolute byte offset of the offending byte.
     *
     * @return a non-negative byte offset
     */
    public long byteOffset() {
        return byteOffset;
    }

    /**
     * Returns the offending byte as an unsigned value in {@code 0..255}.
     *
     * @return the offending byte
     */
    public int offendingByte() {
        return offendingByte & 0xFF;
    }

    /**
     * Returns the descriptor id of the field that failed to decode.
     *
     * @return the field id, never {@code null}
     */
    public String fieldId() {
        return fieldId;
    }
}
