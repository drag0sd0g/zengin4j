package io.zengin4j.core.error;

/**
 * A format descriptor is malformed, internally inconsistent, or violates the
 * verification protocol.
 *
 * <p>Raised for, among others: field lengths that do not sum to the declared
 * record length (R-F1), a duplicate field id, an unresolvable code-list
 * reference, and {@code verified: true} with fewer than two cited sources
 * (R-0.1). The same checks run as a build-time task, so in a healthy
 * repository this exception is only ever seen by someone editing a descriptor.
 *
 * @since 0.1.0
 */
public final class FormatDescriptorException extends ZenginException {
    private final String formatId;
    private final String problem;

    private FormatDescriptorException(String formatId, String problem, String messageEn, String messageJa) {
        super(messageEn, messageJa);
        this.formatId = formatId;
        this.problem = problem;
    }

    /**
     * Creates a diagnostic for a descriptor whose id is already known.
     *
     * @param formatId the descriptor id, for example {@code "sougou-furikomi"}
     * @param problem  a description of what is wrong and how to fix it
     * @return the exception, ready to throw
     */
    public static FormatDescriptorException forFormat(String formatId, String problem) {
        return new FormatDescriptorException(formatId, problem,
                "format descriptor '" + formatId + "': " + problem,
                "フォーマット定義 '" + formatId + "': " + problem);
    }

    /**
     * Creates a diagnostic for a descriptor resource that failed before its id
     * could be determined, for example a YAML syntax error.
     *
     * @param origin  the resource name or path being read
     * @param problem a description of what is wrong and how to fix it
     * @return the exception, ready to throw
     */
    public static FormatDescriptorException forResource(String origin, String problem) {
        return new FormatDescriptorException(origin, problem,
                "descriptor resource '" + origin + "': " + problem,
                "定義ファイル '" + origin + "': " + problem);
    }

    /**
     * Returns the descriptor id, or the resource name when the descriptor
     * failed before its id could be read.
     *
     * @return the format id or resource name, never {@code null}
     */
    public String formatId() {
        return formatId;
    }

    /**
     * Returns the description of the problem, without the location prefix.
     *
     * @return the problem description, never {@code null}
     */
    public String problem() {
        return problem;
    }
}
