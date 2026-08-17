package io.zengin4j.core.model;

import io.zengin4j.core.format.RecordKind;

/**
 * An end record: closes the file.
 *
 * <p>Carries no information beyond its own presence. It is modelled anyway
 * because its bytes must survive a round trip verbatim, filler included
 * (R-D5), and because its absence is a validation finding rather than a parse
 * failure (R-C2).
 *
 * @since 0.1.0
 */
public non-sealed interface EndRecord extends ZenginRecord {
    @Override
    default RecordKind kind() {
        return RecordKind.END;
    }

    /**
     * Returns every byte of the record after the データ区分 discriminator.
     *
     * <p>Returns a fresh copy on every call.
     *
     * @return the filler bytes
     */
    byte[] filler();
}
