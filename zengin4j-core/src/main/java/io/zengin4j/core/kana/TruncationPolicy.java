package io.zengin4j.core.kana;

/**
 * What to do when text does not fit its field (R-K4).
 *
 * <p>The default refuses, because the alternative is the failure §16.2
 * describes: truncating between a base kana and its voicing mark turns ｶﾞ into
 * ｶ, and ガクブチ into カクブチ. Nothing in the resulting file indicates that
 * anything happened, and the payment is now addressed to a different name.
 *
 * @since 0.4.0
 */
public enum TruncationPolicy {
    /**
     * Refuse, and say by how much it overflowed. The default.
     *
     * <p>Shortening a payee's name is a decision about somebody's money, and
     * the caller is better placed to make it than a codec is.
     */
    REJECT_IF_TOO_LONG,

    /**
     * Shorten it, never severing a base kana from its voicing mark, and record
     * a {@code MATERIAL} loss.
     */
    TRUNCATE_SAFE,

    /**
     * As {@link #TRUNCATE_SAFE}, but end the result with a marker so a reader
     * of the file can see that something was removed.
     *
     * <p>The marker costs a byte of the field, so the text is shortened one
     * byte further than {@code TRUNCATE_SAFE} would.
     */
    TRUNCATE_WITH_MARKER
}
