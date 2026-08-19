package io.zengin4j.core.loss;

/// How much a loss matters (R-I16).
///
/// The distinction is about consequences, not about how much text changed.
/// Widening ｱ to ア alters every byte and matters to nobody; dropping the ﾞ from
/// ｶﾞ alters one byte and renames the payee.
///
/// @since 0.4.0
public enum LossSeverity {

    /// Cosmetic, with no effect on reconciliation.
    ///
    /// Case folding, half-width to full-width widening, a dropped field that
    /// carried no meaning downstream.
    INFORMATIONAL,

    /// A party or a reference reads noticeably differently.
    ///
    /// キャノン becoming キヤノン is material: a human matching the payment
    /// against an invoice will see a different name, even though the money still
    /// arrives.
    MATERIAL,

    /// The meaning of the payment could change, or funds could misroute.
    ///
    /// Reserved for losses that can move money to the wrong place. Callers
    /// may configure a conversion to fail rather than return one.
    CRITICAL
}
