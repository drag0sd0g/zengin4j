package io.zengin4j.codegen;

import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.CodeValue;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.FieldSpec;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads format descriptors and code lists at build time.
 *
 * <p>This runs in the build, not in {@code zengin4j-core}, which is why it may
 * use SnakeYAML: R-M1 constrains what the published core artifact requires at
 * runtime. Core receives the result as generated Java (see
 * {@link BundledFormatsGenerator}) and needs no parser of its own.
 *
 * <p>The strictness lives here and in the descriptor model, not in the YAML
 * tokenizer: unknown keys, out-of-order sequence numbers, unresolvable code
 * lists and constants that contradict a record's discriminator are all
 * rejected, and the model itself refuses a layout whose field lengths do not
 * sum to the record length (R-F1) or a claim of verification without two
 * cited sources (R-0.1).
 */
final class YamlDescriptorReader {

    private static final Set<String> DOCUMENT_KEYS = Set.of("format");
    private static final Set<String> FORMAT_KEYS = Set.of(
            "id", "name-ja", "name-en", "type-code", "record-length", "verified", "sources", "note", "records");
    private static final Set<String> RECORD_KEYS = Set.of("discriminator", "fields", "note");
    private static final Set<String> FIELD_KEYS = Set.of(
            "seq", "id", "ja", "en", "type", "length", "required", "filler", "sensitive",
            "format", "const", "codelist", "note");
    private static final Set<String> CODE_LIST_DOCUMENT_KEYS = Set.of("code-lists");
    private static final Set<String> CODE_LIST_KEYS = Set.of(
            "id", "name-ja", "name-en", "verified", "open", "values", "sources", "note");
    private static final Set<String> CODE_VALUE_KEYS = Set.of("code", "ja", "en", "verified", "note");

    private YamlDescriptorReader() {
    }

