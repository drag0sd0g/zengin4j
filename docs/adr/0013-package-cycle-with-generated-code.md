# 0013 — One package cycle is accepted, between codec and generated code

**Status:** Accepted
**Requirements:** R-M5, §8

## Context

`RecordView.materialize()` must produce the format-shaped generated type where one exists. The
generated factory must decode fields, which means reading them through the view. So
`io.zengin4j.core.codec` refers to `io.zengin4j.core.model.generated`, and the generated code refers
back to `codec`.

Every arrangement that removes this cycle moves it somewhere else:

- Route the lookup through `FormatDescriptor`, and `format` depends on `model.generated`, which
  depends on `codec`, which depends on `format`.
- Give the generated code its own decoding primitives, and the same digit loop exists twice, with
  one copy outside the reach of the tests that matter.
- Inject the factories through `ReaderOptions`, and the default value has to name them anyway.

## Decision

Accept the cycle between `codec` and `model.generated`. Keep the boundaries that carry meaning, and
enforce those:

| Rule | Enforced by |
|---|---|
| `core` depends on nothing but the JDK | ArchUnit |
| `core` depends on no other zengin4j module | ArchUnit |
| `model` does not depend on `codec` | ArchUnit |
| `format` depends on neither `model` nor `codec` | ArchUnit |

`model` stays clean because the descriptor-driven fallback records are passive carriers: the codec
decodes the values and hands them over. Only the *generated* package participates in the cycle.

There is deliberately no "no package cycles" rule.

## Consequences

**Cost.** A static analyser configured with a blanket cycle check will flag this pair. The answer is
this document, not a redesign.

**Benefit.** One decoding implementation, tested once; a `materialize()` that needs no argument; and
the boundaries that actually protect the architecture — module direction, zero dependencies, model
independence — enforced rather than assumed.
