package io.zengin4j.core.charset;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks bytes against the character set a field permits (R-C17).
 *
 * <p><strong>Validation returns offsets, not a verdict.</strong> A boolean
 * answers "is this file acceptable", which the caller already suspects is no.
 * The useful question is <em>which byte</em>, and a fixed-length format can
 * answer it exactly: every violation carries the position it occurred at, so a
 * report can point at the character rather than at the record.
 *
 * <p>Offsets are relative to the start of the array passed in. Validating a
 * whole record yields record-relative offsets; validating one field's slice
 * yields field-relative ones. {@link #validateField} does the arithmetic for
 * the common case of a field within a record.
 *
 * <p>Nothing here allocates when the input is clean, which is the usual case
 * and the one on the hot path (R-P3).
 *
 * @since 0.1.0
 */
public final class CharacterSet {

    private CharacterSet() {
    }

    /**
     * Reports every byte in a range that the class does not permit.
     *
     * @param bytes       the buffer to check
     * @param offset      where to start
     * @param length      how many bytes to check
     * @param permitted   the character class the range must satisfy
     * @return the violations in ascending offset order, empty if the range is
     *         clean; never {@code null}
     * @throws IndexOutOfBoundsException if the range is outside the buffer
     */
    public static List<CharacterViolation> validate(
            byte[] bytes, int offset, int length, CharacterClass permitted) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(permitted, "permitted");
        Objects.checkFromIndexSize(offset, length, bytes.length);

        List<CharacterViolation> violations = null;
        for (int i = 0; i < length; i++) {
            int value = bytes[offset + i] & 0xFF;
            if (!permitted.permits(value)) {
                if (violations == null) {
                    violations = new ArrayList<>();
                }
                violations.add(new CharacterViolation(offset + i, (byte) value, permitted));
            }
        }
        return violations == null ? List.of() : List.copyOf(violations);
    }

    /**
     * Reports every byte in an entire buffer that the class does not permit.
     *
     * @param bytes     the buffer to check
     * @param permitted the character class the buffer must satisfy
     * @return the violations, empty if clean; never {@code null}
     */
    public static List<CharacterViolation> validate(byte[] bytes, CharacterClass permitted) {
        Objects.requireNonNull(bytes, "bytes");
        return validate(bytes, 0, bytes.length, permitted);
    }

    /**
     * Whether a range satisfies a character class, without building the
     * violation list.
     *
     * <p>For the caller that only branches on the answer — a strict-mode check
     * that is about to fail the read anyway (R-C13). Prefer
     * {@link #validate(byte[], int, int, CharacterClass)} when the offsets will
     * be reported, rather than checking and then re-scanning.
     *
     * @param bytes     the buffer to check
     * @param offset    where to start
     * @param length    how many bytes to check
     * @param permitted the character class
     * @return {@code true} if every byte is permitted
     * @throws IndexOutOfBoundsException if the range is outside the buffer
     */
    public static boolean isClean(byte[] bytes, int offset, int length, CharacterClass permitted) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(permitted, "permitted");
        Objects.checkFromIndexSize(offset, length, bytes.length);

        for (int i = 0; i < length; i++) {
            if (!permitted.permits(bytes[offset + i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reports violations in one field of a record, with offsets relative to the
     * record rather than to the field.
     *
     * <p>Record-relative is what a finding needs: it composes with the record's
     * own offset in the file to give a position a reader can seek to.
     *
     * @param record      the record's bytes
     * @param fieldOffset the field's offset within the record
     * @param fieldLength the field's length in bytes
     * @param permitted   the field's character class
     * @return the violations, empty if clean; never {@code null}
     * @throws IndexOutOfBoundsException if the field lies outside the record
     */
    public static List<CharacterViolation> validateField(
            byte[] record, int fieldOffset, int fieldLength, CharacterClass permitted) {
        return validate(record, fieldOffset, fieldLength, permitted);
    }
}
