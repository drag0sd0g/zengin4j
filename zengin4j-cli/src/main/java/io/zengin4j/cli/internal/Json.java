package io.zengin4j.cli.internal;

import java.util.Locale;

/**
 * The smallest JSON writer that does the job correctly.
 *
 * <p>Hand-written, for the reason ADR-0022 gives and one more that is specific
 * to this module: a native image has to be told about every class a reflective
 * serialiser will touch, and that configuration goes stale the first time a
 * field is renamed. A writer that reflects over nothing needs no configuration
 * and cannot go stale.
 *
 * <p>Emitting is not the same problem as parsing. This escapes five characters,
 * balances brackets and tracks indentation against a structure the caller
 * already knows; nothing here parses anything, which is where JSON's edge cases
 * actually live.
 *
 * @since 0.3.0
 */
public final class Json {
    private final StringBuilder out = new StringBuilder();
    private int depth;
    private boolean needsComma;
    private boolean pendingName;

    /**
     * Writes an object, whose contents the body emits.
     *
     * @param body emits the fields
     */
    public void object(Runnable body) {
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

    /**
     * Writes an array, whose elements the body emits.
     *
     * @param body emits the elements
     */
    public void array(Runnable body) {
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

    /**
     * Names the next value, which must be an object or an array.
     *
     * @param key the field name
     * @return this writer
     */
    public Json name(String key) {
        separate();
        out.append('"').append(escape(key)).append("\": ");
        needsComma = false;
        pendingName = true;
        return this;
    }

    /**
     * Writes a string field.
     *
     * @param key   the field name
     * @param value the value
     */
    public void field(String key, String value) {
        separate();
        out.append('"').append(escape(key)).append("\": \"").append(escape(value)).append('"');
        needsComma = true;
    }

    /**
     * Writes a numeric field.
     *
     * @param key   the field name
     * @param value the value
     */
    public void field(String key, long value) {
        separate();
        out.append('"').append(escape(key)).append("\": ").append(value);
        needsComma = true;
    }

    /**
     * Writes a boolean field.
     *
     * @param key   the field name
     * @param value the value
     */
    public void field(String key, boolean value) {
        separate();
        out.append('"').append(escape(key)).append("\": ").append(value);
        needsComma = true;
    }

    /**
     * Writes a bare string element inside an array.
     *
     * @param value the element
     */
    public void value(String value) {
        separate();
        out.append('"').append(escape(value)).append('"');
        needsComma = true;
    }

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
     * <p>Japanese text passes through as itself: the output is UTF-8 and JSON
     * permits any Unicode character in a string, so {@code \\u} escaping
     * katakana would only make the document unreadable to the people most
     * likely to read it.
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
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
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
        return out + "\n";
    }
}
