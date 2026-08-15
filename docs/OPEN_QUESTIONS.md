# Open questions

Questions raised while implementing that are not settled. Each records what was implemented in the
meantime, which is always the more conservative reading (§0.6).

Resolutions become ADRs in [`adr/`](adr/).

---

## Raised during Epic 1

### OQ-1 — How should 種別コード `91` be disambiguated?

預金口座振替 and 口座振替結果 share 種別コード `91` and differ only in whether the 振替結果コード is
populated (§13.1). A file therefore cannot always identify its own format.

**Implemented:** `FormatRegistry.byTypeCode` returns a list rather than an `Optional`, and the
reader raises `AmbiguousFormatException` naming both candidates and telling the caller to specify
the format. Guessing between an instruction file and a result file would be a guess about payment
direction.

**Corroborated 2026-08-15.** The JBA document's file-name table assigns データ・サイクルコード
`0191` to 預金口座振替(依頼明細) *and* to 預金口座振替(処理結果明細) — the same code for the
instruction file and the result file, from the standard itself. The ambiguity is a property of the
scheme, not of this registry.

**Open:** whether a heuristic is ever acceptable here — for example, a result file being
identifiable by a populated result code in the first data record. Deferred to Epic 3, when both
descriptors exist.

### OQ-2 — Does the format-detection assumption hold for the 200-byte formats?

Detection reads データ区分 at byte 0 and 種別コード at bytes 1–2, before any descriptor is known. It
has to read *something* before it can know the layout. That position is consistent across every
120-byte format defined so far, but the 200-byte formats (振込入金通知, 入出金取引明細) are
unconfirmed and may differ.

**Implemented:** the constants live in `StreamingZenginReader` with a comment pointing here, and a
caller can always bypass detection with `ReaderOptions.format(...)`.

**Open:** confirm against a published 200-byte layout before Epic 8.

### OQ-3 — Over-length records (R-C5)

Some institutions emit records longer than the standard layout, space-padding the excess. R-C5 asks
for a configurable record length *and* a lenient mode that accepts over-length records and preserves
the trailing bytes.

**Implemented:** the configurable override only (`ReaderOptions.recordLength`). Lenient acceptance
of over-length records is not implemented: with no separators in the file there is no signal that
distinguishes an over-length record from the next record starting early, and inventing one risks
misaligning every subsequent record.

**Open:** whether to accept over-length records when a separator is present, and how to represent
the trailing bytes on the record. R-C5 is not assigned to an epic in the work breakdown.

### OQ-4 — Is a trailing separator after the final record part of the file's framing?

`FileFraming` records whether separators were used and which convention, but not whether one
followed the *last* record. Both forms occur in practice.

**Implemented:** the reader accepts either; the testkit always writes one.

**Open:** INV-1 (byte-exact round trip) needs this distinction before Epic 2 can claim it for files
that omit the final separator. Either extend `FileFraming` or scope INV-1 to files that carry one.

### OQ-5 — There is no published test range for bank and branch codes

R-L1 asks that fixtures use codes "drawn from public reference datasets or documented test ranges".
Public datasets of Japanese bank codes exist, but every code in them belongs to a real institution,
and no reserved test range appears to be published.

**Implemented:** the testkit invents one and documents it — bank `9999`, branch `999`, accounts
beginning with `9` — and asserts it in a test so it cannot drift. The worked example in the build
specification uses a real bank code with an invented name; this project does not reproduce that.

**Open:** whether an authoritative reserved range exists.

### OQ-6 — Does `HeaderRecord.valueDate()` generalise beyond 総合振込?

The shared header interface (§11) exposes `valueDate()`. In 預金口座振替 the equivalent field is the
引落日, and the institution named in the header is the collection destination rather than the
originator — the specification warns explicitly that reversing this produces payments in the wrong
direction.

**Implemented:** the interface follows §11, and the generated accessor maps to whichever field
declares `format: MMDD`, whatever its id. The concrete type keeps the format's own name — a
預金口座振替 record would expose `debitDateRaw()` alongside the inherited `valueDate()`.

**Open:** whether `valueDate()` should be renamed to something direction-neutral before Epic 3 adds
the format that makes the name misleading.

### OQ-7 — What does a blank `N` field mean?

The specification says an omitted `N` field is all zeros. Files in the wild sometimes leave numeric
fields entirely blank instead.

**Implemented:** `asLong` raises `MalformedFieldException` naming the byte, and `asOptionalLong`
returns empty. Nothing is coerced to zero.

**Open:** whether a `V-2xx` validation rule should distinguish "blank" from "non-numeric" when the
validation layer arrives in Epic 4.

