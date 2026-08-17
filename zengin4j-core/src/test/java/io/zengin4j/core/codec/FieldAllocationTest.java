package io.zengin4j.core.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.testing.Fixtures;
import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R-P3: reading a field allocates nothing, where the caller does not retain the
 * value.
 *
 * <p>The claim appears in several places in this codebase — the record view's
 * design, the character-set check, the benchmark documentation — and until now
 * it was asserted nowhere. A performance property nobody measures is a
 * performance property nobody has.
 *
 * <p><strong>The measurement is a difference, not an absolute.</strong> Total
 * allocation on any read path is not zero and never will be: the buffer is
 * allocated once, the reader itself is an object, and the JIT allocates while it
 * warms up. What R-P3 says is that allocation does not grow with the number of
 * fields <em>read</em>. So this decodes every numeric field of every record,
 * then decodes ten times as many, and asserts the difference is negligible. If
 * a decode allocated even sixteen bytes, the difference would be megabytes.
 *
 * <p>Uses {@code com.sun.management.ThreadMXBean}, which HotSpot provides and
 * other JVMs may not; the test skips rather than fails where it is absent.
 *
 * <p><strong>These pass under {@code -Xint}</strong>, with the JIT disabled
 * entirely. That matters: escape analysis will remove a short-lived
 * {@code Optional} or iterator once a path is hot, which makes it easy to
 * believe a hot path is allocation-free when it is merely optimised. Passing
 * with no JIT at all means the allocations are absent from the code rather than
 * from the compiled form of it — and CI runners, which are slower and share
 * cores, sit somewhere between the two. This test first failed on exactly that
 * difference.
 */
class FieldAllocationTest {
    /**
     * Allowed drift between the two measurements, in bytes.
     *
     * <p>Generous on purpose. What is being distinguished is "does not scale"
     * from "scales", and a per-field allocation of even one long would put the
     * difference three orders of magnitude above this. A threshold tight enough
     * to be flaky would buy nothing.
     */
    private static final long TOLERANCE_BYTES = 512 * 1024;

    private static final int PASSES = 20;
    private static final int PAYMENTS = 2000;
    private static final int FIELDS_PER_RECORD = 8;

    private final FormatDescriptor descriptor = Fixtures.descriptor();

    @Test
    void decodingFieldsDoesNotAllocateInProportionToTheFieldsRead() {
        com.sun.management.ThreadMXBean threads = threadMxBean();
        assumeThat(threads).as("this JVM does not report per-thread allocation").isNotNull();
        assumeThat(threads.isThreadAllocatedMemoryEnabled()).isTrue();

        byte[] file = manyRecords(PAYMENTS);

        for (int i = 0; i < 20; i++) {
            decodeFields(file, 1);
            decodeFields(file, FIELDS_PER_RECORD);
        }

        long few = allocatedBy(() -> {
            for (int i = 0; i < PASSES; i++) {
                decodeFields(file, 1);
            }
        }, threads);
        long many = allocatedBy(() -> {
            for (int i = 0; i < PASSES; i++) {
                decodeFields(file, FIELDS_PER_RECORD);
            }
        }, threads);

        long extraReads = (long) PASSES * PAYMENTS * (FIELDS_PER_RECORD - 1);
        long perField = Math.max(0, many - few) / extraReads;

        assertThat(perField)
                .as("each additional field read allocated %d bytes (%d extra reads, %d extra bytes);"
                        + " R-P3 says field access allocates nothing the caller does not keep",
                        perField, extraReads, many - few)
                .isZero();
    }

    /** And the same for the character-set check, which is on the same hot path. */
    @Test
    void checkingCharactersDoesNotAllocateWhenTheRecordIsClean() {
        com.sun.management.ThreadMXBean threads = threadMxBean();
        assumeThat(threads).isNotNull();
        assumeThat(threads.isThreadAllocatedMemoryEnabled()).isTrue();

        byte[] record = Fixtures.data(descriptor);
        RecordDescriptorHolder holder = new RecordDescriptorHolder(descriptor.record(RecordKind.DATA));

        for (int i = 0; i < 10_000; i++) {
            holder.check(record);
        }

        long allocated = allocatedBy(() -> {
            for (int i = 0; i < 100_000; i++) {
                holder.check(record);
            }
        }, threads);

        assertThat(allocated)
                .as("checking a clean record 100,000 times allocated %d bytes", allocated)
                .isLessThan(TOLERANCE_BYTES);
    }

    private static final String[] NUMERIC_FIELDS = {
        "amount", "beneficiaryBankCode", "beneficiaryBranchCode", "accountNumber",
        "clearingHouseCode", "accountType", "newCode", "transferCategory",
    };

    /** Reads every record, decoding the first {@code fields} numeric fields of each. */
    private long decodeFields(byte[] file, int fields) {
        long sink = 0;
        try (ZenginReader reader = ZenginReaders.open(new ByteArrayInputStream(file), Fixtures.options())) {
            while (reader.hasNext()) {
                RecordView view = reader.next();
                if (view.kind() == RecordKind.DATA) {
                    for (int i = 0; i < fields; i++) {
                        sink += view.asLong(view.field(NUMERIC_FIELDS[i]));
                    }
                }
            }
        }
        return sink;
    }

    private static long allocatedBy(Runnable work, com.sun.management.ThreadMXBean threads) {
        long id = Thread.currentThread().threadId();
        long before = threads.getThreadAllocatedBytes(id);
        work.run();
        return threads.getThreadAllocatedBytes(id) - before;
    }

    private byte[] manyRecords(int payments) {
        List<byte[]> parts = new ArrayList<>();
        parts.add(Fixtures.header(descriptor));
        long total = 0;
        for (int i = 0; i < payments; i++) {
            parts.add(Fixtures.data(descriptor));
            total += Fixtures.AMOUNT;
        }
        parts.add(Fixtures.trailer(descriptor, payments, total));
        parts.add(Fixtures.end(descriptor));
        return Fixtures.join(Fixtures.CRLF, parts.toArray(new byte[0][]));
    }

    private static com.sun.management.ThreadMXBean threadMxBean() {
        return ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                ? bean
                : null;
    }

    /** Holds the descriptor so the lambda does not capture and allocate one per call. */
    private record RecordDescriptorHolder(io.zengin4j.core.format.RecordDescriptor descriptor) {
        boolean check(byte[] record) {
            return RecordCharacters.isClean(record, descriptor);
        }
    }
}