    static Map<String, CodeList> readCodeLists(String yaml, String origin) {
        Map<String, Object> document = asMapping(parse(yaml, origin), origin, "document root");
        rejectUnknownKeys(document, origin, "code list document", CODE_LIST_DOCUMENT_KEYS);
        require(document, "code-lists", origin);

        Map<String, CodeList> result = new LinkedHashMap<>();
        for (Map<String, Object> node : mappingList(document, "code-lists", origin)) {
            rejectUnknownKeys(node, origin, "code list", CODE_LIST_KEYS);
            String id = requireString(node, "id", origin);
            List<CodeValue> values = new ArrayList<>();
            for (Map<String, Object> valueNode : mappingList(node, "values", origin)) {
                rejectUnknownKeys(valueNode, origin, "code value", CODE_VALUE_KEYS);
                values.add(new CodeValue(
                        requireString(valueNode, "code", origin),
                        requireString(valueNode, "ja", origin),
                        requireString(valueNode, "en", origin),
                        booleanValue(valueNode, "verified", false, origin),
                        optionalString(valueNode, "note", origin)));
            }
            CodeList list;
            try {
                list = new CodeList(
                        id,
                        requireString(node, "name-ja", origin),
                        requireString(node, "name-en", origin),
                        booleanValue(node, "verified", false, origin),
                        booleanValue(node, "open", true, origin),
                        values,
                        stringList(node, "sources", origin),
                        optionalString(node, "note", origin));
            } catch (RuntimeException e) {
                throw new CodegenException(origin + ": " + e.getMessage(), e);
            }
            if (result.putIfAbsent(id, list) != null) {
                throw new CodegenException(origin + ": code list '" + id + "' is declared twice");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static FormatDescriptor readFormat(String yaml, String origin, Map<String, CodeList> codeLists) {
        Map<String, Object> document = asMapping(parse(yaml, origin), origin, "document root");
        rejectUnknownKeys(document, origin, "descriptor document", DOCUMENT_KEYS);
        Map<String, Object> format = requireMapping(document, "format", origin);
        rejectUnknownKeys(format, origin, "format", FORMAT_KEYS);

        FormatId id = toFormatId(requireString(format, "id", origin), origin);
        int recordLength = requireInt(format, "record-length", origin);
        String typeCode = requireString(format, "type-code", origin);

        Map<String, Object> recordsNode = requireMapping(format, "records", origin);
        Map<RecordKind, RecordDescriptor> records = new EnumMap<>(RecordKind.class);
        for (String key : recordsNode.keySet()) {
            RecordKind kind = RecordKind.fromDescriptorKey(key).orElseThrow(() -> new CodegenException(
                    origin + ": unknown record kind '" + key + "'; expected header, data, trailer or end"));
            records.put(kind, readRecord(requireMapping(recordsNode, key, origin), id, kind, recordLength,
                    typeCode, origin, codeLists));
        }

        try {
            return new FormatDescriptor(
                    id,
                    requireString(format, "name-ja", origin),
                    requireString(format, "name-en", origin),
                    typeCode,
                    recordLength,
                    booleanValue(format, "verified", false, origin),
                    stringList(format, "sources", origin),
                    optionalString(format, "note", origin),
                    records);
        } catch (RuntimeException e) {
            throw new CodegenException(origin + ": " + e.getMessage(), e);
        }
    }

    private static RecordDescriptor readRecord(
            Map<String, Object> node,
            FormatId formatId,
            RecordKind kind,
            int recordLength,
            String typeCode,
            String origin,
            Map<String, CodeList> codeLists) {

        rejectUnknownKeys(node, origin, "record '" + kind.descriptorKey() + "'", RECORD_KEYS);
        byte discriminator = toDiscriminator(requireString(node, "discriminator", origin), kind, origin);

        List<Map<String, Object>> fieldNodes = mappingList(node, "fields", origin);
        if (fieldNodes.isEmpty()) {
            throw new CodegenException(origin + ": record '" + kind.descriptorKey() + "' declares no fields");
        }

        List<FieldSpec> fields = new ArrayList<>(fieldNodes.size());
        for (int i = 0; i < fieldNodes.size(); i++) {
            fields.add(readField(fieldNodes.get(i), i + 1, origin, codeLists));
        }

        FieldSpec first = fields.get(0);
        if (first.constant().isPresent() && first.length() == 1
                && first.constant().get().charAt(0) != (char) discriminator) {
            throw new CodegenException(origin + ": record '" + kind.descriptorKey() + "' has discriminator '"
                    + (char) discriminator + "' but its first field '" + first.id() + "' is fixed at '"
                    + first.constant().get() + "'");
        }

        if (kind == RecordKind.HEADER) {
            fields.stream()
                    .filter(field -> field.id().equals("typeCode"))
                    .findFirst()
                    .flatMap(FieldSpec::constant)
                    .ifPresent(constant -> {
                        if (!constant.equals(typeCode)) {
                            throw new CodegenException(origin + ": the format declares type-code '" + typeCode
                                    + "' but the header's typeCode field is fixed at '" + constant + "'");
                        }
                    });
        }

        try {
            return RecordDescriptor.of(formatId, kind, discriminator, recordLength, fields);
        } catch (RuntimeException e) {
            throw new CodegenException(origin + ": " + e.getMessage(), e);
        }
    }

    private static FieldSpec readField(
            Map<String, Object> node, int expectedSequence, String origin, Map<String, CodeList> codeLists) {

        rejectUnknownKeys(node, origin, "field", FIELD_KEYS);
        int sequence = requireInt(node, "seq", origin);
        String id = requireString(node, "id", origin);
        if (sequence != expectedSequence) {
            throw new CodegenException(origin + ": field '" + id + "' declares seq " + sequence
                    + " but is in position " + expectedSequence
                    + ". Sequence numbers exist to catch a field pasted into the wrong place.");
        }

        FieldSpec spec;
        try {
            spec = FieldSpec.of(sequence, id,
                    requireString(node, "ja", origin),
                    requireString(node, "en", origin),
                    toFieldType(requireString(node, "type", origin), origin),
                    requireInt(node, "length", origin));
        } catch (RuntimeException e) {
            throw new CodegenException(origin + ": " + e.getMessage(), e);
        }

        if (booleanValue(node, "required", false, origin)) {
            spec = spec.withRequired();
        }
        if (booleanValue(node, "filler", false, origin)) {
            spec = spec.withFiller();
        }
        if (booleanValue(node, "sensitive", false, origin)) {
            spec = spec.withSensitive();
        }
        Optional<String> declaredFormat = optionalString(node, "format", origin);
        if (declaredFormat.isPresent()) {
            spec = spec.withFormat(toFieldFormat(declaredFormat.get(), origin));
        }
        Optional<String> constant = optionalString(node, "const", origin);
        if (constant.isPresent()) {
            spec = spec.withConstant(constant.get());
        }
        Optional<String> codeListRef = optionalString(node, "codelist", origin);
        if (codeListRef.isPresent()) {
            CodeList found = codeLists.get(codeListRef.get());
            if (found == null) {
                throw new CodegenException(origin + ": field '" + id + "' references unknown code list '"
                        + codeListRef.get() + "'; known lists: " + String.join(", ", codeLists.keySet()));
            }
            spec = spec.withCodeList(found);
        }
        Optional<String> note = optionalString(node, "note", origin);
        if (note.isPresent()) {
            spec = spec.withNote(note.get());
        }
        return spec;
    }

    // ------------------------------------------------------------- plumbing

    private static Object parse(String yaml, String origin) {
        LoaderOptions options = new LoaderOptions();
        // Parity with the reader this replaced: a repeated key is a mistake,
        // not a last-one-wins override.
        options.setAllowDuplicateKeys(false);
        options.setProcessComments(false);
        try {
            return new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (RuntimeException e) {
            throw new CodegenException(origin + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMapping(Object node, String origin, String what) {
        if (node instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new CodegenException(origin + ": " + what + " must be a mapping");
    }

    private static Object require(Map<String, Object> node, String key, String origin) {
        Object value = node.get(key);
        if (value == null) {
            throw new CodegenException(origin + ": required key '" + key + "' is missing");
        }
        return value;
    }

    private static Map<String, Object> requireMapping(Map<String, Object> node, String key, String origin) {
        return asMapping(require(node, key, origin), origin, "key '" + key + "'");
    }

    private static String requireString(Map<String, Object> node, String key, String origin) {
        Object value = require(node, key, origin);
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw new CodegenException(origin + ": key '" + key + "' must be a single value");
    }

    private static Optional<String> optionalString(Map<String, Object> node, String key, String origin) {
        Object value = node.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String text) {
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return Optional.of(String.valueOf(value));
        }
        throw new CodegenException(origin + ": key '" + key + "' must be a single value");
    }

    private static int requireInt(Map<String, Object> node, String key, String origin) {
        Object value = require(node, key, origin);
        if (value instanceof Integer number) {
            if (number < 0) {
                throw new CodegenException(origin + ": key '" + key + "' must not be negative, found " + number);
            }
            return number;
        }
        throw new CodegenException(origin + ": key '" + key + "' must be a whole number, found '" + value + "'");
    }

    private static boolean booleanValue(
            Map<String, Object> node, String key, boolean defaultValue, String origin) {
        Object value = node.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new CodegenException(origin + ": key '" + key + "' must be true or false, found '" + value + "'");
    }

    private static List<String> stringList(Map<String, Object> node, String key, String origin) {
        Object value = node.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof String text) {
                    result.add(text);
                } else {
                    throw new CodegenException(origin + ": '" + key + "' must be a list of values");
                }
            }
            return result;
        }
        throw new CodegenException(origin + ": key '" + key + "' must be a list");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mappingList(
            Map<String, Object> node, String key, String origin) {
        Object value = node.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                } else {
                    throw new CodegenException(origin + ": '" + key + "' must be a list of mappings");
                }
            }
            return result;
        }
        throw new CodegenException(origin + ": key '" + key + "' must be a list");
    }

    private static void rejectUnknownKeys(
            Map<String, Object> node, String origin, String what, Collection<String> allowed) {
        for (String key : node.keySet()) {
            if (!allowed.contains(key)) {
                throw new CodegenException(origin + ": unknown key '" + key + "' in " + what
                        + ". Permitted keys: " + String.join(", ", allowed));
            }
        }
    }

    private static FormatId toFormatId(String raw, String origin) {
        try {
            return FormatId.of(raw);
        } catch (RuntimeException e) {
            throw new CodegenException(origin + ": " + e.getMessage(), e);
        }
    }

    private static FieldType toFieldType(String raw, String origin) {
        return switch (raw) {
            case "N" -> FieldType.N;
            case "C" -> FieldType.C;
            default -> throw new CodegenException(origin + ": field type must be N or C, found '" + raw + "'");
        };
    }

    private static FieldFormat toFieldFormat(String raw, String origin) {
        for (FieldFormat candidate : FieldFormat.values()) {
            if (candidate.descriptorValue().equals(raw)) {
                return candidate;
            }
        }
        throw new CodegenException(origin + ": unknown field format '" + raw + "'; supported: "
                + FieldFormat.supportedValues());
    }

    private static byte toDiscriminator(String raw, RecordKind kind, String origin) {
        if (raw.length() != 1 || raw.charAt(0) > 0x7F) {
            throw new CodegenException(origin + ": record '" + kind.descriptorKey()
                    + "' must declare a single ASCII character as its discriminator, found '" + raw + "'");
        }
        return (byte) raw.charAt(0);
    }
}
