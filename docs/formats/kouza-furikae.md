# 預金口座振替 — Direct Debit

<!-- GENERATED from kouza-furikae.yaml by io.zengin4j.codegen.RecordSourceGenerator. Do not edit by hand; edit the descriptor and run ./gradlew generateFormatSources. -->

> ## ⚠ Corroborated, but not yet verified
> 
> Every field offset and length below is corroborated by the 3 independent
> published sources cited under Sources, and they agree. The format is
> nevertheless held at `verified: false`, because at least one **field
> attribute** is read differently by different sources, and R-0.2 keeps a
> format unverified until such a disagreement is settled. The readings and
> the resolution are in `docs/DISCREPANCIES.md`; the affected fields carry a
> note in the table below.
> 
> Reading a file with this format still requires
> `ReaderOptions.builder().allowUnverifiedFormats(true)`, and building one
> requires
> `ZenginFileBuilder.forFormat(...).allowUnverifiedFormats(true)`. You
> should still check the layout against your own institution's
> specification.

## At a glance

| | |
|---|---|
| Format id | `kouza-furikae` |
| 種別コード | `91` |
| Record length | 120 bytes |
| Verified | **no** |
| Sources cited | 3 |

### Sources

- 全国銀行協会 — 「全銀協パーソナル・コンピュータ用標準通信プロトコル（ベーシック手順）適用業務およびレコード・フォーマット」令和元年12月, sections 15–16 預金口座振替（依頼明細・処理結果明細）. https://www.zenginkyo.or.jp/fileadmin/res/abstract/efforts/system/jba_protocol_pc.pdf retrieved 2026-08-15. Supports all four record types and the 振替結果コード list.
- 大分銀行 — 「口座振替ファイル（全銀協規定形式）」. https://www.dhbk.co.jp/business/efficiency/ib/pdf/koufuri_zenginkyou.pdf retrieved 2026-08-16. Supports all four record types field by field, with byte positions.
- 北洋システム開発 — 「全国銀行協会制定のレコードフォーマット」. https://www.hsd-hh.co.jp/daikin/doc/zenginrec_format.pdf retrieved 2026-08-16. Supports all four record types, and states that 振替結果コード is zero on request and set by the bank on return.

> Offsets, lengths and the 振替結果コード list corroborated by three independent sources including the JBA standard. Held at verified: false by D-002 — the standard gives 顧客番号 as N(20) and this descriptor declares C, for the reason recorded there — and by D-003 (one source states a wider permitted character set for names than 付録1 does). See docs/DISCREPANCIES.md.

## Records

Every offset below is computed from the cumulative length of the preceding
fields, never transcribed by hand (R-F2). The lengths of each record's fields
sum exactly to the record length, which the build checks (R-F1).

### Header record — データ区分 `1`

| # | Field | 項目名 | Type | Length | Offset | Notes |
|---|---|---|---|---|---|---|
| 1 | `dataKubun` | データ区分 | N | 1 | 0 | fixed `1`; code list `dataKubun` |
| 2 | `typeCode` | 種別コード | N | 2 | 1 | fixed `91`; code list `typeCode` |
| 3 | `codeKubun` | コード区分 | N | 1 | 3 | format `CODE-KUBUN`; code list `codeKubun` |
| 4 | `originatorCode` | 委託者コード | N | 10 | 4 | required |
| 5 | `originatorName` | 委託者名 | C | 40 | 14 | characters: account and party names, symbols `()-.` |
| 6 | `debitDate` | 引落日 | N | 4 | 54 | format `MMDD`; required; 引落指定日, the date the payers' accounts are debited. Not a value date: no funds move to a payee on this date. |
| 7 | `collectionBankCode` | 取引銀行番号 | N | 4 | 58 | required; 入金先金融機関コード — where the collected funds land. This is the originator's own bank, not a payer's. |
| 8 | `collectionBankName` | 取引銀行名 | C | 15 | 62 | characters: bank and branch names, symbols `-` |
| 9 | `collectionBranchCode` | 取引支店番号 | N | 3 | 77 | required |
| 10 | `collectionBranchName` | 取引支店名 | C | 15 | 80 | characters: bank and branch names, symbols `-` |
| 11 | `collectionAccountType` | 預金種目 | N | 1 | 95 | code list `accountType`, narrowed to 1/2/9 |
| 12 | `collectionAccountNumber` | 口座番号 | N | 7 | 96 | masked in diagnostics |
| 13 | `dummy` | ダミー | C | 17 | 103 | filler |

