package io.zengin4j.cli;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Nothing this command prints contains a terminal escape sequence.
///
/// picocli's `Ansi.AUTO` decided the Windows CI runners were a colour
/// terminal and wrote escape codes into the usage text — so
/// `"Usage: zengin validate"` became
/// `"Usage: [1mzengin validate[21m[0m"` and two tests
/// failed on Windows and nowhere else. Invisible to a person reading a terminal;
/// very visible to anything else reading the output.
///
/// **These tests force ANSI on before running.** Without that
/// they would pass everywhere except the platform that has the problem, which is
/// how the defect got in: the same code was green on Linux and macOS across two
/// JDKs. Forcing the property reproduces the Windows condition on every machine,
/// so this is a regression test rather than a coincidence.
class NoAnsiEscapesTest {

    /// picocli reads this to decide whether to emit colour.
    private static final String ANSI_PROPERTY = "picocli.ansi";

    private static final char ESCAPE = 0x1B;

    @TempDir
    Path directory;

    private String previous;
    private Path file;

    @BeforeEach
    void forceAnsiOnAndWriteAFile() throws Exception {
        previous = System.getProperty(ANSI_PROPERTY);
        System.setProperty(ANSI_PROPERTY, "true");

        file = directory.resolve("payments.txt");
        Files.write(file, SougouFurikomiFixtures.create().file(SeparatorStyle.CRLF, false));
    }

    @AfterEach
    void restoreTheProperty() {
        if (previous == null) {
            System.clearProperty(ANSI_PROPERTY);
        } else {
            System.setProperty(ANSI_PROPERTY, previous);
        }
    }

    /// Every invocation this test can think of, successful and failing alike.
    private List<String[]> invocations() {
        String path = file.toString();
        return List.of(
                new String[] {},
                new String[] {"--help"},
                new String[] {"--version"},
                new String[] {"validate", "--help"},
                new String[] {"inspect", "--help"},
                new String[] {"generate", "--help"},
                new String[] {"diff", "--help"},
                new String[] {"explain", "--help"},
                new String[] {"explain"},
                new String[] {"explain", "--format=sougou-furikomi"},
                new String[] {"validate", path, "--allow-unverified"},
                new String[] {"inspect", path, "--annotate", "--allow-unverified"},
                new String[] {"diff", path, path, "--allow-unverified"},
                new String[] {"generate", "--count=2", "--out-format=json"},
                // Failures too: usage errors render through the same help code.
                new String[] {"validate", "--no-such-option"},
                new String[] {"frobnicate"},
                new String[] {"explain", "--format=no-such-format"},
                new String[] {"inspect", path});
    }

    @Test
    void noCommandEmitsAnEscapeSequenceEvenWhenAnsiIsForcedOn() {
        for (String[] arguments : invocations()) {
            Cli result = Cli.run(arguments);

            assertThat(result.all().indexOf(ESCAPE))
                    .as("`zengin %s` emitted a terminal escape sequence:%n%s",
                            String.join(" ", arguments),
                            result.all().replace(String.valueOf(ESCAPE), "<ESC>"))
                    .isEqualTo(-1);
        }
    }

    /// The usage text is readable as plain characters.
    ///
    /// This is the assertion that actually failed on Windows, kept in the
    /// form that failed.
    @Test
    void theUsageTextIsPlainEnoughToMatchOn() {
        assertThat(Cli.run("--help").out()).contains("Usage: zengin");
        assertThat(Cli.run().out()).contains("Usage: zengin");

        for (String command : List.of("validate", "inspect", "generate", "diff", "explain")) {
            assertThat(Cli.run(command, "--help").out())
                    .as("%s --help", command)
                    .contains("Usage: zengin " + command);
        }
    }

    /// The forcing works, so the tests above are not passing vacuously.
    @Test
    void theAnsiPropertyIsActuallySetWhileTheseTestsRun() {
        assertThat(System.getProperty(ANSI_PROPERTY))
                .as("without this the tests prove nothing on Linux or macOS")
                .isEqualTo("true");
    }
}
