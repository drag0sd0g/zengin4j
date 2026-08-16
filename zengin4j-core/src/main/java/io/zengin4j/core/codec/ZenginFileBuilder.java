package io.zengin4j.core.codec;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.error.AmountOverflowException;
import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.error.UnverifiedFormatException;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.EndRecord;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.HeaderRecord;
import io.zengin4j.core.model.TrailerRecord;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.core.time.MonthDays;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Builds a {@link ZenginFile} from field values, computing the trailers.
 *
 * <pre>{@code
 * ZenginFile file = ZenginFileBuilder.forFormat(descriptor)
 *         .header(h -> h.set("originatorCode", "9900000001")
 *                       .set("originatorName", "ﾃｽﾄｼﾖｳｼﾞ")
 *                       .set("valueDate", MonthDay.of(9, 30)))
 *         .payment(p -> p.set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ")
 *                        .set("amount", 150_000L))
 *         .build();
 * }</pre>
 *
 * <p>Each record is encoded to bytes as it is added, then materialised through
 * the same path a reader uses — so a built file is indistinguishable from a
 * read one, and carries the format-shaped generated types rather than a
 * parallel representation. That is what makes INV-2 mean something.
 *
 * <p><strong>Trailers are computed, not supplied.</strong> The record count and
 * total come from the data records actually added, so a file cannot be built
 * whose trailer disagrees with its contents by accident. It can be built that
 * way on purpose — {@link #trailer(Consumer)} overrides the computed values,
 * which is how you produce a fixture for a validation rule that has to catch
 * exactly that.
 *
 * <p>Fields are addressed by descriptor id rather than by typed setter. Typed,
 * generated builders would be a better public API and are not in this epic's
 * scope; the descriptor is the source of truth either way, and an unknown id
 * is rejected rather than ignored.
 *
 * <p><strong>Stateful and not thread-safe</strong> (R-T2). One builder per
 * thread, and one file per builder.
 *
 * @since 0.1.0
 */
public final class ZenginFileBuilder {

    private final FormatDescriptor descriptor;
    private final ViewGeneration generation = new ViewGeneration();

    private ZenginCharset charset = ZenginCharset.defaultCharset();
    private FileFraming framing = FileFraming.conventional();
    private boolean allowUnverifiedFormats;

    private final List<PendingBatch> batches = new ArrayList<>();
    private PendingBatch current;
    private Map<String, String> endValues = new LinkedHashMap<>();

    private ZenginFileBuilder(FormatDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    /**
     * Starts a builder for a format.
     *
     * @param descriptor the format to build
     * @return a new builder
     */
    public static ZenginFileBuilder forFormat(FormatDescriptor descriptor) {
        return new ZenginFileBuilder(descriptor);
    }

    /**
     * Permits building on a format whose byte layout is not verified.
     *
     * <p>Off by default, mirroring
     * {@link ReaderOptions#allowUnverifiedFormats()} — and it matters more here
     * than it does there. A wrong offset when reading puts wrong data in the
     * caller's own system, where their reconciliation may catch it. A wrong
     * offset when writing puts a wrong payment instruction in front of a bank,
     * where nothing will.
     *
     * <p>The opt-in exists so that the decision to place real values at
     * provisional offsets is written down in the caller's own code, where a
     * reviewer sees it, rather than taken on their behalf.
     *
     * @param value whether to allow an unverified descriptor
     * @return this builder
     * @since 0.1.0
     */
    public ZenginFileBuilder allowUnverifiedFormats(boolean value) {
        this.allowUnverifiedFormats = value;
        return this;
    }

    /**
     * Sets the encoding text fields are written in.
     *
     * @param value the charset
     * @return this builder
     */
    public ZenginFileBuilder charset(ZenginCharset value) {
        this.charset = Objects.requireNonNull(value, "charset");
        return this;
    }

    /**
     * Sets the framing the file records, which the writer reproduces.
     *
     * @param value the framing; {@link FileFraming#conventional()} by default
     * @return this builder
     */
    public ZenginFileBuilder framing(FileFraming value) {
        this.framing = Objects.requireNonNull(value, "framing");
        return this;
    }

    /**
     * Opens a batch with a header record.
     *
     * <p>Calling this again closes the current batch and starts another, which
     * the parser accepts even where a particular institution forbids it
     * (R-C1).
     *
     * @param values sets the header's fields
     * @return this builder
     */
    public ZenginFileBuilder header(Consumer<FieldValues> values) {
        RecordDescriptor header = descriptor.record(RecordKind.HEADER);
        FieldValues collected = new FieldValues(header);
        values.accept(collected);
        current = new PendingBatch(collected.values());
        batches.add(current);
        return this;
    }

    /**
     * Adds a data record to the current batch.
     *
     * @param values sets the record's fields
     * @return this builder
     * @throws IllegalStateException if no header has been added yet
     */
    public ZenginFileBuilder payment(Consumer<FieldValues> values) {
        if (current == null) {
            throw new IllegalStateException("a data record must follow a header; call header(...) first");
        }
        FieldValues collected = new FieldValues(descriptor.record(RecordKind.DATA));
        values.accept(collected);
        current.data.add(collected.values());
        return this;
    }

    /**
     * Overrides fields of the current batch's trailer.
     *
     * <p>The record count and total are computed from the data records; values
     * set here replace them. Use it to build a file whose trailer deliberately
     * disagrees with its contents.
     *
     * @param values sets the trailer's fields
     * @return this builder
     * @throws IllegalStateException if no header has been added yet
     */
    public ZenginFileBuilder trailer(Consumer<FieldValues> values) {
        if (current == null) {
            throw new IllegalStateException("a trailer must follow a header; call header(...) first");
        }
        FieldValues collected = new FieldValues(descriptor.record(RecordKind.TRAILER));
        values.accept(collected);
        current.trailerOverrides.putAll(collected.values());
        return this;
    }

    /**
     * Overrides fields of the end record.
     *
     * @param values sets the end record's fields
     * @return this builder
     */
    public ZenginFileBuilder endRecord(Consumer<FieldValues> values) {
        FieldValues collected = new FieldValues(descriptor.record(RecordKind.END));
        values.accept(collected);
        endValues = collected.values();
        return this;
    }

    /**
     * Builds the file.
     *
     * @return the materialised file, with computed trailers and an end record
     *         if the format declares one
     * @throws IllegalStateException      if no header was added
     * @throws UnverifiedFormatException  if the format's byte layout is not
     *                                    verified and
     *                                    {@link #allowUnverifiedFormats(boolean)}
     *                                    was not set
     * @throws AmountOverflowException    if a batch's amounts do not fit a
     *                                    {@code long} (R-D7)
     * @throws FormatDescriptorException  if the format lacks a record kind the
     *                                    build needs
     */
    public ZenginFile build() {
        if (batches.isEmpty()) {
            throw new IllegalStateException("a file needs at least one header record");
        }
        // Checked here rather than in ZenginWriters, because this is where
        // values are placed at descriptor-defined offsets — the step an
        // unverified layout can get wrong. A file the reader produced already
        // passed the equivalent gate, and writing it back reproduces bytes that
        // already existed, so the writer re-asking would add friction to the
        // one path that introduces no risk.
        if (!descriptor.verified() && !allowUnverifiedFormats) {
            throw new UnverifiedFormatException(descriptor.id().value(),
                    UnverifiedFormatException.Operation.BUILDING);
        }

        RecordDescriptor headerDescriptor = descriptor.record(RecordKind.HEADER);
        RecordDescriptor dataDescriptor = descriptor.record(RecordKind.DATA);
        Optional<RecordDescriptor> trailerDescriptor = descriptor.find(RecordKind.TRAILER);

        Cursor cursor = new Cursor();
        List<Batch> built = new ArrayList<>(batches.size());

        for (PendingBatch pending : batches) {
            HeaderRecord header = (HeaderRecord) materialise(headerDescriptor, pending.header, cursor);

            List<DataRecord> data = new ArrayList<>(pending.data.size());
            for (Map<String, String> values : pending.data) {
                data.add((DataRecord) materialise(dataDescriptor, values, cursor));
            }

            Optional<TrailerRecord> trailer = trailerDescriptor.map(td ->
                    (TrailerRecord) materialise(td, trailerValues(td, data, pending.trailerOverrides), cursor));

            built.add(new Batch(header, data, trailer, List.of()));
        }

        Optional<EndRecord> end = descriptor.find(RecordKind.END)
                .map(ed -> (EndRecord) materialise(ed, endValues, cursor));

        return new ZenginFile(descriptor, built, end, List.of(), framing);
    }

    /**
     * Computes 合計件数 and 合計金額 from the records actually present, then
     * applies any explicit overrides.
     */
    private Map<String, String> trailerValues(
            RecordDescriptor trailer, List<DataRecord> data, Map<String, String> overrides) {

        Map<String, String> values = new LinkedHashMap<>();
        trailer.findByFormat(FieldFormat.COUNT)
                .ifPresent(field -> values.put(field.id(), Integer.toString(data.size())));
        trailer.findByFormat(FieldFormat.AMOUNT)
                .ifPresent(field -> values.put(field.id(), Long.toString(total(data))));
        values.putAll(overrides);
        return values;
    }

    private static long total(List<DataRecord> data) {
        long total = 0;
        for (DataRecord record : data) {
            try {
                total = Math.addExact(total, record.amount());
            } catch (ArithmeticException e) {
                throw new AmountOverflowException(record.recordNumber());
            }
        }
        return total;
    }

    private ZenginRecord materialise(RecordDescriptor record, Map<String, String> values, Cursor cursor) {
        byte[] frame = RecordEncoder.encode(record, charset, values);
        int number = cursor.nextRecordNumber();
        long offset = cursor.nextOffset(frame.length, framing);
        RecordView view = RecordView.wellFormed(frame, 0, frame.length, descriptor, record,
                charset, offset, number, generation);
        return view.materialize();
    }

    /** Assigns record numbers and the byte offsets the configured framing implies. */
    private static final class Cursor {

        private int number;
        private long offset;

        int nextRecordNumber() {
            return ++number;
        }

        long nextOffset(int recordLength, FileFraming framing) {
            long current = offset;
            offset += recordLength + framing.separator().bytes().map(bytes -> bytes.length).orElse(0);
            return current;
        }
    }

    private static final class PendingBatch {

        private final Map<String, String> header;
        private final List<Map<String, String>> data = new ArrayList<>();
        private final Map<String, String> trailerOverrides = new LinkedHashMap<>();

        private PendingBatch(Map<String, String> header) {
            this.header = header;
        }
    }

    /**
     * Collects field values for one record, checking each id against the
     * record's layout as it is set.
     *
     * @since 0.1.0
     */
    public static final class FieldValues {

        private final RecordDescriptor descriptor;
        private final Map<String, String> values = new LinkedHashMap<>();

        private FieldValues(RecordDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        /**
         * Sets a field to text.
         *
         * @param fieldId the field id
         * @param value   the value; padded to the field width on encoding
         * @return this collector
         * @throws FormatDescriptorException if the record has no such field
         */
        public FieldValues set(String fieldId, String value) {
            requireField(fieldId);
            values.put(fieldId, Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Sets a numeric field, zero padded on the left to the field width.
         *
         * @param fieldId the field id
         * @param value   the value; must not be negative
         * @return this collector
         * @throws IllegalArgumentException if the value is negative
         */
        public FieldValues set(String fieldId, long value) {
            if (value < 0) {
                throw new IllegalArgumentException(
                        "field '" + fieldId + "' cannot carry a negative value: " + value);
            }
            requireField(fieldId);
            values.put(fieldId, Long.toString(value));
            return this;
        }

        /**
         * Sets an {@code MMDD} field.
         *
         * @param fieldId the field id
         * @param value   the month and day
         * @return this collector
         */
        public FieldValues set(String fieldId, MonthDay value) {
            requireField(fieldId);
            values.put(fieldId, MonthDays.format(Objects.requireNonNull(value, "value")));
            return this;
        }

        /** Exists to throw: a field id that is not in the layout is a mistake, not a new field. */
        private void requireField(String fieldId) {
            descriptor.field(fieldId);
        }

        private Map<String, String> values() {
            return values;
        }
    }
}
