# 0014 — A leap day is never resolved backwards

**Status:** Accepted
**Requirements:** R-D9, R-D11, R-D12, §19.4

## Context

`振込指定日` is four digits with no year. `MonthDayResolver.forwardLooking` picks the next occurrence
at or after a reference date, considering the reference year and the one after it, as §19.4's
reference implementation does.

That algorithm has no answer for `0229` when neither candidate year is a leap year, and the
reference implementation's shape invites two wrong ones:

- fall back to the most recent candidate that exists, which for a reference date of June 2028
  returns 29 February 2028 — three months in the *past*, from a strategy called forward-looking;
- search further ahead to the next leap year, which for a file written in 2026 returns
  29 February 2032 — six years out, from a file that almost certainly contains a typo.

R-D12 requires that `0229` in a non-leap candidate year be "reported explicitly, never surfaced as a
raw `DateTimeException`". It does not say what to report.

## Decision

Report that it could not be resolved. `DateResolution` carries either a date or a reason, never
both and never neither, and the reason for this case is
`LEAP_DAY_NOT_IN_CANDIDATE_YEAR`, alongside the candidate years that were considered.
`explain()` renders it as a sentence suitable for a loss report or a validation finding.

Nothing moves the date to 28 February or 1 March. Nothing searches beyond the candidate years the
strategy defines.

For `NEAREST`, the same rule applies across its three candidate years; where a leap year is among
them, `0229` resolves normally.

## Consequences

**Cost.** A caller has to handle an unresolved result. `DateResolution.isResolved()` and the
`Optional<LocalDate>` make that hard to skip, which is intended.

**Benefit.** The one date value that cannot be resolved is the one a caller most needs to be told
about, and they are told which years were tried. A file dated `0229` in a non-leap window is far
more likely to be a data-entry error than an instruction six years out; this reports the fact
instead of choosing an interpretation of it.

**Note.** Ties in `NEAREST` resolve to the earlier year, because candidates are considered oldest
first. Arbitrary, but deterministic and documented — INV-7 requires the same file to produce the
same result every time.
