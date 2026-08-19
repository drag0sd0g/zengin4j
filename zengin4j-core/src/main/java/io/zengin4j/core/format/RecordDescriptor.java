package io.zengin4j.core.format;

import module java.base;
import io.zengin4j.core.error.FormatDescriptorException;

/// The layout of one record kind within a format.
///
/// @param formatId      the format this record belongs to, carried so that
///   every diagnostic can name it
/// @param kind          the record's role
/// @param discriminator the データ区分 byte that identifies this record kind
/// @param recordLength  the record length in bytes
/// @param fields        the fields, in layout order, with computed offsets
/// @since 0.1.0
public record RecordDescriptor(
        FormatId formatId,
        RecordKind kind,
        byte discriminator,
        int recordLength,
        List<FieldDescriptor> fields) {

    /// Validates the layout.
    ///
    /// This is the runtime half of R-F1: field lengths must sum exactly to
    /// the record length, offsets must be contiguous from zero, and field ids
    /// must be unique. The build runs the same checks as a task so that a
    /// transcription error fails the build rather than a payment run.
    ///
    /// @throws FormatDescriptorException if the layout is inconsistent
    public RecordDescriptor {
        Objects.requireNonNull(formatId, "formatId");
        Objects.requireNonNull(kind, "kind");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        if (kind == RecordKind.MALFORMED) {
            throw FormatDescriptorException.forFormat(formatId.value(),
                    "MALFORMED is not a declarable record kind");
        }
        if (fields.isEmpty()) {
            throw FormatDescriptorException.forFormat(formatId.value(),
                    "record '" + kind.descriptorKey() + "' declares no fields");
        }
        int cursor = 0;
        Set<String> seen = new HashSet<>();
        for (FieldDescriptor field : fields) {
            if (!seen.add(field.id())) {
                throw FormatDescriptorException.forFormat(formatId.value(),
                        "record '" + kind.descriptorKey() + "' declares field id '" + field.id() + "' twice");
            }
            if (field.offset() != cursor) {
                throw FormatDescriptorException.forFormat(formatId.value(),
                        "record '" + kind.descriptorKey() + "' field '" + field.id() + "' has offset "
                                + field.offset() + " but the preceding fields end at " + cursor);
            }
            cursor += field.length();
        }
        if (cursor != recordLength) {
            throw FormatDescriptorException.forFormat(formatId.value(),
                    "record '" + kind.descriptorKey() + "' field lengths sum to " + cursor
                            + " but the record length is " + recordLength
                            + ". Every byte of a fixed-length record must be accounted for, including filler.");
        }
    }

    /// Builds a record layout from field specifications, computing every byte
    /// offset from the cumulative length of the fields before it (R-F2).
    ///
    /// This is the only supported way to construct a layout. The canonical
    /// constructor takes offsets because the descriptor has to carry them, but
    /// nothing should ever be in the position of writing one down: the lengths
    /// already determine them, and a transcribed offset is the classic
    /// fixed-length parser defect.
    ///
    /// @param formatId      the format this record belongs to
    /// @param kind          the record's role
    /// @param discriminator the データ区分 byte identifying this record kind
    /// @param recordLength  the record length in bytes
    /// @param fields        the fields, in layout order, without offsets
    /// @return the layout, with offsets assigned
    /// @throws FormatDescriptorException if the field lengths do not sum to the
    ///   record length (R-F1)
    public static RecordDescriptor of(
            FormatId formatId,
            RecordKind kind,
            byte discriminator,
            int recordLength,
            List<FieldSpec> fields) {
        Objects.requireNonNull(fields, "fields");
        List<FieldDescriptor> placed = new ArrayList<>(fields.size());
        int cursor = 0;
        for (FieldSpec spec : fields) {
            placed.add(spec.at(cursor));
            cursor += spec.length();
        }
        return new RecordDescriptor(formatId, kind, discriminator, recordLength, placed);
    }

    /// Looks a field up by id.
    ///
    /// @param id the field id
    /// @return the field, or empty if this record has no such field
    public Optional<FieldDescriptor> find(String id) {
        return Optional.ofNullable(lookup(id));
    }

    /// Looks a field up, returning `null` when there is none.
    ///
    /// **Allocation-free without relying on the JIT.** This is
    /// the hot path: `view.asLong(view.field("amount"))` runs once per
    /// field per record, so millions of times over a large file. An indexed loop
    /// rather than an enhanced-for, which allocates an iterator; no
    /// [Optional] and no capturing lambda, both of which allocate.
    ///
    /// In practice HotSpot's escape analysis removes all three once the path
    /// is hot — `FieldAllocationTest` measures zero bytes per field either
    /// way. This is written not to need it: escape analysis is a best-effort
    /// optimisation that stops applying when a method grows too large to inline,
    /// and R-P3 should not depend on one.
    private FieldDescriptor lookup(String id) {
        for (int i = 0; i < fields.size(); i++) {
            FieldDescriptor field = fields.get(i);
            if (field.id().equals(id)) {
                return field;
            }
        }
        return null;
    }

    /// Looks a field up by id, failing if it is absent.
    ///
    /// @param id the field id
    /// @return the field
    /// @throws FormatDescriptorException if this record has no such field
    public FieldDescriptor field(String id) {
        FieldDescriptor found = lookup(id);
        if (found == null) {
            // Built only on the failing path, so the message costs nothing on
            // the successful one.
            throw FormatDescriptorException.forFormat(formatId.value(),
                    "record '" + kind.descriptorKey() + "' has no field '" + id + "'; declared fields: "
                            + String.join(", ", fields.stream().map(FieldDescriptor::id).toList()));
        }
        return found;
    }

    /// Returns the single field declaring a given interpretation, if there is
    /// one.
    ///
    /// @param format the interpretation to look for
    /// @return the field, or empty if no field declares it
    /// @throws FormatDescriptorException if more than one field declares it
    public Optional<FieldDescriptor> findByFormat(FieldFormat format) {
        FieldDescriptor found = null;
        for (FieldDescriptor field : fields) {
            if (field.hasFormat(format)) {
                if (found != null) {
                    throw FormatDescriptorException.forFormat(formatId.value(),
                            "record '" + kind.descriptorKey() + "' declares format " + format.descriptorValue()
                                    + " on both '" + found.id() + "' and '" + field.id() + "'");
                }
                found = field;
            }
        }
        return Optional.ofNullable(found);
    }
}
