package io.zengin4j.core.kana;

/// What to do with a character that has no permitted form in the target field.
///
/// The case that makes this necessary is a long vowel in a payroll name.
/// `ー` becomes `-`, and `PAYROLL_NAME` admits no symbols at
/// all — so ヨーコ has no legal half-width spelling in a 給与振込 file. Something
/// has to give, and the caller should choose which.
///
/// Note that this is not the same question as [TruncationPolicy]: there
/// the text is right and too long, here the text will not go in at any length.
///
/// @since 0.4.0
public enum UnmappableCharacterPolicy {

    /// Refuse, naming the characters and the field class that refuses them. The
    /// default.
    ///
    /// Consistent with P5: dropping a character from a payee's name changes
    /// who the reader thinks is being paid, and a codec should not make that
    /// choice on its own.
    REJECT,

    /// Drop the character and record a `MATERIAL` loss.
    ///
    /// For a caller who would rather send ﾖｺ than nothing, and who is
    /// recording the loss report alongside the file.
    DROP
}
