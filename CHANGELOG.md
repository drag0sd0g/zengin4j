# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Any change to parsed output for the same input bytes is a major version bump** (R-B10). That
includes a corrected byte offset: a descriptor fix changes what a file means.

## [Unreleased]

### Added — Epic 1, walking skeleton

- **Reading 総合振込 (`21`) files.** Three APIs, in increasing order of convenience and decreasing
  order of speed: `ZenginReaders.open` (a record at a time, zero copies), `ZenginReaders.batches`
  (a batch at a time, materialised) and `ZenginReaders.readFile` (the whole file).
- **Format descriptors as data.** Byte offsets computed from cumulative field lengths, never
  transcribed; field lengths must sum to the record length or the build fails; `verified` and
  `sources` on every descriptor, with `verified: true` refused below two citations.
- **Descriptors compiled at build time.** They are authored as YAML in `zengin4j-core/formats/`;
  the build reads them and emits committed Java, so `zengin4j-core` requires nothing but
  `java.base` and ships neither a parser nor descriptor resources. A layout that does not add up
  fails the build (ADR-0016, superseding ADR-0001).
- **`RecordDescriptor.of` / `FieldSpec`**, which compute byte offsets from cumulative field
  lengths. Generated code and consumer-supplied descriptors alike get R-F2 by construction; there
  is no supported way to write an offset by hand.
- **Generated, format-shaped record types**, committed and checked for drift against the
  descriptors on every build.
- **Generated format documentation** in `docs/formats/`, carrying a visible banner when a layout is
  unverified.
- **Framing**: optional separators (none, CR, LF, CRLF, and mixed within one file), UTF-8 byte order
  marks, and a trailing `0x1A`.
- **Strict and lenient parsing.** In lenient mode a record that does not fit the format becomes a
  `MalformedRecord` and reading continues, resynchronising by exactly one record length.
- **Year inference** for the yearless `MMDD` dates, with `FORWARD_LOOKING` and `NEAREST` strategies
  and an explicit report when 29 February falls outside every candidate year.
- **EBCDIC detection.** A file declaring コード区分 `1` is rejected by name rather than decoded as
  JIS. Pulled forward from Epic 3; see ADR-0010.
- **Stale record view detection.** Retaining a view across an iteration raises
  `StaleRecordViewException` instead of returning the next record's bytes. See ADR-0008.
- **Account-number masking** in every `toString`, to the last four digits.
- **`zengin4j-testkit`**, published rather than test-scoped, with synthetic fixtures and a
  deterministic seeded generator. Every identifier in it is invented.

### Verified — source research, 2026-08-15

A research pass against published sources. No parsed output changed, so no version bump is implied
(R-B10) — the layout was corroborated, not corrected.

- **The 総合振込 layout is corroborated by six independent publications**, including the JBA's own
  protocol document. They agree on every offset and every length. Cited in
  [docs/SOURCES.md](docs/SOURCES.md).
- **All six code lists are now `verified: true`** with citations, and `CodeList` carries its own
  `sources` with the same two-source rule the loader enforces for formats (R-0.1). The values the
  build specification flagged `[VERIFY]` — 貯蓄預金 `4`, 新規コード, 振込指定区分 — are confirmed.
  種別コード gains `41`, `71` and `72` from the standard.
- **The format stays `verified: false`**, held by [D-002](docs/DISCREPANCIES.md): sources disagree
  on whether 顧客コード1/2 are `N` or `C`. R-0.2 keeps a format unverified until a field-level
  disagreement is settled, however solid the rest is. See
  [ADR-0015](docs/adr/0015-customer-code-declared-as-text.md).
- **Empirical corroboration**: decoding a third-party sample (`Kyash/zengin-go`) with these offsets
  reconciles that file's own trailer totals — a check no specification document can provide (R-T17).
- **Six open questions closed, two reframed.** Notably, the two 種別コード `91` formats have
  *identical* layouts and differ only in values, which may make `AmbiguousFormatException` a
  solution to a non-problem; and the 振替結果コード list — the specification's "highest-value
  verification item" — is now enumerated, including one code the specification had guessed wrong.
  Work arising is indexed by epic in [docs/OPEN_QUESTIONS.md](docs/OPEN_QUESTIONS.md).

