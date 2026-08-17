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
- **A per-record allocation in the character check, found by the new R-P3 test.**
  `RecordCharacters.isClean` and `validate` iterated the field list with an enhanced-for, which
  allocates an iterator per call — once per record under `CharacterPolicy.WARN` or `REJECT`. It
  passed on a fast developer machine, where escape analysis removes it, and failed on every CI
  runner. Both now use indexed loops, and the tests pass under `-Xint` with the JIT disabled
  entirely, which is what distinguishes "allocation-free" from "optimised away".
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

### Added — Epic 6, transliteration and dakuten-safe truncation

- **`KanaTransliterator`** (§16): full-width to half-width with voiced decomposition — ガ becomes
  ｶ+ﾞ, パ becomes ﾊ+ﾟ, ヴ becomes ｳ+ﾞ — plus full-width Latin and digits narrowed and upper-cased,
  and the inverse for display (R-K1, R-K2, R-K8).
- **Dakuten-safe truncation** (R-K3, R-K4, §16.3). A voiced character is two bytes with one glyph,
  so a cut at a byte boundary turns ガクブチ into カクブチ with nothing in the file to show for it.
  A cut that would land on a voicing mark takes the base kana with it. Three policies:
  `REJECT_IF_TOO_LONG` (the default — a payee's name is not a codec's to shorten), `TRUNCATE_SAFE`
  and `TRUNCATE_WITH_MARKER`.
- **Kanji is refused, never guessed** (R-K6, P4). 東 is ヒガシ, トウ or アズマ depending on whose
  name it is, and a wrong reading misroutes the payment. Hiragana is refused by default too —
  convertible, but a name arriving in hiragana usually means the wrong field was sent (R-K5).
- **The loss vocabulary** (`LossKind`, `LossSeverity`, `LossEntry`, `LossReport`, `LossCollector`),
  so that nothing is lost silently (P5, R-I14–R-I16). Narrowing and case folding are
  `INFORMATIONAL`; anything that changes how a name reads is `MATERIAL`. Epic 7 builds its mapping
  report on this rather than a second vocabulary.
- **R-C18's write-side policies**, deferred from Epic 3 because `TRANSLITERATE` needed an engine
  that did not exist. `RecordEncoder` now takes `EncodingOptions`: `REJECT` (default),
  `TRANSLITERATE` and `REPLACE`, applied per field against that field's own character class.
- **`VoicingMarks` in `core`**, holding R-K7's ranges once. Validation rule `V-206` had the only
  copy; the transliterator needs the same fact, and two copies of it would eventually disagree —
  at which point the library would write files it rejects.
- **Tables derived from Unicode, judgement calls declared as data**
  ([ADR-0030](docs/adr/0030-kana-tables-are-derived-not-transcribed.md)). Transcribing 186
  width-correspondence pairs by hand is the error-prone work R-F2 forbids for byte offsets, and a
  slip would be invisible — ｼ for ｿ reads as a plausible name. The 186 are derived from NFKC and
  checked for cardinality and single-byte width at build time; the ~20 substitutions a person had
  to decide live in `kana-substitutions.yaml` with their reasons.
- **`docs/encoding.md` extended to R-DOC4's full scope**, and
  **`examples/TransliterateNames.java`** (§16).

### Fixed — Epic 6

- **`REJECT` did not reject.** The default write policy checked only a value's *length*, so a
  full-width name that happened to fit the byte budget was written into a field permitting only
  half-width — producing exactly the file `V-202` reports. It has been possible to build an invalid
  file this way since Epic 2; R-C18 is what closes it.
- **The encoder would write a voicing mark no kana can carry.** `ｱ` is permitted and `ﾞ` is
  permitted, so `ｱﾞ` passed a character-by-character check while being a sequence the standard does
  not recognise — the one `V-206` exists to report. Now refused, using the shared `VoicingMarks`.
