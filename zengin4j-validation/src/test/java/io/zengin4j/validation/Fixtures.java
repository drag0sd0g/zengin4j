package io.zengin4j.validation;

import module java.base;
import io.zengin4j.core.codec.ParseMode;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import io.zengin4j.testkit.SyntheticRecords;
import io.zengin4j.validation.api.ValidationReport;

/// Files with known problems, for testing that the right rule fires.
///
/// Built from the testkit, so every identifier is invented and outside the
/// ranges real institutions use (R-L1, P1).
final class Fixtures {

    static final SougouFurikomiFixtures TESTKIT = SougouFurikomiFixtures.create();

    private Fixtures() {
    }

    /// Options that accept the provisional bundled descriptor, leniently.
    static ReaderOptions lenient() {
        return ReaderOptions.builder()
                .registry(FormatRegistry.defaults())
                .allowUnverifiedFormats(true)
                .mode(ParseMode.LENIENT)
                .warningListener(warning -> {
                })
                .build();
    }

    static ZenginFile read(byte[] bytes) {
        return ZenginReaders.readFile(new ByteArrayInputStream(bytes), lenient());
    }

    static ValidationReport validateBytes(byte[] bytes) {
        try {
            return ZenginValidator.defaults().validate(read(bytes));
        } catch (io.zengin4j.core.error.ZenginException unreadable) {
            // The reader may refuse bytes that are not a file at all. That is
            // the reader's contract, not the validator's; this helper exists to
            // prove the *validator* never throws, so an unreadable input is
            // reported as an empty report rather than propagated.
            return new ValidationReport(List.of());
        }
    }

    /// A file with nothing wrong with it at all — no errors and no warnings.
    ///
    /// Built record by record rather than with `TESTKIT.file(2, ...)`,
    /// which repeats one payment and therefore trips V-306. That is the testkit
    /// behaving correctly and the duplicate rule behaving correctly; it just
    /// makes the generated file the wrong fixture for "clean".
    static ZenginFile wellFormedFile() {
        long first = SougouFurikomiFixtures.AMOUNT;
        long second = SougouFurikomiFixtures.AMOUNT + 500;
        byte[] file = SyntheticRecords.file(
                List.of(TESTKIT.header(),
                        TESTKIT.data("ﾔﾏﾀﾞ ﾀﾛｳ", first, "9876543"),
                        TESTKIT.data("ﾃｽﾄ ﾊﾅｺ", second, "9876544"),
                        TESTKIT.trailer(2, first + second),
                        TESTKIT.end()),
                SeparatorStyle.CRLF, false);
        return read(file);
    }

    /// A trailer whose total disagrees with its payments (V-301).
    static ZenginFile fileWithWrongTrailerTotal() {
        byte[] file = SyntheticRecords.file(
                List.of(TESTKIT.header(),
                        TESTKIT.data(),
                        TESTKIT.trailer(1, SougouFurikomiFixtures.AMOUNT + 1),
                        TESTKIT.end()),
                SeparatorStyle.CRLF, false);
        return read(file);
    }

    /// Several problems at once, for the ordering and determinism tests: a wrong
    /// trailer total, a wrong count, a duplicate payment and a zero amount.
    static ZenginFile fileWithManyProblems() {
        byte[] file = SyntheticRecords.file(
                List.of(TESTKIT.header(),
                        TESTKIT.data(),
                        TESTKIT.data(),
                        TESTKIT.data("ﾃｽﾄ ﾊﾅｺ", 0L, "9000002"),
                        TESTKIT.trailer(9, 1L),
                        TESTKIT.end()),
                SeparatorStyle.CRLF, false);
        return read(file);
    }

    /// Inputs chosen to break a validator that assumes anything.
    static List<byte[]> hostileInputs() {
        List<byte[]> inputs = new ArrayList<>();
        inputs.add(new byte[0]);
        inputs.add("not a zengin file at all".getBytes(StandardCharsets.UTF_8));
        inputs.add(new byte[120]);
        inputs.add(TESTKIT.file());
        // A header and nothing else: no data, no trailer, no end record.
        inputs.add(TESTKIT.header());
        // A truncated record.
        byte[] truncated = new byte[60];
        System.arraycopy(TESTKIT.header(), 0, truncated, 0, 60);
        inputs.add(truncated);
        // Every byte value, in a record-length block.
        byte[] everyByte = new byte[240];
        for (int i = 0; i < everyByte.length; i++) {
            everyByte[i] = (byte) i;
        }
        inputs.add(everyByte);
        return inputs;
    }
}
