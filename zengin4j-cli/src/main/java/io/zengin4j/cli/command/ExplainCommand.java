package io.zengin4j.cli.command;

import io.zengin4j.cli.ExitCode;
import io.zengin4j.cli.internal.FieldRendering;
import io.zengin4j.cli.internal.Json;
import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.CodeValue;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * {@code zengin explain} — describes a format, or one field of it.
 *
 * <p>Answers "what is at byte 50, and what is it allowed to contain?" without
 * needing a file to hand. The same descriptors the reader uses are the source,
 * so what this prints is what the library will actually do — not a document
 * that agreed with the code when it was written.
 *
 * <p>It states the verification status plainly, because a layout nobody has
 * confirmed against two published sources is exactly the thing a reader of this
 * output needs to know.
 *
 * @since 0.3.0
 */
@CommandLine.Command(
        name = "explain",
        mixinStandardHelpOptions = true,
        description = "Describes a format's byte layout, or one field of it.")
public final class ExplainCommand implements Callable<Integer> {
    @CommandLine.Option(
            names = "--format",
            paramLabel = "ID",
            description = "The format to describe. Omit to list every bundled format.")
    String format;

    @CommandLine.Option(
            names = "--field",
            paramLabel = "ID",
            description = "Describe one field rather than the whole layout.")
    String field;

    @CommandLine.Option(
            names = "--record",
            paramLabel = "KIND",
            description = "Restrict to one record: ${COMPLETION-CANDIDATES}.")
    RecordKind recordKind;

    @CommandLine.Option(
            names = "--out-format",
            paramLabel = "FORMAT",
            description = "Output shape: TEXT or JSON. Default: ${DEFAULT-VALUE}.")
    OutputFormat outFormat = OutputFormat.TEXT;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        FormatRegistry registry = FormatRegistry.defaults();

        if (outFormat == OutputFormat.SARIF) {
            err.println("explain has no SARIF output: SARIF carries findings, and this command "
                    + "describes a layout. Use TEXT or JSON.");
            return ExitCode.USAGE.value();
        }
        if (format == null) {
            if (field != null) {
                err.println("--field needs --format: the same field id means different things in "
                        + "different formats. Try `zengin explain --format=ID --field=" + field
                        + "`, or run `zengin explain` for the list of formats.");
                return ExitCode.USAGE.value();
            }
            if (recordKind != null) {
                err.println("--record needs --format. Run `zengin explain` for the list of "
                        + "formats.");
                return ExitCode.USAGE.value();
            }
            return listFormats(out, registry);
        }

        Optional<FormatDescriptor> found = registry.byId(FormatId.of(format));
        if (found.isEmpty()) {
            err.println("no format '" + format + "'. Known: " + registry.all().stream()
                    .map(descriptor -> descriptor.id().value()).sorted().toList());
            return ExitCode.USAGE.value();
        }
        FormatDescriptor descriptor = found.get();

