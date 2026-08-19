package io.zengin4j.core.model;

import module java.base;
import io.zengin4j.core.error.AmountOverflowException;

/// One header, its data records and its trailer.
///
/// A file may carry several batches even where a particular institution
/// forbids it: the parser accepts them and the validation layer decides
/// whether they are allowed (R-C1).
///
/// @param header    the record that opened the batch
/// @param data      the data records, in file order
/// @param trailer   the record that closed the batch; empty when the file was
///   truncated, which is a validation finding rather than a
///   parse failure (R-C2)
/// @param malformed records inside this batch that could not be interpreted;
///   always empty in strict mode
/// @since 0.1.0
public record Batch(
        HeaderRecord header,
        List<DataRecord> data,
        Optional<TrailerRecord> trailer,
        List<MalformedRecord> malformed) {

    /// Validates and defensively copies the components.
    public Batch {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(trailer, "trailer");
        data = List.copyOf(Objects.requireNonNull(data, "data"));
        malformed = List.copyOf(Objects.requireNonNull(malformed, "malformed"));
    }

    /// Returns the number of data records actually present.
    ///
    /// Compare with `trailer().recordCount()` to check the file's own
    /// claim; this method does not do that comparison, because a mismatch is a
    /// finding to report rather than an error to raise (R-V1).
    ///
    /// @return the data record count
    public int computedCount() {
        return data.size();
    }

    /// Returns the sum of the data records' amounts, in whole yen.
    ///
    /// @return the total
    /// @throws AmountOverflowException if the sum exceeds a `long`; the
    ///   total is never silently wrapped (R-D7)
    public long computedTotal() {
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

    /// Reports whether the batch was closed by a trailer.
    ///
    /// @return `true` if a trailer is present
    public boolean isComplete() {
        return trailer.isPresent();
    }

    /// Returns the number of records this batch occupies in the file,
    /// including the header, any trailer and any malformed records.
    ///
    /// @return the record count
    public int totalRecords() {
        return 1 + data.size() + malformed.size() + (trailer.isPresent() ? 1 : 0);
    }
}
