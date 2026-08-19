package io.zengin4j.testkit;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.EncodingOptions;
import io.zengin4j.core.codec.RecordEncoder;
import io.zengin4j.core.loss.LossCollector;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.SeparatorStyle;

/// Builds record bytes from a descriptor and a map of field values.
///
/// Fields left unset take their declared constant, or the pad byte their
/// type prescribes: zeros for `N`, spaces for `C`. Unknown field
/// ids are rejected, because a fixture that silently ignores a misspelled field
/// asserts nothing.
///
/// Encoding itself is [io.zengin4j.core.codec.RecordEncoder]'s, not a
/// copy of it. A fixture generator that padded fields its own way could produce
/// bytes the library would never write, and then agree with itself about them.
///
/// @since 0.1.0
public final class SyntheticRecords {

    private SyntheticRecords() {
    }

    /// Encodes one record.
    ///
    /// @param descriptor the record layout
    /// @param charset    the encoding to write text fields in
    /// @param values     field values keyed by field id
    /// @return the record bytes, exactly `descriptor.recordLength()` long
    /// @throws IllegalArgumentException if a key names no field of this record,
    ///   or a value does not fit its field
    public static byte[] encode(RecordDescriptor descriptor, ZenginCharset charset, Map<String, String> values) {
        return RecordEncoder.encode(descriptor, charset, values);
    }

    /// Encodes a record *without* checking its characters.
    ///
    /// For building files that are deliberately wrong. A validator's test
    /// suite has to be able to produce the records the validator exists to
    /// complain about — a name carrying a long vowel mark, say — and the
    /// ordinary encoder refuses them, which is what it is for.
    ///
    /// Everything else still applies: field ids are checked, values still have
    /// to fit, padding is still the encoder's.
    ///
    /// @param descriptor the record layout
    /// @param charset    the encoding to write text fields in
    /// @param values     field values keyed by field id
    /// @return the record bytes, exactly `descriptor.recordLength()` long
    /// @since 0.4.0
    public static byte[] encodeUnchecked(RecordDescriptor descriptor, ZenginCharset charset,
            Map<String, String> values) {
        return RecordEncoder.encode(descriptor, charset, values,
                EncodingOptions.builder().withoutCharacterChecks().build(),
                new LossCollector());
    }

    /// Joins records into a file.
    ///
    /// The separator is written after every record, including the last —
    /// the common convention for delimited files, and the one that exercises
    /// the reader's trailing-separator handling.
    ///
    /// @param records         the record frames, in order
    /// @param separator       what to write between records; [SeparatorStyle#MIXED] is
    ///   not a writable setting
    /// @param trailingEofByte whether to append `0x1A` after the last record
    /// @return the file bytes
    /// @throws IllegalArgumentException if the separator style is not writable
    public static byte[] file(List<byte[]> records, SeparatorStyle separator, boolean trailingEofByte) {
        byte[] between = separator.bytes().orElseThrow(() -> new IllegalArgumentException(
                "MIXED is an observation, not a separator a writer can produce"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] record : records) {
            out.writeBytes(record);
            out.writeBytes(between);
        }
        if (trailingEofByte) {
            out.write(0x1A);
        }
        return out.toByteArray();
    }
}
