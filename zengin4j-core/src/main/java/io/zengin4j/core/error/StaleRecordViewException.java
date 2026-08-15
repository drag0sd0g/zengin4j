package io.zengin4j.core.error;

/**
 * A {@link io.zengin4j.core.codec.RecordView RecordView} was accessed after the
 * reader advanced past it.
 *
 * <p>Record views are windows onto a recycled buffer (R-MEM2). Retaining one
 * across a call to {@code next()} would silently return the <em>next</em>
 * record's bytes — a defect that produces plausible values and no error. This
 * exception converts that silent corruption into an immediate, located failure.
 *
 * <p>The fix is always the same: call
 * {@link io.zengin4j.core.codec.RecordView#materialize() materialize()} on any
 * record you intend to keep, or use the {@code BatchReader} API, which
 * materialises by default (R-MEM5).
 *
 * @since 0.1.0
 */
public final class StaleRecordViewException extends ZenginException {

    private final int recordNumber;

    /**
     * Creates a stale-view diagnostic.
     *
     * @param recordNumber the 1-based record number the stale view refers to
     */
    public StaleRecordViewException(int recordNumber) {
        super("the view of record " + recordNumber + " is no longer valid: the reader's buffer has been"
                        + " advanced. A RecordView is valid only until the next call to next(). Call"
                        + " materialize() on any record you need to retain.",
                "レコード " + recordNumber + " のビューは既に無効です。読み取りバッファが次のレコードに"
                        + "進んでいます。RecordView は次の next() 呼び出しまでのみ有効です。保持する必要がある"
                        + "場合は materialize() を呼び出してください。");
        this.recordNumber = recordNumber;
    }

    /**
     * Returns the record number the stale view referred to.
     *
     * @return the 1-based record number
     */
    public int recordNumber() {
        return recordNumber;
    }
}
