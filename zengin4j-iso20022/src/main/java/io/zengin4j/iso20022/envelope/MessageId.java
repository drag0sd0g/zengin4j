package io.zengin4j.iso20022.envelope;

import module java.base;

/// An ISO 20022 message identifier, as it appears in a namespace URI.
///
/// `pain.001.001.03` names a message *and* a version, and the
/// two cannot be separated: the profile pins the version (R-I3), so a document
/// declaring `pain.001.001.09` is not a newer dialect of something this
/// library understands, it is a different message. Keeping the identifier whole
/// makes that impossible to blur.
///
/// @param value the identifier, e.g. `pain.001.001.03`
/// @since 0.5.0
public record MessageId(String value) {

    private static final String NAMESPACE_PREFIX = "urn:iso:std:iso:20022:tech:xsd:";

    /// The one credit-transfer message this library maps (R-I3).
    public static final MessageId PAIN_001_001_03 = new MessageId("pain.001.001.03");

    /// The business application header the profile wraps every message in.
    public static final MessageId HEAD_001_001_01 = new MessageId("head.001.001.01");

    /// Validates the identifier.
    ///
    /// @throws NullPointerException     if `value` is null
    /// @throws IllegalArgumentException if `value` is blank
    public MessageId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a message identifier cannot be blank");
        }
    }

    /// Reads the identifier out of a namespace URI.
    ///
    /// @param namespace the namespace URI
    /// @return the identifier, or empty when the URI is not an ISO 20022 one
    public static Optional<MessageId> fromNamespace(String namespace) {
        if (namespace == null || !namespace.startsWith(NAMESPACE_PREFIX)) {
            return Optional.empty();
        }
        String identifier = namespace.substring(NAMESPACE_PREFIX.length());
        return identifier.isBlank() ? Optional.empty() : Optional.of(new MessageId(identifier));
    }

    /// The namespace URI a document with this identifier declares.
    ///
    /// @return the namespace URI
    public String namespace() {
        return NAMESPACE_PREFIX + value;
    }

    /// The message name without its version — `pain.001` for
    /// `pain.001.001.03`.
    ///
    /// @return the family name
    public String family() {
        String[] parts = value.split("\\.");
        return parts.length < 2 ? value : parts[0] + "." + parts[1];
    }

    @Override
    public String toString() {
        return value;
    }
}