### Added — Epic 2, writing and round-trip proof

- **`ZenginWriters`**, which writes a file back to bytes, a stream or a path. Records are emitted
  from the raw bytes they carry rather than re-encoded (R-D5), so filler this library does not
  interpret survives untouched, and records the reader could not parse keep their place. Writing is
  deterministic: the same file writes the same bytes every time (R-C19).
- **Byte-exact round tripping (INV-1)**, now demonstrable rather than intended. A file read and
  written again reproduces its input byte for byte, including its byte order mark, its separator
  convention, whether a separator followed the *last* record, and a trailing `0x1A`.
- **`ZenginFileBuilder`**, a fluent builder that computes each batch trailer's record count and
  total amount from the payments it contains, and numbers records in file order. An explicit
  trailer overrides the computed one — which is how a fixture for "trailer disagrees with its
  contents" gets built for the validation rules in Epic 4. Built records are the same generated,
  format-shaped types the reader produces, not a parallel model.
- **`WriterOptions`**, which defaults to reproducing the framing the file arrived with, and can
  impose a separator convention instead. A file that mixed conventions within itself cannot be
  reproduced and is refused by name; imposing a convention makes it writable again (R-C9).
- **`RecordEncoder`**, which builds a record frame from field values, filling constants and padding
  by field type — `N` right-aligned and zero-padded, `C` left-aligned and space-padded. The testkit
  now delegates to it rather than carrying its own copy.
- **`FileFraming.trailingSeparator()`**, closing [OQ-4](docs/OPEN_QUESTIONS.md). Whether a separator
  followed the final record is part of the file and is reproduced; `conventional()` — the builder's
  default, for a file that was never read — emits one, per the 後付け framing published record
  lengths describe.
- **Property tests** covering INV-1, INV-2, INV-3, INV-6 and INV-8, plus idempotence and
  determinism, over seeded generators. A failure reports the case seed and the one-line call that
  replays that case alone.
- **Coverage-guided fuzzing** of the reader and the read-write pair with Jazzer (R-T9).
  `./gradlew :zengin4j-core:fuzz` replays the committed corpora — deterministic, about two seconds,
  and part of `check`, so a found input becomes a permanent regression test. `fuzzAll` mutates,
  one target per JVM, and runs nightly in CI. It has already found one input: a file whose
  separator run mixes CR and CRLF is readable but not reproducible, which the writer must refuse by
  name rather than guess at — the property said "anything readable is writable", and the honest
  version of it also asserts the refusal.
- **A golden-file corpus** (R-T8): a committed 総合振込 file and a committed field-per-line
  rendering of what parsing it produces, so a change in how bytes are decoded shows up as a diff a
  reviewer can read. Regenerate with `-Pgolden.regenerate`, then read the diff. The rendering is
  text rather than the JSON R-T8 names — `core` has no JSON writer and is not getting one, and a
  flat record diffs better as lines than as a tree
  ([ADR-0018](docs/adr/0018-golden-files-are-text-not-json.md)).
- **`examples/BuildSougouFurikomi.java`** (UC-6, R-DOC6): building a well-formed fixture, building
  one whose trailer deliberately disagrees with its payments, and showing that the bytes are the
  same on every run. CI now runs every file in `examples/` rather than a named one.

### Changed — Epic 2

- **Property testing does not use jqwik**, whose author asks that the library not be used in
  AI-assisted development. R-T7 names it; this project honours the request and keeps the
  requirement. What replaces it is about sixty lines of seeded generation, and what that gives up —
  shrinking — is recorded in [ADR-0017](docs/adr/0017-property-testing-without-jqwik.md).
