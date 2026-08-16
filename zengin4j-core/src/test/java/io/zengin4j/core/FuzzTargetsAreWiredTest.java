package io.zengin4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every {@code @FuzzTest} has a Gradle task that fuzzes it.
 *
 * <p>When mutating, libFuzzer terminates the JVM as each target's time budget
 * expires, so the targets cannot share one — the build gives each its own task.
 * That list is written by hand, which means it can fall behind the annotations,
 * and the failure would be silent: the new target still passes in corpus-replay
 * mode and simply never gets fuzzed. Nothing would look wrong.
 *
 * <p>So the list is compared against the annotations here. Adding a fuzz target
 * without wiring it fails the build with a message saying what to add.
 */
class FuzzTargetsAreWiredTest {

    /** Classes carrying {@code @FuzzTest} methods. Add new ones here. */
    private static final List<Class<?>> FUZZ_CLASSES = List.of(ReaderFuzzTest.class);

    @Test
    void everyFuzzTargetHasATask() {
        List<String> annotated = FUZZ_CLASSES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(FuzzTest.class))
                        .map(FuzzTargetsAreWiredTest::qualify))
                .sorted()
                .toList();

        assertThat(annotated)
                .as("@FuzzTest methods were found; the reflection below is not vacuous")
                .isNotEmpty();
        assertThat(wired())
                .as("build.gradle.kts registers a fuzz task per target; add the missing one(s) to"
                        + " fuzzTargets, or drop the stale entry")
                .containsExactlyElementsOf(annotated);
    }

    private static String qualify(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
     * The wiring, as the build reports it.
     *
     * <p>Read from a system property the {@code test} task sets. Running this
     * test outside Gradle leaves it unset, and the assertion below says so
     * rather than passing on an empty list.
     */
    private static List<String> wired() {
        String property = System.getProperty("zengin4j.fuzz.targets");
        assertThat(property)
                .as("zengin4j.fuzz.targets is set by the Gradle test task; run this through Gradle")
                .isNotNull();
        return Arrays.stream(property.split(",")).filter(value -> !value.isBlank()).sorted().toList();
    }
}
