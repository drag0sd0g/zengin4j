package io.zengin4j.iso20022.envelope;

import module java.base;
import io.zengin4j.iso20022.xml.IsoDateTime;
import io.zengin4j.iso20022.xml.XmlElement;

/// The `head.001` Business Application Header that precedes every message
/// in the profile.
///
/// The header answers routing questions the message body does not: who sent
/// it, who it is for, what it is, and when it was created. In this profile it is
/// a separate XML document concatenated ahead of the body — see
/// [ZediEnvelopeReader] for why that matters more than it sounds.
///
/// **Partial, deliberately.** The profile specifies this header in
/// twenty-four items, and what is modelled here is the five that a converter
/// can fill in: `Fr`, `To`, `BizMsgIdr`, `MsgDefIdr` and
/// `CreDt`. The rest — `BizSvc`, and the centre confirmation codes
/// and password embedded in `Fr` and `To` — are agreed bilaterally
/// between a company and its bank, so no library can derive them from a Zengin
/// file. **What this writes is a placeholder a caller completes, not a
/// submittable header.** See OQ-15 in `docs/OPEN_QUESTIONS.md`.
///
/// A header read from a real file therefore **contains a credential**, which is
/// worth remembering before rendering one in a diagnostic (R-E6).
///
/// @param from                        `Fr` — the sender's identifier
/// @param to                          `To` — the recipient's identifier
/// @param businessMessageIdentifier   `BizMsgIdr` — the sender's reference
///   for this message
/// @param messageDefinitionIdentifier `MsgDefIdr` — what the body is
/// @param creationDate                `CreDt` — when the header was made
/// @since 0.5.0
public record BusinessApplicationHeader(
        String from,
        String to,
        String businessMessageIdentifier,
        MessageId messageDefinitionIdentifier,
        OffsetDateTime creationDate) {

    /// The root element of a `head.001` document.
    public static final String ROOT = "AppHdr";

    /// What an unreadable `CreDt` becomes.
    ///
    /// A real instant rather than [OffsetDateTime#MIN], which renders as
    /// a year with nine digits and looks like memory corruption in a log.
    private static final OffsetDateTime EPOCH =
            java.time.Instant.EPOCH.atOffset(java.time.ZoneOffset.UTC);

    /// Validates the header.
    ///
    /// @throws NullPointerException if any component is null
    public BusinessApplicationHeader {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(businessMessageIdentifier, "businessMessageIdentifier");
        Objects.requireNonNull(messageDefinitionIdentifier, "messageDefinitionIdentifier");
        Objects.requireNonNull(creationDate, "creationDate");
    }

    /// Reads a header from a parsed `head.001` document.
    ///
    /// Absent elements become empty strings rather than a refusal. A header
    /// this library cannot fully read is still a header whose body is worth
    /// mapping, and the mapping reports what it could not use — refusing the
    /// whole file over a missing `To` would be the wrong trade in a
    /// profile no row of which is confirmed.
    ///
    /// @param root the `AppHdr` element
    /// @return the header
    public static BusinessApplicationHeader from(XmlElement root) {
        Objects.requireNonNull(root, "root");
        return new BusinessApplicationHeader(
                identifier(root.at("Fr")),
                identifier(root.at("To")),
                root.textAt("BizMsgIdr").orElse(""),
                root.textAt("MsgDefIdr").map(MessageId::new).orElse(MessageId.PAIN_001_001_03),
                root.textAt("CreDt").flatMap(BusinessApplicationHeader::parseDate)
                        .orElse(EPOCH));
    }

    /// Renders the header as a `head.001` document.
    ///
    /// @return the `AppHdr` element, ready to serialise
    public XmlElement toXml() {
        return XmlElement.element(ROOT)
                .namespace(MessageId.HEAD_001_001_01.namespace())
                .childIfPresent(organisation("Fr", from))
                .childIfPresent(financialInstitution("To", to))
                .textChild("BizMsgIdr", businessMessageIdentifier)
                .textChild("MsgDefIdr", messageDefinitionIdentifier.value())
                .textChild("CreDt", IsoDateTime.formatNormalised(creationDate))
                .build();
    }

    /// `Fr` and `To` nest an identifier several levels down, and the
    /// profile may use either the organisation or the financial-institution
    /// branch. Both are read; whichever is present wins.
    private static String identifier(Optional<XmlElement> party) {
        return party.flatMap(element -> element.textAt("OrgId/Id/OrgId/Othr/Id")
                        .or(() -> element.textAt("FIId/FinInstnId/Othr/Id"))
                        .or(() -> element.textAt("FIId/FinInstnId/BICFI")))
                .orElse("");
    }

    /// The sender, as an organisation.
    ///
    /// `Fr` is the company transmitting the file, so it takes the
    /// organisation branch. In the live profile its value is the sender's
    /// centre confirmation code and a password agreed with the bank — neither
    /// derivable from a Zengin file, which is why what this writes is a
    /// placeholder a caller completes rather than something submittable.
    ///
    /// @param name the element name
    /// @param id   the identifier
    /// @return the element, or nothing when there is no identifier
    private static Optional<XmlElement> organisation(String name, String id) {
        return party(name, id, XmlElement.element("OrgId")
                .child(XmlElement.element("Id")
                        .child(XmlElement.element("OrgId")
                                .child(XmlElement.element("Othr")
                                        .textChild("Id", id)))));
    }

    /// The recipient, as a financial institution.
    ///
    /// `To` names a bank, and the profile addresses one through `FIId`
    /// rather than `OrgId`. Both branches are legal `head.001` and a
    /// schema accepts either, so nothing but the profile itself says which is
    /// meant — and it says this one.
    ///
    /// @param name the element name
    /// @param id   the identifier
    /// @return the element, or nothing when there is no identifier
    private static Optional<XmlElement> financialInstitution(String name, String id) {
        return party(name, id, XmlElement.element("FIId")
                .child(XmlElement.element("FinInstnId")
                        .child(XmlElement.element("Othr")
                                .textChild("Id", id))));
    }

    /// A party element, or nothing.
    ///
    /// `Fr` and `To` are both mandatory in `head.001`, so an
    /// absent one produces a header a schema rejects. That is the intended
    /// behaviour: a missing recipient is a fact about the conversion, and writing
    /// an empty `<Othr/>` to keep the shape would hide it behind something
    /// that looks structurally fine and means nothing.
    private static Optional<XmlElement> party(String name, String id, XmlElement.Builder body) {
        if (id.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(XmlElement.element(name).child(body).build());
    }

    private static Optional<OffsetDateTime> parseDate(String text) {
        return IsoDateTime.parse(text);
    }
}
