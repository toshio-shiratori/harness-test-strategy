# TRS-ORD-001: 注文への商品追加

> 本 TRS は様式（`_template.md` と 03）を実在の機能に適用して検証するための**最初の1本**である。
> 仕様の正は人間が承認したものでなければならないが、本機能には独立した仕様書が無い。現時点の AC は
> [02a §1.2](../../../../docs/testing/02a-types-and-pbt.md) の設計記述（数量は 1〜999、範囲外は HTTP 400）を仕様として採用したもので、
> 既存コードの挙動からの逆生成ではない。数量の範囲、`sku` の扱い、エラーコードは、人間が 2026-10-10 に OI-001〜006 で決定した。
> AC-01-08〜12 は #33 で実装済み。
> **ステータスは Approved(2026-10-11、承認者 toshio-shiratori)。Approved にできるのは人間のみで、以後の変更も人間の承認を要する。**

## メタ情報

| 項目 | 内容 |
|---|---|
| TRS ID | `ORD-001` |
| 機能名 | 注文への商品追加（数量の検証を含む） |
| ステータス | Approved |
| 仕様の正（SSOT） | [02a §1.2 コンストラクタ防御](../../../../docs/testing/02a-types-and-pbt.md)（独立した仕様書・デザインは無い。数量の範囲は OI-002 で人間が確定済み） |
| 関連 TRS | なし |
| 作成日 / 最終更新日 | 2026-10-10 / 2026-10-15 |
| 仕様承認者 | toshio-shiratori(2026-10-11 承認) |

---

## 第1部：仕様レイヤー

### 1. ユーザーストーリー（US）

| ID | ストーリー |
|---|---|
| US-01 | 購入者として、注文に商品を数量つきで追加したい。なぜなら必要な分だけ購入したいから |
| US-02 | 運営者として、不正な数量が注文に入らないようにしたい。なぜなら在庫・金額の計算を壊したくないから |

### 2. ユースケース（UC）

#### UC-01: 注文に商品を追加する

| 項目 | 内容 |
|---|---|
| 関連 US | US-01, US-02 |
| アクター | 購入者（API クライアント） |
| 事前条件 | なし（認証・注文の存在確認・SKU の実在確認・同一 SKU の重複合算は本 TRS の範囲外。OI-004） |
| 事後条件 | `sku` が空・空白のみでなく、数量が有効範囲内なら、追加が受け付けられる（HTTP 204）。範囲外・不正入力なら何も追加されず HTTP 400（`codes` 付き）が返る |

**基本フロー**

1. クライアントが `POST /orders/items` に `{"sku": "<SKU>", "quantity": <整数>}` を送る
2. システムが数量を検証する
3. 有効なら、システムが追加を受け付け、本文なしで 204 を返す

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-01-A1 | 手順2 | 数量が 1〜999 の範囲外の場合、追加せず 400 を返す |
| UC-01-A2 | 手順1 | JSON が壊れている場合、追加せず 400 を返す |
| UC-01-A3 | 手順1 | `quantity` が欠けている場合、追加せず 400 を返す |
| UC-01-A4 | 手順2 | `sku` が空・空白のみの場合、追加せず 400（`codes` に `BLANK`）を返す（OI-001）。空白の判定は `isBlank` 相当（全角空白を含む Unicode 空白。OI-005） |
| UC-01-A5 | 手順2 | `sku` が空・空白のみで、かつ数量が範囲外の場合、追加せず 400 を返し、`codes` に `BLANK` と `OUT_OF_RANGE` の両方を含める（OI-005） |

### 3. 受入条件（AC / Gherkin）

