package io.zengin4j.cli;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.cli.internal.FieldRendering;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `docs/cli.md` matches the parser.
///
/// A reference that lists an option the tool does not have sends somebody to
/// a usage error; one that omits an option they needed sends them away. Both are
/// the kind of rot that sets in quietly, so the page is checked rather than
/// trusted — the same treatment `docs/validation-rules.md` gets.
class CliReferenceTest {

    private static final Path REFERENCE = Path.of("..", "docs", "cli.md");

    private static final Pattern OPTION = Pattern.compile("--[a-z][a-z-]+");

    private static String reference() throws IOException {
        return Files.readString(REFERENCE, StandardCharsets.UTF_8);
    }

    private static CommandLine parser() {
        return new CommandLine(new Zengin());
    }

    @Test
    void theReferenceExists() {
        assertThat(REFERENCE)
                .as("docs/cli.md is where the commands are documented")
                .exists();
    }

    @Test
    void everySubcommandIsDocumented() throws IOException {
        String text = reference();

        for (String name : parser().getSubcommands().keySet()) {
            assertThat(text)
                    .as("`zengin %s` exists but docs/cli.md does not document it", name)
                    .contains("## `" + name + "`");
        }
    }

    @Test
    void everyDocumentedSubcommandExists() throws IOException {
        Set<String> documented = new TreeSet<>();
        Matcher matcher = Pattern.compile("^## `([a-z]+)`$", Pattern.MULTILINE)
                .matcher(reference());
        while (matcher.find()) {
            documented.add(matcher.group(1));
        }

        assertThat(documented)
                .as("docs/cli.md documents a command that does not exist")
                .isSubsetOf(parser().getSubcommands().keySet());
        assertThat(documented).isNotEmpty();
    }

    /// Every option the parser accepts appears somewhere on the page.
    ///
    /// Deliberately not the reverse for options: the page mentions
    /// `--help` and shell fragments, and demanding an exact match would
    /// make the check about prose rather than about accuracy.
    @Test
    void everyOptionIsMentioned() throws IOException {
        String text = reference();

        for (var entry : parser().getSubcommands().entrySet()) {
            for (CommandLine.Model.OptionSpec option : entry.getValue().getCommandSpec()
                    .options()) {
                for (String name : option.names()) {
                    if (name.equals("-h") || name.equals("-V")
                            || name.equals("--help") || name.equals("--version")) {
                        continue;
                    }
                    assertThat(text)
                            .as("zengin %s %s is not mentioned in docs/cli.md",
                                    entry.getKey(), name)
                            .contains(name);
                }
            }
        }
    }

    /// Nothing on the page claims an option that no command accepts.
    @Test
    void noDocumentedOptionIsInvented() throws IOException {
        Set<String> real = new TreeSet<>(List.of("--help", "--version"));
        for (var subcommand : parser().getSubcommands().values()) {
            for (CommandLine.Model.OptionSpec option : subcommand.getCommandSpec().options()) {
                real.addAll(List.of(option.names()));
            }
        }

        Set<String> mentioned = new TreeSet<>();
        Matcher matcher = OPTION.matcher(reference());
        while (matcher.find()) {
            mentioned.add(matcher.group());
        }

        assertThat(mentioned)
                .as("docs/cli.md mentions an option no command accepts")
                .isSubsetOf(real);
    }

    @Test
    void theExitCodesOnThePageAreTheRealOnes() throws IOException {
        String text = reference();

        for (ExitCode code : ExitCode.values()) {
            assertThat(text)
                    .as("exit code %d (%s) must be documented", code.value(), code)
                    .containsPattern("\\| `" + code.value() + "` \\|");
        }
    }

    /// The sample table on the page has the columns the table actually emits.
    ///
    /// Not an exact match — the sample elides rows, and column widths depend
    /// on the data — but a renamed or dropped column shows up here rather than
    /// in a reader's confusion. The headings are the part that carries meaning.
    @Test
    void theSampleTableHasTheColumnsTheToolPrints() throws IOException {
        String text = reference();

        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        FieldRendering.table(writer, sampleRows());
        writer.flush();
        String headingRow = buffer.toString().lines().findFirst().orElseThrow();

        for (String heading : headingRow.trim().split("\\s+")) {
            assertThat(text)
                    .as("the table prints a '%s' column; docs/cli.md does not show it", heading)
                    .contains(heading);
        }
    }

    private static List<FieldRendering.Row> sampleRows() {
        FormatDescriptor descriptor = FormatRegistry.defaults()
                .byId(SougouFurikomiFixtures.FORMAT).orElseThrow();
        byte[] record = SougouFurikomiFixtures.create().data();
        List<FieldRendering.Row> rows = new java.util.ArrayList<>();
        for (FieldDescriptor field : descriptor.record(RecordKind.DATA).fields()) {
            rows.add(FieldRendering.render(field, record,
                    io.zengin4j.core.charset.ZenginCharset.MS932, false));
        }
        return rows;
    }

    /// Every command §27 names exists.
    ///
    /// This used to assert the opposite for `convert` and
    /// `dryrun` — that they were absent and the page said so — which was
    /// the honest thing to check while they were absent. They arrived in Epic 7,
    /// and the check that replaces it is the one worth keeping: the synopsis in
    /// the specification is now implemented in full, and a command quietly
    /// disappearing would be a regression nothing else notices.
    @Test
    void everyCommandTheSpecificationNamesExists() {
        assertThat(parser().getSubcommands())
                .containsKeys("validate", "inspect", "convert", "dryrun",
                        "generate", "diff", "explain");
    }
}
