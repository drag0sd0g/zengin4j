package io.zengin4j.core.testing;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.RecordEncoder;
import io.zengin4j.core.codec.RecordFramer;
import io.zengin4j.core.codec.ZenginFileBuilder;
import io.zengin4j.core.format.FormatDescriptor;
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
 * Generates small, valid 総合振込 files.
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
                    ZenginCharset.MS932, header(random)));

            int count = random.nextInt(4);
            long batchTotal = 0;
            for (int i = 0; i < count; i++) {
                long amount = amount(random);
                batchTotal += amount;
                records.add(RecordEncoder.encode(descriptor.record(RecordKind.DATA),
                        ZenginCharset.MS932, payment(random, amount)));
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
        ZenginFileBuilder builder = ZenginFileBuilder.forFormat(descriptor)
                .charset(ZenginCharset.MS932)
                .framing(framing(random));

        int batches = 1 + random.nextInt(2);
        for (int batch = 0; batch < batches; batch++) {
            Map<String, String> header = header(random);
            builder.header(values -> header.forEach(values::set));

            int count = random.nextInt(4);
            for (int i = 0; i < count; i++) {
                Map<String, String> payment = payment(random, amount(random));
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

    private static Map<String, String> header(Random random) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("codeKubun", "0");
        values.put("originatorCode", "99" + digits(random, 8));
        values.put("originatorName", name(random));
        values.put("valueDate", monthDay(random));
        values.put("originBankCode", "9999");
        values.put("originBankName", name(random, 15));
        values.put("originBranchCode", "998");
        values.put("originBranchName", name(random, 15));
        values.put("accountType", pick(random, ACCOUNT_TYPES));
        values.put("accountNumber", "9" + digits(random, 6));
        return values;
    }

    private static Map<String, String> payment(Random random, long amount) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("beneficiaryBankCode", "9999");
        values.put("beneficiaryBankName", name(random, 15));
        values.put("beneficiaryBranchCode", "999");
        values.put("beneficiaryBranchName", name(random, 15));
        values.put("clearingHouseCode", "0000");
        values.put("accountType", pick(random, ACCOUNT_TYPES));
        values.put("accountNumber", "9" + digits(random, 6));
        values.put("beneficiaryName", name(random));
        values.put("amount", Long.toString(amount));
        values.put("newCode", pick(random, NEW_CODES));
        values.put("customerCode1", random.nextBoolean() ? "INV" + digits(random, 7) : "");
        values.put("customerCode2", random.nextBoolean() ? digits(random, 4) : "");
        values.put("transferCategory", pick(random, TRANSFER_CATEGORIES));
        values.put("identification", random.nextInt(6) == 0 ? "Y" : " ");
        return values;
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
