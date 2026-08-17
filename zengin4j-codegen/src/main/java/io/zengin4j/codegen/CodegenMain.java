package io.zengin4j.codegen;

import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.FormatDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Entry point for the descriptor-driven code and documentation generator.
 *
 * <pre>
 * --mode generate   rewrite the committed sources, docs and index
 * --mode verify     load every descriptor and fail on an inconsistency
 * --mode check      fail if the committed output differs from what the
 *                   descriptors currently produce
 * </pre>
 *
 * <p>Run through Gradle rather than directly:
 * {@code ./gradlew generateFormatSources}.
 */
public final class CodegenMain {
    private static final String CODE_LISTS_FILE = "code-lists.yaml";
    private static final String YAML_SUFFIX = ".yaml";
    private static final String KANA_SUBSTITUTIONS_FILE = "kana-substitutions.yaml";

    private CodegenMain() {
    }

    /**
     * Runs the generator.
     *
     * @param args {@code --mode}, {@code --formats}, {@code --java-out},
     *             {@code --docs-out} and {@code --kana}
     */
    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        String mode = required(options, "--mode");
        Path formats = Path.of(required(options, "--formats"));
        Path javaOut = Path.of(required(options, "--java-out"));
        Path docsOut = Path.of(required(options, "--docs-out"));
        Path kanaDir = Path.of(required(options, "--kana"));

        List<Path> descriptorFiles = descriptorFiles(formats);
        Map<String, CodeList> codeLists = YamlDescriptorReader.readCodeLists(
                read(formats.resolve(CODE_LISTS_FILE)), CODE_LISTS_FILE);

        List<FormatDescriptor> descriptors = new ArrayList<>();
        Map<FormatDescriptor, String> sources = new LinkedHashMap<>();
        Map<String, FormatDescriptor> byId = new LinkedHashMap<>();
        for (Path file : descriptorFiles) {
            String name = file.getFileName().toString();
            FormatDescriptor descriptor =
                    YamlDescriptorReader.readFormat(read(file), name, codeLists, byId);
            descriptors.add(descriptor);
            sources.put(descriptor, name);
            byId.put(descriptor.id().value(), descriptor);
        }

        System.out.println("zengin4j codegen: " + descriptors.size() + " descriptor(s), mode " + mode);
        for (FormatDescriptor descriptor : descriptors) {
            System.out.println("  " + descriptor.id() + " (種別コード " + descriptor.typeCode() + ", "
                    + descriptor.recordLength() + " bytes, verified=" + descriptor.verified() + ")");
        }

        if ("verify".equals(mode)) {
            new RecordSourceGenerator(javaOut).generate(descriptors.get(0), sources.get(descriptors.get(0)));
            System.out.println("all descriptors are internally consistent");
            return;
        }

        List<GeneratedFile> files = plan(descriptors, codeLists, sources, javaOut, docsOut, kanaDir);
        switch (mode) {
            case "generate" -> write(files);
            case "check" -> check(files);
            default -> throw new CodegenException("unknown --mode '" + mode
                    + "'; expected generate, verify or check");
        }
    }

    private static List<GeneratedFile> plan(
            List<FormatDescriptor> descriptors,
            Map<String, CodeList> codeLists,
            Map<FormatDescriptor, String> sources,
            Path javaOut,
            Path docsOut,
            Path kanaDir) {
        RecordSourceGenerator sourceGenerator = new RecordSourceGenerator(javaOut);
        FormatDocGenerator docGenerator = new FormatDocGenerator(docsOut);
        BundledFormatsGenerator formatsGenerator = new BundledFormatsGenerator(javaOut);

        List<GeneratedFile> files = new ArrayList<>();
        for (FormatDescriptor descriptor : descriptors) {
            String source = sources.get(descriptor);
            files.addAll(sourceGenerator.generate(descriptor, source));
            files.add(docGenerator.generate(descriptor, source));
        }
        files.add(sourceGenerator.aggregate(descriptors));
        files.add(sourceGenerator.packageInfo());
        files.add(formatsGenerator.generate(codeLists, descriptors, sources));
        files.add(formatsGenerator.packageInfo());

        KanaTablesGenerator kanaGenerator = new KanaTablesGenerator(javaOut);
        Path substitutions = kanaDir.resolve(KANA_SUBSTITUTIONS_FILE);
        files.add(kanaGenerator.generate(
                KanaSubstitutionReader.read(substitutions), KANA_SUBSTITUTIONS_FILE));
        files.add(kanaGenerator.packageInfo());
        return files;
    }

    private static void write(List<GeneratedFile> files) {
        for (GeneratedFile file : files) {
            try {
                Files.createDirectories(file.path().getParent());
                Files.writeString(file.path(), file.content(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("writing " + file.path(), e);
            }
            System.out.println("  wrote " + file.path());
        }
    }

    private static void check(List<GeneratedFile> files) {
        List<String> problems = new ArrayList<>();
        for (GeneratedFile file : files) {
            if (!Files.exists(file.path())) {
                problems.add("missing: " + file.path());
                continue;
            }
            String committed = normalise(read(file.path()));
            if (!committed.equals(normalise(file.content()))) {
                problems.add("out of date: " + file.path());
            }
        }
        if (!problems.isEmpty()) {
            throw new CodegenException("the committed generated output does not match the descriptors:\n  "
                    + String.join("\n  ", problems)
                    + "\n\nGenerated sources are committed and never hand-edited (R-M8)."
                    + " Run ./gradlew generateFormatSources and commit the result.");
        }
        System.out.println("committed generated output matches the descriptors");
    }

    /** Line endings vary by check-out; content does not. */
    private static String normalise(String content) {
        return content.replace("\r\n", "\n");
    }

    private static List<Path> descriptorFiles(Path formats) {
        if (!Files.isDirectory(formats)) {
            throw new CodegenException("descriptor directory not found: " + formats);
        }
        try (Stream<Path> entries = Files.list(formats)) {
            List<Path> found = entries
                    .filter(path -> path.getFileName().toString().endsWith(YAML_SUFFIX))
                    .filter(path -> !path.getFileName().toString().equals(CODE_LISTS_FILE))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (found.isEmpty()) {
                throw new CodegenException("no format descriptors found in " + formats);
            }
            return found;
        } catch (IOException e) {
            throw new UncheckedIOException("listing " + formats, e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("reading " + path, e);
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                throw new CodegenException("option '" + args[i] + "' has no value");
            }
            options.put(args[i], args[i + 1]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) {
            throw new CodegenException("missing required option " + name);
        }
        return value;
    }
}
