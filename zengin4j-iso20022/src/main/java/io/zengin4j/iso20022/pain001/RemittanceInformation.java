package io.zengin4j.iso20022.pain001;

import module java.base;
import io.zengin4j.iso20022.xml.XmlElement;

/// A transaction's `RmtInf`, held as the lines it actually contains.
///
/// The profile uses unstructured remittance information for two unrelated
/// purposes: ordinary free text, and the base64-encoded 金融EDI payload
/// ([EdiAttachment]). Modelling it as the raw `Ustrd` list and
/// recognising the attachment on demand keeps both exact — the alternative,
/// parsing eagerly into a typed payload, would have to re-encode on the way out
/// and R-I12 says that changes the bytes.
///
/// @param unstructured the `Ustrd` lines, in document order
/// @since 0.5.0
public record RemittanceInformation(List<String> unstructured) {

    /// Remittance information carrying nothing.
    public static final RemittanceInformation NONE = new RemittanceInformation(List.of());

    /// Validates the remittance information.
    ///
    /// @throws NullPointerException if the list is null
    public RemittanceInformation {
        unstructured = List.copyOf(Objects.requireNonNull(unstructured, "unstructured"));
    }

    /// Remittance information of one line.
    ///
    /// @param line the text, ignored when blank
    /// @return the remittance information
    public static RemittanceInformation of(String line) {
        return line == null || line.isBlank()
                ? NONE : new RemittanceInformation(List.of(line));
    }

    /// Remittance information carrying an encoded attachment.
    ///
    /// @param attachment the attachment
    /// @return the remittance information
    public static RemittanceInformation of(EdiAttachment attachment) {
        Objects.requireNonNull(attachment, "attachment");
        return new RemittanceInformation(attachment.toUnstructured());
    }

    /// Reads remittance information from its element.
    ///
    /// @param element the `RmtInf` element
    /// @return the remittance information
    public static RemittanceInformation from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return new RemittanceInformation(
                element.childrenNamed("Ustrd").stream().map(XmlElement::text).toList());
    }

    /// The encoded 金融EDI payload, when these lines carry one.
    ///
    /// @return the attachment, or empty when the lines are ordinary text
    public Optional<EdiAttachment> ediAttachment() {
        return EdiAttachment.parse(unstructured);
    }

    /// The lines that are not part of an encoded attachment.
    ///
    /// @return the free text, in order
    public List<String> freeText() {
        return ediAttachment().isPresent() ? List.of() : unstructured;
    }

    /// @return true if there is nothing to write
    public boolean isEmpty() {
        return unstructured.isEmpty();
    }

    /// Renders the remittance information.
    ///
    /// @return the `RmtInf` element, or empty when there is nothing to write
    public Optional<XmlElement> toXml() {
        if (isEmpty()) {
            return Optional.empty();
        }
        XmlElement.Builder builder = XmlElement.element("RmtInf");
        unstructured.forEach(line -> builder.child(XmlElement.text("Ustrd", line)));
        return Optional.of(builder.build());
    }
}
