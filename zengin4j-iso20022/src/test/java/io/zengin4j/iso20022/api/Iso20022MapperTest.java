package io.zengin4j.iso20022.api;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.generated.SougouFurikomiData;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediEnvelopeReader;
import io.zengin4j.iso20022.envelope.ZediEnvelopeWriter;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.loss.MappingLossReport;
import io.zengin4j.iso20022.pain001.Account;
import io.zengin4j.iso20022.pain001.Agent;
import io.zengin4j.iso20022.pain001.CreditTransferTransaction;
import io.zengin4j.iso20022.pain001.EdiAttachment;
import io.zengin4j.iso20022.pain001.GroupHeader;
import io.zengin4j.iso20022.pain001.Money;
import io.zengin4j.iso20022.pain001.Pain001Document;
import io.zengin4j.iso20022.pain001.PaymentInstruction;
import io.zengin4j.iso20022.pain001.Party;
import io.zengin4j.iso20022.pain001.RemittanceInformation;
import io.zengin4j.iso20022.xml.XmlElement;
import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import org.junit.jupiter.api.Test;

/// What the conversion does, and what it refuses to do quietly.
///
/// Organised around the requirements rather than the code, because the
/// requirements are the interesting part: the loss report is inescapable
/// (R-I14), a critical loss stops the conversion, the context is mandatory on
/// the way down (R-I20), and the version is pinned (R-I3).
class Iso20022MapperTest {

    private static final FormatId FORMAT = FormatId.of("sougou-furikomi");
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

    private static FormatFixtures fixtures() {
        return FormatFixtures.forFormat(FORMAT);
    }

    private static MappingContext.Builder context() {
        return MappingContext.builder("9900000001", REFERENCE)
                .targetFormat(fixtures().descriptor());
    }

    private static ZenginFile read(byte[] bytes) {
        return ZenginReaders.readFile(new ByteArrayInputStream(bytes), fixtures().readerOptions());
    }

    private static ZenginFile fileWith(byte[]... data) {
        FormatFixtures fixtures = fixtures();
        long total = 0;
        for (byte[] ignored : data) {
            total += 150_000L;
        }
        java.util.List<byte[]> records = new java.util.ArrayList<>();
        records.add(fixtures.header());
        records.addAll(Arrays.asList(data));
        records.add(fixtures.trailer(data.length, total));
        records.add(fixtures.end());
        return read(SyntheticRecords.file(records, SeparatorStyle.CRLF, false));
    }

    private static ZediFile isoFile(CreditTransferTransaction... transactions) {
        var document = new Pain001Document(
                new GroupHeader("MSG-1", OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                        new Party("テストシヨウジ", "9900000001")),
                List.of(new PaymentInstruction("MSG-1-1", LocalDate.of(2026, 9, 30),
                        Party.named("テストシヨウジ"), new Account("9000001", "1"),
                        new Agent("9999", "998", "テストギンコウ"),
                        List.of(transactions))));
        return ZediFile.of(io.zengin4j.iso20022.envelope.ZediMessage.of(
                new io.zengin4j.iso20022.envelope.BusinessApplicationHeader(
                        "9900000001", "9999", "M1", MessageId.PAIN_001_001_03,
                        OffsetDateTime.parse("2026-09-01T00:00:00Z")),
                document.toXml()));
    }

    /// The first payment, as the format-shaped record it is.
    private static SougouFurikomiData onlyPayment(ZenginFile file) {
        return (SougouFurikomiData) file.allData().get(0);
    }

    private static CreditTransferTransaction payment(String name, Money amount, String reference) {
        return new CreditTransferTransaction(reference, "", amount,
                new Agent("9999", "999", "テストギンコウ"), Party.named(name),
                new Account("9876543", "1"), RemittanceInformation.NONE);
    }

    // ------------------------------------------------------------------ R-I14

