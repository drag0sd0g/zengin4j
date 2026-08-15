package io.zengin4j.core.model;

import io.zengin4j.core.format.RecordDescriptor;
import java.util.Map;

/**
 * Descriptor-driven fallback for a data record.
 *
 * @since 0.1.0
 */
public final class GenericDataRecord extends GenericRecord implements DataRecord {

    private final long amount;

    /**
     * Creates a fallback data record.
     *
     * @param descriptor   the layout the record was decoded with
     * @param recordNumber the 1-based position of the record in the file
     * @param byteOffset   the record's byte offset within the file
     * @param rawBytes     the record's bytes
     * @param values       decoded field values keyed by field id
     * @param amount       the decoded monetary amount in whole yen
     */
    public GenericDataRecord(
            RecordDescriptor descriptor,
            int recordNumber,
            long byteOffset,
            byte[] rawBytes,
            Map<String, String> values,
            long amount) {
        super(descriptor, recordNumber, byteOffset, rawBytes, values);
        this.amount = amount;
    }

    @Override
    public long amount() {
        return amount;
    }
}
