# The `zengin` command

Reads, checks and explains 全銀協規定形式 fixed-length payment files.

> `CliReferenceTest` fails the build when this page and the parser disagree, so
> the commands, options and exit codes here are the ones you will actually get.

```
zengin validate <file>  [--format=ID] [--charset=NAME] [--suppress=V-306,V-605]
                        [--out-format=text|json|sarif] [--calendar=FILE|bundled]
                        [--language=en|ja] [--allow-unverified] [--lenient]
                        [--unsafe-print]
zengin inspect  <file>  [--annotate] [--record=N] [--out-format=text|json]
                        [--format=ID] [--charset=NAME] [--allow-unverified]
                        [--lenient] [--unsafe-print]
zengin generate         [--format=ID] [--count=N] [--seed=N] [--separator=STYLE]
                        [--eof-byte] [--out=FILE] [--out-format=text|json]
                        [--list-formats]
zengin diff     <a> <b> [--out-format=text|json] [--format=ID] [--charset=NAME]
                        [--allow-unverified] [--lenient] [--unsafe-print]
zengin explain          [--format=ID] [--field=ID] [--record=KIND]
                        [--out-format=text|json]
zengin convert  <file>  --to=pain.001|zengin [--out=FILE] [--as-of=YYYY-MM-DD]
                        [--originator-code=CODE] [--target-format=ID]
                        [--message-id=ID] [--receiver=ID] [--truncate=POLICY]
                        [--unmappable=POLICY] [--end-to-end=POLICY]
                        [--accept-loss] [--loss-format=text|json] [--loss-out=FILE]
                        [--language=en|ja] [--format=ID] [--charset=NAME]
                        [--allow-unverified] [--lenient]
zengin dryrun   <file>  [--to=pain.001] [--out-format=text|json]
                        [--as-of=YYYY-MM-DD] [--originator-code=CODE]
                        [--target-format=ID] [--message-id=ID] [--receiver=ID]
                        [--truncate=POLICY] [--unmappable=POLICY]
                        [--end-to-end=POLICY] [--accept-loss]
                        [--language=en|ja] [--format=ID] [--charset=NAME]
                        [--allow-unverified] [--lenient]
```

`convert` and `dryrun` arrived in Epic 7 with the ISO 20022 module. §27 sketches
their settings as a YAML context file; they are flags instead, for the reason in
[ADR-0034](adr/0034-the-mapping-context-is-flags-not-a-file.md).

## Running it

```sh
./gradlew :zengin4j-cli:shadedJar
java -jar zengin4j-cli/build/libs/zengin4j-cli-*-all.jar --help
```

A shell alias makes the rest of this page read as written:

```sh
alias zengin='java -jar /path/to/zengin4j-cli-all.jar'
```

There is also a `nativeImage` task, which needs a GraalVM JDK and produces a
binary that starts in milliseconds rather than in JVM startup time. It is not
part of `build`; see [ADR-0024](adr/0024-picocli-for-the-cli.md).

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Clean. |
| `1` | Warnings only — nothing that blocks submission. `diff` also uses this when the files differ. |
| `2` | Errors. The file is not fit to send. |
| `3` | Usage error: unknown option, no such format, no such field. |
| `4` | A file could not be read or written. |

**`1` for warnings alone is deliberate and unusual.** Most tools exit `0` when
they merely have something to say; a payment file is not most things. The
reasoning is in [ADR-0025](adr/0025-warnings-exit-non-zero.md). A pipeline that
has decided its warnings are acceptable says so in one line:

```sh
zengin validate payments.txt || [ $? -eq 1 ]
```

and a team that disagrees with one specific rule suppresses it by id, which is
better because it names which one:

```sh
zengin validate payments.txt --suppress=V-605
```

## Nothing prints an account number by default

Every command that shows record contents masks the fields the descriptors mark
sensitive — in the bundled formats, the account numbers. Their **hex is masked
too**, because hex of an account number is an account number to anyone reading a
byte dump.

`--unsafe-print` turns that off, is spelled that way on purpose, and prints a
warning to stderr so it survives `> dump.txt`. What exactly it gates, and why
names and amounts are not in the same category, is
[ADR-0026](adr/0026-what-unsafe-print-actually-gates.md).

Masking never suppresses a check. A masked field is still validated against its
type, character class, constant and code list — a defect in a field you cannot
see is the one you most need told about.

## Every bundled format needs `--allow-unverified`

No bundled layout is confirmed by two independent published sources, so reading
one without the flag fails rather than quietly proceeding. With the flag, a
warning goes to stderr on every read. See
[DISCLAIMER.md](../DISCLAIMER.md) and `zengin explain --format=ID` for what each
layout is based on.

---

## `validate`

Checks a file against the rule set and reports what is wrong with it.

```sh
zengin validate payments.txt --allow-unverified --calendar=bundled
```

