package io.zengin4j.iso20022.envelope;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.zengin4j.iso20022.xml.MalformedXmlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The concatenation quirk, and what it takes to survive it.
///
/// A ZEDI file contains several XML declarations and is therefore not a single
/// well-formed document. Every claim this library makes about being able to read
/// one rests on the splitting being right, so this is where that gets tested: the
/// happy path, the ambiguous path, and the path where a `<?xml` sequence
/// appears somewhere it does not belong.
class ZediEnvelopeTest {

    private static final String DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n";

    private static String header(String id) {
        return DECL + "<AppHdr xmlns=\"" + MessageId.HEAD_001_001_01.namespace() + "\">\r\n"
                + "  <Fr><OrgId><Id><OrgId><Othr><Id>9900000001</Id></Othr></OrgId></Id></OrgId></Fr>\r\n"
                + "  <To><OrgId><Id><OrgId><Othr><Id>9999</Id></Othr></OrgId></Id></OrgId></To>\r\n"
                + "  <BizMsgIdr>" + id + "</BizMsgIdr>\r\n"
                + "  <MsgDefIdr>pain.001.001.03</MsgDefIdr>\r\n"
                + "  <CreDt>2026-09-01T00:00:00Z</CreDt>\r\n"
                + "</AppHdr>\r\n";
    }

    private static String body(String messageId) {
        return DECL + "<Document xmlns=\"" + MessageId.PAIN_001_001_03.namespace() + "\">\r\n"
                + "  <CstmrCdtTrfInitn>\r\n"
                + "    <GrpHdr><MsgId>" + messageId + "</MsgId></GrpHdr>\r\n"
                + "  </CstmrCdtTrfInitn>\r\n"
                + "</Document>\r\n";
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ split

    @Test
    void aHeaderAndABodyReadAsOneMessage() {
        ZediFile file = ZediEnvelopeReader.read(bytes(header("M1") + body("MSG-1")));

        assertThat(file.messages()).hasSize(1);
        ZediMessage message = file.onlyMessage();
        assertThat(message.header()).isPresent();
        assertThat(message.header().orElseThrow().businessMessageIdentifier()).isEqualTo("M1");
        assertThat(message.messageId()).contains(MessageId.PAIN_001_001_03);
        assertThat(message.body().textAt("CstmrCdtTrfInitn/GrpHdr/MsgId")).contains("MSG-1");
    }

    /// R-I7 — several groups in one file.
    @Test
    void severalGroupsInOneFileReadAsSeveralMessages() {
        ZediFile file = ZediEnvelopeReader.read(bytes(
                header("M1") + body("MSG-1") + header("M2") + body("MSG-2")
                        + header("M3") + body("MSG-3")));

        assertThat(file.messages()).hasSize(3);
        assertThat(file.messages().stream()
                .map(message -> message.body().textAt("CstmrCdtTrfInitn/GrpHdr/MsgId").orElseThrow()))
                .containsExactly("MSG-1", "MSG-2", "MSG-3");
    }

    /// A body with no header is a message, not a header with no body.
    ///
    /// Pairing by root element rather than by position. Most fixtures — and
    /// some senders — produce a bare `pain.001`, and reading that as a
    /// malformed pair would refuse a file that is perfectly usable.
    @Test
    void aBareBodyReadsAsAMessageWithoutAHeader() {
        ZediFile file = ZediEnvelopeReader.read(bytes(body("MSG-1")));

        assertThat(file.onlyMessage().header()).isEmpty();
        assertThat(file.onlyMessage().messageId()).contains(MessageId.PAIN_001_001_03);
    }

    @Test
    void aHeaderWithNoBodyIsRefused() {
        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> ZediEnvelopeReader.read(bytes(header("M1"))))
                .withMessageContaining("not followed by a message body");
    }

