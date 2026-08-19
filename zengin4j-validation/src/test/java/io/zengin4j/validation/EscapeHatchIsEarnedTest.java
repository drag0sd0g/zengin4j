package io.zengin4j.validation;

import module java.base;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.zengin4j.testkit.FormatFixtures;
import io.zengin4j.testkit.SougouFurikomiFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// Every use of `dataUnchecked` is one that could not be written otherwise.
///
/// `FormatFixtures.dataUnchecked` exists so a validator's test suite can
/// build the records the validator exists to complain about — the ordinary
/// encoder refuses them, correctly. That makes it the one call in the testkit
/// that deliberately produces an invalid file, and the obvious failure mode is
/// habit: somebody hits a refusal, reaches for the unchecked path because it
/// makes the red go away, and quietly stops testing what they meant to.
///
/// So each call site has to earn it. This finds them by reading the sources
/// and asserts that the *checked* path really would refuse the same value.
/// A call that did not need the escape hatch fails here.
class EscapeHatchIsEarnedTest {

    /// Directories a call site may legitimately live in.
    private static final List<Path> SEARCHED = List.of(
            Path.of("..", "zengin4j-validation", "src", "test", "java"),
            Path.of("..", "zengin4j-core", "src", "test", "java"),
            Path.of("..", "zengin4j-cli", "src", "test", "java"),
            Path.of("..", "zengin4j-testkit", "src", "test", "java"),
            Path.of("..", "examples"));

    /// `dataUnchecked("…", …)` — the first argument is the value under test.
    private static final Pattern CALL =
            Pattern.compile("dataUnchecked\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /// A call site: where it is, and the name it writes.
    private record CallSite(String file, int line, String name) {
        @Override
        public String toString() {
            return file + ":" + line + "  '" + name + "'";
        }
    }

    static List<CallSite> callSites() throws IOException {
        List<CallSite> sites = new ArrayList<>();
        for (Path root : SEARCHED) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .toList()) {

                    // The declaration and its implementation are not call sites.
                    String name = file.getFileName().toString();
                    if (name.equals("FormatFixtures.java")
                            || name.equals("AbstractFormatFixtures.java")
                            || name.equals("EscapeHatchIsEarnedTest.java")) {
                        continue;
                    }
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        Matcher matcher = CALL.matcher(lines.get(i));
                        while (matcher.find()) {
                            sites.add(new CallSite(name, i + 1, matcher.group(1)));
                        }
                    }
                }
            }
        }
        return sites;
    }

    @Test
    void thereAreCallSitesToCheck() throws IOException {
        assertThat(callSites())
                .as("if this is empty the pattern has drifted and the test proves nothing")
                .isNotEmpty();
    }

    /// The checked path refuses what each unchecked call writes.
    ///
    /// If it does not, the call site should be using `data(...)` — the
    /// value is conformant and the escape hatch is buying nothing but a hole in
    /// the coverage of the encoder's own rules.
    @ParameterizedTest
    @MethodSource("callSites")
    void everyUncheckedCallWritesSomethingTheCheckedPathWouldRefuse(CallSite site) {
        FormatFixtures fixtures = SougouFurikomiFixtures.create();

        Throwable refusal = catchThrowable(() ->
                fixtures.data(site.name(), 150_000L, "9876543"));

        assertThat(refusal)
                .as("%s uses dataUnchecked, but the ordinary encoder accepts '%s'."
                        + " Use data(...) — the escape hatch is for records that could not"
                        + " otherwise be built, not for making a refusal go away.",
                        site, site.name())
                .isNotNull();
    }

    /// And the unchecked path really does write it.
    ///
    /// The other half: an escape hatch that also refused would leave these
    /// tests silently untested.
    @ParameterizedTest
    @MethodSource("callSites")
    void everyUncheckedCallActuallyProducesARecord(CallSite site) {
        FormatFixtures fixtures = SougouFurikomiFixtures.create();

        byte[] record = fixtures.dataUnchecked(site.name(), 150_000L, "9876543");

        assertThat(record)
                .as("%s should produce a record of the declared length", site)
                .hasSize(fixtures.descriptor().recordLength());
    }
}
