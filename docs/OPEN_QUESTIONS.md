# Open questions

Questions raised while implementing that are not settled. Each records what was implemented in the
meantime, which is always the more conservative reading (§0.6).

Resolutions become ADRs in [`adr/`](adr/).

A research pass on **2026-08-15** closed six of these against published sources and narrowed two
more; Epic 7 closed one more and raised one. A second pass on **2026-08-20** obtained Zengin-Net's
own ZEDI connection guidance, which closed three of the carried questions, corroborated the
character work against a fourth publisher, and turned up a reader defect (OQ-13). Closed entries are
kept rather than deleted: the reasoning is why the code looks the way it does, and a future reader
deserves the evidence, not just the outcome.

| | Question | State |
|---|---|---|
| OQ-1 | Disambiguating 種別コード `91` | **Closed** — one descriptor ([ADR-0020](adr/0020-one-descriptor-for-type-code-91.md)) |
| OQ-2 | Detection assumption for 200-byte formats | **Closed** — holds |
| OQ-3 | Over-length records | Open (design) |
| OQ-4 | Trailing separator | Closed — recorded by `FileFraming`, reproduced by the writer |
| OQ-5 | Test range for bank codes | **Closed** — `9999` verified unassigned |
| OQ-6 | `valueDate()` naming | **Closed** — `effectiveDate()` on the interface ([ADR-0021](adr/0021-the-shared-header-date-is-effective-date.md)) |
| OQ-7 | Blank `N` field | Open (design) |
| OQ-8 | 金融EDI情報 overlay | **Confirmed against the standard** — and now the only thing holding four formats unverified |
| OQ-9 | 預金種目 narrower set | **Closed** — master list of nine, narrowed per field, implemented in Epic 3 |
| OQ-10 | Should writing gate on `verified` as reading does? | **Closed** — yes, on the builder ([ADR-0019](adr/0019-building-gates-on-verified.md)) |
| OQ-11 | Should a conversion refuse on critical loss, or report it? | **Closed** — refuse, by default ([ADR-0033](adr/0033-critical-loss-fails-by-default.md)) |
| OQ-12 | What `To` belongs in the business application header | **Closed 2026-08-20** — the profile specifies the shape; the value is a bilateral credential. See OQ-15 |
| OQ-13 | A BAH with no message body is refused, and the profile allows one | **Open (defect)** — the two header-only requests identified |
| OQ-14 | The `pain.001` mapping does not follow the profile | **Open (epic-sized)** — ~14 rows differ |
| OQ-15 | The business application header does not follow the profile | **Open** — two small defects, one modelling decision |

---

## Work this created, by epic

Closing a question does not always remove work; sometimes it replaces a question with a task. This
index exists so that whoever picks up an epic sees those tasks without reading the whole document.

### Epic 3 — charset and the remaining 120-byte formats

**Completed 2026-08-16.** Kept for the reasoning; each item below is now implemented.

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

### Epic 5 — the command line tool

**Completed 2026-08-17.** No open question was closed by this epic; four were raised.

- **How far should `--unsafe-print` reach?** R-CLI4 forbids printing "full record contents" by
  default and R-CLI5 asks for a byte-annotated field table, which read strictly are in conflict. The
  line drawn is: it gates the fields the descriptors mark `sensitive`, and their hex as well.
  Amounts and names print. Written up and open to argument in
  [ADR-0026](adr/0026-what-unsafe-print-actually-gates.md).
- **Every bundled code list is `open: true`**, so "this value is not in the list" can never fire for
  a bundled format — only a *narrowed* field can reject. Whether an unlisted value in an open list
  deserves a soft note rather than silence is undecided; `inspect` currently says nothing, on the
  grounds that a tool which cries wolf on conforming files stops being read.
- **`convert` and `dryrun` are in the §27 synopsis and are not implemented.** Both are ISO 20022
  mappings and belong with that module rather than as stubs. → Epic 7.
