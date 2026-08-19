package io.zengin4j.core.model;

import module java.base;
import io.zengin4j.core.format.RecordDescriptor;

/// Descriptor-driven fallback for a trailer record.
///
/// @since 0.1.0
public final class GenericTrailerRecord extends GenericRecord implements TrailerRecord {

    private final int recordCount;
    private final long totalAmount;

    /// Creates a fallback trailer record.
    ///
    /// @param descriptor   the layout the record was decoded with
    /// @param recordNumber the 1-based position of the record in the file
    /// @param byteOffset   the record's byte offset within the file
    /// @param rawBytes     the record's bytes
    /// @param values       decoded field values keyed by field id
    /// @param recordCount  the declared 合計件数
    /// @param totalAmount  the declared 合計金額
    public GenericTrailerRecord(
            RecordDescriptor descriptor,
            int recordNumber,
            long byteOffset,
            byte[] rawBytes,
            Map<String, String> values,
            int recordCount,
            long totalAmount) {
        super(descriptor, recordNumber, byteOffset, rawBytes, values);
        this.recordCount = recordCount;
        this.totalAmount = totalAmount;
    }

    @Override
    public int recordCount() {
        return recordCount;
    }

    @Override
    public long totalAmount() {
        return totalAmount;
    }
}
