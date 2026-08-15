package io.zengin4j.codegen;

/**
 * A descriptor cannot be turned into code, or the committed output has drifted.
 *
 * <p>Always a build failure. This is the mechanism that stops a
 * hand-transcribed byte layout from reaching a payment file (R-F1).
 */
final class CodegenException extends RuntimeException {

    CodegenException(String message) {
        super(message);
    }

    CodegenException(String message, Throwable cause) {
        super(message, cause);
    }
}
