# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

回答・ドキュメントは日本語で書くこと。

## 概要

AI駆動開発向けテスト戦略の標準（`docs/testing/`）。成果物は Markdown で、アプリでもライブラリでもない。概要・読む順番は [README.md](./README.md)。
`01-test-policy` → `02a-types-and-pbt` → `02b-execution-and-mutation` → `02c-e2e` → `03-trs-guideline` → `04-regression-governance` → `05-adoption-guide` と `trs/_template.md` は相互にリンクしているので、見出しやファイル名を変えるときはリンク切れに注意する。
`src/` は `02a`・`02b` の Kotlin サンプルを実際にコンパイル・実行して検証するための最小プロジェクトで、`order` / `payment` は TRS の実例（ORD-001 / PAY-001）に対応するサンプル領域。製品コードではない。

## 品質ゲート（変更内容ごとに通すもの）

| 変更 | 実行するもの | CI |
|---|---|---|
| `*.md` | `npm ci && npm run lint`（`lint:md` = markdownlint-cli2、`lint:mermaid` = 全 mermaid ブロックを実レンダリング。Chrome を起動するので遅い）。`lint:md:fix` で自動修正 | `lint.yml` |
| `src/`・`build.gradle.kts`・`02a`・`02b` のコード例・`docs/testing/trs/` | `./gradlew detekt ktlintCheck test integrationTest`(`integrationTest` は Docker が必要)、続けて `./gradlew pitest`(閾値 80%、夜間の全体実行は 70%) | `kotlin-samples.yml` / `nightly-mutation.yml` |
| バグ修正 PR | タイトルを `fix:` で始め、`gh pr create --template bug_fix.md` で作る(還元先・完了条件の欄が出る)。`scripts/check-bug-fix-pr.sh` が欄の有無と未チェックの完了条件を検査する | `pr-bug-fix-check.yml` |

- `gradle` ではなく必ず `./gradlew` を使う。`02a`・`02b` のコード例を変えたら `src/` も同時に更新して通す。
- `docs/testing/trs/` は `./gradlew test` の入力でもある。`./gradlew test` は層1のみ(Spring・Docker 不要)で、Spring を起動するテストは `src/integrationTest` に置く(`ExecutionBoundaryTest` が検査)。`TraceabilityTest` が、pending でない全 AC にテストが紐づくことと、AC 番号が TRS 間で一意であることを検査する。TRS を編集したら Gradle も通す。
- コードブロックには必ず言語を指定する(MD040)。本文中の生 HTML は禁止(MD033)。表の罫線整列(MD060)と行長(MD013)は無効化済み。

## 書き方の規約

- 図は ASCII ではなく **Mermaid** を使う。
- サンプルコードは **Kotlin + Spring Boot 4.1.x**(TypeScript 等は使わない)。Spring Boot 4 ではテストスライスのパッケージ名等が 3.x と異なるため、確認できていない API はコードに書かず、公式リファレンスで確認するよう注記する。
- 型ガードレール(`02a` §1): ドメインの値はプリミティブ(`String` / `Int` 等)で持たず、コンストラクタで検証する Value Object にする。状態は `sealed` 型で表し、`when` に `else` を書かない。`allWarningsAsErrors` により非網羅の `when` はビルドエラーになる。
- PBT が失敗したら `02a` §2.3 の手順に従い、縮小後の反例を Example テストとして固定する。シード付き(`PropTestConfig(seed = …)`)のテストは再現確認だけに使い、コミットしない。
- 期待値は仕様(TRS の AC)を正とする。既存コードの挙動から期待値を逆生成しない(`03`)。
- TRS の Open Issues は解決後も行を削除しない。AC / UC 番号は TRS 間で一意にする。

## バックログ(Issue)の運用

- 優先度は `priority:high` / `medium` / `low` のラベルで表す。定義は各ラベルの説明を正とする。
- オーナーの判断が要る Issue には `needs-decision` ラベルを付け、判断が済んだら外す。付けるのは、選択肢のある判断(方式・位置・範囲の選択、仕様や期待値が未定のもの、方針書やゲートの設計変更)に限る。CODEOWNERS 対象のため PR の承認が要るだけで選択肢がないものには付けない。
- 何を決めるのかは、Issue 本文の「注意」欄に書く。
- grill-me でオーナーが選択肢を選んだ(合意した)Issue は、AI が決定内容をコメントに残し、確認を取らずに `needs-decision` を外す。外すのは対象 Issue 本人の判断が済んだときだけで、オーナーが未決のまま終えた論点は外さない。規模が大きければ、同じタイミングで 1 Issue 1 PR の単位に分割する。
- Issue の棚卸し(全件の優先度付けと `needs-decision` の抽出)は、セッション開始時と大きな PR のマージ後に行う。AI は頼まれるのを待たず、実施を提案する。棚卸しは読み取り専用で、ラベルの付け替えやクローズは指示があったときだけ行う。

