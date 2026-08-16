# zengin4j

**全銀協規定形式（固定長）ファイルを扱う JVM ライブラリ。ISO 20022 との双方向マッピングを備えます。**

[English README](README.md) · Apache-2.0 · Java 21

> **本ライブラリは全国銀行協会・全銀ネット・金融機関のいずれからも認定・承認を受けていません。**
> 本リリースに含まれるフォーマット定義はすべて `verified: false` です。バイト配置が独立した 2 つの
> 公開資料で確認されていないため、明示的に許可しない限り読み取りを拒否します。本番利用の前に、
> 取引金融機関の仕様書との照合はご自身の責任で実施してください。詳細は
> [DISCLAIMER.md](DISCLAIMER.md) を参照してください。

---

## 背景

日本の決済は、固定長の独自フォーマットから ISO 20022 XML への長い移行期にあり、両者は今後も
併存します。全銀EDIシステム（ZEDI）は企業から ISO 20022 XML を受け付けますが、**仕向金融機関へ
渡す前に固定長へ変換します**。したがって両形式のマッピングは一度きりの移行作業ではなく恒久的な
橋渡しであり、現状は各参加者が個別に、互いに異なる方法で実装しています。

このマッピングを行うオープンソース実装はどの言語にも存在せず、既存の固定長パーサーにも JVM 向けの
ものはありません。この空白が本プロジェクトの範囲です。

### 独力では気づきにくい問題

ZEDI のプロファイルは、Business Application Header と電文本体を **XML 宣言の粒度で連結** します。
その結果:

> **ZEDI ファイルには XML 宣言が複数含まれ、単一の整形式 XML 文書ではありません。** 通常の XML
> パーサーに渡すと即座に失敗し、汎用の ISO 20022 ライブラリでは読み取れません。

宣言境界での分割と、バイト単位で同一のフレーミングによる再構成を正しく扱うことは、本ライブラリが
存在する具体的な理由のひとつです。ISO 20022 レイヤー（Epic 7）で提供します。

## 現在の状況

本リポジトリは **Epic 2（総合振込の読み書き）** の段階です。

| | |
|---|---|
| ✅ | 総合振込（`21`）の読み取り: ストリーミング / バッチ / ファイル一括の 3 種類の API |
| ✅ | **バイト単位で同一の**書き出し。読み取ったファイルの区切り形式もそのまま再現 |
| ✅ | ファイル組み立て。トレーラの件数・合計金額は明細から自動計算 |
| ✅ | データとしてのフォーマット定義、バイト位置の自動計算、ビルド時の桁数合計チェック |
| ✅ | フォーマット形状に対応した生成レコード型（コミット済み・差分検出付き） |
| ✅ | 任意のレコード区切り（なし / CR / LF / CRLF、混在も可）、BOM、EOF バイトの処理 |
| ✅ | 厳格モードと寛容モード。不正レコードは例外ではなくデータとして表現 |
| ✅ | 年を持たない `MMDD` 日付の年補完（明示的な戦略指定が必須） |
| ⬜ | 残りの 120 バイト系フォーマットと文字集合の処理 — Epic 3 |
| ⬜ | バイト位置つき検証結果、JSON / SARIF 出力 — Epic 4 |
| ⬜ | コマンドラインツール — Epic 5 |
| ⬜ | 半角カナ変換と濁点を壊さない切り詰め — Epic 6 |
| ⬜ | `pain.001` の双方向変換と損失レポート — Epic 7 |

## クイックスタート

```java
// レジストリは不変かつスレッドセーフです。一度作って共有してください。
FormatRegistry registry = FormatRegistry.defaults();

ReaderOptions options = ReaderOptions.builder()
        .registry(registry)
        .charset(ZenginCharset.MS932)      // 既定値。Windows 系の会計システムが出力する文字コード
        .allowUnverifiedFormats(true)      // 0.1.0 では必須。先に DISCLAIMER.md を読んでください
        .build();

ZenginFile file = ZenginReaders.readFile(Path.of("payments.txt"), options);

for (Batch batch : file.batches()) {
    System.out.println(batch.header().originatorName() + " → " + batch.computedCount() + " 件");
    for (DataRecord record : batch.data()) {
        SougouFurikomiData payment = (SougouFurikomiData) record;
        System.out.printf("  %s  ¥%,d%n", payment.beneficiaryName(), payment.amount());
    }
}
```

メモリに収まらない大きなファイルでは、逐次読み取りを使ってください。ファイルサイズに関わらず
メモリ使用量は一定です。

```java
try (ZenginReader reader = ZenginReaders.open(path, options)) {
    while (reader.hasNext()) {
        RecordView view = reader.next();
        if (view.kind() == RecordKind.DATA) {
            long amount = view.asLong(view.field("amount"));   // バイトから直接復号、メモリ確保なし
        }
    }
}
```

> **`RecordView` は再利用されるバッファ上の窓であり、次の `next()` 呼び出しまでのみ有効です。**
> 無効になったビューにアクセスすると、誤ったレコードの値を返す代わりに
> `StaleRecordViewException` を送出します。保持が必要な場合は `materialize()` を呼ぶか、既定で
> レコードを実体化する `ZenginReaders.batches(...)` を使ってください。

ファイルを組み立てる際、各バッチのトレーラ（件数・合計金額）は明細から自動計算されます。取り違えて
不整合なファイルを作ってしまうことはありません。

