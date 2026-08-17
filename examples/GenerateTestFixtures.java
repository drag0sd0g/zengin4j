import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.testkit.KouzaFurikaeFixtures;
import io.zengin4j.testkit.ZenginGenerator;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * UC-6: test fixtures for a downstream payment service, for every format.
 *
 * <p>Run it with:
 *
 * <pre>
 * ./gradlew runExamples
 * </pre>
 *
 * <p><strong>Every value here is invented</strong> (R-L1). Bank {@code 9999},
 * branch {@code 999} and accounts beginning {@code 9} are outside the ranges
 * Japanese institutions use. Nothing this produces resembles a real payment
 * instruction, which matters because test files end up in repositories.
 *
 * <p>The same ground is covered by {@code zengin generate} for anyone who would
 * rather not write Java:
 *
 * <pre>
 * zengin generate --format=kyuyo-furikomi --count=100 --seed=42 --out=payroll.txt
 * </pre>
 */
public final class GenerateTestFixtures {

    private GenerateTestFixtures() {
    }

    public static void main(String[] args) {
        everyFormat();
        reproducibility();
        directDebitRunsTheOtherWay();
    }

    /**
     * All four bundled formats, generated and read back.
     *
     * <p>Reading them back is the part that matters. A generator that emits
     * bytes nobody checks is a generator that eventually emits bytes the
     * library cannot read, and every downstream test built on it inherits the
     * problem.
     */
    private static void everyFormat() {
        System.out.println("== every bundled format ==");

        for (FormatId id : FormatFixtures.supported()) {
            FormatFixtures fixtures = FormatFixtures.forFormat(id);
            byte[] bytes = ZenginGenerator.builder()
                    .format(id)
                    .payments(25)
                    .seed(2026L)
                    .build()
                    .generate();

            ZenginFile parsed = ZenginReaders.readFile(
                    new ByteArrayInputStream(bytes), fixtures.readerOptions());
            Batch batch = parsed.batches().get(0);

            System.out.printf("  %-16s %5d bytes  %2d payments  ¥%,d  trailer agrees: %s%n",
                    id.value(), bytes.length, batch.data().size(), batch.computedTotal(),
                    batch.trailer().orElseThrow().totalAmount() == batch.computedTotal());
        }
    }

    /**
     * The same seed gives the same bytes (R-CLI3), which is what lets a
     * generated file be committed as a fixture and regenerated years later.
     */
    private static void reproducibility() {
        System.out.println();
        System.out.println("== reproducibility ==");

        byte[] first = generate(42L, 50);
        byte[] second = generate(42L, 50);
        byte[] different = generate(43L, 50);

        System.out.println("  same seed, same bytes:      " + Arrays.equals(first, second));
        System.out.println("  different seed, different:  " + !Arrays.equals(first, different));

        // Separator and end-of-file conventions are part of the fixture, since
        // reading them back is exactly what a downstream test needs to prove.
        for (SeparatorStyle separator : new SeparatorStyle[] {
                SeparatorStyle.NONE, SeparatorStyle.LF, SeparatorStyle.CRLF}) {
            byte[] bytes = ZenginGenerator.builder()
                    .payments(1)
                    .separator(separator)
                    .build()
                    .generate();
            System.out.printf("  %-5s %d bytes%n", separator, bytes.length);
        }
    }

    /**
     * 預金口座振替 collects; the other three pay. A fixture that got this
     * backwards would describe money moving the wrong way with nothing in the
     * bytes to show it, so the field ids differ and the fixtures follow them.
     */
    private static void directDebitRunsTheOtherWay() {
        System.out.println();
        System.out.println("== 預金口座振替 is a collection, not a payment ==");

        KouzaFurikaeFixtures instruction = KouzaFurikaeFixtures.create();
        ZenginFile parsed = ZenginReaders.readFile(
                new ByteArrayInputStream(instruction.file()), instruction.readerOptions());

        var data = parsed.descriptor().record(io.zengin4j.core.format.RecordKind.DATA);
        System.out.println("  the data record names a " + (data.find("payerName").isPresent()
                ? "payer (請求先) — the account to be debited"
                : "beneficiary"));
        System.out.println("  amount field: "
                + data.find("debitAmount").map(f -> f.id() + " at byte " + f.offset())
                        .orElse("(none)"));
        System.out.println("  振替結果コード on an instruction file: "
                + (char) parsed.batches().get(0).data().get(0)
                        .rawBytes()[data.field("transferResult").offset()]
                + "  (a result arrives only when the bank sends the file back)");
    }

    private static byte[] generate(long seed, int payments) {
        return ZenginGenerator.builder().seed(seed).payments(payments).build().generate();
    }
}
