package io.zengin4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The options that change what a command does, rather than whether it works.
 *
 * <p>Each of these is a switch somebody will reach for on a bad afternoon:
 * a Japanese report, a suppressed rule, a calendar of their own, a file in an
 * encoding the default does not read.
 */
class OptionHandlingTest {

    @TempDir
    Path directory;

    // ------------------------------------------------------------- language

    @Test
    void findingsCanBeAskedForInJapanese() throws Exception {
        Path file = duplicatedPayments();

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified", "--language=ja");

        assertThat(result.out()).contains("レコード", "件");
        assertThat(result.out()).doesNotContain("Not submittable");
    }

    @Test
    void findingsCanBeAskedForInEnglish() throws Exception {
        Path file = duplicatedPayments();

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified", "--language=en");

        assertThat(result.out()).contains("warning(s)");
    }

    // ------------------------------------------------------------- suppress

    @Test
    void aSuppressedRuleStopsAffectingTheExitStatus() throws Exception {
        Path file = duplicatedPayments();

        Cli before = Cli.run("validate", file.toString(), "--allow-unverified");
        Cli after = Cli.run("validate", file.toString(), "--allow-unverified", "--suppress=V-306");

        assertThat(before.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(before.out()).contains("V-306");
        assertThat(after.status())
                .as("suppressing the only finding must make the run clean")
                .isEqualTo(ExitCode.OK.value());
        assertThat(after.out()).doesNotContain("V-306");
    }

    @Test
    void severalRulesCanBeSuppressedAtOnce() throws Exception {
        Path file = duplicatedPayments();

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--suppress=V-306,V-605,V-504");

        assertThat(result.status()).isEqualTo(ExitCode.OK.value());
    }

    // ------------------------------------------------------------- calendar

    @Test
    void theBundledCalendarCanBeSwitchedOnAndSaysHowLongItIsGoodFor() throws Exception {
        Path file = Cli.generate(directory, "dated.txt", "--count=1");

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--calendar=bundled");

        assertThat(result.err()).contains("bundled Japanese bank calendar", "valid to 2027");
    }

    /**
     * A calendar of the caller's own is read, and its holidays are applied.
     *
     * <p>The holiday is declared for every year in range rather than for one.
     * The fixture's value date is the yearless {@code 0930}, and the reader
     * resolves it <em>forward from today</em> — so a CSV naming only
     * {@code 2026-09-30} silently stops matching on 1 October 2026, and this
     * test would have begun failing six weeks after it was written. A test that
     * expires is worse than no test: it fails long after the change that would
     * explain it.
     */
    @Test
    void aCalendarOfYourOwnCanBeSupplied() throws Exception {
        StringBuilder csv = new StringBuilder("# a calendar of my own\nhorizon=2099-12-31\n");
        for (int year = 2020; year <= 2099; year++) {
            csv.append(year).append("-09-30,Invented Bank Holiday\n");
        }
        Path holidays = directory.resolve("holidays.csv");
        Files.writeString(holidays, csv.toString(), StandardCharsets.UTF_8);
        Path file = Cli.generate(directory, "onholiday.txt", "--count=1");

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--calendar=" + holidays);

        assertThat(result.err()).contains("valid to 2099-12-31");
        assertThat(result.out())
                .as("the value date 0930 falls on the invented holiday, whichever year it lands in")
                .contains("V-502");
        assertThat(result.status()).isEqualTo(ExitCode.ERRORS.value());
    }

    @Test
    void aCalendarWithNoHorizonIsRefusedRatherThanTrusted() throws Exception {
        Path holidays = directory.resolve("nohorizon.csv");
        Files.writeString(holidays, "2026-09-30,Something\n", StandardCharsets.UTF_8);
        Path file = Cli.generate(directory, "any.txt", "--count=1");

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--calendar=" + holidays);

        assertThat(result.status()).isNotEqualTo(ExitCode.OK.value());
        assertThat(result.err())
                .as("without a horizon it cannot tell a business day from a year it lacks data for")
                .contains("horizon");
    }

    @Test
    void aMalformedCalendarLineNamesTheLine() throws Exception {
        Path holidays = directory.resolve("broken.csv");
        Files.writeString(holidays, """
                horizon=2030-12-31
                2026-09-30,Fine
                this line is not a date
                """, StandardCharsets.UTF_8);
        Path file = Cli.generate(directory, "any2.txt", "--count=1");

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--calendar=" + holidays);

        assertThat(result.err()).contains("line 3");
    }

    @Test
    void aMissingCalendarFileIsAnIoError() throws Exception {
        Path file = Cli.generate(directory, "any3.txt", "--count=1");

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--calendar=" + directory.resolve("absent.csv"));

        assertThat(result.status()).isEqualTo(ExitCode.IO.value());
    }

    // -------------------------------------------------------------- charset

    @Test
    void aFileCanBeReadInAnExplicitEncoding() throws Exception {
        Path file = directory.resolve("sjis.txt");
        Files.write(file, SougouFurikomiFixtures
                .using(io.zengin4j.core.format.FormatRegistry.defaults()
                                .byId(SougouFurikomiFixtures.FORMAT).orElseThrow(),
                        io.zengin4j.core.charset.ZenginCharset.SHIFT_JIS)
                .file(SeparatorStyle.CRLF, false));

        Cli result = Cli.run("inspect", file.toString(), "--annotate", "--allow-unverified",
                "--charset=SHIFT_JIS");

        assertThat(result.status()).as(result.all()).isZero();
        assertThat(result.out()).contains("ﾔﾏﾀﾞ ﾀﾛｳ");
    }

    /**
     * UTF-8 does not fit these formats, and the library says so rather than
     * truncating.
     *
     * <p>Half-width katakana is one byte in Shift_JIS and <em>three</em> in
     * UTF-8, so ﾃｽﾄｷﾞﾝｺｳ needs 24 bytes in a 15-byte 被仕向銀行名. This is the
     * concrete reason {@code docs/encoding.md} warns against choosing UTF-8 for
     * a fixed-length payment file, and it is worth pinning: the failure mode if
     * the codec ever truncated instead would be a silently shortened bank name.
     */
    @Test
    void utf8DoesNotFitAFixedLengthNameFieldAndIsRefusedRatherThanTruncated() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                SougouFurikomiFixtures
                        .using(io.zengin4j.core.format.FormatRegistry.defaults()
                                        .byId(SougouFurikomiFixtures.FORMAT).orElseThrow(),
                                io.zengin4j.core.charset.ZenginCharset.UTF_8)
                        .file()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not fit a 15-byte field")
                .hasMessageContaining("rather than letting the codec truncate it");
    }

