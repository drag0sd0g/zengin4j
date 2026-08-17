package io.zengin4j.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import io.zengin4j.validation.api.ReportWriters;
import io.zengin4j.validation.api.ValidationReport;
import io.zengin4j.validation.engine.Rules;
import io.zengin4j.validation.refdata.MapReferenceData;
import io.zengin4j.validation.refdata.ReferenceDataProvider;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * R-V4 (JSON and SARIF) and R-V5 (optional reference data).
 */
class ReportOutputTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Parses with a real parser, so the writer is checked rather than trusted. */
    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            throw new AssertionError("the writer produced JSON a parser rejects:\n" + json, malformed);
        }
    }

    private ValidationReport problems() {
        return ZenginValidator.defaults().validate(Fixtures.fileWithManyProblems());
    }

    // -------------------------------------------------------------- R-V4

    /**
     * The JSON is parsed back rather than pattern-matched. A hand-written
     * writer that produced <em>almost</em> valid JSON would pass every
     * {@code contains} assertion and fail in the consumer.
     */
    @Test
    void jsonIsWellFormedAndCarriesEveryFinding() {
        String json = ReportWriters.toJson(problems());

        JsonNode parsed = parse(json);
        assertThat(parsed.get("submittable").asBoolean()).isFalse();
        assertThat(parsed.get("counts").get("error").asInt()).isGreaterThan(0);

        JsonNode findings = parsed.get("findings");
        assertThat(findings).hasSize(problems().findings().size());
        assertThat(findings.get(0).get("ruleId").asText()).matches("V-\\d{3}");
        assertThat(findings.get(0).get("messageEn").asText()).isNotBlank();
        assertThat(findings.get(0).get("messageJa").asText()).isNotBlank();
    }

    /** Japanese survives the round trip as itself, not as escapes. */
    @Test
    void japaneseTextIsNotMangled() {
        String json = ReportWriters.toJson(problems());

        assertThat(json).contains("トレーラ");
        assertThat(json).doesNotContain("\\u30c8");
        assertThat(parse(json).get("findings").get(0).get("messageJa").asText()).isNotBlank();
    }

    /** A message containing the characters JSON reserves must not break it. */
    @Test
    void quotesAndBackslashesAreEscaped() {
        ValidationReport report = new ValidationReport(List.of(
                io.zengin4j.validation.api.Finding.of(
                                io.zengin4j.validation.api.Severity.ERROR, "V-999")
                        .message("a \"quoted\" value with a \\ backslash\nand a newline",
                                "「引用」と \\ と改行")
                        .build()));

        String json = ReportWriters.toJson(report);

        assertThat(parse(json).get("findings").get(0).get("messageEn").asText())
                .isEqualTo("a \"quoted\" value with a \\ backslash\nand a newline");
    }

    @Test
    void sarifIsWellFormedAndDescribesItsRules() {
        String sarif = ReportWriters.toSarif(problems(), Rules.bundled(), "payments.txt");

        JsonNode parsed = parse(sarif);
        assertThat(parsed.get("version").asText()).isEqualTo("2.1.0");
        assertThat(parsed.get("$schema").asText()).contains("sarif-schema-2.1.0");

        JsonNode run = parsed.get("runs").get(0);
        assertThat(run.get("tool").get("driver").get("name").asText()).isEqualTo("zengin4j");

        // Every id any rule can emit is declared, not just each rule's own —
        // the composite rules emit several each, and a result referencing an
        // undeclared ruleId is a document a consumer may reject.
        JsonNode rules = run.get("tool").get("driver").get("rules");
        java.util.Set<String> declared = new java.util.HashSet<>();
        rules.forEach(rule -> declared.add(rule.get("id").asText()));
        java.util.Set<String> emitted = new java.util.HashSet<>();
        Rules.bundled().forEach(rule -> emitted.addAll(rule.emits()));
        assertThat(declared).containsExactlyInAnyOrderElementsOf(emitted);
        assertThat(rules.get(0).get("shortDescription").get("text").asText()).isNotBlank();

        JsonNode results = run.get("results");
        assertThat(results).hasSize(problems().findings().size());
        results.forEach(result -> assertThat(declared)
                .as("result references ruleId %s, which the document must declare",
                        result.get("ruleId").asText())
                .contains(result.get("ruleId").asText()));
        assertThat(results.get(0).get("level").asText()).isIn("error", "warning", "note");
        assertThat(results.get(0).get("locations").get(0)
                .get("physicalLocation").get("artifactLocation").get("uri").asText())
                .isEqualTo("payments.txt");
    }

    /** Severity maps onto SARIF's three levels, which is why SARIF fits. */
    @Test
    void severityMapsOntoSarifLevels() {
        assertThat(io.zengin4j.validation.api.Severity.ERROR.sarifLevel()).isEqualTo("error");
        assertThat(io.zengin4j.validation.api.Severity.WARNING.sarifLevel()).isEqualTo("warning");
        assertThat(io.zengin4j.validation.api.Severity.INFO.sarifLevel()).isEqualTo("note");
    }

    /** A clean report still produces valid documents, not empty strings. */
    @Test
    void aCleanReportStillSerialises() {
        ValidationReport clean = ZenginValidator.defaults().validate(Fixtures.wellFormedFile());

        assertThat(parse(ReportWriters.toJson(clean)).get("submittable").asBoolean()).isTrue();
        assertThat(parse(ReportWriters.toSarif(clean, Rules.bundled(), "x"))
                .get("runs").get(0).get("results")).isEmpty();
    }

    @Test
    void textRendersInBothLanguages() {
        ValidationReport report = problems();

        assertThat(report.toText(Locale.ENGLISH)).contains("error(s)").contains("Not submittable");
        assertThat(report.toText(Locale.JAPANESE)).contains("エラー").contains("送信できません");
    }

    // -------------------------------------------------------------- R-V5

    /** The library is complete without reference data: those rules just do not run. */
    @Test
    void withoutReferenceDataNoReferenceRulesRun() {
        ValidationReport report = ZenginValidator.defaults().validate(Fixtures.wellFormedFile());

        assertThat(report.findingsOf("V-401")).isEmpty();
        assertThat(report.findingsOf("V-402")).isEmpty();
    }

    @Test
    void v401_reportsABankTheProvidedDataDoesNotKnow() {
        ReferenceDataProvider data = MapReferenceData.describedAs("test data 2026-08")
                .bank("9998", "ﾃｽﾄｷﾞﾝｺｳ")
                .branch("9998", "998", "ﾎﾝﾃﾝ")
                .build();

        ValidationReport report = ZenginValidator.builder()
                .withReferenceData(data)
                .build()
                .validate(Fixtures.wellFormedFile());

        // The fixtures use bank 9999, which this provider does not carry.
        assertThat(report.findingsOf("V-401")).isNotEmpty();
        assertThat(report.findingsOf("V-401").get(0).expectation())
                .as("the finding names the data it was checked against")
                .hasValueSatisfying(value -> assertThat(value).contains("test data 2026-08"));
    }

    @Test
    void v402_reportsAnUnknownBranchOfAKnownBank() {
        ReferenceDataProvider data = MapReferenceData.describedAs("test data")
                .bank("9999", "ﾃｽﾄｷﾞﾝｺｳ")
                .branch("9999", "001", "ﾎﾝﾃﾝ")
                .build();

        ValidationReport report = ZenginValidator.builder()
                .withReferenceData(data)
                .build()
                .validate(Fixtures.wellFormedFile());

        assertThat(report.findingsOf("V-401")).as("the bank is known").isEmpty();
        assertThat(report.findingsOf("V-402")).isNotEmpty();
    }

    @Test
    void aProviderThatKnowsEverythingProducesNoFindings() {
        ReferenceDataProvider data = MapReferenceData.describedAs("complete test data")
                .bank("9999", "ﾃｽﾄｷﾞﾝｺｳ")
                .branch("9999", "999", "ﾃｽﾄｼﾃﾝ")
                .branch("9999", "998", "ﾎﾝﾃﾝ")
                .build();

        ValidationReport report = ZenginValidator.builder()
                .withReferenceData(data)
                .build()
                .validate(Fixtures.wellFormedFile());

        assertThat(report.findingsOf("V-401")).isEmpty();
        assertThat(report.findingsOf("V-402")).isEmpty();
        assertThat(data.bankNameKana("9999")).contains("ﾃｽﾄｷﾞﾝｺｳ");
        assertThat(data.branchNameKana("9999", "999")).contains("ﾃｽﾄｼﾃﾝ");
        assertThat(data.describe()).isEqualTo("complete test data");
    }

    /** V-403: the name in the file disagrees with what the reference data says. */
    @Test
    void v403_warnsWhenANameDisagreesWithTheReferenceData() {
        ReferenceDataProvider data = MapReferenceData.describedAs("test data")
                .bank("9999", "ﾁｶﾞｳﾒｲｼｮｳ")
                .branch("9999", "999", "ﾃｽﾄｼﾃﾝ")
                .branch("9999", "998", "ﾎﾝﾃﾝ")
                .build();

        ValidationReport report = ZenginValidator.builder()
                .withReferenceData(data)
                .build()
                .validate(Fixtures.wellFormedFile());

        assertThat(report.findingsOf("V-403")).isNotEmpty();
        assertThat(report.findingsOf("V-403").get(0).severity())
                .as("institutions abbreviate; a mismatch is worth a look, not a block")
                .isEqualTo(io.zengin4j.validation.api.Severity.WARNING);
    }

    /** And it stays quiet when the names agree. */
    @Test
    void v403_staysQuietWhenNamesMatch() {
        ReferenceDataProvider data = MapReferenceData.describedAs("test data")
                .bank("9999", "ﾃｽﾄｷﾞﾝｺｳ")
                .branch("9999", "999", "ﾃｽﾄｼﾃﾝ")
                .branch("9999", "998", "ﾎﾝﾃﾝ")
                .build();

        ValidationReport report = ZenginValidator.builder()
                .withReferenceData(data)
                .build()
                .validate(Fixtures.wellFormedFile());

        assertThat(report.findingsOf("V-403")).isEmpty();
    }

    /** The report renders its own JSON and SARIF, as §14.1 specifies. */
    @Test
    void theReportSerialisesItself() {
        ValidationReport report = problems();

        assertThat(parse(report.toJson()).get("findings")).isNotEmpty();
        assertThat(parse(report.toSarif()).get("version").asText()).isEqualTo("2.1.0");
        assertThat(parse(report.toSarif("payments.txt")).get("runs").get(0)
                .get("results").get(0).get("locations").get(0)
                .get("physicalLocation").get("artifactLocation").get("uri").asText())
                .isEqualTo("payments.txt");
        assertThat(report.rules()).as("a report knows the rules that produced it").isNotEmpty();
    }

    /** An unused fixture guard: the file the reference tests rely on is clean otherwise. */
    @Test
    void theReferenceFixtureUsesTheSyntheticRanges() {
        byte[] file = SyntheticRecords.file(
                List.of(Fixtures.TESTKIT.header(), Fixtures.TESTKIT.data(),
                        Fixtures.TESTKIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                        Fixtures.TESTKIT.end()),
                SeparatorStyle.CRLF, false);

        assertThat(new String(file, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("9999");
    }
}
