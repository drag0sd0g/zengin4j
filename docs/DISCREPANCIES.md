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

## D-002 — Attribute of 顧客コード1 and 顧客コード2

**Status:** open. This is what holds `sougou-furikomi` at `verified: false` (R-0.2).

**Sources**

| Source | Reading |
|---|---|
| 全国銀行協会, 標準通信プロトコル適用業務およびレコード・フォーマット, 令和元年12月 §8 | `N(10)` each |
| 愛知銀行 | `N(10)` each |
| 十八親和銀行 | `N(10)` each ("お客様番号1/2") |
| 三井住友銀行 | `N`, with a note that the attribute becomes `C` according to the identification code |
| 群馬銀行 | `C(10)` each ("顧客番号1/2") |
| 兵庫県信用組合 | カナ (i.e. `C`) 10 each |

Full citations in [SOURCES.md](SOURCES.md). Lengths and offsets are not in dispute: ten bytes each,
at offsets 91 and 101, in every source.

**Analysis.** The disagreement is real but explicable. All six sources also document an overlay: when
識別表示 (field 15) is `Y`, fields 12 and 13 together are a single `C(20)` 金融EDI情報 field rather
than two customer codes. So the same twenty bytes are `N` in one mode and `C` in the other, and the
institutions that document them as `C` appear to be describing what those bytes carry in practice —
customer references are routinely alphanumeric. SMBC states the conditional attribute explicitly,
which is the reading that reconciles the other five.

The build specification's own worked example (§20.1) illustrates the tension without commenting on
it: it places `INV20260001` across these two fields, which the `N` attribute would forbid.

**Implemented.** `C(10)` for both — the conservative reading, and unchanged from before this
question was investigated, so no parsed output moved (R-B10).

`C` accepts digits and letters alike. Reading is unaffected either way: a zero-padded numeric value
decodes identically under both attributes, because trailing-space stripping cannot touch it. The
choice governs two things that are not yet exercised:

- **padding on write** — `N` pads left with zeros, `C` pads right with spaces (Epic 2);
- **character-set validation** — declaring `N` would flag every alphanumeric customer code, and
  every EDI payload, as a violation (Epic 4).

Both failure modes of choosing `N` are false rejections of legitimate data. The failure mode of
choosing `C` is a missed validation finding on a field whose permitted content is itself disputed.

**To settle it.** Confirm against a further institution guide that documents the field under both
values of 識別表示, or against 全銀ネット's ZEDI documentation, which has to define the EDI payload
precisely. If the conditional attribute is confirmed, the descriptor schema needs conditional
fields — see OQ-8 — and this stops being a discrepancy and becomes a modelling gap.

---

## Reporting a discrepancy

If your institution's specification places a field differently from `docs/formats/`, please open an
issue with:

- the field name in Japanese and the record it belongs to,
- the offset and length each document gives,
- a citation for your source (institution, document title, URL or reference, date).

Both readings will be recorded here. The more conservative one — the one that fails rather than
guesses — gets implemented.
