# Mapping reference

> GENERATED from sougou-furikomi-pain001.yaml — do not edit. Change a declaration and run
> `./gradlew generateFormatSources`; the build fails if this page and the declarations disagree.

Every correspondence this library implements between a Zengin field and an ISO 20022
element, in both directions, with what each one costs.

## Verification status

**No row here is verified.** All 36 of them are marked `verified: false`, which under R-I19 means none has
been checked against published profile documentation. They are not guesses — they follow
the table in the build specification and the shape of the message definition — but "not a
guess" and "verified" are different claims, and only the second one is worth trusting a
payment to.

The load-bearing one is the clearing-system identifier `JPZGN`. It names the scheme every
bank code in the file belongs to, and it is unconfirmed — see Q8 in [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).

The flag is not on the honour system: a row marked `verified: true` must cite at least two
independent published sources, or the build fails. That is the same bar R-0.1 sets for a
format descriptor.

## sougou-furikomi ↔ pain.001.001.03

Bidirectional. The downward leg (ISO to Zengin) needs a MappingContext for 委託者コード, the target format and the truncation policy, none of which the XML carries (R-I20).

| Zengin | ISO 20022 | Direction | Loss | Status |
|---|---|---|---|---|
| — | `CstmrCdtTrfInitn/GrpHdr/MsgId` | → ISO | — | unverified |
| — | `CstmrCdtTrfInitn/GrpHdr/CreDtTm` | → ISO | — | unverified |
| `trailer.recordCount` | `CstmrCdtTrfInitn/GrpHdr/NbOfTxs` | both | `COERCED` / `CRITICAL` | unverified |
| `trailer.totalAmount` | `CstmrCdtTrfInitn/GrpHdr/CtrlSum` | both | `COERCED` / `CRITICAL` | unverified |
| `header.originatorName` | `CstmrCdtTrfInitn/GrpHdr/InitgPty/Nm` | both | `TRANSLITERATED` / `INFORMATIONAL` | unverified |
| `header.originatorCode` | `CstmrCdtTrfInitn/GrpHdr/InitgPty/Id/OrgId/Othr/Id` | both | — | unverified |
| — | `CstmrCdtTrfInitn/PmtInf/PmtInfId` | → ISO | — | unverified |
| — | `CstmrCdtTrfInitn/PmtInf/PmtMtd` | → ISO | — | unverified |
| — | `CstmrCdtTrfInitn/PmtInf/NbOfTxs` | → ISO | — | unverified |
| — | `CstmrCdtTrfInitn/PmtInf/CtrlSum` | → ISO | — | unverified |
| `header.valueDate` | `CstmrCdtTrfInitn/PmtInf/ReqdExctnDt` | both | `DEFAULTED` / `MATERIAL` | unverified |
| `header.originatorName` | `CstmrCdtTrfInitn/PmtInf/Dbtr/Nm` | both | `TRANSLITERATED` / `INFORMATIONAL` | unverified |
| `header.accountNumber` | `CstmrCdtTrfInitn/PmtInf/DbtrAcct/Id/Othr/Id` | both | — | unverified |
| `header.accountType` | `CstmrCdtTrfInitn/PmtInf/DbtrAcct/Tp/Prtry` | both | — | unverified |
| `header.originBankCode` | `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/ClrSysMmbId/MmbId` | both | — | unverified |
| — | `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/ClrSysMmbId/ClrSysId/Cd` | → ISO | — | unverified |
| `header.originBankName` | `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/Nm` | both | `TRANSLITERATED` / `INFORMATIONAL` | unverified |
| `header.originBranchCode` | `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/ClrSysMmbId/MmbId` | both | `COERCED` / `CRITICAL` | unverified |
| `header.originBranchName` | — | → ISO | `DROPPED` / `INFORMATIONAL` | unverified |
| `data.customerCode1` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/PmtId/EndToEndId` | both | `TRUNCATED` / `CRITICAL` | unverified |
| — | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/PmtId/InstrId` | → Zengin | `DROPPED` / `MATERIAL` | unverified |
| `data.amount` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Amt/InstdAmt` | both | `COERCED` / `CRITICAL` | unverified |
| `data.beneficiaryBankCode` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/MmbId` | both | — | unverified |
| — | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/ClrSysId/Cd` | → ISO | — | unverified |
| `data.beneficiaryBankName` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/Nm` | both | `TRANSLITERATED` / `INFORMATIONAL` | unverified |
| `data.beneficiaryBranchCode` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/MmbId` | both | `COERCED` / `CRITICAL` | unverified |
| `data.beneficiaryBranchName` | — | → ISO | `DROPPED` / `INFORMATIONAL` | unverified |
| `data.beneficiaryName` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Cdtr/Nm` | both | `TRUNCATED` / `MATERIAL` | unverified |
| `data.accountNumber` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAcct/Id/Othr/Id` | both | — | unverified |
| `data.accountType` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAcct/Tp/Prtry` | both | — | unverified |
| `data.customerCode2` | `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/RmtInf/Ustrd` | both | `TRUNCATED` / `MATERIAL` | unverified |
| `data.clearingHouseCode` | — | → ISO | `DROPPED` / `INFORMATIONAL` | unverified |
| `data.newCode` | — | → ISO | `DROPPED` / `INFORMATIONAL` | unverified |
| `data.transferCategory` | — | → ISO | `DROPPED` / `INFORMATIONAL` | unverified |
| `data.identification` | — | → ISO | `DROPPED` / `INFORMATIONAL` | unverified |
| `header.codeKubun` | — | → ISO | `DROPPED` / `INFORMATIONAL` | unverified |

