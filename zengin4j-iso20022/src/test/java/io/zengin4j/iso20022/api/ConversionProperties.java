package io.zengin4j.iso20022.api;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.kana.TruncationPolicy;
import io.zengin4j.core.kana.UnmappableCharacterPolicy;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediEnvelopeReader;
import io.zengin4j.iso20022.envelope.ZediEnvelopeWriter;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.xml.XmlElement;
import io.zengin4j.iso20022.xml.XmlParser;
import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.testkit.ZenginGenerator;
import org.junit.jupiter.api.Test;

/// Properties that must hold for any file, not just the ones somebody wrote.
///
/// The envelope reader is fuzzed; the mapper is not, because a fuzzer over
/// arbitrary bytes would spend its time on files the reader rejects before the
/// mapper ever sees them. Generated *valid* files are the useful shape
/// here — hundreds of them, with names, amounts and codes drawn at random —
/// because every defect this epic's audit turned up was a legitimate input that
/// no fixture happened to contain.
///
/// The seed is fixed. A property test that flakes on a schedule teaches the
/// team to re-run CI, which is worse than not having it.
class ConversionProperties {

    private static final long SEED = 0x150_2002L;
    private static final int FILES = 200;
    private static final FormatId FORMAT = FormatId.of("sougou-furikomi");
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

    private static ZenginFile generated(long seed, int payments) {
        FormatFixtures fixtures = FormatFixtures.forFormat(FORMAT);
        byte[] bytes = ZenginGenerator.builder()
                .format(FORMAT)
                .seed(seed)
                .payments(payments)
                .separator(SeparatorStyle.CRLF)
                .build()
                .generate();
        return ZenginReaders.readFile(new ByteArrayInputStream(bytes), fixtures.readerOptions());
    }

    private static MappingContext.Builder context() {
        return MappingContext.builder("9900000001", REFERENCE)
                .targetFormat(FormatFixtures.forFormat(FORMAT).descriptor());
    }

    /// The generator produces varied files, or the properties below prove
    /// nothing.
    ///
    /// A property test over two hundred identical inputs is a slow way of
    /// running one test, and it looks exactly like a thorough one.
    @Test
    void theGeneratedFilesAreActuallyDifferent() {
        Random random = new Random(SEED);
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        java.util.Set<Long> amounts = new java.util.LinkedHashSet<>();

        for (int i = 0; i < 50; i++) {
            ZenginFile file = generated(random.nextLong(), 1 + random.nextInt(4));
            file.allData().forEach(record -> {
                amounts.add(record.amount());
                names.add(((io.zengin4j.core.model.generated.SougouFurikomiData) record)
                        .beneficiaryName().trim());
            });
        }

        assertThat(names).as("beneficiary names must vary").hasSizeGreaterThan(3);
        assertThat(amounts).as("amounts must vary").hasSizeGreaterThan(20);
    }

    /// Converting a readable file never fails in an undeclared way.
    ///
    /// The same contract the reader has: a legitimate input either converts
    /// or raises a [ZenginException]. An `IllegalArgumentException`
    /// from inside the encoder — which is how three separate defects in this
    /// epic presented — is a defect, not an answer.
    @Test
    void convertingAnyGeneratedFileEitherWorksOrFailsInTheDeclaredWay() {
        Random random = new Random(SEED);

        for (int i = 0; i < FILES; i++) {
            long seed = random.nextLong();
            int payments = 1 + random.nextInt(6);
            ZenginFile file = generated(seed, payments);
            MappingContext context = context()
                    .truncation(random.nextBoolean()
                            ? TruncationPolicy.TRUNCATE_SAFE : TruncationPolicy.REJECT_IF_TOO_LONG)
                    .unmappable(random.nextBoolean()
                            ? UnmappableCharacterPolicy.DROP : UnmappableCharacterPolicy.REJECT)
                    .endToEndPolicy(EndToEndIdPolicy.values()[random.nextInt(3)])
                    .acceptAnyLoss()
                    .build();

            try {
                MappingResult<ZediFile> result = Iso20022Mapper.create().toIso(file, context);
                assertThat(result.output().messages()).hasSize(1);
            } catch (ZenginException declared) {
                continue;
            } catch (RuntimeException undeclared) {
                throw new AssertionError("seed " + seed + ", " + payments
                        + " payments: converting raised " + undeclared.getClass().getName()
                        + " rather than a ZenginException", undeclared);
            }
        }
    }

