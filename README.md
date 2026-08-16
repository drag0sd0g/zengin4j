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

This repository is at **Epic 2 — reading and writing 総合振込**. What works today:

| | |
|---|---|
| ✅ | Reading 総合振込 (`21`) files: streaming, batch and whole-file APIs |
| ✅ | Writing files back **byte for byte**, including framing the file arrived with |
| ✅ | Building files, with each batch trailer's count and total computed from its payments |
| ✅ | Format descriptors as data, with computed byte offsets and a build-time length check |
| ✅ | Generated, format-shaped record types, committed and drift-checked |
| ✅ | Optional separators (none / CR / LF / CRLF, even mixed), byte order marks, EOF byte |
| ✅ | Strict and lenient parsing, with malformed records surfaced as data rather than exceptions |
| ✅ | Year inference for the yearless `MMDD` dates, as an explicit, named decision |
| ⬜ | The remaining 120-byte formats and the character-set machinery — Epic 3 |
| ⬜ | Validation with byte-level findings, JSON and SARIF — Epic 4 |
| ⬜ | CLI — Epic 5 |
| ⬜ | Half-width katakana transliteration and dakuten-safe truncation — Epic 6 |
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
                      .set("originatorName", "ﾃｽﾄｼｮｳｼﾞ")
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

Runnable versions live in [`examples/`](examples/).

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

## Licence

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
