package io.zengin4j.core.codec;

/**
 * What the reader does when a record does not fit the format.
 *
 * @since 0.1.0
 */
public enum ParseMode {
    /**
     * Stop at the first problem with a located diagnostic.
     *
     * <p>The default. Appropriate when the file is expected to be well formed
     * and a surprise should halt processing.
     */
    STRICT,

    /**
     * Emit a {@link io.zengin4j.core.model.MalformedRecord} and carry on,
     * resynchronising by exactly one record length (R-C3).
     *
     * <p>Appropriate for diagnostics: one bad record should not hide the
     * other 9,999 (R-D8). Resynchronisation never scans for a plausible
     * discriminator byte — the record length is known, so the next boundary is
     * arithmetic, and scanning would risk locking on to a byte inside a name
     * field.
     */
    LENIENT
}
