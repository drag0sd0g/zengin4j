/**
 * Checking a Zengin file and reporting what is wrong with it (§14).
 *
 * <p>{@link io.zengin4j.validation.ZenginValidator} is the entry point.
 * Validation <strong>returns a report and never throws</strong> (R-V1):
 * malformed third-party files are this module's expected input, and a validator
 * that failed on one would be useless for its only job.
 *
 * @since 0.2.0
 */
package io.zengin4j.validation;
