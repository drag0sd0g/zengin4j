# 0035 — The mapping is declared data, not an executable rule interface

**Status:** Accepted
**Requirements:** R-I19, R-X4, R-X5

## Context

§15.3's class diagram gives the mapping layer a `MappingRule` interface:

```
class MappingRule {
    <<interface>>
    +String sourcePath()
    +String targetPath()
    +boolean verified()
    +void apply(MappingContext, Source, Target, LossCollector)
}
```

and a `MappingRegistry` returning `List<MappingRule>`. R-X4 asks for `MappingRule` to be public and
for the registry to accept overrides "so a consumer can redirect e.g. `EndToEndId` placement".

An interface with `apply(...)` is a rule engine: the mapping executes by walking a list of rules,
each writing into a target through a path. That has a real attraction — a consumer can add a rule
without forking, and the declaration and the behaviour cannot disagree because they are the same
object.

It also has a cost that is easy to underestimate. `apply(context, source, target, loss)` needs a
`Source` and a `Target` general enough for a fixed-length record on one side and an XML tree on the
other, in both directions. Paths have to be resolved at runtime, which means path resolution becomes
a small language with its own failure modes, and a typo in a path becomes a runtime lookup that
finds nothing rather than a build failure. The rules that do anything interesting — a name through
the transliteration engine, an amount that must refuse four different shapes, a member id that
splits four and three — do not fit `apply` without either a cast or an escape hatch that makes the
interface decorative.

## Decision

`MappingRow` is public, and it is **data**: a source, a target, a direction, a verification flag, a
declared loss and two explanations. It has no `apply`.

The mapping itself is hand-written in `ZenginToPain001` and `Pain001ToZengin`. The two are held
together by tests rather than by being the same object:

- every element the mapper emits has a declared row, and every declared row is emitted;
- every element of an inbound document is carried, declared as something that only exists going the
  other way, or named in the loss report;
- every location a loss entry names is spelled as a declared row spells it.

Each of those found a real defect the first time it ran.

`MappingRegistry.withMapping(...)` and `without(...)` accept overrides, and their documentation is
explicit that a row is a declaration: registering one makes the mapper accept a format id it would
otherwise refuse, and does not change how any field is mapped.

## Consequences

**R-X4 is met in substance and not in mechanism.** `MappingRow` is public and the registry accepts
overrides. Redirecting `EndToEndId` placement — the example the requirement gives — is done with
`MappingContext.endToEndPolicy`, which the mapper acts on, rather than by swapping a rule. A
consumer who wants a genuinely different mapping still has to fork or contribute a declaration; a
rule engine would have let them do it from outside.

**What that costs.** The most likely consumer request this cannot serve is "put 顧客コード2 in
`Purp/Prtry` instead of `RmtInf/Ustrd`". Today that is a pull request. It is worth weighing again if
it is asked for more than once.

**What it buys.** A typo in a declared field name fails the build rather than silently mapping
nothing — `MappingReader` checks every `zengin:` reference against the descriptor. The
`verified: true` flag is enforced against two cited sources, which a runtime rule list could not be.
And there is no path-resolution language to specify, document, and get wrong.

**What would make this wrong.** A second and third format mapping arriving with enough shared shape
that the hand-written mappers become copies of each other. At that point the duplication is the
argument for the engine, and this decision should be superseded rather than worked around.
