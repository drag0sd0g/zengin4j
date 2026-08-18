# Loss

Converting between a Zengin fixed-length file and ISO 20022 always loses something. This page says
what, why, and what you can do about each one.

It is the page to read before deciding whether to automate a conversion, and the page to read when a
loss report says something you were not expecting.

## The formats are not isomorphic

Not "nearly isomorphic with a few edge cases". The two describe the same payments with different
amounts of room:

| | ISO 20022 | Zengin | What has to give |
|---|---|---|---|
| Beneficiary name | 140 characters, any script | 30 bytes, half-width katakana | Transliteration, then truncation |
| Originator name | 140 characters | 40 bytes | The same, with more room |
| Execution date | A full date | `MMDD` | The year, in one direction; invented in the other |
| Reference (`EndToEndId`) | 35 characters, mandatory | No field for it | Squeezed into a 顧客コード, or dropped |
| Remittance information | Rich, structured, repeating | Two 10-byte code fields | Almost all of it |
| Currency | Any ISO 4217 | JPY, implicitly | Nothing else is representable |
| Postal address | Fully structured | Absent | All of it |
| Purpose code | An external code list | Absent | All of it |
| 振込指定区分, 新規コード, 手形交換所番号 | Absent | Present | All of them, going up |

So an API that let you convert and then look at the result would be lying by omission. This one does
not have one: `Iso20022Mapper` returns a `MappingResult`, which is the output **and** the report, and
there is no method anywhere that hands back the output alone (R-I14). A test asserts that by
reflection, so a helpful convenience method cannot quietly be added later.

## The vocabulary

Every entry names a kind and a severity.

| Kind | Means |
|---|---|
| `TRUNCATED` | The value did not fit, and was cut. |
| `TRANSLITERATED` | The value was rewritten in a different script or width. |
| `DROPPED` | The value has no home on the other side and was not carried. |
| `DEFAULTED` | The other side needs a value the source does not supply, so one was chosen. |
| `COERCED` | The value was forced into a shape that does not really hold it. |

| Severity | Means | Example |
|---|---|---|
| `INFORMATIONAL` | Cosmetic. Nothing reconciles differently. | `ﾔﾏﾀﾞ` widened to `ヤマダ` for display. |
| `MATERIAL` | A party or a reference is noticeably altered. | A name cut from thirty-eight bytes to thirty. |
| `CRITICAL` | The payment could mean something else, or reach somewhere else. | An amount in EUR carried into a field that can only mean JPY. |

The ordering is the semantics: `hasAtLeast(MATERIAL)` is true for anything material or worse. That is
what the refusal threshold is compared against.

## A critical loss stops the conversion

By default, a conversion **refuses** rather than returning a result that could misroute money:

```java
MappingContext context = MappingContext.builder("9900000001", LocalDate.of(2026, 9, 1))
        .targetFormat(descriptor)
        .build();                       // failOnSeverity defaults to CRITICAL

Iso20022Mapper.create().toZengin(file, context);   // throws MappingFailedException
```

R-I14 makes the report impossible to *miss* by putting it in the return type. This makes the worst
class of loss impossible to *ignore*. To convert anyway:

```java
        .acceptAnyLoss()                // the report is unchanged; only the refusal goes away
```

`dryRun` and `roundTrip` never refuse, whatever the threshold says. Their whole purpose is to show
you the loss, and stopping at the first critical entry would hide the rest of the answer.

See [ADR-0033](adr/0033-critical-loss-fails-by-default.md).

## What each loss actually is

### Names

A beneficiary name is where this conversion does its real damage — and it is damage to *who gets
paid*, not to how much.

Going up, half-width katakana widens for display. That is reversible for kana and is reported
`INFORMATIONAL`; it is reported even though nothing is lost, because the resulting string is not the
one in the file, and a report that mentions only damage cannot be used to explain a difference.

Going down is the hard direction:

- **Kanji is refused, not guessed.** 東 reads ヒガシ, トウ or アズマ depending on whose name it is,
  and a wrong reading is a wrong payee. No reading dictionary is bundled and none will be.
- **A name that does not fit is refused by default.** `TruncationPolicy.TRUNCATE_SAFE` cuts instead,
  and never separates a kana from its voicing mark — `ｶﾞ` is two bytes, and a cut between them turns
  ガクブチ into カクブチ with nothing in the file to show for it.
- **A long vowel has no legal form in a payroll name.** `ー` becomes `-`, and 給与振込 names admit no
  symbols at all, so ヨーコ can be written into a 総合振込 file and not into a 給与振込 one. That is
  an open question, not a settled design — see the Epic 6 entries in
  [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).

