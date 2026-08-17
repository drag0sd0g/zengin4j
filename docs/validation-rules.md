# Validation rules

Every finding this library can produce, with the id you suppress it by.

> Generated from the message bundle and the registered rules. `RuleReferenceTest`
> fails the build if this page and the code disagree, so the ids and severities
> here are the ones you will actually see.

Severity is a **default**. Institutional practice varies enough that some rule
here is wrong for somebody, so every id is individually suppressible and
re-rankable (R-V3):

```java
ZenginValidator.builder()
        .suppress("V-605")                       // turn one off
        .severity("V-306", Severity.ERROR)       // or take one more seriously
        .build();
```

Ids are **stable across versions**. Renumbering one would silently re-enable a
check somebody had turned off deliberately.

Errors stop a file being submittable; warnings and information do not.


## Tier 1 — structural

Is this a Zengin file at all? These run first and, under `failFast`, alone: if the records are not the length the format declares, every later tier is reading the wrong bytes.

| Id | Default | Checks |
|---|---|---|
| `V-101` | ERROR | Every record is exactly the format's record length |
| `V-102` | ERROR | The first byte of every record is a known データ区分 |
| `V-103` | ERROR | Data records follow a header |
| `V-104` | ERROR | Each header has exactly one trailer |
| `V-105` | ERROR | The file ends with an end record |
| `V-106` | ERROR | Nothing follows the end record |
| `V-107` | ERROR | The file contains at least one record |

## Tier 2 — field syntax

Does each field hold what its type permits? Runs over every record whose length and discriminator let its fields be located — including records the reader rejected, since a record is usually malformed *because* a field contains something it should not.

| Id | Default | Checks |
|---|---|---|
| `V-201` | ERROR | Numeric fields contain only ASCII digits |
| `V-202` | ERROR | Text fields contain only permitted characters |
| `V-203` | WARNING | Fields are aligned and padded as their type requires |
| `V-204` | ERROR | Constant fields hold their declared value |
| `V-205` | WARNING | Coded fields hold a value from their code list |
| `V-206` | ERROR | A voicing mark follows a kana that can take one |

## Tier 3 — consistency

Does the file agree with itself? A trailer that disagrees with its batch is the most common reason a file is rejected.

| Id | Default | Checks |
|---|---|---|
| `V-301` | ERROR | The trailer total equals the sum of its batch |
| `V-302` | ERROR | The trailer count equals the number of records in its batch |
| `V-303` | ERROR | Batch amounts do not overflow |
| `V-304` | ERROR | The batch total fits the trailer field |
| `V-305` | ERROR | Every header declares the same 種別コード |
| `V-306` | WARNING | No two payments in a batch are identical |

## Tier 4 — reference data

Do these institutions exist? **Skipped entirely unless you supply a `ReferenceDataProvider`** (R-V5).

| Id | Default | Checks |
|---|---|---|
| `V-401` | ERROR | The bank code exists |
| `V-402` | ERROR | The branch exists within its bank |
| `V-403` | WARNING | The name matches the reference data |

## Tier 5 — calendar

Will funds actually move on that date? **Skipped entirely unless you supply a `BusinessCalendar`** (R-V6).

| Id | Default | Checks |
|---|---|---|
| `V-501` | ERROR | The value date is not a weekend |
| `V-502` | ERROR | The value date is not a public holiday |
| `V-503` | ERROR | The value date is not in the year-end closure |
| `V-504` | WARNING | The value date is within the accepted forward window |
| `V-505` | INFO | The value date is within the calendar's horizon |

## Tier 6 — semantic warnings

Valid, and looks wrong anyway. Every rule here describes something an institution will accept — which is the point: a rejected file gets fixed the same afternoon, and an accepted wrong one does not.

| Id | Default | Checks |
|---|---|---|
| `V-601` | WARNING | A name is not truncated through a voicing mark |
| `V-602` | WARNING | No payment is for zero |
| `V-603` | WARNING | No amount sits at the field maximum |
| `V-604` | WARNING | Name fields are populated |
| `V-605` | INFO | Customer reference fields are populated |

## Two ids that are not rules

| Id | Meaning |
|---|---|
| `V-000` | A rule threw. The defect is in the rule, not in your file; every other rule still ran (R-V1). |
| `V-100` | The file could not be read at all — wrong record length throughout, an unknown 種別コード, an EBCDIC declaration. Reported rather than thrown, because "this is not a file I can parse" is the answer you asked for. |

## Rules that share a walk of the file

Some checks answer several questions from one pass, because computing them
separately could produce answers that disagree with each other:

| Rule | Also emits | Why |
|---|---|---|
| `V-301` | `V-303`, `V-304` | The batch sum is computed once. Overflow, capacity and mismatch are three verdicts on one number (§19.3). |
| `V-501` | `V-502`, `V-503`, `V-505` | The value date is classified once, and the finding names which kind of non-business day it is. |

Each id is suppressible on its own — turning off `V-502` leaves `V-501`
working.

## What these rules do not check

Stated plainly, because a validator that reports nothing is indistinguishable
from one that checked nothing.

**Tier 4 is existence, not activity at the value date.** §14.3 asks for "both
active at the value date where temporal data is available", and
`ReferenceDataProvider` has no temporal dimension — so for this implementation
temporal data is never available and `V-401`/`V-402` answer "does this
institution exist in the snapshot you gave me", nothing more. A branch that
closed last month, or one that opens next quarter, passes. Adding the dimension
means dated validity on the provider interface and a dataset that carries it;
`zengin-code` does not. Until then the honest reading of a clean tier 4 is that
the codes are well-formed and known, not that the money will arrive.

**Tier 4 name matching is advisory.** `V-403` compares against whatever the
provider returns. Abbreviations, spacing and the treatment of 支店/出張所 vary
between datasets and between banks, so it is a warning that says "check this",
not a verdict.

**Nothing here checks your agreement with your bank.** Field widths, permitted
characters and file structure are the standard's; cut-off times, per-file record
limits, whether 総合振込 and 給与振込 may share a transmission, and which 種別コード
your institution actually accepts are contractual. A file this library calls
submittable can still be refused on those grounds.

