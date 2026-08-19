package io.zengin4j.iso20022.api;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.generated.SougouFurikomiData;
import io.zengin4j.iso20022.envelope.BusinessApplicationHeader;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.envelope.ZediMessage;
import io.zengin4j.iso20022.pain001.Account;
import io.zengin4j.iso20022.pain001.Agent;
import io.zengin4j.iso20022.pain001.CreditTransferTransaction;
import io.zengin4j.iso20022.pain001.GroupHeader;
import io.zengin4j.iso20022.pain001.Money;
import io.zengin4j.iso20022.pain001.Pain001Document;
import io.zengin4j.iso20022.pain001.PaymentInstruction;
import io.zengin4j.iso20022.pain001.Party;
import io.zengin4j.iso20022.pain001.RemittanceInformation;
import io.zengin4j.iso20022.xml.XmlParser;
import io.zengin4j.iso20022.xml.XmlSerializer;
import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.validation.ZenginValidator;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// **Anything this mapper writes, this library accepts.**
///
/// The same promise `WrittenFilesValidateTest` makes for the encoder,
/// one layer up — and it was already broken when this was written. The inverse
/// leg produced files carrying a 振込指定区分 of 0, which is what the field's
/// numeric default is and what several institutions require, and which the
/// bundled code list does not contain. A warning rather than an error, but
/// nothing said so and nobody would have found it except by converting a file
/// and validating the result. So: convert a file and validate the result.
///
/// The bar is *no errors*, not *no findings*. A warning can be
/// correct — the 振込指定区分 one is — and the loss report is where the mapper
/// explains itself. An error means the file should not be sent, and a mapper
/// that produces one is broken.
///
/// It lives here because this is the only module that can see both sides:
/// the mapper writes, and `validation` judges.
class ConvertedFilesValidateTest {

    private static final FormatId FORMAT = FormatId.of("sougou-furikomi");
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

    /// Names covering every conversion the engine performs on the way down.
    private static final List<String> NAMES = List.of(
            "ヤマダ　タロウ",
            "ガクブチ　ジロウ",
            "キヤノン",
            "サツポロ",
            "パピプペポ",
            "ＡＢＣ　ＤＥＦ",
            "abc def",
            "ヴアイオリン",
            "ヲダ　タロウ",
            "タロウタロウタロウタロウタロウタロウ",
            "ヨーコ",
            "山田太郎");

    private static FormatFixtures fixtures() {
        return FormatFixtures.forFormat(FORMAT);
    }

    private static MappingContext.Builder context() {
        return MappingContext.builder("9900000001", REFERENCE)
                .targetFormat(fixtures().descriptor())
                .acceptAnyLoss();
    }

    private static ZediFile isoFile(String name, Money amount, String reference) {
        Pain001Document document = new Pain001Document(
                new GroupHeader("MSG-1", OffsetDateTime.parse("2026-09-01T00:00:00Z"),
                        new Party("テストシヨウジ", "9900000001")),
                List.of(new PaymentInstruction("MSG-1-1", LocalDate.of(2026, 9, 30),
                        Party.named("テストシヨウジ"), new Account("9000001", "1"),
                        new Agent("9999", "998", "テストギンコウ"),
                        List.of(new CreditTransferTransaction(reference, "", amount,
                                new Agent("9999", "999", "テストギンコウ"), Party.named(name),
                                new Account("9876543", "1"),
                                RemittanceInformation.of("REF-1"))))));

        return ZediFile.of(ZediMessage.of(
                new BusinessApplicationHeader("9900000001", "9999", "M1",
                        MessageId.PAIN_001_001_03, OffsetDateTime.parse("2026-09-01T00:00:00Z")),
                document.toXml()));
    }

    private static List<String> errorsIn(ZenginFile file) {
        byte[] bytes = ZenginWriters.toByteArray(file, WriterOptions.defaults());
        ZenginFile reread = ZenginReaders.readFile(
                new ByteArrayInputStream(bytes), fixtures().readerOptions());
        ValidationReport report = ZenginValidator.defaults().validate(reread);

        List<String> errors = new ArrayList<>();
        report.findings(Severity.ERROR)
                .forEach(finding -> errors.add(finding.ruleId() + ": " + finding.messageEn()));
        return errors;
    }

    /// Every name, every truncation policy: either refused, or written and
    /// accepted.
    @ParameterizedTest
    @EnumSource(TruncationPolicy.class)
    void whateverTheInverseLegWritesValidatesWithoutErrors(TruncationPolicy truncation) {
        for (String name : NAMES) {
            MappingContext context = context()
                    .truncation(truncation)
                    .unmappable(UnmappableCharacterPolicy.DROP)
                    .build();

            MappingResult<ZenginFile> converted;
            try {
                converted = Iso20022Mapper.create()
                        .toZengin(isoFile(name, Money.yen(150_000), "INV-1"), context);
            } catch (RuntimeException refused) {
                continue;
            }

            assertThat(errorsIn(converted.output()))
                    .as("converting '%s' under %s produced a file the validator rejects",
                            name, truncation)
                    .isEmpty();
        }
    }

