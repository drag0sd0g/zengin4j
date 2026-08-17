package io.zengin4j.core.loss;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Gathers losses as a conversion runs.
 *
 * <p>Passed into an operation rather than returned from it, so that a
 * conversion made of many small steps produces one report instead of a report
 * per step that the caller has to stitch together.
 *
 * <p><strong>Not thread-safe</strong>, deliberately. A collector belongs to one
 * conversion of one value; sharing one across threads would interleave entries
 * from unrelated conversions and produce a report describing neither.
 *
 * @since 0.4.0
 */
public final class LossCollector {
    private final List<LossEntry> entries = new ArrayList<>();

    /**
     * Creates an empty collector.
     *
     * <p>One per conversion of one value. Sharing one across threads would
     * interleave entries from unrelated conversions into a report describing
     * neither.
     */
    public LossCollector() {
    }

    /**
     * Records a loss.
     *
     * @param entry what was lost
     */
    public void record(LossEntry entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
    }

    /**
     * Whether nothing has been recorded.
     *
     * @return {@code true} if no entry has been recorded
     */
    public boolean isLossless() {
        return entries.isEmpty();
    }

    /**
     * How many entries have been recorded.
     *
     * @return the count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Builds the report.
     *
     * <p>The collector stays usable afterwards; the report is a snapshot.
     *
     * @return the report, never {@code null}
     */
    public LossReport build() {
        return entries.isEmpty() ? LossReport.lossless() : new LossReport(entries);
    }
}
