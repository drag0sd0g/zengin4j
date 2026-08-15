# Architecture decision records

R-0.6 requires an ADR for every significant decision and every interpretation call made while
implementing. An implementation that makes twenty undocumented judgement calls is not reviewable.

| # | Decision | Status |
|---|---|---|
| [0001](0001-hand-written-yaml-reader.md) | A hand-written YAML subset reader in `core` | **Superseded by 0016** |
| [0002](0002-java-21-baseline-without-a-toolchain.md) | Java 21 baseline via `--release`, not a toolchain | Accepted |
| [0003](0003-codegen-as-a-separate-module.md) | Code generation lives in its own unpublished module | Accepted |
| [0004](0004-generated-record-shape.md) | Generated record shape and the field-id convention | Accepted |
| [0005](0005-record-equality-by-raw-bytes.md) | Record equality is defined by raw bytes | Accepted |
| [0006](0006-immutable-format-registry.md) | The format registry is immutable, so it has no `register` | Accepted |
| [0007](0007-ambiguous-type-code-fails.md) | An ambiguous 種別コード fails rather than guesses | Accepted |
| [0008](0008-stale-record-view-detection.md) | Stale record views are detected, not merely documented | Accepted |
| [0009](0009-warnings-without-a-logging-framework.md) | Warnings go to a listener, not a logging framework | Accepted |
| [0010](0010-ebcdic-rejection-pulled-forward.md) | EBCDIC detection pulled forward from Epic 3 | Accepted |
| [0011](0011-byte-order-mark-rejected-by-default.md) | A byte order mark is rejected by default | Accepted |
| [0012](0012-coverage-excludes-generated-code.md) | The coverage gate excludes generated code | Accepted |
| [0013](0013-package-cycle-with-generated-code.md) | One package cycle is accepted, between codec and generated code | Accepted |
| [0014](0014-leap-day-is-never-resolved-backwards.md) | A leap day is never resolved backwards | Accepted |
| [0015](0015-customer-code-declared-as-text.md) | 顧客コード1/2 are declared `C`, against the standard's `N` | Accepted |
| [0016](0016-descriptors-compiled-at-build-time.md) | Descriptors are compiled at build time, not parsed at runtime | Accepted |

## Template

```markdown
# NNNN — Title

**Status:** Accepted | Superseded by NNNN
**Requirements:** R-xx, R-yy

## Context
What forced a decision. What the specification says, and where it stops saying it.

## Decision
What was done.

## Consequences
What this costs, what it buys, and what would make it wrong.
```
