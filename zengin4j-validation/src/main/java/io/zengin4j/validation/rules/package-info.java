/**
 * The bundled rules, in the six tiers of §14.3.
 *
 * <p>Tier 1 asks whether this is a Zengin file at all; tier 2 whether each
 * field holds what its type permits; tier 3 whether the file agrees with
 * itself; tiers 4 and 5 bring in reference data and a calendar, both optional;
 * tier 6 flags what is valid and looks wrong anyway.
 *
 * <p>Tier 6 exists because the expensive failures in payments are rarely
 * rejections — a rejected file gets fixed the same afternoon. The expensive
 * ones are files that are accepted and wrong.
 *
 * @since 0.2.0
 */
package io.zengin4j.validation.rules;
