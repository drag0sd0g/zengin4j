# 0004 — Generated record shape and the field-id convention

**Status:** Accepted
**Requirements:** R-D1, R-D4, R-F3, R-M8

## Context

R-D1 calls the format-shaped domain model "the single most important structural decision in the
library": `SougouFurikomiHeader` has exactly the fields the header record has, in the order it has
them. R-F3 requires those types to be generated from the descriptors.

That leaves a question the specification does not answer: how does a generated type satisfy the
shared role interfaces? `HeaderRecord` promises `originatorCode()`, `codeKubun()` and
`valueDate()`; `DataRecord` promises `long amount()`. A generator could infer which field is the
amount from its name, its type, or its position — and every one of those inferences will eventually
meet a ten-digit account number and turn it into a number, losing its leading zeros.

## Decision

**Interpretation is declared, never inferred.** A field may carry an optional `format:` attribute
from a closed vocabulary — `MMDD`, `AMOUNT`, `COUNT`, `CODE-KUBUN`. Anything without one stays raw
text.

**A record component is named after its field id.** A header declaring `originatorCode` produces a
record with `originatorCode()`, which is exactly what the role interface promises. No mapping table,
no annotations: the descriptor's own vocabulary is the contract.

Component types follow the declared interpretation:

| `format:` | Component | Rationale |
|---|---|---|
| *(none)* | `String <id>` | Raw text; leading zeros and interior spacing survive |
| `AMOUNT` | `long <id>` | A zero-padded digit field plus its length round-trips exactly |
| `COUNT` | `int <id>` | Same |
| `MMDD` | `String <id>Raw`, plus `Optional<MonthDay> <id>()` | `"0000"` and `"1332"` both decode to no date; the raw form is the only lossless one |
| `CODE-KUBUN` | `String <id>Raw`, plus `CodeKubun <id>()` | An unrecognised value must survive as itself |

Where a role accessor has no field to answer it, the generator degrades honestly rather than
inventing: a header with no 委託者名 field generates `originatorName()` returning an empty string.
Where it *cannot* degrade honestly — `long amount()` has no value meaning "there wasn't one" — the
generator fails with a message naming the attribute to add.

## Consequences

**Cost.** Field ids in a descriptor are API. Renaming one is a source-incompatible change, and the
`Raw` suffix on two of the five cases is a wrinkle a reader has to learn once.

**Benefit.** A descriptor is checkable against the generated code by eye, and the generator refuses
to produce a type it cannot honestly fill. Reserved names are rejected at generation time, so a
field id colliding with a role accessor is a build failure rather than a compile error in generated
code.

**What would make this wrong.** A format whose header genuinely has no analogue of the fields
`HeaderRecord` promises would make the interface, not this convention, the thing to revisit. That
question is already open — see OQ-6.
