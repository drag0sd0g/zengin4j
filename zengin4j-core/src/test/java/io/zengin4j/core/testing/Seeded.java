package io.zengin4j.core.testing;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A small property-testing harness: generate many inputs, assert a rule holds
 * for all of them.
 *
 * <p>Stands in for a property-testing library. It gives up automatic shrinking
 * — a failure arrives as generated rather than minimised — and buys that back
 * two ways. Generators here deliberately produce <em>small</em> inputs (one to
 * three records), so a failing case is already close to minimal; and every
 * case carries its own derived seed, so a failure can be replayed in isolation
 * with {@link #single} instead of re-running the whole property.
 *
 * <p>Hostile, unstructured input is not this class's job. That is fuzzing, and
 * {@code ReaderFuzzTest} hands it to Jazzer, which is coverage-guided and does
 * it far better than random generation can.
 *
 * <p>See {@code docs/adr/0017-property-testing-without-jqwik.md}.
 */
public final class Seeded {

    /** Cases per property unless a test asks for more. */
    public static final int DEFAULT_CASES = 400;

    private Seeded() {
    }

    /**
     * Checks that a rule holds across many generated inputs.
     *
     * @param name      what the rule is, for the failure message
     * @param cases     how many inputs to try
     * @param seed      the root seed; fixed per property so runs are
     *                  reproducible and CI cannot flake
     * @param generator builds one input from a source of randomness
     * @param check     asserts the rule; throwing means the rule does not hold
     * @param <T>       the generated input type
     */
    public static <T> void property(
            String name, int cases, long seed, Function<Random, T> generator, Consumer<T> check) {

        for (int index = 0; index < cases; index++) {
            long caseSeed = derive(seed, index);
            T value;
            try {
                value = generator.apply(new Random(caseSeed));
            } catch (RuntimeException e) {
                throw new AssertionError("generator for '" + name + "' failed at case " + index
                        + " (caseSeed " + caseSeed + "L)", e);
            }
            try {
                check.accept(value);
            } catch (Throwable failure) {
                throw new AssertionError(name + " does not hold.\n"
                        + "  case      : " + index + " of " + cases + "\n"
                        + "  caseSeed  : " + caseSeed + "L\n"
                        + "  replay    : Seeded.single(" + caseSeed + "L, generator, check)\n"
                        + "  input     : " + describe(value), failure);
            }
        }
    }

    /**
     * Checks a rule against one input, for replaying a reported failure.
     *
     * @param caseSeed  the seed the failure reported
     * @param generator the same generator
     * @param check     the same check
     * @param <T>       the generated input type
     */
    public static <T> void single(long caseSeed, Function<Random, T> generator, Consumer<T> check) {
        check.accept(generator.apply(new Random(caseSeed)));
    }

    /**
     * Derives an independent seed per case, so a failing case can be replayed
     * on its own rather than by re-running everything before it. SplitMix64's
     * finalising mix, which decorrelates neighbouring counter values.
     */
    private static long derive(long seed, int index) {
        long z = seed + (index + 1) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static String describe(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes.length + " bytes";
        }
        String text = String.valueOf(value);
        return text.length() > 400 ? text.substring(0, 400) + "… (truncated)" : text;
    }
}
