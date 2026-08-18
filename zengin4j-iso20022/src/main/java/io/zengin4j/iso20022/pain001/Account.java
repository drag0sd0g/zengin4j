package io.zengin4j.iso20022.pain001;

import io.zengin4j.iso20022.xml.XmlElement;
import java.util.Objects;
import java.util.Optional;

/**
 * A cash account, identified the way a domestic Japanese account is.
 *
 * <p>ISO 20022 prefers an IBAN and falls back to {@code Othr/Id}. Japan has no
 * IBAN, so the account number goes in {@code Othr/Id} and 預金種目 — ordinary,
 * current, savings — goes in {@code Tp/Prtry}, because the account-type code
 * list in the standard has no equivalent for it. Proprietary is the correct
 * place for a code the standard does not define; it is also the reason a
 * receiving system that does not know this profile cannot interpret it.
 *
 * @param number          {@code Id/Othr/Id} — the account number
 * @param proprietaryType {@code Tp/Prtry} — 預金種目 as its Zengin code, empty
 *                        when unknown
 * @since 0.5.0
 */
public record Account(String number, String proprietaryType) {

    /**
     * Validates the account.
     *
     * @throws NullPointerException if either component is null
     */
    public Account {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(proprietaryType, "proprietaryType");
    }

    /**
     * Reads an account from its element.
     *
     * @param element the {@code DbtrAcct} or {@code CdtrAcct} element
     * @return the account
     */
    public static Account from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        return new Account(
                element.textAt("Id/Othr/Id").orElse(""),
                element.textAt("Tp/Prtry").orElse(""));
    }

    /**
     * Renders the account.
     *
     * @param name the element name — {@code DbtrAcct} or {@code CdtrAcct}
     * @return the element, or empty when the account carries nothing to write
     */
    public Optional<XmlElement> toXml(String name) {
        if (number.isBlank() && proprietaryType.isBlank()) {
            return Optional.empty();
        }
        XmlElement.Builder builder = XmlElement.element(name);
        if (!number.isBlank()) {
            builder.child(XmlElement.element("Id")
                    .child(XmlElement.element("Othr").textChild("Id", number)));
        }
        if (!proprietaryType.isBlank()) {
            builder.child(XmlElement.element("Tp").textChild("Prtry", proprietaryType));
        }
        return Optional.of(builder.build());
    }
}
