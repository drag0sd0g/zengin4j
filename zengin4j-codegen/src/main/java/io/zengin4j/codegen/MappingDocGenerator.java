package io.zengin4j.codegen;

import java.nio.file.Path;
import java.util.List;

/**
 * Generates {@code docs/mapping.md} from the declared mappings.
 *
 * <p>R-I19 requires unverified rows to be visibly marked in the generated
 * documentation. Generating the page from the same file the mapper is compiled
 * from is what makes that mark trustworthy: a row cannot be quietly implemented
 * one way and documented another.
 */
final class MappingDocGenerator {

    private static final String NL = "\n";

    private final Path docsOut;

    MappingDocGenerator(Path docsOut) {
        this.docsOut = docsOut;
    }

    /**
     * Generates the page.
     *
     * @param mappings the declarations
     * @param sources  each declaration's file name
     * @return the generated file
     */
    GeneratedFile generate(List<MappingReader.Mapping> mappings, List<String> sources) {
        StringBuilder out = new StringBuilder();
        out.append("# Mapping reference").append(NL).append(NL)
                .append("> GENERATED from ").append(String.join(", ", sources))
                .append(" — do not edit. Change a declaration and run").append(NL)
                .append("> `./gradlew generateFormatSources`; the build fails if this page and ")
                .append("the declarations disagree.").append(NL).append(NL);

        out.append("Every correspondence this library implements between a Zengin field and an ")
                .append("ISO 20022").append(NL)
                .append("element, in both directions, with what each one costs.").append(NL)
                .append(NL);

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
            out.append("**No row here is verified.** All ").append(total)
                    .append(" of them are marked `verified: false`, which under R-I19 means ")
                    .append("none has").append(NL)
                    .append("been checked against published profile documentation. They are not ")
                    .append("guesses — they follow").append(NL)
                    .append("the table in the build specification and the shape of the message ")
                    .append("definition — but \"not a").append(NL)
                    .append("guess\" and \"verified\" are different claims, and only the second ")
                    .append("one is worth trusting a").append(NL)
                    .append("payment to.").append(NL).append(NL)
                    .append("The load-bearing one is the clearing-system identifier `JPZGN`. ")
                    .append("It names the scheme every").append(NL)
                    .append("bank code in the file belongs to, and it is unconfirmed — see Q8 in ")
                    .append("[OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).").append(NL).append(NL)
                    .append("The flag is not on the honour system: a row marked `verified: true` ")
                    .append("must cite at least two").append(NL)
                    .append("independent published sources, or the build fails. That is the same ")
                    .append("bar R-0.1 sets for a").append(NL)
                    .append("format descriptor.").append(NL).append(NL);
        } else {
            out.append(verified).append(" of ").append(total)
                    .append(" rows have been checked against published profile documentation. ")
                    .append("The rest are marked").append(NL)
                    .append("`unverified` in the tables below.").append(NL).append(NL);
        }
    }

    private void appendMapping(StringBuilder out, MappingReader.Mapping mapping) {
        out.append("## ").append(mapping.format()).append(" ↔ ").append(mapping.message())
                .append(NL).append(NL);
        if (!mapping.note().isEmpty()) {
            out.append(mapping.note()).append(NL).append(NL);
        }

        out.append("| Zengin | ISO 20022 | Direction | Loss | Status |").append(NL)
                .append("|---|---|---|---|---|").append(NL);
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
        out.append("## How to read the loss column").append(NL).append(NL)
                .append("A row with no loss carries its value unchanged in both directions. ")
                .append("Everything else names").append(NL)
                .append("what happens and how much it matters:").append(NL).append(NL)
                .append("| Severity | Means |").append(NL)
                .append("|---|---|").append(NL)
                .append("| `INFORMATIONAL` | Cosmetic. Nothing reconciles differently. |")
                .append(NL)
                .append("| `MATERIAL` | A party or a reference is noticeably altered. |")
                .append(NL)
                .append("| `CRITICAL` | The payment could mean something else, or reach ")
                .append("somewhere else. |").append(NL).append(NL)
                .append("A conversion refuses on `CRITICAL` by default. See ")
                .append("[loss.md](loss.md) for what each kind means and").append(NL)
                .append("what to do about it.").append(NL);
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
