package io.zengin4j.core;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.code_intelligence.jazzer.junit.FuzzTest;
import io.zengin4j.core.codec.ParseMode;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.codec.RecordView;
import io.zengin4j.core.codec.WriterOptions;
import io.zengin4j.core.codec.ZenginReader;
import io.zengin4j.core.codec.ZenginReaders;
import io.zengin4j.core.codec.ZenginWriters;
import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.format.FormatRegistry;
import io.zengin4j.core.model.SeparatorStyle;
import io.zengin4j.core.model.ZenginFile;
import org.junit.jupiter.api.Tag;

/// INV-3 by coverage-guided fuzzing (R-T9).
///
/// `InvariantProperties` covers the same invariant with random
/// generation and runs on every build. This does it properly: Jazzer watches
/// which branches an input reaches and mutates towards the ones it has not, so
/// it finds the input that walks past the separator-skipping loop or wedges the
/// buffer refill — inputs random bytes essentially never produce.
///
/// Tagged `fuzz`, so it runs from its own tasks:
///
/// ```
/// ./gradlew :zengin4j-core:fuzz          # replay the corpora: fast, deterministic, in check
/// ./gradlew :zengin4j-core:fuzzAll       # actually mutate, each target in its own JVM
/// ./gradlew :zengin4j-core:fuzzReading   # just one target
/// ```
///
/// Each target gets its own task because libFuzzer terminates the JVM when a
/// target's time budget expires: two mutating targets in one JVM means the
/// second never runs, and the build fails on a missing results file rather than
/// on anything that names the cause. `FuzzTargetsAreWiredTest` fails the
/// build if a target here has no task.
///
/// Jazzer's JUnit integration switches on the `JAZZER_FUZZ`
/// environment variable rather than a system property, which the Gradle task
/// sets. Its working corpus lives in `.cifuzz-corpus/` and is not
/// committed — it is scratch space, and nightly CI caches it so each run starts
/// where the last stopped.
///
/// What *is* committed is
/// `src/test/resources/io/zengin4j/core/ReaderFuzzTestInputs/`: inputs
/// fuzzing actually found. Replay mode runs them on every build, so each one is
/// a permanent regression test rather than a night's result nobody reads
/// (R-T9).
///
/// Any input that escapes [ZenginException], hangs, or exhausts memory
/// is a defect. The reader's promise is that malformed input is *data*
/// (R-E1) — there is no such thing as a byte sequence it may crash on.
@Tag("fuzz")
class ReaderFuzzTest {

    private static final int RECORD_LIMIT = 100_000;

    private static final FormatRegistry REGISTRY = FormatRegistry.defaults();

    /// Reading arbitrary bytes must terminate and must not throw anything
    /// outside the declared hierarchy.
    ///
    /// @param input bytes supplied by the fuzzer
    @FuzzTest(maxDuration = "60s")
    void readingNeverMisbehaves(byte[] input) {
        read(input, ParseMode.STRICT);
        read(input, ParseMode.LENIENT);
    }

    /// Anything the reader accepts, the writer must be able to render again —
    /// and rendering it must not lose or invent bytes. This reaches further
    /// than [#readingNeverMisbehaves], because it only runs for inputs
    /// the parser accepted, which is where the interesting state lives.
    ///
    /// @param input bytes supplied by the fuzzer
    @FuzzTest(maxDuration = "60s")
    void anythingReadableIsWritable(byte[] input) {
        ZenginFile file;
        try {
            file = ZenginReaders.readFile(new ByteArrayInputStream(input), options(ParseMode.STRICT));
        } catch (ZenginException expected) {
            return;
        }
        if (!file.framing().isReproducible()) {
            // Found by fuzzing, and the right behaviour rather than a defect: a
            // file that mixed separator conventions has no convention to
            // reproduce. The writer owes a refusal by name, and must become
            // able to write it once a convention is imposed (R-C9).
            assertThatExceptionOfType(FormatDescriptorException.class)
                    .isThrownBy(() -> ZenginWriters.toByteArray(file, WriterOptions.defaults()));
            assertThat(ZenginWriters.toByteArray(file, WriterOptions.separator(SeparatorStyle.CRLF)))
                    .isNotEmpty();
            return;
        }
        byte[] written = ZenginWriters.toByteArray(file, WriterOptions.defaults());
        ZenginFile reread = ZenginReaders.readFile(
                new ByteArrayInputStream(written), options(ParseMode.STRICT));

        assertThat(reread.totalRecords()).isEqualTo(file.totalRecords());
        assertThat(ZenginWriters.toByteArray(reread, WriterOptions.defaults())).isEqualTo(written);
    }

    private static void read(byte[] input, ParseMode mode) {
        try (ZenginReader reader = ZenginReaders.open(new ByteArrayInputStream(input), options(mode))) {
            int records = 0;
            while (reader.hasNext() && records < RECORD_LIMIT) {
                RecordView view = reader.next();
                view.kind();
                view.rawBytes();
                records++;
            }
            if (records >= RECORD_LIMIT) {
                throw new AssertionError("reader did not terminate within " + RECORD_LIMIT + " records");
            }
        } catch (ZenginException expected) {
            assertThat(expected.messageEn()).isNotBlank();
            assertThat(expected.messageJa()).isNotBlank();
        }
    }

    private static ReaderOptions options(ParseMode mode) {
        return ReaderOptions.builder()
                .registry(REGISTRY)
                .allowUnverifiedFormats(true)
                .mode(mode)
                .warningListener(warning -> {
                })
                .build();
    }
}