```
WARNING V-306 record 3 byte 244: This payment is identical to the one in record 2:
  same bank, branch, account and amount. That is legal, and is usually a duplicated row.
ERROR V-301 record 5 byte 488 [totalAmount]: Trailer total is 999,999 but the batch's
  payments add up to 300,000, a difference of 699,999.

1 error(s), 1 warning(s), 0 info. Not submittable while errors remain.
```

- `--calendar` switches on the `V-5xx` date rules, which are off entirely
  without it. `bundled` uses the built-in Japanese bank calendar; anything else
  is read as a holiday CSV of your own — one `YYYY-MM-DD,name` per line plus a
  required `horizon=YYYY-MM-DD`.
- `--suppress` takes rule ids, comma-separated. Every id, its default severity
  and what it checks are in [validation-rules.md](validation-rules.md).
- `--language=ja` renders findings in Japanese; the default follows the JVM
  locale.
- `--out-format=sarif` produces annotations that GitHub, GitLab and Azure DevOps
  render against the file itself.

In CI:

```sh
zengin validate payments.txt --allow-unverified --out-format=sarif > zengin.sarif
```

## `inspect`

Shows what is actually in the bytes. The tool to reach for when a bank rejects a
file and the rejection notice says something unhelpful.

```sh
zengin inspect payments.txt --annotate --record=2 --allow-unverified
```

```
record 2  DATA  byte 122
  off   len T  field                  項目名          name                     hex                  value
  0     1   N  dataKubun              データ区分      Record Type              32                   2           ok
  1     4   N  beneficiaryBankCode    被仕向銀行番号  Beneficiary Bank Code    39 39 39 39          9999        ok
  5     15  C  beneficiaryBankName    被仕向銀行名    Beneficiary Bank Name    C3 BD C4 B7 DE DD …  ﾃｽﾄｷﾞﾝｺｳ    ok
  43    7   N  accountNumber          口座番号        Account Number           (masked)             ***6543     ok
  50    30  C  beneficiaryName        受取人名        Beneficiary Name         D3 BC DE 20 BC D6 …  ﾓｼﾞ ｼﾖｳ     ok
  80    10  N  amount                 振込金額        Transfer Amount          30 30 30 31 35 30 …  0000150000  ok
```

Both names are there because R-CLI5 asks for both, and because the id is not a
substitute: it diverges from the English name for eight of the fifty-two bundled
fields — `dataKubun` is "Record Type", `dummy` is "Filler", `amount` is
"Transfer Amount".

The last column is the reason to run it: a file is usually rejected over one
field, and a column of `ok` with one entry pointing at a problem is the fastest
way to find it. A bad field reads, for example:

```
  50    30  C  beneficiaryName        受取人名        Beneficiary Name         D3 B0 DE 20 BC D6 …  ﾓｰﾞ ｼﾖｳ     <- byte 0xB0 at offset 51 is not permitted in account and party names; the long vowel mark ｰ is never permitted — write a long vowel as - (0x2D)
```

Without `--annotate` it prints the file's shape only: format, record count,
separator convention, whether it round-trips byte-for-byte.

Control bytes in a field print as `␍`, `␊` and friends rather than as
themselves. A record whose fields have slipped out of alignment — the case this
command exists for — carries the file's own separators inside a field, and a raw
`0x0D` would tear the table in half.

## `generate`

Writes a synthetic file. **Every value is invented** — bank `9999`, branch
`999`, accounts beginning `9`, obviously fictional names.

```sh
zengin generate --format=kyuyo-furikomi --count=100 --seed=42 --out=payroll.txt
```

The same seed produces the same bytes on every platform and every JDK, so a
generated file can be committed as a fixture and regenerated years later to the
byte. `--list-formats` shows what it can produce; all four bundled formats are
covered. With no `--out` it writes the file to stdout.

## `diff`

What changed between two files, field by field.

```sh
zengin diff before.txt after.txt --allow-unverified
```

```
~ record 2
    accountNumber (口座番号) byte 43, first differs at 45: '***9485' -> '***4930'
    amount (振込金額) byte 80, first differs at 83: '0001387164' -> '0002688956'
~ record 4
    beneficiaryName (受取人名) byte 50: 'ﾃｽﾄ ｶﾞｸﾌﾞﾁ' -> 'ｶﾅ ﾊﾟﾋﾟﾌﾟﾍﾟﾎﾟ'
~ record 5
    totalAmount (合計金額) byte 7, first differs at 12: '000014308193' -> '000012320259'

3 changed, 0 added, 0 removed.
```

`byte 43` is where the field starts; `first differs at 45` is the byte to look
for in a hex dump, and is omitted when the field differs from its first byte.
The JSON form carries both as `offset` and `firstDifferingByte`.

A textual diff of a fixed-length file tells you that record 2 changed and
nothing else, because the record is one line and every byte of it is on that
line. Records are aligned by longest common subsequence rather than by position,
so inserting a payment near the top does not report every later record as
changed — [ADR-0027](adr/0027-diff-aligns-records-rather-than-positions.md).

