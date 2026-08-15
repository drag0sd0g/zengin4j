/**
 * Structured validation of Zengin files: rules, findings and reports.
 *
 * <p>Empty until Epic 4. The module exists from Epic 1 so that the dependency
 * direction in specification §7 is expressed in the build graph rather than in
 * a diagram, and so that a {@code core -> validation} import cannot compile.
 *
 * @since 0.1.0
 */
module io.zengin4j.validation {
    requires transitive io.zengin4j.core;
}
