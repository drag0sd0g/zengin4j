package io.zengin4j.core.testing;

import io.zengin4j.core.charset.CharacterSet;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.RecordEncoder;
import io.zengin4j.core.codec.RecordFramer;
import io.zengin4j.core.codec.ZenginFileBuilder;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.FieldType;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import java.io.ByteArrayOutputStream;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates small, valid files for any bundled format.
 *
 * <p>Files are deliberately tiny — one or two batches of at most three
 * payments. Without a shrinking library a failing case is reported as
 * generated, so generating small keeps failures readable. The variation that
 * matters is not size but shape: separator conventions, whether one follows
 * the last record, byte order marks, EOF bytes, and field content at its
 * edges.
 *
 * <p>{@link #bytes} assembles the file itself rather than going through
 * {@code ZenginWriters}. That is deliberate: INV-1 asks whether reading and
 * writing reproduce a file <em>something else</em> produced, and generating it
 * with the writer under test would make the property agree with itself.
 *
 * <p>Every value is invented (R-L1, P1): bank {@code 9999}, branch
 * {@code 999}, accounts beginning {@code 9}.
 */
public final class RandomZenginFiles {

    /** Names chosen to exercise voiced and semi-voiced marks, which are separate bytes (§17). */
    private static final List<String> NAMES = List.of(
            "ﾔﾏﾀﾞ ﾀﾛｳ", "ﾃｽﾄ ﾊﾅｺ", "ｻﾝﾌﾟﾙ ｲﾁﾛｳ", "ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ", "ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ",
            "ﾓｼﾞ ｼﾖｳ", "ﾃｽﾄ", "ｱ", "ﾀﾞ", "ﾃｽﾄｼﾖｳｼﾞ (ｶ");

    private static final List<SeparatorStyle> SEPARATORS = List.of(
            SeparatorStyle.NONE, SeparatorStyle.CR, SeparatorStyle.LF, SeparatorStyle.CRLF);

    private static final List<String> ACCOUNT_TYPES = List.of("1", "2", "4", "9");
    private static final List<String> NEW_CODES = List.of("0", "1", "2");
    private static final List<String> TRANSFER_CATEGORIES = List.of("7", "8");

    private RandomZenginFiles() {
    }

    /**
     * Generates a file as bytes, assembled independently of the writer.
     *
     * @param random     the source of randomness
     * @param descriptor the format to generate
     * @return the generated file and a description of its shape
     */
    public static RandomFile bytes(Random random, FormatDescriptor descriptor) {
        FileFraming framing = framing(random);
        byte[] separator = framing.separator().bytes().orElseThrow();

        List<byte[]> records = new ArrayList<>();
        int batches = 1 + random.nextInt(2);
        int payments = 0;
        long total = 0;

        for (int batch = 0; batch < batches; batch++) {
            records.add(RecordEncoder.encode(descriptor.record(RecordKind.HEADER),
                    ZenginCharset.MS932,
                    valuesFor(random, descriptor.record(RecordKind.HEADER), 0L)));

            int count = random.nextInt(4);
            long batchTotal = 0;
            for (int i = 0; i < count; i++) {
                long amount = amount(random);
                batchTotal += amount;
                records.add(RecordEncoder.encode(descriptor.record(RecordKind.DATA),
                        ZenginCharset.MS932,
                        valuesFor(random, descriptor.record(RecordKind.DATA), amount)));
            }
            payments += count;
            total += batchTotal;

            Map<String, String> trailer = new LinkedHashMap<>();
            trailer.put("recordCount", Integer.toString(count));
            trailer.put("totalAmount", Long.toString(batchTotal));
            records.add(RecordEncoder.encode(descriptor.record(RecordKind.TRAILER),
                    ZenginCharset.MS932, trailer));
        }
        records.add(RecordEncoder.encode(descriptor.record(RecordKind.END), ZenginCharset.MS932, Map.of()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (framing.byteOrderMarkPresent()) {
            out.writeBytes(RecordFramer.BYTE_ORDER_MARK);
        }
        for (int i = 0; i < records.size(); i++) {
            out.writeBytes(records.get(i));
            if (i < records.size() - 1 || framing.trailingSeparator()) {
                out.writeBytes(separator);
            }
        }
        if (framing.trailingEofByte()) {
            out.write(RecordFramer.EOF_BYTE);
        }
        return new RandomFile(out.toByteArray(), framing, batches, payments, total, records.size());
    }

    /**
     * Builds a file through {@link ZenginFileBuilder}, for the invariants that
     * are about construction rather than parsing.
     *
     * @param random     the source of randomness
     * @param descriptor the format to build
     * @return the built file
     */
    public static ZenginFile built(Random random, FormatDescriptor descriptor) {
        ZenginFileBuilder builder = Fixtures.builder(descriptor)
                .charset(ZenginCharset.MS932)
                .framing(framing(random));

        int batches = 1 + random.nextInt(2);
        for (int batch = 0; batch < batches; batch++) {
            Map<String, String> header =
                    valuesFor(random, descriptor.record(RecordKind.HEADER), 0L);
            builder.header(values -> header.forEach(values::set));

            int count = random.nextInt(4);
            for (int i = 0; i < count; i++) {
                Map<String, String> payment =
                        valuesFor(random, descriptor.record(RecordKind.DATA), amount(random));
                builder.payment(values -> payment.forEach(values::set));
            }
        }
        return builder.build();
    }

    private static FileFraming framing(Random random) {
        SeparatorStyle separator = SEPARATORS.get(random.nextInt(SEPARATORS.size()));
        boolean trailing = separator != SeparatorStyle.NONE && random.nextBoolean();
        // Byte order marks are rare in the wild and rare here; the reader must
        // be told to strip them, so a property using this must say so.
        boolean mark = random.nextInt(8) == 0;
        boolean eof = random.nextInt(4) == 0;
        return new FileFraming(mark, separator, trailing, eof);
    }

    /**
     * Field values for a record, derived from the descriptor.
     *
     * <p><strong>Nothing here names a field.</strong> An earlier version listed
     * 総合振込's ids — {@code beneficiaryBankCode}, {@code customerCode1} — which
     * made the {@code descriptor} parameter a false generality: passing any
     * other format failed, because {@link RecordEncoder} rejects an id the
     * record does not declare. The properties that carry R-T7 therefore held
     * for one of the four bundled formats and nobody could tell.
     *
     * <p>Deriving from the descriptor also makes these properties true of a
     * consumer's own format, which is a stronger claim than R-T7 asks for.
     *
     * @param random     the source of randomness
     * @param record     the record layout to fill
     * @param amount     the value for the record's amount field, if it has one
     */
    private static Map<String, String> valuesFor(Random random, RecordDescriptor record,
            long amount) {
        Map<String, String> values = new LinkedHashMap<>();
        for (FieldDescriptor field : record.fields()) {
            // Constants and filler are the encoder's business; setting them
            // here would be asserting that this code and the encoder agree
            // about what it already guarantees.
            if (field.constant().isPresent() || field.filler()) {
                continue;
            }
            valueFor(random, field, amount).ifPresent(value -> values.put(field.id(), value));
        }
        return values;
    }

    /** One field's value, chosen from what the descriptor says it may hold. */
    private static java.util.Optional<String> valueFor(Random random, FieldDescriptor field,
            long amount) {
        if (field.format().filter(f -> f == FieldFormat.AMOUNT).isPresent()) {
            return java.util.Optional.of(Long.toString(amount));
        }
        if (field.format().filter(f -> f == FieldFormat.MMDD).isPresent()) {
            return java.util.Optional.of(monthDay(random));
        }
        if (field.format().filter(f -> f == FieldFormat.CODE_KUBUN).isPresent()) {
            // Always JIS. The other value in the list is EBCDIC, and the reader
            // rejects such a file by name rather than decoding it (ADR-0010) —
            // so drawing it at random would generate a file the library is
            // designed to refuse, and INV-1 speaks only of *valid* files.
            return java.util.Optional.of("0");
        }
        if (field.codeList().isPresent()) {
            List<String> permitted = field.codes().isEmpty()
                    ? field.codeList().get().values().stream()
                            .map(io.zengin4j.core.format.CodeValue::code).toList()
                    : field.codes();
            return permitted.isEmpty()
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(pick(random, permitted));
        }
        if (field.type() == FieldType.N) {
            // Leading 9 keeps every generated identifier inside the invented
            // range (R-L1), whatever the field turns out to be.
            return java.util.Optional.of("9" + digits(random, field.length() - 1));
        }
        // Text: a name that fits, sometimes empty where the field permits it.
        if (!field.required() && random.nextInt(4) == 0) {
            return java.util.Optional.of("");
        }
        return java.util.Optional.of(nameFor(random, field));
    }

    /**
     * A name that fits the field and satisfies its character class.
     *
     * <p>The classes genuinely differ — payroll names admit no Latin letters at
     * all — so a name valid in one format's 受取人名 can be invalid in
     * another's. Candidates are filtered against the field's own class rather
     * than against a single global set.
     */
    private static String nameFor(Random random, FieldDescriptor field) {
        List<String> candidates = NAMES.stream()
                .filter(candidate -> {
                    byte[] encoded = ZenginCharset.MS932.encode(candidate);
                    return encoded.length <= field.length()
                            && CharacterSet.isClean(encoded, 0, encoded.length, field.charClass());
                })
                .toList();
        return candidates.isEmpty() ? "" : candidates.get(random.nextInt(candidates.size()));
    }

    /** Weighted towards small values, with the field's extremes represented. */
    private static long amount(Random random) {
        return switch (random.nextInt(10)) {
            case 0 -> 0L;
            case 1 -> 9_999_999_999L;
            case 2 -> 1L;
            default -> 1L + (long) random.nextInt(1_000_000);
        };
    }

    private static String name(Random random) {
        return NAMES.get(random.nextInt(NAMES.size()));
    }

    /** A name guaranteed to fit a narrower field, measured in bytes (R-C15). */
    private static String name(Random random, int maxBytes) {
        String candidate = name(random);
        while (ZenginCharset.MS932.encode(candidate).length > maxBytes) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate;
    }

    private static String monthDay(Random random) {
        MonthDay value = MonthDay.of(1 + random.nextInt(12), 1 + random.nextInt(28));
        return String.format("%02d%02d", value.getMonthValue(), value.getDayOfMonth());
    }

    private static String digits(Random random, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append((char) ('0' + random.nextInt(10)));
        }
        return result.toString();
    }

    private static String pick(Random random, List<String> options) {
        return options.get(random.nextInt(options.size()));
    }

    /**
     * A generated file and the shape it was generated with, so a failure
     * message says what was tried without printing payment content.
     *
     * @param bytes    the file
     * @param framing  the framing it was assembled with
     * @param batches  how many batches
     * @param payments how many data records in total
     * @param total    the sum of the amounts
     * @param records  the total record count
     */
    public record RandomFile(
            byte[] bytes, FileFraming framing, int batches, int payments, long total, int records) {

        @Override
        public String toString() {
            return "file[" + bytes.length + " bytes, " + records + " records, " + batches + " batch(es), "
                    + payments + " payment(s), total " + total + ", " + framing.separator()
                    + (framing.trailingSeparator() ? "+trailing" : "")
                    + (framing.byteOrderMarkPresent() ? "+bom" : "")
                    + (framing.trailingEofByte() ? "+eof" : "") + "]";
        }
    }
}
