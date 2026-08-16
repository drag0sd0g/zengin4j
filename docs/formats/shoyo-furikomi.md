# 賞与振込 — Bonus Transfer

<!-- GENERATED from shoyo-furikomi.yaml by io.zengin4j.codegen.RecordSourceGenerator. Do not edit by hand; edit the descriptor and run ./gradlew generateFormatSources. -->

> ## ⚠ Corroborated, but not yet verified
> 
> Every field offset and length below is corroborated by the 2 independent
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
| Format id | `shoyo-furikomi` |
| 種別コード | `12` |
| Record length | 120 bytes |
| Verified | **no** |
| Sources cited | 2 |

### Sources

- 全国銀行協会 — 「全銀協パーソナル・コンピュータ用標準通信プロトコル（ベーシック手順）適用業務およびレコード・フォーマット」令和元年12月, section 4 給与・賞与振込レコード・フォーマット. https://www.zenginkyo.or.jp/fileadmin/res/abstract/efforts/system/jba_protocol_pc.pdf retrieved 2026-08-15. States that 賞与振込 uses the 給与振込 layout with 種別コード 12.
- 兵庫県信用組合 — 「全銀協規定フォーマットについて」. https://www.hyogokenshin.co.jp/wp-content/uploads/format1.pdf retrieved 2026-08-15. Lists 賞与振込 as 種別コード 12 sharing the 給与振込 format.

> Layout borrowed from kyuyo-furikomi, which is the reading the standard states. Held at verified: false because the layout it borrows is.

## Records

Every offset below is computed from the cumulative length of the preceding
fields, never transcribed by hand (R-F2). The lengths of each record's fields
sum exactly to the record length, which the build checks (R-F1).

### Header record — データ区分 `1`

| # | Field | 項目名 | Type | Length | Offset | Notes |
|---|---|---|---|---|---|---|
| 1 | `dataKubun` | データ区分 | N | 1 | 0 | fixed `1`; code list `dataKubun` |
| 2 | `typeCode` | 種別コード | N | 2 | 1 | fixed `12`; code list `typeCode` |
| 3 | `codeKubun` | コード区分 | N | 1 | 3 | format `CODE-KUBUN`; code list `codeKubun` |
| 4 | `originatorCode` | 委託者コード | N | 10 | 4 | required |
| 5 | `originatorName` | 委託者名 | C | 40 | 14 | characters: payroll and bonus transfer names, no symbols |
| 6 | `valueDate` | 振込指定日 | N | 4 | 54 | format `MMDD` |
| 7 | `originBankCode` | 仕向銀行番号 | N | 4 | 58 | required |
| 8 | `originBankName` | 仕向銀行名 | C | 15 | 62 | characters: bank and branch names, symbols `-` |
| 9 | `originBranchCode` | 仕向支店番号 | N | 3 | 77 | required |
| 10 | `originBranchName` | 仕向支店名 | C | 15 | 80 | characters: bank and branch names, symbols `-` |
| 11 | `accountType` | 預金種目 | N | 1 | 95 | code list `accountType`, narrowed to 1/2/9 |
| 12 | `accountNumber` | 口座番号 | N | 7 | 96 | masked in diagnostics |
| 13 | `dummy` | ダミー | C | 17 | 103 | filler |

### Data record — データ区分 `2`

| # | Field | 項目名 | Type | Length | Offset | Notes |
|---|---|---|---|---|---|---|
| 1 | `dataKubun` | データ区分 | N | 1 | 0 | fixed `2`; code list `dataKubun` |
| 2 | `beneficiaryBankCode` | 被仕向銀行番号 | N | 4 | 1 | required |
| 3 | `beneficiaryBankName` | 被仕向銀行名 | C | 15 | 5 | characters: bank and branch names, symbols `-` |
| 4 | `beneficiaryBranchCode` | 被仕向支店番号 | N | 3 | 20 | required |
| 5 | `beneficiaryBranchName` | 被仕向支店名 | C | 15 | 23 | characters: bank and branch names, symbols `-` |
| 6 | `clearingHouseCode` | 手形交換所番号 | N | 4 | 38 |  |
| 7 | `accountType` | 預金種目 | N | 1 | 42 | code list `accountType`, narrowed to 1/2; 給与振込 admits only 普通預金 and 当座預金, a narrower set than 総合振込's. |
| 8 | `accountNumber` | 口座番号 | N | 7 | 43 | required; masked in diagnostics |
| 9 | `beneficiaryName` | 受取人名 | C | 30 | 50 | characters: payroll and bonus transfer names, no symbols; required; Three of four sources name this 受取人名; 三菱UFJ labels it 預金者名 while describing its content as 受取人名. Same field, same bytes, same meaning. |
| 10 | `amount` | 振込金額 | N | 10 | 80 | format `AMOUNT`; required |
| 11 | `newCode` | 新規コード | N | 1 | 90 | code list `newCode` |
| 12 | `employeeNumber` | 社員番号 | C | 10 | 91 | Sources split evenly on N versus C — the same disagreement as 総合振込 顧客コード1, at the same byte positions. Declared C, which preserves either reading. See D-002. |
| 13 | `departmentCode` | 所属コード | C | 10 | 101 | Sources split evenly on N versus C, as for 社員番号. Declared C. See D-002. |
| 14 | `dummy` | ダミー | C | 9 | 111 | filler; Nine bytes of filler where 総合振込 has 振込指定区分, 識別表示 and a seven-byte ダミー. There is no EDI overlay in this format. |

### Trailer record — データ区分 `8`

| # | Field | 項目名 | Type | Length | Offset | Notes |
|---|---|---|---|---|---|---|
| 1 | `dataKubun` | データ区分 | N | 1 | 0 | fixed `8`; code list `dataKubun` |
| 2 | `recordCount` | 合計件数 | N | 6 | 1 | format `COUNT` |
| 3 | `totalAmount` | 合計金額 | N | 12 | 7 | format `AMOUNT` |
| 4 | `dummy` | ダミー | C | 101 | 19 | filler |

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

