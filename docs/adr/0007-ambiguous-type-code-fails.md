# 0007 — An ambiguous 種別コード fails rather than guesses

**Status:** Accepted
**Requirements:** §0.2, §13.1, R-E3

## Context

The reader identifies a file's format from the 種別コード in its header. §12.2 shows that as a
lookup returning one descriptor.

It cannot always be one. 預金口座振替 and 口座振替結果 both use 種別コード `91` and differ only in
whether the 振替結果コード is populated (§13.1). One is an instruction to collect money; the other
reports what happened when someone tried. Choosing wrongly between them is choosing wrongly about
the direction of a payment.

## Decision

`FormatRegistry.byTypeCode(String)` returns a `List<FormatDescriptor>`, not an `Optional`. The
reader handles all three cases:

| Matches | Behaviour |
|---|---|
| 0 | `UnsupportedFormatException`, naming the code and listing what is registered |
| 1 | Use it |
| >1 | `AmbiguousFormatException`, naming both candidates and pointing at `ReaderOptions.format(...)` |

`AmbiguousFormatException` is an addition to the taxonomy in §17, which does not anticipate the
case.

## Consequences

**Cost.** One more exception type, and a `List` where an `Optional` reads more naturally today —
only one format is registered, so the ambiguous branch is currently unreachable outside its test.

**Benefit.** When Epic 3 adds the second `91` format, the shape is already right and no caller's
code silently starts reading result files as instruction files. The alternative — resolving the
ambiguity by registration order, or by a heuristic over the first data record — is the kind of
confident wrong answer §0.2 exists to prevent.

**Open.** Whether a heuristic is ever acceptable here is recorded as OQ-1.
