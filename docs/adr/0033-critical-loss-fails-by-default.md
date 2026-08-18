# 0033 — A critical loss stops the conversion by default

**Status:** Accepted
**Requirements:** R-I14, R-I16

## Context

R-I14 says conversion always returns output *and* a loss report, and that no API returns only the
artefact. That makes the report impossible to **miss**: it is in the return type, and a test asserts
by reflection that no method returning a bare `ZediFile` or `ZenginFile` can be added later.

R-I16 says `CRITICAL` means the payment could mean something else or the funds could misroute, and
that `CRITICAL` is *configurable* to hard-fail via `MappingContext.failOnSeverity`.

Configurable implies a default, and the requirement does not say which. The obvious reading is that
failing is opt-in — the report is returned, and a caller who cares reads it.

## Decision

**`failOnSeverity` defaults to `CRITICAL`.** A conversion whose loss could misroute money throws
`MappingFailedException`, with the full report attached, rather than returning quietly.

`MappingContext.Builder.acceptAnyLoss()` is the way out. It is named so that it reads as what it is
at the call site: the report is unchanged and says everything it said before; only the refusal goes
away.

`dryRun` and `roundTrip` never refuse, whatever the threshold says. Their purpose is to show the
loss, and stopping at the first critical entry would hide the rest of the answer.

## Consequences

**Cost.** A caller who wants best-effort conversion has to say so. That is one line, and it is a line
a reviewer can see.

There is a real risk of the wrong kind of habit: somebody hits a refusal, adds `acceptAnyLoss()` to
make it go away, and stops reading the report. The mitigation is the name and nothing else — the
alternative, refusing to offer a way out, would make the library unusable for the callers who
genuinely have to convert imperfect files.

**Benefit.** The failure mode this whole module exists to prevent is a conversion that silently
changes what a payment means. R-I14 makes that visible; this makes it stop. Returning a
misroutable payment and hoping somebody reads a report is not good enough for a file that moves
money — and "somebody will read the report" is exactly the assumption that fails on the Tuesday it
matters.

It is also consistent with how the rest of the library behaves at a boundary where a wrong answer
costs money: reading gates on `verified` ([ADR-0019](0019-building-gates-on-verified.md)), writing
gates on `verified`, transliteration refuses a name it cannot represent rather than approximating
it ([ADR-0028](0028-the-specifications-kana-mappings-are-wrong.md)). The conservative reading is the
house style, and §0.6 asks for it.

**What would make this wrong.** Evidence that in practice most conversions carry a critical entry
for a reason nobody can act on — a currency field that is always populated and always JPY, say —
which would make the default noise rather than protection. Nothing seen so far suggests that; the
critical entries produced by a conformant file are none.
