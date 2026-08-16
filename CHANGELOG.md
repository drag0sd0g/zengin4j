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

- Both READMEs gain a builder and round-trip quickstart, and their `Building` sections list the
  fuzzing and golden-file commands. The Japanese one had also fallen behind on `pitest`.
- **A `.gitattributes`.** Without one, GitHub's Windows runners check out with
  `core.autocrlf=true`, every LF in a committed fixture becomes CRLF, and the golden-file test
  fails on Windows and nowhere else — with a diff of invisible characters. Fixture directories are
  marked binary explicitly rather than left to git's NUL heuristic, which a fixed-length record of
  ASCII digits and spaces would defeat. `GoldenFileTest` now names this failure if it recurs.

### Changed — write-side verification gate (breaking)

- **`ZenginFileBuilder.build()` now refuses a `verified: false` descriptor** unless
  `allowUnverifiedFormats(true)` is set on the builder. Reading has required the equivalent since
  Epic 1; building did not, and the consequences are worse on this side — a wrong offset when
  reading gives you bad data in your own system, a wrong offset when building sends a bad
  instruction to a bank. Every descriptor shipped today is `verified: false`, so **this affects
  every caller that builds a file**: add the one line.
- The gate is on the builder rather than on `WriterOptions`, and **writing a file you just read
  needs no opt-in** — those bytes already existed and are reproduced exactly, so the round trip
  carries no risk the read did not. [ADR-0019](docs/adr/0019-building-gates-on-verified.md), closing
  [OQ-10](docs/OPEN_QUESTIONS.md).
- **`UnverifiedFormatException` now names the opt-in that applies**, reading or building, and
  exposes which operation was refused. A diagnostic that prescribes the wrong remedy costs more
  than one that says nothing.

### Added — Epic 3, the 120-byte formats and the character sets

- **Three more formats**, taking the bundled set to all four 120-byte layouts:
  - **給与振込 (`11`)** and **賞与振込 (`12`)**. 給与振込 is *not* 総合振込 with three fields
    renamed: its data record has **fourteen** fields, not sixteen, with 預金者名 where 総合振込 has
    受取人名, 社員番号 and 所属コード where it has 顧客コード1/2, and nine bytes of filler where it
    has 振込指定区分, 識別表示 and a seven-byte ダミー. The tails total the same either way, which is
    exactly why deriving one from the other looks safe. Confirmed by three independent sources.
  - **預金口座振替 (`91`)**, whose direction is inverted: the header names the account that
    *receives* collected funds, and each data record names an account to be *debited*. The field
    names say so — `collectionBankCode` and `debitDate` in the header, `payerBankCode` and
    `debitAmount` in the data records — because reusing 総合振込's names would produce payments the
    wrong way round with nothing visible to catch it.
- **賞与振込 borrows its layout** through a new `same-layout-as` descriptor key rather than
  repeating it. That is the one case where deriving is the *documented* reading: the standard says
  賞与振込 uses the 給与振込 format with 種別コード 12. Only the 種別コード constant is rewritten;
  everything else is copied, and offsets are recomputed rather than carried, so R-F2 holds for a
  borrowed layout exactly as for a declared one.
- **Per-field character sets (R-C16, R-C17).** `CharacterSet.validate` returns the byte offset of
  every violation, not a verdict. The permitted set depends on what the field *is*: a branch name
  admits one symbol, a party name four, an EDI payload eight, and a 給与振込 name **no Latin letters
  at all** — a rule a 総合振込-shaped validator would never catch. Declared per field in the
  descriptors and visible in the generated format documentation.
- **The long vowel mark is not a permitted character.** `ｰ` (0xB0) is excluded from every class; a
  long vowel is written `-` (0x2D). Three sources agree, one warning about the confusion explicitly.
  This is the mistake that survives review — the glyphs are near-identical, the file looks correct,
  and the bank rejects it. The violation names the fix.
- **Strict mode (R-C13).** `ReaderOptions.characterPolicy` — `IGNORE` (default), `WARN`, or
  `REJECT`. Orthogonal to `ParseMode`, which governs structure: a record can be structurally perfect
  and still carry a character the bank will refuse.
- **[`docs/encoding.md`](docs/encoding.md)** (R-C12): the byte ranges, the dakuten decomposition,
  the permitted sets per field class, and the Shift_JIS/CP932 divergence. The useful finding is a
  negative one — **every divergence is in the double-byte range, and no double-byte character is
  permitted in any field, so a conformant file decodes identically under either.** The encoding
  setting matters only for files that are already invalid. `EncodingMatrixTest` pins the divergence
  table byte pair by byte pair so the document cannot drift.
