# Encoding

How text works in fixed-length Zengin files, and where it goes wrong.

This page is about bytes. Every length in these formats is a byte count, every
field boundary is a byte offset, and the difference between a character and a
byte is where most defects in fixed-length parsers live.

> **Scope.** This covers what the library implements today: the permitted
> character sets, the single-byte katakana encoding, and the Shift_JIS/CP932
> divergence. Transliteration from full-width to half-width, and truncation that
> does not orphan a voicing mark, arrive with the transliteration engine in
> Epic 6.

## The one-byte assumption

Every character a Zengin field may contain occupies **exactly one byte** under
the Japanese encodings. That is why the source specifications use 桁
(characters) and バイト (bytes) as though they were the same thing: for
conformant content they are.

The permitted characters all come from JIS X 0201, the single-byte standard:
ASCII digits and uppercase letters in the low range, half-width katakana in
`0xA1`–`0xDF`. No double-byte character is permitted in any field of any format
this library implements.

That assumption is load-bearing, and it is the first thing to break. A 30-byte
受取人名 holds thirty katakana characters — until someone writes the file in
UTF-8, where each katakana character takes three bytes and the same field holds
ten. The library offers `ZenginCharset.UTF_8` because such files exist and
someone has to read them, not because they are valid.

## Half-width katakana, byte by byte

| Bytes | Contents | Permitted? |
|---|---|---|
| `0xA1`–`0xA5` | `｡` `｢` `｣` `､` `･` | Only `｢` and `｣`, and only in EDI information |
| `0xA6` | `ｦ` | Only in EDI information |
| `0xA7`–`0xAF` | Small kana `ｧｨｩｪｫｬｭｮｯ` | **Never** |
| `0xB0` | `ｰ` prolonged sound mark | **Never** — see below |
| `0xB1`–`0xDD` | `ｱ` through `ﾝ` | Yes |
| `0xDE` | `ﾞ` voiced sound mark (濁点) | Yes |
| `0xDF` | `ﾟ` semi-voiced sound mark (半濁点) | Yes |

### The long vowel mark is not a permitted character

`ｰ` (`0xB0`, 長音) is excluded from every field class. A long vowel is written
with `-` (`0x2D`, ハイフン).

This is the single most common mistake in hand-entered katakana, and the reason
it survives review is that the two glyphs are nearly indistinguishable:

```
ﾔﾏﾀﾞｰﾀﾛｳ    ← 0xB0, rejected
ﾔﾏﾀﾞ-ﾀﾛｳ    ← 0x2D, accepted
```

The file looks correct to a human reader, passes any check that only counts
bytes, and is refused by the bank. `CharacterSet.validate` reports it by offset
and names the fix.

### A voicing mark is its own character and its own byte

`ｶﾞ` is not one character. It is `ｶ` (`0xB6`) followed by `ﾞ` (`0xDE`) — two
code points, two bytes, one apparent glyph.

Everything follows from that:

- `ﾔﾏﾀﾞ ﾀﾛｳ` is **eight** bytes, not seven, though it reads as seven characters.
- Truncating a name to fit a field can cut between a kana and its voicing mark,
  turning ガ into カ — a different character, and in a payee name a different
  person.
- `String.length()` is never the right measurement. The codec counts bytes
  throughout (R-C15).

Composed forms (`ガ` U+30AC as a single full-width character) are not permitted
and do not appear in conformant files.

## Permitted characters, by field class

There is no single permitted set. The standard states a base set and narrows it
by what the field *is*, and the narrowing is not decorative — a branch name
containing a full stop is invalid, and the same full stop in a payee name is
fine.

| Class | Kana | `A`–`Z` | Symbols |
|---|---|---|---|
| **Bank and branch names** (店舗名) | no `ｦ`, no small | yes | `-` |
| **Party names** (口座名・受取人名・委託者名) | no `ｦ`, no small | yes | `(` `)` `-` `.` |
| **Payroll and bonus names** (給与・賞与振込) | no `ｦ`, no small | **no** | none |
| **EDI information** (EDI情報) | `ｦ` allowed, no small | yes | `\` `｢` `｣` `(` `)` `-` `/` `.` |

Digits and space are permitted throughout. A comma is permitted nowhere.

Two consequences worth stating on their own:

- **給与振込 forbids Latin letters entirely.** A validator built around 総合振込
  would pass `ABC` in a payroll payee name and the file would be rejected.
- **`ｦ` is permitted only in EDI information.** Several institutions state that
  they do not accept it there either.

Each field declares its class in the format descriptor, so the check follows the
field rather than the format. Fields whose class the sources do not state —
顧客コード, filler, reserved space — are unconstrained: inventing a rule for
them would produce false findings, and filler in particular must survive a round
trip byte for byte whatever it contains (R-D5).

## Shift_JIS and CP932

`ZenginCharset` offers `SHIFT_JIS` and `MS932` (CP932, Microsoft's superset).
They are not the same encoding, and the difference is a well-known source of
corruption — but **not for conformant Zengin content**.

Every divergence is in the *double-byte* range. Single-byte katakana decodes
identically under both:

| Byte | Shift_JIS | CP932 |
|---|---|---|
| `B6` | `ｶ` U+FF76 | `ｶ` U+FF76 |
| `DE` | `ﾞ` U+FF9E | `ﾞ` U+FF9E |

Since no double-byte character is permitted in any field, **a conformant file
decodes to the same text under either choice.** The setting matters only for
files that are already invalid — which is exactly when you are reading them.

Where they differ, they differ like this (verified against the JDK's own
implementations, and asserted by
`EncodingMatrixTest` so this table cannot drift):

| Bytes | Shift_JIS | CP932 |
|---|---|---|
| `81 60` | `〜` U+301C wave dash | `～` U+FF5E fullwidth tilde |
| `81 61` | `‖` U+2016 double vertical line | `∥` U+2225 parallel to |
| `81 7C` | `−` U+2212 minus sign | `－` U+FF0D fullwidth hyphen-minus |
| `87 40` | unmappable | `①` U+2460 |
| `87 54` | unmappable | `Ⅰ` U+2160 |
| `87 82` | unmappable | `№` U+2116 |

The wave dash is the famous one. A character that round-trips through one
encoding and not the other will be replaced with `?` (`0x3F`) — silently, and
in both directions.

`MS932` is the default because it is what Windows-based Japanese accounting
systems emit in practice, and because being the superset it fails to decode
less.

## Choosing an encoding

| You have | Use |
|---|---|
| A file from a Japanese accounting package | `MS932` (the default) |
| A file you know is strict Shift_JIS | `SHIFT_JIS` |
| A file some tool re-encoded | `UTF_8`, and expect field boundaries to be wrong |

If you do not know: use the default. For conformant content the choice does not
change the result, and for non-conformant content `MS932` decodes strictly more
of it.

## EBCDIC

コード区分 `1` declares EBCDIC. This library does not implement it and does not
guess: a file declaring it is refused by name with
`UnsupportedEncodingVariantException` (R-C14).

Decoding EBCDIC as though it were JIS would not fail. Every byte would produce
some plausible character, every field would contain something that looked like
text, and nothing downstream would indicate a problem. Refusing is the only
honest option. See
[ADR-0010](adr/0010-ebcdic-rejection-pulled-forward.md).

## Sources

The permitted sets are drawn from 全国銀行協会 付録1 使用文字一覧 and
corroborated against two independent institution publications; the citations,
with retrieval dates, are in [SOURCES.md](SOURCES.md). Where the sources
diverge — and on one point about `ｦ` they do — the reading implemented and the
reasoning are in [DISCREPANCIES.md](DISCREPANCIES.md).
