package io.zengin4j.core.error;

/// Helpers for producing diagnostics that are safe to put in a log.
///
/// @since 0.1.0
public final class Diagnostics {

    /// Number of trailing characters left visible when masking (R-E6).
    public static final int VISIBLE_TRAILING_CHARS = 4;

    /// Character substituted for each masked position.
    public static final char MASK_CHAR = '*';

    private Diagnostics() {
    }

    /// Masks an account number or comparable identifier, leaving at most the
    /// last {@value #VISIBLE_TRAILING_CHARS} characters visible (R-E6).
    ///
    /// Values no longer than {@value #VISIBLE_TRAILING_CHARS} characters are
    /// masked completely: showing all of a four-digit value would not be
    /// masking. Full values are available from the record accessors, which is
    /// the explicit opt-in the requirement asks for.
    ///
    /// @param value the identifier to mask, may be `null` or blank
    /// @return the masked identifier; `null` and blank inputs are
    ///   returned unchanged
    public static String maskIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int length = value.length();
        if (length <= VISIBLE_TRAILING_CHARS) {
            return String.valueOf(MASK_CHAR).repeat(length);
        }
        return String.valueOf(MASK_CHAR).repeat(length - VISIBLE_TRAILING_CHARS)
                + value.substring(length - VISIBLE_TRAILING_CHARS);
    }
}
