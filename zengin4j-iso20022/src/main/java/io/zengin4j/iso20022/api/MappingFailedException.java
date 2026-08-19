package io.zengin4j.iso20022.api;

import module java.base;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.iso20022.loss.MappingLossReport;

/// A conversion lost more than the caller was willing to lose.
///
/// Thrown when the loss report reaches
/// [MappingContext#failOnSeverity()], which defaults to
/// [LossSeverity#CRITICAL] — a payment that could reach the wrong account,
/// or for the wrong amount. The report is attached, so the caller can see
/// exactly what crossed the line.
///
/// @since 0.5.0
public final class MappingFailedException extends ZenginException {

    private final transient MappingLossReport loss;
    private final LossSeverity threshold;

    /// Creates the diagnostic.
    ///
    /// @param threshold the severity that was reached
    /// @param loss      the full report
    public MappingFailedException(LossSeverity threshold, MappingLossReport loss) {
        // Before super(...), which is what Java 25 allows: describeEn reads the
        // report to build the message, so without this the null check fired
        // after the NullPointerException it exists to prevent.
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(loss, "loss");
        super(describeEn(threshold, loss), describeJa(threshold, loss));
        this.threshold = threshold;
        this.loss = loss;
    }

    /// The full loss report, including entries below the threshold.
    ///
    /// @return the report
    public MappingLossReport loss() {
        return loss;
    }

    /// The severity that triggered this.
    ///
    /// @return the threshold
    public LossSeverity threshold() {
        return threshold;
    }

    private static String describeEn(LossSeverity threshold, MappingLossReport loss) {
        int reached = loss.atLeast(threshold).size();
        return "conversion refused: " + reached + " loss "
                + (reached == 1 ? "entry is" : "entries are") + " at or above " + threshold
                + ". Set MappingContext.failOnSeverity to accept it, and read the report first:\n"
                + loss.atLeast(threshold).stream()
                        .map(entry -> "  " + entry.toLine())
                        .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static String describeJa(LossSeverity threshold, MappingLossReport loss) {
        return "変換を中止しました: 重大度 " + threshold + " 以上の損失が "
                + loss.atLeast(threshold).size() + " 件あります。"
                + "許容する場合は MappingContext.failOnSeverity を設定してください。"
                + "その前に損失レポートを確認してください。";
    }
}
