package io.zengin4j.cli.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Restates a library diagnostic in terms a shell user can act on.
 *
 * <p>R-E3 asks every diagnostic to say how to fix the problem, and the library
 * answers that correctly for its own audience: a caller writing Java is told to
 * set {@code ReaderOptions.builder().allowUnverifiedFormats(true)}. Printed at a
 * shell prompt the same sentence is useless — there is no builder to reach for,
 * and the reader is left to guess which flag corresponds.
 *
 * <p>So the remedy is translated on the way out. This is deliberately a small
 * lookup rather than a clever rewriter: string replacement is fragile, and the
 * fragility is contained by {@code NoJavaRemediesReachTheTerminalTest}, which
 * runs every failing command it can construct and fails the build if any output
 * still names a Java API. If the library rewords a message, that test breaks and
 * this table gets updated — which is the point.
 *
 * @since 0.3.0
 */
public final class CliMessages {
    /** Java remedy to command-line remedy, longest first so prefixes cannot shadow. */
    private static final Map<String, String> REMEDIES = remedies();

    private CliMessages() {
    }

    private static Map<String, String> remedies() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("ReaderOptions.builder().allowUnverifiedFormats(true)", "--allow-unverified");
        map.put("ZenginFileBuilder.forFormat(...).allowUnverifiedFormats(true)",
                "--allow-unverified");
        map.put("ReaderOptions.builder().format(...)", "--format=ID");
        map.put("WriterOptions.separator(...)", "--separator=STYLE");
        return map;
    }

    /**
     * Rewrites a library message for the command line.
     *
     * @param message the diagnostic as the library phrased it
     * @return the same message with any Java remedy replaced by its flag
     */
    public static String forTheCommandLine(String message) {
        if (message == null) {
            return "";
        }
        String rewritten = message;
        for (Map.Entry<String, String> remedy : REMEDIES.entrySet()) {
            rewritten = rewritten.replace(remedy.getKey(), remedy.getValue());
        }
        return rewritten;
    }

    /**
     * The remedies this class can suggest, for the test that checks they exist.
     *
     * @return the replacement strings, each of which should name a real option
     */
    public static java.util.Collection<String> suggestedRemedies() {
        return REMEDIES.values();
    }

    /**
     * Whether a string still names a Java API a shell user cannot use.
     *
     * <p>Exposed so the guard test and this class agree on what counts, rather
     * than each carrying its own idea of it.
     *
     * @param text the text to check
     * @return {@code true} if it mentions a builder or options class
     */
    public static boolean namesAJavaApi(String text) {
        return text.contains("ReaderOptions")
                || text.contains("WriterOptions")
                || text.contains("ZenginFileBuilder")
                || text.contains("ZenginValidator.builder")
                || text.contains(".builder()");
    }
}
