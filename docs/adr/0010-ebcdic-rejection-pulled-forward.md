# 0010 — EBCDIC detection pulled forward from Epic 3

**Status:** Accepted
**Requirements:** R-C14, §0.2, §0.6

## Context

コード区分 value `1` means EBCDIC. R-C14 requires that such a file be detected and rejected with a
named exception — "never mis-decode as JIS" — and the work breakdown assigns that to Epic 3
(issue 3.3).

Epic 1 nonetheless reads the コード区分 field, because it is part of the header layout. Without the
check, a file declaring EBCDIC would be decoded as MS932: every text field would produce plausible
but wrong characters, and nothing downstream would indicate a problem. That is the failure mode §0.2
describes as worse than an admitted gap.

## Decision

Implement the detection now. After the format is resolved, the reader peeks the header's
`CODE-KUBUN` field — whichever field declares that interpretation, whatever its id — and raises
`UnsupportedEncodingVariantException` naming the value and its byte offset if it is `1`.

The check is skipped when there are not enough bytes to read the field, or when the first record is
not a header, so it never turns a framing problem into an encoding diagnostic.

Full EBCDIC *support* remains out of scope, as the specification says.

## Consequences

**Cost.** Ten lines of Epic 3's work land in Epic 1, and issue 3.3 is partly done ahead of its
milestone.

**Benefit.** No release of this library, however early, silently mis-decodes an EBCDIC file. The
cost of the check is one field read per file.

**Note.** An unrecognised コード区分 — neither `0` nor `1` — maps to `CodeKubun.UNKNOWN` and is
*not* rejected. The list is open (§0.6): the two known values are believed rather than confirmed,
and refusing a third would assert knowledge this project does not have.
