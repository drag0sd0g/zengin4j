package io.zengin4j.core.codec;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a record's bytes from field values.
 *
 * <p>Fields left unset take their declared constant, or the pad byte their
 * type prescribes: zeros for {@code N}, spaces for {@code C}. An unknown field
 * id is rejected rather than ignored — silently dropping a misspelled field
 * would produce a record that is quietly missing a value, which is the class
 * of defect this library exists to prevent.
 *
 * <p>Encoding is deterministic (R-C19): the same values produce the same bytes
 * on every run, on every platform.
 *
 * @since 0.1.0
 */
public final class RecordEncoder {

    private RecordEncoder() {
    }

    /**
     * Encodes one record.
     *
     * @param descriptor the record layout
     * @param charset    the encoding to write text fields in
     * @param values     field values keyed by field id
     * @return the record bytes, exactly {@code descriptor.recordLength()} long
     * @throws IllegalArgumentException if a key names no field of this record,
     *                                  or a value does not fit its field
     */
    public static byte[] encode(
            RecordDescriptor descriptor, ZenginCharset charset, Map<String, String> values) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(values, "values");

        for (String id : values.keySet()) {
            if (descriptor.find(id).isEmpty()) {
                throw new IllegalArgumentException("the " + descriptor.kind() + " record of format "
                        + descriptor.formatId() + " has no field '" + id + "'; declared fields: "
                        + String.join(", ", descriptor.fields().stream().map(FieldDescriptor::id).toList()));
            }
        }

        byte[] frame = new byte[descriptor.recordLength()];
        for (FieldDescriptor field : descriptor.fields()) {
            String value = values.get(field.id());
            if (value == null) {
                value = field.constant().orElse(null);
            }
            if (value == null) {
                FieldCodec.fill(frame, field.offset(), field.length(), field.type().padByte());
            } else {
                FieldCodec.encodeText(value, frame, field.offset(), field.length(), charset,
                        PadPolicy.of(field.type()));
            }
        }
        return frame;
    }
}
