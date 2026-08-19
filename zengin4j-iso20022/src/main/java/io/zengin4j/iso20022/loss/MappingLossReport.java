package io.zengin4j.iso20022.loss;

import module java.base;
import io.zengin4j.core.loss.LossEntry;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossReport;
import io.zengin4j.core.loss.LossSeverity;

/// What a conversion could not carry across.
///
/// Every conversion returns one of these alongside its output, and there is
/// no API that returns the output alone (R-I14). That is the single most
/// important design decision in this module: the formats are not isomorphic, so
/// every conversion loses something, and an API that let a caller ignore the
/// report would let them believe otherwise.
///
/// This adds two things to the [LossReport] the transliteration engine
/// already produces: a Japanese rendering, and JSON for a pipeline to branch on.
/// The vocabulary itself — [LossKind], [LossSeverity],
/// [LossEntry] — lives in `core`, because transliteration on the
/// write path needs it too (ADR-0029), and two sets of loss types that meant
/// almost the same thing would be worse than one in a slightly odd place.
///
/// @since 0.5.0
public final class MappingLossReport {

    private static final MappingLossReport LOSSLESS =
            new MappingLossReport(LossReport.lossless());

    private final LossReport report;

    private MappingLossReport(LossReport report) {
        this.report = report;
    }

    /// Wraps a report from the transliteration engine.
    ///
    /// @param report the report
    /// @return the mapping report
    public static MappingLossReport of(LossReport report) {
        Objects.requireNonNull(report, "report");
        return report.isLossless() ? LOSSLESS : new MappingLossReport(report);
    }

    /// A report of no loss.
    ///
    /// @return the shared empty report
    public static MappingLossReport lossless() {
        return LOSSLESS;
    }

    /// @return the entries, in the order they happened
    public List<LossEntry> entries() {
        return report.entries();
    }

    /// @return true if nothing was lost
    public boolean isLossless() {
        return report.isLossless();
    }

    /// The entries of exactly one severity.
    ///
    /// @param severity the severity
    /// @return the entries
    public List<LossEntry> bySeverity(LossSeverity severity) {
        return report.bySeverity(severity);
    }

    /// The entries at or above a severity.
    ///
    /// @param threshold the lowest severity to include
    /// @return the entries
    public List<LossEntry> atLeast(LossSeverity threshold) {
        return report.atLeast(threshold);
    }

    /// Whether anything reached a severity.
    ///
    /// @param threshold the severity to test
    /// @return true if any entry is at or above it
    public boolean hasAtLeast(LossSeverity threshold) {
        return report.hasAtLeast(threshold);
    }

    /// Combines two reports, keeping order.
    ///
    /// This is what `roundTrip` uses to accumulate loss across both
    /// legs (R-I18).
    ///
    /// @param other the report to append
    /// @return the combined report
    public MappingLossReport and(MappingLossReport other) {
        Objects.requireNonNull(other, "other");
        if (other.isLossless()) {
            return this;
        }
        if (isLossless()) {
            return other;
        }
        return of(report.and(other.report));
    }

    /// The underlying report.
    ///
    /// @return the report, for code that works across the write path and the
    ///   mapping path
    public LossReport toLossReport() {
        return report;
    }

    /// A human-readable rendering, one entry per line, in English.
    ///
    /// @return the text
    public String toText() {
        return report.toText();
    }

    /// A human-readable rendering, one entry per line.
    ///
    /// @param locale Japanese for the Japanese explanations, anything else for
    ///   English
    /// @return the text
    public String toText(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        if (!Locale.JAPANESE.getLanguage().equals(locale.getLanguage())) {
            return toText();
        }
        if (report.isLossless()) {
            return "損失なし\n";
        }
        StringBuilder out = new StringBuilder();
        for (LossEntry entry : report.entries()) {
            out.append(entry.severity()).append(' ').append(entry.kind());
            entry.targetField().ifPresent(field -> out.append(" [").append(field).append(']'));
            out.append(": ").append(entry.explanationJa()).append('\n');
        }
        return out.toString();
    }

    /// The report as JSON, for a pipeline that branches on it.
    ///
    /// Written by hand, like the validation module's JSON and SARIF and for
    /// the same reason (ADR-0022): the structure is fixed and shallow, and the
    /// alternative is a dependency in a module whose value is partly that it has
    /// none. The escaping is checked by parsing the output with a real parser in
    /// the tests.
    ///
    /// @return the JSON text
    public String toJson() {
        StringBuilder out = new StringBuilder("{\n");
        out.append("  \"lossless\": ").append(report.isLossless()).append(",\n");
        out.append("  \"entries\": [");
        for (int i = 0; i < report.entries().size(); i++) {
            out.append(i == 0 ? "\n" : ",\n");
            appendEntry(report.entries().get(i), out);
        }
        out.append(report.entries().isEmpty() ? "]\n" : "\n  ]\n");
        return out.append("}\n").toString();
    }

    private static void appendEntry(LossEntry entry, StringBuilder out) {
        out.append("    {\n");
        field(out, "kind", entry.kind().name(), true);
        field(out, "severity", entry.severity().name(), true);
        entry.sourcePath().ifPresent(path -> field(out, "sourcePath", path, true));
        entry.targetField().ifPresent(field -> field(out, "targetField", field, true));
        field(out, "originalValue", entry.originalValue(), true);
        field(out, "resultingValue", entry.resultingValue(), true);
        field(out, "explanationEn", entry.explanationEn(), true);
        field(out, "explanationJa", entry.explanationJa(), false);
        out.append("\n    }");
    }

    private static void field(StringBuilder out, String name, String value, boolean more) {
        out.append("      \"").append(name).append("\": \"").append(escape(value)).append('"');
        if (more) {
            out.append(",\n");
        }
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "MappingLossReport[" + report.entries().size()
                + (report.entries().size() == 1 ? " entry]" : " entries]");
    }
}
