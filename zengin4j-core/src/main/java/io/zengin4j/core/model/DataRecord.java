package io.zengin4j.core.model;

import io.zengin4j.core.format.RecordKind;

/**
 * A data record: one payment, collection or notification line.
 *
 * @since 0.1.0
 */
public non-sealed interface DataRecord extends ZenginRecord {

    @Override
    default RecordKind kind() {
        return RecordKind.DATA;
    }

    /**
     * Returns the record's monetary amount in whole yen.
     *
     * <p>{@code long}, not {@code BigDecimal}: yen has no minor unit, and the
     * field is {@code N(10)}, so the maximum representable value is
     * ¥9,999,999,999 (R-D6).
     *
     * @return the amount in yen
     */
    long amount();
}
