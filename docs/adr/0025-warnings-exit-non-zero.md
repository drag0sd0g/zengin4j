# 0025 — A file with only warnings exits 1

**Status:** Accepted
**Requirements:** R-CLI1, R-V3

## Context

R-CLI1 fixes the exit codes: `0` clean, `1` warnings only, `2` errors, `3`
usage error, `4` I/O failure.

The `1` is unusual and worth defending rather than merely implementing. Most
tools exit `0` when they merely have something to say — `javac` with warnings,
most linters in their default configuration. A CI step that runs
`zengin validate` will go red on a file every rule considers submittable, and
somebody will file that as a bug.

## Decision

Implement R-CLI1 as written, and document the reasoning where the exit codes
are defined.

**The costs are not symmetric.** Every warning describes something an
institution will accept and a human would probably want to change: a duplicated
row, a zero-amount payment, a blank name field, a value date five weeks out. A
file stopped for a warning costs somebody a minute. A wrong file that went
through costs a reversal, a phone call to a bank, and somebody's afternoon —
and in the duplicate-payment case, money that has actually left.

**A warning that never stops anything is a warning nobody reads.** The
validation module deliberately does not block submission on warnings, because a
report that blocked on them is a report people learn to override. That is the
right call *inside a program*, where a caller inspects `isSubmittable()`. At a
shell prompt there is no such inspection: the exit status is the whole report
unless somebody chooses to read further, and folding warnings into `0` means
they are never seen at all.

**Saying "proceed anyway" is one line.** A pipeline that has decided its
warnings are acceptable writes:

```sh
zengin validate payments.txt || [ $? -eq 1 ]
```

Whereas recovering a warning that was folded into a `0` requires re-running with
`--out-format=json` and parsing it. The easy direction should be the one that
does not lose information.

**Suppression is the right tool for a warning you disagree with.** R-V3 makes
every rule suppressible by id, and the ids are stable across versions. A team
that considers `V-605` irrelevant writes `--suppress=V-605` once and gets a
clean `0` for ever after — which is better than a global "warnings are fine"
setting, because it names which warning and leaves the others working.

## Consequences

- `zengin diff` uses the same `1` for "the files differ", matching `diff(1)` and
  `git diff --exit-code`. A script comparing a generated file against a
  committed one reads naturally.
- Information-level findings (`V-505`, `V-605`) do **not** affect the status.
  They exist to tell you a rule ran, and stopping a pipeline for one would make
  the exit code meaningless.
- `ExitCodeContractTest` pins all five values, because scripts branch on them
  and changing one silently changes what somebody's pipeline does.

## Alternatives

**Exit 0 for warnings, like most tools.** Rejected above: it makes warnings
invisible in the one context where nobody is reading the prose.

**A `--strict` flag that promotes warnings to errors.** Rejected as the wrong
default *and* redundant: per-rule severity override already does this
(`--suppress` in one direction, and the library's `severity()` in the other),
with the advantage of naming which rule.
