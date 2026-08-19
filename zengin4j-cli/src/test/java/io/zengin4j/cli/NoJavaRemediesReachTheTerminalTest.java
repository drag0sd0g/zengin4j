package io.zengin4j.cli;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.cli.internal.CliMessages;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// No diagnostic tells a shell user to call a Java method.
///
/// R-E3 requires every diagnostic to say how to fix the problem, and the
/// library says it correctly for *its* audience: set
/// `ReaderOptions.builder().allowUnverifiedFormats(true)`. At a prompt that
/// is not a fix, it is a puzzle — and it reached the terminal for three different
/// failures before this test existed.
///
/// [CliMessages] translates the known remedies, which is string
/// replacement and therefore fragile: reword a library message and the
/// replacement silently stops matching. This test is what contains that. It
/// provokes every failure it can and asserts that none of the output names a
/// Java API, so a reworded message breaks the build rather than the user's
/// afternoon.
class NoJavaRemediesReachTheTerminalTest {

    @TempDir
    Path directory;

    private Path valid;
    private Path garbage;
    private Path truncated;

    @BeforeEach
    void writeFiles() throws Exception {
        valid = directory.resolve("valid.txt");
        Files.write(valid, SougouFurikomiFixtures.create().file(SeparatorStyle.CRLF, false));

        garbage = directory.resolve("garbage.txt");
        Files.writeString(garbage, "this is not a payment file at all\n", StandardCharsets.UTF_8);

        truncated = directory.resolve("truncated.txt");
        byte[] bytes = Files.readAllBytes(valid);
        Files.write(truncated, java.util.Arrays.copyOf(bytes, bytes.length - 47));
    }

    /// Every way of failing this test can construct.
    private List<String[]> failingInvocations() {
        List<String[]> invocations = new ArrayList<>();
        // R-CLI6: an unverified layout, without the flag.
        invocations.add(new String[] {"inspect", valid.toString()});
        invocations.add(new String[] {"validate", valid.toString()});
        invocations.add(new String[] {"diff", valid.toString(), valid.toString()});
        // Not a Zengin file at all.
        invocations.add(new String[] {"inspect", garbage.toString(), "--allow-unverified"});
        invocations.add(new String[] {"validate", garbage.toString(), "--allow-unverified"});
        invocations.add(new String[] {"diff", garbage.toString(), valid.toString(),
            "--allow-unverified"});
        // A record that does not fit the format.
        invocations.add(new String[] {"inspect", truncated.toString(), "--allow-unverified"});
        invocations.add(new String[] {"validate", truncated.toString(), "--allow-unverified"});
        // Things that do not exist.
        invocations.add(new String[] {"validate", directory.resolve("absent.txt").toString()});
        invocations.add(new String[] {"inspect", directory.resolve("absent.txt").toString()});
        invocations.add(new String[] {"explain", "--format=no-such-format"});
        invocations.add(new String[] {"explain", "--format=sougou-furikomi", "--field=nope"});
        invocations.add(new String[] {"generate", "--format=no-such-format"});
        invocations.add(new String[] {"generate", "--count=-3"});
        invocations.add(new String[] {"generate", "--separator=MIXED"});
        // A path that cannot be written.
        invocations.add(new String[] {"generate",
            "--out=" + directory.resolve("no").resolve("such").resolve("dir.txt")});
        // A calendar that is not one.
        invocations.add(new String[] {"validate", valid.toString(), "--allow-unverified",
            "--calendar=" + garbage});
        invocations.add(new String[] {"validate", valid.toString(), "--allow-unverified",
            "--calendar=" + directory.resolve("absent.csv")});
        return invocations;
    }

    @Test
    void noFailureNamesAJavaApi() {
        for (String[] arguments : failingInvocations()) {
            Cli result = Cli.run(arguments);

            assertThat(CliMessages.namesAJavaApi(result.all()))
                    .as("`zengin %s` told the user to call a Java method:%n%s",
                            String.join(" ", arguments), result.all())
                    .isFalse();
        }
    }

    @Test
    void noFailureLeaksAStackTrace() {
        for (String[] arguments : failingInvocations()) {
            Cli result = Cli.run(arguments);

            assertThat(result.all())
                    .as("`zengin %s` leaked a stack trace", String.join(" ", arguments))
                    .doesNotContain("\tat io.zengin4j")
                    .doesNotContain("Exception:");
        }
    }

    /// Every one of these really does fail — otherwise the test above proves nothing.
    @Test
    void everyInvocationInThisTestActuallyFails() {
        for (String[] arguments : failingInvocations()) {
            assertThat(Cli.run(arguments).status())
                    .as("`zengin %s` was expected to fail but did not", String.join(" ", arguments))
                    .isNotEqualTo(ExitCode.OK.value());
        }
    }

    /// The unverified-format remedy names the flag, since that is the common case.
    @Test
    void theUnverifiedRemedyNamesTheFlagAndTheNextStep() {
        Cli result = Cli.run("inspect", valid.toString());

        assertThat(result.err()).contains("--allow-unverified");
        assertThat(result.err()).contains("zengin explain --format=sougou-furikomi");
    }

    /// A path that does not exist says so, rather than printing the path alone.
    @Test
    void anUnwritablePathSaysWhatWentWrong() {
        Path target = directory.resolve("no").resolve("such").resolve("dir.txt");

        Cli result = Cli.run("generate", "--count=1", "--out=" + target);

        assertThat(result.status()).isEqualTo(ExitCode.IO.value());
        assertThat(result.err())
                .as("printing the bare path reads like a success line")
                .contains("no such file or directory");
    }

    /// Every flag this class suggests is a flag that exists.
    ///
    /// The table once mapped a remedy to `--record-length`, which no
    /// command accepts. Suggesting a flag that does not exist is worse than
    /// suggesting a Java method: at least the Java method is real.
    @Test
    void everySuggestedRemedyNamesAnOptionThatExists() {
        java.util.Set<String> real = new java.util.TreeSet<>();
        picocli.CommandLine parser = new picocli.CommandLine(new Zengin());
        for (picocli.CommandLine subcommand : parser.getSubcommands().values()) {
            for (picocli.CommandLine.Model.OptionSpec option
                    : subcommand.getCommandSpec().options()) {
                real.addAll(List.of(option.names()));
            }
        }

        for (String remedy : CliMessages.suggestedRemedies()) {
            String flag = remedy.split("=")[0];
            assertThat(real)
                    .as("CliMessages suggests %s, which no command accepts", remedy)
                    .contains(flag);
        }
    }

    @Test
    void theTranslationTableCoversWhatTheLibraryActuallySays() {
        assertThat(CliMessages.forTheCommandLine(
                        "set ReaderOptions.builder().allowUnverifiedFormats(true) to proceed"))
                .isEqualTo("set --allow-unverified to proceed");
        assertThat(CliMessages.forTheCommandLine(
                        "name the format with ReaderOptions.builder().format(...)."))
                .isEqualTo("name the format with --format=ID.");
        assertThat(CliMessages.forTheCommandLine(null)).isEmpty();
    }
}