### Dates

振込指定日 is `MMDD`. Going up, a year is supplied from `MappingContext.referenceDate` and reported
`DEFAULTED`/`MATERIAL` **every time**, including when the answer is obviously right: a correct guess
is still a guess, and a report that only mentions the surprising cases teaches a reader that silence
means certainty.

Going down, the year is dropped and reported `DROPPED`. The reader of the resulting file has to
supply one again, and may supply a different one.

If neither the reference year nor the next is a leap year, `0229` resolves to nothing at all
(ADR-0014 — a leap day is never resolved backwards). The reference date is used and the entry is
`CRITICAL`: a payment file whose execution date was made up is one the bank decides the timing of.

### Amounts

Several things a `pain.001` can legitimately say that 振込金額 cannot hold. All are `CRITICAL`, and
none is rounded or truncated into something plausible:

| What the document says | What happens |
|---|---|
| A currency other than JPY | The figure is carried across unconverted, which makes it a different amount of money |
| A fraction of a yen | Discarded, not rounded |
| More digits than the field holds | Written as `0` — the leading digits of an amount *are* the amount, and cutting them produces a plausible payment for the wrong sum |
| A negative amount | Written as `0` — a credit transfer of a negative amount is not a debit, it is not expressible |
| An amount that could not be read at all | Written as `0`, and reported as unread rather than as zero |
| An identifier longer than its field | Not written — see below |

Silently turning 1000.50 EUR into 1000 JPY is precisely the class of mistake this module exists to
make visible. Writing `0` is not a repair either: under the default threshold the conversion refuses
and nobody sees it, and under `acceptAnyLoss` the report says exactly which payment is wrong.

The last row has a second job. `xs:decimal` admits `1e2000000000` — thirteen bytes that parse in
microseconds and exhaust a heap the moment anything renders them. An amount with more than thirty
integer digits is refused at the parse boundary and becomes ISO 4217's `XXX`, "no currency
involved", which is exactly what a figure nobody could read is.

### Identifiers, which are never shortened

ISO 20022 gives an account number thirty-four characters and a clearing-system member id
thirty-five. The Zengin fields are seven, four and three. A sender can legitimately fill them.

Such a value is **not written at all**, and the entry is `CRITICAL`. Half an account number is a
different account, and a file carrying one looks perfectly valid — no validation rule objects to a
well-formed seven-digit number that happens to be the wrong account.

Not writing it is not safe either. A numeric field that was never set holds its padding, which is
zeros: `0000000` is a well-formed account number too. That is survivable only because the entry is
`CRITICAL` and the default threshold stops the conversion before anybody sees the file. Passing
`acceptAnyLoss` here gets you a file with a zeroed identifier and a report naming the payment.

預金種目 is the same shape of problem in miniature: `Tp/Prtry` is thirty-five characters and the
field is one. Anything longer is reported `CRITICAL` and 普通預金 (`1`) is assumed, because an
account type that is wrong sends the payment to a different account at the same branch.

### The document disagreeing with itself

`GrpHdr/NbOfTxs` and `GrpHdr/CtrlSum` are computed from the payments when writing, and compared
against the payments when reading. A `pain.001` whose header disagrees with its own contents is
exactly as suspect as a Zengin file whose trailer does, which `V-301` and `V-302` have caught since
Epic 4.

The payments are what gets converted, because they are what the money is. The disagreement is
`CRITICAL`: once the two differ neither number can be trusted, and the wrong one might be the one
somebody reconciles against.

### References, and why there is no good answer

`EndToEndId` is mandatory in ISO 20022, is 35 characters, and is what the debtor and creditor
reconcile against. The Zengin formats have no field for it. The nearest thing is a 顧客コード — ten
bytes, and already carrying whatever the originator puts there.

`EndToEndIdPolicy` makes you choose, because there is no default that is right:

| Policy | Reference goes to | Remittance goes to | What it costs |
|---|---|---|---|
| `CUSTOMER_CODE_1` | 顧客コード1 | 顧客コード2 | Truncation on the way down, reported `CRITICAL`. A cut reference looks usable and matches the wrong payment. |
| `CUSTOMER_CODE_2` | 顧客コード2 | 顧客コード1 | The same, for the other field. |
| `DROP` | nowhere | 顧客コード2 | Reported `MATERIAL`, and honest: the creditor has nothing to reconcile against. |

