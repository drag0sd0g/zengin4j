# 0019 — Building a file gates on `verified`; writing one does not

**Status:** Accepted
**Requirements:** R-0.1, R-B10
**Closes:** [OQ-10](../OPEN_QUESTIONS.md)

## Context

Reading a file whose descriptor is `verified: false` throws
`UnverifiedFormatException` unless the caller sets
`ReaderOptions.allowUnverifiedFormats(true)`. That gate was specified as issue
1.9, when the library could only read.

Epic 2 added a write path with no equivalent. `ZenginFileBuilder.forFormat`
took a descriptor and used it, verified or not.

That was not a specification violation — R-0.1 governs what the flag means and
what evidence sets it, and the enforcement mechanism is scoped to reading. It
was found while auditing whether `DISCLAIMER.md` needed write-side wording, and
the disclaimer turned out to describe the read gate in a way that invited the
reader to assume it worked in both directions.

**The asymmetry runs the wrong way.** A wrong byte offset when reading produces
wrong data inside the caller's own system, where their reconciliation may catch
it. A wrong byte offset when writing produces a payment instruction a bank will
act on. If the opt-in exists so that trusting a provisional layout is recorded
where a reviewer can see it, that argument is stronger for output than for
input.

## Decision

`ZenginFileBuilder.build()` throws `UnverifiedFormatException` unless
`allowUnverifiedFormats(true)` was set on the builder.

**On the builder, not on `WriterOptions`.** The first proposal was a
`WriterOptions` flag checked in `ZenginWriters`, for symmetry with
`ReaderOptions`. Two facts moved it:

- `ZenginFile` carries a `FormatId`, not a `FormatDescriptor`. A writer-side
  check would have to rediscover the descriptor through a registry in order to
  read a flag the builder already held — or the model would have to grow a
  field to carry it.
- The risk is not evenly spread. Building places caller-supplied values at
  descriptor-defined offsets, which is precisely the step a provisional layout
  gets wrong. Writing a file that was just *read* reproduces bytes that already
  existed, byte for byte; the offsets are not consulted. Gating the writer would
  put friction on the one path that introduces no new risk, and would mean a
  caller who legitimately read with `allowUnverifiedFormats(true)` had to say so
  twice to write the same bytes back.

Both producers of a `ZenginFile` are therefore gated — the reader by
`ReaderOptions`, the builder by its own flag — which is the whole surface.

**The exception names the remedy that applies.** `UnverifiedFormatException`
carries an `Operation` (`READING` or `BUILDING`) and reports the corresponding
opt-in. A diagnostic that prescribes the wrong fix costs more than one that says
nothing: it sends the reader to an API that will not help, and it makes them
doubt the message that would have.

The build-side message is also blunter about the consequence, deliberately.

## Consequences

**What it costs.** A breaking change for anyone who built files against an
earlier snapshot — but the alternative is breaking them after 1.0, which R-B10
forbids without a major bump, or never. Doing it now costs one line per call
site. Every fixture in this repository took that line, and `Fixtures.builder`
carries it for the test suite so the single test that asserts the gate *fires*
is the only place the raw entry point appears.

**What it buys.** The two ways to produce a Zengin file from a provisional
layout now both require the caller to write down that they know it is
provisional. Neither can happen by omission.

**What would make this wrong.** If descriptors reach `verified: true` and the
flag stops being a live concern, the gate becomes ceremony. That is a good
problem and a long way off: every descriptor shipped today is `verified: false`,
and the 総合振込 layout is held there by a single unresolved field attribute
([D-002](../DISCREPANCIES.md)) despite six corroborating sources.
