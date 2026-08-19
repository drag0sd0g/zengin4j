package io.zengin4j.iso20022.xml;

import io.zengin4j.core.error.ZenginException;

/// The bytes are not XML this module will parse.
///
/// Either they are not well-formed, or they exceed a limit the parser imposes
/// on untrusted input — see [XmlParser] for what those limits are and why
/// a size bound is a correctness property here rather than a tuning knob.
///
/// This is the one place the library's usual rule bends. Elsewhere a
/// malformed third-party file becomes data rather than an exception, because a
/// fixed-length file that breaks in record 400 still has 399 readable records.
/// XML has no such property: a document that is not well-formed has no prefix
/// that means anything, so there is nothing to hand back.
///
/// @since 0.5.0
public final class MalformedXmlException extends ZenginException {

    private final long byteOffset;

    /// Creates a diagnostic.
    ///
    /// @param byteOffset the byte offset at which parsing stopped, or `-1`
    ///   when the parser could not say
    /// @param messageEn  the English diagnostic
    /// @param messageJa  the Japanese diagnostic
    /// @param cause      the underlying parse failure, may be `null`
    public MalformedXmlException(long byteOffset, String messageEn, String messageJa,
            Throwable cause) {
        super(byteOffset < 0 ? messageEn : "byte " + byteOffset + ": " + messageEn,
                byteOffset < 0 ? messageJa : byteOffset + " バイト目: " + messageJa,
                cause);
        this.byteOffset = byteOffset;
    }

    /// Returns the byte offset at which parsing stopped.
    ///
    /// @return the offset, or `-1` if unknown
    public long byteOffset() {
        return byteOffset;
    }
}
