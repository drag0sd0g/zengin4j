# zengin4j

**A JVM library for the Japanese Zengin (全銀) fixed-length bank file formats, with bidirectional
ISO 20022 mapping.**

[日本語版 README](README.ja.md) · Apache-2.0 · Java 21

> **Not certified by 全国銀行協会, 全銀ネット or any financial institution.** Every format
> descriptor in this release is `verified: false` — its byte layout has not been confirmed against
> two independent published sources, and the reader refuses to use one unless you opt in explicitly.
> You are responsible for validating output against your own institution's specification before
> production use. See [DISCLAIMER.md](DISCLAIMER.md).

---

## Why this exists

Japan is midway through a long transition from proprietary fixed-length payment files to ISO 20022
XML, and the two will coexist for years. ZEDI (全銀EDIシステム) accepts ISO 20022 from companies —
and then **converts it to fixed-length before handing it to the originating bank**. The mapping
between the two is therefore not a one-off migration exercise but a permanent bridge, currently
implemented privately and inconsistently by every participant.

No open-source project in any language performs that mapping, and none of the existing fixed-length
parsers runs on the JVM. That gap is this project's scope.

### The problem that is hardest to discover on your own

The ZEDI profile concatenates the Business Application Header with the message body **at XML
declaration granularity**. The consequence:

> **A ZEDI file contains multiple XML declarations and is therefore not a single well-formed XML
> document.** Handing one to a standard XML parser fails immediately, and generic ISO 20022
> libraries cannot read these files at all.

