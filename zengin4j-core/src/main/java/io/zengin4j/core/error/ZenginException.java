package io.zengin4j.core.error;

import module java.base;

/// Base of every exception thrown by zengin4j.
///
/// Exceptions in this library signal *programmer error* or an
/// unrecoverable I/O condition (R-E1). They do not signal malformed input:
/// a third-party file that violates the format is expected input, and is
/// surfaced as a
/// [MalformedRecord][io.zengin4j.core.model.MalformedRecord] in lenient
/// mode or as a validation finding, never as an exception the caller must
/// catch to keep going.
///
/// All exceptions are unchecked (R-E2) and carry both an English and a
/// Japanese message (R-E4). [#getMessage()] always returns the English
/// text so that logs are stable across deployments;
/// [#getLocalizedMessage()] returns Japanese when the default locale is
/// Japanese.
///
/// Message text is currently constructed at the throw site. Epic 4 moves it
/// into `ResourceBundle`s so that translations become reviewable
/// (R-E4, issue 4.8); the accessors on this type are the contract that makes
/// that move invisible to callers.
///
/// @since 0.1.0
public abstract class ZenginException extends RuntimeException {

    private final String messageEn;
    private final String messageJa;

    /// Creates an exception with both message variants.
    ///
    /// @param messageEn the English diagnostic, never `null`
    /// @param messageJa the Japanese diagnostic, never `null`
    protected ZenginException(String messageEn, String messageJa) {
        this(messageEn, messageJa, null);
    }

    /// Creates an exception with both message variants and an underlying cause.
    ///
    /// @param messageEn the English diagnostic, never `null`
    /// @param messageJa the Japanese diagnostic, never `null`
    /// @param cause     the underlying cause, may be `null`
    protected ZenginException(String messageEn, String messageJa, Throwable cause) {
        super(Objects.requireNonNull(messageEn, "messageEn"), cause);
        this.messageEn = messageEn;
        this.messageJa = Objects.requireNonNull(messageJa, "messageJa");
    }

    /// Returns the English diagnostic.
    ///
    /// @return the English diagnostic, never `null`
    public final String messageEn() {
        return messageEn;
    }

    /// Returns the Japanese diagnostic.
    ///
    /// @return the Japanese diagnostic, never `null`
    public final String messageJa() {
        return messageJa;
    }

    /// Returns the diagnostic in the default locale's language: Japanese when
    /// the default locale's language is Japanese, English otherwise.
    ///
    /// @return the localised diagnostic, never `null`
    @Override
    public String getLocalizedMessage() {
        return Locale.JAPANESE.getLanguage().equals(Locale.getDefault().getLanguage())
                ? messageJa
                : messageEn;
    }
}
