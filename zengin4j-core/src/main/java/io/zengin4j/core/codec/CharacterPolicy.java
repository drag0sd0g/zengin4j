package io.zengin4j.core.codec;

/**
 * What a reader does about bytes a field's character class does not permit
 * (R-C13).
 *
 * <p>Orthogonal to {@link ParseMode}, which governs <em>structure</em>. A
 * record can be structurally perfect — right length, right discriminator,
 * numerics that parse — and still carry a name containing {@code ｰ}, which the
 * receiving institution will reject. The two questions are separate, so the two
 * settings are separate.
 *
 * <p>{@link #IGNORE} is the default, deliberately. Reading is for getting at
 * what a file says, including a file that is wrong — R-E1 makes malformed input
 * data rather than an error, and a reader that refused files on content would
 * make the library useless for the diagnostic case it exists to serve. Judging
 * content is the validation layer's job.
 *
 * @since 0.1.0
 */
public enum CharacterPolicy {

    /**
     * Do not check. The default: content is not the reader's business.
     */
    IGNORE,

    /**
     * Check, and report each violation as a warning naming the byte and its
     * offset. Reading continues and every record is returned.
     */
    WARN,

    /**
     * Check, and refuse the file at the first record carrying a violation, with
     * a {@link io.zengin4j.core.error.CharacterSetViolationException} naming
     * every violation in that record.
     *
     * <p>This is R-C13's strict mode. Use it when producing a file for
     * submission, where a character the bank rejects is worth failing over.
     */
    REJECT
}
