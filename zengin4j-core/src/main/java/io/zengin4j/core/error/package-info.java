/**
 * The exception hierarchy (§17).
 *
 * <p>Two rules govern this package. Everything extends
 * {@link io.zengin4j.core.error.ZenginException}, which extends
 * {@code RuntimeException}, so the public API has no checked exceptions
 * (R-E2). And exceptions are reserved for programmer error and unrecoverable
 * I/O: malformed third-party input is data, surfaced as
 * {@link io.zengin4j.core.model.MalformedRecord} or as a validation finding
 * (R-E1).
 *
 * @since 0.1.0
 */
package io.zengin4j.core.error;
