package io.zengin4j.core.codec;

import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.EndRecord;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.MalformedRecord;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Reads a Zengin file one batch at a time, materialising every record.
 *
 * <p>The safe half of the pair (R-MEM5). Records returned here are immutable
 * values that outlive the reader, so there is nothing to remember about buffer
 * lifetimes; the cost is a copy per record. {@link ZenginReader} is the fast
 * half, and it is the one that needs care.
 *
 * <p><strong>The more convenient API defaults to the safer behaviour</strong>
 * on purpose.
 *
 * <p>Stateful and not thread-safe (R-T2).
 *
 * @since 0.1.0
 */
public interface BatchReader extends AutoCloseable {
    /**
     * Returns the format the file is being read as.
     *
     * @return the format descriptor
     */
    FormatDescriptor format();

    /**
     * Reports whether another batch is available.
     *
     * @return {@code true} if {@link #next()} will return a batch
     */
    boolean hasNext();

    /**
     * Returns the next batch.
     *
     * @return the batch
     * @throws NoSuchElementException if no batch remains
     */
    Batch next();

    /**
     * Returns the end record.
     *
     * <p>Meaningful only once iteration has finished. An empty result then
     * means the file was truncated, which is a validation finding rather than
     * a parse failure (R-C2).
     *
     * @return the end record, or empty
     */
    Optional<EndRecord> endRecord();

    /**
     * Returns malformed records that fell outside any batch.
     *
     * @return an unmodifiable list, in file order
     */
    List<MalformedRecord> unbatched();

    /**
     * Returns how the file was framed.
     *
     * <p>Complete only once iteration has finished.
     *
     * @return the framing observed so far
     */
    FileFraming framing();

    /**
     * Returns the warnings raised so far.
     *
     * @return an unmodifiable list, oldest first
     */
    List<ZenginWarning> warnings();

    /**
     * Closes the underlying reader and stream.
     *
     * @throws io.zengin4j.core.error.ZenginIOException if closing fails
     */
    @Override
    void close();
}
