# 0020 — One descriptor for 種別コード 91

**Status:** Accepted
**Requirements:** §13.1, R-X1
**Closes:** [OQ-1](../OPEN_QUESTIONS.md)
**Relates to:** [ADR-0007](0007-ambiguous-type-code-fails.md)

## Context

種別コード `91` is used by two businesses: 預金口座振替（依頼明細）, the
instruction file an originator sends, and 預金口座振替（処理結果明細）, the result
file the bank returns. ADR-0007 anticipated this as an ambiguity to be reported,
and OQ-1 asked whether to register one descriptor or two.

The premise turned out to be wrong, and the correction is the whole decision.
**The two have the same layout.** Three sources say so directly:

- The JBA standard describes the result format as identical to the instruction
  format except for listed items — and those items are *values*, not positions.
- 大分銀行 gives both record formats field by field with byte positions; they
  match.
- 北洋システム開発 states it in as many words: 振替結果コード is
  「請求時は 0（ゼロ）／返却時は銀行で振替結果コードをセット」, and each of the
  trailer's four result totals is 「請求時は、すべて 0（ゼロ）」.

So the difference between an instruction file and a result file is that certain
fields are zero in one and populated in the other. Nothing in the bytes says
which you are holding, other than reading those fields.

## Decision

**One descriptor**, `kouza-furikae`, covering both directions.

Two descriptors would create an ambiguity where none exists. Every `91` file
would match both, `byTypeCode` would return two candidates, and ADR-0007's
`AmbiguousFormatException` would fire on every read — forcing the caller to
declare which one they hold in order to parse a file that parses identically
either way. That is a hard failure invented to describe a distinction that makes
no difference to parsing.

The instruction/result distinction is **semantic, and belongs to the caller or
to the validation layer**: a file whose 振替結果コード are all zero is an
instruction; one carrying results is a return. That is a question about content,
which Epic 4 is for.

**ADR-0007 is not superseded.** Its mechanism —`byTypeCode` returning a list, and
more than one match being an error rather than a guess — remains right, and
still applies: R-X1 lets a consumer register their own descriptors at runtime,
and two claiming one type code is a real mistake worth naming. What changes is
that no *bundled* format triggers it. A guard that never fires against shipped
data, and would fire against a consumer's mistake, is doing its job.

The same shape is expected in Epic 8: 振込入金通知 has フォーマットA and
フォーマットB sharing 種別コード `01`. That case is **not** resolved by this ADR,
because those two differ in whether 12-digit amount fields are present — a
difference in positions, not values. Two layouts under one type code is exactly
what ADR-0007 exists for.

## Consequences

**What it costs.** A caller who wants the instruction/result distinction at the
type level does not get it from the format id. They read `transferResult`, or
wait for Epic 4 to say it in a finding.

**What it buys.** Every `91` file parses without the caller having to declare
anything. One layout is described once, so the two cannot drift. And the
descriptor documents the distinction where it actually lives — on the fields
that carry it, each noting that it is zero on request and set by the bank on
return.

**What would make this wrong.** A source showing the two layouts differing in
any position. That would not be an amendment; it would mean this descriptor
describes one of them and misreads the other, and the format would need
splitting with ADR-0007's machinery doing exactly what it was built for.
