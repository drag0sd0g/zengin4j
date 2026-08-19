package io.zengin4j.core.codec;

/// What to do when a value contains characters its field cannot hold (R-C18).
///
/// Distinct from [CharacterPolicy], which is the read side. Reading a
/// character the standard forbids is a fact about somebody else's file and worth
/// reporting; *writing* one is this library producing a file it would
/// itself reject, and the default therefore refuses.
///
/// @since 0.4.0
public enum CharacterWritePolicy {

    /// Refuse, naming the characters. The default.
    ///
    /// The value came from the caller, who knows what it was meant to be. A
    /// codec choosing a substitute on its own is a codec renaming a payee.
    REJECT,

    /// Convert the value with the transliteration engine (§16).
    ///
    /// Full-width becomes half-width, a long vowel becomes a hyphen, a small
    /// kana becomes its full-size form. Everything it changes is recorded, and
    /// anything it cannot convert — kanji, most obviously — still refuses.
    TRANSLITERATE,

    /// Replace each offending character with a configured byte, and record it.
    ///
    /// A last resort, for a caller feeding a system that will accept a
    /// mangled name rather than nothing. It is recorded, and the record is the
    /// point: somebody has to be able to see which names were damaged.
    REPLACE
}
