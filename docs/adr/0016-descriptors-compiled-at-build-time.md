# 0016 — Descriptors are compiled at build time, not parsed at runtime

**Status:** Accepted
**Supersedes:** [ADR-0001](0001-hand-written-yaml-reader.md)
**Requirements:** R-M1, P3, R-F2, R-F3, R-F6, R-X1

## Context

ADR-0001 resolved an apparent conflict — descriptors must be loadable, `core`
may have no dependencies — by writing a YAML subset reader by hand. That
resolution held for one release and does not survive scrutiny.

**The ratio.** 886 lines of parser and 464 lines of tests, to read 198 lines of
descriptor YAML. Nothing else in the codebase is shaped like that.

**The strictness was never in the parser.** Every check that protects a byte
layout — lengths summing to the record length, sequence numbers in order,
contiguous offsets, constants agreeing with discriminators, unknown keys
rejected, `verified` requiring two citations — lives in the descriptor reader
and the descriptor model. That layer behaves identically whichever tokenizer
feeds it. The hand-written one bought no safety at all.

**It was the wrong question.** R-X1 asks that a consumer be able to register
descriptors "loaded from their own YAML". That was read as *core must parse
YAML*. It reads at least as naturally as *the consumer parses their own YAML
and hands us a descriptor* — which needs no parser in core, and does not oblige
anyone to learn this project's YAML dialect.

**And the answer was already in the build.** A codegen pipeline was already
reading these exact files to emit record classes. Nothing about descriptors
needs to happen at runtime.

## Decision

Move descriptor loading into the build.

- `zengin4j-codegen` reads the YAML with SnakeYAML. A build-scoped dependency
  is not a runtime dependency; R-M1 constrains the published artifact.
- It emits a committed `BundledFormats.java` that constructs the descriptors
  in Java.
- `FormatRegistry.defaults()` calls it. It reads no files and cannot fail on
  malformed input — a descriptor that did not add up would have failed the
  build.
- The descriptors move out of `src/main/resources` to `zengin4j-core/formats`.
  They are build inputs, and no longer ship inside the jar.
- `core.format.yaml` and `DescriptorLoader` are deleted.

R-F2 survives the move intact, and gets stronger. Generated code does not write
offsets either: it lists field lengths as `FieldSpec`s and
`RecordDescriptor.of` computes the offsets, which is now the single gate every
layout passes through — generated, hand-built, or registered at runtime by a
consumer.

R-X1 is served by that same API. A consumer builds a `FormatDescriptor` and
passes it to `FormatRegistry.builder()` or `withFormat`, parsing their own
files with whatever they already use.

## Consequences

**What it costs.** Registering a descriptor from a YAML file at runtime now
requires the consumer to bring a YAML parser. For an application that is a
non-event; for a library it would have been a dependency imposed on everyone to
serve a few. And changing a byte layout now requires a rebuild.

That last point reads as a limitation and is closer to a feature. R-B10 makes
any change to parsed output a major version bump. A design where a payment
file's layout can be altered by dropping a file on the class path is at odds
with that; one where it takes a release is not.

**What it buys.**

| | Before | After |
|---|---|---|
| Hand-written code in `core` for descriptors | 1,144 lines | 0 |
| Tests for that code | 464 lines | 0 (19 tests moved to codegen) |
| Descriptor resources in the published jar | 2 files | none |
| Layout validation | runtime, on first read | build failure |
| Offsets written by hand | never | never |

`core` still requires nothing but `java.base`, enforced by the same ArchUnit
rule as before — that property was never in question and is not what changed.

**One honest trade.** SnakeYAML resolves YAML 1.1 booleans, so `verified: yes`
is accepted as `true` where the previous reader rejected it by name. The
checks that matter — lengths, offsets, sums, citations — are unaffected, and
they now run in the build rather than in a consumer's process.

**What would make this wrong.** A consumer needing to swap descriptors without
a rebuild, for an institution-specific variant they cannot get released. The
answer then is a small optional module carrying a parser and depending on
`core`, never a parser back inside `core`.
