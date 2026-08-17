package io.zengin4j.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.engine.Rules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code docs/validation-rules.md} matches the code.
 *
 * <p>That page is what a consumer reads to find the id they want to suppress
 * (R-V3). A reference listing an id that no longer exists, or omitting one that
 * does, sends them to suppress nothing and wonder why. Documentation that is
 * checked is documentation that stays true.
 */
class RuleReferenceTest {

    private static final Path REFERENCE = Path.of("..", "docs", "validation-rules.md");

    private static final Pattern ROW =
            Pattern.compile("^\\| `(V-\\d{3})` \\| (ERROR|WARNING|INFO) \\| (.+?) \\|$", Pattern.MULTILINE);

    /** Every id the code can emit, with its default severity and description. */
    private static Map<String, String> fromCode() {
        Map<String, String> rows = new TreeMap<>();
        for (Rule rule : Rules.bundled()) {
            for (String id : rule.emits()) {
                rows.put(id, rule.severityOf(id) + "|" + Messages.description(id));
            }
        }
        return rows;
    }

    private static Map<String, String> fromDocument() throws IOException {
        String text = Files.readString(REFERENCE, StandardCharsets.UTF_8);
        Map<String, String> rows = new TreeMap<>();
        Matcher matcher = ROW.matcher(text);
        while (matcher.find()) {
            rows.put(matcher.group(1), matcher.group(2) + "|" + matcher.group(3));
        }
        return rows;
    }

    @Test
    void theRuleReferenceListsExactlyWhatTheCodeEmits() throws IOException {
        assertThat(REFERENCE)
                .as("docs/validation-rules.md is the reference a consumer suppresses rules from")
                .exists();

        Map<String, String> code = fromCode();
        Map<String, String> document = fromDocument();

        assertThat(document.keySet())
                .as("ids in docs/validation-rules.md must match what the rules emit")
                .containsExactlyInAnyOrderElementsOf(code.keySet());

        for (Map.Entry<String, String> entry : code.entrySet()) {
            assertThat(document.get(entry.getKey()))
                    .as("%s: severity and description must match the code", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    /** The two ids that are not rules are documented too, since they appear in reports. */
    @Test
    void theNonRuleIdsAreDocumented() throws IOException {
        String text = Files.readString(REFERENCE, StandardCharsets.UTF_8);

        for (String id : List.of("V-000", "V-100")) {
            assertThat(text).as("%s appears in reports and must be documented", id).contains(id);
            assertThat(Messages.has(id + ".message"))
                    .as("%s must have text in the bundles", id)
                    .isTrue();
        }
    }

    /**
     * The README's rule count is the real one.
     *
     * <p>A number in prose is the first thing to go stale and the last thing
     * anyone notices. If this fails, add the rule to the README sentence as well
     * as to the code — it is a one-word edit, and the alternative is a headline
     * figure that quietly drifts.
     */
    @Test
    void theReadmeRuleCountIsCurrent() throws IOException {
        int registered = Rules.bundled().size();
        String readme = Files.readString(Path.of("..", "README.md"), StandardCharsets.UTF_8);

        assertThat(readme)
                .as("README says how many rules there are; there are %d", registered)
                .contains("Validation: " + registered + " rules across six tiers");
    }

    /** Composite rules are documented as such, so a reader is not surprised. */
    @Test
    void rulesThatEmitSeveralIdsAreDocumented() throws IOException {
        String text = Files.readString(REFERENCE, StandardCharsets.UTF_8);

        for (Rule rule : Rules.bundled()) {
            if (rule.emits().size() > 1) {
                assertThat(text)
                        .as("%s emits %s and must say so in the reference", rule.id(), rule.emits())
                        .contains("`" + rule.id() + "` |");
            }
        }
    }
}