    /// Everything the mapper produces is well-formed XML that reads back
    /// unchanged.
    ///
    /// The writer is hand-written (ADR-0031). Its safety net is that a real
    /// parser reads back everything it emits, and this runs that over generated
    /// content rather than over one fixture's names.
    @Test
    void everyProducedMessageParsesBackToTheTreeItWasWrittenFrom() {
        Random random = new Random(SEED);

        for (int i = 0; i < FILES; i++) {
            long seed = random.nextLong();
            ZenginFile file = generated(seed, 1 + random.nextInt(4));

            ZediFile produced = Iso20022Mapper.create()
                    .toIso(file, context().acceptAnyLoss().build())
                    .output();
            byte[] bytes = ZediEnvelopeWriter.toByteArray(produced);

            assertThat(ZediEnvelopeReader.read(bytes))
                    .as("seed %d: the envelope did not survive a round trip through a real parser",
                            seed)
                    .isEqualTo(produced);

            XmlElement body = produced.onlyMessage().body();
            assertThat(XmlParser.parse(
                    io.zengin4j.iso20022.xml.XmlSerializer.toBytes(body)))
                    .as("seed %d: the body did not parse back to itself", seed)
                    .isEqualTo(body);
            assertThat(produced.onlyMessage().messageId()).contains(MessageId.PAIN_001_001_03);
        }
    }

    /// A round trip never loses a payment.
    ///
    /// Values change — that is the whole point of the loss report — but the
    /// *number* of payments is structural, and a conversion that quietly
    /// returned fewer would be moving less money than it was asked to.
    @Test
    void aRoundTripKeepsEveryPayment() {
        Random random = new Random(SEED);

        for (int i = 0; i < FILES; i++) {
            long seed = random.nextLong();
            int payments = 1 + random.nextInt(6);
            ZenginFile file = generated(seed, payments);

            RoundTripResult round = Iso20022Mapper.create().roundTrip(file,
                    context().truncation(TruncationPolicy.TRUNCATE_SAFE)
                            .unmappable(UnmappableCharacterPolicy.DROP)
                            .build());

            assertThat(round.result().allData())
                    .as("seed %d: %d payments went out", seed, payments)
                    .hasSize(payments);
            assertThat(round.original().allData()).hasSize(payments);
        }
    }

    /// Every amount survives a round trip exactly.
    ///
    /// Names are transliterated and references are squeezed, but a sum of
    /// money either arrives intact or the conversion says it could not. There is
    /// no acceptable middle.
    @Test
    void everyAmountSurvivesARoundTripOrIsReportedCritical() {
        Random random = new Random(SEED);

        for (int i = 0; i < FILES; i++) {
            long seed = random.nextLong();
            ZenginFile file = generated(seed, 1 + random.nextInt(5));

            RoundTripResult round = Iso20022Mapper.create().roundTrip(file,
                    context().truncation(TruncationPolicy.TRUNCATE_SAFE)
                            .unmappable(UnmappableCharacterPolicy.DROP)
                            .build());

            boolean anythingCritical = round.loss().hasAtLeast(LossSeverity.CRITICAL);
            for (int p = 0; p < round.original().allData().size(); p++) {
                long before = round.original().allData().get(p).amount();
                long after = round.result().allData().get(p).amount();
                assertThat(before == after || anythingCritical)
                        .as("seed %d payment %d: %d became %d with nothing critical reported",
                                seed, p, before, after)
                        .isTrue();
            }
        }
    }
}
