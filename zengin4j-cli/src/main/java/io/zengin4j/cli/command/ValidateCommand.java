package io.zengin4j.cli.command;

import module java.base;
import io.zengin4j.cli.ExitCode;
import io.zengin4j.cli.internal.CliMessages;
import io.zengin4j.validation.ZenginValidator;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import io.zengin4j.validation.calendar.BusinessCalendar;
import io.zengin4j.validation.calendar.JapaneseBankCalendar;
import picocli.CommandLine;

/// `zengin validate` — checks a file and says what is wrong with it.
///
/// A front end over `zengin4j-validation`, which does the work. What
/// this command adds is the part a pipeline needs: an exit status that
/// distinguishes "fine", "worth a look" and "do not send this", and output in a
/// shape a machine can read.
///
/// It never throws for a bad file. A file that cannot be parsed at all
/// produces a `V-100` finding and exit status 2, because "this is not a
/// file I can read" is an answer to the question that was asked.
///
/// @since 0.3.0
@CommandLine.Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Checks a file against the rule set and reports what is wrong with it.")
public final class ValidateCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", paramLabel = "FILE", description = "The file to check.")
    Path file;

    @CommandLine.Mixin
    ReadingOptions reading = new ReadingOptions();

    @CommandLine.Option(
            names = "--out-format",
            paramLabel = "FORMAT",
            description = "Output shape: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    OutputFormat outFormat = OutputFormat.TEXT;

    @CommandLine.Option(
            names = "--suppress",
            paramLabel = "ID",
            split = ",",
            description = "Rule ids to turn off, e.g. --suppress=V-306,V-605. "
                    + "See docs/validation-rules.md.")
    List<String> suppress = List.of();

    @CommandLine.Option(
            names = "--calendar",
            paramLabel = "FILE|bundled",
            description = "Enable the V-5xx date rules. 'bundled' uses the built-in Japanese bank "
                    + "calendar; otherwise a holiday CSV. Off entirely if omitted.")
    String calendar;

    @CommandLine.Option(
            names = "--language",
            paramLabel = "en|ja",
            description = "Language for findings. Defaults to the JVM locale.")
    String language;

    @CommandLine.Option(
            names = "--unsafe-print",
            description = "Show account numbers in full. Findings reach logs and CI annotations, "
                    + "so this is off by default (R-CLI4).")
    boolean unsafePrint;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!Files.isReadable(file)) {
            err.println("cannot read " + file);
            return ExitCode.IO.value();
        }
        if (unsafePrint) {
            err.println("warning: --unsafe-print shows account numbers in full; "
                    + "this output must not reach a shared log.");
        }

        ZenginValidator.Builder builder = ZenginValidator.builder()
                .unmaskSensitiveValues(unsafePrint);
        suppress.forEach(builder::suppress);
        BusinessCalendar businessCalendar = calendar(err);
        if (businessCalendar != null) {
            builder.withCalendar(businessCalendar);
        }

        ValidationReport report = builder.build()
                .validate(file, reading.toReaderOptions(err));

        // Translated on the way out, not only in the exception handler: this
        // command never throws for a bad file — a file that cannot be read
        // becomes a V-100 finding and goes to stdout as part of the report
        // (R-V1). That path carries the library's Java remedy with it, and the
        // exception handler never sees it.
        switch (outFormat) {
            case TEXT -> out.print(CliMessages.forTheCommandLine(report.toText(locale())));
            case JSON -> out.print(CliMessages.forTheCommandLine(report.toJson()));
            case SARIF -> out.print(CliMessages.forTheCommandLine(report.toSarif(file.toString())));
        }
        return statusOf(report);
    }

    /// Maps a report to an exit status (R-CLI1).
    ///
    /// Errors outrank warnings, and warnings outrank a clean run. Information
    /// findings do not affect the status: `V-505` telling you the value
    /// date is inside the calendar's horizon is not a reason to stop a pipeline.
    private static int statusOf(ValidationReport report) {
        if (report.counts().getOrDefault(Severity.ERROR, 0) > 0) {
            return ExitCode.ERRORS.value();
        }
        if (report.counts().getOrDefault(Severity.WARNING, 0) > 0) {
            return ExitCode.WARNINGS.value();
        }
        return ExitCode.OK.value();
    }

    private Locale locale() {
        if (language == null) {
            return Locale.getDefault();
        }
        return "ja".equalsIgnoreCase(language) ? Locale.JAPANESE : Locale.ENGLISH;
    }

    /// Resolves `--calendar`, or `null` when the caller did not ask
    /// for one. The tier-5 rules then do not run at all, which is R-V6's design
    /// rather than a silent skip.
    private BusinessCalendar calendar(PrintWriter err) {
        if (calendar == null) {
            return null;
        }
        if ("bundled".equalsIgnoreCase(calendar)) {
            JapaneseBankCalendar bundled = JapaneseBankCalendar.bundled();
            err.println("using the bundled Japanese bank calendar, valid to "
                    + bundled.validUntil() + ".");
            return bundled;
        }
        var source = Path.of(calendar);
        JapaneseBankCalendar loaded = JapaneseBankCalendar.fromCsv(source);
        err.println("using holiday data from " + source + ", valid to " + loaded.validUntil() + ".");
        return loaded;
    }
}
