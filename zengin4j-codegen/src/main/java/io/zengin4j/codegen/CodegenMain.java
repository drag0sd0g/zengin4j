package io.zengin4j.codegen;

import module java.base;
import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.FormatDescriptor;

/// Entry point for the descriptor-driven code and documentation generator.
///
/// ```
/// --mode generate   rewrite the committed sources, docs and index
/// --mode verify     load every descriptor and fail on an inconsistency
/// --mode check      fail if the committed output differs from what the
///                   descriptors currently produce
/// ```
///
/// Run through Gradle rather than directly:
/// `./gradlew generateFormatSources`.
public final class CodegenMain {

    private static final String CODE_LISTS_FILE = "code-lists.yaml";
    private static final String YAML_SUFFIX = ".yaml";
    private static final String KANA_SUBSTITUTIONS_FILE = "kana-substitutions.yaml";

    private CodegenMain() {
    }

    /// Runs the generator.
    ///
    /// @param args `--mode`, `--formats`, `--java-out`,
    ///   `--docs-out`, `--kana`, `--mappings` and
    ///   `--iso-java-out`
    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        String mode = required(options, "--mode");
        var formats = Path.of(required(options, "--formats"));
        var javaOut = Path.of(required(options, "--java-out"));
        var docsOut = Path.of(required(options, "--docs-out"));
        var kanaDir = Path.of(required(options, "--kana"));
        var mappingsDir = Path.of(required(options, "--mappings"));
        var isoJavaOut = Path.of(required(options, "--iso-java-out"));

        List<Path> descriptorFiles = descriptorFiles(formats);
        Map<String, CodeList> codeLists = YamlDescriptorReader.readCodeLists(
                read(formats.resolve(CODE_LISTS_FILE)), CODE_LISTS_FILE);

        List<FormatDescriptor> descriptors = new ArrayList<>();
        Map<FormatDescriptor, String> sources = new LinkedHashMap<>();
        // Read in file-name order, so a descriptor borrowing a layout with
        // `same-layout-as` finds it already loaded. The ordering is stated in
        // the error message when it does not.
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
            // Loading is the verification: the descriptor model rejects field
            // lengths that do not sum to the record length, non-contiguous
            // offsets, duplicate ids, unresolvable code lists and any claim of
            // verification without cited sources.
            new RecordSourceGenerator(javaOut).generate(descriptors.get(0), sources.get(descriptors.get(0)));
            System.out.println("all descriptors are internally consistent");
            return;
        }

        List<GeneratedFile> files = plan(descriptors, codeLists, sources, javaOut, docsOut, kanaDir, mappingsDir,
                isoJavaOut);
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
            Path kanaDir,
            Path mappingsDir,
            Path isoJavaOut) {

        var sourceGenerator = new RecordSourceGenerator(javaOut);
        var docGenerator = new FormatDocGenerator(docsOut);
        var formatsGenerator = new BundledFormatsGenerator(javaOut);

        List<GeneratedFile> files = new ArrayList<>();
        for (FormatDescriptor descriptor : descriptors) {
            String source = sources.get(descriptor);
            files.addAll(sourceGenerator.generate(descriptor, source));
            files.add(docGenerator.generate(descriptor, source));
        }
        files.add(sourceGenerator.aggregate(descriptors));
        files.add(sourceGenerator.packageInfo());
        // The descriptors themselves, compiled to Java so that core needs no
        // parser and no descriptor resources at runtime (ADR-0016).
        files.add(formatsGenerator.generate(codeLists, descriptors, sources));
        files.add(formatsGenerator.packageInfo());

        // The transliteration tables (R-K9). Same reasoning as the descriptors:
        // authored as data, compiled to Java, so core parses nothing at runtime.
        var kanaGenerator = new KanaTablesGenerator(javaOut);
        Path substitutions = kanaDir.resolve(KANA_SUBSTITUTIONS_FILE);
        files.add(kanaGenerator.generate(
                KanaSubstitutionReader.read(substitutions), KANA_SUBSTITUTIONS_FILE));
        files.add(kanaGenerator.packageInfo());

        // The Zengin <-> ISO 20022 correspondences (R-I19). Same reasoning
        // again: declared as data, compiled to Java, and the reference page
        // generated from the file the mapper is compiled from — so a row cannot
        // be implemented one way and documented another.
        List<Path> mappingFiles = mappingFiles(mappingsDir);
        List<MappingReader.Mapping> mappings = new ArrayList<>(mappingFiles.size());
        List<String> mappingSources = new ArrayList<>(mappingFiles.size());
        for (Path file : mappingFiles) {
            mappings.add(MappingReader.read(file, descriptors));
            mappingSources.add(file.getFileName().toString());
        }
        var mappingGenerator = new MappingSourceGenerator(isoJavaOut);
        files.add(mappingGenerator.generate(mappings, mappingSources));
        files.add(mappingGenerator.packageInfo());
        files.add(new MappingDocGenerator(docsOut.getParent())
                .generate(mappings, mappingSources));
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

    /// Line endings vary by check-out; content does not.
    private static String normalise(String content) {
        return content.replace("\r\n", "\n");
    }

    private static List<Path> mappingFiles(Path mappings) {
        if (!Files.isDirectory(mappings)) {
            throw new CodegenException("mapping directory not found: " + mappings);
        }
        try (Stream<Path> entries = Files.list(mappings)) {
            List<Path> found = entries
                    .filter(path -> path.getFileName().toString().endsWith(YAML_SUFFIX))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (found.isEmpty()) {
                throw new CodegenException("no mapping declarations found in " + mappings);
            }
            return found;
        } catch (IOException e) {
            throw new UncheckedIOException("listing " + mappings, e);
        }
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
