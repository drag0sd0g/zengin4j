package io.zengin4j.core.codec;

import io.zengin4j.core.model.ZenginRecord;

/**
 * Turns a record view into an immutable record.
 *
 * <p>Each format's generated code supplies one of these, so that
 * {@link RecordView#materialize()} produces the format-shaped type
 * ({@code SougouFurikomiData} and friends) rather than a map of strings.
 * Formats registered at runtime, which have no generated code, fall back to
 * the descriptor-driven {@code Generic*} records.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface RecordFactory {

    /**
     * Materialises the record the view is positioned on.
     *
     * @param view the view; its bytes are copied, so the result outlives it
     * @return the immutable record
     */
    ZenginRecord materialize(RecordView view);
}
