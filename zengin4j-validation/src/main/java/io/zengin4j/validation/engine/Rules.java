package io.zengin4j.validation.engine;

import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.rules.CalendarRules;
import io.zengin4j.validation.rules.ConsistencyRules;
import io.zengin4j.validation.rules.ReferenceDataRules;
import io.zengin4j.validation.rules.SemanticRules;
import io.zengin4j.validation.rules.StructuralRules;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules this library ships.
 *
 * <p>Registered in tier order, which is the order the engine runs them and the
 * order fail-fast depends on. Within a tier the order does not matter: findings
 * are sorted canonically before they leave the engine (INV-7).
 *
 * @since 0.2.0
 */
public final class Rules {

    private Rules() {
    }

    /**
     * Every bundled rule.
     *
     * @return the rules, never {@code null}
     */
    public static List<Rule> bundled() {
        List<Rule> rules = new ArrayList<>();
        rules.addAll(StructuralRules.all());
        rules.addAll(SyntaxRulesHolder.ALL);
        rules.addAll(ConsistencyRules.all());
        rules.addAll(ReferenceDataRules.all());
        rules.addAll(CalendarRules.all());
        rules.addAll(SemanticRules.all());
        return List.copyOf(rules);
    }

    /** Deferred so a failure loading one tier does not hide the others. */
    private static final class SyntaxRulesHolder {
        private static final List<Rule> ALL = io.zengin4j.validation.rules.SyntaxRules.all();
    }
}
