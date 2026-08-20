package io.zengin4j.cli.command;

import module java.base;
import io.zengin4j.cli.ExitCode;
import io.zengin4j.cli.internal.FieldRendering;
import io.zengin4j.cli.internal.Json;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import picocli.CommandLine;

/// `zengin inspect` — shows what is actually in the bytes.
///
/// The tool to reach for when a file is rejected and the rejection notice
/// says something unhelpful. `--annotate` prints, for every field, where it
/// starts, what its bytes are, what they decode to, what the field is called in
/// both languages, and whether the value is one the field may hold (R-CLI5).
///
/// **Account numbers are masked unless `--unsafe-print` is
/// given** (R-CLI4) — and so is their hex, because hex of an account
/// number is an account number to anyone who can read it.
///
/// @since 0.3.0
@CommandLine.Command(
        name = "inspect",
        mixinStandardHelpOptions = true,
        description = "Shows a file's structure, and with --annotate every field byte by byte.")
public final class InspectCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", paramLabel = "FILE", description = "The file to inspect.")
    Path file;

    @CommandLine.Mixin
    ReadingOptions reading = new ReadingOptions();

    @CommandLine.Option(
            names = "--record",
            paramLabel = "N",
            description = "Show only record N, counting from 1.")
    Integer record;

    @CommandLine.Option(
            names = "--annotate",
            description = "Print every field: offset, hex, decoded value, name, and whether the "
                    + "value is permitted.")
    boolean annotate;

    @CommandLine.Option(
            names = "--out-format",
            paramLabel = "FORMAT",
            description = "Output shape: TEXT or JSON. Default: ${DEFAULT-VALUE}.")
    OutputFormat outFormat = OutputFormat.TEXT;

    @CommandLine.Option(
            names = "--unsafe-print",
            description = "Show sensitive fields in full rather than masked (R-CLI4).")
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
        if (outFormat == OutputFormat.SARIF) {
            err.println("inspect has no SARIF output: SARIF carries findings, and inspect reports "
                    + "structure. Use TEXT or JSON, or `zengin validate --out-format=sarif`.");
            return ExitCode.USAGE.value();
        }
        if (unsafePrint) {
            err.println("warning: --unsafe-print shows account numbers in full; "
                    + "this output must not reach a shared log.");
        }

        ZenginFile parsed = ZenginReaders.readFile(file, reading.toReaderOptions(err));
        List<ZenginRecord> records = selected(parsed, err);
        if (records == null) {
            return ExitCode.USAGE.value();
        }

        if (outFormat == OutputFormat.JSON) {
            out.print(json(parsed, records));
        } else {
            text(out, parsed, records);
        }
        return ExitCode.OK.value();
    }

    /// Records in file order, narrowed to `--record` when given.
    private List<ZenginRecord> selected(ZenginFile parsed, PrintWriter err) {
        List<ZenginRecord> all = inOrder(parsed);
        if (record == null) {
            return all;
        }
        List<ZenginRecord> matching = all.stream()
                .filter(candidate -> candidate.recordNumber() == record)
                .toList();
        if (matching.isEmpty()) {
            err.println("no record " + record + "; the file has " + all.size() + ".");
            return null;
        }
        return matching;
    }

    private static List<ZenginRecord> inOrder(ZenginFile file) {
        return file.recordsInOrder();
    }

    // ------------------------------------------------------------------ text

    private void text(PrintWriter out, ZenginFile parsed, List<ZenginRecord> records) {
        FormatDescriptor descriptor = parsed.descriptor();
        out.println(descriptor.id().value() + " — " + descriptor.nameJa()
                + " (" + descriptor.nameEn() + "), 種別コード " + descriptor.typeCode());
        out.println(descriptor.recordLength() + " bytes per record, "
                + parsed.totalRecords() + " records, "
                + describeFraming(parsed));
        if (!descriptor.verified()) {
            out.println("layout unverified — validate against your institution's specification");
        }
        out.println();

        for (ZenginRecord current : records) {
            out.println(header(current));
            if (annotate) {
                annotate(out, parsed, current);
                out.println();
            }
        }
        if (!annotate) {
            out.println();
            out.println("Pass --annotate to see every field.");
        }
    }

    private static String describeFraming(ZenginFile file) {
        var text = new StringBuilder(file.framing().separator().toString().toLowerCase(
                java.util.Locale.ROOT) + "-separated");
        if (file.framing().byteOrderMarkPresent()) {
            text.append(", byte order mark present");
        }
        if (file.framing().trailingEofByte()) {
            text.append(", trailing 0x1A");
        }
        if (!file.framing().isReproducible()) {
            text.append(" — not byte-reproducible");
        }
        return text.toString();
    }

    private static String header(ZenginRecord record) {
        return "record %d  %s  byte %d".formatted(
                record.recordNumber(), record.kind(), record.byteOffset());
    }

    /// The field table (R-CLI5).
    ///
    /// Columns are sized to the widest value actually present rather than to
    /// a guess, because a table whose columns do not line up is harder to read
    /// than no table.
    private void annotate(PrintWriter out, ZenginFile parsed, ZenginRecord record) {
        byte[] bytes = record.rawBytes();
        RecordDescriptor layout = parsed.descriptor().forDiscriminator(bytes[0]).orElse(null);
        if (layout == null) {
            out.println("  (no layout for データ区分 '" + (char) bytes[0] + "')");
            return;
        }

        List<FieldRendering.Row> rows = new ArrayList<>();
        for (FieldDescriptor field : layout.fields()) {
            if (bytes.length < field.endOffset()) {
                continue;
            }
            rows.add(FieldRendering.render(field, bytes, reading.charset(), unsafePrint));
        }
        FieldRendering.table(out, rows);
    }

    // ------------------------------------------------------------------ json

    private String json(ZenginFile parsed, List<ZenginRecord> records) {
        FormatDescriptor descriptor = parsed.descriptor();
        var json = new Json();
        json.object(() -> {
            json.field("file", file.toString());
            json.field("format", descriptor.id().value());
            json.field("typeCode", descriptor.typeCode());
            json.field("recordLength", descriptor.recordLength());
            json.field("verified", descriptor.verified());
            json.field("totalRecords", parsed.totalRecords());
            json.name("framing").object(() -> {
                json.field("separator", parsed.framing().separator().name());
                json.field("byteOrderMark", parsed.framing().byteOrderMarkPresent());
                json.field("trailingEofByte", parsed.framing().trailingEofByte());
                json.field("reproducible", parsed.framing().isReproducible());
            });
            json.field("masked", !unsafePrint);
            json.name("records").array(() -> {
                for (ZenginRecord current : records) {
                    json.object(() -> {
                        json.field("recordNumber", current.recordNumber());
                        json.field("kind", current.kind().name());
                        json.field("byteOffset", current.byteOffset());
                        if (annotate) {
                            jsonFields(json, parsed, current);
                        }
                    });
                }
            });
        });
        return json.toString();
    }

    private void jsonFields(Json json, ZenginFile parsed, ZenginRecord record) {
        byte[] bytes = record.rawBytes();
        RecordDescriptor layout = parsed.descriptor().forDiscriminator(bytes[0]).orElse(null);
        if (layout == null) {
            return;
        }
        json.name("fields").array(() -> {
            for (FieldDescriptor field : layout.fields()) {
                if (bytes.length < field.endOffset()) {
                    continue;
                }
                FieldRendering.Row row =
                        FieldRendering.render(field, bytes, reading.charset(), unsafePrint);
                json.object(() -> {
                    json.field("id", field.id());
                    json.field("nameJa", field.nameJa());
                    json.field("nameEn", field.nameEn());
                    json.field("type", field.type().name());
                    json.field("offset", field.offset());
                    json.field("length", field.length());
                    json.field("hex", row.hex());
                    json.field("value", row.value());
                    json.field("valid", row.valid());
                    if (!row.valid()) {
                        json.field("problem", row.problem());
                    }
                    json.field("sensitive", field.sensitive());
                });
            }
        });
    }
}
