# TRS-ORD-001: 注文への商品追加

> 本 TRS は様式（`_template.md` と 03）を実在の機能に適用して検証するための**最初の1本**である。
> 仕様の正は人間が承認したものでなければならないが、本機能には独立した仕様書が無い。現時点の AC は
> [02 §1.2](../02-test-architecture.md) の設計記述（数量は 1〜999、範囲外は HTTP 400）を仕様として採用した**案**であり、
> 既存コードの挙動からの逆生成ではない。仕様として確定するかは人間が判断する（Open Issues の OI-002）。
> **ステータスは Draft のまま。AI は Approved にしない。**

## メタ情報

| 項目 | 内容 |
|---|---|
| TRS ID | `ORD-001` |
| 機能名 | 注文への商品追加（数量の検証を含む） |
| ステータス | Draft |
| 仕様の正（SSOT） | [02 §1.2 コンストラクタ防御](../02-test-architecture.md)（暫定。独立した仕様書・デザインは無い。OI-002） |
| 関連 TRS | なし |
| 作成日 / 最終更新日 | 2026-10-10 / 2026-10-10 |
| 仕様承認者 | 未承認（人間の承認待ち） |

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
| 事前条件 | なし（認証・注文の存在確認は本 TRS の範囲外。OI-004） |
| 事後条件 | 数量が有効範囲内なら、追加が受け付けられる（HTTP 204）。範囲外・不正入力なら何も追加されず HTTP 400 が返る |

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
| UC-01-A4 | 手順2 | `sku` が空・空白の場合の扱いは未決（OI-001） |

### 3. 受入条件（AC / Gherkin）

| AC ID | 関連 UC | 検証層 | 優先度 | 紐づくテスト |
|---|---|---|---|---|
| AC-01-01 | UC-01 | 層1 | Must | `QuantityTest::AC-01-01 範囲内の整数は受理され、値が保たれる`（PBT） |
| AC-01-02 | UC-01-A1 | 層1 | Must | `QuantityTest::AC-01-02 範囲外の整数は OUT_OF_RANGE で拒否される`（PBT） |
| AC-01-03 | UC-01, UC-01-A1 | 層1 | Must | `QuantityTest::AC-01-03 境界値 1 と 999 は受理、0 と 1000 は拒否` |
| AC-01-04 | UC-01 | 層1 / 層2（Integration） | Must | `OrderControllerTest::AC-01-04 …`、`OrderControllerHttpTest::AC-01-04 …` |
| AC-01-05 | UC-01-A1 | 層1 / 層2（Integration） | Must | `OrderControllerTest::AC-01-05 …`、`OrderControllerHttpTest::AC-01-05 …` |
| AC-01-06 | UC-01-A2 | 層2（Integration） | Must | `OrderControllerHttpTest::AC-01-06 …` |
| AC-01-07 | UC-01-A3 | 層2（Integration） | Must | `OrderControllerHttpTest::AC-01-07 …` |
| AC-01-08 | UC-01-A4 | 層1 | Should | **pending**（OI-001 が未解決のため期待値を確定しない） |

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

  @AC-01-04 @layer:1 @layer:2 @integration
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

  @AC-01-08 @layer:1 @pending:OI-001
  Scenario: sku が空・空白のときの扱い
    # OI-001 が解決するまで期待値を確定しない
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
| | | F2-L2 | 壊れた JSON | 無効 | Integration |
| | | F2-L3 | `quantity` 欠落 | 無効 | Integration |
| | | F2-L4 | `sku` が空・空白 | 未決 | OI-001 |

**組合せ生成方針**: F1 は層1で単独に網羅する（PBT + 境界 Example）。F2 は層2（Integration）で水準ごとに1件。F1 × F2 の組合せ展開は行わない（F2 の無効水準は F1 に到達する前に拒否されるため、C-01）。

### 5. 因子制約ルール

#### 5.1 禁止（成立しない）組合せ

