package io.zengin4j.cli.internal;

import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.CharacterViolation;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.FieldCodec;
import io.zengin4j.core.error.Diagnostics;
import io.zengin4j.core.format.CodeList;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldType;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

/**
 * Renders one field as a line a human can act on (R-CLI5).
 *
 * <p>Per field: where it starts, its bytes, what they decode to, what it is
 * called in both languages, and whether the value is one the field may hold.
 * The last of those is the reason to run the tool — a file is usually rejected
 * for one field, and the fastest way to find it is a column of ticks with one
 * cross in it.
 *
 * @since 0.3.0
 */
public final class FieldRendering {
    /** Bytes of hex shown before the column is truncated. */
    private static final int HEX_BYTES = 6;

    private FieldRendering() {
    }

    /**
     * One field, ready to print.
     *
     * @param field   the descriptor
     * @param hex     the bytes in hex, possibly abbreviated
     * @param value   the decoded value, masked unless the caller opted out
     * @param valid   whether the value is one the field may hold
     * @param problem why not, when it is not; empty otherwise
     */
    public record Row(FieldDescriptor field, String hex, String value, boolean valid,
            String problem) {
        /**
         * The value with control characters made visible, for printing.
         *
         * <p>Separate from {@link #value()}, which stays as decoded. A record
         * whose fields have slipped out of alignment — the case this whole
         * command exists for — puts the file's own separator bytes inside a
         * field, and printing a raw {@code 0x0D} tears the table in half at
         * exactly the moment somebody needs to read it. JSON output uses the
         * raw value instead, because JSON escapes control characters itself.
         *
         * @return the value, safe to put in a line of terminal output
         */
        public String display() {
            return printable(value);
        }
    }

