package io.zengin4j.core.model;

import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A whole Zengin file, materialised.
 *
 * <p>Produced by the batch-oriented reader, which materialises by default
 * (R-MEM5). For files large enough that holding them in memory matters, use
 * the streaming reader instead.
 *
 * @param descriptor the layout the file was read with
 * @param batches    the batches, in file order
 * @param endRecord  the end record; empty when the file was truncated, which
 *                   is a validation finding rather than a parse failure
 *                   (R-C2)
 * @param unbatched  malformed records that fell outside any batch — before
 *                   the first header, or after the end record
 * @param framing    how the file was framed
 * @since 0.1.0
 */
public record ZenginFile(
        FormatDescriptor descriptor,
        List<Batch> batches,
        Optional<EndRecord> endRecord,
        List<MalformedRecord> unbatched,
        FileFraming framing) {
    /**
     * Validates and defensively copies the components.
     */
    public ZenginFile {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(endRecord, "endRecord");
        Objects.requireNonNull(framing, "framing");
        batches = List.copyOf(Objects.requireNonNull(batches, "batches"));
        unbatched = List.copyOf(Objects.requireNonNull(unbatched, "unbatched"));
    }

    /**
     * Returns the id of the format the file was read with.
     *
     * @return the format id
     */
    public FormatId format() {
        return descriptor.id();
    }

    /**
     * Returns the total number of records in the file.
     *
     * @return the record count, including headers, trailers, the end record
     *         and any malformed records
     */
    public int totalRecords() {
        int total = unbatched.size() + (endRecord.isPresent() ? 1 : 0);
        for (Batch batch : batches) {
            total += batch.totalRecords();
        }
        return total;
    }

    /**
     * Returns every data record in the file, in file order.
     *
     * @return the data records
     */
    public List<DataRecord> allData() {
        return batches.stream().flatMap(batch -> batch.data().stream()).toList();
    }
}
