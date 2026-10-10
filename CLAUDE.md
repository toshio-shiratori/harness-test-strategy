# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

回答・ドキュメントは日本語で書くこと。

## 概要

AI駆動開発向けテスト戦略の標準（`docs/testing/`）。成果物は Markdown で、アプリでもライブラリでもない。概要・読む順番は [README.md](./README.md)。
`01-test-policy` → `02-test-architecture` → `03-trs-guideline` → `04-regression-governance` と `trs/_template.md` は相互にリンクしているので、見出しやファイル名を変えるときはリンク切れに注意する。
`src/` は `02` の Kotlin サンプルを実際にコンパイル・実行して検証するための最小プロジェクトで、`order` / `payment` は TRS の実例（ORD-001 / PAY-001）に対応するサンプル領域。製品コードではない。

## 品質ゲート（変更内容ごとに通すもの）

| 変更 | 実行するもの | CI |
|---|---|---|
| `*.md` | `npm ci && npm run lint`（`lint:md` = markdownlint-cli2、`lint:mermaid` = 全 mermaid ブロックを実レンダリング。Chrome を起動するので遅い）。`lint:md:fix` で自動修正 | `lint.yml` |
| `src/`・`build.gradle.kts`・`02` のコード例・`docs/testing/trs/` | `./gradlew detekt ktlintCheck test`、続けて `./gradlew pitest`(閾値 80%、夜間の全体実行は 70%) | `kotlin-samples.yml` / `nightly-mutation.yml` |
| バグ修正 PR | タイトルを `fix:` で始め、`gh pr create --template bug_fix.md` で作る(還元先・完了条件の欄が出る)。`scripts/check-bug-fix-pr.sh` が欄の有無と未チェックの完了条件を検査する | `pr-bug-fix-check.yml` |

- `gradle` ではなく必ず `./gradlew` を使う。`02` のコード例を変えたら `src/` も同時に更新して通す。
- `docs/testing/trs/` は `./gradlew test` の入力でもある。`TraceabilityTest` が、pending でない全 AC にテストが紐づくことと、AC 番号が TRS 間で一意であることを検査する。TRS を編集したら Gradle も通す。
- コードブロックには必ず言語を指定する(MD040)。本文中の生 HTML は禁止(MD033)。表の罫線整列(MD060)と行長(MD013)は無効化済み。

## 書き方の規約

- 図は ASCII ではなく **Mermaid** を使う。
- サンプルコードは **Kotlin + Spring Boot 4.1.x**(TypeScript 等は使わない)。Spring Boot 4 ではテストスライスのパッケージ名等が 3.x と異なるため、確認できていない API はコードに書かず、公式リファレンスで確認するよう注記する。
- 型ガードレール(`02` §1): ドメインの値はプリミティブ(`String` / `Int` 等)で持たず、コンストラクタで検証する Value Object にする。状態は `sealed` 型で表し、`when` に `else` を書かない。`allWarningsAsErrors` により非網羅の `when` はビルドエラーになる。
- 期待値は仕様(TRS の AC)を正とする。既存コードの挙動から期待値を逆生成しない(`03`)。
- TRS の Open Issues は解決後も行を削除しない。AC / UC 番号は TRS 間で一意にする。

## 変更してはいけないもの(人間の承認が必要)

`01`・`04` に定めたとおり、AI は方針書・規程・期待値を単独で変更しない。変更は PR で人間の承認を得る(`.github/CODEOWNERS` 対象)。
特に、ゲートを通すために次を弱めない。

- `src/test/kotlin/com/example/architecture/`(Konsist による検査・テスト保護)
- `build.gradle.kts` の pitest 閾値・`excludedClasses`、`config/detekt/detekt.yml`、`.markdownlint-cli2.jsonc`、`.github/`
- テストの無効化(`@Disabled`、`xdescribe` 等)。意図的な無効化の手順は `04` §3.4.1
