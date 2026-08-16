# 0021 — The shared header date is `effectiveDate`, and each format keeps its own name

**Status:** Accepted
**Requirements:** R-D1, OQ-6
**Closes:** [OQ-6](../OPEN_QUESTIONS.md)

## Context

`HeaderRecord` — the interface every format's header implements — exposed
`valueDate()`, named after 総合振込's 振込指定日. OQ-6 asked whether that
generalises. Adding 預金口座振替 answered it: no.

| Format | Field | What the date is |
|---|---|---|
| 総合振込 | 振込指定日 | the day funds reach the payees |
| 給与振込 | 振込指定日 | the same |
| 預金口座振替 | **引落日** | the day the payers' accounts are **debited** |

A value date is when value reaches a beneficiary. On a 引落日 nothing reaches
anybody — money leaves a payer's account, and the collected total lands later.
Calling it a value date is not merely imprecise; it is wrong in the direction
that matters, in a format whose whole hazard is direction (see
[ADR-0020](0020-one-descriptor-for-type-code-91.md) and §13.1's warning about
producing payments the wrong way round).

Leaving it as `valueDate()` would mean the one interface method every header
shares tells a 預金口座振替 user something false about their file.

## Decision

Two names, for two different jobs.

**The interface method is `effectiveDate()`** — named for what the date does in
every format rather than for what one format calls it. The date on which the
instruction takes effect is true of both, and commits to neither direction.

**Each generated record also carries the name its own format uses.** The
generator emits an accessor named after the descriptor field, delegating to
`effectiveDate()`:

```java
SougouFurikomiHeader.valueDate()   // 振込指定日
KouzaFurikaeHeader.debitDate()     // 引落日
```

So code written against a concrete format reads in that format's own terms,
which is what R-D1 asks for — the model is format-shaped, not idealised — while
code written against the shared interface gets a name that is honest about
covering both.

This is the general shape for shared accessors, not a one-off: where formats
agree on a concept and disagree on its name, the interface takes a neutral name
and the concrete types keep theirs.

## Consequences

**What it costs.** A breaking rename before 1.0, and one extra generated method
per header. Callers using `header.valueDate()` through the interface change to
`effectiveDate()`; callers holding a `SougouFurikomiHeader` change nothing,
because `valueDate()` still exists there and still means 振込指定日.

**What it buys.** No format's header answers a question with another format's
vocabulary. And the pattern scales: 振込入金通知's 勘定日 in Epic 8 gets the same
treatment rather than another argument.

**What would make this wrong.** If a future format's header date were neither an
effective date nor expressible as one — a 作成日, say, which is when the file was
made rather than when anything happens. That is a different concept and wants a
different accessor, not a broader name for this one. Epic 8's 200-byte headers
carry exactly that field, so this is a question with a date on it.
