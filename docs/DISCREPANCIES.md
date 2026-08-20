# Discrepancies

Where sources disagree about a format, both readings are recorded here with citations, the more
conservative one is implemented, and the descriptor stays `verified: false` until the question is
settled (R-0.2, R-L4).

---

## D-001 — Byte length of `ﾃｽﾄｷﾞﾝｺｳ` in the worked example

**Status:** resolved by arithmetic. No effect on any byte layout.

**Sources**

| Source | Reading |
|---|---|
| Build specification §20.1, note 1 | "`ﾃｽﾄｷﾞﾝｺｳ` is 9 bytes for 7 apparent characters" |
| Arithmetic over the CP932 encoding | 8 bytes for 7 rendered characters |

**Analysis.** `ﾃｽﾄｷﾞﾝｺｳ` decomposes into ﾃ, ｽ, ﾄ, ｷ, ﾞ, ﾝ, ｺ, ｳ — eight half-width characters, each
one byte in Shift_JIS and CP932, rendering as the seven characters テストギンコウ. The ｷﾞ is a base
kana followed by a standalone dakuten, which is the point the note is making; the byte count in the
note appears to be a slip.

The same section's note 2 — "`ﾔﾏﾀﾞ` is 4 bytes for 3 apparent characters" — is arithmetically
correct and consistent with this reading.

**Implemented.** The eight-byte reading, which is simply what the encoding produces. The fixture
`SougouFurikomiFixtures.BENEFICIARY_BANK_NAME` asserts it, and
`StreamingZenginReaderTest.voicingMarksAreSeparateCharactersAndSeparateBytes` pins down all three
counts that matter: eight code points, eight bytes under MS932, twenty-four bytes under UTF-8.

**Consequence.** None for any field offset. Recorded because the note is the specification's
illustration of *why* byte arithmetic matters, and an incorrect figure in that illustration is
worth catching before it is copied into documentation.

---

## D-002 — Attribute of 顧客コード1 and 顧客コード2, and the fields sharing their bytes

**Settled 2026-08-16 against the primary source. It was never a disagreement.**

Institution publications give these fields as `N`, as `C`, or as "N(10) または
C(10)", and the same split appears in 給与振込's 社員番号/所属コード and in
預金口座振替's 顧客番号 — the same byte positions in three formats. That looked
like five sources contradicting each other.

The JBA standard resolves it by describing the field twice, deliberately:

| 項番 | 項目名 | 桁数 | Condition |
|---|---|---|---|
| 12 | ※顧客コード1 | `N(10)` | right-aligned, zero-padded |
| 13 | ※顧客コード2 | `N(10)` | right-aligned, zero-padded |
| 12 | ※EDI情報 | `C(20)` | **when 項番15 識別表示 is `Y`** — left-aligned, space-padded |

The same twenty bytes are *either* two numeric customer codes *or* one text EDI
payload, selected by a different field. Sources giving `N` describe the ordinary
case; sources giving `C` describe the overlay; sources giving "N or C" describe
both. Nobody was wrong.

The standard states the same attributes elsewhere: 社員番号 and 所属コード are
`N(10)` (§4), and 預金口座振替's 顧客番号 is `N(20)` (§15) with no overlay.

**Implemented: `C`, knowingly departing from the standard.** The descriptor
schema declares one attribute per field and cannot express "N unless 識別表示
is Y". Of the two single-attribute readings:

- `N` decodes the ordinary case correctly and **fails on an EDI payload**, which
  is text and does not parse as a number.
- `C` decodes both — an EDI payload as itself, and a zero-padded numeric code as
  its digits, leading zeros preserved.

`C` is the reading that never loses data, which is what §0.6 asks for. See
[ADR-0015](adr/0015-customer-code-declared-as-text.md).

**What this now blocks.** Nothing evidentiary: the question is answered, and the
offsets were never in doubt. What keeps the affected formats at
`verified: false` is that their descriptors deliberately declare an attribute
the standard does not, and a `verified` flag should not be set on a layout that
knowingly differs from its sources — however defensible the difference.

**To close it.** Give the descriptor schema conditional fields, so 顧客コード1/2
can be declared `N` with a `C(20)` overlay predicated on 識別表示. That is
[OQ-8](OPEN_QUESTIONS.md)'s modelling gap, already scheduled for Epic 7 because
the ISO 20022 mapping needs to read the EDI payload anyway. When it lands, this
entry closes and the flags can be revisited.

---

## D-003 — How wide is the permitted character set for names?

**Found 2026-08-16, implementing the character-set validation for Epic 3.**

The sources agree on the base set — kana excluding ｦ and small kana, voicing marks, digits, and
the exclusion of the long vowel mark ｰ — and disagree about how many symbols a name field admits.

