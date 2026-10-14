# TRS-PAY-002: 決済状態の永続化（保存と読み戻し）

> `PaymentState` を DB に保存し、読み戻す Adapter の振る舞いを定める TRS。DB を含む Integration（層2）の実例（#56）。
> 期待値は、人間（@toshio-shiratori）が #56 の方針協議（`/grill-me`）で決定した内容（OI-001〜005）に基づく。既存コードの挙動からの逆生成ではない。
> AC / UC の番号は、`TraceabilityTest` が全 TRS のテストを横断して照合するため、TRS 間で重複しないように採番する（ORD-001 が `01`、PAY-001 が `02`、本 TRS が `03`）。
> **ステータスは Approved（2026-10-11、承認者 toshio-shiratori）。Approved にできるのは人間のみ（[03](../03-trs-guideline.md)）で、以後の変更も人間の承認を要する。**

## メタ情報

| 項目 | 内容 |
|---|---|
| TRS ID | `PAY-002` |
| 機能名 | 決済状態の永続化（保存・読み戻し・矛盾行の拒否） |
| ステータス | Approved |
| 仕様の正（SSOT） | 独立した仕様書・デザインは無い。期待値は本 TRS の OI-001〜005 で人間が決定した（#56。状態の定義は [PAY-001](./PAY-001-payment-state-transition.md)） |
| 関連 TRS | PAY-001（状態と遷移ルール。本 TRS は遷移ルールを強制しない） |
| 作成日 / 最終更新日 | 2026-10-11 / 2026-10-11 |
| 仕様承認者 | toshio-shiratori（2026-10-11 承認） |

---

## 第1部：仕様レイヤー

### 1. ユーザーストーリー（US）

| ID | ストーリー |
|---|---|
| US-03 | 運営者として、保存した決済の状態を、再起動や別の処理から読み出しても同じ状態として扱いたい。なぜなら会計の記録を正確に残したいから |
| US-04 | 運営者として、状態と矛盾したデータを DB に残さず、残っていても黙って読み込まないようにしたい。なぜなら壊れたデータで誤った請求・確定をしたくないから |

### 2. ユースケース（UC）

#### UC-03: 決済の状態を保存し、読み戻す

| 項目 | 内容 |
|---|---|
| 関連 US | US-03, US-04 |
| アクター | 決済処理（システム内部の呼び出し元） |
| 事前条件 | 決済 ID（`PaymentId`）と、PAY-001 の4状態（`Pending` / `Authorized` / `Captured` / `Failed`）のいずれかの状態がある |
| 事後条件 | 保存した状態を、同じ ID で読み戻すと同じ状態が得られる（時刻はマイクロ秒精度に切り捨てた値）。矛盾した行は、書き込みも読み込みも拒否される |

**基本フロー**

1. 呼び出し元が、決済 ID と状態を指定して保存を要求する
2. システムが、状態を行に変換して DB に保存する（同じ ID が保存済みなら上書きする）
3. 呼び出し元が、決済 ID を指定して読み込みを要求する
4. システムが、行を状態に変換して返す

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-03-A1 | 手順4 | 保存されていない ID は「なし」を返す（例外にしない） |
| UC-03-A2 | 手順2 | 状態と列が矛盾する行（例: `AUTHORIZED` なのに `authorized_at` が NULL）や未知の `status` は、DB の CHECK 制約が書き込みを拒否する |
| UC-03-A3 | 手順4 | CHECK を迂回して DB に入った矛盾行・未知の `status` は、読み込み時に例外で拒否する（黙って状態に変換しない） |

### 3. 受入条件（AC / Gherkin）

| AC ID | 関連 UC | 検証層 | 優先度 | 紐づくテスト |
|---|---|---|---|---|
| AC-03-01 | UC-03 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-01 …` |
| AC-03-02 | UC-03 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-02 …` |
| AC-03-03 | UC-03 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-03 …` |
| AC-03-04 | UC-03 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-04 …` |
| AC-03-05 | UC-03 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-05 …` |
| AC-03-06 | UC-03 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-06 …` |
| AC-03-07 | UC-03-A1 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-07 …` |
| AC-03-08 | UC-03-A3 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-08 …` |
| AC-03-09 | UC-03-A2 | 層2（Integration） | Must | `PaymentRepositoryIntegrationTest::AC-03-09 …` |

