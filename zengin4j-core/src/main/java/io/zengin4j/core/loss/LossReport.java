package io.zengin4j.core.loss;

import module java.base;

/// Everything a conversion changed or discarded.
///
/// Returned alongside the output rather than instead of it: a conversion that
/// lost something still produced a usable result, and the caller decides whether
/// the loss is acceptable. [#isLossless()] answers the common question in
/// one call; [#atLeast] answers "is any of it serious enough to stop for".
///
/// Entries arrive in the order the losses happened, which for a field-by-field
/// conversion is the order a reader would look for them.
///
/// @since 0.4.0
public final class LossReport {

    private static final LossReport EMPTY = new LossReport(List.of());

    private final List<LossEntry> entries;

    /// Creates a report over the given entries.
    ///
    /// @param entries the losses, in the order they occurred
    public LossReport(List<LossEntry> entries) {
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /// A report with nothing in it.
    ///
    /// @return the empty report
    public static LossReport lossless() {
        return EMPTY;
    }

    /// Every entry, in order.
    ///
    /// @return the entries, never `null`
    public List<LossEntry> entries() {
        return entries;
    }

    /// Whether nothing was lost.
    ///
    /// @return `true` if there are no entries
    public boolean isLossless() {
        return entries.isEmpty();
    }

    /// Entries of exactly one severity.
    ///
    /// @param severity the severity to select
    /// @return the matching entries, in order
    public List<LossEntry> bySeverity(LossSeverity severity) {
        Objects.requireNonNull(severity, "severity");
        return entries.stream().filter(entry -> entry.severity() == severity).toList();
    }

    /// Entries at or above a severity.
    ///
    /// @param threshold the lowest severity to include
    /// @return the matching entries, in order
    public List<LossEntry> atLeast(LossSeverity threshold) {
        Objects.requireNonNull(threshold, "threshold");
        return entries.stream().filter(entry -> entry.isAtLeast(threshold)).toList();
    }

    /// Whether anything was lost at or above a severity.
    ///
    /// @param threshold the level to test
    /// @return `true` if any entry meets it
    public boolean hasAtLeast(LossSeverity threshold) {
        return !atLeast(threshold).isEmpty();
    }

    /// Combines this report with another.
    ///
    /// @param other the report to append
    /// @return a report holding both sets of entries, this one's first
    public LossReport and(LossReport other) {
        Objects.requireNonNull(other, "other");
        if (other.isLossless()) {
            return this;
        }
        if (isLossless()) {
            return other;
        }
        List<LossEntry> combined = new ArrayList<>(entries.size() + other.entries.size());
        combined.addAll(entries);
        combined.addAll(other.entries);
        return new LossReport(combined);
    }

    /// A human-readable rendering, one entry per line.
    ///
    /// @return the text, never `null`
    public String toText() {
        if (entries.isEmpty()) {
            return "no loss\n";
        }
        var out = new StringBuilder();
        for (LossEntry entry : entries) {
            out.append(entry.toLine()).append('\n');
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "LossReport[" + entries.size() + " entr" + (entries.size() == 1 ? "y" : "ies") + "]";
    }
}
