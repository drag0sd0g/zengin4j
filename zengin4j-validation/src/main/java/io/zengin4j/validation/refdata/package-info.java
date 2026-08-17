/**
 * Bank and branch reference data, supplied by the caller (R-V5).
 *
 * <p>Optional and pluggable, and the library is complete without it: with no
 * provider the {@code V-4xx} rules do not run and nothing else changes.
 *
 * <p>No snapshot ships. Institution data goes stale — banks merge, branches
 * close, codes move — and a copy compiled into a released jar would look
 * authoritative while being wrong, on a question where being wrong means a
 * payment goes nowhere.
 *
 * @since 0.2.0
 */
package io.zengin4j.validation.refdata;
