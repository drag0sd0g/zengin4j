package io.zengin4j.cli;

import module java.base;
import io.zengin4j.cli.command.ConvertCommand;
import io.zengin4j.cli.command.DiffCommand;
import io.zengin4j.cli.command.DryrunCommand;
import io.zengin4j.cli.command.ExplainCommand;
import io.zengin4j.cli.command.GenerateCommand;
import io.zengin4j.cli.command.InspectCommand;
import io.zengin4j.cli.command.ValidateCommand;
import io.zengin4j.cli.internal.CliMessages;
import io.zengin4j.core.error.UnverifiedFormatException;
import io.zengin4j.core.error.ZenginException;
import picocli.CommandLine;

/// The `zengin` command.
///
/// ```java
/// zengin validate payments.txt --calendar=bundled
/// zengin inspect  payments.txt --annotate --record=2
/// zengin generate --format=sougou-furikomi --count=100 --seed=42 --out=test.txt
/// zengin diff     before.txt after.txt
/// zengin explain  --format=sougou-furikomi --field=beneficiaryName
/// ```
///
/// **Nothing this command prints contains a full account number unless
/// you ask for one** (R-CLI4). Terminal output is scrollback, CI output is
/// somebody else's storage for ever, and a diagnostic tool that leaks payment
/// data by default is a tool people are right not to run. `--unsafe-print`
/// exists, is spelled that way on purpose, and prints a warning to stderr.
///
/// Exit codes are a contract; see [ExitCode].
///
/// @since 0.3.0
@CommandLine.Command(
        name = "zengin",
        mixinStandardHelpOptions = true,
        versionProvider = Zengin.Version.class,
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {
            ValidateCommand.class,
            InspectCommand.class,
            ConvertCommand.class,
            DryrunCommand.class,
            GenerateCommand.class,
            DiffCommand.class,
            ExplainCommand.class,
        },
        description = "Reads, checks and explains 全銀協規定形式 fixed-length payment files.",
        footer = {
            "",
            "Exit codes: 0 clean, 1 warnings only, 2 errors, 3 usage error, 4 I/O failure.",
            "",
            "No command prints a full account number unless --unsafe-print is given.",
        })
