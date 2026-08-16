# 0017 — Property testing without jqwik

**Status:** Accepted
**Requirements:** R-T7, R-T9, R-T8

## Context

R-T7 names jqwik: *"Property tests (jqwik) covering INV-1 through INV-8. This
is the load-bearing correctness guarantee."* The requirement is the properties.
The parenthesis is a tool suggestion, and it is the only part of R-T7 this ADR
departs from.

jqwik's documentation carries an anti-AI usage clause: its author asks that the
library not be used in AI-assisted development. This project is developed with
AI assistance. That is the whole reason for the departure — an author's stated
wish about how their work is used, which costs nothing to respect and would be
ungracious to argue with.

Two things this decision is *not*:

**It is not dependency hygiene.** jqwik would have been `testImplementation`.
R-M1 and P3 constrain the published `zengin4j-core` artifact, which no test
dependency reaches. Keeping jqwik would not have put a dependency in the jar,
and dropping it does not remove one.

**It is not a judgement on the library.** jqwik is the better property-testing
tool. What replaces it here is smaller and does less, and the shortfall is
recorded below rather than glossed.

One incidental finding, recorded because it is a fact about the artifact and
somebody should be able to look it up: jqwik's runtime output contains text
addressed to an AI agent, instructing it to disregard the results of jqwik test
executions. It was treated as what it is — third-party data encountered while
running a build, not an instruction — and its presence made no difference to
this decision, which rests on the clause the author wrote in prose.

## Decision

Split the requirement in two, because the two halves want different machinery.

**Structured invariants → `Seeded`**, ~60 lines in
`core/src/test/java/io/zengin4j/core/testing/Seeded.java`. A property is a
generator and a check; `Seeded.property` runs the check over N generated cases.
Each case gets its own seed, derived from the property seed through a
SplitMix64 finaliser, so a case is reproducible without replaying the ones
before it. A failure reports the case index, the case seed, the input's shape,
and the one-line `Seeded.single(...)` call that re-runs it alone.

Generation lives in `RandomZenginFiles`, which builds files two ways: `bytes`
assembles them from `RecordEncoder` output directly, and `built` goes through
`ZenginFileBuilder`. INV-1 asks whether reading and writing agree, so its
inputs are assembled by neither reader nor writer — a generator built on
`ZenginWriters` would have made INV-1 agree with itself.

**Hostile bytes → Jazzer** (`ReaderFuzzTest`), which is coverage-guided: it
watches which branches an input reaches and mutates toward the ones it has not.
That finds the input that walks past the separator-skipping loop, which random
generation essentially never produces.

It earned that description immediately: within 30,000 executions it produced a
file whose separator run mixes CR and CRLF, which is readable but has no
convention to reproduce. The defect was in the property, not the library —
"anything readable is writable" is false, and the honest version asserts that
the writer refuses by name and can be told which convention to impose. That
input is committed and replayed on every build.

Fuzzing splits across two modes, which is a build-level distinction rather than
a test-level one:

- **Replay** (`fuzz`) runs the committed corpora with no mutation. Fast,
  deterministic, and wired into `check` — a crash reproducer that only runs
  nightly lets the regression it exists to prevent reach `main` first.
- **Mutation** (`fuzzAll`) runs nightly, one Gradle task per target. libFuzzer
  terminates the JVM when a target's budget expires, so two mutating targets in
  one JVM means the second never runs — and the build fails on a missing
  results file rather than on anything naming the cause. Separate tasks make
  the broken combination inexpressible, and a test fails the build if a
  `@FuzzTest` has no task.

Jazzer is also test-scoped, and its JUnit integration switches on the
`JAZZER_FUZZ` environment variable rather than a system property — the mutating
tasks set it.

## Consequences

**What it costs: shrinking.** jqwik shrinks a failing case toward a minimal
one, which is the feature people adopt it for. `Seeded` does not shrink. When a
property fails you get the case that failed, not the smallest case that would.

Two things blunt that. Generated files are small by construction — one or two
batches, zero to three payments each — so a failing case is rarely large enough
to need reducing. And the reported case seed makes reduction a manual loop that
actually terminates: replay the one case, narrow the generator, replay again.

If a failure ever does arrive too large to read, that is the signal to
reconsider — a shrinker is perhaps eighty lines for this generator shape, and
writing one is a smaller job than acquiring one.

**What it costs: nothing else, so far.** The properties themselves — INV-1,
INV-2, INV-3, INV-6, INV-8, idempotence, determinism — are expressed as
directly as they would have been with an `@Property` annotation. Test count and
coverage did not move when jqwik came out.

**What it buys.** Failure output that names the exact replay command; one fewer
framework whose generator DSL a contributor has to learn; and the seeds are
this project's, so a golden corpus generated from one (R-T8) is reproducible
across versions of nothing but the JDK.

**Verified, not assumed.** The properties were confirmed to bite before this was
written: breaking `ZenginWriters`' trailing-separator handling on purpose failed
INV-1 and INV-2, each reporting a case seed that reproduced the failure alone.
A property that has never failed is a property that has never been tested.

**What would make this wrong.** A property whose inputs are genuinely large or
combinatorially deep — a validation-rule matrix in Epic 4, say — where reading
an unshrunk counterexample stops being practical. The answer then is a shrinker
behind `Seeded`'s existing signature, not a different framework: the call sites
do not need to know.
