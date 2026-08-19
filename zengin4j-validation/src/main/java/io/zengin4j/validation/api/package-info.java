/// What validation produces: findings, reports and the rule contract.
///
/// A [io.zengin4j.validation.api.Finding] says *where* — record,
/// byte offset, field — because the person reading it has to open a
/// 120-byte-per-line file and find the problem (R-V2). It carries both languages
/// at once rather than resolving one against a locale, since a Japanese
/// operations team and an English-speaking integrator frequently read the same
/// report.
///
/// Message text lives in properties files, never in string literals (R-E4),
/// so a translation is reviewable as a diff.
///
/// @since 0.2.0
package io.zengin4j.validation.api;