- **`accountType` carries the master list of nine codes** from 付録3, not the four 総合振込 happens
  to use, and each field declares the subset it admits via a new `codes` descriptor attribute —
  the model the standard itself uses (OQ-9). 預金口座振替 admits 納税準備預金; 給与振込 admits only
  普通預金 and 当座預金.
- **振替結果コード** as a first-class code list with English glosses — the functional analogue of an
  ISO 20022 R-transaction reason code. Note code `4`: the standard says *no mandate on file*, which
  is not the same as a closed account.
- **JMH benchmarks in [`benchmarks/`](benchmarks/README.md)** (R-P1, R-P4), with hardware, JDK and
  JVM flags recorded alongside every figure. Measured on an M5 Max under JDK 25: **3,227 MB/s**
  streaming with a decode per record, **418 MB/s** fully materialised, against R-P1's 50 MB/s.
  Input is in-memory, so these are the parser's cost with no I/O — stated because a number without
  its conditions is not a measurement (P9).
- **A 1 GB constant-memory check** (R-P2), running in CI on every push: 8,802,795 records streamed
  under a 64 MB heap, 9 MB in use at the end. The constrained heap is the assertion, not a number
  to interpret.
- **R-P3 is now asserted rather than claimed.** `FieldAllocationTest` measures thread allocation
  while reading the same file with one field decoded per record and with eight, and requires the
  difference to be exactly zero bytes per additional field. The claim appeared in five places in
  this codebase and was checked in none of them; it holds, and now it is checked. The field lookup
  was also rewritten to avoid an `Optional` and a capturing lambda — HotSpot's escape analysis was
  already removing both, so this changes no measurement, but R-P3 no longer depends on a
  best-effort optimisation.

### Changed — Epic 3

- **`HeaderRecord.valueDate()` is now `effectiveDate()`** (breaking), closing OQ-6. 預金口座振替's
  header date is 引落日 — the day payers are debited, when nothing reaches anybody — so calling it a
  value date is wrong in the direction that matters. Each generated record additionally carries the
  name its own format uses: `valueDate()` on a 総合振込 header, `debitDate()` on a 預金口座振替 one
  ([ADR-0021](docs/adr/0021-the-shared-header-date-is-effective-date.md)).
- **種別コード `91` resolves to one descriptor, not two**, closing OQ-1. The instruction and result
  files have the *same layout* and differ only in values — 振替結果コード is zero on request and set
  by the bank on return. Two descriptors would make every `91` file ambiguous while distinguishing
  nothing. ADR-0007's guard is unchanged and still applies to runtime-registered descriptors and to
  Epic 8's 振込入金通知 variants, which differ in positions
  ([ADR-0020](docs/adr/0020-one-descriptor-for-type-code-91.md)).
- **Fixtures corrected.** The character validation immediately found that this project's own
  `ﾃｽﾄｼｮｳｼﾞ` contains the small kana `ｮ`, which the standard excludes; it is now `ﾃｽﾄｼﾖｳｼﾞ`. The
  validator's first catch was our own test data.

### Verified — source research, 2026-08-16

- **給与振込 is now corroborated by seven independent sources**, which agree on every offset and
  every length. Two corrections fell out of reading them: field 9 is **受取人名**, not 預金者名 as
  the draft had it (three of four institution guides say so, and 三菱UFJ labels it 預金者名 while
  describing its content as 受取人名); and 社員番号 / 所属コード are now declared `C`, consistent
  with the same bytes in 総合振込.
- **[D-002 is settled against the primary source, and was never a disagreement.](docs/DISCREPANCIES.md)**
  Institution guides give 顧客コード1/2 as `N`, as `C`, and as "N or C" — the same split appears in
  給与振込's 社員番号/所属コード and 預金口座振替's 顧客番号. The JBA standard describes the field
  **twice, deliberately**: 顧客コード1 and 顧客コード2 are `N(10)` each, *and* the same twenty bytes
  are 「※EDI 情報 `C(20)`」 when 識別表示 carries `Y`. Sources giving `N` describe the ordinary case,
  sources giving `C` describe the overlay, and nobody was wrong.
- **No format is flipped to `verified: true`, and the reason has changed.** It is no longer missing
  or conflicting evidence — the offsets were never in doubt and the attribute question is now
  answered. It is that these descriptors deliberately declare `C` where the standard says `N`,
  because the schema states one attribute per field and cannot express "N unless 識別表示 is Y".
  Setting a flag that means "confirmed against published sources" on a layout that knowingly differs
  from them would be an overclaim. Closing it needs conditional fields — [OQ-8](docs/OPEN_QUESTIONS.md),
  already scheduled for Epic 7 because the ISO 20022 mapping has to read that payload anyway.