- **Two of the specification's kana mappings are wrong**
  ([ADR-0028](docs/adr/0028-the-specifications-kana-mappings-are-wrong.md)). R-K2 says ー becomes ｰ
  and ャ becomes ｬ; `CharacterClass` permits neither in any field, so following it would emit text
  this library rejects. Implemented as ー→`-` and ャ→ヤ, both `MATERIAL`.
- **`TRUNCATE_WITH_MARKER` wrote a marker no field permits.** The default was `*`, which
  `CharacterClass` admits in *no* name class — so a policy whose whole purpose is to make a change
  visible produced a field `V-202` then rejected. The default is now `-`, and a marker the target
  field would refuse is refused. `PAYROLL_NAME` admits no symbol at all, so marked truncation is
  impossible there and says so.
- **`REPLACE` could write a byte no field permits, or a stranded voicing mark.** `'?'` is the
  obvious replacement and is permitted by no name class; `0xDE` is a voicing mark and would strand
  itself after whatever kana it landed behind. Both are now refused before anything is written.
- **`TRANSLITERATE` measured length in the wrong encoding.** It built its options without the
  encoder's charset, so it measured MS932 while the caller wrote UTF-8 — calling a 45-byte value a
  15-byte one and letting it overflow the field.
- **`V-206` kept its own copy of R-K7's byte ranges** after `VoicingMarks` was introduced to hold
  them once. ADR-0029 said the duplication had been removed; it had not, until now.
- **Truncation separated a kana from its mark when the file encoding was UTF-8.** The string-level
  methods handed UTF-8 bytes to `truncateSafe`, which reads JIS X 0201 — so it looked for `0xDE`
  where that mark's first byte is `0xEF`, kept the base and dropped the mark. `ﾌﾞ` became `ﾌ`: the
  silent rename the whole engine exists to prevent, reintroduced by measuring in the wrong units.
  Length is now counted in the encoding the file will actually be written in.
- **§16.3's reference implementation checks for a leading orphaned mark only when truncating**, so a
  short input beginning with a stray mark passed and a long one did not. The input is equally
  damaged either way.
- **ヷ and ヺ decompose to a stranded mark.** Unicode splits the archaic VA and VO into ﾜ+ﾞ and ｦ+ﾞ,
  and neither kana has a voiced form the standard recognises. The derived table faithfully contains
  mappings that must never be written; the engine refuses them, and a test names all four cases so
  a fifth would fail the build rather than reach a file.

### Added — Epic 5, the command line tool

- **`zengin`**, a command with five subcommands: `validate`, `inspect`, `generate`, `diff` and
  `explain`. Ships as a self-contained jar from `./gradlew :zengin4j-cli:shadedJar`.
- **`inspect --annotate`** (R-CLI5), which §27 calls the primary diagnostic tool and asks for more
  care than the feature list implies. Per field: byte offset, length, type, hex, decoded value, the
  name in both languages, and whether the value is one the field may hold. Columns are measured in
  *display* width — 種別コード is five characters and ten terminal columns, and a table padded by
  character count is visibly crooked in a tool whose whole job is showing where bytes sit.
- **Exit codes as a contract** (R-CLI1): `0` clean, `1` warnings only, `2` errors, `3` usage,
  `4` I/O. The `1` for warnings alone is deliberate and unusual; the reasoning is in
  [ADR-0025](docs/adr/0025-warnings-exit-non-zero.md). `diff` uses the same `1` for "the files
  differ", matching `diff(1)`.
- **`--out-format=json` on every command** (R-CLI2), written by hand for the reason ADR-0022 gives
  and one more: a reflective serialiser needs GraalVM configuration that goes stale the first time a
  field is renamed, and a writer that reflects over nothing needs none.
- **`diff` aligns records rather than comparing positions**
  ([ADR-0027](docs/adr/0027-diff-aligns-records-rather-than-positions.md)). Inserting one payment
  near the top of a 50-payment file would otherwise report 49 records as changed — which is exactly
  the edit somebody most wants to see clearly.
