package io.zengin4j.iso20022.xml;

import module java.base;
import module java.xml;

/// Reads a single well-formed XML document into an [XmlElement] tree.
///
/// **The input is assumed hostile.** A payment file arrives from
/// another organisation's system, and the standard XML attacks are cheap to
/// mount and expensive to survive: external entities read local files, nested
/// entity expansion exhausts heap from a few kilobytes of input, and deep
/// nesting exhausts stack. So:
///
/// - DTDs are refused outright, which removes entity expansion and the
///   billion-laughs family with it;
/// - external entities are disabled, so no parse can touch the filesystem
///   or the network;
/// - depth and element count are bounded, so a document that is well-formed
///   and merely enormous fails with a diagnostic rather than an
///   `OutOfMemoryError` in the caller's process.
///
/// The bounds are generous — sixty-four levels and two million elements —
/// because they exist to stop an attack, not to express a policy about file
/// size. A legitimate `pain.001` carrying 50,000 transactions is nowhere
/// near either.
///
/// @since 0.5.0
public final class XmlParser {

    /// Deeper than any ISO 20022 message, and deliberately below the JDK's own
    /// `jdk.xml.maxElementDepth`.
    ///
    /// The JDK defaults that property to 100, so a limit of 100 here would
    /// never fire — the parser would refuse first, with its own message, and
    /// this guard would be untested code that looked like protection. Sitting
    /// below it makes this the guard that actually runs, and leaves the JDK's as
    /// a backstop for a host that has raised or disabled it.
    ///
    /// A `pain.001` nests about ten levels. Sixty-four is not a
    /// constraint on anything real.
    public static final int MAX_DEPTH = 64;

    /// Roughly a 50 MB document, which parses into several hundred megabytes of
    /// objects. Far past anything the profile produces, and short of what
    /// exhausts a default heap.
    public static final int MAX_ELEMENTS = 2_000_000;

    private XmlParser() {
    }

    /// Parses one document.
    ///
    /// @param bytes the document's bytes, including its declaration
    /// @return the root element
    /// @throws MalformedXmlException if the bytes are not well-formed XML, or
    ///   exceed [#MAX_DEPTH] or
    ///   [#MAX_ELEMENTS]
    public static XmlElement parse(byte[] bytes) {
        return parse(bytes, MAX_DEPTH, MAX_ELEMENTS);
    }

    /// Parses one document under given bounds.
    ///
    /// Exists so the bounds can be tested without building a document large
    /// enough to hit the real ones — a two-million-element fixture would make
    /// the suite slow enough that somebody would delete the test.
    ///
    /// @param bytes       the document's bytes
    /// @param maxDepth    the deepest nesting to accept
    /// @param maxElements the most elements to accept
    /// @return the root element
    /// @throws MalformedXmlException if the bytes are not well-formed XML or
    ///   exceed a bound
    static XmlElement parse(byte[] bytes, int maxDepth, int maxElements) {
        XMLStreamReader reader = null;
        try {
            reader = hardenedFactory().createXMLStreamReader(new ByteArrayInputStream(bytes));
            return read(reader, maxDepth, maxElements);
        } catch (XMLStreamException e) {
            long offset = e.getLocation() == null ? -1 : e.getLocation().getCharacterOffset();
            throw new MalformedXmlException(offset,
                    "not well-formed XML: " + rootCause(e),
                    "整形式の XML ではありません: " + rootCause(e), e);
        } catch (MalformedXmlException alreadyDiagnosed) {
            throw alreadyDiagnosed;
        } catch (RuntimeException unexpected) {
            // The JDK's parser does not always manage to report a malformed
            // document as an XMLStreamException. A DTD containing an invalid
            // character makes it look up the message key "InvalidCharInDTD",
            // which is not in its own bundle, and it throws
            // MissingResourceException from inside the error reporter — past
            // every checked exception it declares. Found by fuzzing, in 42
            // bytes.
            //
            // The contract here is absolute: any byte sequence either parses or
            // raises MalformedXmlException. A third-party parser surprising us
            // is exactly what that contract has to absorb, so the net is wide
            // and the cause is kept.
            throw new MalformedXmlException(-1,
                    "the XML parser failed in a way it does not describe: "
                            + unexpected.getClass().getName() + ": " + unexpected.getMessage()
                            + ". The document is malformed; the parser could not say how.",
                    "XML パーサーが説明を伴わない形で失敗しました: "
                            + unexpected.getClass().getName() + ": " + unexpected.getMessage()
                            + "。文書は不正ですが、パーサーは理由を報告できませんでした。",
                    unexpected);
        } finally {
            closeQuietly(reader);
        }
    }

