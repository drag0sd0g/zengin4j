import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.codec.ZenginFileBuilder;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import java.io.ByteArrayInputStream;
import java.time.MonthDay;
import java.util.Arrays;

/**
 * UC-6: generate a test fixture for a downstream payment service.
 *
 * <p>Run it with:
 *
 * <pre>
 * ./gradlew runExamples
 * </pre>
 *
 * <p>Three things a fixture generator has to be able to do, in order: build a
 * well-formed file, build a deliberately broken one, and produce the same bytes
 * every time so a downstream test can assert against them.
 *
 * <p>Every identifier here is invented, and outside the ranges real Japanese
 * institutions use: bank {@code 9999}, branch {@code 999}, accounts beginning
 * {@code 9} (R-L1, P1).
 */
public final class BuildSougouFurikomi {

    private BuildSougouFurikomi() {
    }

    public static void main(String[] args) {
        FormatRegistry registry = FormatRegistry.defaults();
        FormatDescriptor descriptor = registry.byId(FormatId.of("sougou-furikomi")).orElseThrow();

        wellFormed(descriptor);
        deliberatelyBroken(descriptor);
        deterministic(descriptor);
    }

    /** The trailer is computed from the payments, so it cannot drift by accident. */
    private static void wellFormed(FormatDescriptor descriptor) {
        System.out.println("== a well-formed file ==");

        ZenginFile file = ZenginFileBuilder.forFormat(descriptor)
                // Every descriptor in 0.1.0 is verified: false, and building a
                // file places real values at those offsets. The opt-in is
                // required so the decision is visible in your code, not ours.
                // Read DISCLAIMER.md before doing this against a real bank.
                .allowUnverifiedFormats(true)
                .header(header -> header
                        .set("originatorCode", "9900000001")
                        .set("originatorName", "ﾃｽﾄｼﾖｳｼﾞ")
                        .set("valueDate", MonthDay.of(9, 30))
                        .set("originBankCode", "9999")
                        .set("originBranchCode", "998")
                        .set("accountType", "1")
                        .set("accountNumber", "9000001"))
                .payment(payment -> payment
                        .set("beneficiaryBankCode", "9999")
                        .set("beneficiaryBranchCode", "999")
                        .set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ")
                        .set("accountNumber", "9876543")
                        .set("amount", 150_000L))
                .payment(payment -> payment
                        .set("beneficiaryBankCode", "9999")
                        .set("beneficiaryBranchCode", "999")
                        // ｶﾞ is two characters and two bytes: the voicing mark is
                        // its own code point. A fixture that never exercises one
                        // never exercises the truncation hazard (§17).
                        .set("beneficiaryName", "ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ")
                        .set("accountNumber", "9151178")
                        .set("amount", 2_500L))
                .build();

        Batch batch = file.batches().get(0);
        System.out.printf("  %d payments, trailer says %d / ¥%,d%n",
                batch.data().size(),
                batch.trailer().orElseThrow().recordCount(),
                batch.trailer().orElseThrow().totalAmount());

        byte[] bytes = ZenginWriters.toByteArray(file, WriterOptions.defaults());
        System.out.printf("  %d bytes, CRLF-separated%n", bytes.length);

        // What was built reads back as what was built (INV-2).
        ZenginFile reread = ZenginReaders.readFile(new ByteArrayInputStream(bytes), options());
        System.out.printf("  reads back as %d records, ¥%,d%n",
                reread.totalRecords(), reread.batches().get(0).computedTotal());
    }

    /**
     * The fixture nobody can build by accident: a trailer that disagrees with
     * its own payments. This is what a downstream validator has to catch, so it
     * is what a downstream test needs to be handed.
     */
    private static void deliberatelyBroken(FormatDescriptor descriptor) {
        System.out.println("== a file whose trailer lies ==");

        ZenginFile file = ZenginFileBuilder.forFormat(descriptor)
                .allowUnverifiedFormats(true)
                .header(header -> header.set("originatorCode", "9900000001"))
                .payment(payment -> payment.set("amount", 150_000L))
                // Overriding the computed trailer is deliberate and explicit.
                .trailer(trailer -> trailer.set("recordCount", 7L).set("totalAmount", 999_999L))
                .build();

        Batch batch = file.batches().get(0);
        System.out.printf("  trailer claims %d / ¥%,d; the records add up to %d / ¥%,d%n",
                batch.trailer().orElseThrow().recordCount(),
                batch.trailer().orElseThrow().totalAmount(),
                batch.computedCount(),
                batch.computedTotal());
    }

    /**
     * R-C19: the same file writes the same bytes, always. A fixture that
     * shifted between runs could not be asserted against.
     */
    private static void deterministic(FormatDescriptor descriptor) {
        System.out.println("== determinism ==");

        byte[] first = ZenginWriters.toByteArray(fixture(descriptor), WriterOptions.defaults());
        byte[] second = ZenginWriters.toByteArray(fixture(descriptor), WriterOptions.defaults());
        System.out.println("  two builds produce identical bytes: " + Arrays.equals(first, second));

        // The framing is yours to choose — some institutions want no separators
        // at all, some want a trailing EOF byte.
        byte[] unseparated = ZenginWriters.toByteArray(
                fixture(descriptor), WriterOptions.framing(FileFraming.none()));
        byte[] withEof = ZenginWriters.toByteArray(fixture(descriptor),
                WriterOptions.framing(new FileFraming(false, SeparatorStyle.LF, true, true)));
        System.out.printf("  CRLF %d bytes · none %d bytes · LF+EOF %d bytes%n",
                first.length, unseparated.length, withEof.length);
    }

    private static ZenginFile fixture(FormatDescriptor descriptor) {
        return ZenginFileBuilder.forFormat(descriptor)
                .allowUnverifiedFormats(true)
                .header(header -> header.set("originatorCode", "9900000001"))
                .payment(payment -> payment.set("amount", 150_000L))
                .build();
    }

    private static ReaderOptions options() {
        return ReaderOptions.builder()
                .registry(FormatRegistry.defaults())
                // Every descriptor in 0.1.0 is verified: false. Read
                // DISCLAIMER.md before setting this against a real file.
                .allowUnverifiedFormats(true)
                .build();
    }
}