| Source | 店舗名 (bank/branch) | 口座名等 (party names) |
|---|---|---|
| 全国銀行協会 付録1 注1/注2 | 1 symbol: `-` | 4 symbols: `( ) - .` |
| PCA 全銀協使用可能文字 | hyphen only | `( ) - .` plus space |
| 大分銀行 口座振替ファイル | 8 symbols: `￥ ． （ ） ／ － 「 」`, and ｦ **permitted** | the same 8 |

The first two agree with each other and with the standard. The third states a single wider set for
every name field in 預金口座振替 — the set the standard reserves for EDI information — and permits
ｦ, which 付録1 excludes outside EDI.

**A second disagreement, in the standard itself.** The JBA's own 新旧対照表 shows 付録1 being
*revised*: the new text keeps「ヲと小文字を除く」as the general rule but adds an exception under
which, for 支店名・仕向店名・仕向支店名・被仕向支店名 and for 口座名・振込依頼人名・受取人名 in
総合振込 and four other businesses, only small kana are excluded — so ｦ becomes permitted in those
fields. Institution publications consulted still state the older, stricter rule.

**Implemented: the stricter reading.** `CharacterClass` excludes ｦ from every class except
`EDI_INFORMATION`, and gives 店舗名 one symbol and 口座名等 four. A validator exists to predict what
an institution will reject, and every institution publication consulted states the narrow rule —
including institutions whose own systems follow the revision. Accepting ｦ because the standard now
allows it would pass files that a bank on the older rule will refuse, which is the failure that
costs money.

**What this means for a user.** A finding against ｦ or against a symbol outside the narrow set may
be a false positive for your institution. It is never a false negative: nothing this library
accepts is rejected by the narrow rule.

**To settle it.** Confirm the revised 付録1 text against a current institution publication that
states the wider rule for 総合振込 specifically, rather than for 預金口座振替. If institutions have
adopted the revision, the classes gain a per-format switch and the narrow set becomes the
conservative default rather than the only reading.

---

## D-004 — Where the 支店番号 goes in `ClrSysMmbId`

**Status:** resolved 2026-08-20, in favour of the profile. The implementation was corrected.

**Sources**

| Source | Reading |
|---|---|
| ISO 20022 `ExternalClearingSystemIdentification1Code`, the registered definition of `JPZGN` | "Bank **Branch** code used in Japan" — a single identifier naming an office, which reads as bank code followed by branch code |
| Industry references describing `pain.001` for Japan | A seven-digit identifier after `JPZGN`, with no separator |
| 全銀ネット ZEDI 接続ガイダンス FB編, the request mapping (items 40, 43, 55, 60) | `ClrSysMmbId/MmbId` is 銀行番号 alone, `N(4)`. 支店番号 is `N(3)` and belongs in `FinInstnId/BrnchId/Id` |
| 三菱UFJ銀行 BizSTATION 総合振込（XML形式）レコードフォーマット, same item numbers | The same, independently — `MmbId` `N(4)`, 支店番号 under `BrnchId` |

**Analysis.** The first two readings are about the code set; the third is about the profile that uses
it. They are not equally authoritative for this question. `JPZGN`'s definition says what the code
names — an office rather than an institution — and that is a fact about the code set. It does not
say how a given profile lays that identifier out, and the profile that actually carries these files
splits it across two elements, giving each an explicit length.

Nothing here contradicts Q8's other half: `JPZGN` is the right value for `ClrSysId/Cd`, and the same
guidance fixes it as a constant (item 39). Only the seven-digit `MmbId` reading is refused.

**Implemented:** the seven-digit reading. `Agent.memberId()` returns 銀行番号 followed by 支店番号 and
`BrnchId` is deliberately unused, on the reasoning recorded in Q8 — which was a reasonable inference
from the code-set definition alone, and is contradicted by the profile.

**Resolved in favour of the profile**, and no longer on the strength of one document: a participating
bank publishes the same placement in its own customer-facing specification, which is the independent
corroboration R-I19 asks for. These six rows are the first in this mapping to be marked
`verified: true`.

**Corrected 2026-08-20.** `MmbId` carries 銀行番号 and `BrnchId/Id` carries 支店番号, on both agents.
The inverse leg reads the branch from its own element instead of slicing a member id, so the
"cannot tell where the bank ends" case is gone — and with it the `COERCED`/`CRITICAL` entry that
reported it. What replaced it is narrower and still worth saying: a document with no `BrnchId/Id`
leaves a mandatory Zengin field empty, which is reported `DROPPED`/`CRITICAL`.

The reader still accepts a seven-digit `MmbId` with no `BrnchId`, because that is what this library
wrote until now and its own output should not become unreadable. An explicit branch always wins.

## Reporting a discrepancy

If your institution's specification places a field differently from `docs/formats/`, please open an
issue with:

- the field name in Japanese and the record it belongs to,
- the offset and length each document gives,
- a citation for your source (institution, document title, URL or reference, date).

Both readings will be recorded here. The more conservative one — the one that fails rather than
guesses — gets implemented.
