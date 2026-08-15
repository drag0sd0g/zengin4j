package io.zengin4j.core.model;

import io.zengin4j.core.format.RecordDescriptor;
import java.util.Arrays;
import java.util.Map;

/**
 * Descriptor-driven fallback for an end record.
 *
 * @since 0.1.0
 */
public final class GenericEndRecord extends GenericRecord implements EndRecord {

    /**
     * Creates a fallback end record.
     *
     * @param descriptor   the layout the record was decoded with
     * @param recordNumber the 1-based position of the record in the file
     * @param byteOffset   the record's byte offset within the file
     * @param rawBytes     the record's bytes
     * @param values       decoded field values keyed by field id
     */
    public GenericEndRecord(
            RecordDescriptor descriptor,
            int recordNumber,
            long byteOffset,
            byte[] rawBytes,
            Map<String, String> values) {
        super(descriptor, recordNumber, byteOffset, rawBytes, values);
    }

    @Override
    public byte[] filler() {
        byte[] bytes = rawBytes();
        return Arrays.copyOfRange(bytes, 1, bytes.length);
    }
}
