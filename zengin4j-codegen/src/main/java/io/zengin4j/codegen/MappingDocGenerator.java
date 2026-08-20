package io.zengin4j.codegen;

import module java.base;

/// Generates `docs/mapping.md` from the declared mappings.
///
/// R-I19 requires unverified rows to be visibly marked in the generated
/// documentation. Generating the page from the same file the mapper is compiled
/// from is what makes that mark trustworthy: a row cannot be quietly implemented
/// one way and documented another.
final class MappingDocGenerator {

    private static final String NL = "\n";

    private final Path docsOut;

    MappingDocGenerator(Path docsOut) {
        this.docsOut = docsOut;
    }

    /// Generates the page.
    ///
    /// @param mappings the declarations
    /// @param sources  each declaration's file name
    /// @return the generated file
    GeneratedFile generate(List<MappingReader.Mapping> mappings, List<String> sources) {
        var out = new StringBuilder();
        out.append("""
                # Mapping reference

                > GENERATED from %s — do not edit. Change a declaration and run
                > `./gradlew generateFormatSources`; the build fails if this page and the declarations disagree.

                Every correspondence this library implements between a Zengin field and an ISO 20022
                element, in both directions, with what each one costs.

                """.formatted(String.join(", ", sources)));

        appendVerificationNote(out, mappings);

        for (MappingReader.Mapping mapping : mappings) {
            appendMapping(out, mapping);
        }

        appendReadingNote(out);

        return new GeneratedFile(docsOut.resolve("mapping.md"), out.toString());
    }

    private void appendVerificationNote(StringBuilder out, List<MappingReader.Mapping> mappings) {
        long total = mappings.stream().mapToLong(mapping -> mapping.rows().size()).sum();
        long verified = mappings.stream()
                .flatMap(mapping -> mapping.rows().stream())
                .filter(MappingReader.Row::verified)
                .count();

        out.append("## Verification status").append(NL).append(NL);
        if (verified == 0) {
            out.append("""
                    **No row here is verified.** All %d of them are marked `verified: false`, which under R-I19 means none has
                    been checked against published profile documentation. They are not guesses — they follow
                    the table in the build specification and the shape of the message definition — but "not a
                    guess" and "verified" are different claims, and only the second one is worth trusting a
                    payment to.

                    The load-bearing one is the clearing-system identifier `JPZGN`. It names the scheme every
                    bank code in the file belongs to, and it is unconfirmed — see Q8 in [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).

                    The flag is not on the honour system: a row marked `verified: true` must cite at least two
                    independent published sources, or the build fails. That is the same bar R-0.1 sets for a
                    format descriptor.

                    """.formatted(total));
        } else {
            out.append("""
                    %d of %d rows have been checked against published profile documentation. The rest are marked
                    `unverified` in the tables below.

                    """.formatted(verified, total));
        }
    }

    private void appendMapping(StringBuilder out, MappingReader.Mapping mapping) {
        out.append("## ").append(mapping.format()).append(" ↔ ").append(mapping.message())
                .append(NL).append(NL);
        if (!mapping.note().isEmpty()) {
            out.append(mapping.note()).append(NL).append(NL);
        }

        out.append("""
                | Zengin | ISO 20022 | Direction | Loss | Status |
                |---|---|---|---|---|
                """);
        for (MappingReader.Row row : mapping.rows()) {
            out.append("| ").append(cell(row.zenginField()))
                    .append(" | ").append(cell(row.isoPath()))
                    .append(" | ").append(arrow(row.direction()))
                    .append(" | ").append(loss(row))
                    .append(" | ").append(row.verified() ? "verified" : "unverified")
                    .append(" |").append(NL);
        }
        out.append(NL);

        out.append("### Why each row works this way").append(NL).append(NL);
        for (MappingReader.Row row : mapping.rows()) {
            out.append("**").append(cell(row.zenginField())).append(" → ")
                    .append(cell(row.isoPath())).append("**").append(NL).append(NL)
                    .append(row.whyEn()).append(NL).append(NL)
                    .append(row.whyJa()).append(NL).append(NL);
        }
    }

    private void appendReadingNote(StringBuilder out) {
        out.append("""
                ## How to read the loss column

                A row with no loss carries its value unchanged in both directions. Everything else names
                what happens and how much it matters:

                | Severity | Means |
                |---|---|
                | `INFORMATIONAL` | Cosmetic. Nothing reconciles differently. |
                | `MATERIAL` | A party or a reference is noticeably altered. |
                | `CRITICAL` | The payment could mean something else, or reach somewhere else. |

                A conversion refuses on `CRITICAL` by default. See [loss.md](loss.md) for what each kind means and
                what to do about it.
                """);
    }

    private static String cell(String value) {
        return value.isEmpty() ? "—" : "`" + value + "`";
    }

    private static String arrow(String direction) {
        return switch (direction) {
            case "to-iso" -> "→ ISO";
            case "to-zengin" -> "→ Zengin";
            default -> "both";
        };
    }

    private static String loss(MappingReader.Row row) {
        return row.lossKind().isEmpty()
                ? "—"
                : "`" + row.lossKind() + "` / `" + row.lossSeverity() + "`";
    }
}
