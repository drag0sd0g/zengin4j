package io.zengin4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code convert} and {@code dryrun}, from the outside.
 *
 * <p>What matters here is not that the conversion works — that is tested where
 * the conversion lives. It is that the command cannot be used in a way that
 * hides what the conversion cost: the report goes to stderr so a redirect
 * cannot swallow it, the exit status distinguishes lossless from lossy from
 * dangerous, and there is no flag that turns the report off.
 */
class ConversionCommandTest {

    private static final String AS_OF = "--as-of=2026-09-01";

    private static Path zenginFile(Path directory) throws Exception {
        return Cli.generate(directory, "payments.txt",
                "--format=sougou-furikomi", "--count=3", "--seed=42");
    }

    // ------------------------------------------------------------- convert

    @Test
    void convertingUpwardsWritesTheMessageAndReportsWhatItCost(@TempDir Path directory)
            throws Exception {
        Path source = zenginFile(directory);
        Path out = directory.resolve("payments.xml");

        Cli result = Cli.run("convert", source.toString(), "--to=pain.001",
                AS_OF, "--allow-unverified", "--out=" + out);

        assertThat(result.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(Files.readString(out, StandardCharsets.UTF_8))
                .contains("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03")
                .contains("<CstmrCdtTrfInitn>");
        assertThat(result.err())
                .as("the loss report is not optional and does not go to stdout")
                .contains("TRANSLITERATED", "DEFAULTED");
        assertThat(result.out()).isEmpty();
    }

    /**
     * Without {@code --out} the message goes to stdout and the report still
     * goes to stderr, so a redirect produces a usable file and a readable
     * account of what it cost.
     */
    @Test
    void theMessageAndTheReportGoToDifferentPlaces(@TempDir Path directory) throws Exception {
        Cli result = Cli.run("convert", zenginFile(directory).toString(),
                "--to=pain.001", AS_OF, "--allow-unverified");

        assertThat(result.out()).startsWith("<?xml").contains("<Document");
        assertThat(result.err()).contains("TRANSLITERATED");
        assertThat(result.out()).doesNotContain("TRANSLITERATED");
    }

    @Test
    void convertingBackNeedsWhatTheXmlCannotSupply(@TempDir Path directory) throws Exception {
        Path xml = directory.resolve("payments.xml");
        Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001",
                AS_OF, "--allow-unverified", "--out=" + xml);

        Cli withoutCode = Cli.run("convert", xml.toString(), "--to=zengin",
                "--target-format=sougou-furikomi", AS_OF);
        assertThat(withoutCode.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(withoutCode.err()).contains("--originator-code", "R-I20");

        Cli withoutFormat = Cli.run("convert", xml.toString(), "--to=zengin",
                "--originator-code=9900000001", AS_OF);
        assertThat(withoutFormat.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(withoutFormat.err()).contains("--target-format");
    }

    @Test
    void convertingBackWritesAZenginFile(@TempDir Path directory) throws Exception {
        Path xml = directory.resolve("payments.xml");
        Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001",
                AS_OF, "--allow-unverified", "--out=" + xml);
        Path back = directory.resolve("back.txt");

        Cli result = Cli.run("convert", xml.toString(), "--to=zengin",
                "--originator-code=9900000001", "--target-format=sougou-furikomi",
                AS_OF, "--out=" + back);

        assertThat(result.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(Files.readAllBytes(back)).hasSizeGreaterThan(120 * 3);

        Cli validated = Cli.run("validate", back.toString(), "--allow-unverified");
        assertThat(validated.status())
                .as("a converted file must be one this library accepts")
                .isNotEqualTo(ExitCode.ERRORS.value());
    }

    /** §27 spells it {@code pain.001}; so does the option. */
    @Test
    void theTargetIsSpelledTheWayTheSpecificationSpellsIt(@TempDir Path directory)
            throws Exception {
        Cli wrong = Cli.run("convert", zenginFile(directory).toString(),
                "--to=pain-001", AS_OF, "--allow-unverified");

        assertThat(wrong.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(wrong.err()).contains("pain.001");
    }

    @Test
    void anUnreadableFileIsAnIoFailureRatherThanACrash(@TempDir Path directory) {
        Cli result = Cli.run("convert", directory.resolve("absent.txt").toString(),
                "--to=pain.001", AS_OF);

        assertThat(result.status()).isEqualTo(ExitCode.IO.value());
        assertThat(result.err()).contains("cannot read");
    }

    @Test
    void aDateThatIsNotADateIsAUsageError(@TempDir Path directory) throws Exception {
        Cli result = Cli.run("convert", zenginFile(directory).toString(),
                "--to=pain.001", "--as-of=last Tuesday", "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("YYYY-MM-DD");
    }

    @Test
    void anUnknownTargetFormatSaysWhatIsAvailable(@TempDir Path directory) throws Exception {
        Path xml = directory.resolve("payments.xml");
        Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001",
                AS_OF, "--allow-unverified", "--out=" + xml);

        Cli result = Cli.run("convert", xml.toString(), "--to=zengin",
                "--originator-code=9900000001", "--target-format=nonesuch", AS_OF);

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("sougou-furikomi", "kyuyo-furikomi");
    }

    /**
     * A conversion whose loss could misroute money stops, and says why.
     *
     * <p>A thirty-one-character reference into a ten-byte 顧客コード.
     */
    @Test
    void aCriticalLossStopsTheCommand(@TempDir Path directory) throws Exception {
        Path xml = directory.resolve("payments.xml");
        Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001",
                AS_OF, "--allow-unverified", "--out=" + xml);
        Files.writeString(xml, Files.readString(xml, StandardCharsets.UTF_8)
                .replaceAll("<EndToEndId>[^<]*</EndToEndId>",
                        "<EndToEndId>INVOICE-2026-000123456789012345</EndToEndId>"),
                StandardCharsets.UTF_8);

        Cli refused = Cli.run("convert", xml.toString(), "--to=zengin",
                "--originator-code=9900000001", "--target-format=sougou-furikomi", AS_OF);

        assertThat(refused.status()).isEqualTo(ExitCode.ERRORS.value());
        assertThat(refused.err()).contains("CRITICAL", "TRUNCATED", "failOnSeverity");

        Cli accepted = Cli.run("convert", xml.toString(), "--to=zengin",
                "--originator-code=9900000001", "--target-format=sougou-furikomi",
                AS_OF, "--accept-loss", "--out=" + directory.resolve("anyway.txt"));
        assertThat(accepted.status()).isEqualTo(ExitCode.ERRORS.value());
        assertThat(directory.resolve("anyway.txt")).exists();
    }

    /**
     * JSON needs a stream of its own.
     *
     * <p>stderr already carries the reader's warnings, so a JSON report written
     * there is not parseable however carefully it is produced — which is what
     * this test found when it was written against stderr.
     */
    @Test
    void theReportCanBeJsonWhenItHasSomewhereToGo(@TempDir Path directory) throws Exception {
        Path report = directory.resolve("loss.json");

        Cli result = Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001",
                AS_OF, "--allow-unverified", "--loss-format=json", "--loss-out=" + report,
                "--out=" + directory.resolve("out.xml"));

        assertThat(result.err())
                .as("the report went to the file, not to the stream carrying warnings")
                .doesNotContain("\"entries\"");
        JsonNode parsed = new ObjectMapper().readTree(Files.readString(report));
        assertThat(parsed.get("lossless").asBoolean()).isFalse();
        assertThat(parsed.get("entries")).isNotEmpty();
    }

    @Test
    void theReportCanBeJapanese(@TempDir Path directory) throws Exception {
        Cli result = Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001",
                AS_OF, "--allow-unverified", "--language=ja",
                "--out=" + directory.resolve("out.xml"));

        assertThat(result.err()).contains("全角");
    }

