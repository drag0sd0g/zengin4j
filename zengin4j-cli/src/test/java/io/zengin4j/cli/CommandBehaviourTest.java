package io.zengin4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.testkit.FormatFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What each command does, beyond its exit status.
 *
 * @see ExitCodeContractTest for the status codes themselves
 * @see MaskingTest for R-CLI4
 * @see JsonOutputTest for R-CLI2
 */
class CommandBehaviourTest {
    @TempDir
    Path directory;

    static List<String> formats() {
        return FormatFixtures.supported().stream()
                .map(io.zengin4j.core.format.FormatId::value)
                .toList();
    }

    /** R-CLI3, for every format the testkit covers. */
    @ParameterizedTest
    @MethodSource("formats")
    void theSameSeedProducesTheSameFile(String format) throws Exception {
        Path first = Cli.generate(directory, format + "-1.txt",
                "--format=" + format, "--count=20", "--seed=1234");
        Path second = Cli.generate(directory, format + "-2.txt",
                "--format=" + format, "--count=20", "--seed=1234");

        assertThat(Files.readAllBytes(first))
                .as("R-CLI3: identical settings must produce identical bytes")
                .isEqualTo(Files.readAllBytes(second));
    }

    /**
     * A generated file passes the library's own validator.
     *
     * <p>The count is deliberately large. At {@code --count=5} this passed while
     * the generator's name list held a 長音 ｰ that no format permits, because
     * five draws from eight names usually miss it; 200 draws do not. A fixture
     * generator whose output its own validator rejects is worse than no
     * generator, and the bug was invisible at small sizes.
     */
    @ParameterizedTest
    @MethodSource("formats")
    void everyGeneratedFormatValidatesCleanly(String format) throws Exception {
        Path file = Cli.generate(directory, format + "-clean.txt",
                "--format=" + format, "--count=200");

        Cli result = Cli.run("validate", file.toString(), "--allow-unverified",
                "--suppress=V-306");

        assertThat(result.status())
                .as("%s generated a file its own validator objects to:%n%s", format, result.all())
                .isEqualTo(ExitCode.OK.value());
    }

    @Test
    void aDifferentSeedProducesADifferentFile() throws Exception {
        Path first = Cli.generate(directory, "s1.txt", "--count=20", "--seed=1");
        Path second = Cli.generate(directory, "s2.txt", "--count=20", "--seed=2");

        assertThat(Files.readAllBytes(first)).isNotEqualTo(Files.readAllBytes(second));
    }

    @Test
    void generateListsTheFormatsItSupports() {
        Cli result = Cli.run("generate", "--list-formats");

        assertThat(result.status()).isZero();
        assertThat(result.out().lines()).containsExactlyElementsOf(formats());
    }

    @Test
    void generateRefusesANegativeCount() {
        Cli result = Cli.run("generate", "--count=-1");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("must not be negative");
    }

    /** MIXED describes a file that already exists; no writer can follow it. */
    @Test
    void generateRefusesAMixedSeparator() {
        Cli result = Cli.run("generate", "--separator=MIXED");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("MIXED");
    }

    @Test
    void generateHonoursTheSeparatorAndEofByte() throws Exception {
        Path lf = Cli.generate(directory, "lf.txt", "--count=1", "--separator=LF");
        Path none = Cli.generate(directory, "none.txt", "--count=1", "--separator=NONE");
        Path eof = Cli.generate(directory, "eof.txt", "--count=1", "--separator=LF", "--eof-byte");

        assertThat(Files.readAllBytes(none)).hasSize(480);
        assertThat(Files.readAllBytes(lf)).hasSize(484);
        assertThat(Files.readAllBytes(eof)).hasSize(485);
    }

    @Test
    void inspectSummarisesWithoutAnnotateAndSaysHowToGetMore() throws Exception {
        Path file = Cli.generate(directory, "summary.txt", "--count=2");

        Cli result = Cli.run("inspect", file.toString(), "--allow-unverified");

        assertThat(result.out()).contains("sougou-furikomi", "120 bytes per record", "5 records");
        assertThat(result.out()).contains("--annotate");
        assertThat(result.out()).doesNotContain("項目名");
    }

    @Test
    void inspectNarrowsToOneRecord() throws Exception {
        Path file = Cli.generate(directory, "one.txt", "--count=3");

        Cli result = Cli.run("inspect", file.toString(), "--annotate", "--record=2",
                "--allow-unverified");

        assertThat(result.out()).contains("record 2");
        assertThat(result.out()).doesNotContain("record 3");
    }

