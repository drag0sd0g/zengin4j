# 0008 — Stale record views are detected, not merely documented

**Status:** Accepted
**Requirements:** R-MEM1, R-MEM2, R-MEM5, R-P3

## Context

The throughput target in §22 is reachable only if parsing does not allocate a `String` per field.
The design is a lazy view over a recycled buffer: a `RecordView` is a `(byte[], offset, descriptor)`
triple that copies nothing.

R-MEM2 states the consequence — a view is valid only until `next()` is called again — and requires
it to appear in bold in the Javadoc. Documentation is the whole of the requirement.

Documentation is not enough. A retained view does not fail: it silently describes whatever record
now occupies those bytes. The amount is a plausible amount. The beneficiary is a plausible
beneficiary. It is the wrong payment, and nothing in the output says so.

## Decision

Detect it. The reader and its views share a generation counter, incremented whenever the buffer
advances or is compacted. Every accessor on a view compares its captured generation against the
current one and raises `StaleRecordViewException` — naming the record number, and saying to call
`materialize()` — if they differ.

`StaleRecordViewException` is an addition to the taxonomy in §17.

A view is allocated per record rather than reused, so that a stale reference is a distinguishable
object. That allocation is a few dozen bytes with an obvious lifetime; the requirement it satisfies
is zero allocation *per field* (R-P3), which is untouched.

## Consequences

**Cost.** One `int` comparison per field access, and one small short-lived allocation per record.
Neither is measurable against the ~2.3 µs per record budget, and the per-record allocation is a
strong escape-analysis candidate where the view does not escape.

**Benefit.** The most likely misuse of the fast API fails immediately, at the point of misuse, with
a message that names the fix. A defect that would otherwise reach production as misrouted payments
becomes a stack trace in the caller's first test run.

**What would make this wrong.** If benchmarking in Epic 3 showed the per-record allocation costing
real throughput, the check could move behind an assertion or a debug flag. It should not simply be
deleted: the class of bug it catches is the one this library exists to prevent.