- **`generate` covers all four bundled formats** (R-CLI3), not just 総合振込, closing the known
  limitation recorded in Epic 4. The same seed produces the same bytes on every platform and JDK.
- **`explain`** prints any format's byte layout, or one field of it, from the descriptors the reader
  actually uses — including code lists narrowed by a format, and the sources behind the layout.
- **Nothing prints an account number by default** (R-CLI4), and nor is its hex printed, since hex of
  an account number is an account number to anyone reading a byte dump. What exactly `--unsafe-print`
  gates, and why names and amounts are not in the same category, is
  [ADR-0026](docs/adr/0026-what-unsafe-print-actually-gates.md).
- **picocli**, as a runtime dependency of `zengin4j-cli` and nothing else
  ([ADR-0024](docs/adr/0024-picocli-for-the-cli.md)). This module is an application, is not
  published, and has no consumer to inherit anything; `core`'s zero-dependency guarantee is
  unaffected and still separately enforced.
- **GraalVM native-image configuration**, generated from the `@Command` annotations at compile time
  by `picocli-codegen` so it cannot go stale when an option is renamed. The `nativeImage` task is
  optional per §5.6 and deliberately not wired into `check`.
- **`JapaneseBankCalendar.fromCsv(Path)`**, so `--calendar=FILE` can supply holiday data the caller
  controls. The bundled data expires and a released jar cannot be re-cut every February when the
  Cabinet Office publishes the next year's equinoxes.
- **`FormatFixtures`** in the testkit, with implementations for all four formats. 給与振込 is not
  総合振込 with three fields renamed, and 預金口座振替 moves money the other way, so each
  implementation maps to the field ids its own descriptor declares rather than sharing a layout.
- **[`docs/cli.md`](docs/cli.md)**, checked against the parser by `CliReferenceTest`: a documented
  option that no command accepts, or an option the page omits, fails the build.
- **`examples/GenerateTestFixtures.java`** (UC-6), and a CI job that runs the shaded jar end to end —
  the unit tests drive the command objects in-process, which cannot catch a missing `Main-Class` or a
  resource lost to the shading rules.

### Fixed — Epic 5

- **`zengin diff` could crash and report success.** Two files that differ
  throughout built an `O(n·m)` alignment table — 64 million cells for 8,000
  records — and died with an `OutOfMemoryError` under an ordinary heap. Worse
  than the crash was the exit status: an `Error` is not an `Exception`, so it
  escaped picocli's handler uncaught and **the JVM exited 1, which in this tool
  means "the files differ"**. A script comparing a generated file against a
  committed one would have read a crash as a completed comparison. Records
  matching at both ends are now discounted first, which makes the realistic edit
  cost nothing; a table above 16 million cells after that is refused with a
  sentence; and `Zengin.run` catches `Throwable` so no future `Error` can
  reproduce the collision.
- **The annotate table lacked the English field name**, which R-CLI5 asks for by
  name. The field id is not a substitute: it diverges from `nameEn` for eight of
  the fifty-two bundled fields — `dataKubun` is "Record Type", `dummy` is
  "Filler", `amount` is "Transfer Amount". The sequence column was dropped to pay
  for it, since in a byte-oriented tool the offset is the better key.
- **A control byte in a field tore the table in half.** A record whose fields
  have slipped out of alignment carries the file's own separators inside a field,
  and a raw `0x0D` broke the row across two lines — in the one case `inspect`
  exists for. Control characters now print as `␍`, `␊` and friends. The raw value
  is unchanged, so JSON still escapes it properly.
- **Library remedies naming Java APIs reached the terminal.** "Set
  `ReaderOptions.builder().allowUnverifiedFormats(true)`" is the right advice for
  a caller writing code and a puzzle at a shell prompt. Three separate failures
  printed one, including through `validate`, which does not throw at all — it
  wraps the failure in a `V-100` finding that goes to stdout and never reaches
  the exception handler. `CliMessages` translates them, and
  `NoJavaRemediesReachTheTerminalTest` provokes every failure it can and fails
  the build if any output still names a Java API — which is what contains the
  fragility of translating by string replacement.