Handling that correctly — splitting on declaration boundaries, and reassembling with byte-identical
framing — is one of the concrete reasons this library exists. It arrives with the ISO 20022 layer
in Epic 7; see [Status](#status).

## Status

This repository is at **Epic 6 — transliteration**. What works today:

| | |
|---|---|
| ✅ | All four 120-byte formats: 総合振込 (`21`), 給与振込 (`11`), 賞与振込 (`12`), 預金口座振替 (`91`) |
| ✅ | Reading files: streaming, batch and whole-file APIs |
| ✅ | Writing files back **byte for byte**, including framing the file arrived with |
| ✅ | Building files, with each batch trailer's count and total computed from its payments |
| ✅ | Format descriptors as data, with computed byte offsets and a build-time length check |
| ✅ | Generated, format-shaped record types, committed and drift-checked |
| ✅ | Per-field character sets, reporting the byte offset of every violation |
| ✅ | Shift_JIS / CP932 / UTF-8, with the divergence documented and pinned by tests |
| ✅ | Optional separators (none / CR / LF / CRLF, even mixed), byte order marks, EOF byte |
| ✅ | Strict and lenient parsing, with malformed records surfaced as data rather than exceptions |
| ✅ | Year inference for the yearless `MMDD` dates, as an explicit, named decision |
| ✅ | Validation: 27 rules across six tiers, every finding located to the byte |
| ✅ | JSON and SARIF reports — SARIF renders as CI annotations on the file |
| ✅ | A Japanese bank calendar, holidays included, that refuses to guess past its data |
| ✅ | `zengin` command: `validate`, `inspect`, `generate`, `diff`, `explain` |
| ✅ | Deterministic synthetic fixtures for all four formats, from Java or the CLI |
| ✅ | Half-width katakana transliteration, with everything it changes recorded |
| ✅ | Dakuten-safe truncation: a cut never separates a kana from its voicing mark |
| ⬜ | ISO 20022 `pain.001` in both directions, with loss reporting — Epic 7 |

## Quickstart

```java
// Build the registry once and share it: it is immutable and thread-safe.
FormatRegistry registry = FormatRegistry.defaults();

ReaderOptions options = ReaderOptions.builder()
        .registry(registry)
        .charset(ZenginCharset.MS932)      // the default; what Windows-based systems emit
        .allowUnverifiedFormats(true)      // required in 0.1.0 — read DISCLAIMER.md first
        .build();

ZenginFile file = ZenginReaders.readFile(Path.of("payments.txt"), options);

for (Batch batch : file.batches()) {
    System.out.println(batch.header().originatorName() + " → " + batch.computedCount() + " payments");
    for (DataRecord record : batch.data()) {
        SougouFurikomiData payment = (SougouFurikomiData) record;
        System.out.printf("  %s  ¥%,d%n", payment.beneficiaryName(), payment.amount());
    }
}
```

For files too large to hold in memory, iterate instead — memory stays constant regardless of file
size:

```java
try (ZenginReader reader = ZenginReaders.open(path, options)) {
    while (reader.hasNext()) {
        RecordView view = reader.next();
        if (view.kind() == RecordKind.DATA) {
            long amount = view.asLong(view.field("amount"));   // decodes from bytes, allocates nothing
        }
    }
}
```

> **A `RecordView` is a window onto a recycled buffer and is valid only until the next call to
> `next()`.** Accessing a stale one raises `StaleRecordViewException` rather than returning the
> wrong record's data. Call `materialize()` on anything you need to keep — or use
> `ZenginReaders.batches(...)`, which materialises by default.

Building a file computes each batch trailer from the payments it contains, so it cannot disagree
with them by accident:

```java
ZenginFile file = ZenginFileBuilder.forFormat(descriptor)
        .allowUnverifiedFormats(true)   // required in 0.1.0 — read DISCLAIMER.md first
        .header(h -> h.set("originatorCode", "9900000001")
                      .set("originatorName", "ﾃｽﾄｼﾖｳｼﾞ")
                      .set("valueDate", MonthDay.of(9, 30)))
        .payment(p -> p.set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ")
                       .set("accountNumber", "9876543")
                       .set("amount", 150_000L))
        .build();                       // trailer: 1 record, ¥150,000

ZenginWriters.write(file, Path.of("payments.txt"), WriterOptions.defaults());
```

**A file read and written again is byte-identical to what arrived** — its separator convention,
its byte order mark, whether a separator followed the final record, and any filler this library
does not interpret:

```java
ZenginFile parsed = ZenginReaders.readFile(path, options);

assert Arrays.equals(ZenginWriters.toByteArray(parsed, WriterOptions.defaults()), original);
```

That is the point of retaining every record's raw bytes rather than re-encoding from decoded
fields: reserved space and values nobody has verified yet survive the trip untouched.

Validation returns a report rather than throwing, and every finding says where:

```java
ValidationReport report = ZenginValidator.builder()
        .withCalendar(JapaneseBankCalendar.bundled())
        .build()
        .validate(file);

if (!report.isSubmittable()) {
    System.out.print(report.toText());
}
```

```
ERROR V-202 record 4 byte 366 [beneficiaryName]: the long vowel mark ｰ is never
  permitted — write a long vowel as - (0x2D).
ERROR V-301 record 5 byte 488 [totalAmount]: Trailer total is 999,999 but the
  batch's payments add up to 300,000, a difference of 699,999.
WARNING V-306 record 3 byte 244: This payment is identical to the one in record 2.
```

Every rule is suppressible by id, because institutional practice varies. Errors
block submission; warnings do not — a report that blocked on warnings is a report
people learn to override. [`docs/validation-rules.md`](docs/validation-rules.md)
lists every id with its default severity, and says what these rules do *not*
check.

Names usually arrive full-width and have to go into a fixed number of half-width bytes. That
conversion is where money goes missing, so it is explicit about what it costs:

```java
Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ ジロウ", options);

result.text();                  // ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ
result.isMateriallyChanged();   // false — narrowing alone changes no name
```

**A voiced character is two bytes with one glyph.** `ｶﾞ` is `B6 DE`, so a cut at a byte boundary
turns ガクブチ into カクブチ — a different payee, in a file that records nothing about it.
Truncation here moves the cut back past the base kana rather than severing the pair, and the
default policy refuses to truncate at all.

**The target field changes the answer.** A long vowel becomes `-`, and payroll names admit no
symbols, so ヨーコ can be written into a 総合振込 file and not into a 給与振込 one. Transliteration
takes a `CharacterClass` for that reason.

**Kanji is refused, never guessed** — 東 is ヒガシ, トウ or アズマ depending on whose name it is.
There is no reading dictionary here and there will not be one.

Runnable versions live in [`examples/`](examples/), the byte-level reference is
[`docs/encoding.md`](docs/encoding.md), and the byte layout of every bundled format — generated
from the descriptors the library actually uses — is in [`docs/formats/`](docs/formats/).

## From the command line

```sh
./gradlew :zengin4j-cli:shadedJar
alias zengin='java -jar zengin4j-cli/build/libs/zengin4j-cli-*-all.jar'
```

`inspect --annotate` is the one to reach for when a bank rejects a file and the rejection
notice says something unhelpful. It shows every field's offset, bytes, decoded value, name in
both languages, and whether the value is one the field may hold:

```
record 2  DATA  byte 122
  off   len T  field                  項目名          name                     hex                  value
  1     4   N  beneficiaryBankCode    被仕向銀行番号  Beneficiary Bank Code    39 39 39 39          9999        ok
  43    7   N  accountNumber          口座番号        Account Number           (masked)             ***6543     ok
  50    30  C  beneficiaryName        受取人名        Beneficiary Name         D3 B0 DE 20 …        ﾓｰﾞ ｼﾖｳ     <- the long vowel mark ｰ is never permitted — write a long vowel as - (0x2D)
```

The rest: `validate` (text, JSON or SARIF, with an exit status a pipeline can branch on),
`diff` (field by field, aligned so an inserted payment does not report every later record as
changed), `generate` (synthetic files, same seed same bytes) and `explain` (any format's byte
layout, no file needed).

**No command prints an account number unless you pass `--unsafe-print`** — nor its hex, since
hex of an account number is an account number. Full reference, including the exit codes, is in
[`docs/cli.md`](docs/cli.md).

## What this library will and will not claim

There are two boundaries, and both are load-bearing.

**Verified versus unverified.** Every format descriptor, code list and mapping rule carries a
`verified` flag and its citations. It becomes `verified: true` only when at least two independent
published sources are cited in [`docs/SOURCES.md`](docs/SOURCES.md) — a bar the loader enforces,
not a convention.

The 総合振込 layout has been compared against six independent sources, including the JBA's own
protocol document, and they agree on every offset and every length. It is nonetheless still
`verified: false`, because those sources disagree about one field's *attribute*
([D-002](docs/DISCREPANCIES.md)) and the rules keep a format unverified until such a disagreement is
settled. The code lists it references *are* verified. Generated documentation distinguishes the
three states, and the reader still refuses an unverified format without an explicit opt-in.

**Conformant versus experimental.** There is no official ISO 20022 profile for 預金口座振替 (direct
debit). Any mapping of it to `pain.008` is this project's own design, will live in
`io.zengin4j.iso20022.experimental`, and is excluded from every conformance claim.

## Where are the `pacs` messages?

Out of scope, deliberately. `pacs.*` are bank-to-bank messages; in Japan the domestic interbank leg
has not migrated to ISO 20022 and has no published profile to build against. The corporate-to-bank
boundary — `pain` and `camt` — is where the Zengin corporate file formats live, and it is the whole
scope of this library. It never opens a network socket either: files in, files out.

## Design decisions worth knowing before you read the code

- **The domain model is format-shaped, not idealised.** `SougouFurikomiHeader` has exactly the
  fields the header record has, in the order it has them. There is no unified "payment" abstraction
  in `core`; that belongs in the ISO 20022 layer, where the mapping is explicit and comes with a
  loss report. This is what makes round-tripping provable.
- **`zengin4j-core` has zero runtime dependencies.** No YAML library, no JSON library, no logging
  facade — it requires nothing beyond `java.base`, and an ArchUnit rule fails the build if that
  changes.
- **Field layouts are data, compiled at build time.** Descriptors are authored as YAML in
  `zengin4j-core/formats/`, and the build reads them and emits committed Java. Core therefore ships
  no parser and no descriptor resources, and a layout that does not add up fails the build rather
  than a payment run (ADR-0016). Byte offsets are computed from cumulative field lengths and never
  transcribed — by the generator, or by you.
- **Every length is a byte count.** `ﾃｽﾄｷﾞﾝｺｳ` renders as seven characters and occupies eight bytes,
  because the ｷﾞ is a base kana followed by a standalone dakuten. Truncating between them turns
  ギ into キ, and nothing in the file would indicate it.

Architecture decision records live in [`docs/adr/`](docs/adr/).

## Building

```bash
./gradlew build                    # compile, test, coverage gate, architecture rules, drift check
./gradlew :zengin4j-cli:shadedJar  # build the self-contained `zengin` command
./gradlew runExamples              # run every program in examples/ and print what it prints
./gradlew generateFormatSources    # regenerate record classes and docs after editing a descriptor
./gradlew :zengin4j-core:pitest    # mutation testing; opt-in, takes about a minute
./gradlew :zengin4j-core:fuzzAll   # coverage-guided fuzzing; opt-in, runs nightly in CI
./gradlew test -Pgolden.regenerate # rewrite the golden files, then read the diff
```

Requires a JDK 21 or newer; the build targets Java 21 bytecode regardless of which you use.

`build` is the gate: it fails on a test failure, on coverage below 90% line or 85% branch in
`core`, on a module-dependency violation, on a descriptor whose field lengths do not add up, on
committed generated sources that have drifted from the descriptors they came from, and on a
committed fuzzing input that no longer behaves as it did when it was found.

Fuzzing itself is not in the gate — it is non-deterministic by design, which is the opposite of
what a per-commit check should be. Replaying what it has already found is, because that is
deterministic and takes about two seconds.

## Releasing

Publishing is a manually-approved GitHub Action and cannot be run from a developer machine — see
[RELEASING.md](RELEASING.md). `zengin4j-core`, `zengin4j-testkit` and `zengin4j-validation` publish
to `io.github.drag0sd0g`. `zengin4j-cli` is an application rather than a library and is not
published — which is why it is the one module allowed a runtime dependency
([ADR-0024](docs/adr/0024-picocli-for-the-cli.md)). The remaining modules are skeletons and
deliberately do not publish.

## Licence

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
