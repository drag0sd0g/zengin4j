package io.zengin4j.validation.rules;

import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Messages;
import io.zengin4j.validation.api.Rule;
import io.zengin4j.validation.api.RuleScope;
import io.zengin4j.validation.api.Severity;

/**
 * The bookkeeping every rule shares: its id, its default severity, its scope,
 * and a finding pre-stamped with both.
 *
 * <p>Exists so a rule's source is the check and nothing else. A rule that had
 * to restate its own id in every finding it produced would eventually restate
 * it wrongly, and suppression by id (R-V3) would then miss one.
 *
 * @since 0.2.0
 */
abstract class AbstractRule implements Rule {
    private final String id;
    private final Severity severity;
    private final RuleScope scope;
    private final java.util.Set<String> emits;

    AbstractRule(String id, Severity severity, RuleScope scope) {
        this(id, severity, scope, java.util.Set.of(id));
    }

    AbstractRule(String id, Severity severity, RuleScope scope, java.util.Set<String> emits) {
        this.id = id;
        this.severity = severity;
        this.scope = scope;
        this.emits = java.util.Set.copyOf(emits);
    }

    @Override
    public final java.util.Set<String> emits() {
        return emits;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final Severity defaultSeverity() {
        return severity;
    }

    @Override
    public final RuleScope scope() {
        return scope;
    }

    @Override
    public final String description() {
        return Messages.description(id);
    }

    /** A finding already carrying this rule's id and default severity. */
    final Finding.Builder finding() {
        return Finding.of(severity, id);
    }
}
