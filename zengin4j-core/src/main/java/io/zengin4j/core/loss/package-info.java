/// What a conversion changed or discarded, and why (P5, R-I14–R-I16).
///
/// **No operation in this library loses information silently.**
/// Anything that truncates, transliterates, drops, defaults or coerces a value
/// records a [io.zengin4j.core.loss.LossEntry] saying what it did, what
/// the value was before, and what it is now. A conversion that shortened a
/// beneficiary name produces output indistinguishable from one that did not, so
/// the account of what happened travels with the result rather than sitting in a
/// log nobody reads.
///
/// These types live in `core` rather than in the mapping layer because
/// transliteration needs them and transliteration is a write-side concern: the
/// writer's `TRANSLITERATE` policy (R-C18) is in `core`, and
/// `core` depends on no other module. The ISO 20022 layer builds its
/// mapping report on this vocabulary rather than inventing a second one.
///
/// @since 0.4.0
package io.zengin4j.core.loss;