- **Mutation testing now measures production code only.** `--targetClasses io.zengin4j.core.*`
  matched everything on the class path, tests included. Mutating a test is meaningless — nothing
  asserts on a test's own logic, so every such mutant survives — and it diluted the score in
  proportion to how much test code existed. The previously published **84% was measured wrongly**;
  the same commit scores **87%** once tests are excluded, and 89% after the tests added below. The
  exclusion is derived from the compiled test output rather than from a naming convention.
- **`ZenginWriters` allocates the exact output length** instead of estimating a capacity and
  copying out of a growable buffer. Record lengths are summed individually, because a malformed
  record read in lenient mode may be shorter than the format's and still has to be written back.
- **The identifier scan excludes conformance corpora under `input/`**, for a structural reason: in
  a fixed-length record fields abut with no separator, so every digit run begins with a データ区分
  constant and no conformant file can pass a bare-digit-run check. The committed field-per-line
  rendering of each corpus is *not* excluded, and contains every field of every record, so a real
  identifier is still caught where a digit run means a single field.

### Documented — Epic 2

- **Writing has no `verified` gate, and [DISCLAIMER.md](DISCLAIMER.md) now says so.** Reading an
  unverified descriptor requires `allowUnverifiedFormats(true)`; `ZenginFileBuilder` and
  `ZenginWriters` take a descriptor and use it. The consequences are asymmetric in the direction
  the current design does *not* protect — a wrong offset when reading gives you bad data in your
  own system, a wrong offset when writing sends a bad instruction to a bank. Recorded as
  [OQ-10](docs/OPEN_QUESTIONS.md) to be decided before 1.0, since adding the gate later is
  breaking.
- Both READMEs gain a builder and round-trip quickstart, and their `Building` sections list the
  fuzzing and golden-file commands. The Japanese one had also fallen behind on `pitest`.

### Known limitations

- **Every bundled format descriptor is `verified: false`**, though not for want of evidence: the
  総合振込 offsets are corroborated by six independent sources including the JBA standard, and a
  single unresolved field-attribute disagreement ([D-002](docs/DISCREPANCIES.md)) holds the flag.
  Reading still requires `allowUnverifiedFormats(true)`, and output must be validated against your
  institution's specification. See [DISCLAIMER.md](DISCLAIMER.md).
- Only 総合振込 (`21`) is implemented. 給与振込, 賞与振込, 預金口座振替 and the 200-byte formats
  follow in Epics 3 and 8.
- No validation layer, no CLI, no transliteration engine, no ISO 20022 mapping. Epics 4 to 7.
- Over-length records are supported only through an explicit record-length override (OQ-3).
- **A file that mixed separator conventions within itself cannot be written back byte-exactly.**
  There is no convention to reproduce, so INV-1 does not apply; the writer says so rather than
  guessing, and `WriterOptions.separator` resolves it deliberately.
- **The committed fuzzing corpus is one input.** Fuzzing has found exactly one thing so far, and it
  is committed and replayed on every build; the working corpus is local and untracked. The corpus
  grows as nightly runs find more.
- The JMH benchmarks (R-P1) and the 1 GB constant-memory job (R-P2) are not yet set up; they belong
  to Epic 3. No performance number is published, because none has been measured (P9).

### Quality gates

`./gradlew build` enforces, on every run: the Java 21 baseline, the tests, ≥ 90% line and ≥ 85%
branch coverage on `core`, the ArchUnit module rules, descriptor consistency, and that the
committed generated sources match the descriptors, and the committed fuzzing corpora replay. Current
figures: 221 tests, 95.3% line and 90.1% branch coverage.

Mutating fuzz runs are not part of `check` — they are nightly, via `fuzzAll`. Replaying what
fuzzing has already found is, because it is deterministic and costs about two seconds.

Mutation testing (R-T15) is an opt-in task — `./gradlew :zengin4j-core:pitest` — because it takes
about forty seconds rather than the second `check` takes. Current score: 89%, against a threshold of
80%.

Most of what still survives is in `StreamingZenginReader`'s buffer management — compaction and
growth arithmetic reachable only when the buffer boundary lands in a particular place. Some of it is
now covered; the rest is known and not yet earned.

[Unreleased]: https://example.invalid/zengin4j/compare/main...HEAD