### Data record — データ区分 `2`

| # | Field | 項目名 | Type | Length | Offset | Notes |
|---|---|---|---|---|---|---|
| 1 | `dataKubun` | データ区分 | N | 1 | 0 | fixed `2`; code list `dataKubun` |
| 2 | `payerBankCode` | 引落銀行番号 | N | 4 | 1 | required; 請求先金融機関コード — the account being debited. 9900 for ゆうちょ銀行. |
| 3 | `payerBankName` | 引落銀行名 | C | 15 | 5 | characters: bank and branch names, symbols `-` |
| 4 | `payerBranchCode` | 引落支店番号 | N | 3 | 20 | required |
| 5 | `payerBranchName` | 引落支店名 | C | 15 | 23 | characters: bank and branch names, symbols `-` |
| 6 | `reserved` | ダミー | C | 4 | 38 | filler; Unused. 総合振込 carries 手形交換所番号 in these four bytes; this format does not. |
| 7 | `payerAccountType` | 預金種目 | N | 1 | 42 | code list `accountType`, narrowed to 1/2/3/9; required; Admits 3 納税準備預金, which 総合振込 does not. |
| 8 | `payerAccountNumber` | 口座番号 | N | 7 | 43 | required; masked in diagnostics |
| 9 | `payerName` | 預金者名 | C | 30 | 50 | characters: account and party names, symbols `()-.`; required |
| 10 | `debitAmount` | 引落金額 | N | 10 | 80 | format `AMOUNT`; required |
| 11 | `newCode` | 新規コード | N | 1 | 90 | code list `newCode` |
| 12 | `customerNumber` | 顧客番号 | C | 20 | 91 | The standard gives this as N(20) with no EDI overlay. Declared C to stay consistent with the same bytes in the other formats, where the overlay does exist and C is the only reading that survives it. See D-002. |
| 13 | `transferResult` | 振替結果コード | N | 1 | 111 | code list `transferResult`; Zero throughout an instruction file; set by the bank in a returned result file. This field, and the trailer's four result totals, are the whole difference between the two. |
| 14 | `dummy` | ダミー | C | 8 | 112 | filler |

### Trailer record — データ区分 `8`

| # | Field | 項目名 | Type | Length | Offset | Notes |
|---|---|---|---|---|---|---|
| 1 | `dataKubun` | データ区分 | N | 1 | 0 | fixed `8`; code list `dataKubun` |
| 2 | `recordCount` | 合計件数 | N | 6 | 1 | format `COUNT` |
| 3 | `totalAmount` | 合計金額 | N | 12 | 7 | format `AMOUNT` |
| 4 | `collectedCount` | 振替済件数 | N | 6 | 19 | Zero in an instruction file; filled in by the bank on return. |
| 5 | `collectedAmount` | 振替済金額 | N | 12 | 25 | Zero in an instruction file; filled in by the bank on return. |
| 6 | `uncollectedCount` | 振替不能件数 | N | 6 | 37 | Zero in an instruction file; filled in by the bank on return. |
| 7 | `uncollectedAmount` | 振替不能金額 | N | 12 | 43 | Zero in an instruction file; filled in by the bank on return. |
| 8 | `dummy` | ダミー | C | 65 | 55 | filler |

### End record — データ区分 `9`

| # | Field | 項目名 | Type | Length | Offset | Notes |
|---|---|---|---|---|---|---|
| 1 | `dataKubun` | データ区分 | N | 1 | 0 | fixed `9`; code list `dataKubun` |
| 2 | `dummy` | ダミー | C | 119 | 1 | filler |

## Code lists

Every list is open: a value outside it is carried through as raw field
content rather than rejected, because the published values are not yet
confirmed and asserting that no other value exists would be a guess.