- **Should the CLI let a caller pin the date that yearless `MMDD` fields resolve against?** As it
  stands `zengin validate` resolves them forward from today, so the same file can validate clean in
  August and trip a calendar rule in October — the tool's answer depends on when it is run, which is
  poor for something a pipeline branches on. The library already has `MonthDayResolver` and
  `ZenginValidator.withDateResolver(...)`; §27 lists no flag for it, so none was added. An
  `--as-of=YYYY-MM-DD` would close it, and would also let a stored file be re-validated as of the
  day it was sent. Found by a CLI test that would have started failing on 1 October 2026.

### Epic 6 — transliteration

**Completed 2026-08-17.** Four questions raised, one requirement contradicted.

- **R-K2's two named mappings are wrong**, and this library's own `V-202` says so: `ｰ` and small
  kana are permitted in no field class. Implemented as `ー`→`-` and `ャ`→`ヤ`, both `MATERIAL`.
  → [ADR-0028](adr/0028-the-specifications-kana-mappings-are-wrong.md). **The build specification
  still says otherwise** and has not been edited; the disagreement is recorded rather than
  smoothed over.
- **§7 places `kana` and `loss` in `iso20022`, and they cannot go there.** R-C18 puts
  transliteration on `core`'s write path, and `checkPomHasNoDependencies` forbids `core` depending
  on anything at all. → [ADR-0029](adr/0029-transliteration-lives-in-core.md).
- **A long vowel has no legal form in a payroll name.** `ー` becomes `-`, and `PAYROLL_NAME` admits
  no symbols, so ヨーコ cannot be written into a 給与振込 file at all. Refused by default, droppable
  by policy. Whether institutions in practice accept some other spelling is unconfirmed — no source
  consulted addresses it, and a bank's own guidance would settle it.
- **Whether `ｦ` should be reachable by transliteration.** `ヲ` narrows to `ｦ`, which only
  `EDI_INFORMATION` permits — so a name containing ヲ is refused for every other field. That is the
  conservative reading of D-001; a source saying otherwise would change it.

### Epic 7 — ISO 20022

**Completed 2026-08-18.** Two questions closed, one narrowed, one raised.

- `zengin convert` and `zengin dryrun` are built, with the context as flags rather than a file
  ([ADR-0034](adr/0034-the-mapping-context-is-flags-not-a-file.md)).
