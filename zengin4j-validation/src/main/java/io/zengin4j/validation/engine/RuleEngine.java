package io.zengin4j.validation.engine;

import module java.base;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.Severity;

/// Runs the rules and collects what they find.
///
/// Three responsibilities, all of which exist so that individual rules do not
/// have to think about them:
///
/// - **Nothing escapes** (R-V1). A rule that throws is a bug in
///   the rule, and the engine converts it into a finding rather than into
///   the caller's exception. Validation is what you run *on* a broken
///   file; failing on one is the one behaviour it cannot have.
/// - **Suppression and severity overrides** (R-V3), applied in
///   one place rather than consulted by each rule.
/// - **Determinism** (INV-7). Findings are sorted canonically
///   before they leave, so the report does not depend on rule order, on
///   iteration order, or on which rule happened to run first.
///
/// @since 0.2.0
public final class RuleEngine {

    /// Reported when a rule throws. Not a rule id that any rule owns: it names
    /// the engine, because the defect is in the rule set rather than in the file.
    public static final String RULE_FAILED = "V-000";

    private final List<Rule> rules;
    private final Set<String> suppressed;
    private final Map<String, Severity> overrides;
    private final boolean failFast;

    /// Creates an engine.
    ///
    /// @param rules      the rules to run
    /// @param suppressed rule ids to skip (R-V3)
    /// @param overrides  rule ids whose severity the caller has re-ranked
    /// @param failFast   whether to stop after tier 1 finds errors
    public RuleEngine(List<Rule> rules, Set<String> suppressed, Map<String, Severity> overrides,
            boolean failFast) {
        this.rules = List.copyOf(rules);
        this.suppressed = Set.copyOf(suppressed);
        this.overrides = Map.copyOf(overrides);
        this.failFast = failFast;
    }

    /// Runs every enabled rule against a file.
    ///
    /// @param context what there is to validate
    /// @return the findings, canonically ordered; never `null`
    public List<Finding> run(ValidationContext context) {
        Objects.requireNonNull(context, "context");
        List<Finding> collected = new ArrayList<>();

        // Grouped by tier so fail-fast can stop after structural errors: if the
        // records are not where the layout says, every later tier is reading
        // the wrong bytes and its findings would be noise about noise.
        Map<String, List<Rule>> byTier = new LinkedHashMap<>();
        for (Rule rule : rules) {
            byTier.computeIfAbsent(tierOf(rule), tier -> new ArrayList<>()).add(rule);
        }

        for (Map.Entry<String, List<Rule>> tier : byTier.entrySet()) {
            for (Rule rule : tier.getValue()) {
                // Skipped only when every id it could emit is suppressed. A
                // composite rule with one id suppressed still runs, and the
                // suppressed findings are dropped below — otherwise turning off
                // "value date is a holiday" would also turn off "is a weekend".
                if (suppressed.containsAll(rule.emits())) {
                    continue;
                }
                runOne(rule, context, collected);
            }
            if (failFast && tier.getKey().equals("1") && hasError(collected)) {
                break;
            }
        }

        Collections.sort(collected);
        return List.copyOf(collected);
    }

    /// Runs one rule, converting an escaping exception into a finding.
    ///
    /// The alternative — letting it propagate — would mean a defect in one
    /// rule loses every finding from every other rule, including the ones that
    /// had already succeeded. The caller gets the report they asked for, plus a
    /// finding saying which rule is broken.
    private void runOne(Rule rule, ValidationContext context, List<Finding> collected) {
        List<Finding> fromThisRule = new ArrayList<>();
        try {
            rule.check(context, fromThisRule::add);
        } catch (RuntimeException | StackOverflowError failure) {
            collected.add(Messages.format(RULE_FAILED + ".message", rule.id(), describe(failure))
                    .into(Finding.of(Severity.ERROR, RULE_FAILED))
                    .build());
            return;
        }
        for (Finding finding : fromThisRule) {
            if (suppressed.contains(finding.ruleId())) {
                continue;
            }
            collected.add(applyOverride(finding));
        }
    }

    private Finding applyOverride(Finding finding) {
        Severity override = overrides.get(finding.ruleId());
        if (override == null || override == finding.severity()) {
            return finding;
        }
        return new Finding(override, finding.ruleId(), finding.recordNumber(), finding.byteOffset(),
                finding.fieldOffset(), finding.fieldId(), finding.messageEn(), finding.messageJa(),
                finding.actualValue(), finding.expectation());
    }

    private static boolean hasError(List<Finding> findings) {
        return findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
    }

    /// The digit after `V-`: the tier a rule belongs to.
    private static String tierOf(Rule rule) {
        String id = rule.id();
        return id.length() >= 3 && id.startsWith("V-") ? id.substring(2, 3) : "9";
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /// The rules this engine will run, in registration order.
    ///
    /// @return the rules, never `null`
    public List<Rule> rules() {
        return rules;
    }
}