| AC ID | 関連 UC | 検証層 | 優先度 | 紐づくテスト |
|---|---|---|---|---|
| AC-01-01 | UC-01 | 層1 | Must | `QuantityTest::AC-01-01 範囲内の整数は受理され、値が保たれる`（PBT） |
| AC-01-02 | UC-01-A1 | 層1 | Must | `QuantityTest::AC-01-02 範囲外の整数は OUT_OF_RANGE で拒否される`（PBT） |
| AC-01-03 | UC-01, UC-01-A1 | 層1 | Must | `QuantityTest::AC-01-03 境界値 1 と 999 は受理、0 と 1000 は拒否` |
| AC-01-04 | UC-01 | 層1 / 層2（Integration、E2E） | Must | `OrderControllerTest::AC-01-04 …`、`OrderControllerHttpTest::AC-01-04 …`、`OrderFormE2eTest::AC-01-04 …` |
| AC-01-05 | UC-01-A1 | 層1 / 層2（Integration） | Must | `OrderControllerTest::AC-01-05 …`、`OrderControllerHttpTest::AC-01-05 …` |
| AC-01-06 | UC-01-A2 | 層2（Integration） | Must | `OrderControllerHttpTest::AC-01-06 …` |
| AC-01-07 | UC-01-A3 | 層2（Integration） | Must | `OrderControllerHttpTest::AC-01-07 …` |
| AC-01-08 | UC-01-A4 | 層1 / 層2（Integration） | Must | `SkuTest::AC-01-08 …`、`OrderControllerTest::AC-01-08 …`、`OrderControllerHttpTest::AC-01-08 …` |
| AC-01-09 | UC-01-A1 | 層2（Integration、E2E） | Must | `OrderControllerHttpTest::AC-01-09 …`、`OrderFormE2eTest::AC-01-09 …` |
| AC-01-10 | UC-01-A2 | 層2（Integration） | Must | `OrderControllerHttpTest::AC-01-10 …` |
| AC-01-11 | UC-01-A3 | 層1 / 層2（Integration） | Must | `OrderControllerTest::AC-01-11 …`、`OrderControllerHttpTest::AC-01-11 …` |
| AC-01-12 | UC-01-A5 | 層1 / 層2（Integration、E2E） | Must | `OrderControllerTest::AC-01-12 …`、`OrderControllerHttpTest::AC-01-12 …`、`OrderFormE2eTest::AC-01-12 …` |

**`codes` の規約**（OI-003, OI-005, OI-006）: 400 応答の ProblemDetail は拡張プロパティ `codes`（文字列の配列）を持つ。要素は集合として扱い、順序は仕様にしない。同じ値を重複して含めない。`INVALID_JSON` と `MISSING_FIELD` は解析段階の失敗なので単独（要素が1つ）で返る。`BLANK` と `OUT_OF_RANGE` は検証段階の失敗で、同時に成立すれば両方が入る。以下の Gherkin で「`codes` は X である」は、要素の集合が X に等しいことを指す。

