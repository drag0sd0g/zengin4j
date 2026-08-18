package io.zengin4j.iso20022.pain001;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The 金融EDI情報 payload carried inside a transaction's remittance
 * information.
 *
 * <p>The profile does not put structured remittance data in the structured
 * remittance elements. It base64-encodes an XML document, wraps it in three
 * MIME headers, splits the encoding at 76 characters, and puts each resulting
 * line in its own {@code Ustrd} element:
 *
 * <pre>
 * &lt;RmtInf&gt;
 *   &lt;Ustrd&gt;MIME-Version: 1.0&lt;/Ustrd&gt;
 *   &lt;Ustrd&gt;Content-Type: text/xml&lt;/Ustrd&gt;
 *   &lt;Ustrd&gt;Content-Transfer-Encoding: base64&lt;/Ustrd&gt;
 *   &lt;Ustrd&gt;PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0i…&lt;/Ustrd&gt;
 *   &lt;Ustrd&gt;VVRGLTgiPz48VHJhbkluZj4…&lt;/Ustrd&gt;
 * &lt;/RmtInf&gt;
 * </pre>
 *
 * <p><strong>The encoding is kept exactly as it arrived (R-I12).</strong> Two
 * base64 encodings of the same bytes can differ — in where the lines are split,
 * and in padding — so re-encoding a decoded payload produces different XML for
 * identical content. That would break byte-identical round trips for a payload
 * this library does not otherwise touch, so the lines are stored verbatim and
 * written back verbatim. {@link #decodedBytes()} decodes a copy for callers who
 * want to read it.
 *
 * @param mimeHeaders the header lines, in order
 * @param base64Lines the encoded payload, split exactly as it arrived
 * @since 0.5.0
 */
public record EdiAttachment(List<String> mimeHeaders, List<String> base64Lines) {

    /** The header that marks a remittance block as an encoded attachment. */
    public static final String TRANSFER_ENCODING = "Content-Transfer-Encoding: base64";

    private static final String MIME_VERSION = "MIME-Version: 1.0";
    private static final String CONTENT_TYPE = "Content-Type: text/xml";

    /** Where the profile splits the encoding. */
    public static final int LINE_LENGTH = 76;

    /**
     * Validates the attachment.
     *
     * @throws NullPointerException if either list is null
     */
    public EdiAttachment {
        mimeHeaders = List.copyOf(Objects.requireNonNull(mimeHeaders, "mimeHeaders"));
        base64Lines = List.copyOf(Objects.requireNonNull(base64Lines, "base64Lines"));
    }

    /**
     * Encodes a payload the way the profile does.
     *
     * @param payload the bytes to carry
     * @return the attachment
     */
    public static EdiAttachment of(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        String encoded = Base64.getEncoder().encodeToString(payload);
        List<String> lines = new ArrayList<>();
        for (int start = 0; start < encoded.length(); start += LINE_LENGTH) {
            lines.add(encoded.substring(start, Math.min(start + LINE_LENGTH, encoded.length())));
        }
        return new EdiAttachment(List.of(MIME_VERSION, CONTENT_TYPE, TRANSFER_ENCODING), lines);
    }

    /**
     * Recognises an attachment in a run of {@code Ustrd} lines.
     *
     * <p>Recognition is by the transfer-encoding header, not by position: a
     * sender that omits {@code MIME-Version} or orders the headers differently
     * still produces something this can read, and anything without the header
     * is ordinary unstructured remittance text rather than a broken attachment.
     *
     * @param unstructured the {@code Ustrd} lines, in document order
     * @return the attachment, or empty when the lines are ordinary text
     */
    public static Optional<EdiAttachment> parse(List<String> unstructured) {
        Objects.requireNonNull(unstructured, "unstructured");
        int marker = unstructured.indexOf(TRANSFER_ENCODING);
        if (marker < 0) {
            return Optional.empty();
        }
        return Optional.of(new EdiAttachment(
                List.copyOf(unstructured.subList(0, marker + 1)),
                List.copyOf(unstructured.subList(marker + 1, unstructured.size()))));
    }

    /**
     * The lines as they belong in {@code RmtInf}, headers first.
     *
     * @return the {@code Ustrd} contents, in order
     */
    public List<String> toUnstructured() {
        List<String> lines = new ArrayList<>(mimeHeaders.size() + base64Lines.size());
        lines.addAll(mimeHeaders);
        lines.addAll(base64Lines);
        return List.copyOf(lines);
    }

    /**
     * The encoded payload, lines joined and nothing else changed.
     *
     * @return the base64 text
     */
    public String base64() {
        return String.join("", base64Lines);
    }

    /**
     * The payload itself.
     *
     * @return the decoded bytes
     * @throws IllegalStateException if the payload is not valid base64
     */
    public byte[] decodedBytes() {
        try {
            return Base64.getDecoder().decode(base64());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException(
                    "the attachment declares base64 transfer encoding but does not decode: "
                            + notBase64.getMessage()
                            + ". The lines are preserved exactly as they arrived, so they can "
                            + "still be written back unchanged — see base64Lines().",
                    notBase64);
        }
    }

    /**
     * The payload as text.
     *
     * @param charset the payload's encoding; the profile's is UTF-8
     * @return the decoded text
     * @throws IllegalStateException if the payload is not valid base64
     */
    public String decodedText(Charset charset) {
        Objects.requireNonNull(charset, "charset");
        return new String(decodedBytes(), charset);
    }

    /**
     * The payload as UTF-8 text.
     *
     * @return the decoded text
     * @throws IllegalStateException if the payload is not valid base64
     */
    public String decodedText() {
        return decodedText(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "EdiAttachment[" + base64().length() + " base64 chars in "
                + base64Lines.size() + " lines]";
    }
}
