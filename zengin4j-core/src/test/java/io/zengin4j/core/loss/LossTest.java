package io.zengin4j.core.loss;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// The loss vocabulary, including the severity semantics R-I16 asks to be tested.
///
/// These types are the reason nothing in this library loses information
/// quietly. They arrive in Epic 6 because transliteration needs them, and the
/// mapping layer builds on them rather than inventing a second set — so the
/// meanings are worth pinning now, while there is one caller, rather than later
/// when there are several.
class LossTest {

    private static LossEntry entry(LossKind kind, LossSeverity severity) {
        return LossEntry.of(kind, severity, "before", "after", "what happened", "経緯");
    }

    // ------------------------------------------------------------- severity

    /// R-I16 — the ordering is the semantics.
    ///
    /// Informational is cosmetic, material alters a party or reference
    /// noticeably, critical could move money to the wrong place. Code that asks
    /// "is anything at least material?" depends on that order holding.
    @Test
    void severitiesAreOrderedFromCosmeticToDangerous() {
        assertThat(LossSeverity.values())
                .containsExactly(LossSeverity.INFORMATIONAL, LossSeverity.MATERIAL,
                        LossSeverity.CRITICAL);
    }

    @Test
    void anEntryIsAtLeastItsOwnSeverityAndEverythingBelowIt() {
        LossEntry material = entry(LossKind.TRUNCATED, LossSeverity.MATERIAL);

        assertThat(material.isAtLeast(LossSeverity.INFORMATIONAL)).isTrue();
        assertThat(material.isAtLeast(LossSeverity.MATERIAL)).isTrue();
        assertThat(material.isAtLeast(LossSeverity.CRITICAL)).isFalse();
    }

    @Test
    void criticalMeetsEveryThreshold() {
        LossEntry critical = entry(LossKind.COERCED, LossSeverity.CRITICAL);

        for (LossSeverity threshold : LossSeverity.values()) {
            assertThat(critical.isAtLeast(threshold)).as("%s", threshold).isTrue();
        }
    }

    @Test
    void everyKindTheRequirementNamesExists() {
        assertThat(LossKind.values())
                .as("R-I15")
                .containsExactly(LossKind.TRUNCATED, LossKind.TRANSLITERATED, LossKind.DROPPED,
                        LossKind.DEFAULTED, LossKind.COERCED);
    }

    // ---------------------------------------------------------------- report

    @Test
    void anEmptyReportIsLossless() {
        assertThat(LossReport.lossless().isLossless()).isTrue();
        assertThat(LossReport.lossless().entries()).isEmpty();
        assertThat(LossReport.lossless().toText()).isEqualTo("no loss\n");
    }

    @Test
    void aReportKeepsItsEntriesInTheOrderTheyHappened() {
        LossEntry first = entry(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL);
        LossEntry second = entry(LossKind.TRUNCATED, LossSeverity.MATERIAL);

        LossReport report = new LossReport(List.of(first, second));

        assertThat(report.entries()).containsExactly(first, second);
        assertThat(report.isLossless()).isFalse();
    }

    @Test
    void aReportSelectsBySeverity() {
        LossReport report = new LossReport(List.of(
                entry(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL),
                entry(LossKind.TRUNCATED, LossSeverity.MATERIAL),
                entry(LossKind.DROPPED, LossSeverity.MATERIAL)));

        assertThat(report.bySeverity(LossSeverity.MATERIAL)).hasSize(2);
        assertThat(report.bySeverity(LossSeverity.INFORMATIONAL)).hasSize(1);
        assertThat(report.bySeverity(LossSeverity.CRITICAL)).isEmpty();
    }

    @Test
    void aReportSelectsAtOrAboveASeverity() {
        LossReport report = new LossReport(List.of(
                entry(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL),
                entry(LossKind.TRUNCATED, LossSeverity.MATERIAL),
                entry(LossKind.COERCED, LossSeverity.CRITICAL)));

        assertThat(report.atLeast(LossSeverity.INFORMATIONAL)).hasSize(3);
        assertThat(report.atLeast(LossSeverity.MATERIAL)).hasSize(2);
        assertThat(report.atLeast(LossSeverity.CRITICAL)).hasSize(1);

        assertThat(report.hasAtLeast(LossSeverity.MATERIAL)).isTrue();
        assertThat(LossReport.lossless().hasAtLeast(LossSeverity.INFORMATIONAL)).isFalse();
    }

    @Test
    void reportsCombineInOrder() {
        LossEntry first = entry(LossKind.TRANSLITERATED, LossSeverity.INFORMATIONAL);
        LossEntry second = entry(LossKind.TRUNCATED, LossSeverity.MATERIAL);

        LossReport combined =
                new LossReport(List.of(first)).and(new LossReport(List.of(second)));

        assertThat(combined.entries()).containsExactly(first, second);
    }