```gherkin
Feature: 注文への商品追加

  @AC-01-01 @layer:1
  Scenario Outline: 範囲内の数量は受理される
    Given 数量が <quantity> である
    When  数量を検証する
    Then  数量は受理され、値は <quantity> のままである

    Examples:
      | quantity |
      | 1        |
      | 500      |
      | 999      |

  @AC-01-02 @layer:1
  Scenario Outline: 範囲外の数量は拒否される
    Given 数量が <quantity> である
    When  数量を検証する
    Then  数量は拒否され、理由は "OUT_OF_RANGE" である

    Examples:
      | quantity    |
      | 0           |
      | -1          |
      | 1000        |
      | 2147483647  |

  @AC-01-03 @layer:1
  Scenario: 数量の境界
    When  数量 1 と 999 を検証する
    Then  どちらも受理される
    When  数量 0 と 1000 を検証する
    Then  どちらも拒否される

  @AC-01-04 @layer:1 @layer:2 @integration @e2e:happy-path
  Scenario: 有効な数量で商品を追加できる
    Given リクエスト本文が {"sku":"SKU-1","quantity":3} である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 204 が返る
    And   追加処理が sku "SKU-1"・数量 3 で1回呼ばれる

  @AC-01-05 @layer:1 @layer:2 @integration
  Scenario: 範囲外の数量は 400 で拒否される
    Given リクエスト本文が {"sku":"SKU-1","quantity":1000} である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る
    And   本文は ProblemDetail で status が 400、title が "Bad Request" である
    And   追加処理は呼ばれない

  @AC-01-06 @layer:2 @integration
  Scenario: 壊れた JSON は 400 で拒否される
    Given リクエスト本文が壊れた JSON である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る
    And   追加処理は呼ばれない

  @AC-01-07 @layer:2 @integration
  Scenario: quantity が欠けたリクエストは 400 で拒否される
    Given リクエスト本文が {"sku":"SKU-1"} である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る

  # AC-01-08〜12 は OI-001・OI-003・OI-005・OI-006 で期待値が確定し、#33 で実装した。

  @AC-01-08 @layer:1 @layer:2 @integration
  Scenario Outline: sku が空・空白のみなら 400 で拒否される
    Given リクエスト本文が {"sku":<sku>,"quantity":3} である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る
    And   本文の codes は ["BLANK"] である
    And   追加処理は呼ばれない

    Examples:
      | sku    |
      | ""     |
      | " "    |
      | "　"   |

  @AC-01-09 @layer:2 @integration @e2e:communication
  Scenario: 範囲外の数量の 400 には codes に OUT_OF_RANGE が付く
    Given リクエスト本文が {"sku":"SKU-1","quantity":1000} である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る
    And   本文の codes は ["OUT_OF_RANGE"] である

  @AC-01-10 @layer:2 @integration
  Scenario Outline: 壊れた JSON・型不一致の 400 には codes に INVALID_JSON が付く
    Given リクエスト本文が <body> である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る
    And   本文の codes は ["INVALID_JSON"] である
    # 型不一致（文字列・小数）も INVALID_JSON に含める（OI-005 a）

    Examples:
      | body                                |
      | {"sku":"SKU-1","quantity":           |
      | {"sku":"SKU-1","quantity":"3"}      |
      | {"sku":"SKU-1","quantity":1.5}      |

  @AC-01-11 @layer:1 @layer:2 @integration
  Scenario Outline: 必須フィールドの欠落・null の 400 には codes に MISSING_FIELD が付く
    Given リクエスト本文が <body> である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る
    And   本文の codes は ["MISSING_FIELD"] である
    # 欠けたフィールド名は仕様として返さない（OI-003）

    Examples:
      | body                            |
      | {"sku":"SKU-1"}                 |
      | {"quantity":3}                  |
      | {"sku":null,"quantity":3}       |
      | {"sku":"SKU-1","quantity":null} |

  @AC-01-12 @layer:1 @layer:2 @integration @e2e:display
  Scenario: sku が空白かつ数量が範囲外なら codes に両方が付く
    Given リクエスト本文が {"sku":" ","quantity":1000} である
    When  クライアントが POST /orders/items を呼ぶ
    Then  ステータス 400 が返る
    And   本文の codes は "BLANK" と "OUT_OF_RANGE" の両方を含む（順序は問わない）
    And   追加処理は呼ばれない
```

---

## 第2部：テスト設計レイヤー

### 4. 因子・水準

| 因子ID | 因子 | 水準ID | 水準 | 区分 | 備考 |
|---|---|---|---|---|---|
| F1 | 数量（整数） | F1-L1 | 範囲内（2〜998） | 有効 | PBT |
| | | F1-L2 | 下限境界 1 | 境界（有効） | Example で固定 |
| | | F1-L3 | 上限境界 999 | 境界（有効） | Example で固定 |
| | | F1-L4 | 下限の外 0 | 境界（無効） | Example で固定 |
| | | F1-L5 | 負数 | 無効 | PBT |
| | | F1-L6 | 上限の外 1000 以上 | 無効 | PBT（`Int` の最大値まで） |
| F2 | リクエスト本文の形 | F2-L1 | 正しい JSON | 有効 | |
| | | F2-L2 | 壊れた JSON、または `quantity` / `sku` の型不一致（文字列・小数など） | 無効 | Integration（OI-005 a） |
| | | F2-L3 | `quantity` 欠落 | 無効 | Integration |
| | | F2-L4 | `sku` が空・空白のみ（`isBlank` 相当） | 無効 | Integration。Unit / PBT でも検証する（OI-001, OI-005 b） |
| | | F2-L5 | 必須フィールド（`quantity` / `sku`）が null | 無効 | Integration |

