/**
 * The codec: framing, field decoding and the readers (§12).
 *
 * <p>Two reading APIs with two contracts. {@link io.zengin4j.core.codec.ZenginReader}
 * hands out views onto a recycled buffer: nothing is copied, nothing is decoded
 * until asked for, and a view stops being valid the moment the reader advances.
 * {@link io.zengin4j.core.codec.BatchReader} materialises everything into
 * immutable values, and is the one to reach for unless the profiler says
 * otherwise.
 *
 * <p>Record length is a property of the format descriptor, never a constant in
 * this package (R-C4). Separators between records are optional and are not
 * counted in the record length (R-C6, R-C7).
 *
 * @since 0.1.0
 */
package io.zengin4j.core.codec;