| 制約ID | 組合せ | 理由 | 扱い |
|---|---|---|---|
| C-01 | F2-L2（壊れた JSON）または F2-L3（`quantity` 欠落）× F1 の任意の水準 | 数量が取り出せないため F1 の検証に到達しない | テスト対象外 |
| C-02 | DTO に value class を直接使う構成 | 検証を素通りして不変条件を破れる（`ValueClassDtoTest` で実測、02 §1.2） | 構成として禁止。素通りすること自体を `ValueClassDtoTest` で固定 |

#### 5.2 組合せ別の期待結果

| 制約ID | 条件 | 期待結果 | 根拠（AC / OI） |
|---|---|---|---|
| C-10 | F1-L1〜L3 × F2-L1 | 204、追加処理が呼ばれる | AC-01-04 |
| C-11 | F1-L4〜L6 × F2-L1 | 400（ProblemDetail）、追加処理は呼ばれない | AC-01-05 |
| C-12 | F2-L2 | 400、追加処理は呼ばれない | AC-01-06 |
| C-13 | F2-L3 | 400 | AC-01-07 |
| C-14 | F2-L4 | 未決。期待結果を置かない | OI-001 |

### 6. Open Issues

| ID | 起票日 | 起票者 | 区分 | 関連 | 内容（疑問・論点） | 状態 | 決定内容 | 決定者 | 決定日 | 根拠 | 反映先 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| OI-001 | 2026-10-10 | AI | Blocking | F2-L4, AC-01-08 | `sku` が空・空白のときは受理か拒否か。現状の実装は `sku` を検証していないが、これを仕様としない | Open | | | | | |
| OI-002 | 2026-10-10 | AI | Non-blocking | AC-01-01〜03 | 数量の範囲 1〜999 は業務上の根拠のある値か、サンプルの値か。本 TRS は 02 §1.2 の記述を暫定の仕様としている | Open | | | | | |
| OI-003 | 2026-10-10 | AI | Non-blocking | AC-01-05 | 400 応答の `detail` 文言（現状は日本語の説明文）を仕様とするか。AC は `status` / `title` のみ検証している | Open | | | | | |
| OI-004 | 2026-10-10 | AI | Non-blocking | UC-01 | 認証、注文の存在確認、同一 SKU の重複追加時の合算は本機能の範囲か。現状の追加処理はログ出力のみ | Open | | | | | |

---

## 第3部：運用情報

### 7. トレーサビリティ

| AC ID | 層1（型/Unit/PBT） | 層2（Integration/E2E） | 層3（探索） | 状態 |
|---|---|---|---|---|
| AC-01-01 | `QuantityTest` | — | — | 実装済 |
| AC-01-02 | `QuantityTest` | — | — | 実装済 |
| AC-01-03 | `QuantityTest` | — | — | 実装済 |
| AC-01-04 | `OrderControllerTest` | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-05 | `OrderControllerTest` | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-06 | — | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-07 | — | `OrderControllerHttpTest` | — | 実装済 |
| AC-01-08 | — | — | — | pending（OI-001） |

### 8. 不具合還元履歴

| BUG ID | 発見日 | 発見層 | 根本原因の分類 | 還元先 | 追加テスト | 反映した TRS 項目 |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

### 9. 変更履歴

| 日付 | 版 | 変更者 | 変更内容 | 承認者 |
|---|---|---|---|---|
| 2026-10-10 | 0.1 | AI | 初版作成（様式の検証を兼ねる。Draft） | 未承認 |

---

## レビューチェックリスト

- [x] 仕様レイヤーとテスト設計レイヤーが分離されている
- [x] 全 AC が Gherkin 形式で、検証層タグがある
- [ ] 期待値の根拠が仕様／デザインであり、既存コード由来ではない（暫定。02 §1.2 を採用。OI-002 で人間が確認する）
- [x] 因子の無効水準と、型で排除できる制約が明記されている
- [x] 因子制約に、禁止組合せと組合せ別の期待結果がある
- [x] Blocking の Open Issue が未解決のまま実装に進んでいない（AC-01-08 は pending）
- [x] Open Issues の履歴（解決済みを含む）が残っている（解決済みはまだ無い）
- [x] 全 AC にテストが紐づいている（AC-01-08 は pending として明示）
