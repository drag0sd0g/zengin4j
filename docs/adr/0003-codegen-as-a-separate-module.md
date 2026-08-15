# 0003 — Code generation lives in its own unpublished module

**Status:** Accepted
**Requirements:** R-F1, R-F3, R-F4, R-M8

## Context

§13 requires a Gradle source-generation task — explicitly not an annotation processor, because the
output is committed and simpler to debug. The generator must read the descriptors, and the runtime
must read the same descriptors. Two readers would be two chances to disagree about a byte layout.

Gradle's usual home for build logic is `buildSrc`, but `buildSrc` cannot depend on a project of the
main build, so a generator living there would need its own copy of the descriptor reader.

## Decision

A regular subproject, `zengin4j-codegen`, that depends on `zengin4j-core` and is not published: no
sources jar, no javadoc jar, no JPMS descriptor. It exposes three Gradle tasks:

| Task | Purpose |
|---|---|
| `generateFormatSources` | Rewrites the committed record classes, `docs/formats/*.md` and the descriptor index |
| `verifyFormatDescriptors` | Fails the build if any descriptor is internally inconsistent (R-F1) |
| `checkGeneratedSources` | Fails the build if the committed output has drifted from the descriptors (R-M8) |

The last two are wired into `check`, so `./gradlew build` enforces both.

There is no task cycle: the generated sources are committed, so `core` compiles without running the
generator, and the generator runs against the compiled `core`.

## Consequences

**Cost.** One more module in the graph than Appendix C's skeleton shows, and a bootstrap step — the
very first generation needed a placeholder for the class the codec references. That is a one-time
cost already paid.

**Benefit.** One descriptor reader, so drift is structurally impossible rather than merely unlikely.
`checkGeneratedSources` was verified by hand editing a generated offset constant: the build failed
and named the file.

**Since ADR-0016** this module is the *only* place descriptors are read. The runtime no longer
reads them at all — it receives them as generated Java — which removed the second reader this ADR
was written to avoid needing.

**What would make this wrong.** Nothing yet identified. If Gradle gains a supported way for
`buildSrc` to depend on main-build projects, moving there would remove a module from the graph
without changing anything else.
