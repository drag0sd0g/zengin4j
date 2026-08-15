# 0015 — 顧客コード1/2 are declared `C`, against the standard's `N`

**Status:** Accepted
**Requirements:** R-0.2, R-C18, P5, R-B10
**Supersedes nothing. Blocks:** `verified: true` on `sougou-furikomi` until D-002 is settled.

## Context

Six published sources describe the 総合振込 data record. All six agree on every offset and every
length. They do not agree on the attribute of fields 12 and 13:

- the JBA standard, 愛知銀行 and 十八親和銀行 give `N(10)`;
- 群馬銀行 and 兵庫県信用組合 give `C(10)`;
- 三井住友銀行 gives `N` and notes that the attribute changes with the identification code.

All six also document the same overlay: when 識別表示 is `Y`, those twenty bytes are one `C(20)`
金融EDI情報 field. So the bytes really are `N` in one mode and `C` in the other, and SMBC's
conditional reading reconciles everyone.

Meanwhile the descriptor already said `C(10)`, transcribed from the build specification's draft —
which happens to match the minority reading, and whose own worked example puts `INV20260001` in
these fields, content the `N` attribute forbids.

## Decision

Keep `C(10)`, and record D-002 rather than closing it.

The choice does not affect reading. A zero-padded numeric value decodes identically under both
attributes: trailing-space stripping cannot touch `0000012345`. What the attribute governs is
padding on write (`N` pads left with zeros, `C` pads right with spaces) and character-set
validation.

Under `N`, both of those reject legitimate data — every alphanumeric customer reference, and every
EDI payload. Under `C`, the cost is a validation finding this library will not raise on a field
whose permitted content is itself in dispute. Rejecting valid payments is worse than failing to
flag a disputed one.

The format stays `verified: false`. R-0.2 does not say "unless the disagreement is explicable".

## Consequences

**Cost.** The library disagrees with the standards body on a field attribute, which needs
explaining every time someone notices — hence D-002 and the note on the field itself. And a
descriptor that is corroborated in every offset is still gated behind
`allowUnverifiedFormats(true)`, which will look excessive to anyone who has read the sources.

**Benefit.** No parsed output changed when this was investigated, so no version bump was triggered
(R-B10) — the research confirmed the existing behaviour rather than correcting it. Epic 2's writer
and Epic 4's rules now have the analysis they need in advance of needing it.

**What settles it.** A source documenting the field under both values of 識別表示, or 全銀ネット's
ZEDI material, which must define the EDI payload precisely. If the conditional attribute is
confirmed, this stops being a discrepancy and becomes a schema gap — see OQ-8.