### Added — release engineering (M3)

- **Publishing configuration** for `zengin4j-core` and `zengin4j-testkit` only — the two modules with
  content. Group `io.github.drag0sd0g` (R-B3), signed artefacts with sources and javadoc jars
  (R-B4), reproducible archives (R-B5), a CycloneDX SBOM per artefact (R-B6), Dependabot (R-B8) and
  an OpenSSF Scorecard workflow (R-B9). Procedure in [RELEASING.md](RELEASING.md).
- **Releasing is a manually-approved GitHub Action and nothing else.** The remote repository is
  registered only under `-PcentralPublish`, which nothing outside the release workflow passes — so
  no sequence of Gradle tasks on a developer machine can reach Maven Central. The workflow adds a
  protected environment with required reviewers, an owner check and a typed version confirmation.
  A published coordinate is permanent; making an accidental release merely unlikely is not enough.
- **`check` now fails if `zengin4j-core`'s published POM declares any dependency.** The ArchUnit rule
  checks the code; this checks the metadata a consumer actually resolves, where a dependency added
  to the wrong configuration would appear without breaking a single import. The SBOM agrees:
  **zero components for core**, exactly one for testkit.

### Known limitations

- **Every bundled format descriptor is `verified: false`**, though not for want of evidence: the
  総合振込 offsets are corroborated by six independent sources including the JBA standard, and a
  single unresolved field-attribute disagreement ([D-002](docs/DISCREPANCIES.md)) holds the flag.
  Reading still requires `allowUnverifiedFormats(true)`, and output must be validated against your
  institution's specification. See [DISCLAIMER.md](DISCLAIMER.md).
- **The 200-byte formats (振込入金通知, 入出金取引明細) are not implemented.** They carry 和暦 dates
  and vary more between institutions than the 120-byte ones; Epic 8.
- No validation layer, no CLI, no transliteration engine, no ISO 20022 mapping. Epics 4 to 7.
- Over-length records are supported only through an explicit record-length override (OQ-3).
- **A file that mixed separator conventions within itself cannot be written back byte-exactly.**
  There is no convention to reproduce, so INV-1 does not apply; the writer says so rather than
  guessing, and `WriterOptions.separator` resolves it deliberately.
- **The committed fuzzing corpus is one input.** Fuzzing has found exactly one thing so far, and it
  is committed and replayed on every build; the working corpus is local and untracked. The corpus
  grows as nightly runs find more.
- **The testkit ships fixtures for 総合振込 only.** `SougouFurikomiFixtures` and `ZenginGenerator`
  do not yet cover the three formats added here, so UC-6 fixture generation reaches one format of
  four.
- **R-C18's write-side character policies** (`REJECT` / `TRANSLITERATE` / `REPLACE`) are not
  implemented. `TRANSLITERATE` needs the transliteration engine, so the requirement lands with it in
  Epic 6.
- **Character-set validation may over-report for your institution.** Where sources disagree on how
  many symbols a name admits, the narrow reading is implemented — see
  [D-003](docs/DISCREPANCIES.md). A finding may be a false positive; it is never a false negative.
- **Character validation is off by default.** `CharacterPolicy.IGNORE` keeps reading a diagnostic
  activity (R-E1). Judging content thoroughly is the validation layer's job, in Epic 4.

### Quality gates

`./gradlew build` enforces, on every run: the Java 21 baseline, the tests, ≥ 90% line and ≥ 85%
branch coverage on `core`, the ArchUnit module rules, descriptor consistency, and that the
committed generated sources match the descriptors, and the committed fuzzing corpora replay. Current
figures: 268 tests, 95.7% line and 89.3% branch coverage.

Mutating fuzz runs are not part of `check` — they are nightly, via `fuzzAll`. Replaying what
fuzzing has already found is, because it is deterministic and costs about two seconds.

Mutation testing (R-T15) is an opt-in task — `./gradlew :zengin4j-core:pitest` — because it takes
about forty seconds rather than the second `check` takes. Current score: 89%, against a threshold of
80%.

Most of what still survives is in `StreamingZenginReader`'s buffer management — compaction and
growth arithmetic reachable only when the buffer boundary lands in a particular place. Some of it is
now covered; the rest is known and not yet earned.

[Unreleased]: https://example.invalid/zengin4j/compare/main...HEAD
