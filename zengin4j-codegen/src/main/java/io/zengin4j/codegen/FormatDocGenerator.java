package io.zengin4j.codegen;

import module java.base;
import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.CodeValue;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldSpec;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;

/// Emits reference documentation from the descriptors (R-F4).
///
/// Generated from the same source as the code, so the two cannot drift. The
/// verification state is rendered as a banner at the top of every page (R-F5):
/// a reader who skims one page must not come away believing a provisional
/// layout is a confirmed one.
final class FormatDocGenerator {

    private static final String NL = "\n";

    private final Path docsRoot;

    FormatDocGenerator(Path docsRoot) {
        this.docsRoot = docsRoot;
    }

    GeneratedFile generate(FormatDescriptor format, String source) {
        var out = new StringBuilder();
        out.append("""
                # %s — %s

                <!-- GENERATED from %s by %s. Do not edit by hand; edit the descriptor and run ./gradlew generateFormatSources. -->

                """.formatted(format.nameJa(), format.nameEn(), source,
                        RecordSourceGenerator.GENERATOR));

        banner(out, format);

        out.append("""
                ## At a glance

                | | |
                |---|---|
                | Format id | `%s` |
                | 種別コード | `%s` |
                | Record length | %d bytes |
                | Verified | %s |
                | Sources cited | %s |

                """.formatted(format.id(), format.typeCode(), format.recordLength(),
                        format.verified() ? "yes" : "**no**",
                        format.sources().isEmpty()
                                ? "none" : String.valueOf(format.sources().size())));

        if (!format.sources().isEmpty()) {
            out.append("### Sources").append(NL).append(NL);
            for (String citation : format.sources()) {
                out.append("- ").append(citation).append(NL);
            }
            out.append(NL);
        }

        format.note().ifPresent(note ->
                out.append("> ").append(note).append(NL).append(NL));

        out.append("## Records").append(NL).append(NL)
                .append("Every offset below is computed from the cumulative length of the preceding")
                .append(NL)
                .append("fields, never transcribed by hand (R-F2). The lengths of each record's fields")
                .append(NL)
                .append("sum exactly to the record length, which the build checks (R-F1).").append(NL)
                .append(NL);

        for (RecordKind kind : List.of(RecordKind.HEADER, RecordKind.DATA, RecordKind.TRAILER, RecordKind.END)) {
            format.find(kind).ifPresent(record -> recordSection(out, kind, record));
        }

        codeLists(out, format);

        return new GeneratedFile(docsRoot.resolve(format.id().value() + ".md"), out.toString());
    }

    /// Three states, not two. "Nobody has checked" and "everybody agrees except
    /// on one field" are both `verified: false`, and telling a reader they are
    /// the same thing wastes the work that produced the difference.
    private void banner(StringBuilder out, FormatDescriptor format) {
        // The blockquote spacer lines are "> " with a trailing space, which a
        // text block would strip; \s writes the space and stops the stripping.
        if (format.verified()) {
            out.append("""
                    > **Verified.** This layout has been confirmed against the independent published sources cited below.

                    """);
            return;
        }
        if (format.sources().isEmpty()) {
            out.append("""
                    > ## ⚠ Unverified layout — no sources cited
                    >\s
                    > Nothing has corroborated these byte offsets. They have **not** been
                    > confirmed against two independent published sources (R-0.1), and
                    > reading a file with this format requires
                    > `ReaderOptions.builder().allowUnverifiedFormats(true)`.
                    >\s
                    > Building one requires the same acknowledgement, through
                    > `ZenginFileBuilder.forFormat(...).allowUnverifiedFormats(true)`.
                    >\s
                    > A wrong byte offset in a payment file produces silently corrupted
                    > financial instructions. Check the layout against your own
                    > institution's specification before relying on it.

                    """);
            return;
        }
        out.append("""
                > ## ⚠ Corroborated, but not yet verified
                >\s
                > Every field offset and length below is corroborated by the %d independent
                > published sources cited under Sources, and they agree. The format is
                > nevertheless held at `verified: false`, because at least one **field
                > attribute** is read differently by different sources, and R-0.2 keeps a
                > format unverified until such a disagreement is settled. The readings and
                > the resolution are in `docs/DISCREPANCIES.md`; the affected fields carry a
                > note in the table below.
                >\s
                > Reading a file with this format still requires
                > `ReaderOptions.builder().allowUnverifiedFormats(true)`, and building one
                > requires
                > `ZenginFileBuilder.forFormat(...).allowUnverifiedFormats(true)`. You
                > should still check the layout against your own institution's
                > specification.

                """.formatted(format.sources().size()));
    }

