package io.zengin4j.cli;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// `--out-format=json` works on every command (R-CLI2).
///
/// Checked by parsing the output with a real parser rather than by looking
/// for substrings. A hand-written writer that emits *almost* valid JSON
/// passes every `contains` assertion and fails in the consumer, which is
/// the one place the failure is expensive — the same reasoning as ADR-0022.
class JsonOutputTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path directory;

    private Path file;
    private Path other;

    @BeforeEach
    void generateFiles() throws Exception {
        file = Cli.generate(directory, "a.txt", "--count=3", "--seed=1");
        other = Cli.generate(directory, "b.txt", "--count=4", "--seed=2");
    }

    private static JsonNode parse(Cli result) throws Exception {
        assertThat(result.out()).as("expected JSON on stdout, got: " + result.all()).isNotBlank();
        return MAPPER.readTree(result.out());
    }

    @Test
    void everyCommandEmitsParseableJson() throws Exception {
        List<String[]> invocations = List.of(
                new String[] {"validate", file.toString(), "--allow-unverified"},
                new String[] {"inspect", file.toString(), "--annotate", "--allow-unverified"},
                new String[] {"diff", file.toString(), other.toString(), "--allow-unverified"},
                new String[] {"explain", "--format=sougou-furikomi"},
                new String[] {"explain"},
                new String[] {"generate", "--count=2"});

        for (String[] base : invocations) {
            String[] arguments = java.util.Arrays.copyOf(base, base.length + 1);
            arguments[base.length] = "--out-format=json";

            Cli result = Cli.run(arguments);
            assertThat(MAPPER.readTree(result.out()))
                    .as("%s produced unparseable JSON: %s", String.join(" ", arguments),
                            result.out())
                    .isNotNull();
        }
    }

    @Test
    void validateJsonCarriesTheFindingsAndTheVerdict() throws Exception {
        JsonNode json = parse(Cli.run("validate", file.toString(), "--allow-unverified",
                "--out-format=json"));

        assertThat(json.has("submittable")).isTrue();
        assertThat(json.get("counts").has("error")).isTrue();
        assertThat(json.get("findings").isArray()).isTrue();
    }

    @Test
    void validateSarifIsParseableAndDeclaresItsRules() throws Exception {
        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--out-format=sarif");
        JsonNode json = MAPPER.readTree(result.out());

        assertThat(json.get("version").asText()).isEqualTo("2.1.0");
        JsonNode driver = json.get("runs").get(0).get("tool").get("driver");
        assertThat(driver.get("name").asText()).isEqualTo("zengin4j");
        assertThat(driver.get("rules")).isNotEmpty();
    }

    /// A result carries the file it is about, so a CI consumer can annotate it.
    ///
    /// Needs a file with findings: a clean run has no results and therefore
    /// no locations, which is correct and also why the previous shape of this
    /// test was checking nothing.
    @Test
    void sarifResultsNameTheFileTheyConcern() throws Exception {
        java.nio.file.Files.write(directory.resolve("dup.txt"),
                io.zengin4j.testkit.SougouFurikomiFixtures.create()
                        .file(2, io.zengin4j.core.model.SeparatorStyle.CRLF, false));
        Path withFindings = directory.resolve("dup.txt");

        Cli result = Cli.run("validate", withFindings.toString(), "--allow-unverified",
                "--out-format=sarif");
        JsonNode json = MAPPER.readTree(result.out());

        JsonNode results = json.get("runs").get(0).get("results");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).get("locations").get(0).get("physicalLocation")
                        .get("artifactLocation").get("uri").asText())
                .isEqualTo(withFindings.toString());
    }

    @Test
    void inspectJsonDescribesEveryFieldOfEveryRecord() throws Exception {
        JsonNode json = parse(Cli.run("inspect", file.toString(), "--annotate",
                "--allow-unverified", "--out-format=json"));

        assertThat(json.get("format").asText()).isEqualTo("sougou-furikomi");
        assertThat(json.get("recordLength").asInt()).isEqualTo(120);
        assertThat(json.get("masked").asBoolean()).as("R-CLI4 default").isTrue();

        JsonNode records = json.get("records");
        assertThat(records).hasSize(6);

        JsonNode fields = records.get(1).get("fields");
        assertThat(fields).isNotEmpty();
        JsonNode first = fields.get(0);
        for (String key : List.of("id", "nameJa", "nameEn", "type", "offset", "length", "hex",
                "value", "valid", "sensitive")) {
            assertThat(first.has(key)).as("R-CLI5 requires %s", key).isTrue();
        }
    }

    @Test
    void inspectJsonMarksSensitiveFieldsAsMasked() throws Exception {
        JsonNode json = parse(Cli.run("inspect", file.toString(), "--annotate",
                "--allow-unverified", "--out-format=json"));

        JsonNode accountNumber = fieldNamed(json, "accountNumber");
        assertThat(accountNumber.get("sensitive").asBoolean()).isTrue();
        assertThat(accountNumber.get("hex").asText()).isEqualTo("(masked)");
    }

    private static JsonNode fieldNamed(JsonNode inspectJson, String id) {
        for (JsonNode record : inspectJson.get("records")) {
            if (!record.has("fields")) {
                continue;
            }
            for (JsonNode field : record.get("fields")) {
                if (id.equals(field.get("id").asText())) {
                    return field;
                }
            }
        }
        throw new AssertionError("no field " + id + " in the output");
    }

    @Test
    void diffJsonNamesTheFieldsThatChanged() throws Exception {
        JsonNode json = parse(Cli.run("diff", file.toString(), other.toString(),
                "--allow-unverified", "--out-format=json"));

        assertThat(json.get("changed").asBoolean()).isTrue();
        assertThat(json.get("summary").get("added").asInt()
                + json.get("summary").get("changed").asInt()).isPositive();
        assertThat(json.get("records").isArray()).isTrue();
    }

    @Test
    void explainJsonCarriesTheLayoutAndItsSources() throws Exception {
        JsonNode json = parse(Cli.run("explain", "--format=sougou-furikomi",
                "--out-format=json"));

        assertThat(json.get("id").asText()).isEqualTo("sougou-furikomi");
        assertThat(json.get("typeCode").asText()).isEqualTo("21");
        assertThat(json.get("verified").asBoolean())
                .as("every bundled format is still unverified, and the output must say so")
                .isFalse();
        assertThat(json.get("sources")).isNotEmpty();
        assertThat(json.get("records")).hasSize(4);
    }

    @Test
    void explainJsonNarrowsACodeListWhereTheDescriptorDoes() throws Exception {
        JsonNode json = parse(Cli.run("explain", "--format=kyuyo-furikomi", "--field=accountType",
                "--record=DATA", "--out-format=json"));

        JsonNode permitted = json.get("occurrences").get(0).get("permitted");
        assertThat(permitted).hasSize(2);
        assertThat(permitted.get(0).get("code").asText()).isEqualTo("1");
        assertThat(permitted.get(1).get("code").asText()).isEqualTo("2");
    }

    @Test
    void generateJsonDescribesWhatItWrote() throws Exception {
        Path target = directory.resolve("described.txt");
        JsonNode json = parse(Cli.run("generate", "--count=7", "--seed=5",
                "--out=" + target, "--out-format=json"));

        assertThat(json.get("payments").asInt()).isEqualTo(7);
        assertThat(json.get("seed").asInt()).isEqualTo(5);
        assertThat(json.get("synthetic").asBoolean()).isTrue();
        assertThat(json.get("path").asText()).isEqualTo(target.toString());
    }

    /// Japanese passes through as itself rather than as `\\u` escapes.
    @Test
    void japaneseTextIsReadableInTheOutput() throws Exception {
        Cli result = Cli.run("explain", "--format=sougou-furikomi", "--out-format=json");

        assertThat(result.out()).contains("総合振込");
        assertThat(result.out()).doesNotContain("\\u60");
        assertThat(MAPPER.readTree(result.out()).get("nameJa").asText()).isEqualTo("総合振込");
    }

    /// SARIF carries findings; the commands that report no findings say so.
    @Test
    void commandsWithoutFindingsRefuseSarifRatherThanEmittingSomethingWrong() {
        for (String[] arguments : List.of(
                new String[] {"inspect", file.toString(), "--out-format=sarif"},
                new String[] {"diff", file.toString(), other.toString(), "--out-format=sarif"},
                new String[] {"explain", "--out-format=sarif"},
                new String[] {"generate", "--out-format=sarif"})) {
            Cli result = Cli.run(arguments);
            assertThat(result.status())
                    .as("%s should refuse SARIF", String.join(" ", arguments))
                    .isEqualTo(ExitCode.USAGE.value());
            assertThat(result.err()).contains("SARIF");
        }
    }
}
