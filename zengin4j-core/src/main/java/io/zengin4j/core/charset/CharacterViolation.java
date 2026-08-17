package io.zengin4j.core.charset;

import java.util.Objects;

/**
 * One byte that a field's character class does not permit (R-C17).
 *
 * @param offset    where the byte is, relative to the buffer that was validated
 * @param value     the offending byte
 * @param permitted the class it failed
 * @since 0.1.0
 */
public record CharacterViolation(int offset, byte value, CharacterClass permitted) {
    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if the offset is negative
     */
    public CharacterViolation {
        Objects.requireNonNull(permitted, "permitted");
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative: " + offset);
        }
    }

    /**
     * Returns the offending byte as an unsigned value.
     *
     * @return {@code 0}–{@code 255}
     */
    public int unsignedValue() {
        return value & 0xFF;
    }

    /**
     * Whether this byte is the long vowel mark {@code ｰ} (0xB0).
     *
     * <p>Worth asking separately because it is the overwhelmingly common case
     * and the one with a specific fix: the standard writes a long vowel as
     * {@code -} (0x2D). The two glyphs are nearly identical, so the file looks
     * right and is rejected.
     *
     * @return {@code true} if the byte is {@code ｰ}
     */
    public boolean isProlongedSoundMark() {
        return unsignedValue() == 0xB0;
    }

    /**
     * An English description naming the byte, its position and — where there is
     * one — the correction.
     *
     * @return the description, never {@code null}
     */
    public String describeEn() {
        return "byte 0x%02X at offset %d is not permitted in %s%s"
                .formatted(unsignedValue(), offset, permitted.nameEn(), adviceEn());
    }

    /**
     * A Japanese description naming the byte, its position and the correction.
     *
     * @return the description, never {@code null}
     */
    public String describeJa() {
        return "オフセット %d のバイト 0x%02X は%sでは使用できません%s"
                .formatted(offset, unsignedValue(), permitted.nameJa(), adviceJa());
    }

    private String adviceEn() {
        if (isProlongedSoundMark()) {
            return "; the long vowel mark ｰ is never permitted — write a long vowel as - (0x2D)";
        }
        if (unsignedValue() >= 0xA7 && unsignedValue() <= 0xAF) {
            return "; small kana are not permitted — use the full-size character";
        }
        if (unsignedValue() >= 'a' && unsignedValue() <= 'z') {
            return "; lowercase Latin is not permitted — use uppercase";
        }
        if (unsignedValue() == 0xA6) {
            return "; ｦ is permitted only in EDI information";
        }
        return "";
    }

    private String adviceJa() {
        if (isProlongedSoundMark()) {
            return "。長音 ｰ は使用できません。長音はハイフン - (0x2D) で表記してください";
        }
        if (unsignedValue() >= 0xA7 && unsignedValue() <= 0xAF) {
            return "。小文字のカナは使用できません。大文字を使用してください";
        }
        if (unsignedValue() >= 'a' && unsignedValue() <= 'z') {
            return "。英小文字は使用できません。大文字を使用してください";
        }
        if (unsignedValue() == 0xA6) {
            return "。ｦ は EDI 情報でのみ使用できます";
        }
        return "";
    }
}
