# 0005 — Record equality is defined by raw bytes

**Status:** Accepted
**Requirements:** R-D2, R-D5, INV-2

## Context

Every record retains its raw bytes so that unknown, reserved and filler bytes survive a round trip
verbatim (R-D5). A Java `record` with a `byte[]` component gets an `equals` that compares that array
by *identity*, which means two records read from the same file would not be equal — useless for
INV-2, which asks that `read(write(file))` produce an equal `ZenginFile`.

The generated types also carry `recordNumber` and `byteOffset`. Those describe where a record was
found, not what it is.

## Decision

Generated records override `equals` and `hashCode` to compare the record type and the raw bytes,
and nothing else:

```java
public boolean equals(Object other) {
    return other instanceof SougouFurikomiData record && Arrays.equals(rawBytes, record.rawBytes);
}
```

The decoded components are not compared, because they are derived from those same bytes. Position is
not compared, because the second payment in one file and the second payment in a copy of it are the
same record.

## Consequences

**Cost.** Two records with identical bytes decoded under different character sets compare equal
while their decoded fields differ. In practice a file is read with one charset throughout, so the
situation does not arise; the Javadoc says so rather than leaving it to be discovered.

**Benefit.** Equality means what a payments engineer expects — same bytes, same record — and INV-2
becomes expressible. It is also fast: one `Arrays.equals` over 120 bytes instead of sixteen string
comparisons.

**What would make this wrong.** If a future record type carried state that was *not* derived from
its bytes — a resolved date, say, or a validation outcome — this definition would start hiding
differences. Such state does not belong on a record type for exactly that reason.
