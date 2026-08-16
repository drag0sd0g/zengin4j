package io.zengin4j.core.codec;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.CharacterViolation;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks a whole record against the character classes its fields declare
 * (R-C16, R-C17).
 *
 * <p>Lives here rather than in {@code core.charset} because it needs the
 * descriptor to know which class applies where, and the character package must
 * not depend on the format package — the dependency runs the other way, so a
 * {@link CharacterClass} stays a value anyone can use without a descriptor.
 *
 * @since 0.1.0
 */
public final class RecordCharacters {

    private RecordCharacters() {
    }

    /**
     * Reports every byte in a record that its field's character class does not
     * permit.
     *
     * <p>Offsets are record-relative, so they compose with the record's own
     * offset to give a position in the file.
     *
     * <p>Trailing pad bytes are checked like any other: a {@code C} field is
     * space-padded and every class permits a space, so padding never registers.
     * A field padded with something other than a space is a genuine finding.
     *
     * @param record     the record's bytes; must be at least the record length
     * @param descriptor the record's layout
     * @return the violations in ascending offset order, empty if clean
     * @throws IndexOutOfBoundsException if the record is shorter than the
     *                                   layout requires
     */
    public static List<CharacterViolation> validate(byte[] record, RecordDescriptor descriptor) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(descriptor, "descriptor");

        List<FieldDescriptor> fields = descriptor.fields();
        List<CharacterViolation> violations = null;
        // Indexed rather than enhanced-for: this runs per record, and an
        // iterator per call is an allocation per call (R-P3).
        for (int i = 0; i < fields.size(); i++) {
            FieldDescriptor field = fields.get(i);
            if (field.charClass() == CharacterClass.UNRESTRICTED) {
                continue;
            }
            List<CharacterViolation> found =
                    CharacterSet.validateField(record, field.offset(), field.length(), field.charClass());
            if (!found.isEmpty()) {
                if (violations == null) {
                    violations = new ArrayList<>();
                }
                violations.addAll(found);
            }
        }
        return violations == null ? List.of() : List.copyOf(violations);
    }

    /**
     * Whether a record satisfies every character class its fields declare.
     *
     * @param record     the record's bytes
     * @param descriptor the record's layout
     * @return {@code true} if no field carries a byte it may not
     */
    public static boolean isClean(byte[] record, RecordDescriptor descriptor) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(descriptor, "descriptor");

        List<FieldDescriptor> fields = descriptor.fields();
        // Indexed for the same reason as above. This is the check the reader
        // runs on every record under CharacterPolicy.WARN or REJECT, so an
        // iterator here is an allocation per record.
        for (int i = 0; i < fields.size(); i++) {
            FieldDescriptor field = fields.get(i);
            if (field.charClass() != CharacterClass.UNRESTRICTED
                    && !CharacterSet.isClean(record, field.offset(), field.length(), field.charClass())) {
                return false;
            }
        }
        return true;
    }
}