    /// There is no way to get converted output without its loss report.
    ///
    /// Checked structurally rather than by reading the code: a method added
    /// later that returns a `ZediFile` or a `ZenginFile` on its own
    /// would defeat the requirement, and it is exactly the sort of convenience
    /// somebody adds in good faith.
    @Test
    void noPublicMethodHandsBackOutputWithoutItsLossReport() {
        List<String> offenders = Arrays.stream(Iso20022Mapper.class.getMethods())
                .filter(method -> method.getDeclaringClass() == Iso20022Mapper.class)
                .filter(Iso20022MapperTest::returnsBareOutput)
                .map(Method::getName)
                .toList();

        assertThat(offenders)
                .as("R-I14: conversion always returns output and a loss report together. A "
                        + "method returning the artefact alone lets a caller forget that every "
                        + "conversion loses something.")
                .isEmpty();
    }

    private static boolean returnsBareOutput(Method method) {
        Class<?> returned = method.getReturnType();
        return returned == ZediFile.class || returned == ZenginFile.class
                || returned == Pain001Document.class;
    }

    @Test
    void everyConversionCarriesItsReport() {
        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(fileWith(fixtures().data()), context().build());

        assertThat(result.loss()).isNotNull();
        assertThat(result.isLossless()).isFalse();
        assertThat(result.hasAtLeast(LossSeverity.INFORMATIONAL)).isTrue();
        assertThat(result.toString()).contains("ZediFile");
    }

    // ------------------------------------------------- refusing on real damage

    /// A critical loss stops the conversion by default.
    ///
    /// The trailer here says one payment and the batch holds two, which means
    /// neither number can be trusted. Returning that quietly and hoping somebody
    /// reads the report is not good enough for a file that moves money.
    @Test
    void aTrailerThatDisagreesWithItsOwnPaymentsStopsTheConversion() {
        FormatFixtures fixtures = fixtures();
        ZenginFile wrong = read(SyntheticRecords.file(List.of(
                fixtures.header(), fixtures.data(), fixtures.data(),
                fixtures.trailer(1, 150_000L), fixtures.end()),
                SeparatorStyle.CRLF, false));

        assertThatExceptionOfType(MappingFailedException.class)
                .isThrownBy(() -> Iso20022Mapper.create().toIso(wrong, context().build()))
                .satisfies(refused -> {
                    assertThat(refused.threshold()).isEqualTo(LossSeverity.CRITICAL);
                    assertThat(refused.loss().atLeast(LossSeverity.CRITICAL)).isNotEmpty();
                    assertThat(refused.getMessage()).contains("failOnSeverity");
                });
    }

