package io.zengin4j.cli;

/// What the process returns, and what each value means (R-CLI1).
///
/// These are a contract. A script that treats a non-zero exit as "stop" is
/// doing the right thing, and changing what a code means would silently change
/// what that script does — so the values are fixed and documented rather than
/// incidental.
///
/// **[#WARNINGS] is deliberately non-zero.** That is
/// unusual: most tools exit `0` when they merely have something to say. A
/// payment file is not most things. Every warning here describes something an
/// institution will accept and a human would probably want to change — a
/// duplicated row, a zero-amount payment, a name field left blank — and the
/// cost of the two mistakes is not symmetric. A file stopped for a warning costs
/// somebody a minute; a wrong file that went through costs a reversal. Pipelines
/// that genuinely want to proceed can say so:
///
/// ```java
/// zengin validate payments.txt || [ $? -eq 1 ]
/// ```
///
/// @since 0.3.0
public enum ExitCode {

    /// Nothing to report.
    OK(0),

    /// Warnings, but nothing that blocks submission.
    WARNINGS(1),

    /// Errors. The file is not fit to send.
    ERRORS(2),

    /// The command line itself was wrong: unknown option, missing argument.
    USAGE(3),

    /// A file could not be read or written.
    IO(4);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    /// Returns the number the process exits with.
    ///
    /// @return the exit status
    public int value() {
        return value;
    }
}
