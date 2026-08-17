package io.zengin4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A native image would find everything it needs at run time.
 *
 * <p>GraalVM includes no resource it has not been told about and can reflect
 * over no class it has not been told about, and both failures happen only in
 * the built binary — long after the tests that would have caught them. These
 * checks move the failure to build time.
 *
 * <p>picocli-codegen generates the reflection half on every compile and cannot
 * go stale. The resource half is hand-written, because the processor knows only
 * about picocli's own resources, so it is the half that needs guarding.
 */
class NativeImageConfigTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Path RESOURCE_CONFIG = Path.of("src", "main", "resources", "META-INF",
            "native-image", "io.zengin4j", "zengin4j-cli", "resource-config.json");

    private static final Path GENERATED = Path.of("build", "classes", "java", "main", "META-INF",
            "native-image", "picocli-generated");

    /** Resources loaded by name at run time, wherever in the build they live. */
    private static List<String> runtimeResources() throws IOException {
        List<String> found = new ArrayList<>();
        for (String module : List.of("zengin4j-core", "zengin4j-validation", "zengin4j-testkit")) {
            Path root = Path.of("..", module, "src", "main", "resources");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .map(path -> root.relativize(path).toString()
                                .replace(java.io.File.separatorChar, '/'))
                        .forEach(found::add);
            }
        }
        return found;
    }

    @Test
    void everyRuntimeResourceIsDeclared() throws Exception {
        JsonNode config = MAPPER.readTree(Files.readString(RESOURCE_CONFIG, StandardCharsets.UTF_8));
        List<Pattern> patterns = new ArrayList<>();
        for (JsonNode include : config.get("resources").get("includes")) {
            patterns.add(Pattern.compile(include.get("pattern").asText()));
        }

        List<String> resources = runtimeResources();
        assertThat(resources).as("the modules should have resources to declare").isNotEmpty();

        for (String resource : resources) {
            assertThat(patterns)
                    .as("%s is loaded at run time but no native-image pattern matches it;"
                            + " a native build would not find it", resource)
                    .anyMatch(pattern -> pattern.matcher(resource).matches());
        }
    }

    @Test
    void noPatternMatchesNothing() throws Exception {
        JsonNode config = MAPPER.readTree(Files.readString(RESOURCE_CONFIG, StandardCharsets.UTF_8));
        List<String> resources = runtimeResources();

        for (JsonNode include : config.get("resources").get("includes")) {
            Pattern pattern = Pattern.compile(include.get("pattern").asText());
            assertThat(resources)
                    .as("%s matches no resource; it is either a typo or a leftover",
                            pattern.pattern())
                    .anyMatch(resource -> pattern.matcher(resource).matches());
        }
    }

    /**
     * The generated reflection config covers every command.
     *
     * <p>Not a duplicate of what the processor does — a check that the
     * processor ran at all. It is wired through {@code annotationProcessor}, and
     * a dependency-scope change would silently disable it, leaving a native
     * image that builds and then cannot parse its own arguments.
     */
    @Test
    void theGeneratedReflectionConfigCoversEveryCommand() throws Exception {
        Path reflect = GENERATED.resolve(Path.of("io.github.drag0sd0g", "zengin4j-cli",
                "reflect-config.json"));
        assertThat(reflect)
                .as("picocli-codegen should have written %s; is the annotationProcessor wired?",
                        reflect)
                .exists();

        List<String> registered = new ArrayList<>();
        for (JsonNode entry : MAPPER.readTree(Files.readString(reflect, StandardCharsets.UTF_8))) {
            registered.add(entry.get("name").asText());
        }

        assertThat(registered).contains(
                "io.zengin4j.cli.Zengin",
                "io.zengin4j.cli.command.ValidateCommand",
                "io.zengin4j.cli.command.InspectCommand",
                "io.zengin4j.cli.command.GenerateCommand",
                "io.zengin4j.cli.command.DiffCommand",
                "io.zengin4j.cli.command.ExplainCommand",
                "io.zengin4j.cli.command.ReadingOptions");
    }

    /** Enums used as option types are reflected over when picocli converts them. */
    @Test
    void theGeneratedConfigCoversTheEnumsUsedAsOptionTypes() throws Exception {
        Path reflect = GENERATED.resolve(Path.of("io.github.drag0sd0g", "zengin4j-cli",
                "reflect-config.json"));
        List<String> registered = new ArrayList<>();
        for (JsonNode entry : MAPPER.readTree(Files.readString(reflect, StandardCharsets.UTF_8))) {
            registered.add(entry.get("name").asText());
        }

        assertThat(registered).contains(
                "io.zengin4j.core.charset.ZenginCharset",
                "io.zengin4j.core.model.SeparatorStyle",
                "io.zengin4j.core.format.RecordKind",
                "io.zengin4j.cli.command.OutputFormat");
    }
}
