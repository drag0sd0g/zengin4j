package io.zengin4j.core.charset;

import io.zengin4j.core.error.CharsetUnavailableException;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * The character encodings a Zengin file may be written in (R-C11).
 *
 * <p>{@link #MS932} is the default because it is what Windows-based Japanese
 * accounting systems emit in practice, not because it is the most standard
 * choice. {@code MS932} and {@link #SHIFT_JIS} differ in the NEC/IBM extension
 * ranges and in the mapping of the wave dash; the divergence is documented in
 * {@code docs/encoding.md}.
 *
 * <p><strong>{@link #UTF_8} breaks the format's central assumption.</strong>
 * Every character permitted in a Zengin field occupies exactly one byte under
 * the Japanese encodings, which is why the source specifications use 桁
 * (characters) and バイト (bytes) interchangeably. In UTF-8 a half-width
 * katakana character occupies three bytes, so a 30-byte name field holds ten
 * characters rather than thirty. The option exists because such files are
 * produced by mis-configured tooling and users need to read them; it is not a
 * supported interchange encoding.
 *
 * @since 0.1.0
 */
public enum ZenginCharset {
    /**
     * Shift_JIS as standardised, without the Microsoft extensions.
     */
    SHIFT_JIS("Shift_JIS"),

    /**
     * CP932, Microsoft's Shift_JIS superset including the NEC and IBM
     * extension characters. The default (R-C11).
     */
    MS932("windows-31j"),

    /**
     * UTF-8. Multi-byte for every katakana character; see the type
     * documentation before choosing it.
     */
    UTF_8("UTF-8");

    private final String charsetName;
    private final Charset charset;
    private final RuntimeException lookupFailure;

    ZenginCharset(String charsetName) {
        this.charsetName = charsetName;
        Charset resolved = null;
        RuntimeException failure = null;
        try {
            resolved = Charset.forName(charsetName);
        } catch (RuntimeException e) {
            failure = e;
        }
        this.charset = resolved;
        this.lookupFailure = failure;
    }

    /**
     * Returns the encoding used when the caller does not choose one (R-C11).
     *
     * @return {@link #MS932}
     */
    public static ZenginCharset defaultCharset() {
        return MS932;
    }

    /**
     * Returns the JDK charset name this constant resolves to.
     *
     * @return the JDK charset name, never {@code null}
     */
    public String charsetName() {
        return charsetName;
    }

    /**
     * Returns the resolved JDK charset.
     *
     * @return the charset, never {@code null}
     * @throws CharsetUnavailableException if this JVM does not provide it
     */
    public Charset charset() {
        if (charset == null) {
            throw new CharsetUnavailableException(charsetName, lookupFailure);
        }
        return charset;
    }

    /**
     * Decodes a byte range as text.
     *
     * @param buffer the source buffer, never {@code null}
     * @param offset start offset within the buffer
     * @param length number of <strong>bytes</strong> to decode (R-C15)
     * @return the decoded text, never {@code null}
     * @throws CharsetUnavailableException     if this JVM does not provide the
     *                                         charset
     * @throws IndexOutOfBoundsException       if the range is outside the
     *                                         buffer
     */
    public String decode(byte[] buffer, int offset, int length) {
        Objects.requireNonNull(buffer, "buffer");
        return new String(buffer, offset, length, charset());
    }

    /**
     * Encodes text to bytes.
     *
     * @param text the text to encode, never {@code null}
     * @return the encoded bytes, never {@code null}
     * @throws CharsetUnavailableException if this JVM does not provide the
     *                                     charset
     */
    public byte[] encode(String text) {
        Objects.requireNonNull(text, "text");
        return text.getBytes(charset());
    }
}
