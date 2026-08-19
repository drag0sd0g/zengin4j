package io.zengin4j.iso20022.pain001;

import module java.base;
import io.zengin4j.iso20022.xml.XmlElement;

/// A party to the payment — the initiating party, the debtor or the creditor.
///
/// `PartyIdentification32` carries a postal address, contact details
/// and several identification schemes. None of them survives a conversion to a
/// fixed-length record, which has room for a name and nothing else, so this
/// model keeps the two components the mapping can actually use and the loss
/// report says what was dropped.
///
/// @param name       `Nm` — up to 140 characters, any script
/// @param identifier `Id/OrgId/Othr/Id` — the organisation identifier,
///   empty when the party carries none
/// @since 0.5.0
public record Party(String name, String identifier) {

    /// Validates the party.
    ///
    /// @throws NullPointerException if either component is null
    public Party {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(identifier, "identifier");
    }

    /// A party with a name and no identifier.
    ///
    /// @param name the party's name
    /// @return the party
    public static Party named(String name) {
        return new Party(name, "");
    }

    /// Reads a party from its element.
    ///
    /// @param element the `InitgPty`, `Dbtr` or `Cdtr` element
    /// @return the party
    public static Party from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return new Party(
                element.textAt("Nm").orElse(""),
                element.textAt("Id/OrgId/Othr/Id").orElse(""));
    }

    /// Renders the party.
    ///
    /// @param name the element name to use — `InitgPty`, `Dbtr` or
    ///   `Cdtr`
    /// @return the element, or empty when the party carries nothing to write
    public Optional<XmlElement> toXml(String name) {
        XmlElement.Builder builder = XmlElement.element(name).textChild("Nm", this.name);
        if (!identifier.isBlank()) {
            builder.child(XmlElement.element("Id")
                    .child(XmlElement.element("OrgId")
                            .child(XmlElement.element("Othr").textChild("Id", identifier))));
        }
        return builder.isEmpty() ? Optional.empty() : Optional.of(builder.build());
    }
}