```gherkin
Feature: 決済状態の永続化

  @AC-03-01 @layer:2 @integration
  Scenario: Pending を保存して読み戻すと Pending である
    Given 決済 ID "p-1" で Pending を保存した
    When  決済 ID "p-1" を読み込む
    Then  Pending が得られる

  @AC-03-02 @layer:2 @integration
  Scenario: Authorized を保存して読み戻すと、同じ時刻の Authorized である
    Given 決済 ID "p-1" で、authorizedAt がマイクロ秒精度の時刻 T の Authorized を保存した
    When  決済 ID "p-1" を読み込む
    Then  authorizedAt が T の Authorized が得られる

  @AC-03-03 @layer:2 @integration
  Scenario: Captured を保存して読み戻すと、同じ時刻の Captured である
    Given 決済 ID "p-1" で、capturedAt がマイクロ秒精度の時刻 T の Captured を保存した
    When  決済 ID "p-1" を読み込む
    Then  capturedAt が T の Captured が得られる

  @AC-03-04 @layer:2 @integration
  Scenario Outline: Failed を保存して読み戻すと、同じ理由の Failed である
    Given 決済 ID "p-1" で、理由 "<reason>" の Failed を保存した
    When  決済 ID "p-1" を読み込む
    Then  理由 "<reason>" の Failed が得られる

    Examples:
      | reason        |
      | CARD_DECLINED |
      | TIMEOUT       |

  @AC-03-05 @layer:2 @integration
  Scenario Outline: 時刻はマイクロ秒に切り捨てて保存される
    Given 決済 ID "p-1" で、時刻が T（ナノ秒を含む。例: 2026-10-11T00:00:00.123456789Z）の <state> を保存した
    When  決済 ID "p-1" を読み込む
    Then  読み戻した時刻は、T をマイクロ秒に切り捨てた値（例: 2026-10-11T00:00:00.123456Z）と等しい

    Examples:
      | state      |
      | Authorized |
      | Captured   |

  @AC-03-06 @layer:2 @integration
  Scenario: 同じ ID で保存すると上書きされる
    Given 決済 ID "p-1" で Pending を保存した
    When  同じ決済 ID "p-1" で Failed(TIMEOUT) を保存し、読み込む
    Then  Failed(TIMEOUT) が得られる
    And   遷移ルールの検査はしない（Failed から Pending への上書きも拒否しない。強制はドメインの責務）

  @AC-03-07 @layer:2 @integration
  Scenario: 保存されていない ID は読み込めない
    Given 決済 ID "p-unknown" は保存されていない
    When  決済 ID "p-unknown" を読み込む
    Then  「なし」が得られる（例外ではない）

  @AC-03-08 @layer:2 @integration
  Scenario Outline: CHECK 制約を迂回して入った矛盾行は、読み込み時に例外で拒否される
    Given CHECK 制約を外した DB に、status "<status>"・authorized_at <authorized_at>・captured_at <captured_at>・failure_reason <failure_reason> の行がある
    When  その決済 ID を読み込む
    Then  例外で失敗する（例外の型は契約にしない）

    Examples:
      | status     | authorized_at | captured_at | failure_reason |
      | AUTHORIZED | NULL          | NULL        | NULL           |
      | CAPTURED   | NULL          | NULL        | NULL           |
      | FAILED     | NULL          | NULL        | NULL           |
      | PENDING    | 時刻          | NULL        | NULL           |
      | UNKNOWN    | NULL          | NULL        | NULL           |

  @AC-03-09 @layer:2 @integration
  Scenario Outline: 状態と列が矛盾する行・未知の status は、DB の CHECK 制約が書き込みを拒否する
    Given 通常の DB（CHECK 制約あり）がある
    When  status "<status>"・authorized_at <authorized_at>・captured_at <captured_at>・failure_reason <failure_reason> の行を直接 INSERT する
    Then  INSERT は拒否され、その行は保存されない

    Examples:
      | status     | authorized_at | captured_at | failure_reason |
      | AUTHORIZED | NULL          | NULL        | NULL           |
      | CAPTURED   | NULL          | NULL        | NULL           |
      | FAILED     | NULL          | NULL        | NULL           |
      | PENDING    | 時刻          | NULL        | NULL           |
      | UNKNOWN    | NULL          | NULL        | NULL           |
```

---

## 第2部：テスト設計レイヤー

### 4. 因子・水準

