package io.zengin4j.iso20022.pain001;

import io.zengin4j.iso20022.xml.XmlElement;
import java.util.Objects;
import java.util.Optional;

/**
 * A financial institution, identified by its clearing-system membership.
 *
 * <p>Japanese domestic payments identify a bank by its four-digit 銀行番号 and a
 * branch by its three-digit 支店番号 — not by BIC, which most institutions have
 * and which the fixed-length formats never carry.
 *
 * <h2>Why the two codes become one identifier</h2>
 *
 * <p>{@code ClrSysMmbId/MmbId} means "this party's identifier <em>within the
 * named clearing system</em>". Within 全銀システム the thing that identifies a
 * participant is the office, and an office is the bank code followed by the
 * branch code — seven digits. A four-digit bank code identifies an institution,
 * not a participant, so splitting the two across {@code MmbId} and
 * {@code BrnchId} would put something in {@code MmbId} that is not a member
 * identifier.
 *
 * <p>This model therefore holds the two codes separately, because that is what
 * a Zengin record has, and writes them concatenated, because that is what the
 * element means. §15.9 and Q8 both give it as seven digits.
 *
 * <p>The clearing-system identifier that names the scheme is
 * <strong>unconfirmed</strong>. {@code JPZGN} is the plausible candidate and is
 * what this writes; it has not been checked against the ISO 20022 External Code
 * Sets, which is recorded as Q8 in {@code docs/OPEN_QUESTIONS.md} and is why
 * every row of this mapping is {@code verified: false}.
 *
 * @param bankCode   銀行番号, four digits
 * @param branchCode 支店番号, three digits; empty when the file has none
 * @param name       the institution's name, empty when absent
 * @since 0.5.0
 */
public record Agent(String bankCode, String branchCode, String name) {

    /**
     * The clearing system this profile names.
     *
     * <p>Unconfirmed — see the class documentation and Q8.
     */
    public static final String CLEARING_SYSTEM = "JPZGN";

    /** How many digits of a member id belong to the bank. */
    public static final int BANK_CODE_LENGTH = 4;

    /** How many digits belong to the branch. */
    public static final int BRANCH_CODE_LENGTH = 3;

    /**
     * Validates the agent.
     *
     * @throws NullPointerException if any component is null
     */
    public Agent {
        Objects.requireNonNull(bankCode, "bankCode");
        Objects.requireNonNull(branchCode, "branchCode");
        Objects.requireNonNull(name, "name");
    }

    /**
     * The identifier this institution has within the clearing system.
     *
     * @return 銀行番号 followed by 支店番号
     */
    public String memberId() {
        return bankCode + branchCode;
    }

    /**
     * Whether the member id read back would split the way it was written.
     *
     * <p>False only when there is a member id and it is not four digits plus
     * three — which is not a malformed file, only one this mapping cannot take
     * apart. An <em>absent</em> agent splits cleanly by vacuity: there is
     * nothing to take apart, and reporting it as malformed would send a reader
     * looking for a defect in a value that was never there.
     *
     * @return true if there is nothing to split, or it splits as expected
     */
    public boolean splitsCleanly() {
        if (memberId().isEmpty()) {
            return true;
        }
        return bankCode.length() == BANK_CODE_LENGTH
                && (branchCode.isEmpty() || branchCode.length() == BRANCH_CODE_LENGTH);
    }

    /**
     * Reads an agent from its element.
     *
     * <p>A seven-digit member id splits four and three. Anything else is kept
     * whole in {@link #bankCode()}, with no branch — the caller can see that
     * through {@link #splitsCleanly()} and report it rather than guessing where
     * the boundary was.
     *
     * @param element the {@code DbtrAgt} or {@code CdtrAgt} element
     * @return the agent
     */
    public static Agent from(XmlElement element) {
        Objects.requireNonNull(element, "element");
        String member = element.textAt("FinInstnId/ClrSysMmbId/MmbId").orElse("");
        String institutionName = element.textAt("FinInstnId/Nm").orElse("");

        if (member.length() == BANK_CODE_LENGTH + BRANCH_CODE_LENGTH) {
            return new Agent(member.substring(0, BANK_CODE_LENGTH),
                    member.substring(BANK_CODE_LENGTH), institutionName);
        }
        return new Agent(member, "", institutionName);
    }

    /**
     * Renders the agent.
     *
     * @param elementName the element name — {@code DbtrAgt} or {@code CdtrAgt}
     * @return the element, or empty when the agent carries nothing to write
     */
    public Optional<XmlElement> toXml(String elementName) {
        if (memberId().isBlank() && name.isBlank()) {
            return Optional.empty();
        }
        XmlElement.Builder institution = XmlElement.element("FinInstnId");
        if (!memberId().isBlank()) {
            institution.child(XmlElement.element("ClrSysMmbId")
                    .child(XmlElement.element("ClrSysId").textChild("Cd", CLEARING_SYSTEM))
                    .textChild("MmbId", memberId()));
        }
        institution.textChild("Nm", name);

        return Optional.of(XmlElement.element(elementName).child(institution).build());
    }
}
