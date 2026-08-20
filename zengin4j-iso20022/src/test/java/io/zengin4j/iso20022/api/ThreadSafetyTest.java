package io.zengin4j.iso20022.api;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;

import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.iso20022.envelope.MessageId;
import io.zengin4j.iso20022.envelope.ZediEnvelopeWriter;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.mapping.MappingRegistry;
import io.zengin4j.testkit.FormatFixtures;
import org.junit.jupiter.api.Test;

/// R-T1 and R-T3, which name these two types.
///
/// `MappingRegistry` must be immutable and thread-safe; the mapper must
/// be stateless, with all mutable state in per-call result objects. Both were
/// claimed in Javadoc and neither was tested — and the mapper does hold mutable
/// state during a conversion: a `LossCollector` and a counter, on the
/// per-call leg objects. Whether those are genuinely per-call is the thing worth
/// checking, because "a field on a short-lived helper" and "a field on the
/// shared mapper" look identical at a glance.
///
/// Every thread converts the same file and must get byte-identical output and
/// an identical report. A shared collector would show up as reports of differing
/// length; shared output would show up as bytes that differ.
class ThreadSafetyTest {

    private static final int THREADS = 16;
    private static final int ROUNDS = 8;
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 1);

    private static ZenginFile file() {
        FormatFixtures fixtures = FormatFixtures.forFormat(FormatId.of("sougou-furikomi"));
        return ZenginReaders.readFile(
                new ByteArrayInputStream(fixtures.file(4, SeparatorStyle.CRLF, false)),
                fixtures.readerOptions());
    }

    private static MappingContext context() {
        return MappingContext.builder("9900000001", REFERENCE)
                .targetFormat(FormatFixtures.forFormat(FormatId.of("sougou-furikomi"))
                        .descriptor())
                .acceptAnyLoss()
                .build();
    }

    /// One mapper, many threads, identical results.
    ///
    /// A [CyclicBarrier] releases them together, so the conversions
    /// overlap rather than queueing behind each other.
    @Test
    void oneMapperSharedAcrossThreadsProducesIdenticalResults() throws Exception {
        var shared = Iso20022Mapper.create();
        ZenginFile file = file();
        MappingContext context = context();

        MappingResult<ZediFile> reference = shared.toIso(file, context);
        byte[] expectedBytes = ZediEnvelopeWriter.toByteArray(reference.output());
        String expectedReport = reference.loss().toText();

        var start = new CyclicBarrier(THREADS);
        List<Callable<Boolean>> work = new ArrayList<>(THREADS);
        for (int t = 0; t < THREADS; t++) {
            work.add(() -> {
                start.await();
                for (int round = 0; round < ROUNDS; round++) {
                    MappingResult<ZediFile> result = shared.toIso(file, context);
                    if (!java.util.Arrays.equals(expectedBytes,
                            ZediEnvelopeWriter.toByteArray(result.output()))) {
                        return false;
                    }
                    if (!expectedReport.equals(result.loss().toText())) {
                        return false;
                    }
                }
                return true;
            });
        }

        assertThat(runAll(work))
                .as("every thread must see the conversion it asked for, not another thread's")
                .containsOnly(true);
    }

    /// The same for the leg that writes a file rather than a message.
    @Test
    void theInverseLegIsAlsoSafeToShare() throws Exception {
        var shared = Iso20022Mapper.create();
        MappingContext context = context();
        ZediFile message = shared.toIso(file(), context).output();

        MappingResult<ZenginFile> reference = shared.toZengin(message, context);
        int expectedEntries = reference.loss().entries().size();
        int expectedPayments = reference.output().allData().size();

        var start = new CyclicBarrier(THREADS);
        List<Callable<Boolean>> work = new ArrayList<>(THREADS);
        for (int t = 0; t < THREADS; t++) {
            work.add(() -> {
                start.await();
                for (int round = 0; round < ROUNDS; round++) {
                    MappingResult<ZenginFile> result = shared.toZengin(message, context);
                    if (result.loss().entries().size() != expectedEntries
                            || result.output().allData().size() != expectedPayments) {
                        return false;
                    }
                }
                return true;
            });
        }

        assertThat(runAll(work))
                .as("a shared LossCollector would show up as reports of differing length")
                .containsOnly(true);
    }

    /// R-T1: the registry is immutable, so sharing it cannot go wrong.
    @Test
    void theRegistryCannotBeChangedByAnyoneHoldingIt() {
        MappingRegistry shared = MappingRegistry.defaults();
        List<String> before = shared.supported();

        MappingRegistry extended = shared
                .withMapping(FormatId.of("elsewhere"), MessageId.PAIN_001_001_03,
                        shared.requireRowsFor(FormatId.of("sougou-furikomi"),
                                MessageId.PAIN_001_001_03));
        MappingRegistry reduced = shared
                .without(FormatId.of("sougou-furikomi"), MessageId.PAIN_001_001_03);

        assertThat(shared.supported())
                .as("neither derivation touched the registry they came from")
                .isEqualTo(before);
        assertThat(extended.supported()).hasSize(2);
        assertThat(reduced.supported()).isEmpty();
    }

    private static List<Boolean> runAll(List<Callable<Boolean>> work) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Boolean> results = new ArrayList<>(work.size());
            for (Future<Boolean> future : pool.invokeAll(work)) {
                results.add(future.get());
            }
            return results;
        }
    }
}