    private void recordSection(StringBuilder out, RecordKind kind, RecordDescriptor record) {
        out.append("""
                ### %s record — データ区分 `%s`

                | # | Field | 項目名 | Type | Length | Offset | Notes |
                |---|---|---|---|---|---|---|
                """.formatted(Names.capitalise(kind.descriptorKey()),
                        (char) record.discriminator()));
        for (FieldDescriptor field : record.fields()) {
            out.append("| ").append(field.sequence())
                    .append(" | `").append(field.id()).append('`')
                    .append(" | ").append(field.nameJa())
                    .append(" | ").append(field.type())
                    .append(" | ").append(field.length())
                    .append(" | ").append(field.offset())
                    .append(" | ").append(notes(field))
                    .append(" |").append(NL);
        }
        out.append(NL);
    }

    private String notes(FieldDescriptor field) {
        List<String> notes = new ArrayList<>();
        field.constant().ifPresent(value -> notes.add("fixed `" + value + "`"));
        field.format().ifPresent(value -> notes.add("format `" + value.descriptorValue() + "`"));
        field.codeList().ifPresent(list -> notes.add("code list `" + list.id() + "`"
                + (field.codes().isEmpty() ? "" : ", narrowed to " + String.join("/", field.codes()))));
        if (field.charClass() != FieldSpec.defaultCharacterClass(field.type())) {
            notes.add("characters: " + field.charClass().nameEn()
                    + (field.charClass().symbols().isEmpty()
                            ? ", no symbols"
                            : ", symbols `" + field.charClass().symbols() + "`"));
        }
        if (field.required()) {
            notes.add("required");
        }
        if (field.filler()) {
            notes.add("filler");
        }
        if (field.sensitive()) {
            notes.add("masked in diagnostics");
        }
        field.note().ifPresent(notes::add);
        return notes.isEmpty() ? "" : String.join("; ", notes);
    }

    private void codeLists(StringBuilder out, FormatDescriptor format) {
        Map<String, CodeList> referenced = new LinkedHashMap<>();
        for (RecordKind kind : List.of(RecordKind.HEADER, RecordKind.DATA, RecordKind.TRAILER, RecordKind.END)) {
            format.find(kind).ifPresent(record -> {
                for (FieldDescriptor field : record.fields()) {
                    field.codeList().ifPresent(list -> referenced.putIfAbsent(list.id(), list));
                }
            });
        }
        if (referenced.isEmpty()) {
            return;
        }
        out.append("""
                ## Code lists

                Every list is open: a value outside it is carried through as raw field
                content rather than rejected, because the published values are not yet
                confirmed and asserting that no other value exists would be a guess.

                """);
        for (CodeList list : referenced.values()) {
            out.append("""
                    ### %s — %s (`%s`)

                    %s · %d%s

                    """.formatted(list.nameJa(), list.nameEn(), list.id(),
                            list.verified() ? "**Verified**" : "**Not verified**",
                            list.sources().size(),
                            list.sources().size() == 1 ? " source cited" : " sources cited"));
            list.note().ifPresent(note -> out.append("> ").append(note).append(NL).append(NL));
            out.append("""
                    | Code | 名称 | Meaning | Verified | Notes |
                    |---|---|---|---|---|
                    """);
            for (CodeValue value : list.values()) {
                out.append("| `").append(value.code()).append('`')
                        .append(" | ").append(value.nameJa())
                        .append(" | ").append(value.nameEn())
                        .append(" | ").append(value.verified() ? "yes" : "no")
                        .append(" | ").append(value.note().orElse(""))
                        .append(" |").append(NL);
            }
            out.append(NL);
        }
    }
}
