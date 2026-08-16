package io.zengin4j.benchmarks;

import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.RecordView;
import io.zengin4j.core.codec.ZenginReader;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Parse throughput (R-P1).
 *
 * <p>Three benchmarks, because "how fast does it parse" has three answers and
 * quoting the wrong one is how misleading numbers get published:
 *
 * <ul>
 *   <li>{@link #streamingSkippingFields} — the framing path alone. What the
 *       reader costs before anything is decoded.</li>
 *   <li>{@link #streamingDecodingAmounts} — framing plus a numeric decode per
 *       data record, straight from the buffer with no intermediate
 *       {@code String} (R-MEM3, R-P3). The realistic streaming case.</li>
 *   <li>{@link #wholeFileMaterialised} — everything materialised into immutable
 *       records. Convenient, and necessarily slower; quoting this as the
 *       library's throughput would understate the streaming path, and quoting
 *       the streaming path as though it materialised would overstate it.</li>
 * </ul>
 *
 * <p>Throughput is reported in operations per second over a fixed-size file, so
 * MB/s is ops/s × file size. The file sizes are chosen to sit either side of a
 * typical L2 cache, because a benchmark that fits entirely in cache measures
 * something no production file does.
 *
 * <p><strong>Any number taken from here must be published with its conditions</strong>
 * — hardware, JDK, JVM flags (R-P4, P9). {@code benchmarks/README.md} has the
 * template and the last recorded run.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms1g", "-Xmx1g", "-XX:+AlwaysPreTouch"})
@Measurement(iterations = 5, time = 2)
@org.openjdk.jmh.annotations.Warmup(iterations = 3, time = 2)
@State(Scope.Benchmark)
public class ReadBenchmark {

    /** Payment records per file. 8k ≈ 1 MB; 80k ≈ 10 MB, past any cache. */
    @Param({"8000", "80000"})
    public int payments;

    private byte[] file;
    private ReaderOptions options;

    /**
     * Builds the input once. Generation cost must not land inside the
     * measurement, and the same bytes must be reused across iterations or the
     * allocator noise swamps the signal.
     */
    @Setup
    public void setUp() {
        file = SougouFurikomiFixtures.create().file(payments, SeparatorStyle.CRLF, false);
        options = ReaderOptions.builder()
                .registry(FormatRegistry.defaults())
                .allowUnverifiedFormats(true)
                .warningListener(warning -> {
                })
                .build();
    }

    /**
     * The size of one benchmark input, for turning ops/s into MB/s.
     *
     * @return the file size in bytes
     */
    public int fileBytes() {
        return file.length;
    }

    /** Framing only: find every record, decode nothing. */
    @Benchmark
    public int streamingSkippingFields() {
        int records = 0;
        try (ZenginReader reader = ZenginReaders.open(new ByteArrayInputStream(file), options)) {
            while (reader.hasNext()) {
                reader.next();
                records++;
            }
        }
        return records;
    }

    /** Framing plus one numeric decode per data record, allocating nothing per field. */
    @Benchmark
    public long streamingDecodingAmounts() {
        long total = 0;
        try (ZenginReader reader = ZenginReaders.open(new ByteArrayInputStream(file), options)) {
            while (reader.hasNext()) {
                RecordView view = reader.next();
                if (view.kind() == RecordKind.DATA) {
                    total += view.asLong(view.field("amount"));
                }
            }
        }
        return total;
    }

    /** Everything materialised: the convenient API, and the slower one. */
    @Benchmark
    public void wholeFileMaterialised(Blackhole blackhole) {
        ZenginFile parsed = ZenginReaders.readFile(new ByteArrayInputStream(file), options);
        blackhole.consume(parsed.totalRecords());
        blackhole.consume(parsed.batches());
    }
}
