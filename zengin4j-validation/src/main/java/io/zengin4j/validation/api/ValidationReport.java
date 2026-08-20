package io.zengin4j.validation.api;

import module java.base;

/// What validation found (R-V1).
///
/// Returned, never thrown. A file with two hundred errors produces a report
/// with two hundred findings, not an exception about the first one — the whole
/// point is to see all of it at once and fix it in one pass.
///
/// Findings arrive canonically ordered (INV-7): the same file always produces
/// the same report, byte for byte, which is what lets a report be committed as a
/// fixture or diffed between runs.
///
/// @since 0.2.0
public final class ValidationReport {

    private final List<Finding> findings;
    private final Map<Severity, Integer> counts;
    private final List<Rule> rules;

    /// Creates a report from findings that are already ordered.
    ///
    /// @param findings the findings
    public ValidationReport(List<Finding> findings) {
        this(findings, List.of());
    }

    /// Creates a report that also knows which rules produced it.
    ///
    /// SARIF describes the rules a run used, not just its results, so the
    /// report carries them rather than reaching for a global registry — a
    /// caller who validated with their own rule set gets a document describing
    /// *their* rules.
    ///
    /// @param findings the findings
    /// @param rules    the rules that ran
    public ValidationReport(List<Finding> findings, List<Rule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        this.findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        Map<Severity, Integer> tally = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            tally.put(severity, 0);
        }
        for (Finding finding : this.findings) {
            tally.merge(finding.severity(), 1, Integer::sum);
        }
        this.counts = Map.copyOf(tally);
    }

    /// Every finding, in canonical order.
    ///
    /// @return the findings, never `null`
    public List<Finding> findings() {
        return findings;
    }

    /// How many findings of each severity.
    ///
    /// @return the counts, with an entry for every severity even when zero
    public Map<Severity, Integer> counts() {
        return counts;
    }

    /// Findings of one severity.
    ///
    /// @param severity the severity to filter by
    /// @return the matching findings, in canonical order
    public List<Finding> findings(Severity severity) {
        return findings.stream().filter(finding -> finding.severity() == severity).toList();
    }

    /// Findings from one rule.
    ///
    /// @param ruleId the rule id
    /// @return the matching findings, in canonical order
    public List<Finding> findingsOf(String ruleId) {
        return findings.stream().filter(finding -> finding.ruleId().equals(ruleId)).toList();
    }

    /// Whether the file is fit to send.
    ///
    /// Defined on errors alone. Warnings are things worth looking at that
    /// institutions accept — a report that blocked on them would be a report
    /// people learn to override, which is worse than one that does not block.
    ///
    /// @return `true` if there are no errors
    public boolean isSubmittable() {
        return counts.getOrDefault(Severity.ERROR, 0) == 0;
    }

    /// Whether anything at all was found.
    ///
    /// @return `true` if there are no findings of any severity
    public boolean isClean() {
        return findings.isEmpty();
    }

    /// A human-readable rendering, one finding per line.
    ///
    /// @param locale the language to render in; Japanese for `ja`
    /// @return the text, never `null`
    public String toText(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        boolean japanese = "ja".equals(locale.getLanguage());
        var out = new StringBuilder();
        if (findings.isEmpty()) {
            return japanese ? "指摘事項はありません。\n" : "No findings.\n";
        }
        for (Finding finding : findings) {
            out.append(finding.toLine(locale)).append('\n');
        }
        out.append('\n');
        if (japanese) {
            out.append("エラー ").append(counts.get(Severity.ERROR))
                    .append(" 件、警告 ").append(counts.get(Severity.WARNING))
                    .append(" 件、情報 ").append(counts.get(Severity.INFO)).append(" 件。")
                    .append(isSubmittable() ? "送信可能です。" : "エラーがあるため送信できません。")
                    .append('\n');
        } else {
            out.append(counts.get(Severity.ERROR)).append(" error(s), ")
                    .append(counts.get(Severity.WARNING)).append(" warning(s), ")
                    .append(counts.get(Severity.INFO)).append(" info. ")
                    .append(isSubmittable()
                            ? "Submittable."
                            : "Not submittable while errors remain.")
                    .append('\n');
        }
        return out.toString();
    }

    /// A rendering in the JVM's default locale.
    ///
    /// @return the text, never `null`
    public String toText() {
        return toText(Locale.getDefault());
    }

    /// The rules that produced this report.
    ///
    /// @return the rules, possibly empty, never `null`
    public List<Rule> rules() {
        return rules;
    }

    /// The report as JSON (R-V4).
    ///
    /// @return the JSON document, never `null`
    public String toJson() {
        return ReportWriters.toJson(this);
    }

    /// The report as SARIF 2.1.0 (R-V4).
    ///
    /// Renders natively as annotations in GitHub, GitLab and Azure DevOps, so
    /// validating a payment file in CI lands the finding on the file.
    ///
    /// @return the SARIF document, never `null`
    public String toSarif() {
        return toSarif("zengin-file");
    }

    /// The report as SARIF, naming the file the findings are about.
    ///
    /// @param fileUri the artifact URI to record against each result
    /// @return the SARIF document, never `null`
    public String toSarif(String fileUri) {
        return ReportWriters.toSarif(this, rules, fileUri);
    }
}
