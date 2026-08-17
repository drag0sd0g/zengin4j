package io.zengin4j.cli;

import io.zengin4j.cli.command.DiffCommand;
import io.zengin4j.cli.command.ExplainCommand;
import io.zengin4j.cli.command.GenerateCommand;
import io.zengin4j.cli.command.InspectCommand;
import io.zengin4j.cli.command.ValidateCommand;
import io.zengin4j.cli.internal.CliMessages;
import io.zengin4j.core.error.UnverifiedFormatException;
import io.zengin4j.core.error.ZenginException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * The {@code zengin} command.
 *
 * <pre>{@code
 * zengin validate payments.txt --calendar=bundled
 * zengin inspect  payments.txt --annotate --record=2
 * zengin generate --format=sougou-furikomi --count=100 --seed=42 --out=test.txt
 * zengin diff     before.txt after.txt
 * zengin explain  --format=sougou-furikomi --field=beneficiaryName
 * }</pre>
 *
 * <p><strong>Nothing this command prints contains a full account number unless
 * you ask for one</strong> (R-CLI4). Terminal output is scrollback, CI output is
 * somebody else's storage for ever, and a diagnostic tool that leaks payment
 * data by default is a tool people are right not to run. {@code --unsafe-print}
 * exists, is spelled that way on purpose, and prints a warning to stderr.
 *
 * <p>Exit codes are a contract; see {@link ExitCode}.
 *
 * @since 0.3.0
 */
@CommandLine.Command(
        name = "zengin",
        mixinStandardHelpOptions = true,
        versionProvider = Zengin.Version.class,
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {
            ValidateCommand.class,
            InspectCommand.class,
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

    /**
     * Creates the top-level command.
     *
     * <p>Public because picocli instantiates it, and because {@link #run} is
     * the supported way in — construct this only if you are embedding the
     * command tree in another picocli application.
     */
    public Zengin() {
    }

    /**
     * Runs the command line and exits with the resulting status.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(new java.io.OutputStreamWriter(
                System.out, StandardCharsets.UTF_8), true);
        PrintWriter err = new PrintWriter(new java.io.OutputStreamWriter(
                System.err, StandardCharsets.UTF_8), true);
        int status = run(args, out, err);
        out.flush();
        err.flush();
        System.exit(status);
    }

    /**
     * Runs the command line against supplied writers and returns the status.
     *
     * <p>Separate from {@link #main} so that tests exercise the real command
     * objects, the real parser and the real exit-code mapping without spawning
     * a process or capturing global streams. A CLI whose behaviour is only
     * testable end-to-end tends to be a CLI whose edge cases are untested.
     *
     * @param args the command line arguments
     * @param out  where normal output goes
     * @param err  where diagnostics go
     * @return the exit status; see {@link ExitCode}
     */
    public static int run(String[] args, PrintWriter out, PrintWriter err) {
        CommandLine commandLine = new CommandLine(new Zengin())
                .setOut(out)
                .setErr(err)
                .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.OFF))
                .setCaseInsensitiveEnumValuesAllowed(true)
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
            err.println("internal error: " + describe(fatal)
                    + "\nThis is a defect in zengin4j, not in your file. "
                    + "Please report it at https://github.com/drag0sd0g/zengin4j/issues");
            return ExitCode.ERRORS.value();
        }
    }

    /**
     * States what went wrong in one line, in the terms the caller used.
     *
     * <p>A stack trace is the right output for a defect in this library and the
     * wrong output for a missing file. Only the second kind reaches here in
     * normal use, so the message is the message.
     */
    private static String describe(Throwable exception) {
        if (exception instanceof UnverifiedFormatException unverified) {
            return "format '" + unverified.formatId() + "' has a byte layout that no two"
                    + " independent published sources confirm, so reading it may silently misread"
                    + " financial instructions."
                    + "\nPass --allow-unverified to proceed, and check the output against your"
                    + " own institution's specification."
                    + "\n`zengin explain --format=" + unverified.formatId() + "` shows the layout"
                    + " and its sources.";
        }
        if (exception instanceof ZenginException zengin) {
            return CliMessages.forTheCommandLine(zengin.messageEn());
        }
        if (exception instanceof java.nio.file.NoSuchFileException missing) {
            return "no such file or directory: " + missing.getFile();
        }
        if (exception instanceof java.nio.file.AccessDeniedException denied) {
            return "permission denied: " + denied.getFile();
        }
        if (exception instanceof UncheckedIOException unchecked) {
            return describe(unchecked.getCause());
        }
        if (exception instanceof IOException) {
            String message = exception.getMessage();
            return "I/O error: " + (message == null ? exception.getClass().getSimpleName() : message);
        }
        String message = exception.getMessage();
        return message == null
                ? exception.getClass().getSimpleName()
                : CliMessages.forTheCommandLine(message);
    }

    private static int statusFor(Exception exception) {
        if (exception instanceof IOException || exception instanceof UncheckedIOException) {
            return ExitCode.IO.value();
        }
        if (exception instanceof IllegalArgumentException) {
            return ExitCode.USAGE.value();
        }
        return ExitCode.ERRORS.value();
    }

    /**
     * Prints usage when invoked with no subcommand.
     *
     * @return {@link ExitCode#USAGE}, because a bare {@code zengin} asked for
     *         nothing and a zero exit would tell a script it had succeeded
     */
    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return ExitCode.USAGE.value();
    }

    /** Reports the version from the jar manifest, falling back for a source build. */
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
