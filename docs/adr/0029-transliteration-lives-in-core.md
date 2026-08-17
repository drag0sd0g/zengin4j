# 0029 — Transliteration and the loss vocabulary live in `core`

**Status:** Accepted
**Requirements:** R-M1, R-M2, R-M3, R-C18

## Context

§7 of the build specification places both packages inside the mapping module:

```
├── iso20022
│   ├── loss            MappingLossReport, LossEntry, LossKind, LossSeverity, LossCollector
│   ├── kana            KanaTransliterator, TruncationPolicy, KanaTables
```

That cannot work, for a reason the specification does not mention.

**R-C18 puts transliteration on the write path.** It requires three policies for
values a field cannot hold — `REJECT`, `TRANSLITERATE`, `REPLACE` — and writing
records is `core`'s job, in `RecordEncoder`. If the engine lived in `iso20022`,
`core` would have to depend on it, and the dependency runs the other way.

Nor can it be a sibling module that `core` depends on. R-M1 is enforced by
`checkPomHasNoDependencies`, which asserts that `zengin4j-core`'s published POM
declares **no** dependencies at all — not "no third-party ones". A
`zengin4j-kana` module would fail that check as surely as a YAML parser would.

The loss vocabulary follows the engine: `R-K4`, `R-K5` and `R-K8` all specify
losses in terms of `MATERIAL` and `INFORMATIONAL`, so whatever module holds the
transliterator holds those types too.

## Decision

`io.zengin4j.core.kana` and `io.zengin4j.core.loss`, in `core`.

**Nothing about them requires a dependency.** The tables are data compiled to
Java at build time (ADR-0030), exactly as the format descriptors are, so `core`
still ships no parser and no resources. `LossEntry` is a record and
`LossReport` is a list with three query methods.

**And the placement is the more useful one anyway.** Transliteration is not
specific to ISO 20022. A caller building a Zengin file from a CRM export needs
it and will never touch `pain.001`; the CLI's `generate` and a future
`zengin convert` both want it; validation could use `VoicingMarks` to stop
duplicating R-K7's ranges — and now does.

## Consequences

- `RecordEncoder` gained an `EncodingOptions` overload carrying the R-C18
  policy, and `TRANSLITERATE` routes through the engine with the target field's
  own `CharacterClass`.
- `VoicingMarks` moved into `core.charset`, and validation rule `V-206`
  delegates to it. There was one copy of R-K7's byte ranges in the validation
  module; there is one copy now, in the module both sides can see.
- Epic 7 builds `MappingLossReport` on `LossEntry` rather than defining a
  parallel vocabulary. R-0.4 asks for exactly that: the mapping layer depends on
  interfaces this epic stabilises.
- `core`'s `module-info` exports three more packages. `ModuleDescriptorTest`
  caught them missing, which is what it was written for.
- The specification's §7 tree is now wrong in two places. Recorded here rather
  than edited into agreement.

## Alternatives

**A `zengin4j-kana` module that `core` depends on.** Fails
`checkPomHasNoDependencies`, and would fail it for a good reason: the property
that makes `core` adoptable in a regulated environment is that it requires
nothing, not that it requires nothing *external*.

**Duplicate a minimal transliterator in `core` for R-C18 and keep the full one
in `iso20022`.** Two implementations of the same table, one of which would
eventually disagree with the other about how to spell a payee's name.

**Drop R-C18's `TRANSLITERATE` policy.** Would leave `core` with `REJECT` and
`REPLACE` only, and `REPLACE` without transliteration is a blunt instrument —
it turns a name into asterisks where narrowing would have preserved it.
