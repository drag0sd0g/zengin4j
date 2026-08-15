package io.zengin4j.core.codec;

import java.util.Objects;

/**
 * Something the reader noticed and worked around.
 *
 * <p>Warnings are not findings. A finding says a file violates the format and
 * belongs to the validation layer; a warning says the reader made a decision
 * the caller might want to know about — it stripped a byte order mark, it
 * accepted a provisional format definition.
 *
 * <p>Every warning is collected on the reader and also handed to the listener
 * configured in {@link ReaderOptions}. The default listener writes one line
 * through {@link System.Logger}, which is part of {@code java.base} and so
 * costs the module no dependency; replace it to route warnings anywhere else.
 *
 * @param code       a stable identifier, suitable for suppression rules
 * @param messageEn  the English text
 * @param messageJa  the Japanese text
 * @param byteOffset where in the file the warning applies, or {@code -1}
 * @since 0.1.0
 */
public record ZenginWarning(String code, String messageEn, String messageJa, long byteOffset) {

    /** A UTF-8 byte order mark was found at the start of the file and skipped. */
    public static final String BYTE_ORDER_MARK_STRIPPED = "W-BOM-STRIPPED";

    /** A format whose layout is unconfirmed was used, with the caller's consent. */
    public static final String UNVERIFIED_FORMAT = "W-UNVERIFIED-FORMAT";

    /** Bytes followed the end-of-file marker and were ignored. */
    public static final String DATA_AFTER_EOF_BYTE = "W-DATA-AFTER-EOF";

    /** The file used more than one record separator convention. */
    public static final String MIXED_SEPARATORS = "W-MIXED-SEPARATORS";

    private static final System.Logger LOGGER = System.getLogger("io.zengin4j");

    /**
     * Validates the components.
     */
    public ZenginWarning {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(messageEn, "messageEn");
        Objects.requireNonNull(messageJa, "messageJa");
    }

    /**
     * Writes this warning through {@link System.Logger} at {@code WARNING}
     * level. The default listener.
     */
    public void log() {
        LOGGER.log(System.Logger.Level.WARNING, "{0}: {1}", code, messageEn);
    }
}
