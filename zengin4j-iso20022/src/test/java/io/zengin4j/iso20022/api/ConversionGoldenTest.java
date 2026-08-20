package io.zengin4j.iso20022.api;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.envelope.ZediEnvelopeWriter;
import io.zengin4j.testkit.FormatFixtures;
import org.junit.jupiter.api.Test;

/// A committed conversion, so that a change to it is a diff somebody reads.
///
/// The mapping is thirty-five rows of judgement, none of them verified. A
/// coverage number cannot tell you that `ClrSysId/Cd` moved or that a
/// loss line changed severity; a diff of the actual XML can, and that is the
/// only review this mapping is going to get until somebody obtains the profile
/// documentation.
///
/// To update after an intentional change:
///
/// ```
/// ./gradlew :zengin4j-iso20022:test --tests '*ConversionGoldenTest*' -Pgolden.regenerate
/// ```
///
/// Then read the diff before committing it. A golden updated without being
/// read is worse than no golden at all.
class ConversionGoldenTest {

    private static final String EXPECTED_XML = "/conformance/sougou-furikomi.pain001.xml";
    private static final String EXPECTED_LOSS = "/conformance/sougou-furikomi.loss.txt";

    private static final FormatId FORMAT = FormatId.of("sougou-furikomi");

    /// Fixed, and the reason the golden is possible at all: `CreDtTm` and
    /// `MsgId` default to values derived from this date rather than from
    /// the clock.
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

    private static MappingResult<io.zengin4j.iso20022.envelope.ZediFile> convert() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FORMAT);
        ZenginFile file = ZenginReaders.readFile(
                new java.io.ByteArrayInputStream(fixtures.file(3,
                        io.zengin4j.core.model.SeparatorStyle.CRLF, false)),
                fixtures.readerOptions());

        return Iso20022Mapper.create().toIso(file,
                MappingContext.builder("9900000001", REFERENCE)
                        .targetFormat(fixtures.descriptor())
                        .acceptAnyLoss()
                        .build());
    }

    @Test
    void theConvertedMessageIsWhatWasCommitted() {
        var produced = new String(
                ZediEnvelopeWriter.toByteArray(convert().output()), StandardCharsets.UTF_8);

        if (regenerating()) {
            write(EXPECTED_XML, produced);
            return;
        }
        assertThat(normalise(produced))
                .as("the converted message differs from the committed one. Read the diff: this "
                        + "is the only review an unverified mapping gets.")
                .isEqualTo(normalise(new String(resource(EXPECTED_XML), StandardCharsets.UTF_8)));
    }

    @Test
    void theLossReportIsWhatWasCommitted() {
        String produced = convert().loss().toText();

        if (regenerating()) {
            write(EXPECTED_LOSS, produced);
            return;
        }
        assertThat(normalise(produced))
                .as("the loss report differs from the committed one. A conversion that started "
                        + "losing something new, or stopped saying so, shows up here.")
                .isEqualTo(normalise(new String(resource(EXPECTED_LOSS), StandardCharsets.UTF_8)));
    }

    /// The same input converts to the same bytes however often it is run.
    @Test
    void theConversionIsReproducible() {
        assertThat(ZediEnvelopeWriter.toByteArray(convert().output()))
                .isEqualTo(ZediEnvelopeWriter.toByteArray(convert().output()));
    }

    // ---------------------------------------------------------------- fixtures

    /// Line endings vary by check-out; content does not.
    private static String normalise(String content) {
        return content.replace("\r\n", "\n");
    }

    private static boolean regenerating() {
        return Boolean.getBoolean("zengin4j.golden.regenerate");
    }

    private static byte[] resource(String name) {
        try (InputStream stream = ConversionGoldenTest.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new AssertionError("missing golden resource " + name
                        + "; regenerate with -Pgolden.regenerate");
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Writes back into the source tree, not the build output, so the diff is reviewable.
    private static void write(String name, String content) {
        var path = Path.of("src/test/resources").resolve(name.substring(1));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