**組合せ生成方針**: F1 は層1で単独に網羅する（PBT + 境界 Example）。F2 は層2（Integration）で水準ごとに1件。F1 × F2 の組合せ展開は行わない（F2 の無効水準は F1 に到達する前に拒否されるため、C-01）。F2-L4（空 `sku`）と F1 の無効水準が同時に成立する場合は、400 になり、`codes` に両方を返す（C-15、OI-005 c）。

### 5. 因子制約ルール

#### 5.1 禁止（成立しない）組合せ

| 制約ID | 組合せ | 理由 | 扱い |
|---|---|---|---|
| C-01 | F2-L2（壊れた JSON）、F2-L3（`quantity` 欠落）または F2-L5（null）× F1 の任意の水準 | 数量が取り出せないため F1 の検証に到達しない | テスト対象外 |
| C-02 | DTO に value class を直接使う構成 | 検証を素通りして不変条件を破れる（`ValueClassDtoTest` で実測、02a §1.2） | 構成として禁止。素通りすること自体を `ValueClassDtoTest` で固定 |

#### 5.2 組合せ別の期待結果

| 制約ID | 条件 | 期待結果 | 根拠（AC / OI） |
|---|---|---|---|
| C-10 | F1-L1〜L3 × F2-L1 | 204、追加処理が呼ばれる | AC-01-04 |
| C-11 | F1-L4〜L6 × F2-L1 | 400（ProblemDetail、`codes` = `OUT_OF_RANGE` のみ）、追加処理は呼ばれない | AC-01-05, AC-01-09 |
| C-12 | F2-L2 | 400（`codes` = `INVALID_JSON` のみ）、追加処理は呼ばれない | AC-01-06, AC-01-10 |
| C-13 | F2-L3, F2-L5 | 400（`codes` = `MISSING_FIELD` のみ） | AC-01-07, AC-01-11 |
| C-14 | F2-L4（F1 は有効） | 400、`codes` = `BLANK` のみ、追加処理は呼ばれない | AC-01-08 |
| C-15 | F2-L4 × F1-L4〜L6 | 400。`codes` に `BLANK` と `OUT_OF_RANGE` の両方を含む（順序不問）。追加処理は呼ばれない | AC-01-12, OI-005 |

### 6. Open Issues