- **`explain --field=X` without `--format` silently listed the formats instead**,
  answering a question nobody asked and leaving the reader thinking their field
  did not exist.
- **An unwritable output path printed only the path.** `NoSuchFileException`
  carries the bare filename as its message, so the failure read like a success
  line.
- **A test and a CI assertion that would have started failing on 1 October
  2026.** Both pinned a holiday to `2026-09-30` and relied on the fixture's
  yearless `0930` value date resolving to 2026 — which it does only until
  October, after which the reader looks forward to 2027. The library's own date
  tests pin their reference date explicitly and were never exposed; the CLI has
  no way to pin one, which is what let this through. A test that expires is
  worse than no test, because it fails long after the change that would explain
  it.
- **The CLI wrote ANSI colour codes into its usage text on Windows.** picocli's
  `Ansi.AUTO` decided the Windows CI runners were a colour terminal, so
  `Usage: zengin validate` arrived as `Usage: \e[1mzengin validate\e[21m\e[0m` —
  invisible to a person, very visible to anything else reading the output, and
  green on Linux and macOS across two JDKs. Colour is now off unconditionally:
  detecting a terminal is not portable across this project's own matrix, because
  `System.console() != null` means "not redirected" on JDK 21 while JDK 22
  changed it to return a Console even when redirected, and `--release 21`
  cannot call the `isTerminal()` that replaced it. `NoAnsiEscapesTest` forces
  ANSI on before every assertion, so it reproduces the Windows condition
  everywhere rather than only where the problem is.
- **The CLI smoke job in CI never ran its assertions.** GitHub invokes the step
  with `bash -e`, which `set -uo pipefail` does not undo, so the first
  assertion that deliberately expects a non-zero exit killed the script — it
  produced no output and exited 3. A second bug sat behind it: `pipefail` on the
  `--calendar` check reported grep as having failed whenever `validate`
  legitimately exited 1 for a warning.
- **The round-trip properties held for one format of four, and nothing said so.**
  `RandomZenginFiles` took a `FormatDescriptor` parameter while hard-coding
  総合振込's field ids, so passing any other descriptor failed outright — a false
  generality that left INV-1, INV-2 and INV-6 unproven for 給与振込, 賞与振込 and
  預金口座振替 since Epic 3. R-T7 calls those the load-bearing correctness
  guarantee. The generator now derives values from the descriptor, which also
  makes the properties true of a consumer's own format, and
  `AllFormatsRoundTripProperties` runs them across all four.
- **The golden corpus had silently degraded to no payments.** It is drawn from a
  seeded generator, so changing value generation shifted the draw and left a
  header, a trailer and an end record with nothing between them — while every
  test still passed. A golden file whose diff cannot show a decoding change in a
  payment is doing half its job. The seed now yields a representative corpus and
  `theCorpusIsRepresentative` holds it to that rather than trusting it.
- **The generator emitted a name no format permits.** `ﾀﾞﾐｰ ｻﾌﾞﾛｳ` contains ｰ, the 長音, which the
  standard never allows — and `PAYROLL_NAME` admits no symbols at all, so there is no spelling of it
  that works. Running `zengin validate` over a generated 給与振込 file reported six `V-202` errors
  against this project's own testkit. Every line of the generator was covered while one of its eight
  values was wrong, so the new tests check the *data*: every name against every name field of every
  format, and generation at 200 records rather than 5, since five draws from eight names usually miss
  the bad one.
- **`diff` reported four edited records as four additions and four removals.** The alignment
  backtrack emits removals and additions in runs, not alternating pairs, and the first merge only
  looked at immediate neighbours.
