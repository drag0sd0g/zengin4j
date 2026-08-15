# 0001 — A hand-written YAML subset reader in `core`

**Status:** ~~Accepted~~ **Superseded by [ADR-0016](0016-descriptors-compiled-at-build-time.md)**
**Requirements:** R-M1, P3, R-F6, R-X1

> **Superseded.** The conflict described below is real, but this was the wrong
> resolution of it. The descriptors are now read at build time and compiled
> into `core` as Java, so there is no parser in `core` at all — which keeps the
> zero-dependency property this ADR was protecting while deleting the 1,350
> lines it cost. The reasoning is in ADR-0016; the passage below is kept
> because the requirement conflict it describes is still the thing any future
> design has to answer.

## Context

Two requirements pull in opposite directions.

`zengin4j-core` must have **zero runtime dependencies** (R-M1, P3). Not a JSON library, not a
collections library, not a logging facade. This is stated as the property that makes the library
adoptable in environments with a dependency review process, and it is the one property that a single
convenient `implementation(...)` line destroys permanently.

Format descriptors must be **loadable at runtime**. §13 shows the YAML feeding a `FormatRegistry`
that is "loaded at runtime", and R-F6/R-X1 require that a consumer can register an
institution-specific variant from their own YAML without forking the library.

A YAML file that must be read at runtime, by a module that may not depend on a YAML parser.

## Decision

Write one. `io.zengin4j.core.format.yaml` contains a strict reader for the subset the descriptor
schema uses: block mappings, block sequences, flow mappings, flow sequences, plain scalars,
single- and double-quoted scalars with the usual escapes, and `#` comments.

Everything outside that subset is **rejected by name, with the line number**: tabs in indentation,
anchors, aliases, explicit tags, block scalars, directives, multiple documents, duplicate keys. The
descriptor reader on top of it rejects unknown keys, out-of-order sequence numbers, and constants
that contradict the record's discriminator.

The package is not exported from the module. It is an implementation detail, not a YAML library.

## Consequences

**Cost.** Roughly 400 lines of parser to maintain and test, and a subset that will surprise anyone
who assumes full YAML. A descriptor using an anchor to share a repeated field block would be
rejected rather than expanded.

**Benefit.** `core` requires nothing but `java.base`, enforced by an ArchUnit rule that fails the
build. The reader is also strict in ways a general-purpose parser is not: a YAML 1.1 `verified: yes`
is an error here rather than a silently-true boolean, and a misspelled key is an error rather than a
field that quietly does nothing.

**What would make this wrong.** If descriptors grew to need anchors, merge keys or multi-document
files, the subset would stop being a subset and start being a liability. The answer then is to move
descriptor loading out of `core` into a module that may take a dependency — not to grow this parser
into a YAML implementation.