## 変更してはいけないもの(人間の承認が必要)

`01`・`04` に定めたとおり、AI は方針書・規程・期待値を単独で変更しない。変更は PR で人間の承認を得る(`.github/CODEOWNERS` 対象)。
特に、ゲートを通すために次を弱めない。

- `src/test/kotlin/com/example/architecture/`(Konsist による検査・テスト保護)
- `build.gradle.kts` の pitest 閾値・`excludedClasses`、`config/detekt/detekt.yml`、`.markdownlint-cli2.jsonc`、`.github/`
- テストの無効化(`@Disabled`、`xdescribe` 等)。意図的な無効化の手順は `04` §3.4.1

## サブエージェントチーム(リーダー向けの運用)

変更種別ごとの役割は `.claude/agents/` に定義してある(`doc-author` / `kotlin-sample-engineer` / `spec-reviewer` / `gate-guard`)。Issue / PR の運用は `issue-triage` / `issue-autopilot` / `grill-me` のスキルで足りるため、エージェント化していない。`.claude/agents/` には README を置かない(エージェント定義として読み込まれる恐れがあるため)。運用はこの節に書く。

| 役割 | 担当 | 権限 |
|---|---|---|
| `doc-author` | 方針書(01〜05)・README・TRS の執筆 | 書き込み可 |
| `kotlin-sample-engineer` | `src/` と `02a`・`02b` のコード例の同期 | 書き込み可 |
| `spec-reviewer` | AC/UC 番号の一意性、期待値の出所、相互リンク、`02a`・`02b` と `src/` の同期、非公開情報の混入 | 読み取り専用 |
| `gate-guard` | 保護対象に触れていないか、ゲートを弱める変更がないか。結果は「ゲート弱体化: なし / あり」と「承認: 要 / 不要(理由)」の2軸 | 読み取り専用 |

1. 作成側(`doc-author` / `kotlin-sample-engineer`)は、リーダーが worktree で起動する。Issue 本文、ブランチ名、実施範囲(PR 作成まで / マージまで)をプロンプトで渡す。作成側はコミットしない。
2. 作成側が品質ゲートを通した後、`spec-reviewer` と `gate-guard` を並列に起動する。指摘があれば作成側に戻して直し、再度ゲートとレビューを通す。
   - レビュー役の終了後、リーダーはレビュー対象の worktree で `git status -s` と `git log` を起動前と比べる。差分があれば原因を調べ、拒否リスト(`.claude/hooks/readonly-bash.sh`)に足す。見えるのは git の状態だけで、GitHub 側の書き込みと worktree の外は検知できない。
   - 追加のレビューは、再レビューで重大度「高」が残るか新たに出たときだけ、修正して行う。中・低はリーダーが直接確認する。同じ「高」が2回続いたら、設計方針の誤りを疑ってアドバイザーに相談する。解消しなければ PR 本文で人間に委ねる。
3. 保護対象(下記「変更してはいけないもの」)に触れる作業の後は、`gate-guard` を必ず起動する。`tools` は書き込めるパスの制限ではない。読み取り専用のレビュー役(`spec-reviewer` / `gate-guard`)にだけ、frontmatter の PreToolUse フック(`.claude/hooks/readonly-bash.sh`、書き込み系 Bash の拒否リスト)を足す。書き込み可の役割にはフックを足さない。フックが動く前提は、信頼済みのフォルダであること、対話セッションであること(`-p` では登録されない)、Claude Code v2.1.295 以上であることである。検出は `gate-guard` の独立レビューと、PR の段階の CODEOWNERS 承認でも行う。
4. `gate-guard` が「ゲート弱体化: あり」を返したら、リーダーは作成側に戻して直させる。「承認: 要」を返したら、リーダーは PR 本文に該当ファイルと理由を書き、単独でマージしない。
5. 定義の反映は即時とは限らない。`subagent_type` が解決しないときはセッションを再起動する。
