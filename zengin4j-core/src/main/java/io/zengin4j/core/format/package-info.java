/**
 * Format descriptors: the byte layouts, as data.
 *
 * <p>Everything the codec knows about a format lives in a descriptor loaded
 * from YAML (§13). Byte offsets are computed from cumulative field lengths
 * and never transcribed (R-F2); field lengths must sum exactly to the record
 * length (R-F1); and every descriptor carries a {@code verified} flag and its
 * citations, so a caller can tell a confirmed layout from a provisional one
 * (§0.3).
 *
 * @since 0.1.0
 */
package io.zengin4j.core.format;
