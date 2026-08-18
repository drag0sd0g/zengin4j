package io.zengin4j.iso20022.envelope;

import io.zengin4j.iso20022.xml.XmlElement;
import io.zengin4j.iso20022.xml.XmlSerializer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One message from a ZEDI file: a business application header and the body it
 * introduces.
 *
 * <p>A message keeps the bytes it was read from, and is the only thing the
 * writer emits, which is how R-I6's byte-identical framing is achieved rather
 * than approximated: writing a file that was read concatenates the slices it
 * was split into, so identity is a property of the construction and not of a
 * separator model that has to be got right.
 *
 * <p>A message built from a mapping has no original bytes and serialises its
 * own. The two cases are separate factories so that neither can produce the
 * other's failure — a model that has drifted from stale bytes is impossible
 * here, because nothing is mutable and nothing is regenerated.
 *
 * @since 0.5.0
 */
public final class ZediMessage {

    private final BusinessApplicationHeader header;
    private final XmlElement body;
    private final byte[] headerBytes;
    private final byte[] bodyBytes;

    private ZediMessage(BusinessApplicationHeader header, XmlElement body,
            byte[] headerBytes, byte[] bodyBytes) {
        this.header = header;
        this.body = body;
        this.headerBytes = headerBytes;
        this.bodyBytes = bodyBytes;
    }

    /**
     * A message as it was read, retaining its exact bytes.
     *
     * @param header      the parsed header, or null when the body arrived bare
     * @param headerBytes the header segment's bytes, or null with a null header
     * @param body        the parsed body
     * @param bodyBytes   the body segment's bytes
     * @return the message
     */
    static ZediMessage read(BusinessApplicationHeader header, byte[] headerBytes,
            XmlElement body, byte[] bodyBytes) {
        return new ZediMessage(header, Objects.requireNonNull(body, "body"),
                headerBytes, Objects.requireNonNull(bodyBytes, "bodyBytes"));
    }

    /**
     * A message assembled from a mapping.
     *
     * @param header the header to write ahead of the body
     * @param body   the message body
     * @return the message
     */
    public static ZediMessage of(BusinessApplicationHeader header, XmlElement body) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(body, "body");
        return new ZediMessage(header, body,
                XmlSerializer.toBytes(header.toXml()), XmlSerializer.toBytes(body));
    }

    /**
     * The business application header.
     *
     * @return the header, or empty when the body arrived without one
     */
    public Optional<BusinessApplicationHeader> header() {
        return Optional.ofNullable(header);
    }

    /**
     * The message body.
     *
     * @return the body's root element
     */
    public XmlElement body() {
        return body;
    }

    /**
     * What the body is, read from its namespace.
     *
     * @return the message identifier, or empty when the namespace is not an
     *         ISO 20022 one
     */
    public Optional<MessageId> messageId() {
        return MessageId.fromNamespace(body.namespace());
    }

    /**
     * The bytes this message occupies in a file, header segment included.
     *
     * @return the bytes, exactly as read or exactly as they will be written
     */
    public byte[] bytes() {
        if (headerBytes == null) {
            return bodyBytes.clone();
        }
        byte[] joined = Arrays.copyOf(headerBytes, headerBytes.length + bodyBytes.length);
        System.arraycopy(bodyBytes, 0, joined, headerBytes.length, bodyBytes.length);
        return joined;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ZediMessage message
                && Objects.equals(header, message.header)
                && body.equals(message.body)
                && Arrays.equals(headerBytes, message.headerBytes)
                && Arrays.equals(bodyBytes, message.bodyBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, body, Arrays.hashCode(headerBytes), Arrays.hashCode(bodyBytes));
    }

    @Override
    public String toString() {
        return "ZediMessage[" + messageId().map(MessageId::value).orElse(body.name())
                + ", " + bodyBytes.length + " bytes]";
    }
}
