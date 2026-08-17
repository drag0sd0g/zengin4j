package io.zengin4j.cli.command;

/**
 * What shape output takes (R-CLI2).
 *
 * <p>Every command offers {@link #TEXT} and {@link #JSON}. {@link #SARIF} is
 * meaningful only where there are findings to place against a file, so only
 * {@code validate} accepts it.
 *
 * @since 0.3.0
 */
public enum OutputFormat {
    /** For a person, in their terminal. */
    TEXT,

    /** For a machine, and stable enough to diff between runs. */
    JSON,

    /** For a code-scanning consumer: GitHub, GitLab and Azure DevOps all render it. */
    SARIF
}
