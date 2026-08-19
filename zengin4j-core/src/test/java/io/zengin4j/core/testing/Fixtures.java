package io.zengin4j.core.testing;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.FieldCodec;
import io.zengin4j.core.codec.PadPolicy;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.ZenginFileBuilder;
import io.zengin4j.core.codec.RecordFramer;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.FileFraming;

/// Synthetic 総合振込 bytes for core's own tests.
///
/// Core cannot use zengin4j-testkit — testkit depends on core, and inverting
/// that for tests would put the module graph in the build's way. The overlap is
/// a few lines of field encoding.
///
/// Every value is invented (R-L1, P1).
public final class Fixtures {

    public static final FormatId SOUGOU_FURIKOMI = FormatId.of("sougou-furikomi");
    public static final int RECORD_LENGTH = 120;
    public static final byte[] CRLF = {'\r', '\n'};
    public static final byte[] LF = {'\n'};
    public static final byte[] CR = {'\r'};
    public static final byte[] NO_SEPARATOR = {};

    /// ﾃｽﾄｷﾞﾝｺｳ: eight bytes, rendering as seven characters — the ｷﾞ carries a standalone dakuten.
    public static final String BANK_NAME = "ﾃｽﾄｷﾞﾝｺｳ";

    /// ﾔﾏﾀﾞ ﾀﾛｳ: eight bytes, rendering as seven characters.
    public static final String BENEFICIARY = "ﾔﾏﾀﾞ ﾀﾛｳ";

    public static final String ACCOUNT = "9876543";
    public static final long AMOUNT = 150_000L;

    private Fixtures() {
    }

    public static FormatRegistry registry() {
        return FormatRegistry.defaults();
    }

    public static FormatDescriptor descriptor() {
        return registry().byId(SOUGOU_FURIKOMI).orElseThrow();
    }

    /// Options that accept the provisional bundled descriptor, silently.
    public static ReaderOptions options() {
        return optionsBuilder().build();
    }

    public static ReaderOptions.Builder optionsBuilder() {
        return ReaderOptions.builder()
                .registry(registry())
                .allowUnverifiedFormats(true)
                .warningListener(warning -> {
                    // expected in tests; the reader still collects them
                });
    }

    /// A builder that has already acknowledged the provisional layout.
    ///
    /// Every bundled descriptor is `verified: false`, and building on
    /// one requires an explicit opt-in. Tests take it here so that the one test
    /// asserting the gate *fires* is the only place the raw
    /// `ZenginFileBuilder.forFormat` appears.
    ///
    /// @param descriptor the format to build
    /// @return a builder that will not refuse the descriptor
    public static ZenginFileBuilder builder(FormatDescriptor descriptor) {
        return ZenginFileBuilder.forFormat(descriptor).allowUnverifiedFormats(true);
    }