| ID | 起票日 | 起票者 | 区分 | 関連 | 内容（疑問・論点） | 状態 | 決定内容 | 決定者 | 決定日 | 根拠 | 反映先 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| OI-001 | 2026-10-10 | AI | Blocking | F2-L4, AC-01-08 | `sku` が空・空白のときは受理か拒否か。現状の実装は `sku` を検証していないが、これを仕様としない | Resolved | 空文字・空白のみの `sku` は 400 で拒否する。`code` は `BLANK`。US-02（不正な入力が注文に入らない）と整合させる | toshio-shiratori | 2026-10-10 | US-02。実装の挙動ではなく、人間の判断による | UC-01-A4, F2-L4, C-14, AC-01-08（実装は #33） |
| OI-002 | 2026-10-10 | AI | Non-blocking | AC-01-01〜03 | 数量の範囲 1〜999 は業務上の根拠のある値か、サンプルの値か。本 TRS は 02a §1.2 の記述を暫定の仕様としている | Resolved | 数量の範囲は 1〜999 で確定する | toshio-shiratori | 2026-10-10 | 02a §1.2。他に仕様書は無く、人間が確定した | AC-01-01〜03（変更なし）、メタ情報の仕様の正、レビューチェックリスト |
| OI-003 | 2026-10-10 | AI | Non-blocking | AC-01-05 | 400 応答の `detail` 文言（現状は日本語の説明文）を仕様とするか。AC は `status` / `title` のみ検証している | Superseded（OI-006） | `detail` 文言は仕様にしない。エラーコードだけを仕様とし、400 応答の ProblemDetail に拡張プロパティ `code` を付ける。値は `OUT_OF_RANGE`（数量が範囲外）、`BLANK`（`sku` が空・空白のみ）、`INVALID_JSON`（JSON が壊れている）、`MISSING_FIELD`（必須フィールドの欠落・null）。欠けたフィールド名は仕様として返さない | toshio-shiratori | 2026-10-10 | 文言の変更を TRS 変更（人間承認）にしないため。機械可読なコードのみ契約とする | AC-01-08〜11, C-11〜C-14（実装は #33）。応答形式の `code` 単一は OI-006 で `codes`（配列）に置換された。`detail` 文言を仕様にしない点とコードの値の集合は有効 |
| OI-004 | 2026-10-10 | AI | Non-blocking | UC-01 | 認証、注文の存在確認、同一 SKU の重複追加時の合算は本機能の範囲か。現状の追加処理はログ出力のみ | Resolved | 本 TRS の範囲は入力検証（数量と `sku` の形式）のみ。認証、注文の存在確認、SKU の実在確認、同一 SKU の重複合算は範囲外とし、必要になった時点で別 TRS を起こす | toshio-shiratori | 2026-10-10 | 追加処理がログ出力のみで、検証する対象が無い | UC-01 の事前条件 |
| OI-005 | 2026-10-10 | AI | Non-blocking | AC-01-08〜11, C-15 | 次が未決。(a) `quantity` が文字列・小数など型不一致のとき、`code` は `INVALID_JSON` か別か。(b) `sku` の「空白」判定は `isBlank` 相当（全角空白を含む Unicode 空白）でよいか。(c) 空 `sku` と範囲外の `quantity` が同時のとき、`BLANK` と `OUT_OF_RANGE` のどちらを返すか | Resolved | (a) 型不一致は `INVALID_JSON` に含める。(b) 「空白」は `isBlank` 相当（全角空白を含む Unicode 空白）とする。(c) 同時に成立する場合は優先を付けず、`codes` に両方を返す（応答形式の変更は OI-006） | toshio-shiratori | 2026-10-10 | (a) Spring は型不一致を壊れた JSON と同じ経路で落とすため、振り分けが1本で済む。(b) 標準 API だけで実装でき、全角空白が混じる入力に合う。(c) 一度に全ての違反を直せる | UC-01-A4, UC-01-A5, F2-L2, F2-L4, C-15, AC-01-10, AC-01-12（実装は #33） |
| OI-006 | 2026-10-10 | AI | Non-blocking | OI-003, AC-01-05〜12 | OI-005 (c) で両方を返すことになったため、OI-003 の「拡張プロパティ `code`（単一）」では表せない。応答形式をどうするか | Resolved | `code` を廃止し、`codes`（文字列の配列）に統一する。要素が1つでも配列で返す。要素は集合として扱い、順序は仕様にしない。重複は入れない。`INVALID_JSON` と `MISSING_FIELD` は解析段階の失敗なので単独で返し、他の code と混ぜない。値の集合（`OUT_OF_RANGE` / `BLANK` / `INVALID_JSON` / `MISSING_FIELD`）と、欠けたフィールド名を返さない方針は OI-003 のまま | toshio-shiratori | 2026-10-10 | クライアントの分岐を配列1本にするため。`code` と `codes` の併存は二重管理になるので採らない。OI-003 の行は削除せず、本行で変更を記録する | 「`codes` の規約」、UC-01-A4, AC-01-08〜12, C-11〜C-15（実装は #33） |
| OI-007 | 2026-10-15 | AI | Non-blocking | AC-01-04, AC-01-09, AC-01-12 | E2E（`OrderFormE2eTest`）が見る画面の期待値に、TRS 上の出所が無い。具体的には、結果欄の状態（`data-state` の `idle` / `sending` / `success` / `error`）、`codes` を 1 件ずつ `<li>` に表示すること、ステータスの表示（204・400）である。AC が定めているのは HTTP ステータスと `codes` の値までで、画面にどう出すかは AC に無い。暫定の正は、`static/index.html` の画面の設計（#76）で、AC の期待値ではなく実装側の設計である。画面の AC を足すかどうか（足すなら期待値）は人間が決める。決まるまで画面の AC は足さない | Open | — | — | — | — | — |

---

## 第3部：運用情報

### 7. トレーサビリティ

