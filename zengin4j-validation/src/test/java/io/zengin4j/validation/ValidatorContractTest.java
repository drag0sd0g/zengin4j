package io.zengin4j.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import io.zengin4j.validation.engine.Rules;
import io.zengin4j.validation.engine.ValidationContext;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The contracts that hold whatever the file contains: R-V1, R-V3 and INV-7.
 */
class ValidatorContractTest {
    /**
     * R-V1. The one behaviour a validator cannot have is failing on bad input,
     * because bad input is the only reason anyone runs one.
     */
    @Test
    void neverThrowsWhateverTheFileContains() {
        for (byte[] input : Fixtures.hostileInputs()) {
            assertThatCode(() -> {
                ValidationReport report = Fixtures.validateBytes(input);
                assertThat(report).isNotNull();
                assertThat(report.findings()).isNotNull();
            }).as("input of %d bytes", input.length).doesNotThrowAnyException();
        }
    }

    /** R-V1, again: a rule with a bug in it must not become the caller's exception. */
    @Test
    void aRuleThatThrowsBecomesAFindingRatherThanAnException() {
        ZenginValidator validator = ZenginValidator.builder()
                .withRules(List.of(new ExplodingRule()))
                .build();

        ValidationReport report = validator.validate(Fixtures.wellFormedFile());

        assertThat(report.findingsOf("V-000")).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.ERROR);
            assertThat(finding.messageEn()).contains("V-999").contains("defect in the rule");
            assertThat(finding.messageJa()).isNotBlank();
        });
    }

    /** And the other rules still run when one of them is broken. */
    @Test
    void aBrokenRuleDoesNotSuppressTheOthers() {
        ZenginValidator validator = ZenginValidator.builder()
                .addRule(new ExplodingRule())
                .build();

        ValidationReport report = validator.validate(Fixtures.fileWithWrongTrailerTotal());

        assertThat(report.findingsOf("V-000")).hasSize(1);
        assertThat(report.findingsOf("V-301")).as("the real finding survives").hasSize(1);
    }

    /**
     * R-V1 at the outermost edge: a file that cannot even be <em>read</em>
     * still produces a report.
     *
     * <p>"This is not a file I can parse" is exactly the answer the caller
     * asked for, and an exception would make them write the try/catch that
     * validation exists to save them.
     */
    @Test
    void afileThatCannotBeReadProducesAReportRatherThanAnException(@org.junit.jupiter.api.io.TempDir
            java.nio.file.Path directory) throws java.io.IOException {
        java.nio.file.Path unreadable = directory.resolve("not-zengin.txt");
        java.nio.file.Files.writeString(unreadable, "this is not a zengin file at all");

        ValidationReport report = ZenginValidator.defaults()
                .validate(unreadable, Fixtures.lenient());

        assertThat(report.findingsOf("V-100")).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.ERROR);
            assertThat(finding.messageEn()).contains("could not be read");
            assertThat(finding.messageJa()).contains("読み取れませんでした")
                    .doesNotContain("could not be read");
        });
        assertThat(report.isSubmittable()).isFalse();
    }

    /** R-V3: every rule is addressable and suppressible by id. */
    @Test
    void anyRuleCanBeSuppressedById() {
        ZenginValidator defaults = ZenginValidator.defaults();
        ValidationReport before = defaults.validate(Fixtures.fileWithWrongTrailerTotal());
        assertThat(before.findingsOf("V-301")).isNotEmpty();

        ValidationReport after = ZenginValidator.builder()
                .suppress("V-301")
                .build()
                .validate(Fixtures.fileWithWrongTrailerTotal());

        assertThat(after.findingsOf("V-301")).isEmpty();
    }

    /** R-V3, the other half: a severity a consumer disagrees with can be re-ranked. */
    @Test
    void aRulesSeverityCanBeOverridden() {
        ValidationReport report = ZenginValidator.builder()
                .severity("V-301", Severity.WARNING)
                .build()
                .validate(Fixtures.fileWithWrongTrailerTotal());

        assertThat(report.findingsOf("V-301")).singleElement()
                .extracting(Finding::severity)
                .isEqualTo(Severity.WARNING);
        assertThat(report.isSubmittable())
                .as("downgrading the only error makes the file submittable")
                .isTrue();
    }

    /**
     * INV-7. Asserted by shuffling the rule order, which is the thing most
     * likely to leak into the output: a report that depended on it would look
     * perfectly stable in a single-threaded test that always registered rules
     * the same way.
     */
    @Test
    void theSameFileAlwaysProducesTheSameReport() {
        List<Rule> rules = new java.util.ArrayList<>(Rules.bundled());
        String expected = ZenginValidator.defaults()
                .validate(Fixtures.fileWithManyProblems())
                .toText(java.util.Locale.ENGLISH);

        for (int seed = 0; seed < 20; seed++) {
            java.util.Collections.shuffle(rules, new java.util.Random(seed));
            String actual = ZenginValidator.builder()
                    .withRules(List.copyOf(rules))
                    .build()
                    .validate(Fixtures.fileWithManyProblems())
                    .toText(java.util.Locale.ENGLISH);

            assertThat(actual).as("rule order shuffled with seed %d", seed).isEqualTo(expected);
        }
    }

    /** Findings sort by position, so a report reads down the file. */
    @Test
    void findingsAreOrderedByPositionThenByRule() {
        ValidationReport report = ZenginValidator.defaults().validate(Fixtures.fileWithManyProblems());

        assertThat(report.findings()).isNotEmpty();
        List<Finding> findings = report.findings();
        for (int i = 1; i < findings.size(); i++) {
            assertThat(findings.get(i - 1).compareTo(findings.get(i)))
                    .as("finding %d must not sort after finding %d", i - 1, i)
                    .isLessThanOrEqualTo(0);
        }
    }

    /** R-V2: every finding carries both languages, and neither is blank. */
    @Test
    void everyFindingCarriesBothLanguages() {
        ValidationReport report = ZenginValidator.defaults().validate(Fixtures.fileWithManyProblems());

        assertThat(report.findings()).isNotEmpty().allSatisfy(finding -> {
            assertThat(finding.messageEn()).isNotBlank();
            assertThat(finding.messageJa()).isNotBlank();
            assertThat(finding.messageJa())
                    .as("%s: the Japanese message must not be the English one", finding.ruleId())
                    .isNotEqualTo(finding.messageEn());
        });
    }

    /** R-E4: every bundled rule has text in both bundles, and a description. */
    @Test
    void everyBundledRuleHasMessagesInBothLanguages() {
        for (Rule rule : Rules.bundled()) {
            assertThat(Messages.has(rule.id() + ".message"))
                    .as("%s.message is missing from a bundle", rule.id())
                    .isTrue();
            assertThat(Messages.has(rule.id() + ".description"))
                    .as("%s.description is missing from a bundle", rule.id())
                    .isTrue();
            assertThat(rule.description()).isNotBlank();
        }
    }

    /**
     * Apostrophe quoting differs between the two kinds of entry, and getting it
     * backwards is invisible until somebody reads a report.
     *
     * <p>A {@code .message} goes through {@link java.text.MessageFormat}, where a
     * lone apostrophe is a quoting character and must be doubled. A
     * {@code .description} is returned verbatim, so a doubled one appears
     * literally — which is how {@code "the format''s record length"} reached a
     * SARIF document.
     */
    @Test
    void apostrophesAreQuotedForMessagesAndNotForDescriptions() {
        for (Rule rule : Rules.bundled()) {
            assertThat(rule.description())
                    .as("%s.description is returned verbatim and must not double its apostrophes",
                            rule.id())
                    .doesNotContain("''");
        }
        assertThat(Messages.format("V-301.message", 1, 2, 3).en())
                .contains("batch's").contains("1").doesNotContain("{0}");
    }

    /**
     * R-V3 in full: every id a rule can <em>emit</em> is suppressible, not just
     * the id the rule is registered under.
     *
     * <p>The composite rules answer several questions from one walk of the file
     * — the value date is classified once and yields V-501 through V-505. If
     * suppression only matched the registered id, a consumer could not turn off
     * "value date is a public holiday" while keeping "value date is a weekend",
     * and the requirement would hold in the letter and not the substance.
     */
    @Test
    void everyEmittedIdIsIndividuallySuppressible() {
        for (Rule rule : Rules.bundled()) {
            assertThat(rule.emits())
                    .as("%s must declare its own id among what it emits", rule.id())
                    .contains(rule.id());
        }

        java.util.Set<String> emitted = new java.util.HashSet<>();
        Rules.bundled().forEach(rule -> emitted.addAll(rule.emits()));
        assertThat(emitted).contains("V-303", "V-304", "V-502", "V-503", "V-505");

        ValidationReport report = ZenginValidator.builder()
                .suppress("V-304")
                .build()
                .validate(Fixtures.fileWithWrongTrailerTotal());
        assertThat(report.findingsOf("V-301"))
                .as("suppressing a sibling id leaves the rest of the rule running")
                .isNotEmpty();
    }

    /** Rule ids are unique: two rules sharing one would make suppression ambiguous. */
    @Test
    void ruleIdsAreUnique() {
        List<String> ids = Rules.bundled().stream().map(Rule::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allSatisfy(id -> assertThat(id).matches("V-\\d{3}"));
    }

    /** A rule that always fails, for testing that the engine contains it. */
    private static final class ExplodingRule implements Rule {
        @Override
        public String id() {
            return "V-999";
        }

        @Override
        public Severity defaultSeverity() {
            return Severity.ERROR;
        }

        @Override
        public RuleScope scope() {
            return RuleScope.FILE;
        }

        @Override
        public String description() {
            return "A rule that throws, for testing the engine";
        }

        @Override
        public void check(ValidationContext context, Consumer<Finding> out) {
            throw new IllegalStateException("deliberate");
        }
    }
}
