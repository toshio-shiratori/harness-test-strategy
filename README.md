# harness-test-strategy

AI 駆動開発のためのテスト戦略の標準です。AI が大量にコードを生成しても、人間のレビュー速度に頼らず品質を保てるように、機械的に検証できるガードレール(テストハーネス)の方針・技術標準・運用規程を文書としてまとめています。

成果物は `docs/testing/` の Markdown です。アプリやライブラリではありません。`src/` は、`02` に載せた Kotlin のサンプルが実際にコンパイルされテストを通ることを保証するための検証用プロジェクトです。

## 対象読者

AI(コーディングエージェント)を使った開発に、テスト戦略とガバナンスを導入したいチームです。

## 読む順番

| 順 | 文書 | 内容 |
|---|---|---|
| 1 | [01 基本方針書](docs/testing/01-test-policy.md) | 基本理念、W字モデル、テストの層構造 |
| 2 | [02 AIガードレール＆テストアーキテクチャ標準](docs/testing/02-test-architecture.md) | 型で誤りを書けなくする設計、PBT、ミューテーションテスト、E2E 最小化、AI 向けガードレール設定 |
| 3 | [03 TRS 策定ガイドライン](docs/testing/03-trs-guideline.md) | テスト要件仕様書(TRS)を期待値の正(SSOT)にする方法 |
| 4 | [04 リグレッション＆AIヒーリング運用規程](docs/testing/04-regression-governance.md) | 不具合の最速層への還元(Shift-Left)、AI による自動修復の範囲、E2E の破棄基準 |
| - | [TRS テンプレート](docs/testing/trs/_template.md) | 新しい TRS を書くときの雛形 |
| - | [ORD-001](docs/testing/trs/ORD-001-add-item.md) / [PAY-001](docs/testing/trs/PAY-001-payment-state-transition.md) / [PAY-002](docs/testing/trs/PAY-002-payment-persistence.md) | TRS の実例 |

## 中心となる考え方

- 誤りは、書けない(型)、すぐ落ちる(Unit / PBT)、結合で落ちる、探索で見つかる、の順に検出が遅くなり、コストが増える。できるだけ上流の速い層で止める。
- 期待値の正は、人間が合意した仕様(TRS の AC)にある。AI が既存コードの挙動から期待値を逆生成しない。
- テストの質はカバレッジではなくミューテーションテストで確かめる。
- AI は方針書・規程・期待値を単独で変更しない。変更は PR で人間が承認する。

## `src/`(サンプルの検証用プロジェクト)

`order` と `payment` は、TRS の実例(ORD-001 / PAY-001)に対応するサンプル領域です。製品コードではありません。

| 項目 | 内容 |
|---|---|
| 言語・FW | Kotlin 2.4.20、Spring Boot 4.1.1、JDK 21 |
| テスト | Kotest 6.2.5(PBT・データ駆動を含む)、Konsist(アーキテクチャ検査・テスト保護・TRS との紐づけ検査) |
| 静的解析 | detekt、ktlint、`allWarningsAsErrors` |
| ミューテーション | PIT。PR は変更クラスのみ 80%、夜間の全体実行は 70% |

## ローカルでの確認

```bash
# ドキュメント(markdownlint と Mermaid の構文検査)
npm ci && npm run lint

# サンプルコード(静的解析、テスト、ミューテーションテスト)
./gradlew detekt ktlintCheck test
./gradlew integrationTest   # Docker が必要（Testcontainers）
./gradlew pitest
```

CI は `.github/workflows/` で、同じコマンドを実行します。

## 自分のプロジェクトで使うには

導入用のツールやパッケージはありません。文書を読み、自分のプロジェクトに合わせて取り入れます。

1. 01 と 02 を読み、層構造と型ガードレールの方針をチームで合意する。
2. TRS テンプレートを複製し、最初の機能の TRS を書く。実例の ORD-001 が参考になる。
3. `src/` の Konsist テスト(`architecture/` 配下)、`build.gradle.kts` の PIT・detekt 設定、`CLAUDE.md` を、自分のプロジェクトにコピーして調整する。
4. 不具合の運用は 04 とバグ報告テンプレート(`.github/ISSUE_TEMPLATE/bug_report.yml`)を使う。

コード例は Kotlin + Spring Boot 前提ですが、原則そのものは言語非依存です。

## コントリビュート

方針・規程・期待値(`docs/testing/`)の変更は PR で行い、責任者(`.github/CODEOWNERS`)の承認を得ます。バグ修正の PR はタイトルを `fix:` で始め、`gh pr create --template bug_fix.md` で作成します。AI エージェント向けの作業ルールは [CLAUDE.md](CLAUDE.md) にあります。

## ライセンス

[MIT](LICENSE)
