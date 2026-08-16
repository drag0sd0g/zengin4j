# Sources

Every format definition in this repository must be traceable to the documents it was derived from
(R-L3). This file is maintained from the first commit, because reconstructing provenance afterwards
is significantly more work than recording it as you go.

## Current state

| Format | Descriptor | `verified` | Independent sources | Held back by |
|---|---|---|---|---|
| 総合振込 (`21`) | `zengin4j-core/formats/sougou-furikomi.yaml` | `false` | 6 | [D-002](DISCREPANCIES.md) — 顧客コード1/2 attribute |

| Code list | `verified` | Independent sources |
|---|---|---|
| データ区分 | `true` | 3 |
| 種別コード | `true` | 2 |
| コード区分 | `true` | 2 |
| 預金種目 | `true` | 3 |
| 新規コード | `true` | 3 |
| 振込指定区分 | `true` | 2 |

**The byte offsets of 総合振込 are corroborated by all six sources, including the standard itself,
and they agree.** What keeps the format at `verified: false` is a single field-attribute
disagreement, recorded as D-002. R-0.2 is explicit that a field on which sources disagree keeps the
format unverified until the question is settled, however solid the rest of the layout is.

The original transcription came from §13.1 of this project's own build specification, which
describes its tables as working drafts. A project's own design document is not an independent
published source, so it is not cited below — it is the origin of the transcription and nothing more.

## The bar for `verified: true`

At least **two independent published sources** must agree on every field of every record, and both
must be cited here (R-0.1). The loader enforces the count for formats and for code lists; only a
human can judge independence. Two documents published by the same institution, or one document and
a copy of it, are one source.

Where two sources disagree, both readings go in [DISCREPANCIES.md](DISCREPANCIES.md), the more
conservative one is implemented, and the descriptor stays unverified until the question is settled
(R-0.2).

Note on independence: the institution guides below all describe the same JBA-prescribed format, so
they are not independent *derivations*. They are independent *publications* — separately produced,
separately reviewed, by six organisations — which is what makes their agreement evidence that no
single transcription error is being repeated. Their disagreement on 顧客コード is precisely the
signal this exercise exists to surface.

## Sources cited

### sougou-furikomi

1. **全国銀行協会 (Japanese Bankers Association)** — 「全銀協パーソナル・コンピュータ用標準通信プロトコル
   （ベーシック手順）適用業務およびレコード・フォーマット」, 令和元年12月, section 8
   「総合振込レコード・フォーマット」.
   <https://www.zenginkyo.or.jp/fileadmin/res/abstract/efforts/system/jba_protocol_pc.pdf>,
   retrieved 2026-08-15.
   *The standard itself.* Supports all four record types: every field name, attribute and length,
   and the `(120)` totals. Also supports データ区分, 種別コード, コード区分, 預金種目, 新規コード and
   振込指定区分.

2. **群馬銀行** — 「全銀協制定ファイルフォーマット【総合振込】」.
   <https://www.gunmabank.co.jp/hojin/biznb/service/pdf/z_format1.pdf>, retrieved 2026-08-15.
   Supports all four record types, and states the record length as 120 bytes with 122 when CRLF is
   appended. Reads 顧客コード1/2 as `C`; see D-002.

3. **愛知銀行** — 「総合振込レコード・フォーマット」.
   <https://www.aichibank.co.jp/corporate/efficiently/bizdirect/files/pdf/zengin_format.pdf>,
   retrieved 2026-08-15.
   Supports the header and data records, the EDI情報 overlay, 新規コード and 振込指定区分.

4. **兵庫県信用組合** — 「全銀協規定フォーマットについて」.
   <https://www.hyogokenshin.co.jp/wp-content/uploads/format1.pdf>, retrieved 2026-08-15.
   Supports all four record types and the 種別コード values. Uses 委託者コード / 委託者名 where the
   JBA document uses 振込依頼人コード / 振込依頼人名.