    /**
     * One {@code --charset} governs both reading and converting.
     *
     * <p>They are separate settings in the library — the reader has one and the
     * mapping context has one — and a command that let them disagree would
     * decode every name in the file with the wrong table while looking like it
     * worked.
     */
    @Test
    void oneCharsetGovernsBothReadingAndConverting(@TempDir Path directory) throws Exception {
        Path utf8 = directory.resolve("utf8.txt");
        Files.write(utf8, utf8File());

        Cli result = Cli.run("convert", utf8.toString(), "--to=pain.001", AS_OF,
                "--allow-unverified", "--charset=UTF_8", "--format=sougou-furikomi",
                "--out=" + directory.resolve("u.xml"));

        assertThat(result.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(Files.readString(directory.resolve("u.xml"), StandardCharsets.UTF_8))
                .as("the beneficiary name decoded with the charset the file was written in")
                .contains("<Nm>ヤマダ</Nm>");
    }

    /**
     * A UTF-8 Zengin file with names short enough to fit.
     *
     * <p>Half-width katakana is one byte in MS932 and three in UTF-8, so the
     * ordinary fixtures do not fit a 15-byte bank name at all — which is the
     * concrete reason `docs/encoding.md` warns against choosing UTF-8. Four
     * kana do fit, and four kana are enough to tell the two decodings apart.
     */
    private static byte[] utf8File() {
        var descriptor = io.zengin4j.core.format.FormatRegistry.defaults()
                .byId(io.zengin4j.core.format.FormatId.of("sougou-furikomi")).orElseThrow();
        var charset = io.zengin4j.core.charset.ZenginCharset.UTF_8;
        byte[] header = io.zengin4j.testkit.SyntheticRecords.encode(
                descriptor.record(io.zengin4j.core.format.RecordKind.HEADER), charset,
                java.util.Map.of("codeKubun", "0", "originatorCode", "9900000001",
                        "originatorName", "ﾔﾏﾀﾞ", "valueDate", "0930",
                        "originBankCode", "9999", "originBranchCode", "998",
                        "accountType", "1", "accountNumber", "9000001"));
        byte[] data = io.zengin4j.testkit.SyntheticRecords.encode(
                descriptor.record(io.zengin4j.core.format.RecordKind.DATA), charset,
                java.util.Map.of("beneficiaryBankCode", "9999", "beneficiaryBranchCode", "999",
                        "accountType", "1", "accountNumber", "9876543",
                        "beneficiaryName", "ﾔﾏﾀﾞ", "amount", "150000"));
        byte[] trailer = io.zengin4j.testkit.SyntheticRecords.encode(
                descriptor.record(io.zengin4j.core.format.RecordKind.TRAILER), charset,
                java.util.Map.of("recordCount", "1", "totalAmount", "150000"));
        byte[] end = io.zengin4j.testkit.SyntheticRecords.encode(
                descriptor.record(io.zengin4j.core.format.RecordKind.END), charset,
                java.util.Map.of());

        return io.zengin4j.testkit.SyntheticRecords.file(
                java.util.List.of(header, data, trailer, end),
                io.zengin4j.core.model.SeparatorStyle.CRLF, false);
    }

    /**
     * Every mapping flag reaches the context.
     *
     * <p>A mixin field that is declared and never read compiles, parses, prints
     * in the help text and does nothing — the failure mode that ships. Each of
     * these is asserted by its effect rather than by inspecting the context,
     * because the effect is what a user is promised.
     */
    @Test
    void everyMappingFlagReachesTheConversion(@TempDir Path directory) throws Exception {
        Path source = zenginFile(directory);

        Cli named = Cli.run("convert", source.toString(), "--to=pain.001", AS_OF,
                "--allow-unverified", "--receiver=ZENGINNET", "--message-id=BATCH-7",
                "--out=" + directory.resolve("named.xml"));
        assertThat(named.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(Files.readString(directory.resolve("named.xml"), StandardCharsets.UTF_8))
                .contains("<Id>ZENGINNET</Id>")
                .contains("<MsgId>BATCH-7</MsgId>");

        Cli dropped = Cli.run("convert", source.toString(), "--to=pain.001", AS_OF,
                "--allow-unverified", "--end-to-end=DROP",
                "--out=" + directory.resolve("dropped.xml"));
        assertThat(Files.readString(directory.resolve("dropped.xml"), StandardCharsets.UTF_8))
                .as("--end-to-end=DROP writes the value the standard defines for no reference")
                .contains("<EndToEndId>NOTPROVIDED</EndToEndId>");
        assertThat(dropped.err()).contains("EndToEndIdPolicy.DROP");
    }

    /**
     * {@code --truncate} and {@code --unmappable} are only observable on the way
     * down, where a name has to fit thirty bytes.
     */
    @Test
    void theTruncationFlagsReachTheInverseLeg(@TempDir Path directory) throws Exception {
        Path xml = directory.resolve("long-name.xml");
        Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001", AS_OF,
                "--allow-unverified", "--out=" + xml);
        // The writer indents, so the element spans lines: (?s) or this matches
        // nothing and the test passes while proving the opposite.
        Files.writeString(xml, Files.readString(xml, StandardCharsets.UTF_8)
                .replaceAll("(?s)<Cdtr>.*?</Cdtr>",
                        "<Cdtr><Nm>" + "ヤマダ".repeat(12) + "</Nm></Cdtr>"),
                StandardCharsets.UTF_8);

        Cli refusing = Cli.run("convert", xml.toString(), "--to=zengin",
                "--originator-code=9900000001", "--target-format=sougou-furikomi", AS_OF,
                "--accept-loss", "--out=" + directory.resolve("refused.txt"));
        assertThat(refusing.err())
                .as("the default policy refuses rather than shortening a payee's name")
                .contains("cannot be written into beneficiaryName");

        Cli truncating = Cli.run("convert", xml.toString(), "--to=zengin",
                "--originator-code=9900000001", "--target-format=sougou-furikomi", AS_OF,
                "--truncate=TRUNCATE_SAFE", "--unmappable=DROP", "--accept-loss",
                "--out=" + directory.resolve("cut.txt"));
        assertThat(truncating.err()).contains("TRUNCATED");
        assertThat(new String(Files.readAllBytes(directory.resolve("cut.txt")),
                java.nio.charset.Charset.forName("windows-31j")))
                .contains("ﾔﾏﾀﾞ");
    }

    @Test
    void aFormatWithNoMappingIsAnErrorRatherThanACrash(@TempDir Path directory) throws Exception {
        Path payroll = Cli.generate(directory, "payroll.txt",
                "--format=kyuyo-furikomi", "--count=2", "--seed=3");

        Cli result = Cli.run("dryrun", payroll.toString(), AS_OF, "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.ERRORS.value());
        assertThat(result.err()).contains("no mapping between kyuyo-furikomi");
    }

    @Test
    void writingToAPathThatDoesNotExistIsAnIoFailure(@TempDir Path directory) throws Exception {
        Cli result = Cli.run("convert", zenginFile(directory).toString(), "--to=pain.001",
                AS_OF, "--allow-unverified",
                "--out=" + directory.resolve("no-such-directory").resolve("x.xml"));

        assertThat(result.status()).isEqualTo(ExitCode.IO.value());
        assertThat(result.err()).contains("cannot write");
    }

    // -------------------------------------------------------------- dryrun

    @Test
    void aDryRunReportsAndWritesNothing(@TempDir Path directory) throws Exception {
        Path source = zenginFile(directory);

        Cli result = Cli.run("dryrun", source.toString(), AS_OF, "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.WARNINGS.value());
        assertThat(result.out()).contains("TRANSLITERATED", "DEFAULTED");
        assertThat(directory.toFile().list()).containsExactly("payments.txt");
    }

    @Test
    void aDryRunReportCanBeJson(@TempDir Path directory) throws Exception {
        Cli result = Cli.run("dryrun", zenginFile(directory).toString(),
                AS_OF, "--allow-unverified", "--out-format=json");

        JsonNode parsed = new ObjectMapper().readTree(result.out());
        assertThat(parsed.get("entries")).isNotEmpty();
        assertThat(parsed.get("entries").get(0).get("kind").asText()).isNotBlank();
    }

    @Test
    void aDryRunNeedsNoOriginatorCode(@TempDir Path directory) throws Exception {
        Cli result = Cli.run("dryrun", zenginFile(directory).toString(),
                AS_OF, "--allow-unverified");

        assertThat(result.status()).isNotEqualTo(ExitCode.USAGE.value());
    }

    @Test
    void aDryRunInTheOtherDirectionSaysWhatToRunInstead(@TempDir Path directory) throws Exception {
        Cli result = Cli.run("dryrun", zenginFile(directory).toString(),
                "--to=zengin", AS_OF, "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.USAGE.value());
        assertThat(result.err()).contains("convert", "--accept-loss");
    }

    @Test
    void aDryRunOnAnUnreadableFileIsAnIoFailure(@TempDir Path directory) {
        Cli result = Cli.run("dryrun", directory.resolve("absent.txt").toString(), AS_OF);

        assertThat(result.status()).isEqualTo(ExitCode.IO.value());
    }

    @Test
    void aDryRunOnSomethingThatIsNotAZenginFileFailsWithoutACrash(@TempDir Path directory)
            throws Exception {
        Path notAFile = directory.resolve("notes.txt");
        Files.writeString(notAFile, "these are notes, not a payment file");

        Cli result = Cli.run("dryrun", notAFile.toString(), AS_OF, "--allow-unverified");

        assertThat(result.status()).isEqualTo(ExitCode.ERRORS.value());
        assertThat(result.err()).isNotBlank();
    }

    @Test
    void convertingSomethingThatIsNotXmlFailsWithoutACrash(@TempDir Path directory)
            throws Exception {
        Path notXml = directory.resolve("notes.txt");
        Files.writeString(notXml, "these are notes, not a message");

        Cli result = Cli.run("convert", notXml.toString(), "--to=zengin",
                "--originator-code=9900000001", "--target-format=sougou-furikomi", AS_OF);

        assertThat(result.status()).isEqualTo(ExitCode.ERRORS.value());
        assertThat(result.err()).contains("XML declaration");
    }
}