- **The unverified-format error told a shell user to call a Java API.** Correct advice for a caller
  writing code, useless at a prompt; the CLI now restates the remedy as `--allow-unverified`
  (R-E3, R-CLI6).
- **The examples documented an invocation that no longer works.** All three still showed
  `java -cp "*/build/libs/*"`, which `runExamples` replaced in Epic 4 precisely because the glob
  picks up the sources jar.

### Added — Epic 4, validation

- **`zengin4j-validation`**, 27 rules across the six tiers of §14.3, emitting 32 distinct finding
  ids. Every finding carries its
  severity, rule id, record number, byte offset, field id, both languages, the offending value and
  what would have been acceptable (R-V2) — so a report points at the byte rather than at the file.
- **Validation returns a report and never throws** (R-V1). Not for a malformed file, not for bytes
  that are not a Zengin file at all, and not for a rule with a bug in it: the engine converts an
  escaping exception into a `V-000` finding and runs the other rules. Bad input is the only reason
  anyone runs a validator.
- **Every rule is suppressible and re-rankable by id** (R-V3), because institutional practice
  varies enough that some rule here is wrong for somebody. Ids are stable across versions —
  renumbering one would silently re-enable a check that had been turned off deliberately.
- **Deterministic reports** (INV-7). Findings sort by position then by rule id, so the same file
  always produces the same report; asserted by shuffling the rule order twenty ways and comparing
  the rendered output.
- **JSON and SARIF** (R-V4), written by hand because R-M2 permits no JSON dependency — and checked
  by parsing the output back with a real parser, since a writer that produced *almost* valid JSON
  would pass any `contains` assertion and fail in the consumer
  ([ADR-0022](docs/adr/0022-hand-written-json-and-sarif.md)).
- **A Japanese bank calendar** (R-V6, R-V7) built from the Cabinet Office's published holiday data
  rather than from a formula. The equinoxes are fixed by an astronomical determination published in
  February of the preceding year and cannot be computed; substitute and bridge holidays come with
  the data. The year-end closure is modelled separately because 2 January 2026 is a **Friday** and
  banks are shut — a holidays-only calendar passes that date. Past its horizon it refuses rather
  than guesses ([ADR-0023](docs/adr/0023-holidays-are-data-not-an-algorithm.md)).
- **Reference data is optional and pluggable** (R-V5), and no snapshot ships. Institution data goes
  stale — banks merge, branches close — and a copy compiled into a released jar would look
  authoritative while being wrong. Without a provider the `V-4xx` rules do not run and nothing else
  changes.
- **Messages in both languages from properties files** (R-E4), loaded as `PropertyResourceBundle`
  directly rather than through locale resolution: `getBundle(name, ENGLISH)` falls back to the
  *default* locale before the base bundle, so on a Japanese JVM every finding would have carried the
  same text twice — invisible to anyone developing in another locale.
- **Account numbers are masked in findings** (R-E6) unless the caller opts out, because findings
  reach logs, tickets and CI annotations.
- **`examples/ValidateBeforeSubmitting.java`** (UC-1), which found two message defects on its first
  run: a Japanese message interpolating an English weekday name, and another wrapping an English
  description inside Japanese text.
- **[`docs/validation-rules.md`](docs/validation-rules.md)**, every id with its default severity and
  what it checks, plus a section on what these rules do *not* check. `RuleReferenceTest` fails the
  build when the page and the code disagree, including the rule count quoted in the README.

### Fixed — Epic 4

- **Composite rules are now suppressible one id at a time.** `V-301` also emits `V-303` and `V-304`,
  and `V-501` also emits `V-502`, `V-503` and `V-505`, because each set is several verdicts on one
  computation. Suppression matched on the rule's own id alone, so `suppress("V-303")` silently did
  nothing — precisely the R-V3 promise the ids exist to keep. `Rule.emits()` now declares the full
  set, and the engine filters on it.
- **`V-403` is implemented rather than merely numbered.** The id had a message and a description in
  both bundles and no rule behind it, so a consumer suppressing it was suppressing nothing.