| 因子ID | 因子 | 水準ID | 水準 | 区分 | 備考 |
|---|---|---|---|---|---|
| F1 | 保存する状態 | F1-L1 | `Pending` | 有効 | |
| | | F1-L2 | `Authorized` | 有効 | 時刻列を持つ |
| | | F1-L3 | `Captured` | 有効 | 時刻列を持つ |
| | | F1-L4 | `Failed` | 有効 | 理由（`CARD_DECLINED` / `TIMEOUT`）を持つ |
| F2 | 時刻の精度 | F2-L1 | マイクロ秒以下の桁が無い | 有効 | 読み戻すと同一 |
| | | F2-L2 | ナノ秒を含む | 境界 | マイクロ秒に切り捨て（OI-003）。切り上げ・四捨五入にならないことを `…999` の桁で検証する |
| F3 | ID の保存状況 | F3-L1 | 未保存 | 有効 | 「なし」（AC-03-07） |
| | | F3-L2 | 保存済み | 有効 | 上書きされる（AC-03-06） |
| F4 | 行の整合 | F4-L1 | 状態と列が整合 | 有効 | AC-03-01〜06 |
| | | F4-L2 | 状態と列が矛盾 | 無効（拒否） | 書き込みは DB が拒否（AC-03-09）、迂回した行は読み込みが拒否（AC-03-08） |
| | | F4-L3 | 未知の `status` | 無効（拒否） | 同上（OI-005） |

**組合せ生成方針**: F1（4状態）は全水準を1ケースずつ検証する（AC-03-01〜04）。F2 は時刻列を持つ `Authorized` / `Captured` で検証する（AC-03-05）。F3・F4 は独立した観点なので、それぞれ専用の AC で検証し、組合せの絞り込み（ペアワイズ等）はしない。

### 5. 因子制約ルール

#### 5.1 禁止（成立しない）組合せ

| 制約ID | 組合せ | 理由 | 扱い |
|---|---|---|---|
| C-01 | F1-L1 または F1-L4 × F2 | `Pending` / `Failed` は時刻列を持たない | テスト対象外（時刻の精度は `Authorized` / `Captured` だけで検証） |
| C-02 | 空白の決済 ID | `PaymentId` が生成を拒否する | 型で排除（層1: `PaymentIdTest`） |

#### 5.2 組合せ別の期待結果

| 制約ID | 条件 | 期待結果 | 根拠（AC / OI） |
|---|---|---|---|
| C-10 | F1 の任意 × F4-L1 | 保存して読み戻すと同じ状態（時刻はマイクロ秒に切り捨てた値） | AC-03-01〜04, OI-001, OI-003 |
| C-11 | F1-L2 または F1-L3 × F2-L2 | 時刻はマイクロ秒に切り捨てた値で読み戻る | AC-03-05, OI-003 |
| C-12 | F3-L1 | 「なし」を返す（例外にしない） | AC-03-07, OI-004 |
| C-13 | F3-L2 | 新しい状態で上書きされる。遷移ルールは検査しない | AC-03-06, OI-004 |
| C-20 | F4-L2 または F4-L3 × 書き込み | DB の CHECK 制約が拒否する | AC-03-09, OI-002, OI-005 |
| C-21 | F4-L2 または F4-L3 × 読み込み（CHECK 迂回） | 例外で失敗する（型は契約にしない） | AC-03-08, OI-002, OI-005 |

### 6. Open Issues

