package io.zengin4j.validation.api;

/**
 * What a rule needs to see in order to decide.
 *
 * <p>The engine uses this to decide what to hand a rule and, more usefully,
 * what a rule cannot depend on. A rule scoped to {@link #RECORD} that reached
 * across records would produce findings whose order depended on iteration
 * order, which INV-7 forbids.
 *
 * @since 0.2.0
 */
public enum RuleScope {

    /** The whole file, including framing and the relationship between batches. */
    FILE,

    /** One batch: a header, its data records and its trailer. */
    BATCH,

    /** One record in isolation. */
    RECORD,

    /** One field of one record. */
    FIELD
}
