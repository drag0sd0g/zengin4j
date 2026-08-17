package io.zengin4j.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Each rule fires on what it claims to detect, and stays quiet otherwise.
 *
 * <p>The second half matters as much as the first. A rule that fires on a clean
 * file is worse than a rule that never fires: it teaches people to ignore the
 * report, and they then ignore the finding that mattered.
 */
class RuleCatalogueTest {

    private static final SougouFurikomiFixtures KIT = Fixtures.TESTKIT;

    private ValidationReport validate(ZenginFile file) {
        return ZenginValidator.defaults().validate(file);
    }

    /** The control: a well-formed file produces no errors at all. */
    @Test
    void aWellFormedFileProducesNoErrors() {
        ValidationReport report = validate(Fixtures.wellFormedFile());

        assertThat(report.findings(Severity.ERROR))
                .as("a clean file must be clean, or the report is noise")
                .isEmpty();
        assertThat(report.isSubmittable()).isTrue();
    }

    // -------------------------------------------------------- tier 1: V-1xx

    @Test
    void v101_reportsARecordOfTheWrongLength() {
        // Unseparated, so the record boundaries are the record length and
        // nothing else. Dropping two bytes leaves a genuinely short final
        // record rather than merely a missing separator.
        byte[] good = KIT.file(1, SeparatorStyle.NONE, false);
        byte[] shortened = new byte[good.length - 2];
        System.arraycopy(good, 0, shortened, 0, shortened.length);

        ValidationReport report = validate(Fixtures.read(shortened));

        assertThat(report.findingsOf("V-101")).isNotEmpty();
        assertThat(report.findingsOf("V-101").get(0).messageEn())
                .contains("118").contains("120");
    }