Exits `1` when the files differ, like `diff(1)`. Two files of different formats
are refused rather than diffed.

Records matching at the start and end of both files are discounted before
anything is aligned, so a one-payment edit in a file of thousands costs almost
nothing. Files that differ *throughout* at that size are refused with a message
rather than attempted — see
[ADR-0027](adr/0027-diff-aligns-records-rather-than-positions.md) for why the
refusal matters more than the limit.

## `convert`

Converts a Zengin file to ISO 20022 `pain.001`, or a `pain.001` back to a Zengin
file, and says what the conversion cost.

```sh
zengin convert payments.txt --to=pain.001 --as-of=2026-09-01 \
    --allow-unverified --out=payments.xml
```

```
INFORMATIONAL TRANSLITERATED [CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Cdtr/Nm]:
  'ﾔﾏﾀﾞ ﾀﾛｳ' was widened to 'ヤマダ　タロウ' for display. Widening is not
  reversible: several full-width characters narrow to the same half-width
  sequence.
MATERIAL DEFAULTED [CstmrCdtTrfInitn/PmtInf/ReqdExctnDt]:
  振込指定日 carries no year; 2026 was supplied from the reference date.
INFORMATIONAL DROPPED:
  新規コード says whether this beneficiary is new or changed since the last file —
  a statement about the originator's own history, not about the payment.
```

The bracketed name is spelled exactly as [mapping.md](mapping.md) spells it, so a
line here can be looked up there.

**The loss report is always produced.** There is no flag that turns it off — the
library has no API that returns converted output without it (R-I14), and a
command that discarded it would undo that at the last step. `--loss-out` says
where it goes.

By default it goes to stderr and the converted file goes to `--out`, or to
standard output when there is none, so `> out.xml` produces a usable file and
still tells you what it cost. The reader's own warnings go to stderr as well —
fine for reading, no good for parsing, which is what `--loss-out=report.json`
is for.

Going the other way needs three things the XML cannot supply (R-I20):

```sh
zengin convert payments.xml --to=zengin \
    --originator-code=9900000001 --target-format=sougou-furikomi \
    --as-of=2026-09-01 --out=payments.txt
```

- `--originator-code` is 委託者コード. An initiating party identifier in the XML
  is not required to be the code the receiving bank knows you by, so this is
  never inferred.
- `--target-format` says which Zengin format to produce. 総合振込 and 給与振込
  have different fields and different character rules; the XML does not choose.
- `--as-of` supplies the year that `MMDD` fields lack, and is the basis of
  `MsgId` and `CreDtTm`. **Set it if you want the same input to produce the same
  bytes** — it defaults to today, which makes a conversion depend on when it
  was run.
- `--truncate` decides what happens when a name will not fit thirty bytes. The
  default refuses; `TRUNCATE_SAFE` cuts without separating a kana from its
  voicing mark.
- `--end-to-end` decides where `EndToEndId` lands: `CUSTOMER_CODE_1` (the
  default), `CUSTOMER_CODE_2`, or `DROP`. Thirty-five characters into ten
  truncates, and a truncated reconciliation reference matches the wrong payment.
- `--accept-loss` converts anyway when the loss could misroute money. Without
  it, the command stops and prints why.
- `--receiver` and `--message-id` override values that are otherwise derived
  from the file and from `--as-of`.

The mapping itself — every row, in both directions, with what each one costs —
is in [mapping.md](mapping.md). **No row of it is verified.**

## `dryrun`

What would converting this file cost? The loss report, and no file.

```sh
zengin dryrun payments.txt --as-of=2026-09-01 --allow-unverified
```

The command to run over a month of real files before committing to an
integration. It never refuses whatever it finds — the point is to see the loss,
and stopping at the first critical entry would hide the rest of the answer.

`--out-format=json` gives a pipeline something to branch on. Exit status is the
usual: `0` lossless, `1` something was lost, `2` something was lost that could
misroute money.

## `explain`

Describes a format's byte layout, or one field of it, without needing a file.

```sh
zengin explain                                    # list the bundled formats
zengin explain --format=sougou-furikomi           # the whole layout and its sources
zengin explain --format=kyuyo-furikomi --field=accountType --record=DATA
```

```
kyuyo-furikomi DATA — accountType
  預金種目 (Account Type)
  bytes 42–42 (1 N)
  digits, right-aligned, zero-padded
  code list accountType (open — other values are permitted)
    1  普通預金 (Ordinary deposit)
    2  当座預金 (Current account)
  characters: NUMERIC
  note: 給与振込 admits only 普通預金 and 当座預金, a narrower set than 総合振込's.
```

The descriptors it reads are the ones the reader uses, so what it prints is what
the library will actually do — not a document that agreed with the code when it
was written.
