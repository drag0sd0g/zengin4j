# Architecture decision records

R-0.6 requires an ADR for every significant decision and every interpretation call made while
implementing. An implementation that makes twenty undocumented judgement calls is not reviewable.

| # | Decision | Status |
|---|---|---|
| [0001](0001-hand-written-yaml-reader.md) | A hand-written YAML subset reader in `core` | **Superseded by 0016** |
| [0002](0002-java-21-baseline-without-a-toolchain.md) | Java 21 baseline via `--release`, not a toolchain | **Superseded by 0036** |
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
| [0017](0017-property-testing-without-jqwik.md) | Property testing without jqwik: seeded generators plus Jazzer | Accepted |
| [0018](0018-golden-files-are-text-not-json.md) | Golden files are a text rendering, not JSON | Accepted |
| [0019](0019-building-gates-on-verified.md) | Building a file gates on `verified`; writing one does not | Accepted |
| [0020](0020-one-descriptor-for-type-code-91.md) | One descriptor for 種別コード 91: the two layouts are identical | Accepted |
| [0021](0021-the-shared-header-date-is-effective-date.md) | The shared header date is `effectiveDate`; formats keep their own names | Accepted |
| [0022](0022-hand-written-json-and-sarif.md) | JSON and SARIF are written by hand, and checked by a real parser | Accepted |
| [0023](0023-holidays-are-data-not-an-algorithm.md) | The holiday calendar is data, and refuses to guess past it | Accepted |
| [0024](0024-picocli-for-the-cli.md) | picocli parses the command line, and only the CLI depends on it | Accepted |
| [0025](0025-warnings-exit-non-zero.md) | A file with only warnings exits 1 | Accepted |
| [0026](0026-what-unsafe-print-actually-gates.md) | What `--unsafe-print` actually gates | Accepted |
| [0027](0027-diff-aligns-records-rather-than-positions.md) | `diff` aligns records rather than comparing positions | Accepted |
| [0028](0028-the-specifications-kana-mappings-are-wrong.md) | Two of the specification's kana mappings are wrong | Accepted |
| [0029](0029-transliteration-lives-in-core.md) | Transliteration and the loss vocabulary live in `core` | Accepted |
| [0030](0030-kana-tables-are-derived-not-transcribed.md) | The kana tables are derived; only the judgement calls are declared | Accepted |
| [0031](0031-hand-written-iso20022-xml.md) | ISO 20022 XML is written by hand, not bound from schemas | Accepted |
| [0032](0032-splitting-on-declaration-boundaries.md) | The ZEDI envelope is split on declaration boundaries, and the split is checked | Accepted |
| [0033](0033-critical-loss-fails-by-default.md) | A critical loss stops the conversion by default | Accepted |
| [0034](0034-the-mapping-context-is-flags-not-a-file.md) | The mapping context is command-line flags, not a context file | Accepted |
| [0035](0035-the-mapping-is-data-not-a-rule-engine.md) | The mapping is declared data, not an executable rule interface | Accepted |
| [0036](0036-java-25-baseline.md) | The baseline moves from Java 21 to Java 25 | Accepted |
| [0037](0037-markdown-doc-comments-and-module-imports.md) | Markdown doc comments throughout, and `import module java.base` | Accepted |

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