    /// Including the amounts that cannot be represented at all.
    @Test
    void anAmountThatCannotBeRepresentedStillProducesAValidFile() {
        for (Money amount : List.of(
                Money.yen(0),
                Money.yen(999_999_999L),
                new Money(new BigDecimal("1000.99"), "JPY"),
                new Money(BigDecimal.valueOf(1000), "EUR"))) {
            MappingResult<ZenginFile> converted = Iso20022Mapper.create()
                    .toZengin(isoFile("ヤマダ　タロウ", amount, "INV-1"), context().build());

            assertThat(errorsIn(converted.output()))
                    .as("%s produced a file the validator rejects", amount)
                    .isEmpty();
        }
    }

    /// And every EndToEndId policy.
    @ParameterizedTest
    @EnumSource(EndToEndIdPolicy.class)
    void everyReferencePolicyProducesAValidFile(EndToEndIdPolicy policy) {
        MappingResult<ZenginFile> converted = Iso20022Mapper.create().toZengin(
                isoFile("ヤマダ　タロウ", Money.yen(1), "INVOICE-2026-000123456789"),
                context().endToEndPolicy(policy).build());

        assertThat(errorsIn(converted.output()))
                .as("%s produced a file the validator rejects", policy)
                .isEmpty();
    }

    /// Under every policy, both 顧客コード survive the round trip or are reported.
    ///
    /// This is the test that was missing. Under `CUSTOMER_CODE_2` the
    /// outbound leg sent 顧客コード2 to `EndToEndId` and wrote no
    /// remittance information at all, so 顧客コード1 disappeared from the message
    /// with no loss entry — a payment reference silently gone, in the one policy
    /// branch nothing exercised.
    ///
    /// The rule is not "everything round-trips". It is that a value either
    /// comes back or the report names it, which is the promise this module makes
    /// about every field it touches.
    @ParameterizedTest
    @EnumSource(EndToEndIdPolicy.class)
    void bothCustomerCodesEitherSurviveTheRoundTripOrAreReported(EndToEndIdPolicy policy) {
        ZenginFile original = aFileWithBothCustomerCodes();
        MappingContext context = context().endToEndPolicy(policy).build();

        RoundTripResult round = Iso20022Mapper.create().roundTrip(original, context);

        SougouFurikomiData before = (SougouFurikomiData) original.allData().get(0);
        SougouFurikomiData after = (SougouFurikomiData) round.result().allData().get(0);
        String report = round.loss().toText();

        for (String code : List.of(before.customerCode1().trim(), before.customerCode2().trim())) {
            boolean survived = after.customerCode1().trim().equals(code)
                    || after.customerCode2().trim().equals(code);
            boolean reported = report.contains(code)
                    || report.contains("EndToEndIdPolicy.DROP");

            assertThat(survived || reported)
                    .as("under %s, '%s' neither came back nor appears in the report:%n%s",
                            policy, code, report)
                    .isTrue();
        }
    }

    /// A payment with a distinguishable value in each 顧客コード.
    private static ZenginFile aFileWithBothCustomerCodes() {
        FormatFixtures fixtures = fixtures();
        byte[] data = io.zengin4j.testkit.SyntheticRecords.encode(
                fixtures.descriptor().record(io.zengin4j.core.format.RecordKind.DATA),
                io.zengin4j.core.charset.ZenginCharset.MS932,
                java.util.Map.of(
                        "beneficiaryBankCode", "9999", "beneficiaryBranchCode", "999",
                        "accountType", "1", "accountNumber", "9876543",
                        "beneficiaryName", "ﾔﾏﾀﾞ", "amount", "150000",
                        "customerCode1", "CODE-ONE-A", "customerCode2", "CODE-TWO-B"));

        return ZenginReaders.readFile(new ByteArrayInputStream(
                io.zengin4j.testkit.SyntheticRecords.file(
                        List.of(fixtures.header(), data, fixtures.trailer(1, 150_000L),
                                fixtures.end()),
                        SeparatorStyle.CRLF, false)),
                fixtures.readerOptions());
    }

    /// A round trip lands somewhere the validator accepts.
    ///
    /// Not byte-identical — it cannot be, and `RoundTripResult` says so
    /// — but a file a bank would take.
    @Test
    void aRoundTrippedFileIsStillSubmittable() {
        ZenginFile original = ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures().file(5, SeparatorStyle.CRLF, false)),
                fixtures().readerOptions());

        RoundTripResult round = Iso20022Mapper.create().roundTrip(original, context().build());

        assertThat(errorsIn(round.result()))
                .as("a file that went to ISO 20022 and back is not one a bank would take")
                .isEmpty();
        assertThat(round.result().allData()).hasSize(5);
    }

    /// And the message it passed through is well-formed XML with the right
    /// namespace.
    ///
    /// The XSD check is opt-in because the schemas are not redistributed
    /// here; this is the part that can run on every build.
    @Test
    void theInterveningMessageIsWellFormedAndCorrectlyNamespaced() {
        ZenginFile original = ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures().file(5, SeparatorStyle.CRLF, false)),
                fixtures().readerOptions());

        RoundTripResult round = Iso20022Mapper.create().roundTrip(original, context().build());

        for (ZediMessage message : round.intermediate().messages()) {
            assertThat(XmlParser.parse(XmlSerializer.toBytes(message.body())))
                    .isEqualTo(message.body());
            assertThat(message.messageId()).contains(MessageId.PAIN_001_001_03);
            assertThat(message.header()).isPresent();
        }
    }
}
