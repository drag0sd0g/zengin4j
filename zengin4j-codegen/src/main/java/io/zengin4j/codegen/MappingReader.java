package io.zengin4j.codegen;

import module java.base;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import org.yaml.snakeyaml.Yaml;

/// Reads a declared Zengin ↔ ISO 20022 mapping.
///
/// Build-time only, like every reader here. The rows are compiled into
/// `zengin4j-iso20022` as Java, so nothing parses YAML at runtime
/// (ADR-0016), and `docs/mapping.md` comes from the same source rather
/// than being maintained beside it.
///
/// Every field name is checked against the descriptor it claims to come from.
/// A mapping row naming a field that does not exist is the failure this catches
/// — it would otherwise read perfectly and silently map nothing.
final class MappingReader {

    /// The whole declaration.
    record Mapping(String id, String format, String message, boolean verified, String note,
            List<Row> rows) {
    }

    /// One declared correspondence.
    record Row(String zenginField, String isoPath, String direction, boolean verified,
            String lossKind, String lossSeverity, String whyEn, String whyJa,
            List<String> sources) {

        boolean hasZenginField() {
            return !zenginField.isEmpty();
        }

        boolean hasIsoPath() {
            return !isoPath.isEmpty();
        }
    }

    private static final String NONE = "-";

    /// The same bar R-0.1 sets for a format descriptor, applied to a mapping row.
    ///
    /// R-I19 says a row may be marked conformant only once it has been checked
    /// against published profile documentation. Without this, "checked" is a
    /// boolean somebody can flip, and the README's claim that the two-source bar
    /// is enforced rather than conventional would be true of descriptors and
    /// false here.
    private static final int REQUIRED_SOURCES_FOR_VERIFICATION = 2;

    private MappingReader() {
    }

    /// Reads a mapping declaration.
    ///
    /// @param file        the YAML file
    /// @param descriptors the descriptors a row's `zengin` field may name
    /// @return the mapping
    /// @throws CodegenException if the file is malformed, or a row names a field
    ///   or record the descriptor does not have
    @SuppressWarnings("unchecked")
    static Mapping read(Path file, List<FormatDescriptor> descriptors) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("reading " + file, e);
        }

        if (!(new Yaml().load(text) instanceof Map<?, ?> root)) {
            throw new CodegenException(file + " is not a YAML mapping");
        }
        if (!(root.get("mapping") instanceof Map<?, ?> header)) {
            throw new CodegenException(file + " declares no 'mapping' block");
        }
        if (!(root.get("rows") instanceof List<?> declared)) {
            throw new CodegenException(file + " declares no 'rows' list");
        }

        String format = required(file, header, "format");
        FormatDescriptor descriptor = descriptors.stream()
                .filter(candidate -> candidate.id().value().equals(format))
                .findFirst()
                .orElseThrow(() -> new CodegenException(file + " maps format '" + format
                        + "', which is not one of the descriptors: " + descriptors.stream()
                                .map(each -> each.id().value()).toList()));

        List<Row> rows = new ArrayList<>(declared.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Object element : declared) {
            if (!(element instanceof Map<?, ?> entry)) {
                throw new CodegenException(file + ": every row must be a mapping");
            }
            Row row = row(file, entry);
            if (row.hasZenginField()) {
                checkField(file, descriptor, row.zenginField());
            }
            String key = row.zenginField() + " -> " + row.isoPath();
            if (!seen.add(key)) {
                throw new CodegenException(file + " declares '" + key + "' twice. A row that "
                        + "appears in the table twice appears in the generated documentation "
                        + "twice, and a reader has no way to tell which one is current.");
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new CodegenException(file + " declares no rows");
        }

        boolean mappingVerified = Boolean.TRUE.equals(header.get("verified"));
        if (mappingVerified && rows.stream().anyMatch(row -> !row.verified())) {
            throw new CodegenException(file + " is marked verified: true while some of its rows "
                    + "are not. A mapping is no more verified than its least verified row.");
        }

        return new Mapping(required(file, header, "id"), format,
                required(file, header, "message"), mappingVerified,
                optional(header, "note"), List.copyOf(rows));
    }

    private static Row row(Path file, Map<?, ?> entry) {
        String zengin = side(required(file, entry, "zengin"));
        String iso = side(required(file, entry, "iso"));
        if (zengin.isEmpty() && iso.isEmpty()) {
            throw new CodegenException(file + ": a row with neither side maps nothing");
        }

        String loss = optional(entry, "loss");
        String kind = "";
        String severity = "";
        if (!loss.isEmpty()) {
            String[] parts = loss.split("/");
            if (parts.length != 2) {
                throw new CodegenException(file + ": loss is 'KIND/SEVERITY', not '" + loss + "'");
            }
            kind = parts[0];
            severity = parts[1];
        }

        boolean verified = Boolean.TRUE.equals(entry.get("verified"));
        List<String> sources = sources(entry);
        if (verified && sources.size() < REQUIRED_SOURCES_FOR_VERIFICATION) {
            throw new CodegenException(file + ": the row '" + zengin + " -> " + iso
                    + "' is marked verified: true with " + sources.size() + " cited source(s). "
                    + "R-I19 requires it to have been checked against published profile "
                    + "documentation, and R-0.1 sets the bar at "
                    + REQUIRED_SOURCES_FOR_VERIFICATION + " independent ones. Cite them in "
                    + "docs/SOURCES.md and list them under 'sources:' on the row.");
        }

        return new Row(zengin, iso, required(file, entry, "direction"), verified, kind, severity,
                required(file, entry, "why-en"), required(file, entry, "why-ja"), sources);
    }

    private static List<String> sources(Map<?, ?> entry) {
        return switch (entry.get("sources")) {
            case List<?> list -> list.stream().map(Object::toString).map(String::trim).toList();
            case null, default -> List.of();
        };
    }

    /// The declarations write `"-"` where this model writes an empty string.
    private static String side(String declared) {
        return NONE.equals(declared) ? "" : declared;
    }

    /// A row naming a field the descriptor does not have is the one mistake this
    /// file format makes easy: it is a typo that changes nothing visible, and the
    /// mapping quietly stops carrying a value.
    private static void checkField(Path file, FormatDescriptor descriptor, String reference) {
        int dot = reference.indexOf('.');
        if (dot < 0) {
            throw new CodegenException(file + ": '" + reference
                    + "' should be record.fieldId, e.g. header.originatorName");
        }
        String recordName = reference.substring(0, dot);
        String fieldId = reference.substring(dot + 1);

        RecordKind kind;
        try {
            kind = RecordKind.valueOf(recordName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException notARecord) {
            throw new CodegenException(file + ": '" + recordName + "' is not a record kind. Use "
                    + java.util.Arrays.stream(RecordKind.values())
                            .map(each -> each.name().toLowerCase(java.util.Locale.ROOT))
                            .toList());
        }

        RecordDescriptor record = descriptor.record(kind);
        boolean found = record.fields().stream()
                .map(FieldDescriptor::id)
                .anyMatch(fieldId::equals);
        if (!found) {
            throw new CodegenException(file + ": " + descriptor.id().value() + "'s " + recordName
                    + " record has no field '" + fieldId + "'. It has: "
                    + record.fields().stream().map(FieldDescriptor::id).toList());
        }
    }

    private static String required(Path file, Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new CodegenException(file + ": every entry needs '" + key + "'");
        }
        return value.toString().trim();
    }

    private static String optional(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
