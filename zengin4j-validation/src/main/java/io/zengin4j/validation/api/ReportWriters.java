package io.zengin4j.validation.api;

import java.util.List;
import java.util.Objects;

/**
 * Renders a report as JSON and as SARIF (R-V4).
 *
 * <p><strong>Written by hand, because {@code zengin4j-validation} may depend
 * only on core (R-M2).</strong> That is a smaller undertaking than it sounds
 * and a different problem from the one ADR-0001 got wrong: <em>emitting</em>
 * JSON means escaping five characters and balancing brackets, against a
 * structure this code already knows. <em>Parsing</em> arbitrary JSON is where
 * the edge cases live, and nothing here parses anything.
 *
 * <p>SARIF is worth the trouble. GitHub, GitLab and Azure DevOps all render it
 * natively, so a validation run in CI becomes annotations on the diff — the
 * finding appears against the line of the file it concerns, which for a
 * fixed-length format is the closest thing to pointing at the byte.
 *
 * @since 0.2.0
 */
public final class ReportWriters {

    /** SARIF 2.1.0, which is what every consumer implements. */
    private static final String SARIF_VERSION = "2.1.0";
    private static final String SARIF_SCHEMA =
            "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json";

    private ReportWriters() {
    }

    /**
     * Renders a report as JSON.
     *
     * @param report the report
     * @return the JSON document, never {@code null}
     */
    public static String toJson(ValidationReport report) {
        Objects.requireNonNull(report, "report");
        Json json = new Json();
        json.object(() -> {
            json.field("submittable", report.isSubmittable());
            json.name("counts").object(() -> {
                for (Severity severity : Severity.values()) {
                    json.field(severity.name().toLowerCase(java.util.Locale.ROOT),
                            report.counts().getOrDefault(severity, 0));
                }
            });
            json.name("findings").array(() -> {
                for (Finding finding : report.findings()) {
                    json.object(() -> {
                        json.field("severity", finding.severity().name());
                        json.field("ruleId", finding.ruleId());
                        finding.recordNumber().ifPresent(value -> json.field("recordNumber", value));
                        finding.byteOffset().ifPresent(value -> json.field("byteOffset", value));
                        finding.fieldOffset().ifPresent(value -> json.field("fieldOffset", value));
                        finding.fieldId().ifPresent(value -> json.field("fieldId", value));
                        json.field("messageEn", finding.messageEn());
                        json.field("messageJa", finding.messageJa());
                        finding.actualValue().ifPresent(value -> json.field("actual", value));
                        finding.expectation().ifPresent(value -> json.field("expected", value));
                    });
                }
            });
        });
        return json.toString();
    }

    /**
     * Renders a report as SARIF 2.1.0.
     *
     * @param report the report
     * @param rules  the rules that ran, so the document can describe them
     * @param fileUri the file the findings are about, for the location URIs
     * @return the SARIF document, never {@code null}
     */
    public static String toSarif(ValidationReport report, List<Rule> rules, String fileUri) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(rules, "rules");
        String uri = fileUri == null ? "zengin-file" : fileUri;

