package io.zengin4j.iso20022.xml;

import module java.base;

/// Writes an [XmlElement] tree back out as bytes.
///
/// Hand-written, for the same reason the JSON and SARIF writers are
/// (ADR-0022, ADR-0031): the output shape is fixed and narrow, the alternative
/// is a dependency in a module whose value is partly that it has none, and a
/// hand-written writer is only dangerous when nothing checks it. Something does
/// — every document this produces is parsed back by a real parser in the tests,
/// and by the XSD when one is supplied.
///
/// Output is deterministic: same tree, same bytes, always. That is what makes
/// a golden file meaningful and a diff between two runs readable.
///
/// @since 0.5.0
public final class XmlSerializer {

    /// The declaration the profile uses, followed by CRLF (R-I6).
    public static final String DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    private static final String INDENT = "  ";
    private static final String CRLF = "\r\n";

    private XmlSerializer() {
    }

    /// Serialises a document, declaration included.
    ///
    /// @param root the root element
    /// @return UTF-8 bytes, CRLF line endings
    public static byte[] toBytes(XmlElement root) {
        return toText(root).getBytes(StandardCharsets.UTF_8);
    }

    /// Serialises a document, declaration included.
    ///
    /// @param root the root element
    /// @return the document text, CRLF line endings
    public static String toText(XmlElement root) {
        var out = new StringBuilder(DECLARATION).append(CRLF);
        write(root, "", "", out);
        return out.toString();
    }

    private static void write(XmlElement element, String indent, String inheritedNamespace,
            StringBuilder out) {
        out.append(indent).append('<').append(element.name());

        if (!element.namespace().isEmpty() && !element.namespace().equals(inheritedNamespace)) {
            out.append(" xmlns=\"").append(escapeAttribute(element.namespace())).append('"');
        }
        element.attributes().forEach((name, value) ->
                out.append(' ').append(name).append("=\"").append(escapeAttribute(value))
                        .append('"'));

        if (element.children().isEmpty() && element.text().isEmpty()) {
            out.append("/>").append(CRLF);
            return;
        }

        out.append('>');
        if (element.children().isEmpty()) {
            out.append(escapeText(element.text()));
        } else {
            out.append(CRLF);
            String namespace = element.namespace().isEmpty()
                    ? inheritedNamespace : element.namespace();
            for (XmlElement child : element.children()) {
                write(child, indent + INDENT, namespace, out);
            }
            out.append(indent);
        }
        out.append("</").append(element.name()).append('>').append(CRLF);
    }

    /// Escapes character content.
    ///
    /// `>` is escaped although only `]]>` requires it. Escaping it
    /// unconditionally costs three bytes and removes a case nobody would think
    /// to test.
    ///
    /// @param text the text
    /// @return the escaped text
    static String escapeText(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> appendChecked(c, out);
            }
        }
        return out.toString();
    }

    static String escapeAttribute(String value) {
        var out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> appendChecked(c, out);
            }
        }
        return out.toString();
    }

    /// XML 1.0 admits tab, newline and carriage return and no other control
    /// character. There is no escape for the rest — `&#x1;` is as illegal
    /// as the raw byte — so a document containing one cannot be written at all.
    private static void appendChecked(char c, StringBuilder out) {
        if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
            throw new IllegalArgumentException(String.format(
                    "cannot write U+%04X: XML 1.0 has no representation for this control "
                            + "character, escaped or otherwise. Remove it before mapping.", (int) c));
        }
        out.append(c);
    }
}