### Why each row works this way

**— → `CstmrCdtTrfInitn/GrpHdr/MsgId`**

The sender's reference for the message. No Zengin field carries one, so it comes from MappingContext.messageId, which defaults to a value derived from the originator code and the reference date so that converting the same file twice produces the same bytes.

電文の送信者参照番号。全銀側に該当項目がないため MappingContext.messageId から取得します。既定値は委託者コードと基準日から導出され、同じファイルを 2 回変換しても同一のバイト列になります。

**— → `CstmrCdtTrfInitn/GrpHdr/CreDtTm`**

From MappingContext.creationDateTime, which defaults to midnight UTC on the reference date rather than the clock — a conversion that embeds the current time cannot be compared against a golden file.

MappingContext.creationDateTime から取得します。既定値は現在時刻ではなく 基準日の UTC 0 時です。現在時刻を埋め込むとゴールデンファイルとの比較が できなくなるためです。

**`trailer.recordCount` → `CstmrCdtTrfInitn/GrpHdr/NbOfTxs`**

Recomputed from the payments rather than copied, then cross-checked against the trailer. A file whose trailer disagrees with its own contents is a file whose count cannot be trusted either way, so the computed value is written and the disagreement is reported CRITICAL.

トレーラーからの転記ではなく明細から再計算し、トレーラーと照合します。 自身の内容と一致しないトレーラーはどちらの値も信頼できないため、計算値を 書き出し、不一致を CRITICAL として報告します。

**`trailer.totalAmount` → `CstmrCdtTrfInitn/GrpHdr/CtrlSum`**

Recomputed and cross-checked, exactly as the count is.

件数と同様に再計算し、照合します。

**`header.originatorName` → `CstmrCdtTrfInitn/GrpHdr/InitgPty/Nm`**

Half-width katakana widens on the way out and narrows on the way back. The widening is reversible for kana and the narrowing is not — a name that arrives in kanji cannot go into a fixed-length file at all.

往路で半角カナを全角化し、復路で半角化します。カナの全角化は可逆ですが 半角化は不可逆です。漢字で届いた名称は固定長ファイルに収容できません。

**`header.originatorCode` → `CstmrCdtTrfInitn/GrpHdr/InitgPty/Id/OrgId/Othr/Id`**

委託者コード. On the way back it comes from MappingContext, not from the XML: an initiating party identifier is not required to be the originator code the receiving bank knows, and a mapping that assumed so would produce a file the bank rejects (R-I20).

