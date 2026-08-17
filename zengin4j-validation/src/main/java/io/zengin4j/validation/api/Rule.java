package io.zengin4j.validation.api;

import io.zengin4j.validation.engine.ValidationContext;
import java.util.function.Consumer;

/**
 * One check, addressable by id (R-V3).
 *
 * <p>Rules report through a {@link Consumer} rather than returning a list, so a
 * rule that finds fifty problems does not build fifty lists on the way out, and
 * so the engine decides what happens to a finding — suppression, re-ranking,
 * fail-fast — rather than each rule reimplementing it.
 *
 * <p><strong>A rule never throws</strong> (R-V1). Malformed input is the
 * expected case: this library exists to be pointed at files that are wrong.
 * A rule that cannot evaluate something reports that it could not, or reports
 * nothing; it does not raise. The engine treats an escaping exception as a bug
 * in the rule and says so, rather than letting it become the caller's problem.
 *
 * <p>Implementations must be stateless and safe to run in any order (INV-7).
 *
 * @since 0.2.0
 */
public interface Rule {

    /**
     * The stable identifier, for example {@code V-301}.
     *
     * <p>Stable across versions: consumers suppress by this string, and
     * renumbering a rule would silently re-enable a check somebody had turned
     * off deliberately.
     *
     * @return the id, never {@code null}
     */
    String id();

    /**
     * What this rule reports when it fires, absent a consumer override.
     *
     * @return the default severity, never {@code null}
     */
    Severity defaultSeverity();

    /**
     * What the rule needs to see.
     *
     * @return the scope, never {@code null}
     */
    RuleScope scope();

    /**
     * A short English description of what the rule checks, for reports that
     * list the rules themselves — SARIF carries one per rule.
     *
     * @return the description, never {@code null}
     */
    String description();

    /**
     * Every finding id this rule can produce.
     *
     * <p>Usually just {@link #id()}. Some checks answer several questions from
     * one walk of the file — the trailer sum is computed once and yields
     * {@code V-301}, {@code V-303} or {@code V-304}; the value date is
     * classified once and yields {@code V-501} through {@code V-505} — because
     * computing them separately could produce answers that disagree with each
     * other.
     *
     * <p>Declaring them keeps R-V3 honest. Suppression matches on the id a
     * finding carries, not on the rule that produced it, so a consumer can turn
     * off "value date is a public holiday" while keeping "value date is a
     * weekend". It also lets a SARIF document declare every rule its results
     * reference, which a consumer is entitled to expect.
     *
     * @return the ids, never empty
     */
    default java.util.Set<String> emits() {
        return java.util.Set.of(id());
    }

    /**
     * The default severity of one of the ids this rule emits.
     *
     * <p>A composite rule's siblings need not share its severity — the value
     * date being a public holiday is an error, while the calendar being unable
     * to answer at all is information. Reports that describe rules, SARIF among
     * them, would otherwise declare a level that its own results contradict.
     *
     * @param emittedId one of {@link #emits()}
     * @return that id's default severity, never {@code null}
     */
    default Severity severityOf(String emittedId) {
        return defaultSeverity();
    }

    /**
     * Runs the check.
     *
     * @param context what there is to validate
     * @param out     where to report findings
     */
    void check(ValidationContext context, Consumer<Finding> out);
}
