# 0036 — The baseline moves from Java 21 to Java 25

**Status:** Accepted
**Requirements:** R-M7, R-B2 — and this record diverges from R-M7
**Supersedes:** [ADR-0002](0002-java-21-baseline-without-a-toolchain.md), in its choice of version

## Context

R-M7 says *"Java 21 baseline throughout"*, and [ADR-0002](0002-java-21-baseline-without-a-toolchain.md)
implemented it as `options.release = 21` with a CI matrix covering 21 and 25.

Java 25 is an LTS release, and it carries several features this codebase has concrete uses for:
compact source files and instance `main` methods for the single-file programs in `examples/`, stream
gatherers for fixed-width chunking, Markdown documentation comments, unnamed variables, and flexible
constructor bodies for the exception hierarchy, which currently computes its messages in static
helpers so they can be passed to `super(...)` first.

None of those is load-bearing. Every one is a readability improvement over code that already works.

## Decision

`options.release = 25`. The CI matrix drops the 21 leg and runs 25 across all three operating
systems, which R-T18 still requires.

**This diverges from R-M7 deliberately.** The requirement is not wrong the way R-K2 was wrong — it
was right when it was written, and a baseline is a decision with an expiry date rather than a fact
about the format. It is recorded here rather than quietly done because the consequence lands on
somebody who is not in the room.

## Consequences

**The cost, stated plainly.** Anyone on Java 21 can no longer use this library. Java 21 is the LTS
that most enterprises are actually running in 2026; Java 25 is newer and adoption lags a release
behind in exactly the conservative, dependency-reviewing environments this library was built for
(R-M1's whole argument). This is the largest adoption cost the project has taken on so far, and it
buys no capability — only nicer code.

If that turns out to be the wrong trade, the way back is cheap: `options.release = 21`, restore the
matrix leg, and revert whatever post-21 syntax has been adopted. That is a real escape hatch only
while the post-21 features stay shallow, which is a reason to adopt them for readability and not to
build architecture on them.

**What the mechanism keeps.** ADR-0002's actual decision — `options.release`, not a Gradle toolchain
— is unchanged and still right. A contributor needs a JDK 25 or newer and nothing else; no toolchain
downloads a second JDK behind their back.

**What is lost from the matrix.** Running 21 and 25 side by side used to demonstrate that
`--release 21` really did produce bytecode both accepted. With one version there is nothing to
compare, so the matrix now proves portability across operating systems only. That is the part R-T18
cares about, and it is the part that has actually caught defects — the Windows legs, twice.

**What would make this wrong.** A consumer who cannot move off 21 and needs the library. If one
appears, this decision should be revisited rather than worked around with a backport branch.