```java
ZenginFile file = ZenginFileBuilder.forFormat(descriptor)
        .header(h -> h.set("originatorCode", "9900000001")
                      .set("originatorName", "ﾃｽﾄｼｮｳｼﾞ")
                      .set("valueDate", MonthDay.of(9, 30)))
        .payment(p -> p.set("beneficiaryName", "ﾔﾏﾀﾞ ﾀﾛｳ")
                       .set("accountNumber", "9876543")
                       .set("amount", 150_000L))
        .build();                       // トレーラ: 1 件 / ¥150,000

ZenginWriters.write(file, Path.of("payments.txt"), WriterOptions.defaults());
```

**読み込んだファイルを書き戻すと、バイト単位で元と一致します。** レコード区切りの形式、BOM の有無、
最終レコードの後に区切りがあったかどうか、そして本ライブラリが解釈しない予備領域まで再現されます。

```java
ZenginFile parsed = ZenginReaders.readFile(path, options);

assert Arrays.equals(ZenginWriters.toByteArray(parsed, WriterOptions.defaults()), original);
```

各レコードが元のバイト列を保持し、復号した値から再生成しない理由がこれです。予備領域や、まだ検証
できていない値が、往復しても変化しません。

実行可能な例は [`examples/`](examples/) にあります。

## 2 つの境界

**検証済みか未検証か。** すべてのフォーマット定義とマッピング規則は `verified` フラグと出典を
持ちます。`verified: true` にできるのは、[`docs/SOURCES.md`](docs/SOURCES.md) に独立した 2 つ以上の
公開資料が記載されている場合のみです。0.1.0 の定義はすべて未検証であり、生成ドキュメントにも
その旨が明示されます。

**準拠か実験的か。** 預金口座振替に対応する公式の ISO 20022 プロファイルは存在しません。
`pain.008` へのマッピングは本プロジェクト独自の設計であり、`io.zengin4j.iso20022.experimental`
に置かれ、準拠の主張からは除外されます。

## `pacs` メッセージがないのはなぜですか

意図的に対象外としています。`pacs.*` は金融機関間のメッセージであり、日本の国内内国為替は
ISO 20022 へ移行しておらず、実装の根拠となる公開プロファイルがありません。全銀の企業向け
フォーマットが存在するのは企業↔銀行の境界（`pain` と `camt`）であり、そこが本ライブラリの
範囲のすべてです。またネットワーク通信は一切行いません。ファイルを読み、ファイルを書くだけです。

## 設計上の要点

- **ドメインモデルはフォーマットの形をそのまま持ちます。** `SougouFurikomiHeader` はヘッダー
  レコードが持つ項目を、その順序どおりに持ちます。`core` に統一的な「支払」抽象は置きません。
  それは ISO 20022 レイヤーの役割であり、そこでは変換が明示的で損失レポートを伴います。
- **`zengin4j-core` は実行時依存ゼロです。** YAML ライブラリも JSON ライブラリもロギング
  ファサードもありません。`java.base` 以外に何も要求せず、ArchUnit のルールが違反をビルド失敗に
  します。
- **項目配置はデータであり、ビルド時に Java へコンパイルされます。** 定義は
  `zengin4j-core/formats/` に YAML で記述し、ビルドがそれを読み取ってコミット済みの Java を
  生成します。したがって core にはパーサーも定義ファイルも同梱されず、桁数の合計が合わない
  レイアウトは決済処理ではなくビルドで失敗します（ADR-0016）。バイト位置は桁数の累計から
  計算され、生成コードでも利用側のコードでも手書きしません。
- **長さはすべてバイト数です。** `ﾃｽﾄｷﾞﾝｺｳ` は 7 文字に見えて 8 バイトです。ｷﾞ が基底文字と独立した
  濁点の 2 バイトで構成されるためです。その間で切ると ギ が キ になり、ファイル上には何の痕跡も
  残りません。

設計判断の記録は [`docs/adr/`](docs/adr/) にあります。

## ビルド

```bash
./gradlew build                    # コンパイル・テスト・カバレッジ・アーキテクチャ規則・差分検出
./gradlew generateFormatSources    # 定義ファイル変更後にレコード型とドキュメントを再生成
./gradlew :zengin4j-core:pitest    # ミューテーションテスト（任意実行・1 分程度）
./gradlew :zengin4j-core:fuzzAll   # カバレッジ誘導ファジング（任意実行・CI では毎晩実行）
./gradlew test -Pgolden.regenerate # ゴールデンファイルを再生成し、差分を確認する
```

JDK 21 以降が必要です。使用する JDK に関わらず Java 21 のバイトコードを生成します。

`build` がゲートです。テスト失敗、`core` の行カバレッジ 90% / 分岐 85% 未満、モジュール依存違反、
桁数合計の合わないフォーマット定義、定義とずれた生成コード、そして発見時と挙動の変わったファジング
入力のいずれかがあれば失敗します。

ファジング自体はゲートに含めていません。非決定的であることが本質であり、コミットごとの検査に求め
られる性質とは正反対だからです。すでに発見済みの入力の再実行は決定的で 2 秒程度なので、含めていま
す。

## ライセンス

Apache License 2.0。[LICENSE](LICENSE) と [NOTICE](NOTICE) を参照してください。
