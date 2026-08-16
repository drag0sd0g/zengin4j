package io.zengin4j.codegen;

import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.CodeValue;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits reference documentation from the descriptors (R-F4).
 *
 * <p>Generated from the same source as the code, so the two cannot drift. The
 * verification state is rendered as a banner at the top of every page (R-F5):
 * a reader who skims one page must not come away believing a provisional
 * layout is a confirmed one.
 */
final class FormatDocGenerator {

    private static final String NL = "\n";

    private final Path docsRoot;

    FormatDocGenerator(Path docsRoot) {
        this.docsRoot = docsRoot;
    }

    GeneratedFile generate(FormatDescriptor format, String source) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(format.nameJa()).append(" — ").append(format.nameEn()).append(NL).append(NL)
                .append("<!-- GENERATED from ").append(source).append(" by ")
                .append(RecordSourceGenerator.GENERATOR).append(". Do not edit by hand;")
                .append(" edit the descriptor and run ./gradlew generateFormatSources. -->").append(NL)
                .append(NL);

        banner(out, format);

        out.append("## At a glance").append(NL).append(NL)
                .append("| | |").append(NL)
                .append("|---|---|").append(NL)
                .append("| Format id | `").append(format.id()).append("` |").append(NL)
                .append("| 種別コード | `").append(format.typeCode()).append("` |").append(NL)
                .append("| Record length | ").append(format.recordLength()).append(" bytes |").append(NL)
                .append("| Verified | ").append(format.verified() ? "yes" : "**no**").append(" |").append(NL)
                .append("| Sources cited | ").append(format.sources().isEmpty()
                        ? "none" : String.valueOf(format.sources().size())).append(" |").append(NL)
                .append(NL);

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

    /**
     * Three states, not two. "Nobody has checked" and "everybody agrees except
     * on one field" are both `verified: false`, and telling a reader they are
     * the same thing wastes the work that produced the difference.
     */
    private void banner(StringBuilder out, FormatDescriptor format) {
        if (format.verified()) {
            out.append("> **Verified.** This layout has been confirmed against the independent")
                    .append(" published sources cited below.").append(NL).append(NL);
            return;
        }
        if (format.sources().isEmpty()) {
            out.append("> ## ⚠ Unverified layout — no sources cited").append(NL)
                    .append("> ").append(NL)
                    .append("> Nothing has corroborated these byte offsets. They have **not** been")
                    .append(NL)
                    .append("> confirmed against two independent published sources (R-0.1), and")
                    .append(NL)
                    .append("> reading a file with this format requires").append(NL)
                    .append("> `ReaderOptions.builder().allowUnverifiedFormats(true)`.").append(NL)
                    .append("> ").append(NL)
                    .append("> **Writing has no such gate** — the builder and writer use whatever")
                    .append(NL)
                    .append("> descriptor they are given. A wrong byte offset in a payment file")
                    .append(NL)
                    .append("> produces silently corrupted financial instructions, and on the write")
                    .append(NL)
                    .append("> side nothing downstream will catch it. Check the layout against your")
                    .append(NL)
                    .append("> own institution's specification before relying on it.")
                    .append(NL).append(NL);
            return;
        }
        out.append("> ## ⚠ Corroborated, but not yet verified").append(NL)
                .append("> ").append(NL)
                .append("> Every field offset and length below is corroborated by the ")
                .append(format.sources().size()).append(" independent").append(NL)
                .append("> published sources cited under Sources, and they agree. The format is")
                .append(NL)
                .append("> nevertheless held at `verified: false`, because at least one **field")
                .append(NL)
                .append("> attribute** is read differently by different sources, and R-0.2 keeps a")
                .append(NL)
                .append("> format unverified until such a disagreement is settled. The readings and")
                .append(NL)
                .append("> the resolution are in `docs/DISCREPANCIES.md`; the affected fields carry a")
                .append(NL)
                .append("> note in the table below.").append(NL)
                .append("> ").append(NL)
                .append("> Reading a file with this format still requires").append(NL)
                .append("> `ReaderOptions.builder().allowUnverifiedFormats(true)`. **Writing has no")
                .append(NL)
                .append("> such gate**, so producing a file from this layout is the less guarded")
                .append(NL)
                .append("> direction, not the safer one. Check the layout against your own")
                .append(NL)
                .append("> institution's specification either way.")
                .append(NL).append(NL);
    }

    private void recordSection(StringBuilder out, RecordKind kind, RecordDescriptor record) {
        out.append("### ").append(Names.capitalise(kind.descriptorKey())).append(" record")
                .append(" — データ区分 `").append((char) record.discriminator()).append('`')
                .append(NL).append(NL)
                .append("| # | Field | 項目名 | Type | Length | Offset | Notes |").append(NL)
                .append("|---|---|---|---|---|---|---|").append(NL);
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
        field.codeList().ifPresent(list -> notes.add("code list `" + list.id() + "`"));
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
        out.append("## Code lists").append(NL).append(NL)
                .append("Every list is open: a value outside it is carried through as raw field")
                .append(NL)
                .append("content rather than rejected, because the published values are not yet")
                .append(NL)
                .append("confirmed and asserting that no other value exists would be a guess.")
                .append(NL).append(NL);
        for (CodeList list : referenced.values()) {
            out.append("### ").append(list.nameJa()).append(" — ").append(list.nameEn())
                    .append(" (`").append(list.id()).append("`)").append(NL).append(NL)
                    .append(list.verified() ? "**Verified**" : "**Not verified**")
                    .append(" · ").append(list.sources().size())
                    .append(list.sources().size() == 1 ? " source cited" : " sources cited")
                    .append(NL).append(NL);
            list.note().ifPresent(note -> out.append("> ").append(note).append(NL).append(NL));
            out.append("| Code | 名称 | Meaning | Verified | Notes |").append(NL)
                    .append("|---|---|---|---|---|").append(NL);
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