    @Test
    void anUnknownCharsetIsAUsageErrorThatNamesTheChoices() throws Exception {
        Path file = Cli.generate(directory, "any4.txt", "--count=1");

        Cli result = Cli.run("inspect", file.toString(), "--charset=EBCDIC");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("MS932");
    }

    // ------------------------------------------------------------- pinning

    @Test
    void theFormatCanBePinnedRatherThanDetected() throws Exception {
        Path file = Cli.generate(directory, "pinned.txt", "--count=1");

        Cli result = Cli.run("inspect", file.toString(), "--allow-unverified",
                "--format=sougou-furikomi");

        assertThat(result.status()).isZero();
        assertThat(result.out()).contains("sougou-furikomi");
    }

    // -------------------------------------------------------------- lenient

    @Test
    void lenientModeKeepsReadingPastARecordThatDoesNotFit() throws Exception {
        Path file = Cli.generate(directory, "ragged.txt", "--count=2");
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length - 30));

        Cli strict = Cli.run("inspect", file.toString(), "--allow-unverified");
        Cli lenient = Cli.run("inspect", file.toString(), "--allow-unverified", "--lenient");

        assertThat(strict.status()).isNotEqualTo(ExitCode.OK.value());
        assertThat(lenient.status()).as(lenient.all()).isZero();
    }

    // ------------------------------------------------------------ to stdout

    @Test
    void generateWritesToStdoutWhenGivenNoOutputPath() {
        Cli result = Cli.run("generate", "--count=1", "--out-format=json");

        assertThat(result.status()).isZero();
        assertThat(result.out()).contains("\"synthetic\": true");
        assertThat(result.out()).doesNotContain("\"path\"");
    }

    private Path duplicatedPayments() throws Exception {
        Path file = directory.resolve("duplicated.txt");
        Files.write(file, SougouFurikomiFixtures.create().file(2, SeparatorStyle.CRLF, false));
        return file;
    }
}
