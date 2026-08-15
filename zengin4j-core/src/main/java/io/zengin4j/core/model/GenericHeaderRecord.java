package io.zengin4j.core.model;

import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.format.RecordDescriptor;
import java.time.MonthDay;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Descriptor-driven fallback for a header record.
 *
 * @since 0.1.0
 */
public final class GenericHeaderRecord extends GenericRecord implements HeaderRecord {

    private final CodeKubun codeKubun;
    private final String originatorCode;
    private final String originatorName;
    private final Optional<MonthDay> valueDate;

    /**
     * Creates a fallback header record.
     *
     * @param descriptor     the layout the record was decoded with
     * @param recordNumber   the 1-based position of the record in the file
     * @param byteOffset     the record's byte offset within the file
     * @param rawBytes       the record's bytes
     * @param values         decoded field values keyed by field id
     * @param codeKubun      the decoded コード区分
     * @param originatorCode the decoded 委託者コード, or an empty string
     * @param originatorName the decoded 委託者名, or an empty string
     * @param valueDate      the decoded date, or empty
     */
    public GenericHeaderRecord(
            RecordDescriptor descriptor,
            int recordNumber,
            long byteOffset,
            byte[] rawBytes,
            Map<String, String> values,
            CodeKubun codeKubun,
            String originatorCode,
            String originatorName,
            Optional<MonthDay> valueDate) {
        super(descriptor, recordNumber, byteOffset, rawBytes, values);
        this.codeKubun = Objects.requireNonNull(codeKubun, "codeKubun");
        this.originatorCode = Objects.requireNonNull(originatorCode, "originatorCode");
        this.originatorName = Objects.requireNonNull(originatorName, "originatorName");
        this.valueDate = Objects.requireNonNull(valueDate, "valueDate");
    }

    @Override
    public CodeKubun codeKubun() {
        return codeKubun;
    }

    @Override
    public String originatorCode() {
        return originatorCode;
    }

    @Override
    public String originatorName() {
        return originatorName;
    }

    @Override
    public Optional<MonthDay> valueDate() {
        return valueDate;
    }
}
