package io.zengin4j.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as machine-generated from a format descriptor (R-M8).
 *
 * <p>Generated sources are committed so that the repository is browsable
 * without running the build, and are never hand-edited — a build task
 * regenerates them and fails if the committed output has drifted.
 *
 * <p>Retention is {@code CLASS} rather than {@code SOURCE} on purpose: coverage
 * and review tooling identify generated code by looking for a retained
 * annotation whose simple name contains {@code Generated}.
 * {@code javax.annotation.processing.Generated} has {@code SOURCE} retention
 * and is therefore invisible to those tools, which is why this library declares
 * its own.
 *
 * @since 0.1.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Generated {

    /**
     * Returns the name of the generator that produced the annotated type.
     *
     * @return the generator name
     */
    String value();

    /**
     * Returns the descriptor the annotated type was generated from.
     *
     * @return the source descriptor resource name
     */
    String source();
}
