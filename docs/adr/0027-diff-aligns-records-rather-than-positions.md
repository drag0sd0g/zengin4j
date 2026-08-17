# 0027 — `diff` aligns records rather than comparing positions

**Status:** Accepted
**Requirements:** §27 (5.5)

## Context

`zengin diff a.txt b.txt` has to answer "what changed". Three approaches were
available, and the cheapest two are both wrong in ways that matter.

**A byte diff** reports that the files differ somewhere. In a fixed-length
format the records are one line each, so a changed amount reports the whole
120-byte line as changed and the reader is back to counting columns by hand.
This is what `diff(1)` already does, and if it were adequate the command would
not need to exist.

**Positional pairing** — record 3 against record 3 — is the obvious step up and
fails on the single most common edit. Insert one payment near the top of a
50-payment file and every later record pairs against its neighbour: 49 records
reported as changed, one as added, and the actual edit buried. That is precisely
the change somebody most wants to see clearly.

## Decision

Align records by longest common subsequence over their raw bytes, then compare
the aligned pairs field by field.

**Matched on bytes.** Two records that differ anywhere are different records.
The alternative — pairing on an identity like bank + branch + account — would
require the alignment step to guess what "the same payment" means, and guessing
wrong attributes a changed amount to the wrong beneficiary. Byte equality never
guesses.

**Runs of differences are paired into edits.** The backtrack emits removals and
additions in runs — four removals then four additions, not four alternating
pairs — so a run is taken as a whole and its removals paired positionally with
its additions. Without this, four edited payments report as eight events and the
reader does the pairing themselves. This was a real defect: the first
implementation merged only immediately adjacent pairs and reported
"0 changed, 4 added, 4 removed" for four edited records.

**Positional pairing inside a run is a heuristic** — the same one every
line-based diff uses for "changed". It can mis-pair when several records change
at once in different ways. The field-level output makes that visible rather than
hiding it: a mis-pairing shows up as a record where every field differs, which
reads obviously wrong.

**Common prefix and suffix are stripped first**, and that is what makes
`O(n·m)` affordable rather than a nice thing to say. Without it, two
8,000-record files need a 64-million-cell table — about 256 MB — and the
command dies. With it, the usual edit (a handful of payments changed in a file
of thousands) leaves a middle of a few records and the table is trivial. This
was not a theoretical concern: `diff` on two 8,000-record files raised an
`OutOfMemoryError` under a 256 MB heap.

**And there is a stated ceiling.** After stripping, a table above 16 million
cells is refused with a sentence rather than attempted. Reaching it means
thousands of records differ, and a field-level diff of thousands of changed
payments is not something anybody reads — the useful answer at that size is per
file, which `inspect` and `validate` give.

The ceiling matters more than its value, because of how the failure would
otherwise present. An `OutOfMemoryError` is an `Error`, not an `Exception`:
picocli's handler does not catch it, it escapes uncaught, and **the JVM exits
with status 1 — which in this tool means "the files differ"**. A crashed
comparison would have been indistinguishable from a successful one that found
changes, which is the worst possible failure for a command whose output a script
branches on. `Zengin.run` now also catches `Throwable` so no future `Error` can
reproduce that collision.

If a 200,000-record file ever needs diffing throughout, Myers' algorithm is the
answer and this is the place to note it.

## Consequences

- Inserting or deleting a payment reports as one addition or removal, with
  every other record unchanged.
- An edited payment reports as one `~` entry naming the fields that changed,
  their 項目名, their byte offsets and their before-and-after values.
- Two files of different formats are refused rather than diffed, because
  field-by-field comparison across formats would align 受取人名 against 合計金額.
- Exit status `1` when the files differ, matching `diff(1)` and
  `git diff --exit-code` (ADR-0025).
- Sensitive fields are masked on both sides (ADR-0026). A diff saying an account
  number changed from one masked value to a different one still conveys that the
  field changed, which is the part that was needed.

## Alternatives

**Myers' diff.** Better asymptotics, more code, no observable difference at
these sizes.

**Pair on a payment identity.** Rejected above: it makes the alignment step
guess, and a wrong guess misattributes an amount.

**Report only summary counts.** Rejected — the counts are the part `wc -l`
already gives you.
