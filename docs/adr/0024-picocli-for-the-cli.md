# 0024 — picocli parses the command line

**Status:** Accepted
**Requirements:** R-M1, R-M5, §27

## Context

This project has twice chosen to hand-write rather than take a dependency, and
once regretted it. ADR-0001 hand-wrote a YAML reader; ADR-0016 undid that as a
mistake. ADR-0022 hand-wrote a JSON and SARIF *writer* and argued the case was
different because emitting is not parsing.

The CLI needs argument parsing: five subcommands, three dozen options, enum
conversion, arity, usage text, and a usage error that is distinguishable from a
crash. So the question comes round again, and this time in the direction the
project has been most reluctant to go.

## Decision

Take picocli, as a runtime dependency of `zengin4j-cli` and of nothing else.

**R-M1 constrains `core`, not this.** The requirement is that *the library* is
adoptable in an environment with a dependency review process. `zengin4j-cli` is
an application: it is not published to Maven Central, nothing declares a
dependency on it, and there is no consumer to inherit anything. Applying core's
constraint here would be cargo-culting the letter of a rule whose reason does
not reach.

**And this really is parsing.** ADR-0022's argument was that emitting a
well-specified format has a small correctness surface, while parsing input
somebody else wrote does not. A command line is input somebody else wrote. The
edge cases — `--opt=value` versus `--opt value`, clustered short options,
`--` terminating options, negatable flags, a subcommand that shares an option
name with its parent — are exactly the "somebody else's input" class that
ADR-0016 found hand-rolling loses money on.

**The output quality is the point.** §27 calls `inspect --annotate` the primary
diagnostic tool and says to invest beyond the feature list. Usage text,
`${COMPLETION-CANDIDATES}`, suggestion-on-typo and consistent option help across
five commands are what a person meets before they meet any of the analysis, and
hand-rolling them produces a worse tool for no architectural gain.

**It is one dependency with no transitive dependencies.** picocli is a single
jar that requires nothing. The shaded CLI jar is under a megabyte.

## Consequences

- `zengin4j-cli` has a runtime dependency; every other module still has none,
  and the ArchUnit rule and `checkPomHasNoDependencies` still enforce that.
- The version catalogue's header comment had to stop claiming that nothing in
  it is a runtime dependency. It now says exactly which one is, and where.
- picocli reflects over the command classes, so `module-info.java` opens the two
  command packages to it, and a native image needs reflection configuration.
  `picocli-codegen` generates that at compile time rather than by hand, so it
  cannot go stale when an option is renamed — see ADR-0026.
- Commands are `Callable<Integer>` and never call `System.exit`, so
  `Zengin.run(args, out, err)` drives the real parser and real commands in a
  test without spawning a process.

## Alternatives

**Hand-write it.** Rejected on ADR-0016's own evidence. The parts that look easy
(splitting `--key=value`) are easy; the parts that decide whether the tool feels
finished are not, and they are the parts a hand-rolled parser skips.

**`java.util.spi.ToolProvider` plus manual parsing.** Same objection, plus no
usage text.

**Apache Commons CLI.** Older, no subcommand model, no annotation-driven help,
and it would need the same reflection configuration story without generating it.
