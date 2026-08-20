package io.zengin4j.iso20022.xml;

import module java.base;

/// Renders and reads the two timestamp shapes the profile uses.
///
/// ISO 20022 has two date-time representations and this profile uses one of
/// each, in the two places a file carries a timestamp. They are not
/// interchangeable, and a single formatter cannot satisfy both:
///
/// - `ISODateTime`, in `pain.001`'s `GrpHdr/CreDtTm`, carries
///   **no UTC offset at all** — nineteen characters, or twenty-three with
///   milliseconds.
/// - `ISONormalisedDateTime`, in the business application header's
///   `CreDt`, is normalised to UTC and **must end in `Z`**. Its
///   schema type carries a pattern facet saying so, so an offset like
///   `+09:00` is not merely unconventional there; it fails validation.
///
/// [OffsetDateTime#toString()] satisfies neither. It omits the seconds when
/// they are zero — `2026-09-01T00:00Z` — which `xs:dateTime` does not
/// allow, and it writes whatever offset it was given. Both are lexical rules
/// with no semantic content, which is why they are easy to get wrong and why
/// nothing but a schema notices: the value round-trips through
/// `OffsetDateTime.parse` perfectly and is still wrong on the wire.
///
/// @since 0.5.0
public final class IsoDateTime {

    /// `ISODateTime`: no offset, seconds always present.
    private static final DateTimeFormatter LOCAL =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    /// `ISONormalisedDateTime`: UTC, with the literal `Z` its pattern facet
    /// requires.
    private static final DateTimeFormatter NORMALISED =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");

    /// Reads either shape, and anything else `xs:dateTime` permits.
    ///
    /// The offset is optional because the shape this library writes for
    /// `CreDtTm` does not carry one, so its own output has to be readable.
    private static final DateTimeFormatter EITHER = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart()
            .appendOffsetId()
            .optionalEnd()
            .toFormatter(Locale.ROOT);

    private IsoDateTime() {
    }

    /// Formats a timestamp as `ISODateTime` — no offset.
    ///
    /// The offset is dropped rather than converted, so the wall-clock reading
    /// is the one the caller meant. A file's creation time is metadata about
    /// the sender, not an instant anything reconciles against.
    ///
    /// @param value the timestamp
    /// @return the text, seconds always present and no offset
    public static String format(OffsetDateTime value) {
        Objects.requireNonNull(value, "value");
        return LOCAL.format(value);
    }

    /// Formats a timestamp as `ISONormalisedDateTime` — UTC, ending in `Z`.
    ///
    /// The instant is preserved and the offset is converted, which is what
    /// "normalised" means: the same moment, stated the one way the type allows.
    ///
    /// @param value the timestamp
    /// @return the text, seconds always present and UTC
    public static String formatNormalised(OffsetDateTime value) {
        Objects.requireNonNull(value, "value");
        return NORMALISED.format(value.withOffsetSameInstant(ZoneOffset.UTC));
    }

    /// Reads a timestamp written in either shape.
    ///
    /// A value with no offset is read as UTC. That is a choice rather than a
    /// fact — the shape carries no zone, so none can be recovered — and it is
    /// the choice that makes the text survive a round trip, because
    /// [#format(OffsetDateTime)] writes the wall clock back unchanged.
    ///
    /// @param text the text, which may be anything at all
    /// @return the timestamp, or empty when the text is not one
    public static Optional<OffsetDateTime> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            TemporalAccessor parsed = EITHER.parse(text.trim());
            return Optional.of(parsed.isSupported(ChronoField.OFFSET_SECONDS)
                    ? OffsetDateTime.from(parsed)
                    : LocalDateTime.from(parsed).atOffset(ZoneOffset.UTC));
        } catch (DateTimeException notATimestamp) {
            return Optional.empty();
        }
    }
}