        Json json = new Json();
        json.object(() -> {
            json.field("$schema", SARIF_SCHEMA);
            json.field("version", SARIF_VERSION);
            json.name("runs").array(() -> json.object(() -> {
                json.name("tool").object(() -> json.name("driver").object(() -> {
                    json.field("name", "zengin4j");
                    json.field("informationUri", "https://github.com/drag0sd0g/zengin4j");
                    // Every id a rule can emit, not just its own. A result
                    // referencing a ruleId the document does not declare is a
                    // SARIF document a consumer is entitled to reject, and the
                    // composite rules emit several ids each.
                    json.name("rules").array(() -> {
                        for (String id : declaredIds(rules)) {
                            json.object(() -> {
                                json.field("id", id);
                                json.name("shortDescription").object(() ->
                                        json.field("text", describe(id, rules)));
                                json.name("defaultConfiguration").object(() ->
                                        json.field("level", levelOf(id, rules)));
                            });
                        }
                    });
                }));
                json.name("results").array(() -> {
                    for (Finding finding : report.findings()) {
                        json.object(() -> {
                            json.field("ruleId", finding.ruleId());
                            json.field("level", finding.severity().sarifLevel());
                            json.name("message").object(() -> json.field("text", finding.messageEn()));
                            json.name("locations").array(() -> json.object(() ->
                                    json.name("physicalLocation").object(() -> {
                                        json.name("artifactLocation").object(() ->
                                                json.field("uri", uri));
                                        // A fixed-length file has one record per
                                        // line when separators are present, so
                                        // record number is the line a reviewer
                                        // sees. byteOffset carries the exact
                                        // position for tools that want it.
                                        json.name("region").object(() -> {
                                            finding.recordNumber().ifPresent(number ->
                                                    json.field("startLine", number));
                                            finding.fieldOffset().ifPresent(offset ->
                                                    json.field("startColumn", offset + 1));
                                            finding.byteOffset().ifPresent(offset ->
                                                    json.field("byteOffset", offset));
                                        });
                                    })));
                        });
                    }
                });
            }));
        });
        return json.toString();
    }

    /** Every finding id the given rules can produce, in ascending order. */
    private static List<String> declaredIds(List<Rule> rules) {
        return rules.stream()
                .flatMap(rule -> rule.emits().stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static String describe(String id, List<Rule> rules) {
        // A composite rule's own description covers its primary id; the others
        // have their own entry in the message bundle.
        try {
            return Messages.description(id);
        } catch (java.util.MissingResourceException absent) {
            return rules.stream()
                    .filter(rule -> rule.emits().contains(id))
                    .map(Rule::description)
                    .findFirst()
                    .orElse(id);
        }
    }

    private static String levelOf(String id, List<Rule> rules) {
        return rules.stream()
                .filter(rule -> rule.emits().contains(id))
                .findFirst()
                .map(rule -> rule.severityOf(id).sarifLevel())
                .orElse("warning");
    }

    /**
     * The smallest JSON writer that does the job correctly.
     *
     * <p>Tracks only what it must: whether a comma is due, and how deep to
     * indent. Everything else is the caller's structure, expressed as nested
     * lambdas that mirror the document.
     */
    private static final class Json {

        private final StringBuilder out = new StringBuilder();
        private int depth;
        private boolean needsComma;

        void object(Runnable body) {
            separate();
            out.append('{');
            depth++;
            needsComma = false;
            body.run();
            depth--;
            newline();
            out.append('}');
            needsComma = true;
        }

        void array(Runnable body) {
            separate();
            out.append('[');
            depth++;
            needsComma = false;
            body.run();
            depth--;
            newline();
            out.append(']');
            needsComma = true;
        }

        Json name(String key) {
            separate();
            out.append('"').append(escape(key)).append("\": ");
            needsComma = false;
            pendingName = true;
            return this;
        }

        void field(String key, String value) {
            separate();
            out.append('"').append(escape(key)).append("\": \"").append(escape(value)).append('"');
            needsComma = true;
        }

        void field(String key, int value) {
            separate();
            out.append('"').append(escape(key)).append("\": ").append(value);
            needsComma = true;
        }

        void field(String key, boolean value) {
            separate();
            out.append('"').append(escape(key)).append("\": ").append(value);
            needsComma = true;
        }

        private boolean pendingName;

        private void separate() {
            if (pendingName) {
                pendingName = false;
                return;
            }
            if (needsComma) {
                out.append(',');
            }
            newline();
        }

        private void newline() {
            if (out.isEmpty()) {
                return;
            }
            out.append('\n').append("  ".repeat(Math.max(depth, 0)));
        }

        /**
         * The five characters JSON requires escaped, plus control characters.
         *
         * <p>Japanese text passes through as itself: the output is UTF-8 and
         * JSON permits any Unicode character in a string, so {@code \\u}
         * escaping katakana would only make the document unreadable to the
         * people most likely to read it.
         */
        private static String escape(String value) {
            StringBuilder escaped = new StringBuilder(value.length() + 8);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            escaped.append(String.format("\\u%04x", (int) c));
                        } else {
                            escaped.append(c);
                        }
                    }
                }
            }
            return escaped.toString();
        }

        @Override
        public String toString() {
            return out.append('\n').toString();
        }
    }
}