    public static byte[] header(FormatDescriptor descriptor) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("codeKubun", "0");
        values.put("originatorCode", "9900000001");
        values.put("originatorName", "ﾃｽﾄｼﾖｳｼﾞ");
        values.put("valueDate", "0930");
        values.put("originBankCode", "9999");
        values.put("originBankName", BANK_NAME);
        values.put("originBranchCode", "998");
        values.put("originBranchName", "ﾎﾝﾃﾝ");
        values.put("accountType", "1");
        values.put("accountNumber", "9000001");
        return encode(descriptor.record(RecordKind.HEADER), values);
    }

    public static byte[] data(FormatDescriptor descriptor) {
        return data(descriptor, BENEFICIARY, AMOUNT);
    }

    public static byte[] data(FormatDescriptor descriptor, String beneficiary, long amount) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("beneficiaryBankCode", "9999");
        values.put("beneficiaryBankName", BANK_NAME);
        values.put("beneficiaryBranchCode", "999");
        values.put("beneficiaryBranchName", "ﾃｽﾄｼﾃﾝ");
        values.put("clearingHouseCode", "0000");
        values.put("accountType", "1");
        values.put("accountNumber", ACCOUNT);
        values.put("beneficiaryName", beneficiary);
        values.put("amount", Long.toString(amount));
        values.put("newCode", "0");
        values.put("customerCode1", "INV2026000");
        values.put("customerCode2", "1");
        values.put("transferCategory", "7");
        values.put("identification", " ");
        return encode(descriptor.record(RecordKind.DATA), values);
    }

    public static byte[] trailer(FormatDescriptor descriptor, int count, long total) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("recordCount", Integer.toString(count));
        values.put("totalAmount", Long.toString(total));
        return encode(descriptor.record(RecordKind.TRAILER), values);
    }

    public static byte[] end(FormatDescriptor descriptor) {
        return encode(descriptor.record(RecordKind.END), Map.of());
    }

    /// Header, one payment, trailer and end record, separated by CRLF.
    public static byte[] file(FormatDescriptor descriptor) {
        return join(CRLF, header(descriptor), data(descriptor), trailer(descriptor, 1, AMOUNT),
                end(descriptor));
    }

    /// The same four records, framed as asked.
    ///
    /// Assembled here rather than through `ZenginWriters`, so a test
    /// that reads this and writes it again is comparing the writer against an
    /// independent assembly rather than against itself.
    ///
    /// @param descriptor the format
    /// @param framing    the framing to apply; must not be `MIXED`
    /// @return the framed file
    public static byte[] framed(FormatDescriptor descriptor, FileFraming framing) {
        byte[] separator = framing.separator().bytes().orElseThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (framing.byteOrderMarkPresent()) {
            out.writeBytes(RecordFramer.BYTE_ORDER_MARK);
        }
        List<byte[]> records = List.of(header(descriptor), data(descriptor),
                trailer(descriptor, 1, AMOUNT), end(descriptor));
        for (int i = 0; i < records.size(); i++) {
            out.writeBytes(records.get(i));
            if (i < records.size() - 1 || framing.trailingSeparator()) {
                out.writeBytes(separator);
            }
        }
        if (framing.trailingEofByte()) {
            out.write(RecordFramer.EOF_BYTE);
        }
        return out.toByteArray();
    }

    public static byte[] encode(RecordDescriptor descriptor, Map<String, String> values) {
        byte[] frame = new byte[descriptor.recordLength()];
        for (FieldDescriptor field : descriptor.fields()) {
            String value = values.get(field.id());
            if (value == null) {
                value = field.constant().orElse(null);
            }
            if (value == null) {
                FieldCodec.fill(frame, field.offset(), field.length(), field.type().padByte());
            } else {
                FieldCodec.encodeText(value, frame, field.offset(), field.length(),
                        ZenginCharset.defaultCharset(), PadPolicy.of(field.type()));
            }
        }
        return frame;
    }

    /// Joins records, writing the separator after each one.
    public static byte[] join(byte[] separator, byte[]... records) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] record : records) {
            out.writeBytes(record);
            out.writeBytes(separator);
        }
        return out.toByteArray();
    }

    public static byte[] concat(List<byte[]> chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        chunks.forEach(out::writeBytes);
        return out.toByteArray();
    }

    /// The same layout under a different id, for registry and ambiguity tests.
    public static FormatDescriptor renamed(FormatDescriptor descriptor, String newId) {
        FormatId id = FormatId.of(newId);
        Map<RecordKind, RecordDescriptor> records = new java.util.EnumMap<>(RecordKind.class);
        descriptor.records().forEach((kind, record) -> records.put(kind,
                new RecordDescriptor(id, record.kind(), record.discriminator(), record.recordLength(),
                        record.fields())));
        return new FormatDescriptor(id, descriptor.nameJa(), descriptor.nameEn(), descriptor.typeCode(),
                descriptor.recordLength(), descriptor.verified(), List.of(), descriptor.note(), records);
    }

    /// Replaces bytes at an offset, for building deliberately broken files.
    public static byte[] patch(byte[] source, int offset, String replacement) {
        byte[] copy = source.clone();
        byte[] bytes = ZenginCharset.defaultCharset().encode(replacement);
        System.arraycopy(bytes, 0, copy, offset, bytes.length);
        return copy;
    }
}
