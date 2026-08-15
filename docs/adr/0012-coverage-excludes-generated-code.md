# 0012 — The coverage gate excludes generated code

**Status:** Accepted
**Requirements:** R-T16, R-M8

## Context

R-T16 requires ≥ 90% line and ≥ 85% branch coverage on `core`, enforced in CI. The generated record
classes are part of `core` and contribute roughly sixty accessors, four `equals`/`hashCode` pairs
and four `toString` methods — none of it hand-written.

Including them measures the generator's output rather than the code the gate exists to protect, and
it does so in a way that makes the number easy to move for the wrong reason: adding a format would
raise or lower coverage depending on how many of its accessors a test happened to call.

## Decision

Exclude `io.zengin4j.core.model.generated.**` from the JaCoCo counters, and cover the generated code
by asserting what actually matters about it:

- every generated offset constant equals the descriptor's computed offset,
- every field decodes to the value the fixture encoded,
- equality, masking in `toString`, and the role accessors behave as designed,
- and `checkGeneratedSources` fails the build if the committed output has drifted at all.

JaCoCo would in any case skip these classes automatically: they carry `@Generated`, whose retention
is `CLASS` precisely so that coverage tooling can see it. The explicit exclusion in the build script
states the intent rather than relying on that behaviour.

## Consequences

**Cost.** A defect in the *generator* that produced uncalled code would not show up as a coverage
drop. It would show up as a failing offset assertion, which is a better signal anyway.

**Benefit.** The gate measures hand-written code, which is where a coverage floor has meaning. With
generated code excluded, `core` currently sits at 95.8% line and 89.3% branch — comfortably above
the floor, on code a human wrote.
