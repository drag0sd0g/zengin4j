package io.zengin4j.cli.command;

import io.zengin4j.cli.ExitCode;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.api.Iso20022Mapper;
import io.zengin4j.iso20022.api.MappingContext;
import io.zengin4j.iso20022.loss.MappingLossReport;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * {@code zengin dryrun} — what would converting this file cost?
 *
 * <p>R-I17: the loss report and nothing else. No file is produced, and this is
 * the one command that will not refuse whatever it finds — the point is to see
 * the loss, and stopping at the first critical entry would hide the rest of the
 * answer.
 *
 * <p>It serves the question somebody asks before committing to an integration:
 * run it over a month of real files and read what the conversion would do to
 * them. That is a better basis for a decision than a mapping table.
 *
 * @since 0.5.0
 */
@CommandLine.Command(
        name = "dryrun",
        mixinStandardHelpOptions = true,
        description = "Reports what converting a file to ISO 20022 would lose, without "
                + "converting it.")
public final class DryrunCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", paramLabel = "FILE",
            description = "The Zengin file to examine.")
    Path file;

    @CommandLine.Option(
            names = "--to",
            paramLabel = "TARGET",
            converter = ConvertCommand.TargetConverter.class,
            completionCandidates = ConvertCommand.TargetCandidates.class,
            description = "What the conversion would target. Only pain.001 exists so far; the "
                    + "option is here because a second message will not change the command.")
    ConvertCommand.Target to = ConvertCommand.Target.PAIN_001;

    @CommandLine.Option(
            names = "--out-format",
            paramLabel = "FORMAT",
            description = "Output shape: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    OutputFormat outFormat = OutputFormat.TEXT;

    @CommandLine.Option(
            names = "--language",
            paramLabel = "en|ja",
            description = "Language for the report. Defaults to the JVM locale.")
    String language;

    @CommandLine.Mixin
    ReadingOptions reading = new ReadingOptions();

    @CommandLine.Mixin
    MappingOptions mapping = new MappingOptions();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!Files.isReadable(file)) {
            err.println("cannot read " + file);
            return ExitCode.IO.value();
        }
        if (to != ConvertCommand.Target.PAIN_001) {
            err.println("dryrun examines a Zengin file on its way to ISO 20022. To see what "
                    + "converting XML back would cost, run convert with --accept-loss and read "
                    + "the report.");
            return ExitCode.USAGE.value();
        }

        MappingContext context = mapping.toContext(err, false, reading.charset());
        if (context == null) {
            return ExitCode.USAGE.value();
        }

        try {
            ZenginFile source = ZenginReaders.readFile(file, reading.toReaderOptions(err));
            MappingLossReport loss = Iso20022Mapper.create().dryRun(source, context);

            out.print(outFormat == OutputFormat.JSON ? loss.toJson() : loss.toText(locale()));

            if (loss.hasAtLeast(LossSeverity.CRITICAL)) {
                return ExitCode.ERRORS.value();
            }
            return loss.isLossless() ? ExitCode.OK.value() : ExitCode.WARNINGS.value();
        } catch (ZenginException unreadable) {
            err.println(unreadable.getLocalizedMessage());
            return ExitCode.ERRORS.value();
        }
    }

    private Locale locale() {
        if (language == null) {
            return Locale.getDefault();
        }
        return "ja".equalsIgnoreCase(language) ? Locale.JAPANESE : Locale.ENGLISH;
    }
}
