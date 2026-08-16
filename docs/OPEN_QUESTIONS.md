# Open questions

Questions raised while implementing that are not settled. Each records what was implemented in the
meantime, which is always the more conservative reading (§0.6).

Resolutions become ADRs in [`adr/`](adr/).

A research pass on **2026-08-15** closed six of these against published sources and narrowed two
more. Closed entries are kept rather than deleted: the reasoning is why the code looks the way it
does, and a future reader deserves the evidence, not just the outcome.

| | Question | State |
|---|---|---|
| OQ-1 | Disambiguating 種別コード `91` | **Reframed** — the two layouts are identical |
| OQ-2 | Detection assumption for 200-byte formats | **Closed** — holds |
| OQ-3 | Over-length records | Open (design) |
| OQ-4 | Trailing separator | Closed — recorded by `FileFraming`, reproduced by the writer |
| OQ-5 | Test range for bank codes | **Closed** — `9999` verified unassigned |
| OQ-6 | `valueDate()` naming | Open (design) |
| OQ-7 | Blank `N` field | Open (design) |
| OQ-8 | 金融EDI情報 overlay | **Narrowed** — both ends now specified |
| OQ-9 | 預金種目 narrower set | **Closed** — master list plus per-field narrowing |
| OQ-10 | Should writing gate on `verified` as reading does? | **Closed** — yes, on the builder ([ADR-0019](adr/0019-building-gates-on-verified.md)) |

---

## Work this created, by epic

Closing a question does not always remove work; sometimes it replaces a question with a task. This
index exists so that whoever picks up an epic sees those tasks without reading the whole document.

### Epic 3 — charset and the remaining 120-byte formats

