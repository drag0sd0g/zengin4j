package io.zengin4j.cli.command;

import module java.base;
import io.zengin4j.cli.ExitCode;
import io.zengin4j.cli.internal.Json;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.testkit.ZenginGenerator;
import picocli.CommandLine;

/// `zengin generate` — writes a synthetic file.
///
/// **Every value it produces is invented** (R-L1). Bank
/// `9999`, branch `999` and accounts beginning `9` are outside
/// the ranges Japanese institutions use, and the names are obviously fictional.
/// Nothing this command writes resembles a real payment instruction, which is
/// the point: test files end up in repositories.
///
/// **The same seed produces the same bytes** on every platform
/// and every JDK (R-CLI3), so a generated file can be committed as a fixture and
/// regenerated years later to the byte.
///
/// @since 0.3.0
@CommandLine.Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        description = "Writes a synthetic file. Every value is invented; the same seed "
                + "reproduces the same bytes.")
public final class GenerateCommand implements Callable<Integer> {

    @CommandLine.Option(
            names = "--format",
            paramLabel = "ID",
            description = "Format to generate. Default: ${DEFAULT-VALUE}.")
    String format = "sougou-furikomi";

    @CommandLine.Option(
            names = "--count",
            paramLabel = "N",
            description = "How many payment records. Default: ${DEFAULT-VALUE}.")
    int count = 10;

    @CommandLine.Option(
            names = "--seed",
            paramLabel = "N",
            description = "Random seed. Default: ${DEFAULT-VALUE}.")
    long seed = 42L;

    @CommandLine.Option(
            names = "--separator",
            paramLabel = "STYLE",
            description = "Record separator: ${COMPLETION-CANDIDATES}. Default: ${DEFAULT-VALUE}.")
    SeparatorStyle separator = SeparatorStyle.CRLF;

    @CommandLine.Option(
            names = "--eof-byte",
            description = "Append a trailing 0x1A, as some mainframe exports do.")
    boolean eofByte;

    @CommandLine.Option(
            names = "--out",
            paramLabel = "FILE",
            description = "Where to write. Writes to stdout if omitted.")
    Path out;

    @CommandLine.Option(
            names = "--out-format",
            paramLabel = "FORMAT",
            description = "TEXT writes the file itself; JSON writes a description of what was "
                    + "generated. Default: ${DEFAULT-VALUE}.")
    OutputFormat outFormat = OutputFormat.TEXT;

    @CommandLine.Option(
            names = "--list-formats",
            description = "List the formats this command can generate, then exit.")
    boolean listFormats;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        PrintWriter stdout = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (listFormats) {
            FormatFixtures.supported().forEach(id -> stdout.println(id.value()));
            return ExitCode.OK.value();
        }
        if (count < 0) {
            err.println("--count must not be negative, found " + count);
            return ExitCode.USAGE.value();
        }
        if (separator == SeparatorStyle.MIXED) {
            err.println("--separator=MIXED is an observation about a file that already exists, "
                    + "not a convention a writer can follow. Choose NONE, CR, LF or CRLF.");
            return ExitCode.USAGE.value();
        }
        if (outFormat == OutputFormat.SARIF) {
            err.println("generate has no SARIF output: SARIF carries findings, and this command "
                    + "produces a file. Use TEXT or JSON.");
            return ExitCode.USAGE.value();
        }

        // Surfaced as a usage error rather than a stack trace: asking for a
        // format the testkit does not cover is a reasonable thing to try.
        ZenginGenerator generator;
        try {
            generator = ZenginGenerator.builder()
                    .format(FormatId.of(format))
                    .payments(count)
                    .seed(seed)
                    .separator(separator)
                    .trailingEofByte(eofByte)
                    .build();
        } catch (IllegalArgumentException unknown) {
            err.println(unknown.getMessage());
            return ExitCode.USAGE.value();
        }

        byte[] bytes = generator.generate();

        if (out == null) {
            if (outFormat == OutputFormat.JSON) {
                stdout.print(describe(bytes, null));
            } else {
                // Written to the raw stream, not the writer: this is a
                // Shift_JIS payment file, and putting it through a UTF-8
                // PrintWriter would re-encode every katakana byte.
                stdout.flush();
                System.out.write(bytes);
                System.out.flush();
            }
            return ExitCode.OK.value();
        }

        Files.write(out, bytes);
        if (outFormat == OutputFormat.JSON) {
            stdout.print(describe(bytes, out));
        } else {
            stdout.println("wrote " + bytes.length + " bytes to " + out
                    + " (" + count + " payments, format " + format + ", seed " + seed + ")");
        }
        return ExitCode.OK.value();
    }

    private String describe(byte[] bytes, Path written) {
        Json json = new Json();
        json.object(() -> {
            json.field("format", format);
            json.field("payments", count);
            json.field("seed", seed);
            json.field("separator", separator.name());
            json.field("trailingEofByte", eofByte);
            json.field("bytes", bytes.length);
            json.field("synthetic", true);
            if (written != null) {
                json.field("path", written.toString());
            }
        });
        return json.toString();
    }
}
