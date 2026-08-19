package io.zengin4j.iso20022.mapping;

import module java.base;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.mapping.generated.BundledMappings;

/// The mappings this library ships, looked up by what they connect.
///
/// Immutable and thread-safe, like `FormatRegistry`. There is currently
/// one mapping — 総合振込 to `pain.001.001.03` — and the registry exists
/// anyway, because the alternative is a static reference that every future
/// mapping has to be threaded past.
///
/// @since 0.5.0
public final class MappingRegistry {

    private static final MappingRegistry DEFAULTS = new MappingRegistry(BundledMappings.ALL);

    private final Map<String, List<MappingRow>> mappings;

    private MappingRegistry(Map<String, List<MappingRow>> mappings) {
        this.mappings = Map.copyOf(mappings);
    }

    /// The bundled mappings.
    ///
    /// @return the registry
    public static MappingRegistry defaults() {
        return DEFAULTS;
    }

    /// A registry holding this one's mappings plus another.
    ///
    /// **What this can do.** Make the mapper accept a format id
    /// it otherwise refuses. An institution-specific 総合振込 variant registered
    /// with `FormatRegistry.withFormat` (R-X1) has its own
    /// [FormatId], so `requireRowsFor` would not find the bundled
    /// mapping; registering the bundled rows under that id makes it work, and it
    /// genuinely does work when the variant keeps the field ids the standard
    /// layout uses — which is what a variant is.
    ///
    /// **What this cannot do.** Change how a field is mapped.
    /// The rows are a *declaration*: they say what the mapper does, they
    /// drive the generated reference page, and a test holds the two together.
    /// They are not interpreted at runtime, so editing a row's ISO path here
    /// changes what the mapping claims and not what it does — which would be
    /// worse than not offering the method at all if this paragraph were missing.
    /// See `docs/adr/0035-the-mapping-is-data-not-a-rule-engine.md`.
    ///
    /// To redirect where `EndToEndId` lands — the example R-X4 gives —
    /// use `MappingContext.endToEndPolicy`, which the mapper does act on.
    ///
    /// @param format  the Zengin format
    /// @param message the ISO 20022 message
    /// @param rows    the rows to register
    /// @return a new registry; this one is unchanged
    /// @throws IllegalArgumentException if a mapping for that pair is already
    ///   registered, or `rows` is empty
    public MappingRegistry withMapping(FormatId format, MessageId message,
            List<MappingRow> rows) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("a mapping with no rows declares nothing");
        }

        String key = BundledMappings.key(format, message);
        if (mappings.containsKey(key)) {
            throw new IllegalArgumentException("a mapping for " + key + " is already registered. "
                    + "Start from MappingRegistry.defaults().without(...) if you mean to replace "
                    + "it.");
        }
        Map<String, List<MappingRow>> combined = new LinkedHashMap<>(mappings);
        combined.put(key, List.copyOf(rows));
        return new MappingRegistry(combined);
    }

    /// A registry without a mapping, so that one can be replaced.
    ///
    /// @param format  the Zengin format
    /// @param message the ISO 20022 message
    /// @return a new registry; this one is unchanged
    public MappingRegistry without(FormatId format, MessageId message) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(message, "message");
        Map<String, List<MappingRow>> remaining = new LinkedHashMap<>(mappings);
        remaining.remove(BundledMappings.key(format, message));
        return new MappingRegistry(remaining);
    }

    /// The rows connecting a format to a message.
    ///
    /// @param format  the Zengin format
    /// @param message the ISO 20022 message
    /// @return the rows in declaration order, or empty when no mapping is
    ///   bundled for that pair
    public Optional<List<MappingRow>> rowsFor(FormatId format, MessageId message) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(message, "message");
        return Optional.ofNullable(mappings.get(BundledMappings.key(format, message)));
    }

    /// The rows connecting a format to a message, or a diagnostic naming what is
    /// available.
    ///
    /// @param format  the Zengin format
    /// @param message the ISO 20022 message
    /// @return the rows, in declaration order
    /// @throws UnsupportedMappingException if no mapping is bundled for that pair
    public List<MappingRow> requireRowsFor(FormatId format, MessageId message) {
        return rowsFor(format, message).orElseThrow(() ->
                new UnsupportedMappingException(format, message, mappings.keySet()));
    }

    /// Every pair a mapping is bundled for.
    ///
    /// @return the keys, as `format <-> message`
    public List<String> supported() {
        return mappings.keySet().stream().sorted().toList();
    }

    @Override
    public String toString() {
        return "MappingRegistry" + supported();
    }
}
