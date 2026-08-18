package io.zengin4j.iso20022.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossReport;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.envelope.BusinessApplicationHeader;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.envelope.ZediMessage;
import io.zengin4j.iso20022.loss.MappingLossReport;
import io.zengin4j.iso20022.pain001.Account;
import io.zengin4j.iso20022.pain001.Agent;
import io.zengin4j.iso20022.pain001.CreditTransferTransaction;
import io.zengin4j.iso20022.pain001.GroupHeader;
import io.zengin4j.iso20022.pain001.Money;
import io.zengin4j.iso20022.pain001.Pain001Document;
import io.zengin4j.iso20022.pain001.PaymentInstruction;
import io.zengin4j.iso20022.pain001.Party;
import io.zengin4j.iso20022.pain001.RemittanceInformation;
import io.zengin4j.iso20022.xml.XmlElement;
import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The cases a real file eventually produces and a fixture never does.
 *
 * <p>An empty batch, a date that is not a date, an account with no type, a name
 * with a quotation mark in it. None is exotic — each is a file somebody sends
 * on a Tuesday — and each is a branch that would otherwise be reached first in
 * production.
 */
class EdgeCaseTest {

    private static final FormatId FORMAT = FormatId.of("sougou-furikomi");
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

    private static FormatFixtures fixtures() {
        return FormatFixtures.forFormat(FORMAT);
    }

    private static MappingContext.Builder context() {
        return MappingContext.builder("9900000001", REFERENCE)
                .targetFormat(fixtures().descriptor())
                .acceptAnyLoss();
    }

    private static ZenginFile read(byte[] bytes) {
        return ZenginReaders.readFile(new ByteArrayInputStream(bytes), fixtures().readerOptions());
    }

    private static byte[] headerWith(Map<String, String> overrides) {
        Map<String, String> values = new LinkedHashMap<>(Map.of(
                "codeKubun", "0",
                "originatorCode", "9900000001",
                "originatorName", "ﾃｽﾄｼﾖｳｼﾞ",
                "valueDate", "0930",
                "originBankCode", "9999",
                "originBranchCode", "998",
                "accountType", "1",
                "accountNumber", "9000001"));
        values.putAll(overrides);
        return SyntheticRecords.encodeUnchecked(
                fixtures().descriptor().record(RecordKind.HEADER), ZenginCharset.MS932, values);
    }

    private static ZenginFile fileWithHeader(byte[] header) {
        return read(SyntheticRecords.file(List.of(
                header, fixtures().data(), fixtures().trailer(1, 150_000L), fixtures().end()),
                SeparatorStyle.CRLF, false));
    }

    private static ZediFile isoFile(PaymentInstruction... instructions) {
        return ZediFile.of(ZediMessage.of(
                new BusinessApplicationHeader("9900000001", "9999", "M1",
                        MessageId.PAIN_001_001_03, OffsetDateTime.parse("2026-09-01T00:00:00Z")),
                new Pain001Document(
                        new GroupHeader("MSG-1", OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                                new Party("テストシヨウジ", "9900000001")),
                        List.of(instructions)).toXml()));
    }

    // ---------------------------------------------------------------- dates