- **`V-505` reports the severity it actually emits.** Its rule declared `ERROR` while the finding was
  built as `INFO`, which SARIF then published as an error-level rule that never produces an error.
  `Rule.severityOf(String)` lets a composite rule answer per id.
- **SARIF declares every id it can reference.** The driver listed one rule per `Rule`, so results
  carrying `V-303`, `V-304`, `V-502`, `V-503` or `V-505` referenced undeclared ids — a document a
  strict consumer is entitled to reject.
- **`toJson()` and `toSarif()` are on `ValidationReport`**, as §14.1 describes them, rather than only
  on `ReportWriters`.
- **`V-000` and `V-100` take their text from the message bundles** like every other id. Both were
  built from string literals in Java, so the one place message text is supposed to live did not have
  it and neither could be re-worded without a recompile.
- **`io.zengin4j.validation` is exported from the module descriptor.** Five packages were exported
  and the sixth — the one holding `ZenginValidator` — was not, so on the module path the entry point
  was unreachable while everything supporting it was visible. Nothing in an ordinary build notices:
  on the class path it works, and the compiler has no opinion. `ModuleDescriptorTest` now fails the
  build when a package with public types is not exported, across all three published modules.
- **`FieldDescriptor.endOffset()` has its Javadoc back.** It had been separated from its comment by
  an inserted method, leaving the comment dangling and the method undocumented — a `javac` warning
  that was being scrolled past.
- **`runExamples` prints what the examples print.** Output went to `logger.info`, invisible without
  `--info`, which defeats the point: reading an example's output is how two Japanese message defects
  were found, and both had exit code zero.
- **Syntax rules now examine records the reader rejected.** They skipped malformed records on the
  grounds that field boundaries are unreliable — but a record is usually malformed *because* a field
  contains something it should not, and boundaries are reliable whenever the length and
  discriminator are right. Tier 2 was skipping exactly the records it exists to explain.
- **`runExamples` replaces the documented `java -cp "*/build/libs/*"` invocation.** That glob also
  matches the sources jar, which carries a copy of every resource, sorts before the main jar and
  therefore shadows it — a stale one served a freshly built example an old message bundle.
- **`''` no longer leaks into rule descriptions.** A `.message` goes through `MessageFormat` and
  needs its apostrophes doubled; a `.description` is returned verbatim and must not. Getting it
  backwards is invisible until somebody reads a SARIF document.

### Known limitations

- **Every bundled format descriptor is `verified: false`**, though not for want of evidence: the
  総合振込 offsets are corroborated by six independent sources including the JBA standard, and a
  single unresolved field-attribute disagreement ([D-002](docs/DISCREPANCIES.md)) holds the flag.
  Reading still requires `allowUnverifiedFormats(true)`, and output must be validated against your
  institution's specification. See [DISCLAIMER.md](DISCLAIMER.md).
- **The 200-byte formats (振込入金通知, 入出金取引明細) are not implemented.** They carry 和暦 dates
  and vary more between institutions than the 120-byte ones; Epic 8.
- No ISO 20022 mapping. Epic 7.
- **`V-4xx` needs reference data you supply**, and `V-5xx` needs the calendar switched on. Both are
  optional by design (R-V5, R-V6); neither runs by default.
- **`zengin validate` is not reproducible across dates for a yearless value
  date.** The `MMDD` fields carry no year, and the reader resolves them forward
  from *today*, so the same file can validate clean in August and trip a
  calendar rule in October. The library exposes `MonthDayResolver` for exactly
  this, and `ZenginValidator.withDateResolver(...)` accepts one; the CLI does
  not surface it, because §27 lists no such option. An `--as-of=YYYY-MM-DD` flag
  would close it — recorded in [OPEN_QUESTIONS](docs/OPEN_QUESTIONS.md) rather
  than added unasked.
