package io.zengin4j.core.error;

import java.io.IOException;

/**
 * An unrecoverable I/O failure while reading or writing a Zengin file.
 *
 * <p>Wraps the checked {@link IOException} so that the public API stays free of
 * checked exceptions (R-0.9). The original exception is always available via
 * {@link #getCause()}.
 *
 * @since 0.1.0
 */
public final class ZenginIOException extends ZenginException {

    /**
     * Wraps an I/O failure.
     *
     * @param what  a short description of the operation that failed, for
     *              example {@code "reading record 42"}
     * @param cause the underlying I/O exception, never {@code null}
     */
    public ZenginIOException(String what, IOException cause) {
        super("I/O failure while " + what + ": " + cause.getMessage(),
                "入出力エラーが発生しました (" + what + "): " + cause.getMessage(),
                cause);
    }
}
