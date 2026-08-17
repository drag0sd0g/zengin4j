package io.zengin4j.core.codec;

import io.zengin4j.core.kana.HiraganaPolicy;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import java.util.Objects;

/**
 * How a record's fields should be encoded (R-C18).
 *
 * <p>Defaults to refusing anything the field cannot hold. That is the shape P5
 * asks for: a value the standard forbids is one the caller can still fix, and
 * the moment it becomes bytes in a file it is somebody else's problem to
 * diagnose.
 *
 * <pre>{@code
 * EncodingOptions options = EncodingOptions.builder()
 *         .characters(CharacterWritePolicy.TRANSLITERATE)
 *         .truncation(TruncationPolicy.TRUNCATE_SAFE)
 *         .build();
 * }</pre>
 *
 * @since 0.4.0
 */
public final class EncodingOptions {
    private static final EncodingOptions DEFAULTS = builder().build();

    private final CharacterWritePolicy characters;
    private final byte replacement;
    private final TruncationPolicy truncation;
    private final HiraganaPolicy hiragana;
    private final UnmappableCharacterPolicy unmappable;
    private final boolean checkCharacters;

    private EncodingOptions(Builder builder) {
        this.characters = builder.characters;
        this.replacement = builder.replacement;
        this.truncation = builder.truncation;
        this.hiragana = builder.hiragana;
        this.unmappable = builder.unmappable;
        this.checkCharacters = builder.checkCharacters;
    }

    /**
     * Refuse anything the field cannot hold.
     *
     * @return the default options
     */
    public static EncodingOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Starts configuring.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * What to do with characters the field cannot hold.
     *
     * @return the policy
     */
    public CharacterWritePolicy characters() {
        return characters;
    }

    /**
     * The byte written in place of a refused character under
     * {@link CharacterWritePolicy#REPLACE}.
     *
     * @return the replacement byte
     */
    public byte replacement() {
        return replacement;
    }

    /**
     * What to do when transliterated text is too long.
     *
     * @return the truncation policy
     */
    public TruncationPolicy truncation() {
        return truncation;
    }

    /**
     * What to do with hiragana while transliterating.
     *
     * @return the hiragana policy
     */
    public HiraganaPolicy hiragana() {
        return hiragana;
    }

    /**
     * What to do with a character that has no permitted form.
     *
     * @return the policy
     */
    public UnmappableCharacterPolicy unmappable() {
        return unmappable;
    }

    /**
     * Whether the character check runs at all.
     *
     * @return {@code false} only when a caller has deliberately turned it off
     */
    public boolean checkCharacters() {
        return checkCharacters;
    }

    /**
     * Assembles encoding options.
     *
     * @since 0.4.0
     */
    public static final class Builder {
        private CharacterWritePolicy characters = CharacterWritePolicy.REJECT;
        private byte replacement = ' ';
        private TruncationPolicy truncation = TruncationPolicy.REJECT_IF_TOO_LONG;
        private HiraganaPolicy hiragana = HiraganaPolicy.REJECT;
        private UnmappableCharacterPolicy unmappable = UnmappableCharacterPolicy.REJECT;
        private boolean checkCharacters = true;

        private Builder() {
        }

        /**
         * Sets what happens to characters the field cannot hold.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder characters(CharacterWritePolicy value) {
            this.characters = Objects.requireNonNull(value, "characters");
            return this;
        }

        /**
         * Sets the byte written in place of a refused character.
         *
         * @param value the replacement; must be one the field class permits
         * @return this builder
         */
        public Builder replacement(byte value) {
            this.replacement = value;
            return this;
        }

        /**
         * Sets what happens when transliterated text is too long.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder truncation(TruncationPolicy value) {
            this.truncation = Objects.requireNonNull(value, "truncation");
            return this;
        }

        /**
         * Sets what happens to hiragana.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder hiragana(HiraganaPolicy value) {
            this.hiragana = Objects.requireNonNull(value, "hiragana");
            return this;
        }

        /**
         * Sets what happens to a character with no permitted form.
         *
         * @param value the policy
         * @return this builder
         */
        public Builder unmappable(UnmappableCharacterPolicy value) {
            this.unmappable = Objects.requireNonNull(value, "unmappable");
            return this;
        }

        /**
         * Writes the value as given, without checking it (R-L1 fixtures).
         *
         * <p><strong>For building files that are deliberately wrong.</strong> A
         * validator's test suite has to be able to produce the very records the
         * validator exists to complain about, and going through the ordinary
         * encoder cannot do that — it refuses them, correctly.
         *
         * <p>Not a fourth write policy. R-C18 names three, and all three
         * produce files the standard accepts; this produces one it does not,
         * which is why it is a separate switch with an unattractive name rather
         * than an option sitting alongside them.
         *
         * @return this builder
         */
        public Builder withoutCharacterChecks() {
            this.checkCharacters = false;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public EncodingOptions build() {
            return new EncodingOptions(this);
        }
    }
}