    /**
     * Replaces control characters with a visible placeholder.
     *
     * <p>{@code ␍} and {@code ␊} (the Unicode control pictures) name the byte
     * rather than merely hiding it, which matters when the byte is the reason
     * the field is wrong.
     *
     * @param value the text to make printable
     * @return the text with no control characters in it
     */
    public static String printable(String value) {
        boolean clean = true;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < 0x20 || value.charAt(i) == 0x7F) {
                clean = false;
                break;
            }
        }
        if (clean) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r') {
                out.append('␍');
            } else if (c == '\n') {
                out.append('␊');
            } else if (c == '\t') {
                out.append('␉');
            } else if (c < 0x20 || c == 0x7F) {
                out.append((char) (0x2400 + (c == 0x7F ? 0x21 : c)));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Renders a field.
     *
     * @param field     the field to render
     * @param record    the record's bytes
     * @param charset   the encoding to decode text with
     * @param unmask    whether to show sensitive values in full (R-CLI4)
     * @return the row
     */
    public static Row render(FieldDescriptor field, byte[] record, ZenginCharset charset,
            boolean unmask) {
        String raw = FieldCodec.decodeField(record, 0, field, charset);
        boolean hide = field.sensitive() && !unmask;

        String value = hide ? Diagnostics.maskIdentifier(raw.strip()) : raw;
        String hex = hide ? "(masked)" : hex(record, field.offset(), field.length());

        String problem = problemWith(field, record, raw);
        return new Row(field, hex, value, problem.isEmpty(), problem);
    }

    /**
     * Why this value is not one the field may hold, or an empty string.
     *
     * <p>Checked against the descriptor rather than against a rule set: this
     * command reports what the bytes are, and a field whose declared constant
     * does not match is wrong regardless of anyone's validation policy.
     */
    private static String problemWith(FieldDescriptor field, byte[] record, String raw) {
        if (field.type() == FieldType.N) {
            for (int i = field.offset(); i < field.endOffset(); i++) {
                byte b = record[i];
                if (b < '0' || b > '9') {
                    return "byte " + i + " is 0x" + hexByte(b) + ", not a digit";
                }
            }
        } else {
            List<CharacterViolation> violations =
                    CharacterSet.validate(record, field.offset(), field.length(), field.charClass());
            if (!violations.isEmpty()) {
                CharacterViolation first = violations.get(0);
                return first.describeEn()
                        + (violations.size() > 1 ? " (+" + (violations.size() - 1) + " more)" : "");
            }
        }

        String trimmed = raw.strip();
        if (field.constant().isPresent() && !field.constant().get().equals(trimmed)) {
            return "expected the constant '" + field.constant().get() + "'";
        }
        if (field.codeList().isPresent() && !trimmed.isEmpty()) {
            CodeList list = field.codeList().get();
            if (!field.codes().isEmpty() && !field.codes().contains(trimmed)) {
                return "'" + trimmed + "' is not one of " + field.codes()
                        + ", which is all this format admits for " + list.id();
            }
            if (field.codes().isEmpty() && !list.accepts(trimmed)) {
                return "'" + trimmed + "' is not in code list " + list.id();
            }
        }
        if (field.required() && trimmed.isEmpty()) {
            return "required, but empty";
        }
        return "";
    }

    private static String hex(byte[] bytes, int offset, int length) {
        int shown = Math.min(length, HEX_BYTES);
        StringBuilder text = new StringBuilder(shown * 3);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                text.append(' ');
            }
            text.append(hexByte(bytes[offset + i]));
        }
        if (length > shown) {
            text.append(" …");
        }
        return text.toString();
    }

    private static String hexByte(byte b) {
        return String.format(Locale.ROOT, "%02X", b);
    }

    /**
     * Prints the rows as a table.
     *
     * <p>Columns are measured in <em>display</em> width, not character count.
     * 項目名 are full-width: 種別コード is five characters and ten columns, and a
     * table padded by {@code String.length} puts every Japanese row out of
     * alignment — which in a tool whose whole job is showing where bytes sit is
     * a poor first impression.
     *
     * <p><strong>Both names are carried, as R-CLI5 requires</strong>, and the
     * field id as well. The id is not a substitute for the English name: it
     * diverges for eight of the fifty-two bundled fields — {@code dataKubun} is
     * "Record Type", {@code dummy} is "Filler", {@code amount} is "Transfer
     * Amount" — so a reader who cannot read 項目名 would be guessing. The id
     * stays because it is what {@code explain --field=} and the JSON output key
     * on.
     *
     * <p>The sequence number was dropped to pay for the extra column. In a
     * byte-oriented tool the offset is the better key anyway, and it is the one
     * every diagnostic elsewhere in this library quotes.
     *
     * @param out  where to print
     * @param rows the rows, in field order
     */
    public static void table(PrintWriter out, List<Row> rows) {
        int idWidth = width(rows, row -> row.field().id(), 5, 24);
        int jaWidth = width(rows, row -> row.field().nameJa(), 6, 20);
        int enWidth = width(rows, row -> row.field().nameEn(), 4, 24);
        int hexWidth = width(rows, Row::hex, 8, 20);
        int valueWidth = width(rows, Row::display, 5, 30);

        out.println("  " + pad("off", 5) + " " + pad("len", 3) + " " + pad("T", 1)
                + "  " + pad("field", idWidth)
                + "  " + pad("項目名", jaWidth)
                + "  " + pad("name", enWidth)
                + "  " + pad("hex", hexWidth)
                + "  " + pad("value", valueWidth));

        for (Row row : rows) {
            FieldDescriptor field = row.field();
            out.println("  " + pad(Integer.toString(field.offset()), 5)
                    + " " + pad(Integer.toString(field.length()), 3)
                    + " " + pad(field.type().name(), 1)
                    + "  " + pad(field.id(), idWidth)
                    + "  " + pad(field.nameJa(), jaWidth)
                    + "  " + pad(field.nameEn(), enWidth)
                    + "  " + pad(row.hex(), hexWidth)
                    + "  " + pad(row.display(), valueWidth)
                    + "  " + (row.valid() ? "ok" : "<- " + row.problem()));
        }
    }

    private static int width(List<Row> rows, java.util.function.Function<Row, String> of,
            int minimum, int maximum) {
        int widest = minimum;
        for (Row row : rows) {
            widest = Math.max(widest, displayWidth(of.apply(row)));
        }
        return Math.min(widest, maximum);
    }

    /**
     * Pads or truncates to an exact display width.
     *
     * <p>Truncation appends {@code …} so a shortened value never looks like a
     * complete one — in this tool, a silently shortened account number would be
     * actively misleading.
     */
    private static String pad(String value, int width) {
        int actual = displayWidth(value);
        if (actual > width) {
            StringBuilder cut = new StringBuilder();
            int used = 0;
            for (int i = 0; i < value.length(); i++) {
                int next = charWidth(value.charAt(i));
                if (used + next > width - 1) {
                    break;
                }
                cut.append(value.charAt(i));
                used += next;
            }
            cut.append('…');
            return cut + " ".repeat(Math.max(0, width - used - 1));
        }
        return value + " ".repeat(width - actual);
    }

    /**
     * How many terminal columns a string occupies.
     *
     * <p>Half-width katakana — what these files are full of — are one column
     * each despite being outside ASCII, which is exactly the case a naive
     * "non-ASCII is wide" rule gets backwards.
     *
     * @param value the text
     * @return its display width
     */
    public static int displayWidth(String value) {
        int total = 0;
        for (int i = 0; i < value.length(); i++) {
            total += charWidth(value.charAt(i));
        }
        return total;
    }

    private static int charWidth(char c) {
        if (c >= 0xFF61 && c <= 0xFFDC) {
            return 1;
        }
        if (c >= 0xFFE8 && c <= 0xFFEE) {
            return 1;
        }
        if ((c >= 0x1100 && c <= 0x115F)
                || (c >= 0x2E80 && c <= 0xA4CF)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6)) {
            return 2;
        }
        return 1;
    }
}