委託者コード。復路では XML ではなく MappingContext から取得します。 InitgPty の識別子が受取銀行の認識する委託者コードである保証はなく、 同一とみなすと銀行に受け付けられないファイルになります (R-I20)。

**— → `CstmrCdtTrfInitn/PmtInf/PmtInfId`**

One instruction per Zengin batch, identified by the message id and the batch's position. Derived rather than carried; the format has no field for it.

全銀のバッチ 1 件につき 1 件の PmtInf を生成し、電文 ID とバッチ位置から 識別子を導出します。全銀側に該当項目はありません。

**— → `CstmrCdtTrfInitn/PmtInf/PmtMtd`**

Always TRF. A 総合振込 file is a credit transfer and nothing else.

常に TRF。総合振込は信用振替のみです。

**— → `CstmrCdtTrfInitn/PmtInf/NbOfTxs`**

The instruction's own count, computed from the payments it contains rather than carried. A batch that disagrees with itself is not something a caller should be able to construct.

当該 PmtInf の件数。転記ではなく明細から計算します。自身の内容と矛盾する バッチを呼び出し側が組み立てられないようにするためです。

**— → `CstmrCdtTrfInitn/PmtInf/CtrlSum`**

The instruction's own sum, computed the same way.

当該 PmtInf の合計金額。同様に計算します。

**`header.valueDate` → `CstmrCdtTrfInitn/PmtInf/ReqdExctnDt`**

振込指定日 is MMDD and carries no year. Going out, the year comes from MappingContext.referenceDate. Coming back, the year is dropped — which is not recoverable, and is reported every time rather than only when it looks surprising.

振込指定日は MMDD で年を持ちません。往路では MappingContext.referenceDate から年を補い、復路では年を削除します。復元不可能なため、例外的な場合だけ でなく毎回報告します。

**`header.originatorName` → `CstmrCdtTrfInitn/PmtInf/Dbtr/Nm`**

The originator is both the initiating party and the debtor in this profile. Written to both, and read from the debtor on the way back because that is the party whose account is named alongside it.

本プロファイルでは委託者が InitgPty と Dbtr の双方に相当します。往路では 両方に書き出し、復路では口座情報と併記される Dbtr から読み取ります。

**`header.accountNumber` → `CstmrCdtTrfInitn/PmtInf/DbtrAcct/Id/Othr/Id`**

Japan has no IBAN, so the account number goes in the generic Othr/Id rather than in IBAN.

日本には IBAN がないため、口座番号は IBAN ではなく汎用の Othr/Id に 格納します。

**`header.accountType` → `CstmrCdtTrfInitn/PmtInf/DbtrAcct/Tp/Prtry`**

預金種目 as its Zengin code. The ISO account-type code list has no equivalent for 普通/当座/貯蓄, so a proprietary code is the correct place — and the reason a system that does not know this profile cannot interpret it.

預金種目を全銀のコードのまま格納します。ISO の口座種別コードには 普通/当座/貯蓄に対応する値がないため独自コードが適切ですが、本プロファイル を知らないシステムには解釈できません。

**`header.originBankCode` → `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/ClrSysMmbId/MmbId`**

仕向銀行番号, as the first four digits of the member identifier. MmbId means "this party's identifier within the named clearing system", and within 全銀システム a participant is an office — 銀行番号 followed by 支店番号, seven digits. A four-digit bank code identifies an institution rather than a participant, so it is not a member id on its own.

仕向銀行番号。メンバー識別子の先頭 4 桁に相当します。MmbId は「当該清算機関 における参加者識別子」を意味し、全銀システムの参加者は店舗、すなわち銀行番号 + 支店番号の 7 桁です。銀行番号 4 桁のみでは金融機関を示すにとどまり、 参加者識別子にはなりません。

**— → `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/ClrSysMmbId/ClrSysId/Cd`**

JPZGN, and unconfirmed — see Q8. It names the scheme the member identifier belongs to, so getting it wrong makes every bank code in the file ambiguous. It is written because omitting it would be no safer, and it is the single most load-bearing unverified value in this mapping.