- The 金融EDI情報 overlay is read as a derived property of the record rather than modelled in the
  descriptor schema — the smaller of the two changes OQ-8 weighed, and the one that does not
  require the schema to express a condition. **The descriptor question is still open**; nothing yet
  writes the overlay back on the downward leg, because twenty bytes cannot hold a base64 payload.
  → [OQ-8](#oq-8--the-金融edi情報-overlay-is-not-modelled)
- The Base64 encoding is preserved exactly, line splitting and padding included (R-I12).
- **`JPZGN` rests on three secondary sources** and is load-bearing; the registry file itself is
  what would let the row stop saying `verified: false`. →
  [Q8](#q8--jpzgn-corroborated-three-times-one-primary-citation-short)
- **What belongs in the header's `To`** is a new question. → [OQ-12](#oq-12--what-belongs-in-the-headers-to)

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
- **A header-only ZEDI file is refused**, and R-I5 needs rewording before the reader changes.
  → [OQ-13](#oq-13--a-business-application-header-with-no-body-is-refused-and-the-profile-allows-one)

### Conforming to the ZEDI profile — the order to do it in

The 2026-08-20 pass produced more work than it closed, and the pieces are not equal in size. Doing
them in this order keeps each one reviewable, and puts the cheap certain fixes ahead of the large
uncertain one.

1. **Record the findings** — this document, [D-004](DISCREPANCIES.md), and the source entry.
   *Done 2026-08-20.*
2. **Fix the two `CreDt` formats.** *Done 2026-08-20.* `IsoDateTime` now renders both shapes and
   reads either: `format` writes `ISODateTime` with no offset for `GrpHdr/CreDtTm`, and
   `formatNormalised` converts to UTC for the header's `CreDt`. A value with no offset is read as
   UTC, which is what lets the plain shape survive its own round trip.
   → [OQ-15](#oq-15--the-business-application-header-does-not-follow-the-profile),
   [OQ-14](#oq-14--the-pain001-mapping-does-not-follow-the-profile)
3. **Fix the writer's `To` path** to `FIId/FinInstnId/Othr/Id`. *Done 2026-08-20.* The writer now
   distinguishes the two branches — `Fr` is an organisation, `To` is a financial institution — and
   the reader was already accepting both, so nothing on the inbound leg moved.
   → [OQ-15](#oq-15--the-business-application-header-does-not-follow-the-profile)
4. **Revise the mapping**, as its own epic: the branch codes out of `MmbId`, the trailer totals down
   into `PmtInf`, the party fields onto `Dbtr`/`UltmtDbtr`/`Cdtr`, the six dropped fields into the
   elements that hold them, and the inverse leg to match. Rows that end up matching the profile can
   cite it and stop saying `verified: false`, which is the whole point.
   → [OQ-14](#oq-14--the-pain001-mapping-does-not-follow-the-profile)

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

**Closed 2026-08-16, in Epic 3: one descriptor.** Two more sources confirmed the layouts are
identical — 大分銀行 gives both field by field with byte positions, and 北洋システム開発 states that
振替結果コード is zero on request and set by the bank on return. Registering two would make every
`91` file ambiguous while distinguishing nothing.

Direction-explicit naming, which §13.1 asks for in strong terms, is delivered by the field names
rather than by the format count: the header carries `collectionBankCode` and `debitDate`, the data
records carry `payerBankCode` and `debitAmount`, and none of 総合振込's directional names appear.
ADR-0007 is not superseded — its guard still applies to descriptors a consumer registers at
runtime, and to 振込入金通知's two variants in Epic 8, which differ in *positions* rather than
values. See [ADR-0020](adr/0020-one-descriptor-for-type-code-91.md).

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

**Closed 2026-08-16, in Epic 3: no, and the interface was renamed.**

Adding 預金口座振替 settled it. Its header date is 引落日 — the day the payers' accounts are
debited. Nothing reaches anybody on that date, so calling it a value date is wrong, and wrong in
the direction of a format whose whole hazard is direction.

`HeaderRecord.effectiveDate()` now carries the shared concept under a name true of both, and each
generated record also carries the name its own format uses: `valueDate()` on a 総合振込 header,
`debitDate()` on a 預金口座振替 one. Code written against a concrete format reads in that format's
terms (R-D1); code written against the interface gets a name that is honest about covering both.
See [ADR-0021](adr/0021-the-shared-header-date-is-effective-date.md).

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

## Raised during Epic 7

### OQ-11 — Should a conversion refuse on critical loss, or only report it?

R-I14 makes the loss report impossible to *miss* by putting it in the return type. R-I16 says
`CRITICAL` is *configurable* to hard-fail, and does not say which way the default goes.

**Closed 2026-08-18: it refuses.** `MappingContext.failOnSeverity` defaults to `CRITICAL`, and
`acceptAnyLoss()` is the way out — named so that it reads as what it is at the call site. See
[ADR-0033](adr/0033-critical-loss-fails-by-default.md).

`dryRun` and `roundTrip` never refuse whatever the threshold says, because their whole purpose is
to show the loss.

### OQ-12 — What belongs in the header's `To`

`head.001` requires `To`, and no Zengin field says outright who a file is addressed to.

**Implemented:** the file's own 仕向銀行番号. A 総合振込 file goes to the originator's own bank, and
that bank's code is in the header record — so the default is derived from the file rather than
invented. `MappingContext.receiver(...)` overrides it, and a file with no header record produces an
envelope that omits `To` and says so in the loss report, rather than one carrying a placeholder that
looks structurally fine and means nothing.

**Open, and — as of 2026-08-20 — not researchable after all.** The connection guide this entry was
waiting for has now been read, and it declines to answer. Zengin-Net's own material tells a company
transmitting a file that it must add a business application header and to confirm the details with
whichever transmission package it uses. The content of `Fr` and `To` is delegated to the bank or the
vendor, deliberately, which means there is no single correct value to find and no document that
would supply one.

That changes what the entry is. It is not a gap in the research; it is a variable the profile
leaves open, and the implementation already has the right shape for one: derive a default from the
file's own 仕向銀行番号, let `MappingContext.receiver(...)` override it, and omit `To` with a loss
entry rather than invent a placeholder when there is no header record to derive from. A caller
integrating with a named bank fills in what that bank asks for.

The connection guidance does specify the header's field table, but on image-only pages that no text
extraction reaches. Reading them needs OCR or a person, and would settle the shape of `BizSvc`,
`Prty` and `Rltd` — none of which is modelled, for the same reason as before.

**Closed 2026-08-20, once the tables were read rather than extracted.** The field tables are images,
which is why the text layer said nothing; rendering the pages and reading them settles it. The
profile specifies the shape exactly — `To` is a financial institution, reached through
`FIId/FinInstnId/Othr/Id`, and its value is 仕向銀行番号 followed by a colon and the counterparty's
centre confirmation code. `Fr` is an organisation, reached through `OrgId/Id/OrgId/Othr/Id`, and its
value ends in a password.

So both halves of this entry resolve, in opposite directions. The **shape** was findable and is now
known — and what is written does not match it, which is [OQ-15](#oq-15--the-business-application-header-does-not-follow-the-profile).
The **value** genuinely is delegated: a centre confirmation code and a password are agreed between a
company and its bank, so no amount of reading settles what a given file should carry. Deriving a
default from the file's own 仕向銀行番号 and letting the caller override it remains the right design,
because the derivable part is exactly the part the profile derives too.

`BizSvc`, `Prty` and `Rltd` are answered as well: the first is used and unmodelled, the other two do
not appear in the profile at all.

### OQ-13 — A business application header with no body is refused, and the profile allows one

**Raised 2026-08-20, from Zengin-Net's connection guidance.** `ZediEnvelopeReader` pairs each body
with the header in front of it and throws `danglingHeader()` when a header has no body, on the
stated reasoning that every `head.001` in the profile introduces exactly one body (R-I5).

That reasoning is true of 総合振込 and false of the profile. The guidance says plainly that a
振込入金通知 or 入出金取引明細 *request* consists of the header alone — there is no `pain.001` to
follow it, because the request carries no detail. The business the header refers to is named in its
own `MsgDefIdr`, which is how a reader is meant to tell the three apart.

**Impact is narrower than it sounds.** This library models 総合振込 only, so it cannot produce such
a file and no round trip it performs can hit the case. But `ZediEnvelopeReader.read` is a general
entry point over ZEDI bytes, and it currently rejects a file the profile defines as valid — with a
diagnostic that confidently states the opposite of the specification.

**Implemented:** nothing yet. The fix is small — a header with no body becomes a message with no
body, mirroring the bare-body case the reader already handles — but it needs a decision about what
`ZediMessage` means when it has no body, and R-I5 should be reworded before the code changes, since
the code is only repeating what the requirement says.

**Sharpened 2026-08-20.** The header's own `MsgDefIdr` names which business it introduces, and the
profile lists three values: `pain.001.001.03` for a 総合振込 request, `camt.054.001.02` for
振込入金通知, and `camt.052.001.02` for 入出金取引明細. The header-only files are the latter two —
the identifier names the message that *would* follow, and for a request there is nothing to follow
it. So the reader can tell the cases apart without guessing: a header naming a `camt` message is
complete on its own.

**One source.** The claim rests on a single document, though that document is the profile owner's
own. A second would be an institution's ZEDI guide describing either request type.

### OQ-14 — The `pain.001` mapping does not follow the profile

**Raised 2026-08-20, from the request mapping in Zengin-Net's connection guidance.** The document
lists every XML tag the system accepts, what each one carries, and its type and length. Read against
the declared mapping, about twelve rows agree and roughly fourteen do not.

*Rows that go somewhere else.*

| 全銀 field | Declared here | The profile's element |
|---|---|---|
| 仕向支店番号 / 被仕向支店番号 | packed into `ClrSysMmbId/MmbId` | `FinInstnId/BrnchId/Id`, `N(3)` — see [D-004](DISCREPANCIES.md) |
| トレーラ 合計件数 | `GrpHdr/NbOfTxs` | `PmtInf/NbOfTxs` |
| トレーラ 合計金額 | `GrpHdr/CtrlSum` | `PmtInf/CtrlSum` |
| 委託者コード | `GrpHdr/InitgPty/Id/OrgId/Othr/Id` | `PmtInf/Dbtr/Id/OrgId/Othr/Id`, with `SchmeNm/Cd` fixed to `BANK` |
| 委託者名 | `GrpHdr/InitgPty/Nm` and `PmtInf/Dbtr/Nm` | `PmtInf/UltmtDbtr/Nm` |
| 顧客コード1 | `CdtTrfTxInf/PmtId/EndToEndId` | `Cdtr/Id/OrgId/Othr/Id`, with `SchmeNm/Prtry` fixed to `Customer Code1` |
| 顧客コード2 | `CdtTrfTxInf/RmtInf/Ustrd` | the same shape, `Customer Code2` |

`GrpHdr/NbOfTxs` counts `PmtInf` blocks rather than payments, so both trailer totals are currently
one level too high in the tree. `RmtInf/Ustrd` is reserved for 金融EDI情報 (item 90), so 顧客コード2
is occupying an element that belongs to something else. `EndToEndId` is a reference the originating
company assigns freely (item 49), which is what [Q2](#q2-answered--endtoendid-has-no-right-home-so-the-caller-picks-one)
concluded independently — but it is not where 顧客コード1 goes.

*Fields dropped here that the profile has a home for.* 仕向支店名 and 被仕向支店名 (`BrnchId/Nm`,
店舗名称属性), 手形交換所番号 (`CdtrAgt/FinInstnId/Othr/Id`), 新規コード (`Purp/Prtry`), 振込指定区分
(`InstrForCdtrAgt/InstrInf`), and 識別表示 — which the profile packs together with both ダミー fields
into a 27-character colon-delimited `InstrForDbtrAgt`. That last one is how the profile achieves what
R-I6 wants: the filler bytes survive the round trip because there is somewhere to put them.

Each of these is currently reported as a loss. Six loss entries would stop existing.

*One more, unrelated to placement — and already fixed.* `GrpHdr/CreDtTm` is specified with **no UTC
offset at all**, nineteen characters or twenty-three with milliseconds. `IsoDateTime` used to append
one, which made the value twenty-five characters for a JST input and put it past the stated maximum.
Corrected 2026-08-20; the rest of this entry stands.

**Implemented:** the mapping as declared, every row `verified: false`, which is exactly the claim the
evidence supported. Nothing was ever asserted about conformance that this refutes.

**What this is.** Not a defect report — a specification arriving after the fact. The work is a
revision of the mapping, its loss model, its round-trip properties and its fixtures, and it is large
enough to be its own epic. The reward is that the corrected rows can cite a source and stop saying
`verified: false`.

### OQ-15 — The business application header does not follow the profile

**Raised 2026-08-20.** The same document specifies the header in twenty-four items, and three things
differ from what is written.

- ~~**`To` uses the wrong identifier path.**~~ *Fixed 2026-08-20.* The profile addresses a bank
  through `FIId/FinInstnId/Othr/Id` and a company through `OrgId/Id/OrgId/Othr/Id`; the writer used
  the organisation shape for both. Both are legal `head.001`, so no schema would have caught it —
  only the profile says which is meant. The reader already tried both paths and needed no change.
- ~~**`CreDt` must be UTC.**~~ *Fixed 2026-08-20.* Its type is `ISONormalisedDateTime`, whose
  lexical space ends in `Z`; `IsoDateTime.formatNormalised` converts the instant rather than writing
  whatever offset it was handed.
- **`BizSvc` is used and is not modelled.** It carries a colon-delimited control string — transfer
  mode, file name, character-code flag, connection type, resend flag. `Prty` and `Rltd` do not appear
  in the profile at all, which closes the other half of
  [OQ-12](#oq-12--what-belongs-in-the-headers-to).

**Not modellable in full, and that is not a gap.** Both `Fr` and `To` embed centre confirmation codes
agreed bilaterally between a company and its bank, and `Fr` embeds a **password**. No library can
derive a submittable header from file content, which is why `MappingContext.receiver(...)` exists and
why omitting `To` with a loss entry is the right failure. Two consequences worth carrying: a real
header read from a file **contains a credential**, so R-E6 masking applies to it in any diagnostic;
and the header this library writes should be understood as a placeholder a caller completes, not as
something submittable.

**The first two are small and self-contained.** The third needs a decision about how much of the
header to model at all.

---

## Carried from the build specification (§30)

| # | Question | Status |
|---|---|---|
| Q1 | Project name and Maven coordinate | **Answered 2026-08-20** — the coordinate is free; what remains is a choice. See below |
| Q2 | Where `EndToEndId` goes on the inverse leg | **Answered — the caller chooses. See below** |
| Q3 | Bundle bank/branch reference data, or require it? | **Answered 2026-08-20 — require it.** See below |
| Q4 | Hiragana input handling | **Answered** — `HiraganaPolicy`, defaulting to convert (Epic 6) |
| Q5 | Exact permitted symbol set for `C` fields | **Answered 2026-08-15 — see below** |
| Q6 | 振替結果コード list and per-institution variation | **Answered 2026-08-15 — see below** |
| Q7 | 200-byte format layouts | **Available** — JBA §§1–2. See OQ-2 |
| Q8 | ISO 20022 clearing system identifier | **`JPZGN` confirmed by the profile**; the seven-digit `MmbId` reading is **overturned** — see [D-004](DISCREPANCIES.md) |
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

**Corroborated 2026-08-20 by the profile owner.** Zengin-Net's ZEDI connection guidance does exactly
what this entry claims the answer is: it names five item attributes, maps every fixed-length field
to one of them, and lists what each admits. Its lists agree with `CharacterClass` on every symbol
and on the one detail most easily got wrong — that `ｦ` is admitted by the EDI attribute alone. That
is a fourth independent publisher, and the first that is the body defining the profile rather than
an institution describing it.

Two cautions for anyone re-reading that document. Its tables sit in a two-column layout that
collapses under naive text extraction, and a collapsed read makes the branch-name attribute look as
though it permits no symbols at all — it permits `-`, exactly as implemented. And every character
list renders its digits without a `0`, which is an artefact of the file rather than a claim about
the format.

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

### Q2, answered — `EndToEndId` has no right home, so the caller picks one

`EndToEndId` is mandatory in ISO 20022, is 35 characters, and is the reference the debtor and the
creditor reconcile against. The Zengin formats have no field for it. The nearest thing is a
顧客コード — ten bytes, and already carrying whatever the originator puts there.

There is no default that is right, so `EndToEndIdPolicy` makes the caller choose, and every option
reports what it costs:

| Policy | Cost |
|---|---|
| `CUSTOMER_CODE_1` | Truncation, reported `CRITICAL`. A cut reference looks usable and matches the wrong payment. |
| `CUSTOMER_CODE_2` | The same, for the other field. |
| `DROP` | Reported `MATERIAL`, and honest: the creditor has nothing to reconcile against. |

Going up, a reference that was never supplied is written as `NOTPROVIDED` — the value the standard
defines for exactly that — and is not reported as lost, because nothing was.

The default is `CUSTOMER_CODE_1`, which is where §15.9 puts it. Under the default refusal threshold
a reference that does not fit **stops the conversion**, so the truncation cannot happen by accident.

### Q8 — `JPZGN`, corroborated three times, one primary citation short

`JPZGN` is the ISO 20022 External Clearing System Identification code for the Zengin system, with
the member id being the seven digits of bank code plus branch code — consistent with the mapping
table in §15.9, which shows `MmbId` as `0009123`.

**Corroborated 2026-08-20 by three independent published sources, and still one short of the bar.**
All three agree that `JPZGN` is the `ExternalClearingSystemIdentification1Code` value for Japan, and
the registered definition is **"Bank Branch code used in Japan"**. That wording settles more than the
question asked: *Branch* is why the member id is seven digits rather than four, and why `BrnchId`
carries nothing. Industry references state the same shape independently — a seven-digit identifier,
no separator, for `pain.001`.

The authority is still the External Code Sets file itself, published quarterly by the Registration
Authority at the end of February, May, August and November as XLSX, XSD and JSON. It is a binary
download rather than a page, so it has not been read here, and **the mapping row stays
`verified: false` until it has been** (R-I19). When it is cited, cite the quarter: the sets are
versioned by publication, not by content, and "the External Code Sets" alone does not identify what
was read.

Nothing about the implementation is waiting on that citation — the value written and the
seven-digit structure are what all three sources describe. What is waiting is the claim that it has
been verified, which is a different thing and the one R-I19 governs.

**Implemented in Epic 7, and it is the single most load-bearing unverified value in the mapping.**
`ClrSysId/Cd` names the scheme that every bank code in the file belongs to, so getting it wrong
makes each of them ambiguous. It is written rather than omitted because omitting it would be no
safer — an unqualified `MmbId` is not more correct, only less legible.

**The seven-digit half is overturned, 2026-08-20.** The profile's own request mapping gives
`ClrSysMmbId/MmbId` as 銀行番号 alone, `N(4)`, and puts 支店番号 in `FinInstnId/BrnchId/Id` as `N(3)`,
on both the debtor and creditor sides. The reasoning recorded here — that `MmbId` identifies an
office and so must carry both — is a fair inference from the code set's own definition, and the
document describing how these files are actually built says otherwise. Recorded as
[D-004](DISCREPANCIES.md); correcting it is part of [OQ-14](#oq-14--the-pain001-mapping-does-not-follow-the-profile).

What is implemented today is the seven-digit reading: `Agent.memberId()` returns 銀行番号 followed by
支店番号 and `BrnchId` is unused. Coming back, a member id that is not four digits plus three cannot
be taken apart, and 支店番号 is left empty and reported `CRITICAL` rather than guessed at. That
inverse logic disappears with the concatenation, which is why this is not a one-line fix.

**The `JPZGN` half is confirmed, and by the strongest possible source.** The profile fixes
`ClrSysId/Cd` to that constant outright, so the value written has now been checked against the
document the receiving system is built from — not merely against the registry that defines the code.

### Q1, answered — the coordinate is free; the namespace is the decision

`io.zengin4j` returns no artifacts on Maven Central, so nothing is squatting on it. What stands
between that and a release is not availability but namespace verification: Central grants a
domain-shaped groupId only to someone who can prove they own the domain, by publishing a DNS TXT
record for `zengin4j.io`. The alternative costs nothing — `io.github.<user>` is verified by creating
a named public repository under that account.

So the research half is closed and the remaining half is a choice with a price attached: buy and
hold a domain for as long as the artifact is published, or accept a coordinate that names a
forge rather than a project. Deferred to R-B3, where it belongs, but no longer deferred for want of
knowing.

### Q3, answered — require the reference data, and the reason is freshness rather than licence

`zengin-code/source-data` is **MIT licensed**, which the README states and no `LICENSE` file
records — so tooling that reads repository metadata reports it as unlicensed, and a reviewer
checking that way will reach the wrong conclusion. Redistribution is permitted, with attribution.

Licensing is therefore not what decides this. Cadence is: the dataset is regenerated **monthly**,
and a snapshot compiled into a library that releases a few times a year would be stale for most of
its life. Stale bank and branch data in a *validator* is worse than absent data, because it produces
a confident finding about a branch that has since moved, merged or closed — and V-2xx rules exist to
be believed.

**Decided: not bundled.** `ReferenceDataProvider` stays the seam, and a consumer points it at a
copy they refresh on their own schedule. This is the same reasoning as R-M1 arriving at the same
place by a different road: the thing a payment library should not ship is a fact with an expiry
date.

One provenance note for anyone tempted to treat the dataset as a source rather than as evidence: it
credits its data to a third-party site, not to 全銀協. It is good evidence of which codes are *in
use* — which is all OQ-5 needed — and it is not a specification.

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
