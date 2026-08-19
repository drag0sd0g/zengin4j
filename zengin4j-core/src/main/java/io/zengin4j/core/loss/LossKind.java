package io.zengin4j.core.loss;

/// What kind of information a conversion lost (R-I15).
///
/// Separate from [LossSeverity], which says how much it matters. The
/// two are independent on purpose: dropping a field is `DROPPED` whether
/// the field was a cosmetic flag or the beneficiary's account number, and the
/// severity is what distinguishes them.
///
/// @since 0.4.0
public enum LossKind {

    /// Text was shortened to fit a fixed-width field.
    TRUNCATED,

    /// Characters were replaced with different ones that the field admits.
    TRANSLITERATED,

    /// A value had no target and was discarded.
    DROPPED,

    /// A value was absent and something was supplied in its place.
    DEFAULTED,

    /// A value was forced into a type or range it did not naturally have.
    COERCED
}
