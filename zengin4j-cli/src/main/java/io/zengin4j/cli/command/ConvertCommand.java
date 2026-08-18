package io.zengin4j.cli.command;

import io.zengin4j.cli.ExitCode;
import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.api.Iso20022Mapper;
import io.zengin4j.iso20022.api.MappingContext;
import io.zengin4j.iso20022.api.MappingFailedException;
import io.zengin4j.iso20022.api.MappingResult;
import io.zengin4j.iso20022.envelope.ZediEnvelopeReader;
import io.zengin4j.iso20022.envelope.ZediEnvelopeWriter;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.loss.MappingLossReport;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * {@code zengin convert} — a Zengin file becomes a {@code pain.001}, or back.
 *
 * <p>The loss report is always produced, whatever else happens. That is not a
 * stylistic choice: R-I14 makes the report inescapable in the library, and a
 * command that printed the converted file and swallowed the report would undo
 * that at the last step. There is no flag that turns it off — only
 * {@code --loss-out}, which says where it goes.
 *
 * <p>By default it goes to stderr, so
 * {@code zengin convert x.txt --to=pain.001 > out.xml} produces a usable file
 * and still tells you what it cost. The reader's own warnings go to stderr too,
 * which is fine for reading and no good for parsing — hence
 * {@code --loss-out=report.json}.
 *
 * <p>Exit status follows the same shape as {@code validate} (R-CLI1): 0 when
 * nothing was lost, 1 when something was, 2 when the conversion refused because
 * the loss could misroute money.
 *
 * @since 0.5.0
 */
@CommandLine.Command(
        name = "convert",
        mixinStandardHelpOptions = true,
        description = "Converts between a Zengin file and ISO 20022 pain.001, reporting what "
                + "the conversion loses.")
public final class ConvertCommand implements Callable<Integer> {

    /** What shell completion should offer for {@code --to}. */
    static final class TargetCandidates extends java.util.ArrayList<String> {
        private static final long serialVersionUID = 1L;

        TargetCandidates() {
            super(java.util.List.of("pain.001", "zengin"));
        }
    }

    /**
     * Which direction to convert in.
     *
     * <p>Spelled as §27 spells it — {@code --to=pain.001}, not
     * {@code --to=PAIN_001}. A message identifier has dots in it and an enum
     * constant cannot, so the two are kept apart rather than the user being
     * asked to learn a Java naming convention.
     */
    enum Target {
        /** Zengin fixed-length to {@code pain.001.001.03}. */
        PAIN_001("pain.001"),
        /** {@code pain.001.001.03} to Zengin fixed-length. */
        ZENGIN("zengin");

        private final String spelling;

        Target(String spelling) {
            this.spelling = spelling;
        }

        @Override
        public String toString() {
            return spelling;
        }