    @Test
    void twoHeadersInARowAreRefused() {
        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> ZediEnvelopeReader.read(bytes(header("M1") + header("M2"))))
                .withMessageContaining("not followed by a message body");
    }

    @Test
    void aFileWithNoDeclarationIsNotAZediFile() {
        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> ZediEnvelopeReader.read(
                        "a fixed-length record, not XML".getBytes(StandardCharsets.UTF_8)))
                .withMessageContaining("ZenginReaders");
    }

    @Test
    void boundariesAreEveryDeclarationOffset() {
        byte[] file = bytes(header("M1") + body("MSG-1"));

        List<Integer> boundaries = ZediEnvelopeReader.findDeclarationBoundaries(file);

        assertThat(boundaries).hasSize(2);
        assertThat(boundaries.get(0)).isZero();
        assertThat(boundaries.get(1)).isEqualTo(header("M1").getBytes(StandardCharsets.UTF_8).length);
    }

    // -------------------------------------------------------- false boundaries

    /// R-I8, and the part of it the requirement does not cover.
    ///
    /// Character content cannot hold a literal `<`, and the base64
    /// alphabet does not include one — so a payload cannot contain
    /// `<?xml` however large it gets. A comment can, though, and the
    /// requirement's argument does not reach that case. The split is therefore
    /// checked rather than assumed: a false boundary produces a segment that is
    /// not well-formed, and the diagnostic says so.
    @Test
    void aDeclarationInsideACommentIsCaughtRatherThanSilentlySplitting() {
        String withComment = DECL + "<Document xmlns=\""
                + MessageId.PAIN_001_001_03.namespace() + "\">\r\n"
                + "  <!-- <?xml version=\"1.0\"?> -->\r\n"
                + "  <CstmrCdtTrfInitn/>\r\n"
                + "</Document>\r\n";

        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> ZediEnvelopeReader.read(bytes(withComment)))
                .withMessageContaining("comment or CDATA");
    }

    @Test
    void anEscapedDeclarationInTextIsNotABoundary() {
        String escaped = DECL + "<Document xmlns=\""
                + MessageId.PAIN_001_001_03.namespace() + "\">\r\n"
                + "  <CstmrCdtTrfInitn><GrpHdr><MsgId>&lt;?xml here</MsgId></GrpHdr>"
                + "</CstmrCdtTrfInitn>\r\n"
                + "</Document>\r\n";

        ZediFile file = ZediEnvelopeReader.read(bytes(escaped));

        assertThat(file.messages()).hasSize(1);
        assertThat(file.onlyMessage().body().textAt("CstmrCdtTrfInitn/GrpHdr/MsgId"))
                .contains("<?xml here");
    }

    /// The base64 alphabet has no `<`, so a payload cannot fake a boundary.
    @Test
    void aLargeBase64PayloadDoesNotSplitTheDocument() {
        String payload = "PD94bWwgdmVyc2lvbj0iMS4wIj8+".repeat(200);
        String withPayload = DECL + "<Document xmlns=\""
                + MessageId.PAIN_001_001_03.namespace() + "\">\r\n"
                + "  <CstmrCdtTrfInitn><GrpHdr><MsgId>" + payload + "</MsgId></GrpHdr>"
                + "</CstmrCdtTrfInitn>\r\n"
                + "</Document>\r\n";

        ZediFile file = ZediEnvelopeReader.read(bytes(withPayload));

        assertThat(file.messages()).hasSize(1);
        assertThat(file.onlyMessage().body().textAt("CstmrCdtTrfInitn/GrpHdr/MsgId"))
                .contains(payload);
    }

    // ------------------------------------------------------------ write back

    /// R-I6 — what was read is what is written.
    @Test
    void aFileThatWasReadIsWrittenBackByteForByte() {
        byte[] original = bytes(header("M1") + body("MSG-1") + header("M2") + body("MSG-2"));

        byte[] written = ZediEnvelopeWriter.toByteArray(ZediEnvelopeReader.read(original));

        assertThat(written).isEqualTo(original);
    }

    /// Including whatever preceded the first declaration.
    @Test
    void bytesBeforeTheFirstDeclarationSurvive() {
        byte[] original = bytes("﻿" + header("M1") + body("MSG-1"));

        ZediFile file = ZediEnvelopeReader.read(original);

        assertThat(file.preamble()).isNotEmpty();
        assertThat(ZediEnvelopeWriter.toByteArray(file)).isEqualTo(original);
    }

    /// Including line endings that are not the ones the profile specifies.
    @Test
    void unusualFramingSurvivesUnchanged() {
        byte[] original = bytes(header("M1").replace("\r\n", "\n") + body("MSG-1"));

        assertThat(ZediEnvelopeWriter.toByteArray(ZediEnvelopeReader.read(original)))
                .isEqualTo(original);
    }

    /// `Fr` and `To` take different branches of `head.001`, and both are
    /// legal, so a schema cannot tell them apart — only the profile does. The
    /// sender is a company and takes `OrgId`; the recipient is a bank and
    /// takes `FIId`.
    @Test
    void theSenderIsAnOrganisationAndTheRecipientIsABank() {
        var head = new BusinessApplicationHeader(
                "9900000001", "9999", "M1", MessageId.PAIN_001_001_03,
                java.time.OffsetDateTime.parse("2026-09-01T00:00:00Z"));

        var written = new String(
                io.zengin4j.iso20022.xml.XmlSerializer.toBytes(head.toXml()),
                StandardCharsets.UTF_8);

        assertThat(written)
                .contains("<Fr>")
                .containsSubsequence("<Fr>", "<OrgId>", "9900000001", "</Fr>")
                .containsSubsequence("<To>", "<FIId>", "<FinInstnId>", "9999", "</To>");
        assertThat(written.substring(written.indexOf("<To>"), written.indexOf("</To>")))
                .as("the recipient must not carry the organisation branch")
                .doesNotContain("OrgId");
    }

    /// Changing the branch the writer uses must not cost the reader anything:
    /// it accepts both, which is why this fix was one method.
    @Test
    void aRecipientWrittenAsABankReadsBackUnchanged() {
        var head = new BusinessApplicationHeader(
                "9900000001", "9999", "M1", MessageId.PAIN_001_001_03,
                java.time.OffsetDateTime.parse("2026-09-01T00:00:00Z"));

        assertThat(BusinessApplicationHeader.from(head.toXml())).isEqualTo(head);
    }

    /// Headers written before this change addressed the bank through `OrgId`.
    /// They still read, because the reader was already right.
    @Test
    void aRecipientWrittenTheOldWayStillReads() {
        var legacy = io.zengin4j.iso20022.xml.XmlElement.element("AppHdr")
                .namespace(MessageId.HEAD_001_001_01.namespace())
                .child(io.zengin4j.iso20022.xml.XmlElement.element("To")
                        .child(io.zengin4j.iso20022.xml.XmlElement.element("OrgId")
                                .child(io.zengin4j.iso20022.xml.XmlElement.element("Id")
                                        .child(io.zengin4j.iso20022.xml.XmlElement.element("OrgId")
                                                .child(io.zengin4j.iso20022.xml.XmlElement
                                                        .element("Othr")
                                                        .textChild("Id", "9999"))))))
                .textChild("MsgDefIdr", "pain.001.001.03")
                .textChild("CreDt", "2026-09-01T00:00:00Z")
                .build();

        assertThat(BusinessApplicationHeader.from(legacy).to()).isEqualTo("9999");
    }

    /// `CreDt` is `ISONormalisedDateTime`, whose schema type carries a
    /// pattern facet requiring the literal `Z`. A JST timestamp written with
    /// its own offset fails that facet — and a validator is the only thing
    /// that would ever have said so, which is why this is pinned here rather
    /// than left to the golden.
    @Test
    void theHeaderTimestampIsNormalisedToUtc() {
        var head = new BusinessApplicationHeader(
                "9900000001", "9999", "M1", MessageId.PAIN_001_001_03,
                java.time.OffsetDateTime.parse("2026-09-01T09:30:00+09:00"));

        var written = new String(
                io.zengin4j.iso20022.xml.XmlSerializer.toBytes(head.toXml()),
                StandardCharsets.UTF_8);

        assertThat(written).contains("<CreDt>2026-09-01T00:30:00Z</CreDt>");
        assertThat(written).doesNotContain("+09:00");
    }

    /// And the instant survives the conversion: normalising is a change of
    /// notation, not of time.
    @Test
    void normalisingTheHeaderTimestampKeepsTheInstant() {
        var jst = java.time.OffsetDateTime.parse("2026-09-01T09:30:00+09:00");
        var head = new BusinessApplicationHeader(
                "9900000001", "9999", "M1", MessageId.PAIN_001_001_03, jst);

        var reread = BusinessApplicationHeader.from(head.toXml());

        assertThat(reread.creationDate()).isEqualTo(jst);
    }

    @Test
    void aMessageBuiltFromAModelSerialisesItsOwnBytes() {
        var head = new BusinessApplicationHeader(
                "9900000001", "9999", "M1", MessageId.PAIN_001_001_03,
                java.time.OffsetDateTime.parse("2026-09-01T00:00:00Z"));
        var message = ZediMessage.of(head,
                io.zengin4j.iso20022.xml.XmlElement.element("Document")
                        .namespace(MessageId.PAIN_001_001_03.namespace())
                        .child(io.zengin4j.iso20022.xml.XmlElement.element("CstmrCdtTrfInitn")
                                .child(io.zengin4j.iso20022.xml.XmlElement.element("GrpHdr")
                                        .textChild("MsgId", "MSG-1")))
                        .build());

        byte[] written = ZediEnvelopeWriter.toByteArray(ZediFile.of(message));

        assertThat(new String(written, StandardCharsets.UTF_8))
                .contains("</AppHdr>\r\n<?xml")
                .contains("<MsgId>MSG-1</MsgId>");
        assertThat(ZediEnvelopeReader.read(written).onlyMessage()).isEqualTo(message);
    }

    // ------------------------------------------------------------------ i/o

    @Test
    void readingAndWritingAFileOnDiskRoundTrips(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("payments.xml");
        byte[] original = bytes(header("M1") + body("MSG-1"));
        Files.write(path, original);

        ZediFile file = ZediEnvelopeReader.read(path);
        Path out = directory.resolve("written.xml");
        ZediEnvelopeWriter.write(file, out);

        assertThat(Files.readAllBytes(out)).isEqualTo(original);
    }

    @Test
    void readingAStreamWorksTheSameWay() {
        byte[] original = bytes(header("M1") + body("MSG-1"));

        ZediFile file = ZediEnvelopeReader.read(new ByteArrayInputStream(original));

        assertThat(ZediEnvelopeWriter.toByteArray(file)).isEqualTo(original);
    }

    @Test
    void onlyMessageSaysSoWhenThereIsNotExactlyOne() {
        ZediFile file = ZediEnvelopeReader.read(bytes(body("A") + body("B")));

        assertThat(file.messages()).hasSize(2);
        assertThat(file).hasToString("ZediFile[2 messages]");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(file::onlyMessage)
                .withMessageContaining("R-I7");
    }

    @Test
    void nullsAreRejectedByName() {
        assertThatNullPointerException()
                .isThrownBy(() -> ZediEnvelopeReader.read((byte[]) null));
        assertThatNullPointerException()
                .isThrownBy(() -> ZediEnvelopeReader.read((Path) null));
        assertThatNullPointerException()
                .isThrownBy(() -> ZediEnvelopeWriter.toByteArray(null));
    }
}