| ID | 起票日 | 起票者 | 区分 | 関連 | 内容（疑問・論点） | 状態 | 決定内容 | 決定者 | 決定日 | 根拠 | 反映先 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| OI-001 | 2026-10-11 | AI | Blocking | UC-03, AC-03-01〜04 | 本 TRS で何を保証するか。範囲に、遷移ルールの永続化時の強制を含めるか | Resolved | 読み戻しの同一性（4状態）・矛盾行の拒否・時刻精度を保証する。遷移ルール（PAY-001）の永続化時の強制は含めない。PAY-001 は Approved のまま触らない | toshio-shiratori | 2026-10-11 | 契約を小さく保つ。遷移ルールの強制はドメインの責務で、Adapter は保存と読み戻しだけを担う | UC-03, AC-03-01〜04, AC-03-06 |
| OI-002 | 2026-10-11 | AI | Blocking | UC-03-A2, UC-03-A3, AC-03-08, AC-03-09 | 状態と列が矛盾する行（例: `AUTHORIZED` なのに時刻が NULL）をどう扱うか | Resolved | DB の CHECK 制約で書き込みを拒否し、CHECK を迂回して入れた行は読み込み時も例外で拒否する（AC は書き込み・読み込みの2本） | toshio-shiratori | 2026-10-11 | 防御を二重にする。アプリを経由しない書き込みも DB が防ぎ、万一入った行も黙って状態に変換しない | UC-03-A2, UC-03-A3, AC-03-08, AC-03-09, C-20, C-21 |
| OI-003 | 2026-10-11 | AI | Blocking | AC-03-02, AC-03-03, AC-03-05 | 時刻（`Instant`、ナノ秒精度）を `DATETIME(6)`（マイクロ秒精度）に保存するとき、精度の差をどう扱うか | Resolved | `truncatedTo(MICROS)` で切り捨てて保存する。読み戻した時刻は、元の時刻をマイクロ秒に切り捨てた値と等しい | toshio-shiratori | 2026-10-11 | 四捨五入は繰り上がりで時刻が進み得る。切り捨てなら元の時刻より後にならない | AC-03-02, AC-03-03, AC-03-05, F2-L2, C-11 |
| OI-004 | 2026-10-11 | AI | Blocking | UC-03, UC-03-A1, AC-03-06, AC-03-07 | `PaymentId` の導入、同じ ID の保存の扱い、未保存 ID の扱い | Resolved | `PaymentId`（value class）を `payment.domain` に足す。`save` は同じ ID を上書きし、遷移ルールの強制はドメインの責務で Adapter はしない。未保存 ID の読み込みは「なし」を型（`null`）で表し、AC に「未保存の ID は読み込めない」を足す | toshio-shiratori | 2026-10-11 | ID を型で取り違えられなくする（02a §1.1）。「なし」を例外にせず型で表すと呼び出し側に分岐を強制できる | AC-03-06, AC-03-07, C-02, C-12, C-13 |
| OI-005 | 2026-10-11 | AI | Non-blocking | AC-03-08, AC-03-09 | 未知の `status` 文字列の扱いと、読み込み失敗時の例外の型を契約にするか | Resolved | `status` 列にも CHECK（4状態のみ）を置き、未知の状態文字列も DB 制約で拒否する。読み込み失敗時の例外の型は契約にしない（「例外で失敗する」だけ。テストは `shouldThrow<Exception>`）。PAY-001 OI-004 と同様に契約を小さく保つ | toshio-shiratori | 2026-10-11 | 契約を小さく保つ。例外の型を契約にすると、実装の詳細（変換方式）を仕様に固定してしまう | AC-03-08, AC-03-09, C-20, C-21 |

---

## 第3部：運用情報

### 7. トレーサビリティ

| AC ID | 層1（型/Unit/PBT） | 層2（Integration/E2E） | 層3（探索） | 状態 |
|---|---|---|---|---|
| AC-03-01 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-02 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-03 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-04 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-05 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-06 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-07 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-08 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |
| AC-03-09 | — | `PaymentRepositoryIntegrationTest` | — | 実装済 |

### 8. 不具合還元履歴

| BUG ID | 発見日 | 発見層 | 根本原因の分類 | 還元先 | 追加テスト | 反映した TRS 項目 |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

### 9. 変更履歴

| 日付 | 版 | 変更者 | 変更内容 | 承認者 |
|---|---|---|---|---|
| 2026-10-11 | 0.1 | AI | 初版作成（Draft）。#56 の方針協議で人間が決定した期待値を AC・OI-001〜005 に記録し、実装（`MyBatisPaymentRepository`・Flyway マイグレーション）とテストを追加した。AI は Approved にしない | 未承認 |
| 2026-10-11 | 1.0 | AI | 仕様承認者の承認を受け、ステータスを Draft から Approved にした（承認者の明示的な指示による。AI の判断ではない） | toshio-shiratori |

---

## レビューチェックリスト

- [x] 仕様レイヤーとテスト設計レイヤーが分離されている
- [x] 全 AC が Gherkin 形式で、検証層タグがある
- [x] 期待値の根拠が仕様／デザインであり、既存コード由来ではない（OI-001〜005 で人間が決定した）
- [x] 因子の無効水準と、型で排除できる制約が明記されている
- [x] 因子制約に、禁止組合せと組合せ別の期待結果がある
- [x] Blocking の Open Issue が未解決のまま実装に進んでいない（OI-001〜004 は Resolved）
- [x] Open Issues の履歴（解決済みを含む）が残っている
- [x] 全 AC にテストが紐づいている（AC-03-01〜09）
- [ ] 仕様承認者の承認（Approved）— オーナーのみ
