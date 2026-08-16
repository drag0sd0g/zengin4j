/**
 * The codec: framing, field coding, the readers and the writer (§12).
 *
 * <p>Two reading APIs with two contracts. {@link io.zengin4j.core.codec.ZenginReader}
 * hands out views onto a recycled buffer: nothing is copied, nothing is decoded
 * until asked for, and a view stops being valid the moment the reader advances.
 * {@link io.zengin4j.core.codec.BatchReader} materialises everything into
 * immutable values, and is the one to reach for unless the profiler says
 * otherwise.
 *
 * <p>Writing is the reverse and deliberately not symmetrical.
 * {@link io.zengin4j.core.codec.ZenginWriters} emits each record from the raw
 * bytes it carries rather than re-encoding it from decoded fields (R-D5), which
 * is what makes a read-then-write round trip byte-exact rather than merely
 * equivalent. {@link io.zengin4j.core.codec.ZenginFileBuilder} is for files that
 * did not come from bytes in the first place; it encodes through
 * {@link io.zengin4j.core.codec.RecordEncoder} and then materialises through the
 * same path a reader uses, so a built file and a read one are the same kind of
 * thing.
 *
 * <p>Record length is a property of the format descriptor, never a constant in
 * this package (R-C4). Separators between records are optional and are not
 * counted in the record length (R-C6, R-C7); whether one followed the final
 * record is part of the file and is reproduced (R-C9).
 *
 * @since 0.1.0
 */
package io.zengin4j.core.codec;
