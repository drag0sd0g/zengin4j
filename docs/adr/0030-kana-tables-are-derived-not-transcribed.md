# 0030 — The kana tables are derived; only the judgement calls are declared

**Status:** Accepted
**Requirements:** R-K9, R-F2, R-T10

## Context

R-K9 says the transliteration tables are *a data resource, not code*, covered by
an exhaustive table-driven test. The obvious reading is a file listing every
pair: ア→ｱ, ガ→ｶﾞ, パ→ﾊﾟ, Ａ→A, and so on for about a hundred and ninety
entries.

Writing that by hand is the same work R-F2 forbids for byte offsets, and for the
same reason. A transposed pair would not look wrong. `ｼ` for `ｿ` reads as a
plausible name, `ﾂ` for `ｼ` likewise, and the error surfaces as a payment to
somebody whose name is nearly right.

But most of the table is not a judgement at all. Unicode already defines which
half-width form corresponds to which full-width one, through the compatibility
decompositions: normalising `ｶﾞ` under NFKC yields `ガ`. Inverting that over the
half-width block yields the whole narrowing table, voiced decomposition
included.

## Decision

**Derive what is derivable; declare what was decided.**

The build reads `zengin4j-core/kana/kana-substitutions.yaml`, which holds only
the characters the standard's field rules refuse and what to write instead —
about twenty entries, each with a severity and a reason. It derives the
mechanical correspondence from Unicode, merges the two, and emits committed Java
into `io.zengin4j.core.kana.generated`. Same pipeline as the format descriptors,
same reasoning: `core` parses nothing at run time (ADR-0016).

**The generator checks its own output.** Every narrowed form must be one byte per
character in JIS X 0201 — a two-byte result would silently consume twice the
room a caller budgeted — and the table must have exactly 186 entries. A JDK
whose Unicode data disagreed would fail the build with a number in the message
rather than emit a different table on one leg of the CI matrix.

**The test is anchored elsewhere.** A test that re-derived the table would agree
with itself and prove nothing, so `KanaTableTest` asserts against §16.1's byte
layout instead: the gojūon, transcribed by hand, must narrow to `0xB1` through
`0xDD` in order. That transcription is the right place for transcription — a
slip in it fails immediately, whereas a slip in the table would ship.

## Consequences

- The reviewable artifact is twenty declared substitutions, not a hundred and
  ninety mechanical pairs. A reader can check the decisions without checking
  arithmetic.
- The reader also gets the reasons: each entry carries `why-en` and `why-ja`,
  which become the text of the loss entry a caller sees.
- `KanaSubstitutionReader` refuses a substitution whose replacement is itself
  substituted, because the engine makes a single pass and such a rule would not
  resolve.
- `checkGeneratedSources` covers the kana table as it covers the descriptors, so
  a hand-edit cannot survive review.
- The derivation depends on the JDK's Unicode data. Pinned by cardinality and
  checked on every build; if it ever changes, the diff is reviewed rather than
  accepted.

## Alternatives

**Transcribe the whole table by hand.** The literal reading of R-K9, and the one
that risks a wrong name in production. Rejected on R-F2's reasoning.

**Call `Normalizer` at run time.** Would make `core` depend on nothing extra,
but the table would stop being data anybody can review, and behaviour would
follow whichever JDK the caller happened to run.

**Ship the table as a resource read at run time.** Contradicts ADR-0016 and
would put a parser back in `core`.
