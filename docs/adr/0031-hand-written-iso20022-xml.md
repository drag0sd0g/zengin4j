# 0031 — ISO 20022 XML is written by hand, not bound from schemas

**Status:** Accepted
**Requirements:** R-I21, R-M3, R-T14

## Context

Issue 7.1 asks for JAXB bindings generated from the official XSDs, and R-M3 names
`zengin4j-iso20022` as the one module permitted an XML dependency. R-I21 asks for the generated XML
to be validated against those XSDs in CI, and then hedges: *ship XSDs only if licensing permits;
otherwise document how to obtain them and validate optionally.*

That hedge is the whole problem. The ISO 20022 message definitions are published by ISO 20022 under
terms this repository is not in a position to redistribute under. If the schemas cannot be committed,
they cannot be present at build time either — so a build that generates bindings from them cannot
run in CI, cannot run on a fresh clone, and cannot run for a contributor who has not downloaded them.
Generation is not optional in the way validation is.

Committing the *generated* bindings instead moves the question rather than answering it: machine
translations of a schema carry the schema's structure, which is the part in question.

Meanwhile the profile uses a small, fixed part of one message definition, and the version is pinned
(R-I3). This is not a case of tracking a moving standard.

## Decision

Model the `pain.001.001.03` subset by hand, as records, and read and write it with `java.xml` —
StAX for reading, a hand-written serialiser for writing.

`zengin4j-iso20022` therefore has **no runtime dependencies**. R-M3 permits it one; it turns out not
to need one.

XSD validation is a task that runs when it is pointed at schemas the user obtained:

```
./gradlew :zengin4j-iso20022:validateAgainstXsd -Pxsd.dir=/path/to/schemas
```

and skips loudly when it is not. It is deliberately **not** wired into `check`: a gate that passes
silently when its input is missing is worse than an absent one, because it reads like coverage
nobody has.

This is the same reasoning as [ADR-0022](0022-hand-written-json-and-sarif.md), which hand-wrote JSON
and SARIF for the same three reasons: fixed shape, a dependency in a module whose value is partly
that it has none, and a hand-written writer being dangerous only when nothing checks it.

## Consequences

**Cost.** Elements outside the subset are not modelled, and a schema change would not break the
build. Neither matters much here — the version is pinned, and `XmlElement` is exported, so a caller
who needs `Purp` or a postal address reaches it through the tree rather than forking the library.

The real cost is that correctness of the *lexical* details is on us. That is not theoretical: the
first version wrote `2026-09-01T00:00Z` for `CreDtTm`, because `OffsetDateTime.toString()` omits
zero seconds and `xs:dateTime` does not allow that. It parses back perfectly and is invalid on the
wire. Only a schema notices — which is exactly why the opt-in task exists, and why `IsoDateTime`
now exists as well.

**Benefit.** The module publishes with nothing behind it. Every document it writes is parsed back by
a real parser in the tests, every document it reads is parsed by the JDK's, and a committed golden
conversion makes a change to the output a diff somebody reads.

**What would make this wrong.** Licensing that clearly permits redistribution, or a second and third
message arriving with enough surface that hand-modelling stops being cheaper than generating. Epic 8
adds `pain.002`, `camt.052` and `camt.054`; if that goes badly, this is the decision to revisit.
