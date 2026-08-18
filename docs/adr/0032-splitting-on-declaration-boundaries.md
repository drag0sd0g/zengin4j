# 0032 — The ZEDI envelope is split on declaration boundaries, and the split is checked

**Status:** Accepted
**Requirements:** R-I5, R-I6, R-I7, R-I8

## Context

The ZEDI profile concatenates the `head.001` business application header with the message body at
XML-declaration granularity. A file carrying one payment instruction contains two XML declarations;
one carrying three groups contains six.

> **A ZEDI file is therefore not a single well-formed XML document.** Handing one to an XML parser
> fails on the second declaration, which is why generic ISO 20022 tooling cannot read these files at
> all.

Reading one means scanning the bytes for `<?xml` and cutting there. R-I8 states the safety argument:
the base64 alphabet — `A–Z a–z 0–9 + / =` — does not contain `<`, so the encoded 金融EDI payload
cannot contain the sequence however large it grows.

## Decision

Scan for `<?xml` at the byte level, cut at every occurrence, and parse each segment independently.

**The safety argument is stronger than R-I8 states, and still not a proof — so the split is checked
rather than assumed.**

Stronger: character content cannot contain a literal `<` at all. Well-formed XML requires it escaped
as `&lt;`, so a beneficiary name or a remittance line that reads `<?xml` is not those bytes by the
time it reaches a file. The base64 argument is a special case of that.

Not a proof: neither argument covers comments or CDATA sections, where `<?xml` **is** legal. The
profile uses neither, but "the profile does not do that" is an assumption about other people's
systems.

So every segment must parse as a well-formed document on its own. A false boundary necessarily
produces one that does not — it would end mid-element — and the diagnostic names that possibility
explicitly rather than reporting a generic parse failure.

Writing is concatenation. A message read from a file keeps the slice it was cut from, separator
bytes and all, so R-I6's byte-identical framing is a property of the construction rather than of
getting CRLF placement right. Anything before the first declaration is kept as a preamble, so a byte
order mark survives too.

## Consequences

**Cost.** The whole file is read into memory and every segment is parsed before any is used, so a
damaged segment refuses the file rather than the segment. For a payment file that is the right
trade: partial delivery of a payment instruction is worse than none.

**Benefit.** The claim the README makes on its first screen (R-I9) is true and checked. Two fuzz
targets hold it: one that arbitrary bytes never make the reader misbehave, and one that anything the
reader accepts, the writer reproduces exactly. The second is R-I6 as an invariant over generated
input rather than over the fixtures somebody thought of.

That was worth doing. Between them the two targets found two defects no fixture would have reached:
mixed content, which is legal XML and made the parser throw the exception its *writer* raises for a
mapping mistake; and a malformed DTD, which makes the JDK's own parser raise
`MissingResourceException` from inside its error reporter because the message key it wants is
missing from its bundle. Forty-two bytes for the second one. Both crashing inputs are committed and
replayed on every build.

**What would make this wrong.** A profile variant that puts a comment before the body's declaration,
which would now be refused rather than read. It would be refused *with a diagnostic that names the
cause*, which is the outcome this decision is designed for.
