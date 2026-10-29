# examples/kotlin-spring

`docs/testing/` の標準を、Kotlin + Spring Boot のプロジェクトに導入した一例です。標準の各文書に対応するコード例(`docs/<文書番号>.md`)をコンパイルしてテストを通し、サンプルの TRS(`docs/trs/`)の受入条件(AC)がテストに紐づいていることを検証します。

このディレクトリは導入の一例で、配布の対象ではありません。`order` / `payment` は TRS の実例に対応するサンプル領域で、製品コードではありません。

## 動かし方

Gradle で自己完結したプロジェクトです。コマンドはこのディレクトリで実行します。

```bash
cd examples/kotlin-spring
./gradlew detekt ktlintCheck test
./gradlew integrationTest   # Docker が必要（Testcontainers）
./gradlew e2eTest           # Docker とブラウザ（Chromium）が必要
./gradlew pitest            # ミューテーションテスト（閾値 80%）
```

`e2eTest` の準備(Docker、Chromium の取得)はリポジトリ直下の README の「[E2E のセットアップ](../../README.md#e2e-のセットアップ)」に、サンプルアプリを手元で動かす手順は同じ README の「[サンプルアプリを手元で動かす](../../README.md#サンプルアプリを手元で動かす)」にあります。

## 配置

| パス | 内容 |
|---|---|
| `src/` | サンプルのソースとテスト(層1は `src/test`、統合テストは `src/integrationTest`、E2E は `src/e2eTest`) |
| `docs/01.md` | 01 基本方針書の実現例(G1〜G5 をこのサンプルがどう実現しているか) |
| `docs/02a.md` | 02a 型設計と PBT の Kotlin のコード例(`src/` と同期。変えるときは両方を更新する) |
| `docs/02b.md` | 02b 層1の実行境界とミューテーションの実装(設定・コード例・実測。`src/`・`build.gradle.kts` と同期) |
| `docs/02c.md` | 02c E2E 最小化原則の実例(注文追加フォームの E2E) |
| `docs/03.md` | 03 TRS ガイドラインのサンプルでの実現 |
| `docs/04.md` | 04 リグレッション運用規程のサンプルでの実装 |
| `docs/trs/` | サンプルの TRS(ORD-001 / PAY-001 / PAY-002)。`TraceabilityTest` が読む |
| `config/` | detekt の設定と、Konsist の検査を確かめる違反例 |
| `build.gradle.kts` | ビルドと、PIT・detekt の設定 |

TRS のテンプレート(`_template.md`)は標準の側にあり、`TraceabilityTest` は `../../docs/testing/trs/_template.md` を読みます。
