package io.zengin4j.core.codec;

import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.error.MalformedFieldException;
import io.zengin4j.core.error.StaleRecordViewException;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.MalformedRecord;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.core.time.MonthDays;
import java.time.MonthDay;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A window onto one record inside the reader's buffer.
 *
 * <p><strong>The buffer is recycled. A view is valid only until the next call
 * to {@link ZenginReader#next()}.</strong> Nothing is copied when a view is
 * created — that is the point, and it is what makes the throughput target
 * reachable — so a view retained across an iteration would describe whatever
 * record now occupies those bytes. Accessing a stale view raises
 * {@link StaleRecordViewException} rather than returning plausible wrong
 * values.
 *
 * <p>To keep a record, call {@link #materialize()}. To avoid thinking about it
 * at all, use {@link BatchReader}, which materialises by default (R-MEM5).
 *
 * <p>Field values are decoded on access and cached for the life of the view
 * (R-MEM4). A caller that never asks for the beneficiary name never pays for
 * decoding it.
 *
 * @since 0.1.0
 */
public final class RecordView {
    private final byte[] buffer;
    private final int offset;
    private final int length;
    private final FormatDescriptor format;
    private final RecordDescriptor descriptor;
    private final String malformedReason;
    private final ZenginCharset charset;
    private final long byteOffset;
    private final int recordNumber;
    private final ViewGeneration generation;
    private final int generationValue;

    private String[] cache;

    private RecordView(
            byte[] buffer,
            int offset,
            int length,
            FormatDescriptor format,
            RecordDescriptor descriptor,
            String malformedReason,
            ZenginCharset charset,
            long byteOffset,
            int recordNumber,
            ViewGeneration generation) {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
        this.format = format;
        this.descriptor = descriptor;
        this.malformedReason = malformedReason;
        this.charset = charset;
        this.byteOffset = byteOffset;
        this.recordNumber = recordNumber;
        this.generation = generation;
        this.generationValue = generation.current();
    }

    static RecordView wellFormed(
            byte[] buffer,
            int offset,
            int length,
            FormatDescriptor format,
            RecordDescriptor descriptor,
            ZenginCharset charset,
            long byteOffset,
            int recordNumber,
            ViewGeneration generation) {
        return new RecordView(buffer, offset, length, format, Objects.requireNonNull(descriptor, "descriptor"),
                null, charset, byteOffset, recordNumber, generation);
    }

    static RecordView malformed(
            byte[] buffer,
            int offset,
            int length,
            FormatDescriptor format,
            String reason,
            ZenginCharset charset,
            long byteOffset,
            int recordNumber,
            ViewGeneration generation) {
        return new RecordView(buffer, offset, length, format, null,
                Objects.requireNonNull(reason, "reason"), charset, byteOffset, recordNumber, generation);
    }

    /**
     * Returns the record's role.
     *
     * @return the kind, or {@link RecordKind#MALFORMED} if the record could
     *         not be interpreted
     */
    public RecordKind kind() {
        return descriptor == null ? RecordKind.MALFORMED : descriptor.kind();
    }

    /**
     * Returns the format being read.
     *
     * @return the format descriptor
     */
    public FormatDescriptor format() {
        return format;
    }

    /**
     * Returns the layout this record was matched to.
     *
     * @return the record descriptor, or empty for a malformed record
     */
    public Optional<RecordDescriptor> descriptor() {
        return Optional.ofNullable(descriptor);
    }

    /**
     * Returns the layout this record was matched to, failing if there is none.
     *
     * @return the record descriptor
     * @throws IllegalStateException if the record is malformed
     */
    public RecordDescriptor requireDescriptor() {
        if (descriptor == null) {
            throw new IllegalStateException("record " + recordNumber + " is malformed (" + malformedReason
                    + "); check kind() or isMalformed() before reading fields");
        }
        return descriptor;
    }

    /**
     * Reports whether the record could be interpreted.
     *
     * @return {@code true} if no layout matched, or the record is truncated
     */
    public boolean isMalformed() {
        return descriptor == null;
    }

    /**
     * Returns why the record could not be interpreted.
     *
     * @return the reason, or empty for a well-formed record
     */
    public Optional<String> malformedReason() {
        return Optional.ofNullable(malformedReason);
    }

    /**
     * Returns the 1-based position of this record within the file.
     *
     * @return the record number
     */
    public int recordNumber() {
        return recordNumber;
    }

    /**
     * Returns the byte offset of this record within the file.
     *
     * @return the byte offset
     */
    public long byteOffset() {
        return byteOffset;
    }

    /**
     * Returns the record's length in bytes.
     *
     * @return the length; less than the format's record length only for a
     *         truncated final record
     */
    public int length() {
        return length;
    }

    /**
     * Reports whether the view still describes the record it was created for.
     *
     * @return {@code false} once the reader has advanced past it
     */
    public boolean isValid() {
        return generation.current() == generationValue;
    }

    /**
     * Looks a field up by id.
     *
     * @param fieldId the field id
     * @return the descriptor
     * @throws IllegalStateException if the record is malformed
     * @throws io.zengin4j.core.error.FormatDescriptorException if the record
     *                                                          has no such
     *                                                          field
     */
    public FieldDescriptor field(String fieldId) {
        return requireDescriptor().field(fieldId);
    }

    /**
     * Decodes a field as text, with the field type's padding removed.
     *
     * @param field the field to decode
     * @return the decoded value
     * @throws StaleRecordViewException if the reader has advanced past this
     *                                  record
     */
    public String asString(FieldDescriptor field) {
        checkValid();
        int index = checkField(field);
        if (cache == null) {
            cache = new String[requireDescriptor().fields().size()];
        }
        String cached = cache[index];
        if (cached == null) {
            cached = FieldCodec.decodeField(buffer, offset, field, charset);
            cache[index] = cached;
        }
        return cached;
    }

    /**
     * Decodes a field as text by id.
     *
     * @param fieldId the field id
     * @return the decoded value
     */
    public String asString(String fieldId) {
        return asString(field(fieldId));
    }

    /**
     * Decodes a field as text, keeping its padding exactly as written.
     *
     * @param field the field to decode
     * @return the decoded value, padding included
     */
    public String asPaddedString(FieldDescriptor field) {
        checkValid();
        checkField(field);
        return FieldCodec.decodeText(buffer, offset + field.offset(), field.length(), charset);
    }

    /**
     * Decodes a zoned-decimal field, allocating nothing (R-MEM3).
     *
     * @param field the field to decode
     * @return the value
     * @throws MalformedFieldException  if the field is not all digits
     * @throws StaleRecordViewException if the reader has advanced past this
     *                                  record
     */
    public long asLong(FieldDescriptor field) {
        checkValid();
        checkField(field);
        return FieldCodec.decodeNumeric(buffer, offset + field.offset(), field.length(),
                field.id(), byteOffset + field.offset());
    }

    /**
     * Decodes a zoned-decimal field without failing on non-digit content.
     *
     * @param field the field to decode
     * @return the value, or empty if the field is not all digits
     */
    public OptionalLong asOptionalLong(FieldDescriptor field) {
        checkValid();
        checkField(field);
        return FieldCodec.tryDecodeNumeric(buffer, offset + field.offset(), field.length());
    }

    /**
     * Decodes an {@code MMDD} field.
     *
     * @param field the field to decode
     * @return the month and day, or empty if the field is unset or not a valid
     *         month and day; the year is never invented (R-D9)
     */
    public Optional<MonthDay> asMonthDay(FieldDescriptor field) {
        return MonthDays.parse(asPaddedString(field));
    }

    /**
     * Decodes a コード区分 field.
     *
     * @param field the field to decode
     * @return the encoding indicator
     */
    public CodeKubun asCodeKubun(FieldDescriptor field) {
        return CodeKubun.of(asPaddedString(field));
    }

    /**
     * Returns a copy of a field's bytes.
     *
     * @param field the field
     * @return the bytes, padding included
     */
    public byte[] asBytes(FieldDescriptor field) {
        checkValid();
        checkField(field);
        return Arrays.copyOfRange(buffer, offset + field.offset(), offset + field.endOffset());
    }

    /**
     * Returns a copy of the record's bytes.
     *
     * @return the raw bytes
     * @throws StaleRecordViewException if the reader has advanced past this
     *                                  record
     */
    public byte[] rawBytes() {
        checkValid();
        return Arrays.copyOfRange(buffer, offset, offset + length);
    }

    /**
     * Copies this record into an immutable value that outlives the buffer.
     *
     * @return the record: a generated, format-shaped type where one exists,
     *         a descriptor-driven fallback otherwise, or a
     *         {@link MalformedRecord} if the record could not be interpreted
     * @throws MalformedFieldException  if a typed field cannot be decoded, for
     *                                  example a non-numeric amount
     * @throws StaleRecordViewException if the reader has advanced past this
     *                                  record
     */
    public ZenginRecord materialize() {
        checkValid();
        if (descriptor == null) {
            return new MalformedRecord(format.id(), recordNumber, byteOffset, rawBytes(), malformedReason);
        }
        return RecordMaterializers.forFormat(format.id())
                .orElse(GenericRecordFactory.INSTANCE)
                .materialize(this);
    }

    private void checkValid() {
        if (!isValid()) {
            throw new StaleRecordViewException(recordNumber);
        }
    }

    private int checkField(FieldDescriptor field) {
        RecordDescriptor record = requireDescriptor();
        int index = field.sequence() - 1;
        if (index < 0 || index >= record.fields().size() || record.fields().get(index) != field) {
            throw new IllegalArgumentException("field '" + field.id() + "' does not belong to the "
                    + record.kind() + " record of format " + format.id());
        }
        if (field.endOffset() > length) {
            throw new IllegalArgumentException("field '" + field.id() + "' ends at byte " + field.endOffset()
                    + " but this record is only " + length + " bytes long");
        }
        return index;
    }
}
