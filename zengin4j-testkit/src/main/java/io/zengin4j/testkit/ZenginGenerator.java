package io.zengin4j.testkit;

import module java.base;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.SeparatorStyle;

/// Deterministic synthetic file generator (UC-6).
///
/// The same seed produces the same bytes on every platform and every JDK:
/// [Random] has an exactly specified algorithm, and every value derives
/// from it (R-CLI3). That is what makes generated fixtures usable as golden
/// files.
///
/// **Every value is invented.** Bank `9999`, branch
/// `999` and accounts beginning with `9` are outside the ranges
/// real institutions use, and names are drawn from a fixed list of obviously
/// fictional katakana strings (R-L1, P1).
///
/// Works for any format [FormatFixtures] covers. The generated values
/// are the same for each — a name, an amount, an account number — while which
/// fields carry them, and in which direction the money moves, is the fixtures'
/// business.
///
/// @since 0.1.0
public final class ZenginGenerator {

    /// Names drawn on by the generator.
    ///
    /// All eight must be valid in **every** format's name field,
    /// which means satisfying the strictest: `PAYROLL_NAME` admits no
    /// Latin letters and no symbols whatever, so these are half-width katakana
    /// and spaces and nothing else. No small kana, and no 長音 `ｰ` — the
    /// standard writes a long vowel as `-`, which `PAYROLL_NAME`
    /// does not permit either, so these names simply have none.
    ///
    /// This list contained ﾀﾞﾐｰ ｻﾌﾞﾛｳ until running `zengin validate`
    /// over a generated payroll file reported six `V-202` errors against
    /// it. [io.zengin4j.testkit.GeneratorNamesTest] now checks every name
    /// against every name field of every bundled format.
    private static final List<String> NAMES = List.of(
            "ﾃｽﾄ ﾀﾛｳ", "ﾃｽﾄ ﾊﾅｺ", "ｻﾝﾌﾟﾙ ｲﾁﾛｳ", "ｻﾝﾌﾟﾙ ｼﾞﾛｳ", "ﾀﾞﾐ ｻﾌﾞﾛｳ",
            "ﾓｼﾞ ｼﾖｳ", "ｶﾅ ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ", "ﾃｽﾄ ｶﾞｸﾌﾞﾁ");

    private final FormatFixtures fixtures;
    private final long seed;
    private final int payments;
    private final SeparatorStyle separator;
    private final boolean trailingEofByte;

    private ZenginGenerator(Builder builder) {
        this.fixtures = builder.fixtures;
        this.seed = builder.seed;
        this.payments = builder.payments;
        this.separator = builder.separator;
        this.trailingEofByte = builder.trailingEofByte;
    }

    /// Creates a builder.
    ///
    /// @return a new builder
    public static Builder builder() {
        return new Builder();
    }

    /// Returns the format this generator produces.
    ///
    /// @return the format id, never `null`
    public FormatId formatId() {
        return fixtures.formatId();
    }

    /// Generates the file.
    ///
    /// @return the file bytes; identical for identical settings
    public byte[] generate() {
        Random random = new Random(seed);
        List<byte[]> records = new ArrayList<>(payments + 3);
        records.add(fixtures.header());
        long total = 0;
        for (int i = 0; i < payments; i++) {
            String name = NAMES.get(random.nextInt(NAMES.size()));
            long amount = 1_000L + random.nextInt(9_999_000);
            String account = "9" + String.format("%06d", random.nextInt(1_000_000));
            total += amount;
            records.add(fixtures.data(name, amount, account));
        }
        records.add(fixtures.trailer(payments, total));
        records.add(fixtures.end());
        return SyntheticRecords.file(records, separator, trailingEofByte);
    }

    /// Collects generator settings.
    ///
    /// @since 0.1.0
    public static final class Builder {

        private FormatFixtures fixtures = SougouFurikomiFixtures.create();
        private long seed = 42L;
        private int payments = 10;
        private SeparatorStyle separator = SeparatorStyle.CRLF;
        private boolean trailingEofByte;

        private Builder() {
        }

        /// Sets the fixtures used to encode records.
        ///
        /// @param value the fixtures
        /// @return this builder
        public Builder fixtures(FormatFixtures value) {
            this.fixtures = Objects.requireNonNull(value, "fixtures");
            return this;
        }

        /// Sets the format to generate, using its bundled fixtures.
        ///
        /// @param value the format id
        /// @return this builder
        /// @throws IllegalArgumentException if the testkit has no fixtures for it
        public Builder format(FormatId value) {
            return fixtures(FormatFixtures.forFormat(Objects.requireNonNull(value, "format")));
        }

        /// Sets the random seed.
        ///
        /// @param value the seed
        /// @return this builder
        public Builder seed(long value) {
            this.seed = value;
            return this;
        }

        /// Sets how many data records to generate.
        ///
        /// @param value the payment count
        /// @return this builder
        /// @throws IllegalArgumentException if the count is negative
        public Builder payments(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("payment count must not be negative, found " + value);
            }
            this.payments = value;
            return this;
        }

        /// Sets the record separator.
        ///
        /// @param value the separator style
        /// @return this builder
        public Builder separator(SeparatorStyle value) {
            this.separator = Objects.requireNonNull(value, "separator");
            return this;
        }

        /// Sets whether to append an end-of-file byte.
        ///
        /// @param value whether to append `0x1A`
        /// @return this builder
        public Builder trailingEofByte(boolean value) {
            this.trailingEofByte = value;
            return this;
        }

        /// Builds the generator.
        ///
        /// @return the generator
        public ZenginGenerator build() {
            return new ZenginGenerator(this);
        }
    }
}
