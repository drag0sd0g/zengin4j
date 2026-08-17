package io.zengin4j.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * Reads the declared transliteration substitutions.
 *
 * <p>Build-time only, like every other reader here: the result is compiled into
 * {@code core} as Java, so nothing at runtime parses YAML (ADR-0016).
 *
 * <p>The file holds only the judgement calls — which characters the field rules
 * refuse and what is written instead. The mechanical width correspondence is
 * derived from Unicode by {@link KanaTablesGenerator} and is deliberately not
 * transcribed here.
 */
final class KanaSubstitutionReader {
    /** One declared substitution. */
    record Substitution(String from, String to, String severity, String whyEn, String whyJa) {
    }

    private KanaSubstitutionReader() {
    }

    /**
     * Reads the substitutions.
     *
     * @param file the YAML file
     * @return the substitutions, in declaration order
     * @throws CodegenException if the file is malformed or self-contradictory
     */
    @SuppressWarnings("unchecked")
    static List<Substitution> read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("reading " + file, e);
        }

        Object loaded = new Yaml().load(text);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new CodegenException(file + " is not a YAML mapping");
        }
        Object entries = root.get("substitutions");
        if (!(entries instanceof List<?> list)) {
            throw new CodegenException(file + " declares no 'substitutions' list");
        }

        List<Substitution> substitutions = new ArrayList<>(list.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> entry)) {
                throw new CodegenException(file + ": every substitution must be a mapping");
            }
            Substitution substitution = new Substitution(
                    required(file, entry, "from"),
                    required(file, entry, "to"),
                    required(file, entry, "severity"),
                    required(file, entry, "why-en"),
                    required(file, entry, "why-ja"));

            if (substitution.from().codePointCount(0, substitution.from().length()) != 1) {
                throw new CodegenException(file + ": 'from' must be a single character, found '"
                        + substitution.from() + "'");
            }
            if (!seen.add(substitution.from())) {
                throw new CodegenException(file + ": '" + substitution.from()
                        + "' is substituted more than once");
            }
            substitutions.add(substitution);
        }

        for (Substitution substitution : substitutions) {
            for (int i = 0; i < substitution.to().length(); ) {
                int codePoint = substitution.to().codePointAt(i);
                if (seen.contains(new String(Character.toChars(codePoint)))) {
                    throw new CodegenException(file + ": '" + substitution.from() + "' is replaced by '"
                            + substitution.to() + "', which is itself substituted."
                            + " Substitution is a single pass, so this would not resolve.");
                }
                i += Character.charCount(codePoint);
            }
        }
        return substitutions;
    }

    private static String required(Path file, Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        if (value == null) {
            throw new CodegenException(file + ": substitution " + entry + " has no '" + key + "'");
        }
        return value.toString();
    }
}
