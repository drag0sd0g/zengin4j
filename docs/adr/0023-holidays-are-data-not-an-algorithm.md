# 0023 — The holiday calendar is data, and refuses to guess past it

**Status:** Accepted
**Requirements:** R-V6, R-V7, R-M2

## Context

R-V6 asks for a Japanese business calendar covering public holidays "including
the astronomically-determined moving holidays", substitute holidays, and the
year-end closure. R-V7 asks that it declare `validUntil()` and fail loudly past
its horizon.

The tempting implementation is a set of rules: fixed dates, "the third Monday
of January", and a formula for the equinoxes. Several such formulas circulate.

**They cannot be right.** 春分の日 and 秋分の日 are fixed by an astronomical
determination that the Cabinet Office publishes in February of the preceding
year. There is no algorithm that yields them, only approximations that agree
with the published dates for a while and then do not. A calendar that
extrapolated would be confidently wrong about a date somebody scheduled a
payment for.

The same applies less dramatically to everything else. Japan has moved holidays
by legislation several times in the last decade — for an Olympics, for an
imperial succession — and each time a rule-based calendar was wrong until
somebody updated it.

## Decision

Bundle the Cabinet Office's published CSV as a resource, which R-M2 explicitly
permits, and answer from it.

- Converted mechanically: Shift_JIS to UTF-8, dates to ISO 8601. Nothing is
  computed and nothing is dropped.
- Substitute holidays (振替休日) and bridge holidays (国民の休日) are already in
  the source, so they are not derived either.
- The file declares its own `horizon`, and `validUntil()` returns it.
- Past the horizon, `BeyondCalendarHorizonException` — never a guess.

The **year-end closure is separate**, because it is not a holiday question. 2
and 3 January are ordinary days in the holidays act and financial institutions
are shut; 2 January 2026 is a Friday, and a payment dated then does not settle
then. This is modelled as its own kind of non-business day so a finding can say
which of the two reasons applies.

Validation never sees the exception. `V-505` catches it and reports "I cannot
tell you about this date", because R-V1 says validation returns a report.

## Consequences

**What it costs.** The calendar expires. Its horizon today is the end of 2027,
and a file dated later produces an honest `V-505` rather than an answer. The
fix is to refresh one CSV and re-run the conversion, which is a smaller job than
auditing a formula against a legislature.

**What it buys.** The dates are right, including the ones a formula gets wrong,
and the calendar says plainly when it does not know. A validator that answers
"business day" for a date it has no data about is worse than one that declines.

**What would make this wrong.** Nothing about the equinoxes will change. What
could change is the shape of the requirement: if a consumer needs to validate
files years ahead, they need a calendar with later data, and that is an argument
for making the data pluggable rather than for computing it. The
`BusinessCalendar` interface already allows exactly that — `JapaneseBankCalendar`
is one implementation, not the contract.
