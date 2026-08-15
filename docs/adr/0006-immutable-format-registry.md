# 0006 — The format registry is immutable, so it has no `register`

**Status:** Accepted
**Requirements:** R-T1, R-T4, R-0.10, R-0.11, R-X1, R-F6

## Context

Two requirements conflict directly.

R-X1 asks for `FormatRegistry.register(FormatDescriptor)`, so that a consumer can add an
institution-specific variant at runtime without forking.

R-T1 requires `FormatRegistry` to be immutable and thread-safe — "build once, share freely" — and
R-0.11 makes immutability the default for every public type. A mutating `register` on a shared
registry means a descriptor can appear, or change, under a reader that is midway through a file.

§0.6 says: where two requirements appear to conflict, record it as an open question and implement
the safer behaviour.

## Decision

Immutability wins, and the extension point is preserved in a different shape:

- `FormatRegistry.builder().register(...)...build()` — assemble a registry before use.
- `registry.withFormat(descriptor)` — returns a **new** registry; the receiver is unchanged.
- `ReaderOptions.builder().registry(...)` — inject the registry the reader should use.

A consumer registering their own descriptor builds it with `RecordDescriptor.of(...)` — which
computes the byte offsets for them (R-F2) — and passes it to either of the first two, which
satisfies the intent of R-X1 without a mutable shared object. Since ADR-0016 there is no descriptor
file reader in `core`, so a consumer who keeps their variants in YAML parses them with whatever
their application already uses.

## Consequences

**Cost.** A consumer who expected to call `register` on a global registry has to thread one through
their code instead. That is more typing, and it is the point: the registry a reader used is visible
at the call site.

**Benefit.** No static mutable state, no lazy singleton, no defensive copying inside the reader, and
`FormatRegistry.defaults()` can be called from any thread at any time. `withFormat` refuses a
duplicate id rather than silently replacing a descriptor.

**Note.** `FormatRegistry.defaults()` parses the bundled descriptors on every call rather than
caching them, because a cache is exactly the lazily-initialised shared state R-T4 forbids. The
Javadoc tells callers to build one and inject it.
