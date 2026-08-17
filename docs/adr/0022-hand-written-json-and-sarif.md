# 0022 — JSON and SARIF are written by hand

**Status:** Accepted
**Requirements:** R-V4, R-M2

## Context

R-V4 requires the validation report to serialise to JSON and to SARIF. R-M2
says `zengin4j-validation` depends only on `core`. Those two together forbid a
JSON library.

ADR-0001 made a related decision — hand-write a YAML reader rather than take a
dependency — and ADR-0016 undid it as a mistake. The obvious reading is that
this decision is the same mistake again.

It is not, and the difference is the whole argument.

## Decision

Write both by hand, in `ReportWriters`.

**Emitting is not parsing.** ADR-0016's finding was that 886 lines of parser and
464 lines of tests bought no safety, because every check that mattered lived
above the tokenizer. A parser has to cope with input somebody else wrote:
malformed escapes, surrogate pairs, numeric edge cases, nesting depth. A writer
emits a structure this code already holds, and its entire correctness surface is
escaping five characters and balancing brackets. `ReportWriters` is about 180
lines including the SARIF document shape.

**And it is checked by an independent implementation.** The tests parse the
output back with Jackson, test-scoped. A hand-written writer that produced
*almost* valid JSON would pass any number of `contains` assertions and fail in
the consumer; parsing it with a real parser is the only test worth having, and
costs nothing because a test dependency ships nowhere.

SARIF is worth the trouble rather than merely required. GitHub, GitLab and Azure
DevOps render it natively, so validating a payment file in CI produces
annotations against the file — which, for a fixed-length format where a finding
knows its record and byte offset, lands about as close to the problem as a
review tool can.

## Consequences

**What it costs.** A format-shaped writer rather than a general one: adding a
field means editing `ReportWriters` rather than annotating a class. For two
documents with a fixed shape, that is a smaller cost than it sounds.

**What it buys.** `zengin4j-validation` still has one dependency, which is
`core`, which itself has none. A consumer adopting validation adds two jars and
no transitive graph — the property R-M1 exists to protect, extended one module
outward.

**What would make this wrong.** A third and fourth output format, or a
requirement to *read* JSON — a suppression file, say, or a baseline of accepted
findings. Reading is the case ADR-0001 got wrong, and the answer there is a real
parser in a module that may have one, never a hand-written reader here.
