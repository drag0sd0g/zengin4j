import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.api.EndToEndIdPolicy;
import io.zengin4j.iso20022.api.Iso20022Mapper;
import io.zengin4j.iso20022.api.MappingContext;
import io.zengin4j.iso20022.api.MappingFailedException;
import io.zengin4j.iso20022.api.MappingResult;
import io.zengin4j.iso20022.api.RoundTripResult;
import io.zengin4j.iso20022.envelope.ZediEnvelopeReader;
import io.zengin4j.iso20022.envelope.ZediEnvelopeWriter;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.envelope.ZediMessage;
import io.zengin4j.testkit.FormatFixtures;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * UC-3 — an edge adapter between a fixed-length file and ISO 20022 (§15).
 *
 * <p>Run it with:
 *
 * <pre>
 * ./gradlew runExamples
 * </pre>
 *
 * <p>The point of this example is not that conversion works. It is that
 * conversion <em>costs</em> something, that the cost is returned rather than
 * logged, and that the library will not quietly hand you a payment file whose
 * money could go somewhere else.
 *
 * <p><strong>Every identifier here is invented</strong> (R-L1).
 */
public final class ConvertToIso20022 {

    /**
     * Fixed rather than {@code LocalDate.now()}: it supplies the year that
     * {@code MMDD} fields lack, and it is the basis of {@code MsgId} and
     * {@code CreDtTm}, so pinning it makes the output reproducible.
     */
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

    private ConvertToIso20022() {
    }

    public static void main(String[] args) {
        ZenginFile file = aFileToConvert();

        upwards(file);
        whatItRefuses(file);
        theRoundTrip(file);
        theEnvelopeQuirk(file);
    }

    /** A synthetic 総合振込 file, as read from disk. */
    private static ZenginFile aFileToConvert() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FormatId.of("sougou-furikomi"));
        return ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures.file(3, SeparatorStyle.CRLF, false)),
                fixtures.readerOptions());
    }

    private static FormatDescriptor descriptor() {
        return FormatRegistry.defaults().byId(FormatId.of("sougou-furikomi")).orElseThrow();
    }

    private static MappingContext.Builder context() {
        return MappingContext.builder("9900000001", REFERENCE).targetFormat(descriptor());
    }

    // --------------------------------------------------------------- upwards

    /** Zengin to {@code pain.001}, and what it cost. */
    private static void upwards(ZenginFile file) {
        System.out.println("== converting to pain.001 ==");

        MappingResult<ZediFile> converted =
                Iso20022Mapper.create().toIso(file, context().build());

        String xml = new String(
                ZediEnvelopeWriter.toByteArray(converted.output()), StandardCharsets.UTF_8);
        System.out.println(firstLines(xml, 6));
        System.out.println("  ... " + xml.lines().count() + " lines in total");

        System.out.println();
        System.out.println("  what it cost:");
        for (LossEntry entry : converted.loss().atLeast(LossSeverity.MATERIAL)) {
            System.out.println("    " + entry.toLine());
        }
        System.out.println("    (and " + converted.loss().bySeverity(LossSeverity.INFORMATIONAL)
                .size() + " informational entries)");
        System.out.println();
    }

    // ------------------------------------------------------------- refusing

    /**
     * The default is to stop rather than to hand back something misroutable.
     *
     * <p>Here the reference is thirty-five characters and 顧客コード1 holds ten,
     * so carrying it truncates — and a truncated reconciliation reference looks
     * usable and matches the wrong payment.
     */
    private static void whatItRefuses(ZenginFile file) {
        System.out.println("== what it refuses ==");

        ZediFile message = Iso20022Mapper.create()
                .toIso(file, context().acceptAnyLoss().build())
                .output();
        byte[] withLongReference = new String(ZediEnvelopeWriter.toByteArray(message),
                StandardCharsets.UTF_8)
                .replaceAll("<EndToEndId>[^<]*</EndToEndId>",
                        "<EndToEndId>INVOICE-2026-000123456789012345</EndToEndId>")
                .getBytes(StandardCharsets.UTF_8);

        try {
            Iso20022Mapper.create()
                    .toZengin(ZediEnvelopeReader.read(withLongReference), context().build());
            System.out.println("  (nothing was refused)");
        } catch (MappingFailedException refused) {
            System.out.println("  refused, and said why:");
            refused.loss().atLeast(LossSeverity.CRITICAL)
                    .forEach(entry -> System.out.println("    " + entry.toLine()));
        }

        System.out.println();
        System.out.println("  the same conversion, with the loss accepted deliberately:");
        MappingResult<ZenginFile> anyway = Iso20022Mapper.create().toZengin(
                ZediEnvelopeReader.read(withLongReference),
                context().endToEndPolicy(EndToEndIdPolicy.DROP).acceptAnyLoss().build());
        System.out.println("    " + anyway.output().allData().size() + " payments written, "
                + anyway.loss().entries().size() + " loss entries recorded");
        System.out.println();
    }

    // ----------------------------------------------------------- round trip

    /** R-I18: the honest demonstration that this is not a bijection. */
    private static void theRoundTrip(ZenginFile file) {
        System.out.println("== there and back ==");

        RoundTripResult round =
                Iso20022Mapper.create().roundTrip(file, context().build());

        System.out.println("  byte-identical: " + round.isByteIdentical());
        System.out.println("  payments in:  " + round.original().allData().size());
        System.out.println("  payments out: " + round.result().allData().size());
        System.out.println("  what both legs lost, in total: "
                + round.loss().entries().size() + " entries, "
                + round.loss().atLeast(LossSeverity.MATERIAL).size() + " material or worse");
        System.out.println();
        System.out.println("  the ones that change what a person would read:");
        round.loss().atLeast(LossSeverity.MATERIAL)
                .forEach(entry -> System.out.println("    " + entry.toLine()));
        System.out.println();
    }

    // -------------------------------------------------------- the envelope

    /**
     * The quirk that makes generic ISO 20022 tooling useless here.
     *
     * <p>A ZEDI file carries the business application header and the message
     * body as two separate XML documents, concatenated. It therefore contains
     * two XML declarations and is not a well-formed document at all.
     */
    private static void theEnvelopeQuirk(ZenginFile file) {
        System.out.println("== the envelope ==");

        byte[] bytes = ZediEnvelopeWriter.toByteArray(Iso20022Mapper.create()
                .toIso(file, context().acceptAnyLoss().build())
                .output());

        long declarations = new String(bytes, StandardCharsets.UTF_8).lines()
                .filter(line -> line.startsWith("<?xml"))
                .count();
        System.out.println("  XML declarations in this one file: " + declarations);
        System.out.println("  ... which is why a standard XML parser fails on it");

        ZediFile reread = ZediEnvelopeReader.read(bytes);
        for (ZediMessage message : reread.messages()) {
            System.out.println("  header " + message.header().orElseThrow()
                    .businessMessageIdentifier()
                    + " introduces " + message.messageId().orElseThrow());
        }
        System.out.println("  written back unchanged: "
                + java.util.Arrays.equals(ZediEnvelopeWriter.toByteArray(reread), bytes));
    }

    private static String firstLines(String text, int count) {
        return text.lines().limit(count).map(line -> "  " + line)
                .reduce((a, b) -> a + System.lineSeparator() + b).orElse("");
    }
}
