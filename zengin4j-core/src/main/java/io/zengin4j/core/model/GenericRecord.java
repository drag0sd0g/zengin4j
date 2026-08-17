package io.zengin4j.core.model;

import io.zengin4j.core.error.Diagnostics;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordDescriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base of the descriptor-driven fallback records.
 *
 * <p>Every format bundled with this library has generated, format-shaped
 * record types (R-D1). These fallbacks exist for descriptors registered at
 * runtime, where no generated type can exist: they expose the same
 * information keyed by field id instead of by accessor name.
 *
 * <p>Prefer the generated types. A fallback record cannot give you
 * {@code beneficiaryName()}; it can only give you {@code value("...")}, and a
 * misspelling there is a runtime surprise rather than a compile error.
 *
 * @since 0.1.0
 */
public abstract sealed class GenericRecord
        permits GenericHeaderRecord, GenericDataRecord, GenericTrailerRecord, GenericEndRecord {
    private final RecordDescriptor descriptor;
    private final int recordNumber;
    private final long byteOffset;
    private final byte[] rawBytes;
    private final Map<String, String> values;

    /**
     * Creates a fallback record.
     *
     * @param descriptor   the layout the record was decoded with
     * @param recordNumber the 1-based position of the record in the file
     * @param byteOffset   the record's byte offset within the file
     * @param rawBytes     the record's bytes, copied defensively
     * @param values       decoded field values keyed by field id, copied
     *                     defensively
     */
    protected GenericRecord(
            RecordDescriptor descriptor,
            int recordNumber,
            long byteOffset,
            byte[] rawBytes,
            Map<String, String> values) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.recordNumber = recordNumber;
        this.byteOffset = byteOffset;
        this.rawBytes = Objects.requireNonNull(rawBytes, "rawBytes").clone();
        this.values = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
    }

    /**
     * Returns the layout this record was decoded with.
     *
     * @return the record descriptor
     */
    public final RecordDescriptor descriptor() {
        return descriptor;
    }

    /**
     * Returns the format this record belongs to.
     *
     * @return the format id
     */
    public final FormatId formatId() {
        return descriptor.formatId();
    }

    /**
     * Returns the 1-based position of this record within the file.
     *
     * @return the record number
     */
    public final int recordNumber() {
        return recordNumber;
    }

    /**
     * Returns the byte offset of this record within the file.
     *
     * @return the byte offset
     */
    public final long byteOffset() {
        return byteOffset;
    }

    /**
     * Returns a copy of the record's bytes.
     *
     * @return the raw bytes
     */
    public final byte[] rawBytes() {
        return rawBytes.clone();
    }

    /**
     * Returns every decoded field value, keyed by field id, in layout order.
     *
     * @return an unmodifiable map of field values
     */
    public final Map<String, String> values() {
        return values;
    }

    /**
     * Returns one decoded field value.
     *
     * @param fieldId the field id
     * @return the value, or an empty string if this record has no such field
     */
    public final String value(String fieldId) {
        return values.getOrDefault(fieldId, "");
    }

    /**
     * Two fallback records are equal when they share a format, a role and
     * their bytes; position within the file is metadata, not identity.
     *
     * @param other the object to compare with
     * @return whether the records are equal
     */
    @Override
    public final boolean equals(Object other) {
        return other instanceof GenericRecord record
                && getClass() == record.getClass()
                && descriptor.formatId().equals(record.descriptor.formatId())
                && descriptor.kind() == record.descriptor.kind()
                && Arrays.equals(rawBytes, record.rawBytes);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(descriptor.formatId(), descriptor.kind(), Arrays.hashCode(rawBytes));
    }

    /**
     * Renders the record with values marked {@code sensitive} in the
     * descriptor masked to their last four characters (R-E6).
     *
     * @return a diagnostic description
     */
    @Override
    public final String toString() {
        StringBuilder result = new StringBuilder(getClass().getSimpleName())
                .append("[format=").append(descriptor.formatId())
                .append(", recordNumber=").append(recordNumber);
        for (FieldDescriptor field : descriptor.fields()) {
            if (field.filler()) {
                continue;
            }
            String value = value(field.id());
            result.append(", ").append(field.id()).append('=')
                    .append(field.sensitive() ? Diagnostics.maskIdentifier(value) : value);
        }
        return result.append(']').toString();
    }
}
