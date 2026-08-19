/// Running the rules: the engine, the registry and the context.
///
/// Three things live here so that individual rules do not have to think about
/// them: nothing escapes as an exception (R-V1), suppression and severity
/// overrides are applied in one place (R-V3), and findings are sorted
/// canonically before they leave so the same file always produces the same
/// report (INV-7).
///
/// @since 0.2.0
package io.zengin4j.validation.engine;
