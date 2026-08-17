package io.zengin4j.core.error;

/**
 * Summing the amounts in a batch overflowed a {@code long}.
 *
 * <p>R-D7 requires that an overflow is detected and reported, never silently
 * wrapped: a wrapped total is a negative number presented as a control sum,
 * which is exactly the kind of value that gets waved through. Reaching this
 * condition requires roughly a billion maximum-value records, so in practice
 * it indicates a corrupt or adversarial file rather than a large one.
 *
 * <p>The validation layer computes totals with its own overflow-tolerant loop
 * and reports a finding instead (§19.3, rule {@code V-303}); this exception is
 * for callers who ask {@code core} for the sum directly.
 *
 * @since 0.1.0
 */
public final class AmountOverflowException extends ZenginException {
    private final int recordNumber;

    /**
     * Creates an overflow diagnostic.
     *
     * @param recordNumber the 1-based record number at which the running total
     *                     overflowed
     */
    public AmountOverflowException(int recordNumber) {
        super("summing batch amounts overflowed a 64-bit signed integer at record " + recordNumber
                        + ". The file's amounts cannot be totalled; treat it as corrupt.",
                "レコード " + recordNumber + " で合計金額が 64 ビット符号付き整数の範囲を超えました。"
                        + "このファイルの金額は合計できません。破損データとして扱ってください。");
        this.recordNumber = recordNumber;
    }

    /**
     * Returns the record number at which the running total overflowed.
     *
     * @return the 1-based record number
     */
    public int recordNumber() {
        return recordNumber;
    }
}