**Both 顧客コード are carried under every policy**, because whichever one is not holding the
reference goes to the remittance information. Sending a fixed one regardless would drop the other
silently, which is what `CUSTOMER_CODE_2` did until a coverage gap pointed at it.

Going up, a reference that was never supplied is written as `NOTPROVIDED` — the value the standard
defines for exactly that — and is *not* reported as lost, because nothing was.

### Remittance information, and `remt.001`

This is the trade-off worth stating plainly, because the profile made a choice and it is not the only
one available.

ISO 20022 has `remt.001`, a standalone remittance advice: structured, typed, and readable by any
system that knows the standard. The ZEDI profile does not use it. It base64-encodes an XML payload,
wraps it in three MIME headers, splits the encoding at 76 characters, and puts each line in its own
`Ustrd` element inside the payment.

Neither choice is wrong. `remt.001` is more interoperable and needs a second message with its own
routing; the encoded payload keeps everything in one message at the cost of being opaque to anything
that does not know the convention. This library implements what the profile does, models it as a
typed `EdiAttachment` rather than an opaque string, and preserves the encoding **exactly** — the
line splitting and the padding included, because re-encoding the same bytes can produce different
XML and would break a byte-identical round trip (R-I12).

Going down, the payload is **not carried**: 顧客コード has twenty bytes at most, and a fragment of a
base64 encoding is not a shorter version of it. Reported `DROPPED`/`MATERIAL`.

### Records that could not be read

Reading in lenient mode surfaces a record that does not fit the format as data rather than failing
the whole file (R-D8), so one bad record does not hide the other 9,999. The conversion has nothing
to map those to — a record whose fields could not be located has no beneficiary and no amount — so
they do not appear in the message.

That is `CRITICAL`, reported once with a count. A malformed record may well be a payment, and a
payment that silently fails to appear is money that does not move with nothing to show for it. Under
the default threshold it stops the conversion, which is right: a file that could not be read whole
should not be converted in part.

### Several `PmtInf` blocks, one batch

A Zengin batch has one execution date, one debit account and one originating bank. Going down, every
`PmtInf` in the document becomes payments in a single batch.

When the blocks agree on all three, only the grouping is lost — `INFORMATIONAL`. When they disagree,
the first block's values are applied to *every* payment in the file, including payments that asked
for a different date or a different account. That is `CRITICAL`, and it is not a subtle difference
to bury in a note about structure.

### Fields with nowhere to go

Going up: 手形交換所番号, 新規コード, 振込指定区分, 識別表示, the branch names, コード区分. Going
down: `PmtId/InstrId`, the debtor's own reference to its own bank — distinct from `EndToEndId`,
which is the one the creditor reconciles against, and with nowhere to go because both 顧客コード
fields are already spoken for.

Each is reported once per file rather than once per record — the fact is a
property of the mapping, not of any particular payment, and thirty thousand identical lines would
bury the one that matters.

Coming back, 振込指定区分 takes the field's numeric default of 0 and is reported `DEFAULTED`. Several
institutions document that as the required value for an unused field; the bundled code list carries
7 and 8, so `V-205` will note it as outside the list. Both are right, and the report says which you
are looking at.

## Reading a report

```java
MappingResult<ZediFile> result = Iso20022Mapper.create().toIso(file, context);

result.loss().isLossless();                       // almost never true
result.loss().hasAtLeast(LossSeverity.MATERIAL);  // what to branch on
System.out.print(result.loss().toText());         // or toText(Locale.JAPANESE)
System.out.print(result.loss().toJson());         // for a pipeline
```

From the command line, [`zengin dryrun`](cli.md) answers "what would this cost?" without producing a
file — run it over a month of real files before committing to an integration. That is a better basis
for a decision than a mapping table.

## The round trip

`roundTrip` converts a file to ISO 20022 and back, and returns everything both legs lost (R-I18):

```java
RoundTripResult round = Iso20022Mapper.create().roundTrip(file, context);

round.isByteIdentical();   // false, and that is the point
System.out.print(round.loss().toText());
```

It is meant to be run rather than argued about. A name loses its kanji on the way out and does not
get them back; a year is invented on the way out and dropped on the way in; a branch name goes and
does not return. Every difference between the file that went in and the file that came back has a
line in the report — and a file that came back is still one a bank would accept, which a test
asserts.

## Where the rows are

Every correspondence, in both directions, with what each one costs, is generated from the mapping
declarations into [mapping.md](mapping.md). **No row of it is verified** (R-I19): none has been
checked against published profile documentation, and the most load-bearing unverified value is the
clearing-system identifier `JPZGN` — see Q8 in [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).