5. **十八親和銀行** — 「全銀フォーマット」.
   <https://www.18shinwabank.co.jp/pdf/bb_format_zengin.pdf>, retrieved 2026-08-15.
   Supports the data record, コード区分 and 新規コード. Reads 顧客コード1/2 as `N`.

6. **三井住友銀行** — 「ファイルレイアウト（総合振込・全銀形式）」.
   <https://www.smbc.co.jp/hojin/eb/web21/pdf/file-layout_01.pdf>, retrieved 2026-08-15.
   Supports the data record. Documents 顧客コード1/2 as `N` with the attribute changing to `C`
   according to the identification code — the reading that explains D-002.

## Sources consulted beyond the shipped descriptor

These support answers recorded in [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) rather than the
`sougou-furikomi` layout itself. They are cited here because a question closed without a citation
is only an opinion.

7. **全国銀行協会**, the document cited at (1) above, further sections. Retrieved 2026-08-15.
   - §1 振込入金通知 and §2 入出金取引明細 — the 200-byte layouts and their 種別コード (`01`, `03`),
     closing OQ-2 and supplying Q7.
   - §4 給与振込 and §5 賞与振込 — closing Q9.
   - §15–16 預金口座振替 — the 振替結果コード list, closing Q6, plus the format's own trailer shape.
   - 付録1 使用文字一覧 — the per-field-class character sets, closing Q5.
   - 付録3 預金種目コード — the nine-value master list and the narrowing rule, closing OQ-9.

8. **一般社団法人全国銀行資金決済ネットワーク (Zengin-Net)** — 「全銀EDIシステム 簡易XMLファイル作成機能
   操作マニュアル」第1.2版, 2018年12月. Retrieved 2026-08-15 via
   <https://www.tottoribank.co.jp/business/houjin_ib/service/ikkatsu/img/s-zedi_manual.pdf>.
   Specifies how 金融EDI情報 is carried in `pain.001`: MIME headers and Base64 payload split across
   `<Ustrd>` elements at 76 characters per line, inside `<RmtInf>`. Narrows OQ-8 and supplies the
   detail R-I10 and R-I12 depend on.

9. **`zengin-code/source-data`** — machine-readable Japanese financial institution codes.
   <https://github.com/zengin-code/source-data>, retrieved 2026-08-15. 1,146 institutions; `9900`
   (ゆうちょ銀行) is the only assigned code in the `99xx` block, which is what closes OQ-5. A
   dataset, not a specification — evidence about which codes are *in use*, not about the format.

10. **`Kyash/zengin-go`** — `samples/sample.txt`. Retrieved 2026-08-15. Used for differential
    testing (R-T17), not as authority: decoding it with this library's offsets reconciles the
    file's own trailer totals, which is empirical corroboration no document can provide. Also the
    source of a data point in [D-002](DISCREPANCIES.md), and of the observation recorded in OQ-4.

## Naming variants observed

The same field is named differently across sources. All are in current use; none changes a byte
offset. The descriptor uses the first column.

| This library | 全銀協 | Also seen as |
|---|---|---|
| 委託者コード | 振込依頼人コード（取引企業コード） | 会社コード |
| 委託者名 | 振込依頼人名 | 振込依頼人名（カナ） |
| 振込指定日 | 取組日 | 振込日 |
| 顧客コード1 / 2 | 顧客コード1 / 2 | 顧客番号1 / 2, お客様番号1 / 2 |

## Where to look next

Per Appendix B of the build specification:

- **全銀ネット** — ZEDI connection guidance and XML format documentation, for the Epic 7 mapping layer.
- **Institution-published format guides** — at least three independent institutions per format.
  Regional banks and 信用金庫 publish the most complete documents.
- **ISO 20022** — the iso20022.org message catalogue and XSDs.
- **Existing implementations** — `Kyash/zengin-go`, `diva-osaka/Diva.Zengin`, `zengin-code/*`. An
  implementation is evidence of what someone else concluded, not of what the format is. Treat a
  divergence as a question and record it here (R-T17).