### OQ-8 — The 金融EDI情報 overlay is not modelled

Every source consulted documents the same conditional: when 識別表示 (data field 15) is `Y`, fields
12 and 13 stop being 顧客コード1/2 and become a single `C(20)` 金融EDI情報 field.

The descriptor schema has no way to say that. A field is one field, at one offset, with one
attribute.

**Implemented:** nothing conditional. The two ten-byte fields are always present, so an EDI payload
is readable as two halves and the record's raw bytes carry it whole — nothing is lost, it is only
untyped. `identification` carries a note pointing here.

**Open:** this matters more than its size suggests. 金融EDI情報 is ZEDI's entire value proposition
and the payload the ISO 20022 mapping has to carry (R-I10), so Epic 7 needs it typed. Options are a
conditional/overlay field in the descriptor schema, or a derived accessor on the generated record
that reads the twenty bytes when the flag is set. The second is smaller and does not complicate
every descriptor for one case.

### OQ-9 — The header's 預金種目 admits a narrower set than the data record's

The JBA document lists 1, 2 and 9 for the originator's own 預金種目 in the header, and 1, 2, 4 and 9
for the beneficiary's in the data record — 貯蓄預金 is absent from the header.

**Implemented:** one shared `accountType` code list carrying all four values, referenced from both.
The list is open, so nothing is rejected either way; the effect is only that a header declaring 4
would not be flagged.

**Open:** whether to split the list, or to let a field narrow a shared list to a subset. Worth
deciding when the validation rules arrive in Epic 4, not before.

---

## Carried from the build specification (§30)

| # | Question | Status |
|---|---|---|
| Q1 | Project name and Maven coordinate | Placeholder `io.zengin4j` retained; check Maven Central and GitHub before publishing (R-B3) |
| Q2 | Where `EndToEndId` goes on the inverse leg | Epic 7 |
| Q3 | Bundle bank/branch reference data, or require it? | Epic 4 |
| Q4 | Hiragana input handling | Epic 6 |
| Q5 | Exact permitted symbol set for `C` fields | Open. Modelled as configurable per descriptor; character-set validation arrives in Epic 3 |
| Q6 | 振替結果コード list and per-institution variation | Epic 3. Highest-value verification item |
| Q7 | 200-byte format layouts | Epic 8; see OQ-2 |
| Q8 | ISO 20022 clearing system identifier for the domestic scheme | Epic 7 |
| Q9 | 給与 / 賞与 field repurposing in data fields 12–14 | **Partly answered 2026-08-15 — see below.** One source so far; Epic 3 still needs a second |
| Q10 | Should `0.1.0` ship with `verified: false` formats at all? | Yes, gated behind `allowUnverifiedFormats`, stated in the README and DISCLAIMER |

### Q9, in detail — 給与振込 is not 総合振込 with three fields renamed

The build specification's §13.1 describes 給与振込 as "structurally identical to 総合振込, with data
record fields 12–14 repurposed", and warns in the same breath not to derive the layout from
総合振込. That warning was well placed: the JBA document's own 給与振込 section shows the data
record is **not** structurally identical.

| | 総合振込 | 給与振込 |
|---|---|---|
| Data record fields | 16 | **14** |
| Field 9 | 受取人名 `C(30)` | 預金者名 `C(30)` |
| Field 12 | 顧客コード1 `N(10)` | 社員番号 `N(10)` |
| Field 13 | 顧客コード2 `N(10)` | 所属コード `N(10)` |
| Field 14 | 振込指定区分 `N(1)` | **ダミー `C(9)`** |
| Field 15 | 識別表示 `C(1)` | — |
| Field 16 | ダミー `C(7)` | — |
| 預金種目 values | 1, 2, 4, 9 | **1, 2 only** |
| EDI情報 overlay | yes | **no** |

The last twenty-nine bytes total the same either way, which is exactly why deriving one layout from
the other looks safe and is not: a parser that assumed 総合振込's tail would read 給与振込's filler
as a 振込指定区分 and an 識別表示, and would then treat 社員番号 and 所属コード as an EDI payload
whenever the byte at offset 112 happened to be `Y`.

賞与振込 is identical to 給与振込 with 種別コード `12` — stated as such in the standard, so for once
deriving it *is* the documented reading.

Source: 全国銀行協会, 標準通信プロトコル適用業務およびレコード・フォーマット, 令和元年12月, sections
4 and 5. Retrieved 2026-08-15. **One source.** Epic 3 needs a second before either descriptor ships
as anything but `verified: false`.
