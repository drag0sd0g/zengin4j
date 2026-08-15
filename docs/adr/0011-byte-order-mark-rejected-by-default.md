# 0011 — A byte order mark is rejected by default

**Status:** Accepted
**Requirements:** R-C10, §0.6

## Context

R-C10 says to detect a UTF-8 byte order mark at the start of a file and "either reject or strip with
a warning". It does not say which is the default, and the two differ in what they assume about the
rest of the file.

A byte order mark is never valid in a fixed-length Zengin file. Its presence means the file has been
through a text editor — and an editor that added a byte order mark has usually also re-encoded the
content, quite possibly from MS932 to UTF-8. Under UTF-8 every half-width katakana character
occupies three bytes instead of one, so a 30-byte name field holds ten characters rather than
thirty, and every field offset after the first name is wrong.

Stripping the mark and continuing would produce records that parse cleanly and are misaligned.

## Decision

`ByteOrderMarkPolicy.REJECT` is the default: `MalformedFileException` at byte 0, naming the mark,
explaining what it usually indicates, and naming the option that skips it.

`ByteOrderMarkPolicy.STRIP` skips the three bytes and raises a `ZenginWarning` whose text says the
content may have been re-encoded and that the text fields are worth checking. The framing records
that a mark was present, so a writer can reproduce the file byte for byte.

## Consequences

**Cost.** A caller with a mark-prefixed file that is otherwise correct has to opt in. That is one
builder call, and it puts a note in their code about a file that came from somewhere unusual.

**Benefit.** The default fails loudly on a file whose encoding is in doubt, rather than producing
plausible misaligned records. §0.6 asks for the more conservative reading where the specification is
ambiguous; this is it.