- **Tier 4 checks existence, not activity at the value date.** §14.3 asks for "both active at the
  value date where temporal data is available"; `ReferenceDataProvider` has no temporal dimension, so
  that data is never available and a branch which closed last month still passes. Supplying it means
  dated validity on the interface and a dataset carrying it — `zengin-code` does not. Recorded in
  [docs/validation-rules.md](docs/validation-rules.md) rather than left as an unstated gap.
- **The bundled calendar expires at the end of 2027.** Later dates produce a `V-505` finding rather
  than an answer. Refreshing it is one CSV and a re-run of the conversion.
- **Validation branch coverage is gated at 80%, below core's 85%.** Half the remaining branches are
  the "no calendar", "no reference data", "this format has no such field" guards that R-V5 and R-V6
  require; reaching them needs contrived descriptors that prove the guard compiles rather than that
  any rule works. The floor ratchets up, never down.
- Over-length records are supported only through an explicit record-length override (OQ-3).
- **A file that mixed separator conventions within itself cannot be written back byte-exactly.**
  There is no convention to reproduce, so INV-1 does not apply; the writer says so rather than
  guessing, and `WriterOptions.separator` resolves it deliberately.
- **The committed fuzzing corpus is one input.** Fuzzing has found exactly one thing so far, and it
  is committed and replayed on every build; the working corpus is local and untracked. The corpus
  grows as nightly runs find more.
- ~~**The testkit ships fixtures for 総合振込 only.**~~ Closed in Epic 5: `FormatFixtures` covers all
  four, from Java or from `zengin generate --format=…`.
- ~~**R-C18's write-side character policies** are not implemented.~~ Closed in Epic 6:
  `EncodingOptions` carries all three, and `REJECT` — the default — now actually rejects.
- **Character-set validation may over-report for your institution.** Where sources disagree on how
  many symbols a name admits, the narrow reading is implemented — see
  [D-003](docs/DISCREPANCIES.md). A finding may be a false positive; it is never a false negative.
- **Character validation is off by default.** `CharacterPolicy.IGNORE` keeps reading a diagnostic
  activity (R-E1). Judging content thoroughly is the validation layer's job, in Epic 4.

### Quality gates

`./gradlew build` enforces, on every run: the Java 21 baseline, the tests, the coverage floors
(≥ 90% line and ≥ 85% branch on `core`; 90/80 on `validation`; 95/90 on `testkit`; 85/75 on `cli`),
the ArchUnit module rules, descriptor consistency, that the committed generated sources match the
descriptors, that `docs/cli.md` and `docs/validation-rules.md` match the code, and that the
committed fuzzing corpora replay. Current figures: 760 tests — 464 in `core`, 135 in `cli`, 94 in
`validation`, 48 in `testkit`, 19 in `codegen` — alongside 847 corpus replays in the non-mutating
`fuzz` task, and property runs covering INV-1, INV-2, INV-4, INV-6 and INV-8. `core` 95.9% line
and 90.0% branch; `testkit` 96.1% and 100%; `validation` 93.1% and 81.6%; `cli` 92.9% and 79.9%.

Fuzzing stays pointed at `core`. The CLI adds no parser of its own — picocli does the argument
parsing and the record parsing is `core`'s, already fuzzed — so a fuzz target here would exercise
somebody else's code.

Mutating fuzz runs are not part of `check` — they are nightly, via `fuzzAll`. Replaying what
fuzzing has already found is, because it is deterministic and costs about two seconds.

Mutation testing (R-T15) is an opt-in task — `./gradlew :zengin4j-core:pitest` — because it takes
about forty seconds rather than the second `check` takes. Current score: 88%, against a threshold of
80%.

Most of what still survives is in `StreamingZenginReader`'s buffer management — compaction and
growth arithmetic reachable only when the buffer boundary lands in a particular place. Some of it is
now covered; the rest is known and not yet earned.

[Unreleased]: https://example.invalid/zengin4j/compare/main...HEAD
