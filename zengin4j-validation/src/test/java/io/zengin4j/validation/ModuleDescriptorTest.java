package io.zengin4j.validation;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// `module-info.java` exports what consumers need.
///
/// A package left out of the descriptor is invisible on the module path and
/// perfectly visible on the class path, so nothing in an ordinary build notices:
/// the compiler is content, the tests pass, and the failure surfaces in a
/// modular consumer who cannot import the entry point. This module shipped
/// exactly that defect — every package exported but
/// `io.zengin4j.validation`, the one holding [ZenginValidator].
class ModuleDescriptorTest {

    private static final Pattern EXPORTS = Pattern.compile("^\\s*exports\\s+([\\w.]+)\\s*;",
            Pattern.MULTILINE);

    /// Every module that publishes a descriptor.
    ///
    /// Checked from here rather than from each module because the failure is
    /// the same everywhere and the check is worth having in one place. The build
    /// declares the sources as inputs so that editing one of them re-runs this.
    private static final List<String> MODULES = List.of(
            "zengin4j-core", "zengin4j-validation", "zengin4j-testkit", "zengin4j-iso20022");

    private static Path sourceRoot(String module) {
        return Path.of("..", module, "src", "main", "java");
    }

    /// Packages declared in the module descriptor.
    private static Set<String> exported(String module) throws IOException {
        String text = Files.readString(sourceRoot(module).resolve("module-info.java"),
                StandardCharsets.UTF_8);
        Set<String> packages = new TreeSet<>();
        Matcher matcher = EXPORTS.matcher(text);
        while (matcher.find()) {
            packages.add(matcher.group(1));
        }
        return packages;
    }

    /// Packages holding at least one public type, which is what a consumer can use.
    private static Set<String> packagesWithPublicTypes(String module) throws IOException {
        Path root = sourceRoot(module);
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("module-info.java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(ModuleDescriptorTest::declaresAPublicType)
                    .map(path -> root.relativize(path).getParent().toString()
                            .replace(java.io.File.separatorChar, '.'))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static boolean declaresAPublicType(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .anyMatch(line -> line.startsWith("public ") || line.startsWith("public\t"));
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read " + file, unreadable);
        }
    }

    @Test
    void everyPackageWithPublicTypesIsExported() throws IOException {
        for (String module : MODULES) {
            assertThat(exported(module))
                    .as("%s/module-info.java must export every package holding public types —"
                            + " otherwise a modular consumer cannot reach them", module)
                    .containsAll(packagesWithPublicTypes(module));
        }
    }

    @Test
    void nothingIsExportedThatDoesNotExist() throws IOException {
        for (String module : MODULES) {
            for (String exported : exported(module)) {
                Path directory = sourceRoot(module)
                        .resolve(exported.replace('.', java.io.File.separatorChar));
                assertThat(directory)
                        .as("%s exports %s, which has no source directory", module, exported)
                        .exists();
            }
        }
    }
}
