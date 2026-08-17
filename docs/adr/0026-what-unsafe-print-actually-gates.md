# 0026 — What `--unsafe-print` actually gates

**Status:** Accepted
**Requirements:** R-CLI4, R-CLI5, R-E6

## Context

Two requirements point in opposite directions.

R-CLI4: *never print full record contents by default — payment data must not
leak into CI logs. Require `--unsafe-print`.*

R-CLI5: *`inspect --annotate` prints, per field: byte offset, hex, decoded
value, field name in both languages, and a validity marker.*

Read strictly, R-CLI4 forbids the tool R-CLI5 asks for. "Full record contents"
has to mean something narrower than "the record", or `--annotate` prints nothing
without a flag whose name is designed to discourage use — and a diagnostic tool
that shows nothing by default is one people stop reaching for.

## Decision

`--unsafe-print` gates the fields the descriptors mark `sensitive`. Everything
else prints.

In the bundled formats that is the account numbers, which is what R-E6 already
masks in library diagnostics. Names, amounts, dates, bank codes and branch codes
print in full.

**And it gates the hex, not only the decoded value.** `39 38 37 36 35 34 33` is
an account number to anyone who can read hex, which includes everyone likely to
be reading a byte-annotated dump. Masking one and printing the other would be
theatre. `MaskingTest` asserts the ASCII-hex form does not appear either.

**Masking never suppresses the validity check.** A masked field is still checked
against its type, its character class, its constant and its code list; only the
value is hidden. A defect in a field you cannot see is exactly the defect you
most need told about.

**The warning goes to stderr.** A caller doing `zengin inspect x --unsafe-print
> dump.txt` still sees that they asked for unmasked output, and `dump.txt` does
not gain a line that is not part of the output.

## Why this is the right line

The categories differ in how bad exposure is and in how much the tool loses by
hiding them.

An account number plus a bank and branch code is enough to attempt a debit. It
is also the field a diagnostic almost never needs in full — "the account number
is 7 digits and all of them are digits" answers nearly every question, and
`***6543` still lets a reader match a record against a spreadsheet row.

A beneficiary name and an amount are personal data and should not be casually
published either. But masking them makes `inspect` and `diff` close to useless:
"a name field changed from `***` to `***`" tells nobody anything, and the whole
value of `diff` on a payment file is seeing which payment changed. Since the
files these commands read are files the user already has on disk, hiding their
contents from the person who owns them buys little.

This is a judgement call and it is written down so it can be argued with. The
lever exists in both directions: a descriptor can mark more fields `sensitive`,
and that immediately widens what the CLI masks, because the CLI reads the flag
rather than keeping its own list.

## Consequences

- The masking rule lives in one place, `FieldRendering.render`, and every
  command that prints record contents goes through it.
- `MaskingTest` asserts the account number does not appear *anywhere* in the
  output of every such command, rather than asserting that a masking function
  was called — the second passes happily while some other code path prints it.
- `inspect --out-format=json` carries `"masked": true` so a consumer can tell
  which kind of document it has.
- A future format that marks, say, 顧客番号 sensitive gets masked with no CLI
  change.

## Alternatives

**Mask everything by default.** Faithful to the strictest reading of R-CLI4 and
produces a tool nobody uses. Rejected.

**Mask nothing and rely on the flag name.** Rejected: the default is what runs
in CI, and CI output is the CI provider's storage for ever.

**A `--mask=none|sensitive|all` scale.** Rejected as more configuration than the
problem has. Two states are what the requirement describes and what people will
remember under pressure.
