# 0028 — Two of the specification's kana mappings are wrong

**Status:** Accepted
**Requirements:** R-K2, R-K7, R-C13

## Context

R-K2 of the build specification says, in as many words:

> Long vowel `ー` → `ｰ`. Small kana `ャ` → `ｬ`.

Both produce characters that **this library's own validator rejects**.

Epic 3 built `CharacterClass` from the field rules in 全国銀行協会 付録1. The
prolonged sound mark `ｰ` (`0xB0`) and every small kana (`0xA7`–`0xAF`) are
excluded from every field class — the run of permitted kana starts at `0xB1`
precisely because they sit below it. Validation rule `V-202` reports either of
them, with a message that names the fix: *the long vowel mark ｰ is never
permitted — write a long vowel as `-` (0x2D)*.

So a transliterator following R-K2 would emit text that the reader in the same
jar refuses. Epic 4 already caught this project's own fixtures doing it:
`ﾃｽﾄｼｮｳｼﾞ` had to become `ﾃｽﾄｼﾖｳｼﾞ`.

## Decision

Follow the research, not the specification.

| Input | R-K2 says | Implemented | Severity |
|---|---|---|---|
| `ー` | `ｰ` | `-` | `MATERIAL` |
| `ャ` `ュ` `ョ` `ッ` `ァ`… | `ｬ` `ｭ` `ｮ`… | `ヤ` `ユ` `ヨ` `ツ` `ア`… | `MATERIAL` |

Both are recorded as `MATERIAL` rather than `INFORMATIONAL`, because キャノン
becoming キヤノン is something a human reconciling a payment against an invoice
will notice. The money still arrives; the paperwork reads differently.

**The specification predates the source research.** It was written before Epic 3
read 付録1 and before Epic 4 built the rules that enforce it. Where a document
written up front disagrees with evidence gathered later, the evidence wins —
and the disagreement is recorded rather than quietly resolved.

## A consequence worth stating on its own

`-` is permitted in `BANK_NAME`, `PARTY_NAME` and `EDI_INFORMATION`. It is
**not** permitted in `PAYROLL_NAME`, which admits no symbols at all.

So a long vowel has no legal half-width form in a payroll name. ヨーコ can be
written into a 総合振込 file and cannot be written into a 給与振込 one, and no
choice of mapping changes that — the character simply has nowhere to go.

This is why transliteration takes a `CharacterClass` rather than a string alone,
and why `UnmappableCharacterPolicy` exists: something has to give, and the
caller is better placed than a codec to decide whether it is the name or the
payment. The default refuses.

## R-K7 has the same shape

The specification also treats the width correspondence as mechanical, and for
one family of characters it is not. `ヷ` `ヺ` — the archaic VA and VO — decompose
under Unicode to `ﾜ` + `ﾞ` and `ｦ` + `ﾞ`, and neither `ﾜ` nor `ｦ` has a voiced
form the standard recognises. R-K7 lists the kana that may carry a mark, and
those are not among them.

So the derived table faithfully contains mappings that must never be written.
The engine refuses them at a dedicated pass rather than the table pretending
they do not exist, and `VoicingMarks` — the ranges R-K7 names — lives in `core`
so that the transliterator and validation rule `V-206` share one copy of the
fact rather than two that can drift.

## Consequences

- `KanaTableTest` asserts that every substituted character is one **no** field
  class permits, so if a class ever widened, the substitution would be revisited
  rather than left in place out of habit.
- `RecordEncoder` refuses to write a stranded mark under its default policy, so
  the library cannot produce a file its own `V-206` reports.
- The four decompositions that strand a mark are named in a test. A fifth would
  fail the build rather than reach a file.
- The substitutions are declared as data in `kana-substitutions.yaml`, so the
  judgement is reviewable without reading code — see ADR-0030.

## Alternatives

**Implement R-K2 as written.** Produces files this library rejects. Rejected on
sight once the conflict was noticed.

**Change `CharacterClass` to permit `ｰ` and small kana.** Would make R-K2 correct
and the field rules wrong. The rules are cited to 付録1 and corroborated; R-K2
cites nothing.

**Ask the caller which mapping to use.** Configuration in place of a decision.
There is a right answer here and the library should know it.
