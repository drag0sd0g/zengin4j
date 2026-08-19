package io.zengin4j.cli.command;

import module java.base;
import io.zengin4j.cli.ExitCode;
import io.zengin4j.cli.internal.FieldRendering;
import io.zengin4j.cli.internal.Json;
import io.zengin4j.cli.internal.RecordAlignment;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import picocli.CommandLine;

/// `zengin diff` — what changed between two files, field by field.
///
/// A textual diff of a fixed-length file tells you that record 4 changed and
/// nothing else: the records are one line each and every byte of that line is on
/// the same line as every other. This reports which *field* changed, and
/// from what to what, which is the question anyone running a diff on a payment
/// file is actually asking.
///
/// Records are aligned by longest common subsequence rather than by position,
/// so inserting a payment near the top does not report every later record as
/// changed.
///
/// **Sensitive fields are masked unless `--unsafe-print` is
/// given** (R-CLI4). A diff that says an account number changed from one
/// masked value to a different masked value still tells you the field changed,
/// which is the part you needed.
///
/// @since 0.3.0
@CommandLine.Command(
        name = "diff",
        mixinStandardHelpOptions = true,
        description = "Reports what changed between two files, field by field.")
public final class DiffCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", paramLabel = "BEFORE", description = "The earlier file.")
    Path before;

    @CommandLine.Parameters(index = "1", paramLabel = "AFTER", description = "The later file.")
    Path after;

    @CommandLine.Mixin
    ReadingOptions reading = new ReadingOptions();

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

        for (Path path : List.of(before, after)) {
            if (!Files.isReadable(path)) {
                err.println("cannot read " + path);
                return ExitCode.IO.value();
            }
        }
        if (outFormat == OutputFormat.SARIF) {
            err.println("diff has no SARIF output: SARIF carries findings, and this command "
                    + "reports changes. Use TEXT or JSON.");
            return ExitCode.USAGE.value();
        }
        if (unsafePrint) {
            err.println("warning: --unsafe-print shows account numbers in full; "
                    + "this output must not reach a shared log.");
        }

        ZenginFile left = ZenginReaders.readFile(before, reading.toReaderOptions(err));
        ZenginFile right = ZenginReaders.readFile(after, reading.toReaderOptions(err));

        if (!left.format().equals(right.format())) {
            err.println("these are different formats: " + before + " is " + left.format().value()
                    + ", " + after + " is " + right.format().value()
                    + ". A field-by-field diff would compare unrelated fields.");
            return ExitCode.USAGE.value();
        }

        List<RecordAlignment.Pair> pairs;
        try {
            pairs = RecordAlignment.align(bytesOf(left), bytesOf(right));
        } catch (RecordAlignment.TooLargeToAlignException tooLarge) {
            // Reported rather than thrown, so it cannot reach the top-level
            // handler and be mistaken for a defect. It is a stated limit.
            err.println(tooLarge.getMessage());
            return ExitCode.ERRORS.value();
        }
        boolean changed = pairs.stream()
                .anyMatch(pair -> pair.change() != RecordAlignment.Change.SAME);

        if (outFormat == OutputFormat.JSON) {
            out.print(json(left, pairs, changed));
        } else {
            text(out, left, pairs, changed);
        }
        // Exit 1 when the files differ, matching what every diff tool does and
        // what a script comparing a generated file to a committed one expects.
        return changed ? ExitCode.WARNINGS.value() : ExitCode.OK.value();
    }

    private static List<byte[]> bytesOf(ZenginFile file) {
        return file.recordsInOrder().stream().map(ZenginRecord::rawBytes).toList();
    }

    // ------------------------------------------------------------------ text

    private void text(PrintWriter out, ZenginFile left, List<RecordAlignment.Pair> pairs,
            boolean changed) {
        if (!changed) {
            out.println("no differences (" + pairs.size() + " records)");
            return;
        }
        out.println("--- " + before);
        out.println("+++ " + after);
        out.println();

        for (RecordAlignment.Pair pair : pairs) {
            switch (pair.change()) {
                case SAME -> { }
                case ADDED -> out.println("+ record " + pair.rightNumber() + "  "
                        + summarise(left, pair.right()));
                case REMOVED -> out.println("- record " + pair.leftNumber() + "  "
                        + summarise(left, pair.left()));
                case CHANGED -> {
                    out.println("~ record " + pair.leftNumber()
                            + (pair.leftNumber() == pair.rightNumber()
                                    ? "" : " -> " + pair.rightNumber()));
                    for (FieldChange field : fieldChanges(left, pair.left(), pair.right())) {
                        out.println("    " + field.id() + " (" + field.nameJa() + ") "
                                + "byte " + field.offset() + ": "
                                + field.was() + " -> " + field.now());
                    }
                }
            }
        }

        out.println();
        long added = count(pairs, RecordAlignment.Change.ADDED);
        long removed = count(pairs, RecordAlignment.Change.REMOVED);
        long edited = count(pairs, RecordAlignment.Change.CHANGED);
        out.println(edited + " changed, " + added + " added, " + removed + " removed.");
    }

    private static long count(List<RecordAlignment.Pair> pairs, RecordAlignment.Change change) {
        return pairs.stream().filter(pair -> pair.change() == change).count();
    }

    /// A one-line description of a whole record, for an addition or removal.
    private String summarise(ZenginFile file, byte[] record) {
        RecordDescriptor layout = file.descriptor().forDiscriminator(record[0]).orElse(null);
        if (layout == null) {
            return "(unrecognised データ区分 '" + (char) record[0] + "')";
        }
        StringBuilder text = new StringBuilder(layout.kind().toString());
        for (FieldDescriptor field : layout.fields()) {
            if (field.filler() || record.length < field.endOffset()) {
                continue;
            }
            FieldRendering.Row row =
                    FieldRendering.render(field, record, reading.charset(), unsafePrint);
            String value = row.display().strip();
            if (!value.isEmpty() && !value.matches("0+")) {
                text.append("  ").append(field.id()).append('=').append(value);
            }
        }
        return text.toString();
    }

    /// Which fields differ between two records of the same kind.
    private List<FieldChange> fieldChanges(ZenginFile file, byte[] left, byte[] right) {
        RecordDescriptor layout = file.descriptor().forDiscriminator(left[0]).orElse(null);
        if (layout == null || left[0] != right[0]) {
            // Different record kinds at the same position: there is no
            // field-level comparison to make, so say so rather than align
            // 受取人名 against 合計金額.
            return List.of(new FieldChange("(record kind)", "データ区分", 0,
                    String.valueOf((char) left[0]), String.valueOf((char) right[0])));
        }
        List<FieldChange> changes = new ArrayList<>();
        for (FieldDescriptor field : layout.fields()) {
            if (left.length < field.endOffset() || right.length < field.endOffset()) {
                continue;
            }
            if (Arrays.equals(left, field.offset(), field.endOffset(),
                    right, field.offset(), field.endOffset())) {
                continue;
            }
            FieldRendering.Row was =
                    FieldRendering.render(field, left, reading.charset(), unsafePrint);
            FieldRendering.Row now =
                    FieldRendering.render(field, right, reading.charset(), unsafePrint);
            changes.add(new FieldChange(field.id(), field.nameJa(), field.offset(),
                    "'" + was.display().strip() + "'", "'" + now.display().strip() + "'"));
        }
        return changes;
    }

    // ------------------------------------------------------------------ json

    private String json(ZenginFile left, List<RecordAlignment.Pair> pairs, boolean changed) {
        Json json = new Json();
        json.object(() -> {
            json.field("before", before.toString());
            json.field("after", after.toString());
            json.field("format", left.format().value());
            json.field("changed", changed);
            json.field("masked", !unsafePrint);
            json.name("summary").object(() -> {
                json.field("same", count(pairs, RecordAlignment.Change.SAME));
                json.field("changed", count(pairs, RecordAlignment.Change.CHANGED));
                json.field("added", count(pairs, RecordAlignment.Change.ADDED));
                json.field("removed", count(pairs, RecordAlignment.Change.REMOVED));
            });
            json.name("records").array(() -> {
                for (RecordAlignment.Pair pair : pairs) {
                    if (pair.change() == RecordAlignment.Change.SAME) {
                        continue;
                    }
                    json.object(() -> {
                        json.field("change", pair.change().name());
                        if (pair.leftNumber() > 0) {
                            json.field("beforeRecord", pair.leftNumber());
                        }
                        if (pair.rightNumber() > 0) {
                            json.field("afterRecord", pair.rightNumber());
                        }
                        if (pair.change() == RecordAlignment.Change.CHANGED) {
                            json.name("fields").array(() -> {
                                for (FieldChange field
                                        : fieldChanges(left, pair.left(), pair.right())) {
                                    json.object(() -> {
                                        json.field("id", field.id());
                                        json.field("nameJa", field.nameJa());
                                        json.field("offset", field.offset());
                                        json.field("before", unquote(field.was()));
                                        json.field("after", unquote(field.now()));
                                    });
                                }
                            });
                        }
                    });
                }
            });
        });
        return json.toString();
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("'") && value.endsWith("'")
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private record FieldChange(String id, String nameJa, int offset, String was, String now) {
    }
}
