/// Half-width katakana transliteration and dakuten-safe truncation (§16).
///
/// **The hazard this package exists for is one byte wide.** A
/// voiced character is a base kana followed by a separate voicing mark: ｶﾞ is
/// `0xB6 0xDE`. Cut between them and ガクブチ becomes カクブチ — a
/// different payee, in a file that records nothing about the change. Every
/// operation here either preserves the pair or refuses.
///
/// **The target field is an argument.** A long vowel becomes a
/// hyphen and [io.zengin4j.core.charset.CharacterClass#PAYROLL_NAME]
/// admits no symbols, so ヨーコ can be written into a 総合振込 file and not into
/// a 給与振込 one. Transliteration that did not know the field would be wrong for
/// one of them.
///
/// **Nothing is lost silently** (P5). Anything that changes what
/// a name says produces a [io.zengin4j.core.loss.LossEntry]; anything that
/// cannot be done raises rather than guesses. Kanji is always refused, because a
/// reading is ambiguous and a wrong one misroutes money (R-K6, P4).
///
/// @since 0.4.0
package io.zengin4j.core.kana;
