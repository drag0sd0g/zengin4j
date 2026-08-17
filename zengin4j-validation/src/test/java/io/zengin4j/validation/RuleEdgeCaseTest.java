package io.zengin4j.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The paths the happy-case tests do not reach: each rule's other branch, and
 * the report's own behaviour.
 *
 * <p>A rule tested only on the input that makes it fire is half tested. The
 * half that matters more in practice is the one that keeps it quiet, because a
 * rule that fires on clean files is how a report stops being read.
 */
class RuleEdgeCaseTest {
    private static final SougouFurikomiFixtures KIT = Fixtures.TESTKIT;

    private ValidationReport validate(byte[] file) {
        return ZenginValidator.defaults().validate(Fixtures.read(file));
    }

    private byte[] fileWith(byte[] data) {
        return SyntheticRecords.file(
                List.of(KIT.header(), data,
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);
    }

    /**
     * A name ending on a kana that takes a voicing mark, filling the field
     * exactly — what truncation through a mark leaves behind. 受取人名 is 30
     * bytes, so a 30-byte name ending in ｶ is the shape.
     */
    @Test
    void v601_warnsAboutANameCutThroughAVoicingMark() {
        String thirtyBytes = "ｱｲｳｴｵｱｲｳｴｵｱｲｳｴｵｱｲｳｴｵｱｲｳｴｵｱｲｳｴｶ";
        assertThat(thirtyBytes).hasSize(30);

        ValidationReport report = validate(fileWith(
                KIT.data(thirtyBytes, SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-601")).isNotEmpty();
    }

    /** A name that stops short of the field end was not truncated. */
    @Test
    void v601_staysQuietWhenTheFieldIsNotFull() {
        ValidationReport report = validate(fileWith(
                KIT.data("ﾔﾏﾀﾞ ﾀﾛｳ", SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-601")).isEmpty();
    }

    /** A full field ending on a kana that takes no mark was not truncated either. */
    @Test
    void v601_staysQuietWhenTheLastKanaTakesNoMark() {
        String endsInN = "ｱｲｳｴｵｱｲｳｴｵｱｲｳｴｵｱｲｳｴｵｱｲｳｴｵｱｲｳｴﾝ";
        assertThat(endsInN).hasSize(30);

        ValidationReport report = validate(fileWith(
                KIT.data(endsInN, SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-601")).isEmpty();
    }

    /** A handakuten may follow only ﾊ-ﾎ; after ｶ it is illegal. */
    @Test
    void v206_reportsAHandakutenAfterAKanaThatCannotTakeOne() {
        ValidationReport report = validate(fileWith(
                KIT.dataUnchecked("ｶﾟﾔﾏ", SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-206")).isNotEmpty();
        assertThat(report.findingsOf("V-206").get(0).messageEn()).contains("handakuten");
    }

    /** ﾊﾟ is legal — ﾊ takes a handakuten. */
    @Test
    void v206_staysQuietOnALegalHandakuten() {
        ValidationReport report = validate(fileWith(
                KIT.data("ﾊﾟﾋﾟﾌﾟ", SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-206")).isEmpty();
    }

    /** ｳﾞ is legal: ｳ is the one vowel that takes a dakuten. */
    @Test
    void v206_acceptsTheVowelThatTakesADakuten() {
        ValidationReport report = validate(fileWith(
                KIT.data("ｳﾞｱｲｵﾘﾝ", SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-206")).isEmpty();
    }

    /** A mark at the very start of a field follows nothing at all. */
    @Test
    void v206_reportsAMarkWithNoBaseAtAll() {
        ValidationReport report = validate(fileWith(
                KIT.dataUnchecked("ﾞﾔﾏﾀﾞ", SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-206")).isNotEmpty();
        assertThat(report.findingsOf("V-206").get(0).messageEn()).contains("start of the field");
    }

    /** A text field pushed right rather than left is padded on the wrong side. */
    @Test
    void v203_warnsAboutAMisalignedTextField() {
        ValidationReport report = validate(fileWith(
                KIT.data("   ﾔﾏﾀﾞ", SougouFurikomiFixtures.AMOUNT, "9876543")));

        assertThat(report.findingsOf("V-203")).isNotEmpty();
    }

    @Test
    void v203_staysQuietOnCorrectlyAlignedFields() {
        ValidationReport report = validate(fileWith(KIT.data()));

        assertThat(report.findingsOf("V-203")).isEmpty();
    }

    /** A record whose first byte names no record kind. */
    @Test
    void v102_reportsAnUnknownDataKubun() {
        byte[] data = KIT.data();
        data[0] = '5';

        ValidationReport report = validate(fileWith(data));

        assertThat(report.findingsOf("V-102")).isNotEmpty();
        assertThat(report.findingsOf("V-102").get(0).messageEn()).contains("'5'");
    }

    /** A code the list does not carry — a warning, because the lists are open. */
    @Test
    void v205_warnsAboutAValueOutsideItsCodeList() {
        byte[] data = KIT.data();
        data[42] = '7';

        ValidationReport report = validate(fileWith(data));

        assertThat(report.findingsOf("V-205")).isNotEmpty();
        assertThat(report.findingsOf("V-205").get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void v305_reportsTwoHeadersWithDifferentBusinessTypes() {
        byte[] second = KIT.header();
        second[1] = '1';
        second[2] = '1';

        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data(),
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                        second, KIT.data(),
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(file);

        assertThat(report.findingsOf("V-305")).hasSize(1);
    }

    @Test
    void v305_staysQuietWhenEveryHeaderAgrees() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data(),
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                        KIT.header(), KIT.data("ﾃｽﾄ ﾊﾅｺ", 100L, "9000009"),
                        KIT.trailer(1, 100L), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(file);

        assertThat(report.findingsOf("V-305")).isEmpty();
    }

    @Test
    void v605_reportsOnceWhenNoRecordUsesCustomerCodes() {
        byte[] data = KIT.data();
        java.util.Arrays.fill(data, 91, 111, (byte) ' ');

        ValidationReport report = validate(fileWith(data));

        assertThat(report.findingsOf("V-605")).hasSize(1);
        assertThat(report.findingsOf("V-605").get(0).severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void v605_staysQuietWhenAnyRecordUsesThem() {
        ValidationReport report = validate(fileWith(KIT.data()));

        assertThat(report.findingsOf("V-605")).isEmpty();
    }

    /** Fail-fast stops after tier 1, so later tiers do not describe misalignment. */
    @Test
    void failFastStopsAfterStructuralErrors() {
        byte[] good = KIT.file(1, SeparatorStyle.NONE, false);
        byte[] shortened = new byte[good.length - 2];
        System.arraycopy(good, 0, shortened, 0, shortened.length);

        ValidationReport all = ZenginValidator.defaults().validate(Fixtures.read(shortened));
        ValidationReport stopped = ZenginValidator.builder()
                .failFast(true)
                .build()
                .validate(Fixtures.read(shortened));

        assertThat(stopped.findingsOf("V-101")).isNotEmpty();
        assertThat(stopped.findings().size())
                .as("fail-fast reports less, not more")
                .isLessThanOrEqualTo(all.findings().size());
        assertThat(stopped.findings())
                .allSatisfy(finding -> assertThat(finding.ruleId()).startsWith("V-1"));
    }

    @Test
    void reportCountsAndFiltersBySeverityAndRule() {
        ValidationReport report = ZenginValidator.defaults()
                .validate(Fixtures.fileWithManyProblems());

        assertThat(report.isClean()).isFalse();
        assertThat(report.counts()).containsKeys(Severity.ERROR, Severity.WARNING, Severity.INFO);
        assertThat(report.findings(Severity.ERROR))
                .allSatisfy(finding -> assertThat(finding.severity()).isEqualTo(Severity.ERROR));
        assertThat(report.findingsOf("V-999")).isEmpty();

        ValidationReport clean = new ValidationReport(List.of());
        assertThat(clean.isClean()).isTrue();
        assertThat(clean.isSubmittable()).isTrue();
        assertThat(clean.toText(Locale.ENGLISH)).isEqualTo("No findings.\n");
        assertThat(clean.toText(Locale.JAPANESE)).contains("指摘事項はありません");
    }

    @Test
    void findingRejectsAnIncompleteConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Finding.of(Severity.ERROR, " ").message("a", "b").build())
                .withMessageContaining("must name the rule");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Finding.of(Severity.ERROR, "V-1").message("a", " ").build())
                .withMessageContaining("both languages");
    }

    /** An override that matches the rule's own severity changes nothing. */
    @Test
    void anOverrideEqualToTheDefaultIsANoOp() {
        ValidationReport report = ZenginValidator.builder()
                .severity("V-301", Severity.ERROR)
                .build()
                .validate(Fixtures.fileWithWrongTrailerTotal());

        assertThat(report.findingsOf("V-301")).singleElement()
                .extracting(Finding::severity).isEqualTo(Severity.ERROR);
    }

    /** Duplicates are per batch: the same payment in two batches is not one. */
    @Test
    void v306_doesNotReportAcrossBatchBoundaries() {
        byte[] file = SyntheticRecords.file(
                List.of(KIT.header(), KIT.data(),
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT),
                        KIT.header(), KIT.data(),
                        KIT.trailer(1, SougouFurikomiFixtures.AMOUNT), KIT.end()),
                SeparatorStyle.CRLF, false);

        ValidationReport report = validate(file);

        assertThat(report.findingsOf("V-306"))
                .as("two batches may legitimately pay the same account")
                .isEmpty();
    }

    /** A filler field is never checked for characters or padding (R-D5). */
    @Test
    void fillerIsNeverPoliced() {
        byte[] data = KIT.data();
        java.util.Arrays.fill(data, 113, 120, (byte) 'a');

        ValidationReport report = validate(fileWith(data));

        assertThat(report.findings())
                .allSatisfy(finding -> assertThat(finding.fieldId().orElse(""))
                        .isNotEqualTo("dummy"));
    }

    /** Findings at the same position order by rule id, so the report is stable. */
    @Test
    void findingsAtOneLocationOrderByRuleId() {
        Finding first = Finding.of(Severity.ERROR, "V-201").at(2, 122).field("a", 10)
                .message("en", "ja").build();
        Finding second = Finding.of(Severity.ERROR, "V-206").at(2, 122).field("a", 10)
                .message("en", "ja").build();

        assertThat(first.compareTo(second)).isNegative();
        assertThat(second.compareTo(first)).isPositive();
        assertThat(first.compareTo(first)).isZero();
    }

    /** A finding with no position sorts after one that has a position. */
    @Test
    void findingsWithoutAPositionSortLast() {
        Finding located = Finding.of(Severity.ERROR, "V-101").at(1, 0).message("en", "ja").build();
        Finding fileWide = Finding.of(Severity.ERROR, "V-107").message("en", "ja").build();

        assertThat(located.compareTo(fileWide)).isNegative();
    }

    @Test
    void findingRendersALineInEitherLanguage() {
        Finding finding = Finding.of(Severity.WARNING, "V-306")
                .at(4, 366)
                .field("amount", 80)
                .message("English text", "日本語のテキスト")
                .build();

        assertThat(finding.toLine(Locale.ENGLISH))
                .contains("WARNING").contains("V-306").contains("record 4")
                .contains("byte 366").contains("[amount]").contains("English text");
        assertThat(finding.toLine(Locale.JAPANESE)).contains("日本語のテキスト");
        assertThat(finding.message(Locale.GERMAN))
                .as("an unknown locale falls back to English rather than to nothing")
                .isEqualTo("English text");
    }
}
