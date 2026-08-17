package io.zengin4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * No command leaks payment data by default (R-CLI4).
 *
 * <p>The requirement this module most needs to keep. Terminal output becomes
 * scrollback, CI output becomes the CI provider's storage for ever, and a
 * pasted diagnostic becomes an attachment on a ticket. Every one of those is a
 * place an account number should not be, and none of them is a place it can be
 * taken back from.
 *
 * <p>Written as "the account number does not appear anywhere in the output"
 * rather than "the masking function was called", because the second passes
 * happily while some other code path prints the value.
 */
class MaskingTest {

    /** The account number the fixtures put in the data record. Invented (R-L1). */
    private static final String ACCOUNT = SougouFurikomiFixtures.BENEFICIARY_ACCOUNT;

    @TempDir
    Path directory;

    private Path file;

    @BeforeEach
    void writeAFile() throws Exception {
        file = directory.resolve("payments.txt");
        Files.write(file, SougouFurikomiFixtures.create().file(SeparatorStyle.CRLF, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXT", "JSON"})
    void inspectDoesNotPrintTheAccountNumber(String outFormat) {
        Cli result = Cli.run("inspect", file.toString(), "--annotate", "--allow-unverified",
                "--out-format=" + outFormat);

        assertThat(result.status()).as(result.all()).isZero();
        assertThat(result.all())
                .as("the account number must not appear anywhere in the output")
                .doesNotContain(ACCOUNT);
    }

    @Test
    void inspectDoesNotPrintTheAccountNumberAsHexEither() {
        Cli result = Cli.run("inspect", file.toString(), "--annotate", "--allow-unverified");

        // 9876543 as ASCII hex. Masking the decoded value while printing the
        // bytes it came from would be theatre.
        assertThat(result.all()).doesNotContain("39 38 37 36 35 34 33");
        assertThat(result.out()).contains("(masked)");
    }

    @Test
    void inspectPrintsItInFullOnlyWhenAsked() {
        Cli result = Cli.run("inspect", file.toString(), "--annotate", "--allow-unverified",
                "--unsafe-print");

        assertThat(result.out()).contains(ACCOUNT);
    }

    @Test
    void unsafePrintWarnsOnStderrSoTheWarningSurvivesRedirection() {
        Cli result = Cli.run("inspect", file.toString(), "--annotate", "--allow-unverified",
                "--unsafe-print");

        assertThat(result.err())
                .as("a caller redirecting stdout to a file must still see the warning")
                .contains("--unsafe-print");
        assertThat(result.out()).doesNotContain("warning:");
    }

    @Test
    void diffDoesNotPrintAccountNumbers() throws Exception {
        Path other = directory.resolve("other.txt");
        Files.write(other, SougouFurikomiFixtures.create()
                .file(2, SeparatorStyle.CRLF, false));

        Cli result = Cli.run("diff", file.toString(), other.toString(), "--allow-unverified");

        assertThat(result.all()).doesNotContain(ACCOUNT);
    }

    @Test
    void validateDoesNotPrintAccountNumbers() {
        Cli result = Cli.run("validate", file.toString(), "--allow-unverified");

        assertThat(result.all()).doesNotContain(ACCOUNT);
    }

    /**
     * The safe path is the default one, for every command that can print record
     * contents. A new command that forgets this fails here rather than in
     * somebody's CI log.
     */
    @Test
    void everyCommandThatReadsAFileMasksByDefault() {
        List<String[]> invocations = List.of(
                new String[] {"inspect", file.toString(), "--annotate", "--allow-unverified"},
                new String[] {"inspect", file.toString(), "--allow-unverified"},
                new String[] {"validate", file.toString(), "--allow-unverified"},
                new String[] {"diff", file.toString(), file.toString(), "--allow-unverified"});

        for (String[] arguments : invocations) {
            Cli result = Cli.run(arguments);
            assertThat(result.all())
                    .as("%s leaked an account number", String.join(" ", arguments))
                    .doesNotContain(ACCOUNT);
        }
    }

    /**
     * Generated files are synthetic, so the values in them are invented (R-L1).
     *
     * <p>Checked field by field rather than by searching the whole file for a
     * code. In a fixed-length format the fields abut, so {@code "0001"} occurs
     * inside the originator code {@code 9900000001} and inside account
     * {@code 9000001} — a substring search finds identifiers that are not
     * there. The same trap caught the CI identifier scan in Epic 2.
     */
    @Test
    void generatedFilesUseTheSyntheticRanges() throws Exception {
        Path generated = Cli.generate(directory, "generated.txt", "--count=20");
        io.zengin4j.testkit.FormatFixtures fixtures =
                io.zengin4j.testkit.SougouFurikomiFixtures.create();
        io.zengin4j.core.model.ZenginFile parsed = io.zengin4j.core.codec.ZenginReaders
                .readFile(generated, fixtures.readerOptions());

        io.zengin4j.core.format.RecordDescriptor layout =
                fixtures.descriptor().record(io.zengin4j.core.format.RecordKind.DATA);
        io.zengin4j.core.format.FieldDescriptor bank = layout.field("beneficiaryBankCode");
        io.zengin4j.core.format.FieldDescriptor branch = layout.field("beneficiaryBranchCode");
        io.zengin4j.core.format.FieldDescriptor account = layout.field("accountNumber");

        for (io.zengin4j.core.model.DataRecord record : parsed.allData()) {
            byte[] bytes = record.rawBytes();
            assertThat(read(bytes, bank)).isEqualTo("9999");
            assertThat(read(bytes, branch)).isEqualTo("999");
            assertThat(read(bytes, account))
                    .as("accounts must begin with 9, the invented range")
                    .startsWith("9");
        }
    }

    private static String read(byte[] record, io.zengin4j.core.format.FieldDescriptor field) {
        return new String(record, field.offset(), field.length(),
                java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}
