package io.zengin4j.benchmarks;

import module java.base;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.RecordView;
import io.zengin4j.core.codec.ZenginReader;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.testkit.SougouFurikomiFixtures;

/// R-P2: constant memory on the streaming path, whatever the file size.
///
/// Streams a file of the requested size — 1 GB by default — under whatever
/// heap the JVM was given, and fails if it does not finish. The Gradle task runs
/// it under `-Xmx64m`.
///
/// **The constrained heap is the assertion.** There is no
/// measurement to interpret and no threshold to argue about: if anything on the
/// read path retained per-record state, a 1 GB file at 122 bytes per record is
/// roughly nine million records, and the run would die with an
/// [OutOfMemoryError] long before the end. Completing is the result.
///
/// The input is generated on the fly rather than written to disk. A 1 GB
/// fixture is not something to create, keep or clean up in CI, and generating it
/// as a stream also proves the reader never needs the whole file at once — which
/// is the property under test.
public final class ConstantMemoryCheck {

    private static final int DEFAULT_BYTES = 1024 * 1024 * 1024;

    private ConstantMemoryCheck() {
    }

    /// Runs the check.
    ///
    /// @param args optionally, the target size in bytes
    /// @throws IOException if generation fails
    public static void main(String[] args) throws IOException {
        long target = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_BYTES;
        Runtime runtime = Runtime.getRuntime();

        System.out.printf("streaming %,d bytes under a %,d MB heap%n",
                target, runtime.maxMemory() / (1024 * 1024));

        var options = ReaderOptions.builder()
                .registry(FormatRegistry.defaults())
                .allowUnverifiedFormats(true)
                .warningListener(warning -> {
                })
                .build();

        long started = System.nanoTime();
        long records = 0;
        long payments = 0;
        long total = 0;

        try (InputStream input = new GeneratedFileStream(target);
                ZenginReader reader = ZenginReaders.open(input, options)) {
            while (reader.hasNext()) {
                RecordView view = reader.next();
                records++;
                if (view.kind() == RecordKind.DATA) {
                    // Decoded, not merely skipped: the point is that reading
                    // values does not retain them either (R-P3).
                    total += view.asLong(view.field("amount"));
                    payments++;
                }
            }
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        long used = runtime.totalMemory() - runtime.freeMemory();
        double megabytesPerSecond = target / (1024.0 * 1024.0) / Math.max(elapsed.toMillis(), 1) * 1000;

        System.out.printf("read %,d records (%,d payments, ¥%,d) in %s%n",
                records, payments, total, elapsed);
        System.out.printf("heap in use at the end: %,d MB of %,d MB max%n",
                used / (1024 * 1024), runtime.maxMemory() / (1024 * 1024));
        System.out.printf("throughput: %.1f MB/s%n", megabytesPerSecond);
        System.out.println("This figure is indicative only — it is measured under a deliberately"
                + " constrained heap and alongside file generation. Use the JMH harness for"
                + " anything published (R-P4, P9).");

        if (records == 0) {
            throw new IllegalStateException("no records were read");
        }
    }

    /// One synthetic 総合振込 file of arbitrary length, produced a batch at a
    /// time.
    ///
    /// **One file, not many concatenated.** Emitting whole files
    /// back to back would put a header after an end record, which the reader
    /// rightly refuses — it is not a valid file, and generating invalid input
    /// would test the error path rather than the memory property. Instead this
    /// emits repeated batches (header, payments, trailer), which R-C1 permits,
    /// and a single end record when the target size is reached.
    ///
    /// Never holds more than one batch, so the generator does not defeat the
    /// property the reader is being tested for.
    private static final class GeneratedFileStream extends InputStream {

        private static final int PAYMENTS_PER_BATCH = 2000;
        private static final byte[] SEPARATOR = {'\r', '\n'};

        private final SougouFurikomiFixtures fixtures = SougouFurikomiFixtures.create();
        private final long target;

        private byte[] chunk = new byte[0];
        private int position;
        private long produced;
        private boolean ended;

        GeneratedFileStream(long target) {
            this.target = target;
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] destination, int offset, int length) {
            if (position >= chunk.length && !refill()) {
                return -1;
            }
            int count = Math.min(length, chunk.length - position);
            System.arraycopy(chunk, position, destination, offset, count);
            position += count;
            return count;
        }

        private boolean refill() {
            if (ended) {
                return false;
            }
            chunk = produced >= target ? endRecord() : batch();
            ended = produced >= target;
            position = 0;
            produced += chunk.length;
            return true;
        }

        /// A header, its payments and its trailer — a well-formed batch, R-C1.
        private byte[] batch() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            long total = 0;
            out.writeBytes(fixtures.header());
            out.writeBytes(SEPARATOR);
            for (int i = 0; i < PAYMENTS_PER_BATCH; i++) {
                out.writeBytes(fixtures.data());
                out.writeBytes(SEPARATOR);
                total += SougouFurikomiFixtures.AMOUNT;
            }
            out.writeBytes(fixtures.trailer(PAYMENTS_PER_BATCH, total));
            out.writeBytes(SEPARATOR);
            return out.toByteArray();
        }

        private byte[] endRecord() {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            out.writeBytes(fixtures.end());
            out.writeBytes(SEPARATOR);
            return out.toByteArray();
        }
    }
}