        static Target parse(String value) {
            for (Target candidate : values()) {
                if (candidate.spelling.equalsIgnoreCase(value)
                        || candidate.name().equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("expected pain.001 or zengin, not '" + value + "'");
        }
    }

    /** Turns {@code pain.001} into a {@link Target}. */
    static final class TargetConverter implements CommandLine.ITypeConverter<Target> {
        @Override
        public Target convert(String value) {
            return Target.parse(value);
        }
    }

    @CommandLine.Parameters(index = "0", paramLabel = "FILE", description = "The file to convert.")
    Path file;

    @CommandLine.Option(
            names = "--to",
            paramLabel = "TARGET",
            required = true,
            converter = TargetConverter.class,
            completionCandidates = TargetCandidates.class,
            description = "What to convert to: pain.001 or zengin.")
    Target to;

    @CommandLine.Option(
            names = "--out",
            paramLabel = "FILE",
            description = "Where to write the result. Standard output if omitted.")
    Path out;

    @CommandLine.Option(
            names = "--loss-out",
            paramLabel = "FILE",
            description = "Where to write the loss report. Standard error if omitted — which is "
                    + "fine for reading and no good for parsing, because the reader's warnings "
                    + "go there too.")
    Path lossOut;

    @CommandLine.Option(
            names = "--loss-format",
            paramLabel = "FORMAT",
            description = "Shape of the loss report: ${COMPLETION-CANDIDATES}. "
                    + "Default: ${DEFAULT-VALUE}.")
    OutputFormat lossFormat = OutputFormat.TEXT;

    @CommandLine.Option(
            names = "--language",
            paramLabel = "en|ja",
            description = "Language for the loss report. Defaults to the JVM locale.")
    String language;

    @CommandLine.Mixin
    ReadingOptions reading = new ReadingOptions();

    @CommandLine.Mixin
    MappingOptions mapping = new MappingOptions();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter stdout = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (!Files.isReadable(file)) {
            err.println("cannot read " + file);
            return ExitCode.IO.value();
        }

        MappingContext context = mapping.toContext(err, to == Target.ZENGIN, reading.charset());
        if (context == null) {
            return ExitCode.USAGE.value();
        }

        try {
            return to == Target.PAIN_001
                    ? toIso(stdout, err, context)
                    : toZengin(stdout, err, context);
        } catch (MappingFailedException refused) {
            err.println(refused.getMessage());
            err.println();
            try {
                writeReport(err, refused.loss());
            } catch (IOException unwritable) {
                err.println("cannot write " + lossOut + ": " + unwritable.getMessage());
                return ExitCode.IO.value();
            }
            return ExitCode.ERRORS.value();
        } catch (ZenginException unreadable) {
            err.println(unreadable.getLocalizedMessage());
            return ExitCode.ERRORS.value();
        } catch (IOException e) {
            err.println("cannot write " + out + ": " + e.getMessage());
            return ExitCode.IO.value();
        }
    }

    private int toIso(PrintWriter stdout, PrintWriter err, MappingContext context)
            throws IOException {
        ZenginFile source = ZenginReaders.readFile(file, reading.toReaderOptions(err));
        MappingResult<ZediFile> converted = Iso20022Mapper.create().toIso(source, context);

        byte[] bytes = ZediEnvelopeWriter.toByteArray(converted.output());
        if (out == null) {
            stdout.print(new String(bytes, StandardCharsets.UTF_8));
        } else {
            Files.write(out, bytes);
        }
        return finish(err, converted.loss());
    }

    private int toZengin(PrintWriter stdout, PrintWriter err, MappingContext context)
            throws IOException {
        ZediFile source = ZediEnvelopeReader.read(file);
        MappingResult<ZenginFile> converted = Iso20022Mapper.create().toZengin(source, context);

        byte[] bytes = ZenginWriters.toByteArray(converted.output(), WriterOptions.defaults());
        if (out == null) {
            stdout.print(new String(bytes, context.targetCharset().charset()));
        } else {
            Files.write(out, bytes);
        }
        return finish(err, converted.loss());
    }

    /**
     * The report is written and the status says whether anything was lost.
     *
     * <p>Loss below the refusal threshold is exit 1 rather than 0. A pipeline
     * that treats 1 as failure will stop on every conversion, which is roughly
     * correct: this conversion is never lossless, and a team that wants it
     * automated should have to say so.
     */
    private int finish(PrintWriter err, MappingLossReport loss) throws IOException {
        writeReport(err, loss);
        if (loss.hasAtLeast(LossSeverity.CRITICAL)) {
            return ExitCode.ERRORS.value();
        }
        return loss.isLossless() ? ExitCode.OK.value() : ExitCode.WARNINGS.value();
    }

    /**
     * stderr for a human, a file for a machine.
     *
     * <p>The reader's own warnings go to stderr as well, so a JSON report there
     * is not parseable however carefully it is written. Rather than pretend
     * otherwise, {@code --loss-out} gives the report a destination of its own.
     */
    private void writeReport(PrintWriter err, MappingLossReport loss) throws IOException {
        if (lossOut == null) {
            err.print(report(loss));
        } else {
            Files.writeString(lossOut, report(loss), StandardCharsets.UTF_8);
        }
    }

    private String report(MappingLossReport loss) {
        return lossFormat == OutputFormat.JSON ? loss.toJson() : loss.toText(locale());
    }

    private Locale locale() {
        if (language == null) {
            return Locale.getDefault();
        }
        return "ja".equalsIgnoreCase(language) ? Locale.JAPANESE : Locale.ENGLISH;
    }
}
