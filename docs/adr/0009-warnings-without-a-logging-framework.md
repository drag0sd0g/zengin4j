# 0009 — Warnings go to a listener, not a logging framework

**Status:** Accepted
**Requirements:** R-M1, R-O4, R-0.3, R-C10

## Context

The reader has things to say that are neither errors nor validation findings: it stripped a byte
order mark; it is reading with a format whose layout is unconfirmed (§0.3 asks for "a startup
warning"); the file mixes separator conventions.

`core` may not depend on a logging facade (R-M1), and R-O4 puts structured logging in the Spring
Boot starter and "never in core". But a warning that goes nowhere by default is not a warning — an
unverified format would be used with no signal at all.

## Decision

A typed `ZenginWarning` — code, English message, Japanese message, byte offset — with two
destinations:

1. **Collected on the reader.** `reader.warnings()` returns every warning raised, so nothing is
   lost regardless of configuration.
2. **Handed to a listener.** `ReaderOptions.warningListener(Consumer<ZenginWarning>)`, defaulting to
   one line through `System.Logger` at `WARNING`.

`System.Logger` is part of `java.base`, so it costs the module no dependency, and it routes into
whatever the host application has configured — SLF4J, Log4j and JUL all bridge it. A caller who
wants silence passes `warning -> {}`; a caller who wants structured output passes their own
consumer.

## Consequences

**Cost.** A strict reading of R-O4 says "no logging in core", and this writes one line through a
JDK logging interface. The requirement's parenthetical cites R-M1, so the concern is dependencies
rather than the existence of a log line — and the default is overridable in one call.

**Benefit.** The §0.3 startup warning actually reaches someone by default, warnings are typed and
bilingual rather than formatted strings, and an ArchUnit rule still fails the build if
`java.util.logging` or SLF4J appears in `core`.

**What would make this wrong.** If a consumer reported that the default listener wrote to a
destination they could not control, the default would move to no-op — but only alongside a louder
signal for the unverified-format case, never on its own.
