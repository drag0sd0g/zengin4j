package io.zengin4j.iso20022.pain001;

import module java.base;
import io.zengin4j.iso20022.xml.XmlElement;

/// A financial institution, identified by its clearing-system membership.
///
/// Japanese domestic payments identify a bank by its four-digit 銀行番号 and a
/// branch by its three-digit 支店番号 — not by BIC, which most institutions have
/// and which the fixed-length formats never carry.
///
/// ## Where each code goes
///
/// `ClrSysMmbId/MmbId` carries 銀行番号 alone, four digits, and 支店番号 goes in
/// `FinInstnId/BrnchId/Id`, three digits. Both the ZEDI profile and an
/// independent bank's own specification give it that way, on the debtor and
/// creditor sides alike.
///
/// This was previously written as one seven-digit `MmbId` with `BrnchId`
/// unused, reasoning that a 全銀システム participant is an office rather than an
/// institution and so the member identifier must name the office. That is a
/// defensible reading of what `MmbId` means, and it is not what the profile
/// does — see D-004 in `docs/DISCREPANCIES.md`.
///
/// The clearing system is named by `JPZGN`, which the profile fixes as a
/// constant.
///
/// @param bankCode   銀行番号, four digits
/// @param branchCode 支店番号, three digits; empty when the file has none
/// @param name       the institution's name, empty when absent
/// @since 0.5.0
public record Agent(String bankCode, String branchCode, String name) {

    /// The clearing system this profile names.
    public static final String CLEARING_SYSTEM = "JPZGN";

    /// How many digits 銀行番号 has.
    public static final int BANK_CODE_LENGTH = 4;

    /// How many digits 支店番号 has.
    public static final int BRANCH_CODE_LENGTH = 3;

    /// Validates the agent.
    ///
    /// @throws NullPointerException if any component is null
    public Agent {
        Objects.requireNonNull(bankCode, "bankCode");
        Objects.requireNonNull(branchCode, "branchCode");
        Objects.requireNonNull(name, "name");
    }

    /// Whether this agent identifies anything at all.
    ///
    /// @return true when neither code nor name is present
    public boolean isEmpty() {
        return bankCode.isBlank() && branchCode.isBlank() && name.isBlank();
    }

    /// Reads an agent from its element.
    ///
    /// Reads the profile's shape, and also the seven-digit `MmbId` this
    /// library used to write — otherwise files it produced before that was
    /// corrected would stop being readable by it. An explicit `BrnchId/Id`
    /// always wins over anything inferred from a long member id.
    ///
    /// @param element the `DbtrAgt` or `CdtrAgt` element
    /// @return the agent
    public static Agent from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        String member = element.textAt("FinInstnId/ClrSysMmbId/MmbId").orElse("");
        String branch = element.textAt("FinInstnId/BrnchId/Id").orElse("");
        String institutionName = element.textAt("FinInstnId/Nm").orElse("");

        if (branch.isEmpty() && member.length() == BANK_CODE_LENGTH + BRANCH_CODE_LENGTH) {
            return new Agent(member.substring(0, BANK_CODE_LENGTH),
                    member.substring(BANK_CODE_LENGTH), institutionName);
        }
        return new Agent(member, branch, institutionName);
    }

    /// Renders the agent.
    ///
    /// @param elementName the element name — `DbtrAgt` or `CdtrAgt`
    /// @return the element, or empty when the agent carries nothing to write
    public Optional<XmlElement> toXml(String elementName) {
        if (isEmpty()) {
            return Optional.empty();
        }
        XmlElement.Builder institution = XmlElement.element("FinInstnId");
        if (!bankCode.isBlank()) {
            institution.child(XmlElement.element("ClrSysMmbId")
                    .child(XmlElement.element("ClrSysId").textChild("Cd", CLEARING_SYSTEM))
                    .textChild("MmbId", bankCode));
        }
        institution.textChild("Nm", name);
        if (!branchCode.isBlank()) {
            institution.child(XmlElement.element("BrnchId").textChild("Id", branchCode));
        }

        return Optional.of(XmlElement.element(elementName).child(institution).build());
    }
}
