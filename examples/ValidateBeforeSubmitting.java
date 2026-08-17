import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.time.MonthDayResolver;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import io.zengin4j.validation.ZenginValidator;
import io.zengin4j.validation.api.Finding;
import io.zengin4j.validation.api.ReportWriters;
import io.zengin4j.validation.api.Severity;
import io.zengin4j.validation.api.ValidationReport;
import io.zengin4j.validation.calendar.JapaneseBankCalendar;
import io.zengin4j.validation.engine.Rules;
import io.zengin4j.validation.refdata.MapReferenceData;
import io.zengin4j.validation.refdata.ReferenceDataProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * UC-1: catch what a bank would reject, before sending the file.
 *
 * <p>Run it with:
 *
 * <pre>
 * ./gradlew runExamples
 * </pre>
 *
 * <p>The file below has five things wrong with it, of three different kinds:
 * one that stops the bank accepting it, one that the bank accepts and a human
 * would call a mistake, and one that only a calendar can see. A validator that
 * found only the first would be a parser with opinions.
 *
 * <p>Every identifier here is invented (R-L1, P1).
 */
public final class ValidateBeforeSubmitting {
    private ValidateBeforeSubmitting() {
    }

    public static void main(String[] args) {
        SougouFurikomiFixtures kit = SougouFurikomiFixtures.create();

        ZenginFile file = deliberatelyFlawedFile(kit);

        ZenginValidator validator = ZenginValidator.builder()
                .withCalendar(JapaneseBankCalendar.bundled())
                .withReferenceData(referenceData())
                .withDateResolver(MonthDayResolver.forwardLooking(LocalDate.of(2026, 8, 17)))
                .build();

        ValidationReport report = validator.validate(file);

        System.out.println("== what a human reads ==");
        System.out.print(report.toText(Locale.ENGLISH));

        System.out.println();
        System.out.println("== the same report in Japanese ==");
        System.out.print(report.toText(Locale.JAPANESE));

        System.out.println();
        System.out.println("== the decision ==");
        System.out.println("  submittable: " + report.isSubmittable());
        System.out.println("  " + report.counts().get(Severity.ERROR) + " error(s) block it; "
                + report.counts().get(Severity.WARNING) + " warning(s) do not.");

        for (Finding finding : report.findings(Severity.WARNING)) {
            System.out.println("  worth checking: " + finding.toLine(Locale.ENGLISH));
        }

        System.out.println();
        System.out.println("== for a machine ==");
        System.out.println("  JSON:  " + ReportWriters.toJson(report).lines().count() + " lines");
        System.out.println("  SARIF: " + ReportWriters.toSarif(report, Rules.bundled(), "payments.txt")
                .lines().count() + " lines");
        System.out.println("  SARIF renders natively as annotations in GitHub, GitLab and Azure");
        System.out.println("  DevOps, so a validation run in CI lands on the file itself.");

        ValidationReport relaxed = ZenginValidator.builder()
                .withCalendar(JapaneseBankCalendar.bundled())
                .withDateResolver(MonthDayResolver.forwardLooking(LocalDate.of(2026, 8, 17)))
                .suppress("V-306")
                .build()
                .validate(file);
        System.out.println();
        System.out.println("== with V-306 suppressed ==");
        System.out.println("  " + report.findings().size() + " findings became "
                + relaxed.findings().size());
    }

    /**
     * A file with, deliberately:
     *
     * <ul>
     *   <li>a trailer total that disagrees with its payments — V-301, an error;</li>
     *   <li>a duplicated payment — V-306, a warning, and usually a pasted row;</li>
     *   <li>a payee name written with ｰ instead of - — V-202, the mistake that
     *       looks correct;</li>
     *   <li>a zero amount — V-602;</li>
     *   <li>a value date on a Saturday — V-501, which needs a calendar.</li>
     * </ul>
     */
    private static ZenginFile deliberatelyFlawedFile(SougouFurikomiFixtures kit) {
        byte[] header = kit.header();
        System.arraycopy("0822".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0,
                header, 54, 4);

        byte[] file = SyntheticRecords.file(
                List.of(header,
                        kit.data("ﾔﾏﾀﾞ ﾀﾛｳ", 150_000L, "9876543"),
                        kit.data("ﾔﾏﾀﾞ ﾀﾛｳ", 150_000L, "9876543"),
                        kit.dataUnchecked("ﾃｽﾄｰﾊﾅｺ", 0L, "9876544"),
                        kit.trailer(3, 999_999L),
                        kit.end()),
                SeparatorStyle.CRLF, false);

        return io.zengin4j.core.codec.ZenginReaders.readFile(
                new java.io.ByteArrayInputStream(file), kit.readerOptions());
    }

    /**
     * Reference data the caller controls. None ships with the library: bank
     * data goes stale, and a snapshot compiled into a released jar would look
     * authoritative while being wrong (R-V5).
     */
    private static ReferenceDataProvider referenceData() {
        return MapReferenceData.describedAs("example data, 2026-08")
                .bank("9999", "ﾃｽﾄｷﾞﾝｺｳ")
                .branch("9999", "998", "ﾎﾝﾃﾝ")
                .branch("9999", "999", "ﾃｽﾄｼﾃﾝ")
                .build();
    }
}