    @Test
    void inspectSaysSoWhenThereIsNoSuchRecord() throws Exception {
        Path file = Cli.generate(directory, "short.txt", "--count=1");

        Cli result = Cli.run("inspect", file.toString(), "--record=99", "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("no record 99", "the file has 4");
    }

    @Test
    void inspectStatesWhenTheLayoutIsUnverified() throws Exception {
        Path file = Cli.generate(directory, "unverified.txt", "--count=1");

        Cli result = Cli.run("inspect", file.toString(), "--allow-unverified");

        assertThat(result.out()).contains("unverified");
    }

    /** R-CLI6: reading an unverified format without the flag must not silently work. */
    @Test
    void readingAnUnverifiedFormatWithoutTheFlagFails() throws Exception {
        Path file = Cli.generate(directory, "flagless.txt", "--count=1");

        Cli result = Cli.run("inspect", file.toString());

        assertThat(result.status()).isNotEqualTo(ExitCode.OK.value());
        assertThat(result.err())
                .as("the remedy must be one a shell user can act on, not a Java API call")
                .contains("--allow-unverified");
        assertThat(result.err()).doesNotContain("ReaderOptions.builder()");
    }

    /** R-CLI6: and with the flag, the warning is still printed. */
    @Test
    void theUnverifiedWarningGoesToStderrSoItSurvivesRedirection() throws Exception {
        Path file = Cli.generate(directory, "warned.txt", "--count=1");

        Cli result = Cli.run("inspect", file.toString(), "--allow-unverified");

        assertThat(result.err()).contains("has not been confirmed against two independent");
    }

    @Test
    void diffOnIdenticalFilesSaysSoAndExitsZero() throws Exception {
        Path file = Cli.generate(directory, "same.txt", "--count=3");

        Cli result = Cli.run("diff", file.toString(), file.toString(), "--allow-unverified");

        assertThat(result.status()).isZero();
        assertThat(result.out()).contains("no differences");
    }

    @Test
    void diffNamesTheFieldThatChangedAndItsByteOffset() throws Exception {
        Path before = Cli.generate(directory, "before.txt", "--count=2", "--seed=1");
        Path after = Cli.generate(directory, "after.txt", "--count=2", "--seed=2");

        Cli result = Cli.run("diff", before.toString(), after.toString(), "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(result.out()).contains("amount", "振込金額", "byte 80");
        assertThat(result.out()).containsPattern("\\d+ changed, \\d+ added, \\d+ removed");
    }

    @Test
    void diffRefusesTwoDifferentFormats() throws Exception {
        Path sougou = Cli.generate(directory, "sougou.txt", "--count=1");
        Path kyuyo = Cli.generate(directory, "kyuyo.txt", "--format=kyuyo-furikomi", "--count=1");

        Cli result = Cli.run("diff", sougou.toString(), kyuyo.toString(), "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("different formats", "sougou-furikomi", "kyuyo-furikomi");
    }

    /**
     * A large diff that cannot be aligned says so, and does not exit 1.
     *
     * <p>Exit 1 means "the files differ". Before this was guarded, two large
     * files that differ throughout produced an {@code OutOfMemoryError}, which
     * escapes uncaught and exits the JVM with status 1 — so a crash and a
     * successful comparison were indistinguishable to a script.
     */
    @Test
    void aDiffTooLargeToAlignIsReportedRatherThanCrashing() throws Exception {
        Path before = Cli.generate(directory, "big-a.txt", "--count=5000", "--seed=1");
        Path after = Cli.generate(directory, "big-b.txt", "--count=5000", "--seed=2");

        Cli result = Cli.run("diff", before.toString(), after.toString(), "--allow-unverified");

        assertThat(result.status())
                .as("must not be 1, which would mean a completed comparison")
                .isEqualTo(ExitCode.ERRORS.value());
        assertThat(result.err()).contains("differ across", "zengin inspect");
        assertThat(result.all()).doesNotContain("OutOfMemoryError", "internal error");
    }

    /** The same size of file, edited once, is compared without trouble. */
    @Test
    void aLargeFileWithOneEditStillDiffsFieldByField() throws Exception {
        Path before = Cli.generate(directory, "big-1.txt", "--count=5000", "--seed=3");
        Path after = Cli.generate(directory, "big-2.txt", "--count=5001", "--seed=3");

        Cli result = Cli.run("diff", before.toString(), after.toString(), "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(result.out()).contains("1 added");
    }

    @Test
    void diffReportsAnAddedPaymentAsAnAdditionNotAsEveryLaterRecordChanging() throws Exception {
        Path before = Cli.generate(directory, "two.txt", "--count=2", "--seed=9");
        Path after = Cli.generate(directory, "three.txt", "--count=3", "--seed=9");

        Cli result = Cli.run("diff", before.toString(), after.toString(), "--allow-unverified");

        assertThat(result.out()).contains("1 added");
    }

    @Test
    void explainListsEveryBundledFormatWhenGivenNothing() {
        Cli result = Cli.run("explain");

        assertThat(result.status()).isZero();
        for (String format : List.of("sougou-furikomi", "kyuyo-furikomi", "shoyo-furikomi",
                "kouza-furikae")) {
            assertThat(result.out()).contains(format);
        }
    }

    @Test
    void explainPrintsTheLayoutAndItsSources() {
        Cli result = Cli.run("explain", "--format=sougou-furikomi");

        assertThat(result.out()).contains("HEADER record", "DATA record", "TRAILER record",
                "END record");
        assertThat(result.out()).contains("beneficiaryName", "受取人名");
        assertThat(result.out()).contains("Sources:");
        assertThat(result.out()).contains("UNVERIFIED");
    }

    @Test
    void explainNarrowsToOneRecordKind() {
        Cli result = Cli.run("explain", "--format=sougou-furikomi", "--record=TRAILER");

        assertThat(result.out()).contains("TRAILER record");
        assertThat(result.out()).doesNotContain("DATA record");
    }

    @Test
    void explainDescribesOneFieldIncludingWhereItSits() {
        Cli result = Cli.run("explain", "--format=sougou-furikomi", "--field=amount",
                "--record=DATA");

        assertThat(result.out()).contains("振込金額", "bytes 80–89", "10 N");
    }

    @Test
    void explainSaysSoWhenThereIsNoSuchField() {
        Cli result = Cli.run("explain", "--format=sougou-furikomi", "--field=nonesuch");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("no field 'nonesuch'");
    }

    /**
     * 預金口座振替 collects rather than pays, and the descriptor's names say so.
     * A reader of this output must not come away thinking it credits anyone.
     */
    @Test
    void explainShowsDirectDebitInItsOwnDirection() {
        Cli result = Cli.run("explain", "--format=kouza-furikae", "--record=DATA");

        assertThat(result.out()).contains("payerAccountNumber", "payerName");
        assertThat(result.out()).doesNotContain("beneficiaryName");
    }
}