        if (field != null) {
            return explainField(out, err, descriptor);
        }
        if (outFormat == OutputFormat.JSON) {
            out.print(json(descriptor));
        } else {
            text(out, descriptor);
        }
        return ExitCode.OK.value();
    }

    private int listFormats(PrintWriter out, FormatRegistry registry) {
        List<FormatDescriptor> all = registry.all().stream()
                .sorted(java.util.Comparator.comparing(descriptor -> descriptor.id().value()))
                .toList();
        if (outFormat == OutputFormat.JSON) {
            Json json = new Json();
            json.object(() -> json.name("formats").array(() -> {
                for (FormatDescriptor descriptor : all) {
                    json.object(() -> {
                        json.field("id", descriptor.id().value());
                        json.field("typeCode", descriptor.typeCode());
                        json.field("nameJa", descriptor.nameJa());
                        json.field("nameEn", descriptor.nameEn());
                        json.field("recordLength", descriptor.recordLength());
                        json.field("verified", descriptor.verified());
                    });
                }
            }));
            out.print(json.toString());
        } else {
            int nameWidth = all.stream()
                    .mapToInt(descriptor -> FieldRendering.displayWidth(descriptor.nameJa()))
                    .max().orElse(8);
            out.println("Bundled formats:");
            for (FormatDescriptor descriptor : all) {
                out.printf("  %-18s 種別コード %-2s  %s  %3d bytes  %s%n",
                        descriptor.id().value(), descriptor.typeCode(),
                        padDisplay(descriptor.nameJa(), nameWidth),
                        descriptor.recordLength(),
                        descriptor.verified() ? "verified" : "unverified");
            }
            out.println();
            out.println("Pass --format=ID for the byte layout.");
        }
        return ExitCode.OK.value();
    }

    private int explainField(PrintWriter out, PrintWriter err, FormatDescriptor descriptor) {
        List<Located> matches = new ArrayList<>();
        for (RecordKind kind : RecordKind.values()) {
            if (recordKind != null && kind != recordKind) {
                continue;
            }
            descriptor.find(kind)
                    .flatMap(record -> record.find(field))
                    .ifPresent(found -> matches.add(new Located(kind, found)));
        }
        if (matches.isEmpty()) {
            err.println("no field '" + field + "' in " + descriptor.id().value()
                    + (recordKind == null ? "" : " " + recordKind) + ".");
            return ExitCode.USAGE.value();
        }

        if (outFormat == OutputFormat.JSON) {
            Json json = new Json();
            json.object(() -> {
                json.field("format", descriptor.id().value());
                json.name("occurrences").array(() -> {
                    for (Located located : matches) {
                        json.object(() -> {
                            json.field("record", located.kind().name());
                            describeField(json, located.field());
                        });
                    }
                });
            });
            out.print(json.toString());
            return ExitCode.OK.value();
        }

        for (Located located : matches) {
            FieldDescriptor found = located.field();
            out.println(descriptor.id().value() + " " + located.kind() + " — " + found.id());
            out.println("  " + found.nameJa() + " (" + found.nameEn() + ")");
            out.println("  bytes " + found.offset() + "–" + (found.endOffset() - 1)
                    + " (" + found.length() + " " + found.type() + ")");
            out.println("  " + (found.type() == io.zengin4j.core.format.FieldType.N
                    ? "digits, right-aligned, zero-padded"
                    : "text, left-aligned, space-padded"));
            if (found.required()) {
                out.println("  required");
            }
            if (found.filler()) {
                out.println("  filler — the library preserves whatever is here");
            }
            if (found.sensitive()) {
                out.println("  masked in diagnostics unless --unsafe-print");
            }
            found.constant().ifPresent(constant -> out.println("  always '" + constant + "'"));
            found.format().ifPresent(fieldFormat -> out.println("  format " + fieldFormat));
            found.codeList().ifPresent(list -> {
                out.println("  code list " + list.id()
                        + (list.open() ? " (open — other values are permitted)" : ""));
                for (CodeValue code : permittedCodes(found, list)) {
                    out.println("    " + code.code() + "  " + code.nameJa()
                            + " (" + code.nameEn() + ")"
                            + (code.verified() ? "" : "  [unverified]"));
                }
            });
            out.println("  characters: " + found.charClass());
            found.note().ifPresent(note -> out.println("  note: " + note));
            out.println();
        }
        return ExitCode.OK.value();
    }

    private void text(PrintWriter out, FormatDescriptor descriptor) {
        out.println(descriptor.id().value() + " — " + descriptor.nameJa()
                + " (" + descriptor.nameEn() + ")");
        out.println("種別コード " + descriptor.typeCode() + ", "
                + descriptor.recordLength() + " bytes per record");
        out.println(descriptor.verified()
                ? "verified against " + descriptor.sources().size() + " published sources"
                : "UNVERIFIED — the byte layout is not confirmed by two independent published "
                        + "sources. Validate against your institution's specification.");
        descriptor.note().ifPresent(note -> out.println("note: " + note));
        out.println();

        for (RecordKind kind : RecordKind.values()) {
            if (recordKind != null && kind != recordKind) {
                continue;
            }
            Optional<RecordDescriptor> record = descriptor.find(kind);
            if (record.isEmpty()) {
                continue;
            }
            RecordDescriptor layout = record.get();
            out.println(kind + " record — データ区分 '" + (char) layout.discriminator() + "'");
            List<FieldRendering.Row> rows = new ArrayList<>();
            for (FieldDescriptor descriptorField : layout.fields()) {
                rows.add(new FieldRendering.Row(descriptorField, "", "", true, ""));
            }
            layoutTable(out, rows);
            out.println();
        }
        out.println("Sources:");
        for (String source : descriptor.sources()) {
            out.println("  - " + source);
        }
    }

    /** The layout table: no hex or value column, because there is no file here. */
    private static void layoutTable(PrintWriter out, List<FieldRendering.Row> rows) {
        int idWidth = 10;
        int jaWidth = 8;
        for (FieldRendering.Row row : rows) {
            idWidth = Math.max(idWidth, row.field().id().length());
            jaWidth = Math.max(jaWidth, FieldRendering.displayWidth(row.field().nameJa()));
        }
        for (FieldRendering.Row row : rows) {
            FieldDescriptor descriptorField = row.field();
            out.printf("  %3d  %5d %3d  %s  %-" + idWidth + "s  %s%s%n",
                    descriptorField.sequence(), descriptorField.offset(), descriptorField.length(),
                    descriptorField.type(), descriptorField.id(),
                    padDisplay(descriptorField.nameJa(), jaWidth),
                    notes(descriptorField));
        }
    }

    private static String padDisplay(String value, int width) {
        return value + " ".repeat(Math.max(0, width - FieldRendering.displayWidth(value)));
    }

    private static String notes(FieldDescriptor field) {
        List<String> notes = new ArrayList<>();
        field.constant().ifPresent(constant -> notes.add("fixed '" + constant + "'"));
        field.codeList().ifPresent(list -> notes.add("code list " + list.id()));
        if (field.required()) {
            notes.add("required");
        }
        if (field.filler()) {
            notes.add("filler");
        }
        if (field.sensitive()) {
            notes.add("masked");
        }
        return notes.isEmpty() ? "" : "  " + String.join(", ", notes);
    }

    private String json(FormatDescriptor descriptor) {
        Json json = new Json();
        json.object(() -> {
            json.field("id", descriptor.id().value());
            json.field("nameJa", descriptor.nameJa());
            json.field("nameEn", descriptor.nameEn());
            json.field("typeCode", descriptor.typeCode());
            json.field("recordLength", descriptor.recordLength());
            json.field("verified", descriptor.verified());
            json.name("sources").array(() -> descriptor.sources().forEach(json::value));
            json.name("records").array(() -> {
                for (RecordKind kind : RecordKind.values()) {
                    if (recordKind != null && kind != recordKind) {
                        continue;
                    }
                    descriptor.find(kind).ifPresent(layout -> json.object(() -> {
                        json.field("kind", kind.name());
                        json.field("discriminator", String.valueOf((char) layout.discriminator()));
                        json.name("fields").array(() -> {
                            for (FieldDescriptor descriptorField : layout.fields()) {
                                json.object(() -> describeField(json, descriptorField));
                            }
                        });
                    }));
                }
            });
        });
        return json.toString();
    }

    private static void describeField(Json json, FieldDescriptor field) {
        json.field("sequence", field.sequence());
        json.field("id", field.id());
        json.field("nameJa", field.nameJa());
        json.field("nameEn", field.nameEn());
        json.field("type", field.type().name());
        json.field("offset", field.offset());
        json.field("length", field.length());
        json.field("required", field.required());
        json.field("filler", field.filler());
        json.field("sensitive", field.sensitive());
        json.field("characterClass", field.charClass().name());
        field.constant().ifPresent(constant -> json.field("constant", constant));
        field.format().ifPresent(fieldFormat -> json.field("format", fieldFormat.name()));
        field.codeList().ifPresent(list -> {
            json.field("codeList", list.id());
            json.field("openCodeList", list.open());
            json.name("permitted").array(() -> {
                for (CodeValue code : permittedCodes(field, list)) {
                    json.object(() -> {
                        json.field("code", code.code());
                        json.field("nameJa", code.nameJa());
                        json.field("nameEn", code.nameEn());
                        json.field("verified", code.verified());
                    });
                }
            });
        });
        field.note().ifPresent(note -> json.field("note", note));
    }

    /**
     * The codes this field may hold.
     *
     * <p>A descriptor may narrow a shared list — 給与振込 admits two of the
     * account types 総合振込 admits — and the narrowed set is what the reader
     * of this output needs, not the superset.
     */
    private static List<CodeValue> permittedCodes(FieldDescriptor field, CodeList list) {
        if (field.codes().isEmpty()) {
            return list.values();
        }
        return list.values().stream()
                .filter(value -> field.codes().contains(value.code()))
                .toList();
    }

    private record Located(RecordKind kind, FieldDescriptor field) {
    }
}
