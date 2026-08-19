package io.zengin4j.iso20022.envelope;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.junit.FuzzTest;
import io.zengin4j.core.error.ZenginException;
import org.junit.jupiter.api.Tag;

/// The envelope reader, against bytes nobody chose (R-T9).
///
/// Worth fuzzing for a reason the fixed-length reader is not: this one scans
/// for a byte sequence and cuts a file at every occurrence, so the interesting
/// inputs are the ones where `<?xml` lands somewhere structurally
/// surprising — at the very end, twice in a row, inside an attribute, one byte
/// short of the end of the buffer. Those are exactly the inputs random bytes
/// essentially never produce and coverage-guided mutation finds quickly.
///
/// Two promises are under test.
///
/// - **Termination and typed failure.** Any byte sequence
///   either reads or raises a [ZenginException]. Anything else — a
///   `StringIndexOutOfBoundsException`, a hang, an
///   `OutOfMemoryError` — is a defect, because this reader's whole
///   job is to survive a file somebody else produced.
/// - **R-I6: what was read is written back byte for byte.**
///   Held for *every* input the reader accepts, not just for
///   well-formed ones, which is the strongest form the requirement has.
///
/// Tagged `fuzz`, so it runs from its own tasks:
///
/// ```
/// ./gradlew :zengin4j-iso20022:fuzz            # replay the corpora: deterministic, in check
/// ./gradlew :zengin4j-iso20022:fuzzAll         # actually mutate, each target in its own JVM
/// ./gradlew :zengin4j-iso20022:fuzzSplitting   # just one target
/// ```
@Tag("fuzz")
class EnvelopeFuzzTest {

    /// Splitting arbitrary bytes terminates and fails only in the declared way.
    ///
    /// @param input bytes supplied by the fuzzer
    @FuzzTest(maxDuration = "60s")
    void splittingNeverMisbehaves(byte[] input) {
        try {
            ZediEnvelopeReader.read(input);
        } catch (ZenginException expected) {
            // A file that is not a ZEDI file is not a defect. Anything outside
            // this hierarchy is.
        }
    }

    /// R-I6 over arbitrary input: whatever the reader accepts, the writer
    /// reproduces exactly.
    ///
    /// @param input bytes supplied by the fuzzer
    @FuzzTest(maxDuration = "60s")
    void anythingReadableIsWrittenBackUnchanged(byte[] input) {
        ZediFile file;
        try {
            file = ZediEnvelopeReader.read(input);
        } catch (ZenginException notAZediFile) {
            return;
        }

        assertThat(ZediEnvelopeWriter.toByteArray(file))
                .as("the reader accepted %d bytes and the writer did not reproduce them",
                        input.length)
                .isEqualTo(input);
    }
}
