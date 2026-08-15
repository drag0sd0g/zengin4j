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

### Known limitations

- **Every bundled format descriptor is `verified: false`**, though not for want of evidence: the
  総合振込 offsets are corroborated by six independent sources including the JBA standard, and a
  single unresolved field-attribute disagreement ([D-002](docs/DISCREPANCIES.md)) holds the flag.
  Reading still requires `allowUnverifiedFormats(true)`, and output must be validated against your
  institution's specification. See [DISCLAIMER.md](DISCLAIMER.md).
- Only 総合振込 (`21`) is implemented. 給与振込, 賞与振込, 預金口座振替 and the 200-byte formats
  follow in Epics 3 and 8.
- **No writer yet**, so byte-exact round tripping (INV-1) is not yet demonstrable. Epic 2.
- No validation layer, no CLI, no transliteration engine, no ISO 20022 mapping. Epics 4 to 7.
- Over-length records are supported only through an explicit record-length override (OQ-3).
- Fuzzing (R-T9), the JMH benchmarks (R-P1) and the 1 GB constant-memory job (R-P2) are not yet set
  up; they belong to Epic 3. No performance number is published, because none has been measured
  (P9).

### Quality gates

`./gradlew build` enforces, on every run: the Java 21 baseline, the tests, ≥ 90% line and ≥ 85%
branch coverage on `core`, the ArchUnit module rules, descriptor consistency, and that the
committed generated sources match the descriptors. Current figures: 179 tests, 94.7% line and 88.7%
branch coverage.

Mutation testing (R-T15) is an opt-in task — `./gradlew :zengin4j-core:pitest` — because it takes
about forty seconds rather than the second `check` takes. Current score: 84%, against a threshold of
80%.

[Unreleased]: https://example.invalid/zengin4j/compare/main...HEAD
