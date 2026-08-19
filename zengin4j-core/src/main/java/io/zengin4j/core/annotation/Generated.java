package io.zengin4j.core.annotation;

import module java.base;

/// Marks a type as machine-generated from a format descriptor (R-M8).
///
/// Generated sources are committed so that the repository is browsable
/// without running the build, and are never hand-edited — a build task
/// regenerates them and fails if the committed output has drifted.
///
/// Retention is `CLASS` rather than `SOURCE` on purpose: coverage
/// and review tooling identify generated code by looking for a retained
/// annotation whose simple name contains `Generated`.
/// `javax.annotation.processing.Generated` has `SOURCE` retention
/// and is therefore invisible to those tools, which is why this library declares
/// its own.
///
/// @since 0.1.0
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Generated {

    /// Returns the name of the generator that produced the annotated type.
    ///
    /// @return the generator name
    String value();

    /// Returns the descriptor the annotated type was generated from.
    ///
    /// @return the source descriptor resource name
    String source();
}