JPZGN。未確認です (Q8)。メンバー識別子が属する清算機関を示すため、誤ると ファイル中のすべての銀行番号が曖昧になります。省略しても安全にはならない ため書き出していますが、本マッピングで最も重要な未確認値です。

**`header.originBankName` → `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/Nm`**

仕向銀行名, widened on the way out and narrowed on the way back.

仕向銀行名。往路で全角化し、復路で半角化します。

**`header.originBranchCode` → `CstmrCdtTrfInitn/PmtInf/DbtrAgt/FinInstnId/ClrSysMmbId/MmbId`**

仕向支店番号, as the last three digits of the same member identifier. Coming back, a member id that is not four digits plus three cannot be taken apart — 支店番号 is left empty rather than guessed at, and reported CRITICAL, because a wrong branch code sends the payment to a different office.

仕向支店番号。同じメンバー識別子の末尾 3 桁に相当します。復路で 4 桁 + 3 桁 でないメンバー識別子は分解できないため、支店番号は推測せず空欄とし、CRITICAL として報告します。支店番号を誤ると別の店舗に振り込まれるためです。

**`header.originBranchName` → —**

仕向支店名 is not carried. The branch is already identified by its code, which is the part a payment depends on, and BrnchId/Nm would add a second spelling of the same thing that nothing reconciles against.

仕向支店名は転送しません。支店は番号で特定でき、決済が依存するのは番号 です。BrnchId/Nm に名称を入れても照合対象のない二重表記が増えるだけです。

**`data.customerCode1` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/PmtId/EndToEndId`**

Controlled by MappingContext.endToEndPolicy. 顧客コード1 is ten bytes and EndToEndId is thirty-five, so the downward leg truncates — reported CRITICAL, because a truncated reconciliation reference looks usable and matches the wrong payment. Choose DROP to refuse to carry it at all.

MappingContext.endToEndPolicy で制御します。顧客コード1 は 10 バイト、 EndToEndId は 35 文字のため復路で切り詰めが発生し、CRITICAL として報告 します。切り詰められた照合キーは一見使用可能に見えて誤った取引と一致する ためです。転送しない場合は DROP を指定してください。

**— → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/PmtId/InstrId`**

The debtor's own reference to its own bank, distinct from EndToEndId, which is the one the creditor reconciles against. Never written on the way up — the Zengin formats have no field it could come from — and dropped on the way down, because both 顧客コード fields are already spoken for by the EndToEndId policy and the remittance text.

委託者から自行への参照番号。受取人が照合に用いる EndToEndId とは別項目です。 全銀側に該当項目がないため往路では書き出さず、復路では顧客コード 2 項目が いずれも EndToEndId ポリシーと送金情報に割り当て済みのため転送しません。

**`data.amount` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Amt/InstdAmt`**

振込金額 is whole yen. An amount that is not JPY, or that has a fractional part, cannot be represented at all — both are reported CRITICAL rather than rounded, because rounding money silently is how a difference becomes somebody else's reconciliation problem.

振込金額は円単位の整数です。JPY 以外の通貨や小数部を持つ金額は表現できず、 いずれも丸めずに CRITICAL として報告します。金額を黙って丸めることは差額を 他者の照合問題に転嫁することだからです。

**`data.beneficiaryBankCode` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/MmbId`**

被仕向銀行番号, as the first four digits of the member identifier.

被仕向銀行番号。メンバー識別子の先頭 4 桁に相当します。

**— → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/ClrSysId/Cd`**

JPZGN again, on the beneficiary's side, and unconfirmed for the same reason — see Q8 and the debtor agent's row.

被仕向側の JPZGN。仕向側と同じ理由で未確認です (Q8 および仕向銀行の行を 参照)。

**`data.beneficiaryBankName` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/Nm`**

被仕向銀行名, widened on the way out and narrowed on the way back.

被仕向銀行名。往路で全角化し、復路で半角化します。

