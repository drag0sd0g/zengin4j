package io.zengin4j.core.kana;

/// What to do with hiragana input (R-K5).
///
/// Hiragana has an unambiguous katakana equivalent, so converting it is safe
/// in a way that kanji never is. It is still refused by default: a name arriving
/// in hiragana usually means the upstream system sent the wrong field, and
/// converting it silently would hide that.
///
/// @since 0.4.0
public enum HiraganaPolicy {

    /// Refuse, naming the characters. The default.
    REJECT,

    /// Convert to katakana and record a `MATERIAL` loss. Never silent.
    CONVERT
}
