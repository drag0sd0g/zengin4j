# 0037 — Markdown doc comments, and `import module java.base`

**Status:** Accepted
**Requirements:** R-DOC5, R-0.12, R-MEM2, R-T2
**Follows:** [ADR-0036](0036-java-25-baseline.md)

## Context

[ADR-0036](0036-java-25-baseline.md) moved the baseline to Java 25 and listed the features that
motivated it. Two of them change how every file in the repository looks, so they are worth recording
separately from the version bump that made them legal.

Markdown documentation comments (JEP 467) let a doc comment be written as `///` lines containing
CommonMark instead of `/** */` containing HTML. This is a documentation-heavy codebase — roughly ten
thousand lines of doc comment against a comparable amount of code — and the HTML was doing real
damage to readability: `<p>` on every paragraph, `{@code}` around every identifier, `<ul><li>` around
every list.

Module imports (JEP 511) let `import module java.base;` stand in for the single-type imports of
everything java.base exports. Before the change, 247 files imported at least one java.base type, 879
imports in total.

## Decision

**Every doc comment in the repository is a Markdown doc comment.** No `/** */` remains, including in
the code the generators emit — `RecordSourceGenerator` and its siblings write `///` too, so the
committed generated sources match the convention rather than sitting outside it.

**Every file that uses the JDK declares `import module java.base;`** in place of its single-type
`java.*` imports, as the first line of the import block. Two files add `import module java.xml;`.
One import survives as a single type: `java.lang.management.ManagementFactory`, which is not in
java.base.

The `examples/` programs are compact source files, which import java.base implicitly, so their JDK
imports are simply gone.

## Consequences

**Doc comments cost nothing and read better.** Rendered javadoc is unchanged — `javadoc` runs under
`-Xdoclint:all,-missing` and reports no warning, and the rendered HTML carries the same `<code>`,
`<pre>`, `<ul>` and `<strong>` elements it did before. R-MEM2's bold buffer-recycling warning on
`RecordView` is now `**…**` and still renders bold. The one thing lost is that `{@link}` with a
signature containing `[]` cannot become a `[reference]` — CommonMark would have to parse the
brackets — so the two such links in the codebase stay as taglets. Mixing the two forms in one
comment is legal and reads fine.

**Module imports are the weaker half of this, and the numbers say so.** Of the 247 files that
imported from the JDK, 108 — 43% — imported one type or two. For those, the module import saves no
lines and replaces a precise statement ("this file uses `List`") with a vague one ("this file uses
the JDK"). The 68 files importing five or more are where it pays, and the whole repository saves
about 640 lines. The convention is applied uniformly anyway, because "module import above five
imports, single types below" is a rule a reader cannot infer from any single file, and a convention
nobody can infer is worse than either extreme.

**What this costs on review.** A diff that touches an import block no longer shows which JDK types a
change started using. That information moves to the call site, where a reader has to know that
`Deque` is java.base rather than seeing it declared.

**The way back is mechanical.** `///` to `/** */` and module imports to single types are both
whole-file transforms an IDE performs; neither is load-bearing, which is the property ADR-0036 asked
these features to keep. Nothing in the library's behaviour, API or bytecode depends on either.

**What would make this wrong.** A contributor who finds `import module java.base;` obscures more
than it saves — the argument above concedes it is a real cost for nearly half the files, and the
decision rests on uniformity rather than on the feature earning its place everywhere. The doc
comment half is not close and should stand regardless.