### データ区分 — Record Type (`dataKubun`)

**Verified** · 3 sources cited

| Code | 名称 | Meaning | Verified | Notes |
|---|---|---|---|---|
| `1` | ヘッダーレコード | Header record | yes |  |
| `2` | データレコード | Data record | yes |  |
| `8` | トレーラーレコード | Trailer record | yes |  |
| `9` | エンドレコード | End record | yes |  |

### 種別コード — Business Type (`typeCode`)

**Verified** · 2 sources cited

> Not exhaustive. The JBA document defines further business types this library does not implement; an unlisted value is carried through rather than rejected.

| Code | 名称 | Meaning | Verified | Notes |
|---|---|---|---|---|
| `11` | 給与振込（民間） | Payroll transfer | yes |  |
| `12` | 賞与振込（民間） | Bonus transfer | yes |  |
| `21` | 総合振込 | Bulk credit transfer | yes |  |
| `71` | 給与振込（地方公務員） | Payroll transfer for local government employees | yes |  |
| `72` | 賞与振込（地方公務員） | Bonus transfer for local government employees | yes |  |
| `91` | 預金口座振替 | Direct debit | yes | Used by both the instruction file and the result file, which is why a 種別コード alone cannot always identify a layout. See ADR-0007. |
| `41` | 株式配当金振込 | Share dividend transfer | no | Confirmed by the JBA document only; a second independent source is still wanted. |

### コード区分 — Character Code (`codeKubun`)

**Verified** · 2 sources cited

> Value 1 indicates EBCDIC. The reader rejects such files by name rather than decoding them as JIS (R-C14).

| Code | 名称 | Meaning | Verified | Notes |
|---|---|---|---|---|
| `0` | JIS | JIS | yes |  |
| `1` | EBCDIC | EBCDIC | yes |  |

### 預金種目 — Account Type (`accountType`)

**Verified** · 3 sources cited

> The master list, from 付録3 預金種目コード. The standard states that not every code is valid for every business, and that where a format enumerates a subset that subset governs — so a field narrows this list rather than the list being split. The narrowing each field applies is recorded on the field. See OQ-9.

| Code | 名称 | Meaning | Verified | Notes |
|---|---|---|---|---|
| `1` | 普通預金 | Ordinary deposit | yes |  |
| `2` | 当座預金 | Current account | yes |  |
| `3` | 納税準備預金 | Tax reserve deposit | yes |  |
| `4` | 貯蓄預金 | Savings deposit | yes |  |
| `5` | 通知預金 | Deposit at notice | yes |  |
| `6` | 定期預金 | Time deposit | yes |  |
| `7` | 積立定期預金 | Instalment time deposit | yes |  |
| `8` | 定期積金 | Instalment savings | yes |  |
| `9` | その他 | Other | yes |  |

### 新規コード — New Account Code (`newCode`)

**Verified** · 3 sources cited

| Code | 名称 | Meaning | Verified | Notes |
|---|---|---|---|---|
| `0` | その他 | Other | yes |  |
| `1` | 第1回振込分 | First transfer to this account | yes |  |
| `2` | 変更分 | Beneficiary bank, branch, account type or account number changed | yes |  |

### 振替結果コード — Direct Debit Result (`transferResult`)

**Verified** · 2 sources cited

> The functional analogue of an ISO 20022 R-transaction reason code, and the most useful thing this library exposes to an English-speaking integrator. Populated in a 口座振替結果 file; zero throughout an instruction file. Note code 4: the standard says no mandate is on file, which is not the same as an account being closed.

| Code | 名称 | Meaning | Verified | Notes |
|---|---|---|---|---|
| `0` | 振替済 | Collected | yes |  |
| `1` | 資金不足 | Insufficient funds | yes |  |
| `2` | 取引なし | No such account or no transaction | yes |  |
| `3` | 預金者の都合による振替停止 | Stopped at the depositor's instruction | yes |  |
| `4` | 預金口座振替依頼書なし | No direct debit mandate on file | yes |  |
| `8` | 委託者の都合による振替停止 | Stopped at the originator's instruction | yes |  |
| `9` | その他 | Other | yes |  |