    @Test
    void combiningWithAnEmptyReportChangesNothing() {
        LossReport report = new LossReport(List.of(entry(LossKind.DROPPED, LossSeverity.MATERIAL)));

        assertThat(report.and(LossReport.lossless())).isSameAs(report);
        assertThat(LossReport.lossless().and(report)).isSameAs(report);
    }

    @Test
    void aReportRendersOneEntryPerLine() {
        LossReport report = new LossReport(List.of(
                entry(LossKind.TRUNCATED, LossSeverity.MATERIAL),
                entry(LossKind.DROPPED, LossSeverity.INFORMATIONAL)));

        assertThat(report.toText().lines()).hasSize(2);
        assertThat(report.toText()).contains("MATERIAL TRUNCATED", "INFORMATIONAL DROPPED");
    }

    @Test
    void aReportSaysHowManyEntriesItHas() {
        assertThat(LossReport.lossless()).hasToString("LossReport[0 entries]");
        assertThat(new LossReport(List.of(entry(LossKind.DROPPED, LossSeverity.MATERIAL))))
                .hasToString("LossReport[1 entry]");
    }

    @Test
    void aReportDoesNotChangeWhenItsSourceListDoes() {
        List<LossEntry> mutable =
                new java.util.ArrayList<>(List.of(entry(LossKind.DROPPED, LossSeverity.MATERIAL)));
        LossReport report = new LossReport(mutable);

        mutable.clear();

        assertThat(report.entries()).hasSize(1);
    }

    // ------------------------------------------------------------- collector

    @Test
    void aCollectorGathersEntriesAndBuildsAReport() {
        LossCollector collector = new LossCollector();

        assertThat(collector.isLossless()).isTrue();
        assertThat(collector.size()).isZero();

        collector.record(entry(LossKind.TRUNCATED, LossSeverity.MATERIAL));

        assertThat(collector.isLossless()).isFalse();
        assertThat(collector.size()).isEqualTo(1);
        assertThat(collector.build().entries()).hasSize(1);
    }

    @Test
    void aCollectorStaysUsableAfterBuilding() {
        LossCollector collector = new LossCollector();
        collector.record(entry(LossKind.DROPPED, LossSeverity.MATERIAL));

        LossReport snapshot = collector.build();
        collector.record(entry(LossKind.TRUNCATED, LossSeverity.MATERIAL));

        assertThat(snapshot.entries()).as("the report is a snapshot").hasSize(1);
        assertThat(collector.build().entries()).hasSize(2);
    }

    @Test
    void anEmptyCollectorBuildsTheSharedEmptyReport() {
        assertThat(new LossCollector().build().isLossless()).isTrue();
    }

    // ----------------------------------------------------------------- entry

    @Test
    void anEntryCanBeLocatedAfterTheFact() {
        LossEntry located = entry(LossKind.TRUNCATED, LossSeverity.MATERIAL)
                .at("Cdtr/Nm", "beneficiaryName");

        assertThat(located.sourcePath()).contains("Cdtr/Nm");
        assertThat(located.targetField()).contains("beneficiaryName");
        assertThat(located.kind()).isEqualTo(LossKind.TRUNCATED);
        assertThat(located.originalValue()).isEqualTo("before");
    }

    @Test
    void anUnlocatedEntryHasNeitherPath() {
        LossEntry unlocated = entry(LossKind.DROPPED, LossSeverity.MATERIAL);

        assertThat(unlocated.sourcePath()).isEmpty();
        assertThat(unlocated.targetField()).isEmpty();
    }

    @Test
    void anEntryRendersWithItsFieldWhenItHasOne() {
        assertThat(entry(LossKind.TRUNCATED, LossSeverity.MATERIAL).toLine())
                .isEqualTo("MATERIAL TRUNCATED: what happened");
        assertThat(entry(LossKind.TRUNCATED, LossSeverity.MATERIAL)
                .at("a", "beneficiaryName").toLine())
                .contains("[beneficiaryName]");
    }

    @Test
    void anEntryCarriesBothLanguages() {
        LossEntry located = entry(LossKind.TRUNCATED, LossSeverity.MATERIAL);

        assertThat(located.explanationEn()).isEqualTo("what happened");
        assertThat(located.explanationJa()).isEqualTo("経緯");
    }

    @ParameterizedTest
    @EnumSource(LossKind.class)
    void everyKindCanBeRecorded(LossKind kind) {
        LossCollector collector = new LossCollector();

        collector.record(entry(kind, LossSeverity.MATERIAL));

        assertThat(collector.build().entries().get(0).kind()).isEqualTo(kind);
    }

    @Test
    void nullsAreRejectedByName() {
        assertThatNullPointerException()
                .isThrownBy(() -> LossEntry.of(null, LossSeverity.MATERIAL, "a", "b", "c", "d"));
        assertThatNullPointerException()
                .isThrownBy(() -> new LossReport(null));
        assertThatNullPointerException()
                .isThrownBy(() -> new LossCollector().record(null));
        assertThatNullPointerException()
                .isThrownBy(() -> entry(LossKind.DROPPED, LossSeverity.MATERIAL).isAtLeast(null));
    }
}
