package io.zengin4j.core.codec;

import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.generated.GeneratedRecords;
import java.util.Optional;

/**
 * Finds the generated, format-shaped materialiser for a format.
 *
 * <p>The single point where the codec meets the generated code. Everything
 * else in this package works from descriptors alone.
 */
final class RecordMaterializers {

    private RecordMaterializers() {
    }

    /**
     * Returns the generated factory for a format.
     *
     * @param id the format id
     * @return the factory, or empty for a format registered at runtime, which
     *         by definition has no generated code
     */
    static Optional<RecordFactory> forFormat(FormatId id) {
        return GeneratedRecords.forFormat(id);
    }
}
