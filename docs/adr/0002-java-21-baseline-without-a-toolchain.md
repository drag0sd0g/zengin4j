# 0002 — Java 21 baseline via `--release`, not a toolchain

**Status:** ~~Accepted~~ **Superseded by [ADR-0036](0036-java-25-baseline.md)** — the
baseline moved to 25 in August 2026. The *mechanism* this record chose survives that
change: `options.release` rather than a toolchain, for the reasons below.
**Requirements:** R-M7, R-B1, R-B2

## Context

R-M7 sets a Java 21 baseline. Gradle offers two ways to hold it: a Java toolchain, which pins an
exact JDK and downloads it if absent, or `options.release = 21`, which compiles against the Java 21
API with whichever JDK ≥ 21 is running the build.

A toolchain is the more rigorous choice — it guarantees the same compiler everywhere. It also means
that a fresh clone on a machine with only JDK 25 downloads a second JDK before it can compile, and
that JDKs installed by common version managers are frequently not auto-detected, so the download
happens even when a suitable JDK is already on disk.

## Decision

`options.release = 21` in the root build, with no toolchain block. The CI matrix runs JDK 21 and
JDK 25 on Linux, macOS and Windows (R-B2), which is what actually demonstrates the baseline holds.

## Consequences

**Cost.** A local build uses whatever JDK runs Gradle. If that JDK's compiler ever differed
materially from JDK 21's for `--release 21` output, CI would catch it rather than the developer.

**Benefit.** `git clone && ./gradlew build` works with no downloads beyond dependencies, on any
JDK 21 or newer, which is the property that makes a repository pleasant to contribute to.

**What would make this wrong.** Reproducible-build verification (R-B5) eventually wants byte-identical
artifacts from a named compiler. When release engineering arrives, pin a toolchain for the publish
path specifically, and leave the development path as it is.