public final class Zengin implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    /// Creates the top-level command.
    ///
    /// Public because picocli instantiates it, and because [#run] is
    /// the supported way in — construct this only if you are embedding the
    /// command tree in another picocli application.
    public Zengin() {
    }

    /// Runs the command line and exits with the resulting status.
    ///
    /// @param args the command line arguments
    public static void main(String[] args) {
        var out = new PrintWriter(new java.io.OutputStreamWriter(
                System.out, StandardCharsets.UTF_8), true);
        var err = new PrintWriter(new java.io.OutputStreamWriter(
                System.err, StandardCharsets.UTF_8), true);
        int status = run(args, out, err);
        out.flush();
        err.flush();
        System.exit(status);
    }

    /// Runs the command line against supplied writers and returns the status.
    ///
    /// Separate from [#main] so that tests exercise the real command
    /// objects, the real parser and the real exit-code mapping without spawning
    /// a process or capturing global streams. A CLI whose behaviour is only
    /// testable end-to-end tends to be a CLI whose edge cases are untested.
    ///
    /// @param args the command line arguments
    /// @param out  where normal output goes
    /// @param err  where diagnostics go
    /// @return the exit status; see [ExitCode]
    public static int run(String[] args, PrintWriter out, PrintWriter err) {
        var commandLine = new CommandLine(new Zengin())
                .setOut(out)
                .setErr(err)
                // No ANSI colour, ever. picocli's AUTO mode decided the Windows
                // CI runners were a colour terminal and wrote escape codes into
                // the usage text, where they are invisible to a person and very
                // visible to anything reading the output.
                //
                // The obvious alternative — detect a terminal — is not portable
                // across this project's own matrix. `System.console() != null`
                // means "not redirected" on JDK 21, but JDK 22 changed it to
                // return a Console even when redirected, with the real check
                // moving to Console.isTerminal(); this module compiles with
                // --release 21 and cannot call that. A heuristic that answers
                // differently on JDK 21 and JDK 25 is the platform-dependence
                // this library exists to pin down.
                //
                // And on the merits it is the right default anyway. This tool's
                // output is a diagnostic dump that gets piped to files, diffed,
                // and pasted into tickets and CI logs; escape codes are noise in
                // all of those, and the field table carries its structure in the
                // alignment rather than in colour. If colour is ever wanted, an
                // explicit --color flag is the honest way to ask for it.
                .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF))
                .setCaseInsensitiveEnumValuesAllowed(true)
                // Without this, a mistyped option produces a stack trace and a
                // usage error is indistinguishable from a crash.
                .setParameterExceptionHandler((exception, parsed) -> {
                    CommandLine failing = exception.getCommandLine();
                    failing.getErr().println(exception.getMessage());
                    CommandLine.UnmatchedArgumentException
                            .printSuggestions(exception, failing.getErr());
                    failing.getErr().println(failing.getCommandSpec().commandLine().getUsageMessage());
                    return ExitCode.USAGE.value();
                })
                .setExecutionExceptionHandler((exception, failing, parsed) -> {
                    failing.getErr().println(describe(exception));
                    return statusFor(exception);
                });

        try {
            return commandLine.execute(args);
        } catch (Throwable fatal) {
            // picocli's handler catches Exception, not Error. Without this an
            // OutOfMemoryError escapes uncaught, the JVM exits 1, and 1 already
            // means "the files differ" — so a crashed `zengin diff` would tell a
            // script the comparison had succeeded and found changes. Reporting
            // an internal failure as an error is a smaller lie than that.
            err.println("internal error: " + describe(fatal)
                    + "\nThis is a defect in zengin4j, not in your file. "
                    + "Please report it at https://github.com/drag0sd0g/zengin4j/issues");
            return ExitCode.ERRORS.value();
        }
    }

    /// States what went wrong in one line, in the terms the caller used.
    ///
    /// A stack trace is the right output for a defect in this library and the
    /// wrong output for a missing file. Only the second kind reaches here in
    /// normal use, so the message is the message.
    private static String describe(Throwable exception) {
        // The library's remedy is a Java API call, which is the right advice
        // for a caller writing code and useless advice at a shell prompt. R-E3
        // asks a diagnostic to say how to fix it, so the fix is restated in the
        // terms the reader actually has (R-CLI6).
        return switch (exception) {
            case UnverifiedFormatException unverified ->
                    "format '" + unverified.formatId() + "' has a byte layout that no two"
                            + " independent published sources confirm, so reading it may silently"
                            + " misread financial instructions."
                            + "\nPass --allow-unverified to proceed, and check the output against"
                            + " your own institution's specification."
                            + "\n`zengin explain --format=" + unverified.formatId() + "` shows the"
                            + " layout and its sources.";
            case ZenginException zengin -> CliMessages.forTheCommandLine(zengin.messageEn());
            // getMessage() on this one is the bare path, which printed alone
            // reads like a success line rather than a failure.
            case java.nio.file.NoSuchFileException missing ->
                    "no such file or directory: " + missing.getFile();
            case java.nio.file.AccessDeniedException denied ->
                    "permission denied: " + denied.getFile();
            case UncheckedIOException unchecked -> describe(unchecked.getCause());
            case IOException io -> "I/O error: " + (io.getMessage() == null
                    ? io.getClass().getSimpleName() : io.getMessage());
            case null -> "failed for a reason it did not record";
            default -> exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : CliMessages.forTheCommandLine(exception.getMessage());
        };
    }

    private static int statusFor(Exception exception) {
        if (exception instanceof IOException || exception instanceof UncheckedIOException) {
            return ExitCode.IO.value();
        }
        if (exception instanceof IllegalArgumentException) {
            // A format id nobody has, a seed that is not a number: the command
            // line parsed, but what it asked for cannot be done.
            return ExitCode.USAGE.value();
        }
        return ExitCode.ERRORS.value();
    }

    /// Prints usage when invoked with no subcommand.
    ///
    /// @return [ExitCode#USAGE], because a bare `zengin` asked for
    ///   nothing and a zero exit would tell a script it had succeeded
    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return ExitCode.USAGE.value();
    }

    /// Reports the version from the jar manifest, falling back for a source build.
    static final class Version implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            Package pkg = Zengin.class.getPackage();
            String version = pkg == null ? null : pkg.getImplementationVersion();
            return new String[] {
                "zengin4j " + (version == null ? "(development build)" : version),
            };
        }
    }
}
