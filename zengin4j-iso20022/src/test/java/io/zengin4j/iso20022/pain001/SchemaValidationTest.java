package io.zengin4j.iso20022.pain001;

import module java.base;
import module java.xml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.api.Iso20022Mapper;
import io.zengin4j.iso20022.api.MappingContext;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediMessage;
import io.zengin4j.iso20022.xml.XmlSerializer;
import io.zengin4j.testkit.FormatFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// R-I21 — the generated XML, checked against the official schemas.
///
/// **Skipped unless you supply the schemas.** The ISO 20022
/// message definitions are published by ISO 20022 under terms this repository
/// does not redistribute under, so they are not committed here (ADR-0031).
/// Download `pain.001.001.03.xsd` and `head.001.001.01.xsd` from
/// [iso20022.org](https://www.iso20022.org/iso-20022-message-definitions)
/// into a directory and run:
///
/// ```
/// ./gradlew :zengin4j-iso20022:validateAgainstXsd -Pxsd.dir=/path/to/schemas
/// ```
///
/// Deliberately not part of {@code check}. A gate that passes silently when
/// its input is missing is worse than an absent one, because it reads like
/// coverage nobody has — so this skips loudly instead, and the test that always
/// runs is {@link Pain001ModelTest}, which sends every element through a real
/// parser.
///
/// The schema factory is configured to resolve nothing outside the directory
/// it was given. A validator that fetched an import over the network would put
/// a socket in the middle of a payment conversion.
@Tag("xsd")
class SchemaValidationTest {

    private static Path schemaDirectory() {
        String configured = System.getProperty("zengin4j.xsd.dir", "");
        return configured.isBlank() ? null : Path.of(configured);
    }

    private static Path schema(String name) {
        Path directory = schemaDirectory();
        assumeTrue(directory != null,
                "no schema directory: run with -Pxsd.dir=/path/to/schemas. "
                        + "The ISO 20022 schemas are not redistributed here (ADR-0031).");
        Path file = directory.resolve(name);
        assumeTrue(Files.isReadable(file), () -> name + " is not in " + directory);
        return file;
    }

    private static byte[] convertedBody() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FormatId.of("sougou-furikomi"));
        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures.file(3,
                        io.zengin4j.core.model.SeparatorStyle.CRLF, false)),
                fixtures.readerOptions());

        ZediMessage message = Iso20022Mapper.create().toIso(file,
                        MappingContext.builder("9900000001", LocalDate.of(2026, 9, 1))
                                .targetFormat(fixtures.descriptor())
                                .acceptAnyLoss()
                                .build())
                .output().onlyMessage();
        return XmlSerializer.toBytes(message.body());
    }

    private static byte[] convertedHeader() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FormatId.of("sougou-furikomi"));
        ZenginFile file = ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures.file()), fixtures.readerOptions());

        ZediMessage message = Iso20022Mapper.create().toIso(file,
                        MappingContext.builder("9900000001", LocalDate.of(2026, 9, 1))
                                .targetFormat(fixtures.descriptor())
                                .acceptAnyLoss()
                                .build())
                .output().onlyMessage();
        return XmlSerializer.toBytes(message.header().orElseThrow().toXml());
    }

    private static List<String> validate(Path schemaFile, byte[] document) throws Exception {
        SchemaFactory factory =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");

        Schema schema = factory.newSchema(schemaFile.toFile());
        Validator validator = schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");

        List<String> problems = new ArrayList<>();
        validator.setErrorHandler(collectingInto(problems));
        validator.validate(new StreamSource(new ByteArrayInputStream(document)));
        return problems;
    }

    private static ErrorHandler collectingInto(List<String> problems) {
        return new ErrorHandler() {
            @Override
            public void warning(SAXParseException problem) {
                problems.add("warning line " + problem.getLineNumber() + ": "
                        + problem.getMessage());
            }

            @Override
            public void error(SAXParseException problem) {
                problems.add("error line " + problem.getLineNumber() + ": "
                        + problem.getMessage());
            }

            @Override
            public void fatalError(SAXParseException problem) {
                problems.add("fatal line " + problem.getLineNumber() + ": "
                        + problem.getMessage());
            }
        };
    }

    @Test
    void theConvertedMessageValidatesAgainstPain001() throws Exception {
        Path schema = schema("pain.001.001.03.xsd");

        assertThat(validate(schema, convertedBody()))
                .as("the generated pain.001 does not satisfy the official schema")
                .isEmpty();
    }

    @Test
    void theBusinessApplicationHeaderValidatesAgainstHead001() throws Exception {
        Path schema = schema("head.001.001.01.xsd");

        assertThat(validate(schema, convertedHeader()))
                .as("the generated head.001 does not satisfy the official schema")
                .isEmpty();
    }

    /// The pin is a claim about the schema, so the schema is what checks it.
    ///
    /// If somebody supplies `pain.001.001.09` under the pinned name,
    /// the documents will not validate — and that failure is the point rather
    /// than a nuisance.
    @Test
    void thePinnedNamespaceIsWhatTheDocumentDeclares() {
        assumeTrue(schemaDirectory() != null, "no schema directory");

        assertThat(new String(convertedBody(), java.nio.charset.StandardCharsets.UTF_8))
                .contains(MessageId.PAIN_001_001_03.namespace());
    }
}
