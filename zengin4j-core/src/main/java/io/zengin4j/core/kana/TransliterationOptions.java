package io.zengin4j.core.kana;

import io.zengin4j.core.charset.CharacterClass;
import io.zengin4j.core.charset.ZenginCharset;
import java.util.Objects;

/**
 * How a transliteration should behave when it cannot proceed cleanly.
 *
 * <p>Every default refuses. That is the shape P5 asks for: a conversion that
 * quietly shortened a payee's name, or quietly dropped a character from it,
 * produces a file indistinguishable from one that did not — and the caller is
 * better placed than a codec to decide whose name may be altered.
 *
 * <pre>{@code
 * TransliterationOptions options = TransliterationOptions.builder()
 *         .characterClass(CharacterClass.PARTY_NAME)
 *         .truncation(TruncationPolicy.TRUNCATE_SAFE)
 *         .build();
 * }</pre>
 *
 * @since 0.4.0
 */
public final class TransliterationOptions {

    private static final TransliterationOptions DEFAULTS = builder().build();

    private final CharacterClass characterClass;
    private final ZenginCharset charset;
    private final TruncationPolicy truncation;
    private final HiraganaPolicy hiragana;
    private final UnmappableCharacterPolicy unmappable;
    private final String truncationMarker;

    private TransliterationOptions(Builder builder) {
        this.characterClass = builder.characterClass;
        this.charset = builder.charset;
        this.truncation = builder.truncation;
        this.hiragana = builder.hiragana;
        this.unmappable = builder.unmappable;
        this.truncationMarker = builder.truncationMarker;
    }

    /**
     * The defaults: party names, MS932, and refuse rather than alter.
     *
     * @return the default options
     */
    public static TransliterationOptions defaults() {
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
     * The field class the output must satisfy.
     *
     * @return the character class
     */
    public CharacterClass characterClass() {
        return characterClass;
    }

    /**
     * The encoding lengths are measured in.
     *
     * @return the charset
     */
    public ZenginCharset charset() {
        return charset;
    }

    /**
     * What to do when the text is too long.
     *
     * @return the truncation policy
     */
    public TruncationPolicy truncation() {
        return truncation;
    }

    /**
     * What to do with hiragana.
     *
     * @return the hiragana policy
     */
    public HiraganaPolicy hiragana() {
        return hiragana;
    }

    /**
     * What to do with a character the field cannot hold in any form.
     *
     * @return the unmappable-character policy
     */
    public UnmappableCharacterPolicy unmappable() {
        return unmappable;
    }

    /**
     * The marker written at the end of text shortened under
     * {@link TruncationPolicy#TRUNCATE_WITH_MARKER}.
     *
     * <p>Must be a character the target field admits, which rules more out than
     * it looks: {@code *} is permitted by no name class at all, and
     * {@link io.zengin4j.core.charset.CharacterClass#PAYROLL_NAME} admits no
     * symbol whatever — so marked truncation is impossible in a payroll name
     * and says so rather than writing something the field rejects.
     *
     * @return the marker
     */
    public String truncationMarker() {
        return truncationMarker;
    }

    /**
     * Assembles options.
     *
     * @since 0.4.0
     */
    public static final class Builder {

        private CharacterClass characterClass = CharacterClass.PARTY_NAME;
        private ZenginCharset charset = ZenginCharset.defaultCharset();
        private TruncationPolicy truncation = TruncationPolicy.REJECT_IF_TOO_LONG;
        private HiraganaPolicy hiragana = HiraganaPolicy.REJECT;
        private UnmappableCharacterPolicy unmappable = UnmappableCharacterPolicy.REJECT;
        // A hyphen, because it is the only marker the name classes agree on.
        // '*' is permitted by none of them, '.' only by party names — and a
        // marker the field refuses turns a shortened name into a rejected file.
        private String truncationMarker = "-";

        private Builder() {
        }

        /**
         * Sets the field class the output must satisfy.
         *
         * <p>This is not a formality. A long vowel becomes a hyphen, and
         * {@link CharacterClass#PAYROLL_NAME} admits no symbols, so the same
         * name transliterates differently — or not at all — depending on which
         * field it is going into.
         *
         * @param value the class
         * @return this builder
         */
        public Builder characterClass(CharacterClass value) {
            this.characterClass = Objects.requireNonNull(value, "characterClass");
            return this;
        }

        /**
         * Sets the encoding lengths are measured in.
         *
         * @param value the charset
         * @return this builder
         */
        public Builder charset(ZenginCharset value) {
            this.charset = Objects.requireNonNull(value, "charset");
            return this;
        }

        /**
         * Sets what happens when the text is too long.
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
         * Sets the marker for {@link TruncationPolicy#TRUNCATE_WITH_MARKER}.
         *
         * @param value the marker; must be permitted by the field class
         * @return this builder
         * @throws IllegalArgumentException if the marker is empty
         */
        public Builder truncationMarker(String value) {
            Objects.requireNonNull(value, "truncationMarker");
            if (value.isEmpty()) {
                throw new IllegalArgumentException("the truncation marker must not be empty;"
                        + " use TruncationPolicy.TRUNCATE_SAFE for no marker");
            }
            this.truncationMarker = value;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public TransliterationOptions build() {
            return new TransliterationOptions(this);
        }
    }
}
