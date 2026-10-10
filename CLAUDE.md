# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

回答・ドキュメントは日本語で書くこと。

## 概要

AI駆動開発向けテスト戦略のドキュメント体系（`docs/testing/`）。コードベースではなく Markdown が成果物。
`01-test-policy` → `02-test-architecture` → `03-trs-guideline` → `04-regression-governance` と `trs/_template.md` は相互にリンクしているので、見出しやファイル名を変えるときはリンク切れに注意する。

## 品質ゲート

- `npm ci && npm run lint` が唯一のチェック（ローカルも CI `.github/workflows/lint.yml` も同一）。変更後は必ず通すこと。
  - `lint:md`: markdownlint-cli2（設定 `.markdownlint-cli2.jsonc`）。`npm run lint:md:fix` で自動修正。
  - `lint:mermaid`: 全 ` ```mermaid ` ブロックを mermaid-cli で実際にレンダリングして構文検査（Chrome を起動するので遅い）。
- `./gradlew test pitest`（テスト + ミューテーション閾値 80%）: `src/` はドキュメント内の Kotlin サンプルの検証用プロジェクト（CI `kotlin-samples.yml`）。`02-test-architecture.md` のコード例を変えたら `src/` も同時に更新して通すこと。`gradle` ではなく必ず `./gradlew` を使う。
- コードブロックには必ず言語を指定する（MD040）。表の罫線整列（MD060）と行長（MD013）は無効化済み。

## 書き方の規約

- 図は ASCII ではなく **Mermaid** を使う。
- サンプルコードは **Kotlin + Spring Boot 4.1.x**（TypeScript 等は使わない）。Spring Boot 4 ではテストスライスのパッケージ名等が 3.x と異なるため、確認できていない API はコードに書かず、公式リファレンスで確認するよう注記する。
- 期待値は仕様（TRS の AC）を正とする。既存コードの挙動から期待値を逆生成しない（`03-trs-guideline.md`）。
- TRS の Open Issues は解決後も行を削除しない。
- `01`・`04` に定めたとおり、AI は方針書・規程・期待値を単独で変更しない。変更は PR で人間の承認を得る。
- バグ修正の PR は、タイトルを `fix:` で始め、`gh pr create --template bug_fix.md` で作る（還元先・完了条件の欄が出る）。`PR Bug Fix Check`（`scripts/check-bug-fix-pr.sh`）が、欄の有無と完了条件の未チェックを検査する。