| AC ID | 層1（型/Unit/PBT） | 層2（Integration/E2E） | 層3（探索） | 状態 |
|---|---|---|---|---|
| AC-01-01 | `QuantityTest` | — | — | 実装済 |
| AC-01-02 | `QuantityTest` | — | — | 実装済 |
| AC-01-03 | `QuantityTest` | — | — | 実装済 |
| AC-01-04 | `OrderControllerTest` | `OrderControllerHttpTest`、`OrderFormE2eTest` | — | 実装済 |
| AC-01-05 | `OrderControllerTest` | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-06 | — | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-07 | — | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-08 | `SkuTest`、`OrderControllerTest` | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-09 | — | `OrderControllerHttpTest`、`OrderFormE2eTest` | — | 実装済 |
| AC-01-10 | — | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-11 | `OrderControllerTest` | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-12 | `OrderControllerTest` | `OrderControllerHttpTest`、`OrderFormE2eTest` | — | 実装済 |

### 8. 不具合還元履歴

| BUG ID | 発見日 | 発見層 | 根本原因の分類 | 還元先 | 追加テスト | 反映した TRS 項目 |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

### 9. 変更履歴

| 日付 | 版 | 変更者 | 変更内容 | 承認者 |
|---|---|---|---|---|
| 2026-10-10 | 0.1 | AI | 初版作成（様式の検証を兼ねる。Draft） | 未承認 |
| 2026-10-10 | 0.2 | AI | OI-001〜004 を人間の決定に基づき Resolved にした。AC-01-08 を確定し、AC-01-09〜11 を追加した。実装待ちの AC は `@pending:ISSUE-33`。OI-005 を起票した（#18） | 未承認 |
| 2026-10-10 | 0.3 | AI | OI-005 を人間の決定に基づき Resolved にし、OI-006 を追加した。400 応答を `code` から `codes`（配列）に変更し、AC-01-08〜11 の期待値を書き換えた。AC-01-12（同時違反）と UC-01-A5 を追加した | 未承認 |
| 2026-10-10 | 0.4 | AI | #33 の実装に合わせ、AC-01-08〜12 の `@pending` を外し、紐づくテストとトレーサビリティを記入した。AC-01-11・12 は層1（`OrderControllerTest`）でも検証する。`quantity` / `sku` の欠落・null はコントローラーで判定する（DTO のフィールドを nullable にした） | 未承認 |
| 2026-10-11 | 1.0 | AI | 仕様承認者の承認を受け、ステータスを Draft から Approved にした(承認者の明示的な指示による。AI の判断ではない) | toshio-shiratori |
| 2026-10-11 | 1.1 | AI | OI-003 を `Superseded`（置換先 OI-006）にした。OI-006 による応答形式の変更を、状態にも反映した。内容は変わらない | toshio-shiratori（指示による） |
| 2026-10-15 | 1.2 | AI | E2E の実装（`OrderFormE2eTest`）を反映した。AC-01-04・09・12 の検証層に E2E を足し、`@e2e:<領域>` タグ（`happy-path` / `communication` / `display`）、紐づくテスト、トレーサビリティを更新した。AC 本文（期待値）は変えていない。画面の期待値に出所が無いため OI-007 を起票した（Non-blocking、画面の AC は足さない） | 未承認 |

---

## レビューチェックリスト

- [x] 仕様レイヤーとテスト設計レイヤーが分離されている
- [x] 全 AC が Gherkin 形式で、検証層タグがある
- [x] 期待値の根拠が仕様／デザインであり、既存コード由来ではない（02a §1.2 を OI-002 で人間が確定した）
- [x] 因子の無効水準と、型で排除できる制約が明記されている
- [x] 因子制約に、禁止組合せと組合せ別の期待結果がある
- [x] Blocking の Open Issue が未解決のまま実装に進んでいない（OI-001 は Resolved）
- [x] Open Issues の履歴（解決済みを含む）が残っている（OI-001〜006 は行を残している。OI-001・002・004〜006 は Resolved、OI-003 は Superseded（OI-006）で、OI-003 の変更は OI-006 に記録。OI-007 は Open で起票）
- [x] 全 AC にテストが紐づいている（AC-01-08〜12 は #33 で実装済み）
