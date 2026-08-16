# Disclaimer

**zengin4j is not certified by 全国銀行協会 (the Japanese Bankers Association),
全銀ネット (the Japanese Banks' Payment Clearing Network), or any financial
institution.** Nothing in this project implies review, approval or endorsement
by any standards body or bank.

## What this means in practice

Every format descriptor shipped in this release is marked `verified: false`.

That does not mean nobody has checked. The 総合振込 layout has since been
compared field by field against six independent published sources, including
the JBA's own protocol document, and **they agree on every offset and every
length** — the citations are in [docs/SOURCES.md](docs/SOURCES.md). What holds
the flag at `false` is that those sources disagree about one field's
*attribute*, recorded as [D-002](docs/DISCREPANCIES.md), and the project's
rules keep a format unverified until such a disagreement is settled.

The library still refuses to read a file with an unverified descriptor unless
you say, in your own code:

```java
ReaderOptions.builder().allowUnverifiedFormats(true)
```

That opt-in exists so that the decision to trust a provisional layout is
recorded where a reviewer can see it, rather than assumed on your behalf.

Building a file requires the same acknowledgement, separately:

```java
ZenginFileBuilder.forFormat(descriptor).allowUnverifiedFormats(true)
```

Both exist for the same reason, and the second matters more than the first. A
wrong offset when reading gives you wrong data inside your own system, where
your own checks may catch it. A wrong offset when writing sends a wrong payment
instruction to a bank, where they will not.

Writing a file you have just *read* needs no further opt-in: the bytes already
existed and are reproduced exactly, so the round trip introduces no risk the
read did not already carry.

**A wrong byte offset in a payment file produces silently corrupted financial
instructions.** Nothing downstream will flag it. Before using output from this
library in production:

1. Obtain the record format specification from the institution you are sending
   to or receiving from.
2. Check it against `docs/formats/`, which is generated from the descriptors
   this library actually uses.
3. Validate real output byte by byte against a file your institution has
   accepted.

You are responsible for that validation. This library's job is to be honest
about what it does and does not know; it is not a substitute for your
institution's specification.

## Reporting a discrepancy

If you find a field this library places differently from your institution's
specification, please open an issue with the field name, the offsets both
documents give, and a citation for the source. Discrepancies are recorded in
[docs/DISCREPANCIES.md](docs/DISCREPANCIES.md) with both readings, and the
more conservative one is implemented until the question is settled.
