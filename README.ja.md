# zengin4j

**全銀協規定形式（固定長）ファイルを扱う JVM ライブラリ。ISO 20022 との双方向マッピングを備えます。**

[English README](README.md) · Apache-2.0 · Java 25

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
存在する具体的な理由のひとつであり、すでに動作します。

```java
ZediFile file = ZediEnvelopeReader.read(Path.of("payments.xml"));

for (ZediMessage message : file.messages()) {
    Pain001Document payments = Pain001Document.from(message.body());
}

assert Arrays.equals(ZediEnvelopeWriter.toByteArray(file), original);   // バイト単位で同一
```

## 現在の状況

本リポジトリは **Epic 7（ISO 20022）** の段階です。

| | |
|---|---|
| ✅ | 120 バイト系 4 フォーマット: 総合振込（`21`）、給与振込（`11`）、賞与振込（`12`）、預金口座振替（`91`） |
| ✅ | 読み取り: ストリーミング / バッチ / ファイル一括の 3 種類の API |
| ✅ | **バイト単位で同一の**書き出し。読み取ったファイルの区切り形式もそのまま再現 |
| ✅ | ファイル組み立て。トレーラの件数・合計金額は明細から自動計算 |
| ✅ | データとしてのフォーマット定義、バイト位置の自動計算、ビルド時の桁数合計チェック |
| ✅ | フォーマット形状に対応した生成レコード型（コミット済み・差分検出付き） |
| ✅ | 項目ごとの使用可能文字チェック。違反バイトの位置を返します |
| ✅ | Shift_JIS / CP932 / UTF-8 対応。両者の差異は文書化しテストで固定 |
| ✅ | 任意のレコード区切り（なし / CR / LF / CRLF、混在も可）、BOM、EOF バイトの処理 |
| ✅ | 厳格モードと寛容モード。不正レコードは例外ではなくデータとして表現 |
| ✅ | 年を持たない `MMDD` 日付の年補完（明示的な戦略指定が必須） |
| ✅ | 検証: 6 階層 27 ルール。すべての指摘がバイト位置を持ちます |
| ✅ | JSON / SARIF 出力。SARIF は CI 上でファイルへの注釈として表示されます |
| ✅ | 日本の銀行営業日カレンダー（祝日込み）。データ範囲外は推測せず報告します |
| ✅ | `zengin` コマンド: `validate` / `inspect` / `generate` / `diff` / `explain` |
| ✅ | 4 フォーマットすべての合成テストデータ生成（Java からも CLI からも） |
| ✅ | 半角カナ変換。変換によって生じた差異はすべて記録されます |
| ✅ | 濁点を壊さない切り詰め。基底文字と濁点・半濁点が分離されることはありません |
| ✅ | ZEDI エンベロープ: 複数の XML 宣言を分割し、バイト単位で同一に書き戻します |
| ✅ | `pain.001.001.03` の双方向変換。変換ごとに損失レポートを必ず返します |
| ✅ | `zengin convert` / `zengin dryrun`、および変換の代償を示す往復変換 |
| ⬜ | 受信系 `pain.002` / `camt.052` / `camt.054`、および 200 バイト系 — Epic 8 |

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
        .allowUnverifiedFormats(true)   // 0.1.0 では必須。DISCLAIMER.md を参照
        .header(h -> h.set("originatorCode", "9900000001")
                      .set("originatorName", "ﾃｽﾄｼﾖｳｼﾞ")
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

検証は例外を投げず、レポートを返します。各指摘は「どこが」を必ず伴います。

```java
ValidationReport report = ZenginValidator.builder()
        .withCalendar(JapaneseBankCalendar.bundled())
        .build()
        .validate(file);

if (!report.isSubmittable()) {
    System.out.print(report.toText(Locale.JAPANESE));
}
```

```
ERROR V-202 record 4 byte 366 [beneficiaryName]: 項目 beneficiaryName に使用できない文字が
  含まれています: 長音 ｰ は使用できません。長音はハイフン - (0x2D) で表記してください。
ERROR V-301 record 5 byte 488 [totalAmount]: トレーラーの合計金額は 999,999 ですが、明細の
  合計は 300,000 です（差額 699,999）。
WARNING V-306 record 3 byte 244: レコード 2 の明細と、銀行・支店・口座・金額がすべて同一です。
```

金融機関ごとに運用が異なるため、すべてのルールは ID で個別に抑止・重大度変更ができます。送信可否を
左右するのはエラーのみで、警告は妨げません。警告で止めるレポートは、いずれ無視されるからです。
ID と既定の重大度、そして**これらのルールが検査しない範囲**は
[`docs/validation-rules.md`](docs/validation-rules.md) にまとめています。

氏名は多くの場合全角で渡され、固定長の半角バイト列に収める必要があります。ここで金額ではなく
「誰宛か」が失われるため、変換の代償は必ず明示されます。

```java
Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ ジロウ", options);

result.text();                  // ｶﾞｸﾌﾞﾁ ｼﾞﾛｳ
result.isMateriallyChanged();   // false — 半角化だけでは氏名は変わりません
```

**濁音は 1 文字に見えて 2 バイトです。** `ｶﾞ` は `B6 DE` であり、バイト境界で切ると
ガクブチ が カクブチ になります。ファイル上には何の痕跡も残らず、振込先だけが変わります。
本ライブラリの切り詰めは、切断位置が濁点に当たる場合は基底文字ごと落とします。既定では
そもそも切り詰めません。

**変換結果は書き込む項目によって変わります。** 長音はハイフンになりますが、給与・賞与振込の
名称は記号を一切使用できません。したがって ヨーコ は総合振込には書けても給与振込には書けません。
変換関数が `CharacterClass` を受け取るのはこのためです。

**漢字は推測せず拒否します。** 東 は氏名によって ヒガシ・トウ・アズマ のいずれにもなり、
誤った読みは振込先の誤りに直結します。読み辞書は同梱せず、今後も同梱しません。

実行可能な例は [`examples/`](examples/) に、バイトレベルの解説は
[`docs/encoding.md`](docs/encoding.md) にあります。各フォーマットのバイト配置は
[`docs/formats/`](docs/formats/) にあり、ライブラリが実際に使用する定義から生成しています。

ISO 20022 への変換は、電文と**その代償**を必ず同時に返します。片方だけを返す API は存在しません。

```java
MappingContext context = MappingContext.builder("9900000001", LocalDate.of(2026, 9, 1))
        .targetFormat(descriptor)
        .build();                       // 既定では CRITICAL の損失で変換を中止します

MappingResult<ZediFile> converted = Iso20022Mapper.create().toIso(file, context);

ZediEnvelopeWriter.write(converted.output(), Path.of("payments.xml"));
System.out.print(converted.loss().toText(Locale.JAPANESE));
```

変換が全単射でないことは、議論するより実行して確かめてください。

```java
RoundTripResult round = Iso20022Mapper.create().roundTrip(file, context);

round.isByteIdentical();   // false。差異はすべて損失レポートに記載されます
```

損失の種類とその意味は [`docs/loss.md`](docs/loss.md)、マッピングの全行は
[`docs/mapping.md`](docs/mapping.md) にあります。**検証済みの行は 1 行もありません。**

## コマンドラインから

```sh
./gradlew :zengin4j-cli:shadedJar
alias zengin='java -jar zengin4j-cli/build/libs/zengin4j-cli-*-all.jar'
```

金融機関にファイルを返却され、理由が判然としないときに使うのが `inspect --annotate` です。
各項目のバイト位置・生バイト・復号値・和英の項目名、そしてその値が使用可能かどうかを表示します。

```
record 2  DATA  byte 122
  off   len T  field                  項目名          name                     hex                  value
  1     4   N  beneficiaryBankCode    被仕向銀行番号  Beneficiary Bank Code    39 39 39 39          9999        ok
  43    7   N  accountNumber          口座番号        Account Number           (masked)             ***6543     ok
  50    30  C  beneficiaryName        受取人名        Beneficiary Name         D3 B0 DE 20 …        ﾓｰﾞ ｼﾖｳ     <- 長音 ｰ は使用できません
```

このほか `validate`（テキスト / JSON / SARIF 出力と、パイプラインで分岐できる終了コード）、
`diff`（項目単位の差分。レコードを対応付けるため、明細を 1 件挿入しても以降が全件変更扱いに
なりません）、`generate`（合成ファイル生成。同じシードなら同じバイト列）、`explain`
（ファイルなしで任意フォーマットのバイト配置を表示）があります。

**`--unsafe-print` を指定しない限り、口座番号は出力されません。** 16 進表記も同様です
（16 進の口座番号も口座番号です）。終了コードを含む詳細は [`docs/cli.md`](docs/cli.md)
を参照してください。

## 2 つの境界

**検証済みか未検証か。** すべてのフォーマット定義・コードリスト・マッピング規則は `verified`
フラグと出典を持ちます。`verified: true` にできるのは、[`docs/SOURCES.md`](docs/SOURCES.md) に
独立した 2 つ以上の公開資料が記載されている場合のみで、これは慣習ではなくローダーが強制します。

総合振込のレイアウトは、全銀協の手順書を含む独立した 6 件の資料と照合し、バイト位置・桁数の
すべてが一致しています。それでも `verified: false` のままです。ある項目の属性について資料間に
不一致が残っており（[D-002](docs/DISCREPANCIES.md)）、項目単位の不一致が解消されるまでフォーマット
を未検証として扱う規則があるためです。参照しているコードリストは検証済みです。生成ドキュメントは
この 3 つの状態を区別し、未検証のフォーマットは明示的に許可しない限り読み込めません。

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

- **変換は失ったものを必ず返します。** ISO 20022 レイヤーには、変換結果のみを返すメソッドが
  存在しません（リフレクションによる検査で担保しています）。両形式は同型ではなく — 任意文字
  140 文字は半角カナ 30 バイトに収まらず、全銀側で表現できる通貨は JPY のみです — 呼び出し側が
  それを忘れられる API は、最も重要な点について嘘をつくことになります。
- **検証済みのマッピング行はありません。** 全銀項目と ISO 20022 要素の対応はデータとして宣言し、
  [`docs/mapping.md`](docs/mapping.md) に生成しています（件数もそちらに記載されます）。いずれの
  行も公開プロファイル文書との照合を経ていません（R-I19）。最も影響が大きいのは清算機関識別子
  `JPZGN` です。

設計判断の記録は [`docs/adr/`](docs/adr/) にあります。

## ビルド

```bash
./gradlew build                    # コンパイル・テスト・カバレッジ・アーキテクチャ規則・差分検出
./gradlew :zengin4j-cli:shadedJar  # 単独実行可能な `zengin` コマンドを生成
./gradlew runExamples              # examples/ の全プログラムを実行し、出力をそのまま表示
./gradlew generateFormatSources    # 定義ファイル変更後にレコード型とドキュメントを再生成
./gradlew :zengin4j-core:pitest    # ミューテーションテスト（任意実行・1 分程度）
./gradlew :zengin4j-core:fuzzAll   # カバレッジ誘導ファジング（任意実行・CI では毎晩実行）
./gradlew test -Pgolden.regenerate # ゴールデンファイルを再生成し、差分を確認する
```

JDK 25 以降が必要です。使用する JDK に関わらず Java 25 のバイトコードを生成します。
2026 年 8 月にベースラインを 21 から引き上げました。理由と代償は
[ADR-0036](docs/adr/0036-java-25-baseline.md) を参照してください。

`build` がゲートです。テスト失敗、`core` の行カバレッジ 90% / 分岐 85% 未満、モジュール依存違反、
桁数合計の合わないフォーマット定義、定義とずれた生成コード、そして発見時と挙動の変わったファジング
入力のいずれかがあれば失敗します。

ファジング自体はゲートに含めていません。非決定的であることが本質であり、コミットごとの検査に求め
られる性質とは正反対だからです。すでに発見済みの入力の再実行は決定的で 2 秒程度なので、含めていま
す。

## リリース

公開は手動承認付きの GitHub Actions でのみ行い、開発マシンからは実行できません。手順は
[RELEASING.md](RELEASING.md) を参照してください。公開対象は `zengin4j-core`、`zengin4j-testkit`、
`zengin4j-validation` の 3 つです。`zengin4j-cli` はライブラリではなくアプリケーションであり
公開しません。実行時依存を持つことを許している唯一のモジュールなのはそのためです
（[ADR-0024](docs/adr/0024-picocli-for-the-cli.md)）。残りのモジュールは雛形であり、
意図的に公開しません。

## ライセンス

Apache License 2.0。[LICENSE](LICENSE) と [NOTICE](NOTICE) を参照してください。