    @Test
    void v105_reportsAMissingEndRecord() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data(), KIT.trailer(1, SougouFurikomiFixtures.AMOUNT)),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-105")).hasSize(1);
    }

    @Test
    void v104_reportsABatchWithNoTrailer() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data(), KIT.end()), SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-104")).hasSize(1);
    }

    // -------------------------------------------------------- tier 2: V-2xx

    @Test
    void v201_reportsANonDigitInANumericField() {
        // 振込金額 at offset 80 of the data record, made non-numeric.
        byte[] data = KIT.data();
        data[80] = 'X';
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), data, KIT.trailer(1, 0L), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-201")).isNotEmpty();
        assertThat(report.findingsOf("V-201").get(0).fieldId()).contains("amount");
    }

    @Test
    void v202_reportsACharacterTheFieldMayNotCarry() {
        // The long vowel mark in a party name: the mistake that looks correct.
        byte[] data = KIT.dataUnchecked("ﾔﾏﾀﾞｰﾀﾛｳ", SougouFurikomiFixtures.AMOUNT, "9876543");
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), data,
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-202")).isNotEmpty();
        assertThat(report.findingsOf("V-202").get(0).messageEn()).contains("long vowel mark");
    }

    /** R-K7: a voicing mark after a kana that cannot take one. */
    @Test
    void v206_reportsAnIllegalVoicingMark() {
        // ｱ followed by a dakuten — a byte pair no reader can pronounce.
        byte[] data = KIT.dataUnchecked("ｱﾞﾔﾏﾀﾞ", SougouFurikomiFixtures.AMOUNT, "9876543");
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), data,
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-206")).isNotEmpty();
        assertThat(report.findingsOf("V-206").get(0).messageEn()).contains("dakuten");
    }

    /** And a legal one does not fire — ﾀﾞ is ﾀ plus a mark, and is fine. */
    @Test
    void v206_staysQuietOnALegalVoicingMark() {
        ValidationReport report = validate(Fixtures.wellFormedFile());

        assertThat(report.findingsOf("V-206")).isEmpty();
    }

    // -------------------------------------------------------- tier 3: V-3xx

    @Test
    void v301_reportsATrailerTotalThatDisagreesWithItsBatch() {
        ValidationReport report = validate(Fixtures.fileWithWrongTrailerTotal());

        assertThat(report.findingsOf("V-301")).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.ERROR);
            assertThat(finding.fieldId()).contains("totalAmount");
            assertThat(finding.expectation()).contains(Long.toString(SougouFurikomiFixtures.AMOUNT));
        });
        assertThat(report.isSubmittable()).isFalse();
    }

    @Test
    void v302_reportsATrailerCountThatDisagrees() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data(),
                        KIT.trailer(7, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-302")).singleElement().satisfies(finding ->
                assertThat(finding.actualValue()).contains("7"));
    }

    /** V-304: a total that does not fit the trailer's N(12) field. */
    @Test
    void v304_reportsABatchTotalThatOutgrowsTheTrailerField() {
        // 9,999,999,999 is the N(10) maximum; 200 of them exceed N(12).
        List<byte[]> records = new java.util.ArrayList<>();
        records.add(KIT.header());
        for (int i = 0; i < 200; i++) {
            records.add(KIT.data("ﾔﾏﾀﾞ ﾀﾛｳ", 9_999_999_999L, "9876543"));
        }
        records.add(KIT.trailer(200, 0L));
        records.add(KIT.end());

        ValidationReport report = validate(
                Fixtures.read(SyntheticRecords.file(records, SeparatorStyle.CRLF, false)));

        assertThat(report.findingsOf("V-304")).hasSize(1);
        assertThat(report.findingsOf("V-304").get(0).messageEn()).contains("Split the batch");
        assertThat(report.findingsOf("V-301"))
                .as("the total mismatch is not also reported: the capacity finding supersedes it")
                .isEmpty();
    }

    /** V-306 is a warning, deliberately: duplicates are legal and usually wrong. */
    @Test
    void v306_warnsAboutTwoIdenticalPaymentsInOneBatch() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data(), KIT.data(),
                        KIT.trailer(2, SougouFurikomiFixtures.AMOUNT * 2), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-306")).singleElement().satisfies(finding -> {
            assertThat(finding.severity()).isEqualTo(Severity.WARNING);
            assertThat(finding.messageEn()).contains("identical");
        });
        assertThat(report.isSubmittable()).as("a warning does not block").isTrue();
    }

    @Test
    void v306_staysQuietWhenPaymentsDiffer() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(),
                        KIT.data("ﾔﾏﾀﾞ ﾀﾛｳ", 100L, "9000001"),
                        KIT.data("ﾃｽﾄ ﾊﾅｺ", 100L, "9000002"),
                        KIT.trailer(2, 200L), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-306")).isEmpty();
    }

    // -------------------------------------------------------- tier 6: V-6xx

    @Test
    void v602_warnsAboutAZeroAmount() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data("ﾔﾏﾀﾞ ﾀﾛｳ", 0L, "9876543"),
                        KIT.trailer(1, 0L), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-602")).hasSize(1);
    }

    @Test
    void v603_warnsAboutAnAmountAtTheFieldMaximum() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data("ﾔﾏﾀﾞ ﾀﾛｳ", 9_999_999_999L, "9876543"),
                        KIT.trailer(1, 9_999_999_999L), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-603")).hasSize(1);
        assertThat(report.findingsOf("V-603").get(0).messageEn()).contains("truncated or overflowed");
    }

    @Test
    void v604_warnsAboutABlankPayeeName() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data("", SougouFurikomiFixtures.AMOUNT, "9876543"),
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(Fixtures.read(file));

        assertThat(report.findingsOf("V-604")).isNotEmpty();
    }

    // --------------------------------------------------------------- R-E6

    /** Account numbers are masked in findings unless the caller opts out. */
    @Test
    void accountNumbersAreMaskedInFindingsByDefault() {
        byte[] data = KIT.data("ﾔﾏﾀﾞ ﾀﾛｳ", SougouFurikomiFixtures.AMOUNT, "9876543");
        data[43] = 'X';   // 口座番号 at offset 43: make it non-numeric so V-201 fires
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), data,
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);

        List<Finding> masked = validate(Fixtures.read(file)).findingsOf("V-201");
        assertThat(masked).isNotEmpty();
        assertThat(masked.get(0).actualValue().orElseThrow())
                .as("the account number must not appear in full")
                .startsWith("*")
                .doesNotContain("X876543");

        List<Finding> unmasked = ZenginValidator.builder()
                .unmaskSensitiveValues(true)
                .build()
                .validate(Fixtures.read(file))
                .findingsOf("V-201");
        assertThat(unmasked.get(0).actualValue().orElseThrow()).doesNotStartWith("*");
    }
}