**`data.beneficiaryBranchCode` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAgt/FinInstnId/ClrSysMmbId/MmbId`**

被仕向支店番号, as the last three digits of the beneficiary's member identifier, for the same reason as the originator's.

被仕向支店番号。仕向側と同じ理由で、被仕向側メンバー識別子の末尾 3 桁に 相当します。

**`data.beneficiaryBranchName` → —**

被仕向支店名, not carried, for the same reason as the origin branch name.

被仕向支店名。仕向支店名と同じ理由で転送しません。

**`data.beneficiaryName` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/Cdtr/Nm`**

受取人名 is thirty bytes of half-width katakana; Cdtr/Nm is 140 characters of anything. This is where the conversion does its real damage: a name in kanji has no automatic reading, and a name that is too long has to lose something. Refused by default, truncated only when the caller says so.

受取人名は半角カナ 30 バイト、Cdtr/Nm は任意文字 140 文字です。変換で最も 損失が生じる箇所であり、漢字名には自動的な読みがなく、長すぎる名前は必ず 何かを失います。既定では拒否し、呼び出し側が指定した場合のみ切り詰めます。

**`data.accountNumber` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAcct/Id/Othr/Id`**

口座番号.

口座番号。

**`data.accountType` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/CdtrAcct/Tp/Prtry`**

預金種目, as its Zengin code, for the same reason as the debtor's.

預金種目。委託者側と同じ理由で全銀のコードのまま格納します。

**`data.customerCode2` → `CstmrCdtTrfInitn/PmtInf/CdtTrfTxInf/RmtInf/Ustrd`**

顧客コード2 as unstructured remittance text. Ustrd is 140 characters and 顧客コード2 is ten bytes, so the downward leg truncates. When the lines carry a base64 金融EDI attachment instead, they are not written to 顧客コード2 at all — see the identification row.

顧客コード2 を非構造化送金情報として格納します。Ustrd は 140 文字、 顧客コード2 は 10 バイトのため復路で切り詰めが発生します。Ustrd が base64 の金融EDI情報を保持している場合は顧客コード2 には書き込みません (識別表示の行を参照)。

**`data.clearingHouseCode` → —**

手形交換所番号 belongs to the bill-clearing arrangement, not to the credit transfer, and ISO 20022 has nowhere for it.

手形交換所番号は手形交換制度に属する項目で、信用振替には対応する ISO 20022 の要素がありません。

**`data.newCode` → —**

新規コード says whether this beneficiary is new or changed since the last file — a statement about the originator's own history, not about the payment.

新規コードは前回ファイルからの新規・変更を示す委託者側の履歴情報であり、 決済そのものの属性ではありません。

**`data.transferCategory` → —**

振込指定区分 distinguishes 電信 from 文書 — how the instruction reaches the beneficiary's bank, which ISO 20022 does not model because it does not arise.

振込指定区分は電信・文書の別を示します。指示が被仕向銀行に届く経路の 区別であり、ISO 20022 には対応する概念がありません。

**`data.identification` → —**

識別表示 is a flag, not a value: Y means data fields 12 and 13 are one C(20) 金融EDI情報 field rather than two customer codes. It is read and acted on, and then not written anywhere, because the ISO side expresses the same thing structurally — the payload is in RmtInf or it is not. See OQ-8.

識別表示は値ではなくフラグです。Y の場合、データ項目 12・13 は 2 つの 顧客コードではなく 1 つの C(20) 金融EDI情報になります。読み取って処理には 使いますが書き出しません。ISO 側では RmtInf に payload があるか否かで同じ ことを構造的に表現するためです (OQ-8 参照)。

**`header.codeKubun` → —**

コード区分 says which character encoding the fixed-length file uses. XML declares its own, so the value has no meaning on the other side.

コード区分は固定長ファイルの文字コードを示します。XML は自身で宣言する ため、ISO 側では意味を持ちません。

## How to read the loss column

A row with no loss carries its value unchanged in both directions. Everything else names
what happens and how much it matters:

| Severity | Means |
|---|---|
| `INFORMATIONAL` | Cosmetic. Nothing reconciles differently. |
| `MATERIAL` | A party or a reference is noticeably altered. |
| `CRITICAL` | The payment could mean something else, or reach somewhere else. |

A conversion refuses on `CRITICAL` by default. See [loss.md](loss.md) for what each kind means and
what to do about it.