    private static XMLInputFactory hardenedFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return factory;
    }

    private static XmlElement read(XMLStreamReader reader, int maxDepth, int maxElements)
            throws XMLStreamException {
        Deque<XmlElement.Builder> open = new ArrayDeque<>();
        Deque<StringBuilder> characters = new ArrayDeque<>();
        XmlElement root = null;
        int elements = 0;

        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT -> {
                    if (open.size() >= maxDepth) {
                        throw tooBig(reader, "nested more than " + maxDepth + " levels deep",
                                maxDepth + " 階層を超える入れ子です");
                    }
                    if (++elements > maxElements) {
                        throw tooBig(reader, "more than " + maxElements + " elements",
                                "要素数が " + maxElements + " を超えています");
                    }
                    open.push(startElement(reader));
                    characters.push(new StringBuilder());
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (!characters.isEmpty()) {
                        characters.peek().append(reader.getText());
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    XmlElement.Builder finished = open.pop();
                    String content = characters.pop().toString();
                    if (!content.isBlank()) {
                        if (finished.hasChildren()) {
                            throw mixedContent(reader, content.trim());
                        }
                        finished.text(content.trim());
                    }
                    XmlElement element = finished.build();
                    if (open.isEmpty()) {
                        root = element;
                    } else {
                        open.peek().child(element);
                    }
                }
                default -> {
                    // Comments, processing instructions and whitespace between
                    // elements carry no information this module maps.
                }
            }
        }

        if (root == null) {
            throw new MalformedXmlException(-1, "the document has no root element",
                    "ルート要素がありません", null);
        }
        return root;
    }

    private static XmlElement.Builder startElement(XMLStreamReader reader) {
        XmlElement.Builder builder = XmlElement.element(reader.getLocalName());
        String namespace = reader.getNamespaceURI();
        if (namespace != null) {
            builder.namespace(namespace);
        }
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            builder.attribute(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }
        return builder;
    }

    /// Text and child elements in the same element.
    ///
    /// Legal XML, and not something ISO 20022 uses: no element in the profile
    /// is mixed content. Reading one would mean deciding whether the text or the
    /// children were the value, and there is no answer — so it is refused, in
    /// the module's own vocabulary rather than as an
    /// `IllegalStateException` escaping the parser.
    ///
    /// Found by fuzzing. The document that produced it is committed as a
    /// replay input.
    private static MalformedXmlException mixedContent(XMLStreamReader reader, String text) {
        long offset = reader.getLocation() == null ? -1 : reader.getLocation().getCharacterOffset();
        String shown = text.length() <= 40 ? text : text.substring(0, 40) + "…";
        return new MalformedXmlException(offset,
                "<" + reader.getLocalName() + "> holds both child elements and the text '" + shown
                        + "'. That is legal XML and is not something ISO 20022 uses — no element "
                        + "in the profile is mixed content — so there is no way to say whether the "
                        + "text or the children are the value.",
                "<" + reader.getLocalName() + "> は子要素とテキスト '" + shown
                        + "' の両方を含んでいます。XML としては正当ですが ISO 20022 では"
                        + "使用されない形式(混在内容)であり、テキストと子要素のどちらが値なのか"
                        + "判断できません。",
                null);
    }

    private static MalformedXmlException tooBig(XMLStreamReader reader, String en, String ja) {
        long offset = reader.getLocation() == null ? -1 : reader.getLocation().getCharacterOffset();
        return new MalformedXmlException(offset,
                "refused: the document is " + en + ". This bound exists to stop a small file "
                        + "from exhausting memory, not to limit legitimate ones.",
                "処理を中止しました: " + ja + "。この上限は小さなファイルによるメモリ枯渇を防ぐためのもので、"
                        + "正当なファイルを制限するものではありません。",
                null);
    }

    private static String rootCause(XMLStreamException e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        int marker = message.lastIndexOf("Message: ");
        return marker < 0 ? message : message.substring(marker + "Message: ".length());
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException _) {
            // Closing a reader over a byte array releases nothing that matters,
            // and a failure here would mask the parse failure being reported.
        }
    }
}
