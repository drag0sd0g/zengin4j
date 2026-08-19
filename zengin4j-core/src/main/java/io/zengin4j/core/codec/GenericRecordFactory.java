package io.zengin4j.core.codec;

import module java.base;
import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.GenericDataRecord;
import io.zengin4j.core.model.GenericEndRecord;
import io.zengin4j.core.model.GenericHeaderRecord;
import io.zengin4j.core.model.GenericTrailerRecord;
import io.zengin4j.core.model.ZenginRecord;

/// Materialises records for formats that have no generated types — descriptors
/// a consumer registered at runtime.
///
/// Fields that the role interfaces promise but a descriptor may reasonably
/// omit degrade to an explicit absence: no 委託者名 field yields an empty name,
/// not a fabricated one. Fields that cannot degrade honestly are required: a
/// data record has to say which of its fields is the amount, because
/// `long amount()` has no value that means "there wasn't one".
final class GenericRecordFactory implements RecordFactory {

    static final GenericRecordFactory INSTANCE = new GenericRecordFactory();

    private GenericRecordFactory() {
    }

    @Override
    public ZenginRecord materialize(RecordView view) {
        RecordDescriptor record = view.requireDescriptor();
        Map<String, String> values = new LinkedHashMap<>();
        for (FieldDescriptor field : record.fields()) {
            values.put(field.id(), view.asString(field));
        }
        byte[] rawBytes = view.rawBytes();
        int number = view.recordNumber();
        long offset = view.byteOffset();

        return switch (record.kind()) {
            case HEADER -> new GenericHeaderRecord(record, number, offset, rawBytes, values,
                    codeKubun(view, record), text(view, record, "originatorCode"),
                    text(view, record, "originatorName"), valueDate(view, record));
            case DATA -> new GenericDataRecord(record, number, offset, rawBytes, values,
                    view.asLong(require(record, FieldFormat.AMOUNT, "amount()")));
            case TRAILER -> new GenericTrailerRecord(record, number, offset, rawBytes, values,
                    Math.toIntExact(view.asLong(require(record, FieldFormat.COUNT, "recordCount()"))),
                    view.asLong(require(record, FieldFormat.AMOUNT, "totalAmount()")));
            case END -> new GenericEndRecord(record, number, offset, rawBytes, values);
            case MALFORMED -> throw new IllegalStateException("a malformed record has no descriptor");
        };
    }

    private static FieldDescriptor require(RecordDescriptor record, FieldFormat format, String accessor) {
        return record.findByFormat(format).orElseThrow(() -> FormatDescriptorException.forFormat(
                record.formatId().value(),
                "the " + record.kind() + " record declares no field with format: " + format.descriptorValue()
                        + ", so " + accessor + " cannot be answered. Add the attribute to the field that"
                        + " carries it."));
    }

    private static CodeKubun codeKubun(RecordView view, RecordDescriptor record) {
        return record.findByFormat(FieldFormat.CODE_KUBUN)
                .map(view::asCodeKubun)
                .orElse(CodeKubun.UNKNOWN);
    }

    private static Optional<MonthDay> valueDate(RecordView view, RecordDescriptor record) {
        return record.findByFormat(FieldFormat.MMDD).flatMap(view::asMonthDay);
    }

    private static String text(RecordView view, RecordDescriptor record, String fieldId) {
        return record.find(fieldId).map(view::asString).orElse("");
    }
}
