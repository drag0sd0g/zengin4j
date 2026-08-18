package io.zengin4j.iso20022.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The XML layer, including what it does with input that is trying to hurt it.
 *
 * <p>A payment file arrives from another organisation's system. The standard
 * XML attacks cost a few hundred bytes to mount and can read local files or
 * exhaust a heap, and every one of them is a default that has to be switched
 * off explicitly — which is exactly the kind of thing that is switched off
 * once, never tested, and quietly switched back on by a refactor.
 */
class XmlTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // --------------------------------------------------------------- hardening

    /**
     * XXE: the classic. Without {@code SUPPORT_DTD=false} this reads a local
     * file into an element and hands it to whoever asked for the payment.
     */
    @Test
    void externalEntitiesCannotReadTheFilesystem(@TempDir Path directory) throws Exception {
        Path secret = directory.resolve("secret.txt");
        Files.writeString(secret, "the contents of a file nobody asked for");

        String attack = "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE root [<!ENTITY xxe SYSTEM \"file://" + secret + "\">]>\n"
                + "<root>&xxe;</root>";

        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(bytes(attack)));
    }

    /**
     * The billion laughs: a few hundred bytes that expand to gigabytes.
     * Refusing DTDs outright removes the whole family, rather than trying to
     * bound an expansion whose whole point is that bounds get raised.
     */
    @Test
    void nestedEntityExpansionIsRefused() {
        StringBuilder attack = new StringBuilder("<?xml version=\"1.0\"?>\n<!DOCTYPE lolz [\n")
                .append("<!ENTITY lol0 \"lol\">\n");
        for (int i = 1; i <= 9; i++) {
            String inner = "&lol" + (i - 1) + ";";
            attack.append("<!ENTITY lol").append(i).append(" \"")
                    .append(inner.repeat(10)).append("\">\n");
        }
        attack.append("]>\n<lolz>&lol9;</lolz>");

        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(bytes(attack.toString())));
    }

    @Test
    void aDocumentNestedTooDeeplyIsRefused() {
        int depth = XmlParser.MAX_DEPTH + 1;
        String deep = "<?xml version=\"1.0\"?>" + "<a>".repeat(depth) + "x" + "</a>".repeat(depth);

        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(bytes(deep)))
                .withMessageContaining("nested more than");
    }

    /**
     * The depth guard must be this library's, not the JDK's.
     *
     * <p>{@code jdk.xml.maxElementDepth} defaults to 100 and is a system
     * property a host can raise or disable. A limit set at 100 here would never
     * run — and would look like protection while providing none.
     */
    @Test
    void theDepthGuardFiresBeforeTheJdkOwnLimit() {
        assertThat(XmlParser.MAX_DEPTH).isLessThan(100);
    }

    @Test
    void aDocumentWithTooManyElementsIsRefused() {
        String many = "<?xml version=\"1.0\"?><a>" + "<b/>".repeat(20) + "</a>";

        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(bytes(many), XmlParser.MAX_DEPTH, 10))
                .withMessageContaining("more than 10 elements");
    }

    @Test
    void theRealElementBoundIsFarPastAnythingLegitimate() {
        assertThat(XmlParser.MAX_ELEMENTS).isGreaterThan(1_000_000);
    }

    /**
     * The JDK's parser cannot always say what is wrong, and must not escape.
     *
     * <p>A DTD containing an invalid character sends Xerces looking for the
     * message key {@code InvalidCharInDTD}, which is missing from its own
     * bundle, so it throws {@code MissingResourceException} from inside the
     * error reporter — past every checked exception it declares. Found by
     * fuzzing, in forty-two bytes.
     *
     * <p>The contract is absolute: any byte sequence either parses or raises
     * {@link MalformedXmlException}.
     */
    @Test
    void aParserFailureThatIsNotAStreamExceptionIsStillMalformedXml() {
        byte[] brokenDtd = bytes("<?xml version=\"1.0\"?><!DOCTYPE x PUBLIC \"\" \"\"[>");

        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(brokenDtd))
                .satisfies(refused -> assertThat(refused.getCause()).isNotNull());
    }

    @Test
    void aDocumentThatIsNotWellFormedSaysWhereItStopped() {
        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(bytes("<?xml version=\"1.0\"?><a><b></a>")))
                .satisfies(refused -> assertThat(refused.byteOffset()).isNotNegative());
    }

    @Test
    void emptyInputIsRefusedRatherThanReturningNothing() {
        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(new byte[0]));
    }

    // ------------------------------------------------------------------ read

    @Test
    void anElementTreeIsReadWithItsNamespaceAndAttributes() {
        XmlElement root = XmlParser.parse(bytes("<?xml version=\"1.0\"?>"
                + "<Document xmlns=\"urn:test\">"
                + "<Amt><InstdAmt Ccy=\"JPY\">150000</InstdAmt></Amt>"
                + "</Document>"));

        assertThat(root.name()).isEqualTo("Document");
        assertThat(root.namespace()).isEqualTo("urn:test");
        assertThat(root.at("Amt/InstdAmt")).isPresent();
        assertThat(root.textAt("Amt/InstdAmt")).contains("150000");
        assertThat(root.at("Amt/InstdAmt").orElseThrow().attribute("Ccy")).contains("JPY");
        assertThat(root.at("Amt/InstdAmt").orElseThrow().namespace())
                .as("a default namespace reaches every element below it")
                .isEqualTo("urn:test");
    }

    @Test
    void aMissingPathIsEmptyRatherThanAnError() {
        XmlElement root = XmlParser.parse(bytes("<?xml version=\"1.0\"?><a><b>x</b></a>"));

        assertThat(root.at("b/c/d")).isEmpty();
        assertThat(root.textAt("b/c/d")).isEmpty();
        assertThat(root.child("nope")).isEmpty();
        assertThat(root.childrenNamed("nope")).isEmpty();
    }

    @Test
    void repeatedElementsAreKeptInOrder() {
        XmlElement root = XmlParser.parse(bytes("<?xml version=\"1.0\"?>"
                + "<RmtInf><Ustrd>one</Ustrd><Ustrd>two</Ustrd><Ustrd>three</Ustrd></RmtInf>"));

        assertThat(root.childrenNamed("Ustrd").stream().map(XmlElement::text))
                .containsExactly("one", "two", "three");
    }

    @Test
    void anEmptyElementHasNoText() {
        XmlElement root = XmlParser.parse(bytes("<?xml version=\"1.0\"?><a><b/></a>"));

        assertThat(root.child("b").orElseThrow().text()).isEmpty();
        assertThat(root.textAt("b")).isEmpty();
    }

    /**
     * Mixed content is legal XML and is refused, in this module's vocabulary.
     *
     * <p>Found by fuzzing after 116 runs. It used to throw the
     * {@code IllegalStateException} the <em>builder</em> raises for a mapping
     * mistake — straight out of the parser, past the whole declared exception
     * hierarchy, on input a sender can simply write. The two refusals look the
     * same and mean different things: one is a document this library cannot
     * represent, the other is a bug in the mapper.
     */
    @Test
    void mixedContentIsRefusedAsMalformedRatherThanAsAMappingMistake() {
        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(bytes(
                        "<?xml version=\"1.0\"?><a>text<b/></a>")))
                .withMessageContaining("mixed content")
                .withMessageContaining("'text'");
    }

    /** Whitespace between elements is not mixed content. */
    @Test
    void whitespaceBetweenElementsIsNotMixedContent() {
        XmlElement root = XmlParser.parse(bytes(
                "<?xml version=\"1.0\"?><a>\n  <b>x</b>\n</a>"));

        assertThat(root.children()).hasSize(1);
        assertThat(root.text()).isEmpty();
    }

    /** Text before a child counts too, not only after. */
    @Test
    void textBeforeAChildIsAlsoMixedContent() {
        assertThatExceptionOfType(MalformedXmlException.class)
                .isThrownBy(() -> XmlParser.parse(bytes(
                        "<?xml version=\"1.0\"?><a>before<b/>after</a>")));
    }

    @Test
    void commentsAndProcessingInstructionsAreIgnored() {
        XmlElement root = XmlParser.parse(bytes("<?xml version=\"1.0\"?>"
                + "<a><!-- note --><?target data?><b>x</b></a>"));

        assertThat(root.children()).hasSize(1);
        assertThat(root.textAt("b")).contains("x");
    }

    // ----------------------------------------------------------------- write

    @Test
    void escapingSurvivesAParser() {
        XmlElement root = XmlElement.element("Nm").text("A & B <c> \"d\" 'e'").build();

        String written = XmlSerializer.toText(root);

        assertThat(XmlParser.parse(written.getBytes(StandardCharsets.UTF_8)).text())
                .isEqualTo("A & B <c> \"d\" 'e'");
    }

    @Test
    void attributesAreEscapedToo() {
        XmlElement root = XmlElement.element("InstdAmt")
                .attribute("Ccy", "a\"b<c&d").text("1").build();

        String written = XmlSerializer.toText(root);

        assertThat(XmlParser.parse(written.getBytes(StandardCharsets.UTF_8)).attribute("Ccy"))
                .contains("a\"b<c&d");
    }

    /**
     * XML 1.0 has no representation for most control characters — not even an
     * escape — so a document containing one cannot be written at all. Saying so
     * beats emitting a file no parser will read.
     */
    @Test
    void aControlCharacterCannotBeWritten() {
        XmlElement root = XmlElement.element("Nm").text("beforeafter").build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> XmlSerializer.toText(root))
                .withMessageContaining("U+0001");
    }

    @Test
    void tabsAndNewlinesAreFine() {
        XmlElement root = XmlElement.element("Nm").text("a\tb\nc").build();

        assertThat(XmlSerializer.toText(root)).contains("a\tb");
    }

    @Test
    void outputIsDeterministic() {
        XmlElement root = XmlElement.element("a").namespace("urn:x")
                .child(XmlElement.element("b").attribute("p", "1").attribute("q", "2").text("x"))
                .build();

        assertThat(XmlSerializer.toText(root)).isEqualTo(XmlSerializer.toText(root));
        assertThat(XmlSerializer.toBytes(root))
                .isEqualTo(XmlSerializer.toText(root).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theNamespaceIsDeclaredOnceRatherThanOnEveryElement() {
        XmlElement root = XmlElement.element("a").namespace("urn:x")
                .child(XmlElement.element("b").text("x"))
                .build();

        assertThat(XmlSerializer.toText(root).split("xmlns=", -1)).hasSize(2);
    }

    @Test
    void anElementWithNothingInItIsSelfClosing() {
        assertThat(XmlSerializer.toText(XmlElement.element("a").build()))
                .contains("<a/>");
    }

    @Test
    void mixedContentIsARefusedMistake() {
        assertThatIllegalStateException()
                .isThrownBy(() -> XmlElement.element("a")
                        .text("x")
                        .child(XmlElement.element("b"))
                        .build())
                .withMessageContaining("mixed content");
    }

    @Test
    void aTextChildIsSkippedWhenThereIsNothingToWrite() {
        XmlElement root = XmlElement.element("a")
                .textChild("present", "x")
                .textChild("absent", null)
                .textChild("blank", "   ")
                .build();

        assertThat(root.children()).hasSize(1);
        assertThat(root.child("present")).isPresent();
    }

    @Test
    void aTreeSurvivesBeingWrittenAndReadBack() {
        XmlElement root = XmlElement.element("Document").namespace("urn:test")
                .child(XmlElement.element("GrpHdr")
                        .textChild("MsgId", "MSG-1")
                        .child(XmlElement.element("Amt")
                                .attribute("Ccy", "JPY")
                                .text("150000")))
                .build();

        XmlElement parsed = XmlParser.parse(XmlSerializer.toBytes(root));

        assertThat(parsed).isEqualTo(root);
        assertThat(parsed).hasSameHashCodeAs(root);
    }

    @Test
    void toStringSaysWhatTheElementIs() {
        assertThat(XmlElement.text("Nm", "x")).hasToString("<Nm>x</Nm>");
        assertThat(XmlElement.element("a").child(XmlElement.text("b", "x")).build())
                .hasToString("<a> (1 children)");
    }

    // --------------------------------------------------------------- date-time

    /**
     * {@code xs:dateTime} requires seconds. {@code OffsetDateTime.toString()}
     * omits them when they are zero, which parses back perfectly and is invalid
     * on the wire — the sort of thing only a schema notices.
     */
    @Test
    void aTimestampAlwaysCarriesItsSeconds() {
        assertThat(IsoDateTime.format(OffsetDateTime.parse("2026-09-01T00:00Z")))
                .isEqualTo("2026-09-01T00:00:00Z");
        assertThat(IsoDateTime.format(OffsetDateTime.parse("2026-09-01T12:34:56+09:00")))
                .isEqualTo("2026-09-01T12:34:56+09:00");
    }

    @Test
    void aFormattedTimestampParsesBackToWhatItCameFrom() {
        OffsetDateTime original = OffsetDateTime.parse("2026-09-01T00:00Z");

        assertThat(OffsetDateTime.parse(IsoDateTime.format(original))).isEqualTo(original);
    }
}