    @Test
    void acceptingAnyLossLetsItThroughWithTheReportUnchanged() {
        FormatFixtures fixtures = fixtures();
        ZenginFile wrong = read(SyntheticRecords.file(List.of(
                fixtures.header(), fixtures.data(), fixtures.data(),
                fixtures.trailer(1, 150_000L), fixtures.end()),
                SeparatorStyle.CRLF, false));

        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(wrong, context().acceptAnyLoss().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .as("accepting the loss does not make it go away")
                .isNotEmpty();
        assertThat(result.output().messages()).hasSize(1);
    }

    @Test
    void theThresholdCanBeLoweredToRefuseOnLessThanCritical() {
        assertThatExceptionOfType(MappingFailedException.class)
                .isThrownBy(() -> Iso20022Mapper.create().toIso(fileWith(fixtures().data()),
                        context().failOnSeverity(LossSeverity.INFORMATIONAL).build()))
                .withMessageContaining("INFORMATIONAL");
    }

    // ------------------------------------------------------------ the way down

    @Test
    void aCurrencyThatIsNotYenIsCriticalRatherThanConverted() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ　タロウ",
                        new Money(BigDecimal.valueOf(1000), "EUR"), "INV-1")),
                context().acceptAnyLoss().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("EUR"));
        assertThat(result.output().allData().get(0).amount()).isEqualTo(1000L);
    }

    @Test
    void aFractionalAmountIsCriticalRatherThanRounded() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ　タロウ",
                        new Money(new BigDecimal("1000.50"), "JPY"), "INV-1")),
                context().acceptAnyLoss().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.COERCED));
        assertThat(result.output().allData().get(0).amount())
                .as("discarded, not rounded up")
                .isEqualTo(1000L);
    }

    @Test
    void theYearIsDroppedComingBackAndTheReportSaysSo() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ　タロウ", Money.yen(150_000), "INV-1")),
                context().build());

        assertThat(result.loss().entries())
                .anySatisfy(entry -> {
                    assertThat(entry.kind()).isEqualTo(LossKind.DROPPED);
                    assertThat(entry.explanationEn()).contains("2026");
                });
    }

    /// A name in kanji has no automatic reading, so it is refused rather than
    /// guessed.
    @Test
    void aKanjiNameIsRefusedRatherThanGuessedAt() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("山田太郎", Money.yen(150_000), "INV-1")),
                context().acceptAnyLoss().build());

        assertThat(result.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("cannot be written into beneficiaryName"));
        assertThat(onlyPayment(result.output()).beneficiaryName().trim()).isEmpty();
    }

    @Test
    void aNameTooLongForItsFieldIsRefusedUnlessTruncationIsAskedFor() {
        String tooLong = "ヤマダ".repeat(12);

        MappingResult<ZenginFile> refusing = Iso20022Mapper.create().toZengin(
                isoFile(payment(tooLong, Money.yen(150_000), "INV-1")),
                context().acceptAnyLoss().build());
        assertThat(onlyPayment(refusing.output()).beneficiaryName().trim()).isEmpty();

        MappingResult<ZenginFile> truncating = Iso20022Mapper.create().toZengin(
                isoFile(payment(tooLong, Money.yen(150_000), "INV-1")),
                context().truncation(TruncationPolicy.TRUNCATE_SAFE)
                        .unmappable(UnmappableCharacterPolicy.DROP)
                        .acceptAnyLoss().build());
        assertThat(onlyPayment(truncating.output()).beneficiaryName().trim()).isNotEmpty();
        assertThat(truncating.loss().entries())
                .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.TRUNCATED));
    }

    // ----------------------------------------------------------- EndToEndId

    @Test
    void aReferenceTooLongForItsFieldIsTruncatedAndReportedCritical() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ", Money.yen(1), "INVOICE-2026-000123456789")),
                context().acceptAnyLoss().build());

        assertThat(result.loss().atLeast(LossSeverity.CRITICAL))
                .anySatisfy(entry -> assertThat(entry.kind()).isEqualTo(LossKind.TRUNCATED));
        assertThat(onlyPayment(result.output()).customerCode1().trim())
                .isEqualTo("INVOICE-20");
    }

    @Test
    void theReferenceCanBeSentToTheOtherCustomerCode() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ", Money.yen(1), "INV-1")),
                context().endToEndPolicy(EndToEndIdPolicy.CUSTOMER_CODE_2)
                        .acceptAnyLoss().build());

        assertThat(onlyPayment(result.output()).customerCode2().trim()).isEqualTo("INV-1");
    }

    @Test
    void theReferenceCanBeRefusedCarriageAltogether() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ", Money.yen(1), "INV-1")),
                context().endToEndPolicy(EndToEndIdPolicy.DROP).acceptAnyLoss().build());

        assertThat(onlyPayment(result.output()).customerCode1().trim()).isEmpty();
        assertThat(result.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("DROP"));
    }

    @Test
    void aReferenceThatWasNeverSuppliedIsNotReportedAsLost() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ", Money.yen(1),
                        CreditTransferTransaction.NOT_PROVIDED)),
                context().build());

        assertThat(onlyPayment(result.output()).customerCode1().trim()).isEmpty();
        assertThat(result.loss().entries())
                .noneSatisfy(entry -> assertThat(entry.explanationEn()).contains("EndToEndId"));
    }

    // -------------------------------------------------------------- attachment

    @Test
    void aBase64AttachmentIsNotSqueezedIntoTwentyBytes() {
        var withEdi = new CreditTransferTransaction(
                "INV-1", "", Money.yen(1), new Agent("9999", "999", ""),
                Party.named("ヤマダ"), new Account("9876543", "1"),
                RemittanceInformation.of(EdiAttachment.of(
                        "<TranInf>a long structured payload</TranInf>"
                                .getBytes(StandardCharsets.UTF_8))));

        MappingResult<ZenginFile> result = Iso20022Mapper.create()
                .toZengin(isoFile(withEdi), context().acceptAnyLoss().build());

        assertThat(result.loss().entries())
                .anySatisfy(entry -> assertThat(entry.explanationEn())
                        .contains("base64 encoding is not a shorter version"));
        assertThat(onlyPayment(result.output()).customerCode2().trim()).isEmpty();
    }

    /// 識別表示 = Y means the two customer codes are one C(20) 金融EDI情報 field
    /// rather than two codes (OQ-8).
    @Test
    void anEdiOverlayIsReadAsOneFieldRatherThanTwoCustomerCodes() {
        FormatFixtures fixtures = fixtures();
        byte[] withOverlay = SyntheticRecords.encode(
                fixtures.descriptor().record(io.zengin4j.core.format.RecordKind.DATA),
                io.zengin4j.core.charset.ZenginCharset.MS932,
                java.util.Map.of(
                        "beneficiaryBankCode", "9999",
                        "beneficiaryBranchCode", "999",
                        "accountType", "1",
                        "accountNumber", "9876543",
                        "beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ",
                        "amount", "150000",
                        "customerCode1", "EDI0000001",
                        "customerCode2", "0000000002",
                        "identification", "Y"));

        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(fileWith(withOverlay), context().build());

        XmlElement transaction = result.output().onlyMessage().body()
                .at("CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf").orElseThrow();
        assertThat(transaction.textAt("RmtInf/Ustrd"))
                .as("the twenty bytes are one EDI field, not a customer code")
                .contains("EDI00000010000000002");
    }

    // ------------------------------------------------------------------ R-I17

    @Test
    void aDryRunProducesOnlyTheReport() {
        MappingLossReport report = Iso20022Mapper.create()
                .dryRun(fileWith(fixtures().data()), context().build());

        assertThat(report.entries()).isNotEmpty();
        assertThat(report.toText()).contains("TRANSLITERATED");
    }

    /// A dry run shows the loss even when the loss would stop a conversion.
    @Test
    void aDryRunDoesNotRefuse() {
        FormatFixtures fixtures = fixtures();
        ZenginFile wrong = read(SyntheticRecords.file(List.of(
                fixtures.header(), fixtures.data(), fixtures.data(),
                fixtures.trailer(1, 150_000L), fixtures.end()),
                SeparatorStyle.CRLF, false));

        MappingLossReport report = Iso20022Mapper.create().dryRun(wrong, context().build());

        assertThat(report.hasAtLeast(LossSeverity.CRITICAL)).isTrue();
    }

    // ------------------------------------------------------------------ R-I18

    @Test
    void aRoundTripAccumulatesTheLossFromBothLegs() {
        RoundTripResult round =
                Iso20022Mapper.create().roundTrip(fileWith(fixtures().data()), context().build());

        assertThat(round.loss().entries())
                .as("the widening on the way out and the narrowing on the way back")
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("widened"))
                .anySatisfy(entry -> assertThat(entry.explanationEn()).contains("narrowed"));
        assertThat(round.original()).isNotNull();
        assertThat(round.intermediate().messages()).hasSize(1);
        assertThat(round.isLossless()).isFalse();
    }

    /// And it is not bijective, which is the point.
    ///
    /// The fixture carries a branch name and a 振込指定区分 that the mapping
    /// declares dropped, so the file that comes back is not the file that went
    /// out — and every difference has a line in the report.
    @Test
    void aRoundTripIsNotByteIdenticalAndSaysWhy() {
        RoundTripResult round =
                Iso20022Mapper.create().roundTrip(fileWith(fixtures().data()), context().build());

        assertThat(round.isByteIdentical()).isFalse();
        assertThat(round.result().allData()).hasSize(1);
        assertThat(round.loss().bySeverity(LossSeverity.INFORMATIONAL)).isNotEmpty();
    }

    @Test
    void aRoundTripDoesNotRefuseOnCriticalLoss() {
        MappingResult<ZenginFile> ignored = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ", new Money(BigDecimal.valueOf(1), "EUR"), "INV-1")),
                context().acceptAnyLoss().build());
        assertThat(ignored.loss().hasAtLeast(LossSeverity.CRITICAL)).isTrue();

        RoundTripResult round = Iso20022Mapper.create()
                .roundTrip(fileWith(fixtures().data()), context().build());

        assertThat(round.result()).isNotNull();
    }

    // ------------------------------------------------------------------ R-I20

    @Test
    void theInverseLegRefusesWithoutATargetFormat() {
        var noFormat = MappingContext.builder("9900000001", REFERENCE).build();

        assertThatIllegalStateException()
                .isThrownBy(() -> Iso20022Mapper.create()
                        .toZengin(isoFile(payment("ﾔﾏﾀﾞ", Money.yen(1), "X")), noFormat))
                .withMessageContaining("R-I20");
    }

    @Test
    void theOriginatorCodeComesFromTheContextRatherThanFromTheXml() {
        MappingResult<ZenginFile> result = Iso20022Mapper.create().toZengin(
                isoFile(payment("ヤマダ", Money.yen(1), "INV-1")),
                MappingContext.builder("9911111111", REFERENCE)
                        .targetFormat(fixtures().descriptor())
                        .acceptAnyLoss()
                        .build());

        assertThat(result.output().batches().get(0).header().originatorCode())
                .as("the XML said 9900000001; the context is the authority")
                .isEqualTo("9911111111");
    }

    // ------------------------------------------------------------------- R-I3

    @Test
    void aDifferentVersionOfTheSameMessageIsRefused() {
        byte[] newer = ZediEnvelopeWriter
                .toByteArray(isoFile(payment("ﾔﾏﾀﾞ", Money.yen(1), "X")));
        byte[] retagged = new String(newer, StandardCharsets.UTF_8)
                .replace("pain.001.001.03", "pain.001.001.09")
                .getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Iso20022Mapper.create()
                        .toZengin(ZediEnvelopeReader.read(retagged), context().build()))
                .withMessageContaining("same message in a different version");
    }

    @Test
    void anInboundMessageSaysWhichEpicItBelongsTo() {
        byte[] statusReport = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.03\">\r\n"
                + "  <CstmrPmtStsRpt/>\r\n</Document>\r\n").getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Iso20022Mapper.create()
                        .toZengin(ZediEnvelopeReader.read(statusReport), context().build()))
                .withMessageContaining("Epic 8");
    }

    @Test
    void aBodyWithNoIsoNamespaceIsRefused() {
        byte[] notIso = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Document><CstmrCdtTrfInitn/></Document>\r\n")
                .getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Iso20022Mapper.create()
                        .toZengin(ZediEnvelopeReader.read(notIso), context().build()))
                .withMessageContaining("no ISO 20022 namespace");
    }

    @Test
    void aFormatWithNoMappingSaysWhatIsAvailable() {
        FormatFixtures payroll = FormatFixtures.forFormat(FormatId.of("kyuyo-furikomi"));
        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(payroll.file()), payroll.readerOptions());

        assertThatExceptionOfType(
                io.zengin4j.iso20022.mapping.UnsupportedMappingException.class)
                .isThrownBy(() -> Iso20022Mapper.create().toIso(file, context().build()))
                .withMessageContaining("sougou-furikomi");
    }

    // ----------------------------------------------------------- determinism

    /// The same file converts to the same bytes, twice.
    ///
    /// Only true because `CreDtTm` and `MsgId` default to
    /// something derived from the reference date rather than from the clock. A
    /// conversion that embedded `Instant.now()` would make golden files
    /// impossible and diffs useless.
    @Test
    void convertingTheSameFileTwiceProducesTheSameBytes() {
        ZenginFile file = fileWith(fixtures().data());

        byte[] first = ZediEnvelopeWriter.toByteArray(
                Iso20022Mapper.create().toIso(file, context().build()).output());
        byte[] second = ZediEnvelopeWriter.toByteArray(
                Iso20022Mapper.create().toIso(file, context().build()).output());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void aCreationTimestampCanBeSetWhenARealOneIsWanted() {
        MappingResult<ZediFile> result = Iso20022Mapper.create().toIso(
                fileWith(fixtures().data()),
                context().creationDateTime(OffsetDateTime.parse("2026-09-01T09:30:00+09:00"))
                        .messageId("BATCH-7")
                        .build());

        XmlElement header = result.output().onlyMessage().body()
                .at("CstmrCdtTrfInitn/GrpHdr").orElseThrow();
        assertThat(header.textAt("CreDtTm")).contains("2026-09-01T09:30:00+09:00");
        assertThat(header.textAt("MsgId")).contains("BATCH-7");
    }

    @Test
    void theRecipientDefaultsToTheFilesOwnOriginatingBank() {
        MappingResult<ZediFile> result =
                Iso20022Mapper.create().toIso(fileWith(fixtures().data()), context().build());

        assertThat(result.output().onlyMessage().header().orElseThrow().to())
                .isEqualTo("9999");
    }

    @Test
    void theRecipientCanBeSaidOutright() {
        MappingResult<ZediFile> result = Iso20022Mapper.create()
                .toIso(fileWith(fixtures().data()), context().receiver("ZENGINNET").build());

        assertThat(result.output().onlyMessage().header().orElseThrow().to())
                .isEqualTo("ZENGINNET");
    }

    // ---------------------------------------------------------------- context

    @Test
    void acceptingAnyLossReturnsTheSameContextWhenItAlreadyDoes() {
        MappingContext permissive = context().acceptAnyLoss().build();

        assertThat(permissive.acceptingAnyLoss()).isSameAs(permissive);
        assertThat(context().build().acceptingAnyLoss().failOnSeverity()).isEmpty();
    }

    @Test
    void aContextSaysWhatItIsConfiguredFor() {
        assertThat(context().build().toString())
                .contains("9900000001", "2026-09-01", "CRITICAL");
        assertThat(context().build().messageId()).isEqualTo("9900000001-2026-09-01");
        assertThat(context().build().creationDateTime())
                .isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
        assertThat(context().build().receiver()).isEmpty();
        assertThat(context().build().targetFormat()).isPresent();
    }

    @Test
    void nullsAreRejectedByName() {
        assertThatNullPointerException()
                .isThrownBy(() -> Iso20022Mapper.create().toIso(null, context().build()));
        assertThatNullPointerException()
                .isThrownBy(() -> Iso20022Mapper.create().toIso(fileWith(), null));
        assertThatNullPointerException()
                .isThrownBy(() -> Iso20022Mapper.create().toZengin(null, context().build()));
        assertThatNullPointerException()
                .isThrownBy(() -> Iso20022Mapper.using(null));
        assertThatNullPointerException()
                .isThrownBy(() -> MappingContext.builder(null, REFERENCE));
        assertThatNullPointerException()
                .isThrownBy(() -> new MappingResult<>(null, MappingLossReport.lossless()));
    }

    @Test
    void aLossReportRendersInBothLanguagesAndAsJson() {
        MappingLossReport report = Iso20022Mapper.create()
                .dryRun(fileWith(fixtures().data()), context().build());

        assertThat(report.toText()).contains("TRANSLITERATED");
        assertThat(report.toText(java.util.Locale.JAPANESE)).contains("全角");
        assertThat(report.toJson()).contains("\"lossless\": false", "\"kind\": \"");
        assertThat(MappingLossReport.lossless().toText(java.util.Locale.JAPANESE))
                .isEqualTo("損失なし\n");
        assertThat(MappingLossReport.lossless().toJson()).contains("\"entries\": []");
        assertThat(MappingLossReport.lossless())
                .hasToString("MappingLossReport[0 entries]");
    }

    @Test
    void reportsCombineAcrossLegs() {
        var one = MappingLossReport.of(new io.zengin4j.core.loss.LossReport(
                List.of(LossEntry.of(LossKind.DROPPED, LossSeverity.MATERIAL,
                        "a", "b", "en", "ja"))));

        assertThat(one.and(MappingLossReport.lossless())).isSameAs(one);
        assertThat(MappingLossReport.lossless().and(one).entries()).hasSize(1);
        assertThat(one.and(one).entries()).hasSize(2);
        assertThat(one.toLossReport().entries()).hasSize(1);
    }
}
