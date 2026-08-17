package io.zengin4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The exit codes are a contract (R-CLI1).
 *
 * <p>Scripts branch on these. Changing what a value means would silently change
 * what somebody's pipeline does, so each one is pinned here rather than left to
 * whatever the code happens to return.
 */
class ExitCodeContractTest {

    @TempDir
    Path directory;

    @Test
    void theCodesAreTheDocumentedNumbers() {
        assertThat(ExitCode.OK.value()).isZero();
        assertThat(ExitCode.WARNINGS.value()).isEqualTo(1);
        assertThat(ExitCode.ERRORS.value()).isEqualTo(2);
        assertThat(ExitCode.USAGE.value()).isEqualTo(3);
        assertThat(ExitCode.IO.value()).isEqualTo(4);
    }

    @Test
    void aCleanFileExitsZero() throws Exception {
        Path file = Cli.generate(directory, "clean.txt", "--count=3");

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified");

        assertThat(result.status()).as(result.all()).isEqualTo(ExitCode.OK.value());
    }

    @Test
    void warningsAloneExitOne() throws Exception {
        // Two identical payments trip V-306, which is a warning by design.
        Path file = directory.resolve("duplicated.txt");
        Files.write(file, io.zengin4j.testkit.SougouFurikomiFixtures.create()
                .file(2, io.zengin4j.core.model.SeparatorStyle.CRLF, false));

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified");

        assertThat(result.out()).contains("V-306");
        assertThat(result.status()).as(result.all()).isEqualTo(ExitCode.WARNINGS.value());
    }

    @Test
    void errorsExitTwo() throws Exception {
        Path file = Cli.generate(directory, "broken.txt", "--count=2");
        // Truncating the last record makes it the wrong length: V-101, an error.
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length - 40));

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified", "--lenient");

        assertThat(result.status()).as(result.all()).isEqualTo(ExitCode.ERRORS.value());
    }

    @Test
    void aFileThatIsNotAZenginFileAtAllStillProducesAReport() throws Exception {
        Path file = directory.resolve("not-zengin.txt");
        Files.writeString(file, "this is a text file, not a payment instruction\n",
                StandardCharsets.UTF_8);

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified");

        assertThat(result.status()).as(result.all()).isEqualTo(ExitCode.ERRORS.value());
        assertThat(result.out()).as("R-V1: reported, not thrown").contains("V-100");
        assertThat(result.all()).doesNotContain("Exception");
    }

    @Test
    void anUnknownOptionIsAUsageError() throws Exception {
        Path file = Cli.generate(directory, "any.txt", "--count=1");

        Cli result = Cli.run("validate", file.toString(), "--no-such-option");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("Unknown option");
    }

    @Test
    void anUnknownSubcommandIsAUsageError() {
        Cli result = Cli.run("frobnicate");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
    }

    @Test
    void noSubcommandAtAllIsAUsageErrorRatherThanSuccess() {
        Cli result = Cli.run();

        assertThat(result.status())
                .as("a bare `zengin` asked for nothing; exiting 0 would tell a script it worked")
                .isEqualTo(ExitCode.USAGE.value());
        assertThat(result.out()).contains("Usage: zengin");
    }

    @Test
    void aMissingFileIsAnIoErrorNotACrash() {
        Cli result = Cli.run("validate", directory.resolve("absent.txt").toString());

        assertThat(result.status()).isEqualTo(ExitCode.IO.value());
        assertThat(result.err()).contains("cannot read");
        assertThat(result.all()).doesNotContain("Exception");
    }

    @Test
    void anUnknownFormatIsAUsageErrorWithTheKnownOnesNamed() {
        Cli result = Cli.run("explain", "--format=furikomi-nyukin-tsuchi");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("sougou-furikomi");
    }

    @Test
    void generatingAFormatWithoutFixturesIsAUsageErrorNotAStackTrace() {
        Cli result = Cli.run("generate", "--format=furikomi-nyukin-tsuchi");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("no fixtures for format");
        assertThat(result.all()).doesNotContain("Exception");
    }

    @Test
    void helpAndVersionSucceed() {
        assertThat(Cli.run("--help").status()).isZero();
        assertThat(Cli.run("--version").status()).isZero();
        assertThat(Cli.run("--version").out()).contains("zengin4j");
    }

    @Test
    void everySubcommandHasItsOwnHelp() {
        for (String command : java.util.List.of("validate", "inspect", "generate", "diff",
                "explain")) {
            Cli result = Cli.run(command, "--help");
            assertThat(result.status()).as("%s --help", command).isZero();
            assertThat(result.out()).as("%s --help", command).contains("Usage: zengin " + command);
        }
    }
}
