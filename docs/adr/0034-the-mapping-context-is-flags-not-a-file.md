# 0034 — The mapping context is command-line flags, not a context file

**Status:** Accepted
**Requirements:** R-I20, R-CLI2

## Context

§27 gives the conversion commands a context file:

```
zengin convert <file> --to=pain.001 --context=ctx.yaml [--out=file.xml]
zengin dryrun  <file> --to=pain.001 --context=ctx.yaml
```

The context genuinely is required (R-I20): a `pain.001` does not carry 委託者コード, does not say
which Zengin format to produce, and does not say what to do when a name will not fit. Those have to
come from somewhere.

## Decision

They come from flags. There is no context file.

The context is eight values: an originator code, a reference date, a target format, a message
identifier, a recipient, a truncation policy, an unmappable-character policy and an
`EndToEndId` policy. A file for eight values is a second configuration language, in a tool whose
library deliberately parses no YAML at runtime (ADR-0016) — so a `--context` file would mean adding
a YAML parser to the CLI in order to read eight scalars.

Flags also put the values where a reader of a pipeline can see them. A CI job that says

```sh
zengin convert payments.txt --to=zengin \
    --originator-code=9900000001 --target-format=sougou-furikomi --as-of=2026-09-01
```

is self-describing in a way that `--context=ctx.yaml` is not.

## Consequences

**Cost.** A long command line, and no way to version-control the context as a unit. If a team wants
that, a shell script or a `Makefile` target holds the flags perfectly well, and is a file their
tooling already understands.

Adding a ninth and tenth value would weaken this. If the option list grows past the point where a
reader can hold it, a `--context` file becomes the better answer, and picocli reads one with
`@file` argument expansion today without any parser at all — which is the migration path if it comes
to that.

**Benefit.** No YAML parser in the CLI, no second file format to document, and a command that shows
what it is doing.

**A side effect worth naming.** `--as-of` closes OQ-12 for these two commands: it pins the date that
yearless `MMDD` fields resolve against, so a conversion does not depend on the day it was run.
`validate` still resolves forward from today, and should get the same flag.
