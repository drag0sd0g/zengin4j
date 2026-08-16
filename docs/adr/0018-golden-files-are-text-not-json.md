# 0018 — Golden files are a text rendering, not JSON

**Status:** Accepted
**Requirements:** R-T8, R-M1, R-L5

## Context

R-T8 asks for golden files "in `src/test/resources/conformance/` with expected
parse results checked in as JSON. Any output change becomes a visible diff in
review."

The requirement has two halves, and only one of them is about JSON. The
load-bearing half is the second sentence: a change in how bytes are decoded must
show up as something a reviewer reads, rather than as a number moving in a
coverage report. JSON is one way to get that, named before there was a codebase
to name it against.

Three things make it the wrong way here.

**`core` has no JSON writer, and is not getting one.** R-M1 and P3 keep the
published artifact on `java.base` alone. Producing JSON goldens means either a
test-scoped JSON dependency, or hand-rolling a serialiser — and hand-rolling a
parser to satisfy an inferred requirement is exactly the mistake
[ADR-0016](0016-descriptors-compiled-at-build-time.md) was written to undo.

**JSON diffs worse than the thing it would encode.** What a reviewer needs to
see is *one field decoded differently*. A field-per-line rendering puts each
field on its own line with its offset and length, so a changed field is a
one-line diff with its byte position right there. The JSON equivalent is the
same information with punctuation, indentation and a bracket cascade whenever
the record count changes.

**A parsed 全銀 record is not a tree.** JSON's shape earns its keep on nested,
variable data. A record is a flat, fixed sequence of fields, and the file is a
flat sequence of records. The format would be carrying structure the data does
not have.

## Decision

Commit two files per corpus:

- `conformance/input/<format>.txt` — the bytes.
- `conformance/<format>.expected.txt` — a rendering, one field per line, with
  each field's id, byte offset, length and decoded value, plus the file's
  framing and each batch's computed count and total.

`GoldenFileTest` asserts one against the other, and also asserts that the
deterministic generator still produces the corpus, so the corpus itself stays
reproducible. Regenerate with `./gradlew :zengin4j-core:test -Pgolden.regenerate`
— and then read the diff, because a golden updated without being read is worse
than no golden at all.

**The corpus lives under `input/` for a separate reason.** The identifier scan
(R-L5) flags bare runs of seven or more digits that do not begin with 9 or 0. In
a fixed-length record every field abuts the next with no separator, so a digit
run spans field boundaries and takes its first digit from whatever precedes it —
a データ区分 or 種別コード constant. *No conformant file of these formats can
pass that check*, whatever its account numbers are. So `input/` is excluded and
the rendering is not: the rendering contains every field of every record, one
per line, which is the representation where a digit run means a single field and
the scan works as intended. Verified by planting an out-of-range account number
in a rendering and watching the scan fail.

## Consequences

**What it costs.** A consumer wanting to diff our goldens with a JSON tool
cannot. Nobody has asked, and the renderer is about forty lines if that changes.
The rendering is also this project's own format, so it carries no schema and no
tooling — its only contract is that it is stable and diffable.

**What it buys.** No dependency, no serialiser, and a diff that reads as prose:

```
   amount                   [80+10] 0000150000
```

A changed byte offset moves the bracketed numbers. A changed charset decision
changes the value. Both are one line.

**What would make this wrong.** Epic 4 produces validation findings as JSON and
SARIF, because those are consumed by tools rather than read by people. If golden
files ever need to be *machine*-compared against another implementation's
output, JSON becomes the right answer for that comparison — and it can sit
alongside this rendering rather than replace it, since the two answer different
questions.