- **Character-set validation needs a per-field character class**, not one global permitted set. The
  permitted symbols differ by what the field *is*: one symbol for branch names, four for party
  names, eight for EDI. 給与振込 forbids Latin letters entirely, which a 総合振込-shaped validator
  would never catch. → [Q5](#q5-answered--the-permitted-character-set-is-per-field-class-not-per-format)
- **`accountType` currently ships the narrowed set as though it were the master list.** Carry all
  nine values from 付録3 and let each field declare its permitted subset. → [OQ-9](#oq-9--the-headers-預金種目-admits-a-narrower-set-than-the-data-records)
- **Do not derive the 預金口座振替 trailer from 総合振込.** It has its own shape: 合計件数, 合計金額,
  振替済件数/金額, 振替不能件数/金額, ダミー C(65). → [Q6](#q6-answered--振替結果コード)
- **Decide whether `91` is one descriptor or two**, given the two layouts are identical and differ
  only in values — and revisit ADR-0007, which may need superseding rather than amending. The same
  shape recurs for `01`. → [OQ-1](#oq-1--how-should-種別コード-91-be-disambiguated)
- **Rename `valueDate()` or keep it**, decided against a format where the name is misleading.
  → [OQ-6](#oq-6--does-headerrecordvaluedate-generalise-beyond-総合振込)
- 給与振込 and 賞与振込 descriptors can now be written from three corroborating sources.
  → [Q9](#q9-answered--給与振込-is-not-総合振込-with-three-fields-renamed)

### Epic 4 — validation

- Whether a `V-2xx` rule distinguishes a blank `N` field from a non-numeric one. → [OQ-7](#oq-7--what-does-a-blank-n-field-mean)
- Whether a field can narrow a shared code list, or lists must be split. → [OQ-9](#oq-9--the-headers-預金種目-admits-a-narrower-set-than-the-data-records)
- `zengin-code/source-data` is the obvious `ReferenceDataProvider` dataset. → [OQ-5](#oq-5--there-is-no-published-test-range-for-bank-and-branch-codes)

### Epic 7 — ISO 20022

- **Model the 金融EDI情報 overlay** — a conditional descriptor field, or a derived accessor reading
  the twenty bytes when 識別表示 is `Y`. → [OQ-8](#oq-8--the-金融edi情報-overlay-is-not-modelled)
- **Preserve the Base64 encoding exactly**, including the 76-character line split across `<Ustrd>`
  elements and the three MIME headers. Re-encoding produces different XML for identical content
  (R-I12). → [OQ-8](#oq-8--the-金融edi情報-overlay-is-not-modelled)
- **Confirm `JPZGN`** against the ISO 20022 External Code Sets before any mapping row claims
  verification. → [Q8](#q8--jpzgn-pending-primary-confirmation)

### Epic 8 — 200-byte formats

- **和暦 dates.** 作成日 and 勘定日（自）（至） are `N(6)` YYMMDD in the Japanese imperial era.
  `FieldFormat` models only `MMDD`, and era boundaries are not arithmetic — 平成 ended mid-year in
  2019. → [OQ-2](#oq-2--does-the-format-detection-assumption-hold-for-the-200-byte-formats)
- **Capture the layouts.** They are available in the JBA document §§1–2; only the header has been
  read so far. → [Q7](#carried-from-the-build-specification-30)
- **振込入金通知 has two variants** (フォーマットA/B) sharing 種別コード `01`, differing in whether
  12-digit amount fields are present. → [OQ-1](#oq-1--how-should-種別コード-91-be-disambiguated)

### Unassigned

- **Over-length records (R-C5)** is in no epic in the work breakdown. → [OQ-3](#oq-3--over-length-records-r-c5)

---

## Raised during Epic 1

### OQ-1 — How should 種別コード `91` be disambiguated?

**Reframed 2026-08-15. The premise was wrong, and the correction matters.**

The JBA document's §16 states that 預金口座振替（処理結果明細） is *「次の項目以外は預金口座振替(依頼明細)と同一」*
— identical to the instruction format except for the listed items. Those items are **values, not
positions**: 振替結果コード is `0` in an instruction file and carries a result code in a result
file, and the trailer's 振替済件数／金額 and 振替不能件数／金額 are all zeros in an instruction file.

So the two formats **have the same byte layout**. There is nothing to disambiguate at the parsing
level, and one descriptor serves both.

**Implication for the current design.** `AmbiguousFormatException` may be solving a problem that
does not exist. If both `91` descriptors are the same layout, the reader can parse either without
knowing which it holds, and the instruction/result distinction becomes a *semantic* question for
the caller or the validation layer — not a reason to refuse the file.

**Still open:** whether to register one `91` descriptor or two. One is simpler and matches the
standard; two would let the record types carry direction-explicit names, which §13.1 asks for in
strong terms. Decide in Epic 3, and revisit ADR-0007 when doing so — it may need superseding rather
than amending.

**Note:** the same shape appears again in 振込入金通知, which has two variants (フォーマットA and
フォーマットB) sharing 種別コード `01` and differing in whether 12-digit amount fields are present.
Whatever is decided for `91` should hold for `01`.

### OQ-2 — Does the format-detection assumption hold for the 200-byte formats?

**Closed 2026-08-15. It holds.**

The JBA document's §1 振込入金通知 header begins:

| 項番 | 項目名 | 桁数 | Byte |
|---|---|---|---|
| 1 | データ区分 | N(1) | 0 |
| 2 | 種別コード | N(2) | 1–2 |
| 3 | コード区分 | N(1) | 3 |

Identical positioning to every 120-byte format. Detection reads データ区分 at byte 0 and
種別コード at bytes 1–2 before it knows the layout, and that is safe for the 200-byte formats too.

種別コード values recovered at the same time: `01` 振込入金通知, `03` 入出金取引明細 (from the
file-name table). The build specification marked both `[VERIFY]`.

**New work this creates, not a question:** the 200-byte headers carry 作成日 and 勘定日（自）（至）
as **N(6) YYMMDD in 和暦**, the Japanese imperial era. That is a third date encoding, and
`FieldFormat` models only `MMDD`. Era boundaries are not arithmetic — 平成 ended mid-year in 2019 —
so this needs its own interpretation and its own care. Epic 8.

### OQ-3 — Over-length records (R-C5)

Some institutions emit records longer than the standard layout, space-padding the excess. R-C5 asks
for a configurable record length *and* a lenient mode that accepts over-length records and preserves
the trailing bytes.

**Implemented:** the configurable override only (`ReaderOptions.recordLength`). Lenient acceptance
of over-length records is not implemented: with no separators in the file there is no signal that
distinguishes an over-length record from the next record starting early, and inventing one risks
misaligning every subsequent record.

**Still open, and not researchable.** No source describes what a reader should do with a
non-conforming file; that is our API's decision. R-C5 is not assigned to an epic in the work
breakdown.

### OQ-4 — Is a trailing separator after the final record part of the file's framing?

`FileFraming` records whether separators were used and which convention, but not whether one
followed the *last* record. Both forms occur in practice.

**Implemented:** the reader accepts either; the testkit always writes one.

**Evidence gathered 2026-08-15.** The question splits in two, and only one half was open.

*What the sources say.* 群馬銀行 states the record length as
「１２０バイト（改行コード(ＣＲＬＦ)をつける場合は後付けで１２２バイト）」 — 120 bytes, or 122 when
a CRLF is **appended** (後付け). That framing makes the separator a per-record *suffix* rather than
a delimiter placed *between* records, which entails one after the final record too. No source
consulted describes it the other way.

*What files actually do.* The sample file in `Kyash/zengin-go` ends at byte `0x39` — the end
record's データ区分, with no trailing separator. That file has also had its trailing spaces
stripped on every line, so it has been through text tooling and is weak evidence of intent; but it
demonstrates the form exists.

**Resolved:** the writer's default. Emit a separator after every record including the last, because
that is what the documented framing describes. This is no longer a coin-flip.

**Closed 2026-08-16, in Epic 2.** The second half — whether `FileFraming` must record which form
the *input* used — was answered by INV-1 rather than by a source: byte-exactness is owed to
whatever arrived, not to what the standard prefers, and files omitting the final separator
demonstrably exist.

`FileFraming.trailingSeparator()` now carries it. `StreamingZenginReader` tracks whether a
separator run followed the record it last returned; `ZenginWriters` emits a separator after the
final record only when the file it read had one; `FileFraming.conventional()` — the builder's
default for a file that was never read — has it `true`, per the 後付け framing above.
`reproducesTheAbsenceOfATrailingSeparator` and the INV-1 property both pin it.

### OQ-5 — There is no published test range for bank and branch codes

**Closed 2026-08-15.** The literal question keeps its answer — no reserved test range appears to be
published — but the concern behind it is now settled with evidence rather than assumption.

Checked against the `zengin-code/source-data` open dataset (1,146 institutions, retrieved
2026-08-15): **`9900` (ゆうちょ銀行) is the only assigned code in the entire `99xx` block.** `9999`,
`9998`, `9997` and `9990` are all unassigned.

So the testkit's invented range — bank `9999`, branch `999`, accounts beginning `9` — is
demonstrably not in use by any Japanese institution, which is what P1 and R-L1 actually care about.
`everyIdentifierIsOutsideTheRangesRealInstitutionsUse` pins it so it cannot drift.

### OQ-6 — Does `HeaderRecord.valueDate()` generalise beyond 総合振込?

The shared header interface (§11) exposes `valueDate()`. In 預金口座振替 the equivalent field is the
引落日, and the institution named in the header is the collection destination rather than the
originator.

**Implemented:** the interface follows §11, and the generated accessor maps to whichever field
declares `format: MMDD`, whatever its id.

**Still open, and not researchable.** No published source has an opinion about what this library
names its accessors. Decide in Epic 3, when 預金口座振替 makes the name concretely misleading — and
note that the 200-byte formats will add 作成日 and 勘定日, neither of which is a "value date" in any
sense, which strengthens the case for a neutral name.

### OQ-7 — What does a blank `N` field mean?

The specification says an omitted `N` field is all zeros. Files in the wild sometimes leave numeric
fields entirely blank instead.

**Implemented:** `asLong` raises `MalformedFieldException` naming the byte, and `asOptionalLong`
returns empty. Nothing is coerced to zero.

**Still open, and not researchable.** The standard states the padding rule (右詰め残り前「0」) and is
silent on what a reader should do when a file breaks it. That silence is the answer to the research
question and not to the design one: whether a `V-2xx` rule should distinguish "blank" from
"non-numeric" is for Epic 4.

### OQ-8 — The 金融EDI情報 overlay is not modelled

**Narrowed 2026-08-15. Both ends of the mapping are now specified.**

*The fixed-length end.* Confirmed by all six sources: when 識別表示 is `Y`, data fields 12 and 13
are one `C(20)` 金融EDI情報 field. The JBA 使用文字一覧 注3 adds its character set — カナ
(**including** ヲ, excluding small kana), 濁点, 半濁点, A–Z, digits, SP, and eight symbols
`\ 「 」 ( ) - / .` — with two warnings worth carrying into validation: some banks do not accept all
eight, and **comma must never be used**, because some bank systems treat it as an EDI delimiter.

*The ISO 20022 end.* Zengin-Net's own ZEDI manual specifies the structure exactly:

```xml
<RmtInf>
  <Ustrd>MIME-Version: 1.0</Ustrd>
  <Ustrd>Content-Type: text/xml</Ustrd>
  <Ustrd>Content-Transfer-Encoding: base64</Ustrd>
  <Ustrd>ZT48L1RheEluZj48L1RyYW5JbmY+...</Ustrd>
  <Ustrd>L1BtdFlNRD48L1BtdEluZj4...</Ustrd>
</RmtInf>
```

Three MIME headers, one per `<Ustrd>`, then the Base64 payload **wrapped at 76 characters**, each
line its own `<Ustrd>`. This is precisely the shape R-I10 anticipated, and the 76-character
wrapping is exactly why R-I12 demands the encoding be preserved rather than regenerated: a
re-encode that re-wraps differently produces different XML for identical content.

**Still open:** how to model the fixed-length overlay — a conditional field in the descriptor
schema, or a derived accessor on the generated record that reads the twenty bytes when the flag is
set. The second remains the smaller change. Epic 7 needs it either way.

### OQ-9 — The header's 預金種目 admits a narrower set than the data record's

**Closed 2026-08-15, and the answer is bigger than the question.**

付録3 預金種目コード gives the master list — **nine values**, not four:

| Code | 預金種目 | English |
|---|---|---|
| 1 | 普通預金 | Ordinary deposit |
| 2 | 当座預金 | Current account |
| 3 | 納税準備預金 | Tax reserve deposit |
| 4 | 貯蓄預金 | Savings deposit |
| 5 | 通知預金 | Deposit at notice |
| 6 | 定期預金 | Time deposit |
| 7 | 積立定期預金 | Instalment time deposit |
| 8 | 定期積金 | Instalment savings |
| 9 | その他 | Other |

and states the rule directly: *「全ての業務について表中 1〜9 の全てのコードが使えるわけではない…
使用するコード区分が限定列挙されている場合には、当該定めに従う」* — not every code is valid for
every business, and where a format enumerates a subset, that subset governs.

So the model OQ-9 was reaching for is the one the standard uses: **a shared master list, narrowed
per field by enumeration.**

**New work this creates, not a question:** the bundled `accountType` list currently holds four
values (1, 2, 4, 9) — the *narrowed* set for 総合振込 presented as though it were the whole list.
That is now demonstrably incomplete as a master list. Epic 3 should carry all nine and let each
field declare its permitted subset.

### OQ-10 — Should writing gate on `verified` the way reading does?

**Raised 2026-08-16, in Epic 2.** Reading a file with an unverified descriptor throws
`UnverifiedFormatException` unless the caller sets `allowUnverifiedFormats(true)` — issue 1.9,
enforcing R-0.1. `ZenginFileBuilder` and `ZenginWriters` have no equivalent gate: they take a
`FormatDescriptor` and use it, verified or not.

**Why this is not obviously a defect.** R-0.1 governs what the flag means and what evidence sets
it; the opt-in mechanism is scoped by issue 1.9 to reading, and the specification does not extend
it to writing. The write path also has no options object to hang the switch on — the builder takes
a descriptor directly, so adding a gate is an API change rather than a new field.

**Why it may still be wrong.** The consequences are asymmetric, and not in the direction the
current design protects. A wrong offset when reading produces wrong data inside the caller's own
system, where their own reconciliation may catch it. A wrong offset when writing produces a payment
instruction that a bank will act on. If the opt-in exists so that trusting a provisional layout is
recorded where a reviewer can see it, that argument is *stronger* for output than for input.

**Closed 2026-08-16: yes, and on the builder.** `ZenginFileBuilder.build()` now throws
`UnverifiedFormatException` unless `allowUnverifiedFormats(true)` was set.

The gate went on the builder rather than on `WriterOptions`, which was the first proposal. Two
reasons. `ZenginFile` carries a `FormatId`, not a `FormatDescriptor`, so a writer-side check would
have to rediscover the descriptor through a registry to read a flag the builder already had. And
the risk is not evenly spread across the two paths: building places caller-supplied values at
descriptor-defined offsets, which is the step a provisional layout can get wrong, whereas writing a
file that was just *read* reproduces bytes that already existed. Gating the writer would have added
friction to the one path that introduces no new risk.

The exception names whichever opt-in the caller actually needs — a diagnostic that prescribes the
wrong remedy costs more than one that says nothing. See
[ADR-0019](adr/0019-building-gates-on-verified.md).

---

## Carried from the build specification (§30)

| # | Question | Status |
|---|---|---|
| Q1 | Project name and Maven coordinate | Placeholder `io.zengin4j` retained; check before publishing (R-B3) |
| Q2 | Where `EndToEndId` goes on the inverse leg | Epic 7. Design decision, not researchable |
| Q3 | Bundle bank/branch reference data, or require it? | Epic 4. `zengin-code/source-data` is the obvious dataset — see OQ-5 |
| Q4 | Hiragana input handling | Epic 6. Design decision |
| Q5 | Exact permitted symbol set for `C` fields | **Answered 2026-08-15 — see below** |
| Q6 | 振替結果コード list and per-institution variation | **Answered 2026-08-15 — see below** |
| Q7 | 200-byte format layouts | **Available** — JBA §§1–2. See OQ-2 |
| Q8 | ISO 20022 clearing system identifier | **Probably `JPZGN`** — see below, needs primary confirmation |
| Q9 | 給与 / 賞与 field repurposing | **Answered — three independent sources** |
| Q10 | Should `0.1.0` ship `verified: false` formats? | Yes, gated behind `allowUnverifiedFormats` |

### Q5, answered — the permitted character set is per field class, not per format

付録1 使用文字一覧 注1–注3 enumerate it exactly. The set that applies depends on what the field
*is*, which is more structure than the descriptor schema currently expresses.

| Field class | Permitted |
|---|---|
| 店舗名 (bank/branch names) | カナ (no ヲ, no small kana), 濁点, 半濁点, A–Z, 0–9, and **one** symbol: `-` |
| 口座名・受取人名・委託者名 etc. | カナ (no ヲ, no small kana for 総合振込), 濁点, 半濁点, A–Z, 0–9, SP, and **four** symbols: `( ) - .` |
| 給与振込・賞与振込, other fields | カナ (no ヲ, no small kana), 濁点, 半濁点, 0–9, SP — **no Latin letters at all** |
| EDI情報 | カナ (**ヲ allowed**, no small kana), 濁点, 半濁点, A–Z, 0–9, SP, and **eight** symbols `\ 「 」 ( ) - / .`; never comma |

**New work this creates:** Epic 3's character-set validation needs a per-field character class, not
one global permitted set. Note also that 給与振込 forbidding A–Z is a rule a 総合振込-shaped
validator would never catch.

### Q6, answered — 振替結果コード

The specification called this "the highest-value verification item". JBA §16 enumerates it:

| Code | 意味 | Meaning |
|---|---|---|
| 0 | 振替済 | Collected |
| 1 | 資金不足 | Insufficient funds |
| 2 | 取引なし | No such account / no transaction |
| 3 | 預金者の都合による振替停止 | Stopped at the depositor's instruction |
| 4 | 預金口座振替依頼書なし | No direct debit mandate on file |
| 8 | 委託者の都合による振替停止 | Stopped at the originator's instruction |
| 9 | その他 | Other |

The build specification guessed "account closed" for one of these. The standard says code 4 is
*no mandate on file* — a different thing, and one that would have produced a wrong English gloss on
a code integrators rely on. §0.2 earning its keep again.

**Also recovered:** the 預金口座振替 trailer is **not** the 総合振込 trailer. It carries 合計件数,
合計金額, 振替済件数 N(6), 振替済金額 N(12), 振替不能件数 N(6), 振替不能金額 N(12), ダミー C(65).
Epic 3 must not derive it from 総合振込.

### Q8 — `JPZGN`, pending primary confirmation

`JPZGN` is the ISO 20022 External Clearing System Identification code for the Zengin system, with
the member id being the seven digits of bank code plus branch code — consistent with the mapping
table in §15.9, which shows `MmbId` as `0009123`.

**Not yet confirmed against a primary source.** This came from secondary references, and the
authority is the ISO 20022 External Code Sets published by iso20022.org. Confirm there before the
mapping row is marked anything but `verified: false` (R-I19).

### Q9, answered — 給与振込 is not 総合振込 with three fields renamed

Confirmed by **three independent sources**: JBA §4, 十八親和銀行, and 愛知銀行 — all giving field
14 as ダミー C(9).

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
the other looks safe and is not: a parser assuming 総合振込's tail would read 給与振込's filler as a
振込指定区分 and an 識別表示, and would treat 社員番号 and 所属コード as an EDI payload whenever the
byte at offset 112 happened to be `Y`.

賞与振込 is identical to 給与振込 with 種別コード `12`, stated as such in the standard — so for once
deriving it *is* the documented reading.
