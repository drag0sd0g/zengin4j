package io.zengin4j.iso20022.xml;

import module java.base;

/// Renders a timestamp the way `xs:dateTime` requires.
///
/// [OffsetDateTime#toString()] omits the seconds when they are zero —
/// `2026-09-01T00:00Z` — and `xs:dateTime` does not allow that. It
/// is a lexical rule with no semantic content, which is why it is easy to get
/// wrong and why nothing but a schema notices: the value round-trips through
/// `OffsetDateTime.parse` perfectly and is still invalid on the wire.
///
/// @since 0.5.0
public final class IsoDateTime {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

    private IsoDateTime() {
    }

    /// Formats a timestamp.
    ///
    /// @param value the timestamp
    /// @return the text, seconds always present
    public static String format(OffsetDateTime value) {
        return FORMAT.format(value);
    }
}