    /**
     * 0229 in a year that has no 29 February.
     *
     * <p>A leap day is never resolved backwards (ADR-0014) and the resolver
     * looks only one year ahead, so 0229 converted in 2026 — with 2026 and 2027
     * both ordinary years — has no execution date at all. The reference date is
     * used, and that is critical: the bank decides the timing of a payment file
     * whose execution date was made up.
     */
    @Test
    void aLeapDayInANonLeapYearFallsBackAndSaysItIsCritical() {
        MappingContext nonLeap = MappingContext.builder("9900000001", LocalDate.of(2026, 3, 1))
                .targetFormat(fixtures().descriptor())
                .acceptAnyLoss()
                .build();

        MappingResult<ZediFile> result = Iso20022Mapper.create()
                .toIso(fileWithHeader(headerWith(Map.of("valueDate", "0229"))), nonLeap);

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("0229"));
    }

    @Test
    void aHeaderWithNoExecutionDateFallsBackToTheReferenceDate() {
        MappingResult<ZediFile> result = Iso20022Mapper.create()
                .toIso(fileWithHeader(headerWith(Map.of("valueDate", "0000"))), context().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.DEFAULTED));
        assertThat(result.output().onlyMessage().body()
                .textAt("CstmrCdtTrfInitn/PmtInf/ReqdExctnDt"))
                .contains("2026-09-01");
    }

    // -------------------------------------------------------------- emptiness

    /**
     * A file with no batches has to be constructed rather than read — the
     * reader refuses one, correctly — but the mapper still has to survive it,
     * because a caller can build one and nothing stops them.
     */
    private static ZenginFile withoutBatches() {
        return new ZenginFile(fixtures().descriptor(), List.of(),
                java.util.Optional.empty(), List.of(),
                new FileFraming(false, SeparatorStyle.CRLF, false, false));
    }

    @Test
    void aFileWithNoBatchesConvertsToADocumentWithNoPayments() {
        ZenginFile empty = withoutBatches();

        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(empty, context().build());

        XmlElement body = result.output().onlyMessage().body();
        assertThat(body.textAt("CstmrCdtTrfInitn/GrpHdr/NbOfTxs")).contains("0");
        assertThat(body.at("CstmrCdtTrfInitn").orElseThrow().childrenNamed("PmtInf")).isEmpty();
    }

    /**
     * A file whose header record never arrived names no recipient, and the
     * business application header says so by omission rather than by inventing
     * one.
     */
    @Test
    void aFileWithNoHeaderRecordProducesAnEnvelopeThatAdmitsItHasNoRecipient() {
        ZenginFile empty = withoutBatches();

        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(empty, context().build());

        assertThat(result.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("names no recipient"));
        assertThat(result.output().onlyMessage().header().orElseThrow().to()).isEmpty();
    }

    @Test
    void aDocumentWithNoPaymentInstructionsStillProducesAFile() {
        MappingResult<ZenginFile> result =
                Iso20022Mapper.create().toZengin(isoFile(), context().build());

        assertThat(result.output().allData()).isEmpty();
        assertThat(result.output().batches()).hasSize(1);
    }

    private static PaymentInstruction instructionOn(LocalDate date, String debitAccount) {
        return new PaymentInstruction("P-1", date, Party.named("テストシヨウジ"),
                new Account(debitAccount, "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("9876543", "1"), RemittanceInformation.NONE)));
    }

    /**
     * Instructions that agree lose only their grouping.
     *
     * <p>A Zengin batch has one execution date and one debit account. When
     * every {@code PmtInf} names the same ones, flattening costs nothing a
     * downstream system reads — so reporting it as material would send somebody
     * hunting for a loss that did not happen.
     */
    @Test
    void instructionsThatAgreeFlattenWithoutMaterialLoss() {
        PaymentInstruction same = instructionOn(LocalDate.of(2026, 9, 30), "9000001");

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(same, same), context().build());

        assertThat(result.output().allData()).hasSize(2);
        assertThat(result.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("only the grouping is lost"));
        assertThat(result.loss().atLeast(LossSeverity.MATERIAL))
                .noneSatisfy(entry -> assertThat(entry.explanationEn()).contains("PmtInf blocks"));
    }

    /**
     * Instructions that disagree do not.
     *
     * <p>The first block's execution date and debit account are applied to
     * every payment in the file, including ones that asked for something else.
     * That is not a note about structure.
     */
    @Test
    void instructionsThatDisagreeAreFlattenedAndReportedCritical() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(instructionOn(LocalDate.of(2026, 9, 30), "9000001"),
                        instructionOn(LocalDate.of(2026, 10, 31), "9000002")),
                context().build());

        assertThat(result.output().allData()).hasSize(2);
        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("do not agree on execution date"));
    }

    /**
     * {@code InstrId} has nowhere to go, and says so.
     *
     * <p>The debtor's own reference, distinct from the one the creditor
     * reconciles against. Both 顧客コード fields are already spoken for.
     */
    @Test
    void anInstructionIdThatCannotBeCarriedIsReported() {
        PaymentInstruction withInstrId = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "DEBTOR-REF-9", Money.yen(1),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("9876543", "1"), RemittanceInformation.NONE)));

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(withInstrId), context().build());

        assertThat(result.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("InstrId"));
    }

    @Test
    void aDocumentWithNoInstructionIdReportsNothingAboutOne() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(instructionOn(LocalDate.of(2026, 9, 30), "9000001")),
                        context().build());

        assertThat(result.loss().entries())
                .noneSatisfy(entry -> assertThat(entry.explanationEn()).contains("InstrId"));
    }

    /**
     * The context supplies 委託者コード and the XML's own identifier is replaced.
     *
     * <p>Reported only when they differ: when they agree, nothing was replaced.
     */
    @Test
    void anOriginatorCodeTheContextOverridesIsReportedOnlyWhenItDiffers() {
        MappingResult<ZenginFile> differing = Iso20022Mapper.create().toZengin(
                isoFile(instructionOn(LocalDate.of(2026, 9, 30), "9000001")),
                MappingContext.builder("9911111111", REFERENCE)
                        .targetFormat(fixtures().descriptor()).acceptAnyLoss().build());
        assertThat(differing.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("InitgPty identifier is '9900000001'"));

        MappingResult<ZenginFile> agreeing = Iso20022Mapper.create()
                .toZengin(isoFile(instructionOn(LocalDate.of(2026, 9, 30), "9000001")),
                        context().build());
        assertThat(agreeing.loss().entries())
                .noneSatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("InitgPty identifier"));
    }

    /**
     * An account with no 預金種目 gets 普通預金 assumed, and that is critical.
     *
     * <p>An account type that is wrong sends the payment to a different account
     * at the same branch — the same number can exist under two types.
     */
    @Test
    void anAccountWithNoTypeIsAssumedOrdinaryAndSaidToBeCritical() {
        PaymentInstruction untyped = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", ""), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("9876543", ""), RemittanceInformation.NONE)));

        MappingResult<ZenginFile> result =
                Iso20022Mapper.create().toZengin(isoFile(untyped), context().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("普通預金"));
    }

    // ---------------------------------------------------------------- amounts

    /**
     * Amounts a {@code pain.001} can legitimately carry and 振込金額 cannot.
     *
     * <p>Each of these threw an untyped exception out of the mapper before this
     * test existed — an {@code ArithmeticException} from {@code longValueExact},
     * and two {@code IllegalArgumentException}s from the encoder. A whole file
     * failing because one payment is too large is the wrong outcome for a
     * module whose entire design is "report it and let the threshold decide".
     */
    @Test
    void anAmountTheFieldCannotHoldIsReportedRatherThanThrown() {
        for (String impossible : List.of("99999999999", "1".repeat(25), "-500")) {
            PaymentInstruction instruction = new PaymentInstruction("P-1",
                    LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                    new Account("9000001", "1"), new Agent("9999", "998", ""),
                    List.of(new CreditTransferTransaction("INV-1", "",
                            new Money(new java.math.BigDecimal(impossible), "JPY"),
                            new Agent("9999", "999", ""), Party.named("ヤマダ"),
                            new Account("9876543", "1"), RemittanceInformation.NONE)));

            MappingResult<ZenginFile> result = Iso20022Mapper.create()
                    .toZengin(isoFile(instruction), context().build());

            assertThat(result.output().allData().get(0).amount())
                    .as("%s is written as zero, not truncated into a plausible wrong sum",
                            impossible)
                    .isZero();
            assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                    .as("%s must be reported", impossible)
                    .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.COERCED));
        }
    }

    /**
     * An amount nobody could read reaches the report rather than the file.
     *
     * <p>End to end, from XML a hostile sender could write: thirteen bytes that
     * used to be an {@code OutOfMemoryError}.
     */
    @Test
    void anAmountTooLargeToRenderIsReportedRatherThanExhaustingMemory() {
        byte[] hostile = new String(io.zengin4j.iso20022.envelope.ZediEnvelopeWriter
                .toByteArray(isoFile(new PaymentInstruction("P-1", LocalDate.of(2026, 9, 30),
                        Party.named("テストシヨウジ"), new Account("9000001", "1"),
                        new Agent("9999", "998", ""),
                        List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                                new Agent("9999", "999", ""), Party.named("ヤマダ"),
                                new Account("9876543", "1"), RemittanceInformation.NONE))))),
                java.nio.charset.StandardCharsets.UTF_8)
                .replace(">1</InstdAmt>", ">1e2000000000</InstdAmt>")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                io.zengin4j.iso20022.envelope.ZediEnvelopeReader.read(hostile),
                context().build());

        assertThat(result.output().allData().get(0).amount()).isZero();
        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("too large to represent"));
    }

    /** And under the default threshold, that stops the conversion. */
    @Test
    void anImpossibleAmountStopsTheConversionByDefault() {
        PaymentInstruction instruction = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "",
                        new Money(new java.math.BigDecimal("99999999999"), "JPY"),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("9876543", "1"), RemittanceInformation.NONE)));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                Iso20022Mapper.create().toZengin(isoFile(instruction),
                        MappingContext.builder("9900000001", REFERENCE)
                                .targetFormat(fixtures().descriptor())
                                .build())))
                .isInstanceOf(MappingFailedException.class);
    }

    @Test
    void theLargestRepresentableAmountIsStillWritten() {
        PaymentInstruction instruction = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "",
                        Money.yen(9_999_999_999L),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("9876543", "1"), RemittanceInformation.NONE)));

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(instruction), context().build());

        assertThat(result.output().allData().get(0).amount()).isEqualTo(9_999_999_999L);
    }

    /** A record whose データ区分 names no record kind this format has. */
    private static byte[] unknownRecordKind() {
        byte[] record = new byte[120];
        java.util.Arrays.fill(record, (byte) '0');
        record[0] = '5';
        return record;
    }

    // ------------------------------------------------------------- reporting

    /**
     * A record the reader could not parse does not quietly vanish.
     *
     * <p>Lenient mode surfaces it as data rather than failing the read, and the
     * conversion has nothing to map it to — so without this it would be absent
     * from the message with nothing anywhere to say a payment had gone missing.
     */
    @Test
    void recordsTheReaderCouldNotParseAreReportedRatherThanDroppedSilently() {
        ZenginFile lenient = ZenginReaders.readFile(
                new ByteArrayInputStream(SyntheticRecords.file(List.of(
                        headerWith(Map.of()), fixtures().data(), unknownRecordKind(),
                        fixtures().trailer(1, 150_000L), fixtures().end()),
                        SeparatorStyle.CRLF, false)),
                io.zengin4j.core.codec.ReaderOptions.builder()
                        .allowUnverifiedFormats(true)
                        .mode(io.zengin4j.core.codec.ParseMode.LENIENT)
                        .build());

        assertThat(lenient.batches().get(0).malformed())
                .as("the fixture must actually produce a malformed record")
                .isNotEmpty();

        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(lenient, context().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("read as malformed", "may be a payment"));
    }

    /** And under the default threshold, an unreadable record stops the conversion. */
    @Test
    void anUnreadableRecordStopsTheConversionByDefault() {
        ZenginFile lenient = ZenginReaders.readFile(
                new ByteArrayInputStream(SyntheticRecords.file(List.of(
                        headerWith(Map.of()), unknownRecordKind(),
                        fixtures().trailer(0, 0L), fixtures().end()),
                        SeparatorStyle.CRLF, false)),
                io.zengin4j.core.codec.ReaderOptions.builder()
                        .allowUnverifiedFormats(true)
                        .mode(io.zengin4j.core.codec.ParseMode.LENIENT)
                        .build());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                Iso20022Mapper.create().toIso(lenient,
                        MappingContext.builder("9900000001", REFERENCE)
                                .targetFormat(fixtures().descriptor())
                                .build())))
                .isInstanceOf(MappingFailedException.class);
    }

    @Test
    void aFileThatReadsCleanlyReportsNoUnreadableRecords() {
        MappingResult<ZediFile> result = Iso20022Mapper.create()
                .toIso(fileWithHeader(headerWith(Map.of())), context().build());

        assertThat(result.loss().entries())
                .noneSatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("read as malformed"));
    }

    /**
     * One message names one initiating party, and a file may hold batches that
     * disagree.
     */
    @Test
    void batchesThatNameDifferentOriginatorsAreReported() {
        ZenginFile twoOriginators = read(SyntheticRecords.file(List.of(
                headerWith(Map.of("originatorName", "ﾃｽﾄｼﾖｳｼﾞ")),
                fixtures().data(), fixtures().trailer(1, 150_000L),
                headerWith(Map.of("originatorName", "ﾍﾞﾂｶｲｼﾔ")),
                fixtures().data(), fixtures().trailer(1, 150_000L),
                fixtures().end()), SeparatorStyle.CRLF, false));

        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(twoOriginators, context().build());

        assertThat(result.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("2 different originators"));
        assertThat(result.output().onlyMessage().body()
                .at("CstmrCdtTrfInitn").orElseThrow().childrenNamed("PmtInf")).hasSize(2);
    }

    /** Dropping the reference is reported once, with a count, not once per payment. */
    @Test
    void droppingTheReferenceIsReportedOnceForTheWholeFile() {
        MappingResult<ZediFile> result = Iso20022Mapper.create().toIso(
                read(SyntheticRecords.file(List.of(
                        headerWith(Map.of()), fixtures().data(), fixtures().data(),
                        fixtures().data(), fixtures().trailer(3, 450_000L), fixtures().end()),
                        SeparatorStyle.CRLF, false)),
                context().endToEndPolicy(EndToEndIdPolicy.DROP).build());

        assertThat(result.loss().entries().stream()
                .filter(entry -> entry.explanationEn().contains("EndToEndIdPolicy.DROP"))
                .toList())
                .hasSize(1)
                .allSatisfy(entry -> assertThat(entry.explanationEn()).contains("3 payments"));
    }

    // ------------------------------------------------------------ identifiers

    /**
     * An identifier that will not fit is dropped, never shortened.
     *
     * <p>ISO 20022 gives an account number thirty-four characters and a member
     * id thirty-five; the Zengin fields are seven, four and three. Each of
     * these threw an untyped {@code IllegalArgumentException} out of the
     * encoder before this test existed — a whole file lost to one payment, in
     * an exception outside this module's vocabulary.
     *
     * <p>Half an account number is a different account, and a file carrying one
     * looks perfectly valid, so the field is emptied rather than cut.
     */
    @Test
    void anIdentifierTooLongForItsFieldIsDroppedRatherThanShortened() {
        PaymentInstruction longAccount = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("98765432109876543210", "1"),
                        RemittanceInformation.NONE)));

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(longAccount), context().build());

        assertThat(((io.zengin4j.core.model.generated.SougouFurikomiData)
                result.output().allData().get(0)).accountNumber())
                .as("not written, so the numeric field holds its padding — which is zeros, "
                        + "and is why this has to be CRITICAL rather than merely reported")
                .isEqualTo("0000000")
                .doesNotContain("9876");
        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("An identifier is not shortened", "zeros, and is no safer"));
    }

    /**
     * And under the default threshold, that stops the conversion — which is the
     * only reason a zeroed identifier is survivable.
     */
    @Test
    void anIdentifierThatCannotBeWrittenStopsTheConversionByDefault() {
        PaymentInstruction longAccount = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("98765432109876543210", "1"),
                        RemittanceInformation.NONE)));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                Iso20022Mapper.create().toZengin(isoFile(longAccount),
                        MappingContext.builder("9900000001", REFERENCE)
                                .targetFormat(fixtures().descriptor())
                                .build())))
                .isInstanceOf(MappingFailedException.class);
    }

    /** A member id of an unexpected shape reaches the bank-code field the same way. */
    @Test
    void aMemberIdTooLongForTheBankCodeFieldIsDropped() {
        PaymentInstruction longMember = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                        new Agent("SOMEBANKXXXXX", "", ""), Party.named("ヤマダ"),
                        new Account("9876543", "1"), RemittanceInformation.NONE)));

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(longMember), context().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL)).isNotEmpty();
        assertThat(((io.zengin4j.core.model.generated.SougouFurikomiData)
                result.output().allData().get(0)).beneficiaryBankCode()).isEqualTo("0000");
    }

    /**
     * 預金種目 is one character and {@code Tp/Prtry} is thirty-five.
     *
     * <p>A sender writing {@code SAVINGS} is entirely plausible, and it used to
     * throw out of the encoder.
     */
    @Test
    void aProprietaryAccountTypeThatIsNotOneCharacterIsAssumedOrdinary() {
        PaymentInstruction wordy = new PaymentInstruction("P-1",
                LocalDate.of(2026, 9, 30), Party.named("テストシヨウジ"),
                new Account("9000001", "1"), new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("9876543", "SAVINGS"), RemittanceInformation.NONE)));

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(wordy), context().build());

        assertThat(((io.zengin4j.core.model.generated.SougouFurikomiData)
                result.output().allData().get(0)).accountType()).isEqualTo("1");
        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("SAVINGS"));
    }

    // ------------------------------------------------------- self-consistency

    /**
     * A document that contradicts its own header, on the way down.
     *
     * <p>The mirror of the trailer cross-check the upward leg has done since
     * this epic started, and it was missing: {@code NbOfTxs} and
     * {@code CtrlSum} are computed on write and were never compared on read.
     * A {@code pain.001} that disagrees with itself is exactly as suspect as a
     * Zengin file whose trailer does.
     */
    @Test
    void aGroupHeaderThatDisagreesWithItsOwnPaymentsIsReported() {
        byte[] inconsistent = withGroupHeaderClaiming("<NbOfTxs>5</NbOfTxs>"
                + "<CtrlSum>999999</CtrlSum>");

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(io.zengin4j.iso20022.envelope.ZediEnvelopeReader.read(inconsistent),
                        context().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("GrpHdr/NbOfTxs"))
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("GrpHdr/CtrlSum"));
        assertThat(result.output().allData())
                .as("the payments are what is converted, because they are what the money is")
                .hasSize(1);
    }

    @Test
    void aGroupHeaderThatAgreesWithItsPaymentsIsNotReported() {
        byte[] consistent = withGroupHeaderClaiming("<NbOfTxs>1</NbOfTxs><CtrlSum>1</CtrlSum>");

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(io.zengin4j.iso20022.envelope.ZediEnvelopeReader.read(consistent),
                        context().build());

        assertThat(result.loss().entries())
                .noneSatisfy(entry -> assertThat(entry.explanationEn()).contains("GrpHdr/NbOfTxs"))
                .noneSatisfy(entry -> assertThat(entry.explanationEn()).contains("GrpHdr/CtrlSum"));
    }

    @Test
    void aGroupHeaderWithUnreadableTotalsIsNotMistakenForADisagreement() {
        byte[] rubbish = withGroupHeaderClaiming(
                "<NbOfTxs>lots</NbOfTxs><CtrlSum>a fortune</CtrlSum>");

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(io.zengin4j.iso20022.envelope.ZediEnvelopeReader.read(rubbish),
                        context().build());

        assertThat(result.loss().entries())
                .noneSatisfy(entry -> assertThat(entry.explanationEn()).contains("GrpHdr/NbOfTxs"));
    }

    /** One payment of ¥1, with whatever the caller wants in the group header. */
    private static byte[] withGroupHeaderClaiming(String claimed) {
        PaymentInstruction one = new PaymentInstruction("P-1", LocalDate.of(2026, 9, 30),
                Party.named("テストシヨウジ"), new Account("9000001", "1"),
                new Agent("9999", "998", ""),
                List.of(new CreditTransferTransaction("INV-1", "", Money.yen(1),
                        new Agent("9999", "999", ""), Party.named("ヤマダ"),
                        new Account("9876543", "1"), RemittanceInformation.NONE)));

        String xml = new String(io.zengin4j.iso20022.envelope.ZediEnvelopeWriter
                .toByteArray(isoFile(one)), java.nio.charset.StandardCharsets.UTF_8);
        return xml.replaceFirst("<NbOfTxs>[^<]*</NbOfTxs>\\s*<CtrlSum>[^<]*</CtrlSum>", claimed)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ JSON

    /**
     * The hand-written JSON is checked by a real parser, quotes and all
     * (ADR-0022).
     */
    @Test
    void theJsonReportParsesAndKeepsWhatItSaid() throws Exception {
        MappingLossReport report = MappingLossReport.of(new LossReport(List.of(
                LossEntry.of(LossKind.TRUNCATED, LossSeverity.MATERIAL,
                                "a \"quoted\" name\nwith a newline\tand a tab",
                                "back\\slash", "explanation", "説明")
                        .at("Cdtr/Nm", "data.beneficiaryName"),
                LossEntry.of(LossKind.DROPPED, LossSeverity.INFORMATIONAL,
                        "x", "", "no location", "位置なし"))));

        JsonNode parsed = new ObjectMapper().readTree(report.toJson());

        assertThat(parsed.get("lossless").asBoolean()).isFalse();
        assertThat(parsed.get("entries")).hasSize(2);
        assertThat(parsed.get("entries").get(0).get("originalValue").asText())
                .isEqualTo("a \"quoted\" name\nwith a newline\tand a tab");
        assertThat(parsed.get("entries").get(0).get("resultingValue").asText())
                .isEqualTo("back\\slash");
        assertThat(parsed.get("entries").get(0).get("sourcePath").asText()).isEqualTo("Cdtr/Nm");
        assertThat(parsed.get("entries").get(1).has("sourcePath"))
                .as("an entry with nowhere to point does not claim a location")
                .isFalse();
    }

    @Test
    void aLosslessJsonReportParsesToAnEmptyArray() throws Exception {
        JsonNode parsed = new ObjectMapper().readTree(MappingLossReport.lossless().toJson());

        assertThat(parsed.get("lossless").asBoolean()).isTrue();
        assertThat(parsed.get("entries")).isEmpty();
    }

    @Test
    void aControlCharacterInAValueIsEscapedRatherThanEmitted() throws Exception {
        String withControl = "beforeafter";
        MappingLossReport report = MappingLossReport.of(new LossReport(List.of(
                LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL,
                        withControl, "", "en", "ja"))));

        JsonNode parsed = new ObjectMapper().readTree(report.toJson());

        assertThat(parsed.get("entries").get(0).get("originalValue").asText())
                .isEqualTo(withControl);
    }

    @Test
    void anEnglishLocaleGetsTheEnglishText() {
        MappingLossReport report = MappingLossReport.of(new LossReport(List.of(
                LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL, "a", "b", "en", "ja"))));

        assertThat(report.toText(Locale.ENGLISH)).contains("en").doesNotContain("ja");
        assertThat(report.toText(Locale.JAPANESE)).contains("ja").doesNotContain("en");
        assertThat(report).hasToString("MappingLossReport[1 entry]");
    }

    // ------------------------------------------------------------- identity

    @Test
    void messagesAndFilesCompareByWhatTheyContain() {
        ZediMessage message = ZediMessage.of(
                new BusinessApplicationHeader("9900000001", "9999", "M1",
                        MessageId.PAIN_001_001_03, OffsetDateTime.parse("2026-09-01T00:00:00Z")),
                XmlElement.element("Document").namespace(MessageId.PAIN_001_001_03.namespace())
                        .child(XmlElement.element("CstmrCdtTrfInitn")).build());
        ZediMessage same = ZediMessage.of(
                new BusinessApplicationHeader("9900000001", "9999", "M1",
                        MessageId.PAIN_001_001_03, OffsetDateTime.parse("2026-09-01T00:00:00Z")),
                XmlElement.element("Document").namespace(MessageId.PAIN_001_001_03.namespace())
                        .child(XmlElement.element("CstmrCdtTrfInitn")).build());

        assertThat(message).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(message).isNotEqualTo("not a message");
        assertThat(message.toString()).contains("pain.001.001.03");

        assertThat(ZediFile.of(message)).isEqualTo(ZediFile.of(same))
                .hasSameHashCodeAs(ZediFile.of(same));
        assertThat(ZediFile.of(message)).isNotEqualTo(ZediFile.of(List.of()));
        assertThat(ZediFile.of(message)).hasToString("ZediFile[1 message]");
    }

    @Test
    void aMessageIdKnowsItsNamespaceAndItsFamily() {
        assertThat(MessageId.PAIN_001_001_03.namespace())
                .isEqualTo("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");
        assertThat(MessageId.PAIN_001_001_03.family()).isEqualTo("pain.001");
        assertThat(new MessageId("head").family()).isEqualTo("head");
        assertThat(MessageId.PAIN_001_001_03).hasToString("pain.001.001.03");

        assertThat(MessageId.fromNamespace(MessageId.PAIN_001_001_03.namespace()))
                .contains(MessageId.PAIN_001_001_03);
        assertThat(MessageId.fromNamespace("http://example.com/not-iso")).isEmpty();
        assertThat(MessageId.fromNamespace(null)).isEmpty();
        assertThat(MessageId.fromNamespace("urn:iso:std:iso:20022:tech:xsd:")).isEmpty();
    }

    @Test
    void aHeaderReadsBackWhatItWrote() {
        BusinessApplicationHeader original = new BusinessApplicationHeader(
                "9900000001", "9999", "M1", MessageId.PAIN_001_001_03,
                OffsetDateTime.parse("2026-09-01T00:00:00Z"));

        assertThat(BusinessApplicationHeader.from(original.toXml())).isEqualTo(original);
    }

    @Test
    void aHeaderMissingEverythingReadsAsBlanksRatherThanFailing() {
        BusinessApplicationHeader read = BusinessApplicationHeader.from(
                XmlElement.element(BusinessApplicationHeader.ROOT).build());

        assertThat(read.from()).isEmpty();
        assertThat(read.to()).isEmpty();
        assertThat(read.businessMessageIdentifier()).isEmpty();
        assertThat(read.creationDate().getYear()).isEqualTo(1970);
    }

    @Test
    void aHeaderWithAnUnreadableDateDoesNotRefuseTheWholeFile() {
        BusinessApplicationHeader read = BusinessApplicationHeader.from(
                XmlElement.element(BusinessApplicationHeader.ROOT)
                        .textChild("CreDt", "not a date")
                        .build());

        assertThat(read.creationDate().getYear()).isEqualTo(1970);
    }

    @Test
    void aFinancialInstitutionIdentifiedByBicIsStillReadable() {
        BusinessApplicationHeader read = BusinessApplicationHeader.from(
                XmlElement.element(BusinessApplicationHeader.ROOT)
                        .child(XmlElement.element("Fr")
                                .child(XmlElement.element("FIId")
                                        .child(XmlElement.element("FinInstnId")
                                                .textChild("BICFI", "TESTJPJT"))))
                        .build());

        assertThat(read.from()).isEqualTo("TESTJPJT");
    }
}
