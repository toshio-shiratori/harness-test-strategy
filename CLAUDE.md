# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

回答・ドキュメントは日本語で書くこと。

## 概要

AI駆動開発向けテスト戦略の標準。成果物は Markdown で、アプリでもライブラリでもない。概要・読む順番は [README.md](./README.md)、検査とコントリビュートの流れは [CONTRIBUTING.md](./CONTRIBUTING.md)。

リポジトリは次の 3 つに分かれる。

| パス | 位置づけ | 配布 |
|---|---|---|
| `docs/testing/` | 標準の本体（方針書 `01`〜`05`、`trs/_template.md`、`templates/`）。**言語中立で自己完結**する。プログラミング言語のコード例を置かず、`docs/testing/` の外へリンクせず、このリポジトリ固有の語（`src/`、サンプル TRS の ID など）を書かない。`lint:portable` が検査する | する |
| `examples/kotlin-spring/` | 標準を Kotlin + Spring Boot に導入した一例。自己完結の Gradle プロジェクトで、Gradle のコマンドはすべてこのディレクトリで実行する。標準の各文書に対応する実現例・コード例は `docs/<文書番号>.md`（`01`・`02a`・`02b`・`02c`・`03`・`04`）にあり、`src/` と同期させる。サンプルの TRS は `docs/trs/` にある。`order` / `payment` は TRS の実例（ORD-001 / PAY-001 / PAY-002）に対応するサンプル領域で、製品コードではない | しない |
| `docs/records/` | このリポジトリ自身の運用・パイロットの記録 | しない |

`01-test-policy` → `02a-types-and-pbt` → `02b-execution-and-mutation` → `02c-e2e` → `03-trs-guideline` → `04-regression-governance` → `05-adoption-guide` と `trs/_template.md` は相互にリンクしているので、見出しやファイル名を変えるときはリンク切れに注意する。

## 品質ゲート（変更内容ごとに通すもの）

| 変更 | 実行するもの | CI |
|---|---|---|
| `*.md` | `npm ci && npm run lint`（`lint:md` = markdownlint-cli2、`lint:refs` = 02a〜02c の節番号参照とリンク、`lint:portable` = `docs/testing/` の言語中立・自己完結〔外へのリンク、言語のコードブロック、このリポジトリ固有の語。自己テスト付き〕、`lint:mermaid` = 全 mermaid ブロックを実レンダリング。Chrome を起動するので遅い）。`lint:md:fix` で自動修正 | `lint.yml` |
| `examples/kotlin-spring/` の `src/`・`build.gradle.kts`・`docs/*.md` のコード例・`docs/trs/`、`docs/testing/trs/_template.md` | `cd examples/kotlin-spring` してから `./gradlew detekt ktlintCheck test integrationTest`(`integrationTest` は Docker が必要)、続けて `./gradlew pitest` と `./gradlew pitest -Ppitest.domainOnly=true`(層1の全対象と、ドメイン層だけの2回。閾値はどちらも 80%、夜間の実行は 70%)。`src/e2eTest` や `src/main/resources/static/` を変えたときは `./gradlew e2eTest` も(Docker とブラウザ〔Chromium〕が必要。`examples/kotlin-spring/README.md` の「E2E のセットアップ」) | `kotlin-samples.yml`(`test` / `integration` / `e2e` ジョブ)/ `nightly-mutation.yml` |
| バグ修正 PR | タイトルを `fix:` で始め、`gh pr create --template bug_fix.md` で作る(還元先・完了条件の欄が出る)。`scripts/check-bug-fix-pr.sh` が欄の有無と未チェックの完了条件を検査する | `pr-bug-fix-check.yml` |

- `gradle` ではなく必ず `./gradlew` を使う。`docs/testing/` の `02a`・`02b` にはコード例を置かない。コード例は `examples/kotlin-spring/docs/*.md`（`02a.md`・`02b.md` など）にあり、`examples/kotlin-spring/src/` と同期させる。どちらかを変えたら、もう一方も同時に更新して通す。
- `examples/kotlin-spring/docs/trs/`（サンプルの TRS）と `docs/testing/trs/_template.md` は `./gradlew test` の入力でもある。`./gradlew test` は層1のみ(Spring・Docker 不要)で、Spring・Testcontainers を使うテストは `src/integrationTest` と、実ブラウザで操作する `src/e2eTest` にだけ置き、ブラウザ操作(Playwright)は `src/e2eTest` のみ(`ExecutionBoundaryTest` が検査)。層1で実行されないテスト(JUnit の `@Test` 等。層1は Kotest のランナーだけ)は `UnrunTestDetectionTest` が検査する。`TraceabilityTest` が(`src/test`・`src/integrationTest`・`src/e2eTest` を照合し)、pending でない全 AC にテストが紐づくことと、AC 番号が TRS 間で一意であることを検査する。TRS を編集したら Gradle も通す。
- コードブロックには必ず言語を指定する(MD040)。本文中の生 HTML は禁止(MD033)。表の罫線整列(MD060)と行長(MD013)は無効化済み。
- `docs/testing/` を変えたら `lint:portable` も通る必要がある。許可するコードブロックの言語は `gherkin` / `mermaid` / `markdown` / `text` だけで、禁止する語の一覧は `scripts/check-portable.mjs` にある。ツール名は括弧の例示（例: PIT、Stryker）に留める。

## 書き方の規約

- 図は ASCII ではなく **Mermaid** を使う。図は本文の幅(約 900px)に収める。`LR` で横に一列に並べて長くなるときは、`TB` にする・段に分けるなど向きや配置を変え、1 つのノードに文字を詰め込まない。図を追加・変更したら `npm run measure:mermaid -- <変更した md>` で幅・高さを測る(追加・変更した図に、幅が 900px を超える印が付かないこと。既存の図の印は対象外で、別の Issue で扱う。引数を省略するとリポジトリの全 Markdown が対象で、`lint` には含まれず、`--png` を付けると画像も確認できる)。手元の描画は GitHub の表示と一致しないため、mermaid の図と Markdown の表を追加・変更した PR は、マージ前に GitHub 上の表示も確かめる(手順は「サブエージェントチーム」の 6)。
- サンプルコードは `examples/kotlin-spring/` に置き、**Kotlin + Spring Boot 4.1.x**(TypeScript 等は使わない)。Spring Boot 4 ではテストスライスのパッケージ名等が 3.x と異なるため、確認できていない API はコードに書かず、公式リファレンスで確認するよう注記する。
- `docs/testing/` には、プログラミング言語のコード例、`docs/testing/` の外へのリンク、このリポジトリ固有の記述（サンプルのパス・ID・検査クラス名・PR/Issue 番号）を書かない。実現例・コード例・実測は `examples/kotlin-spring/docs/`、運用の記録は `docs/records/` に書く。
- 型ガードレール(規範は `docs/testing/02a-types-and-pbt.md` §1、Kotlin の実装は `examples/kotlin-spring/docs/02a.md`): ドメインの値はプリミティブ(`String` / `Int` 等)で持たず、コンストラクタで検証する Value Object にする。状態は `sealed` 型で表し、`when` に `else` を書かない。`allWarningsAsErrors` により非網羅の `when` はビルドエラーになる。
- PBT が失敗したら `02a` §2.3.1 の手順（Kotlin の例は `examples/kotlin-spring/docs/02a.md`）に従い、縮小後の反例を Example テストとして固定する。シード付き(`PropTestConfig(seed = …)`)のテストは再現確認だけに使い、コミットしない。
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

- `examples/kotlin-spring/src/test/kotlin/com/example/architecture/`(Konsist による検査・テスト保護)
- `examples/kotlin-spring/build.gradle.kts` の pitest 閾値・`excludedClasses`、`examples/kotlin-spring/config/detekt/detekt.yml`、`.markdownlint-cli2.jsonc`、`scripts/check-portable.mjs`(`lint:portable` の禁止語・許可リスト)と `package.json` の `lint` からの `lint:portable` の除外、`examples/kotlin-spring/docs/`・`docs/records/`、`.github/`
- テストの無効化(`@Disabled`、`xdescribe` 等)。意図的な無効化の手順は `04` §3.4.1

## サブエージェントチーム(リーダー向けの運用)

変更種別ごとの役割は `.claude/agents/` に定義してある(`doc-author` / `kotlin-sample-engineer` / `spec-reviewer` / `gate-guard`)。Issue / PR の運用は `issue-triage` / `issue-autopilot` / `grill-me` のスキルで足りるため、エージェント化していない。`.claude/agents/` には README を置かない(エージェント定義として読み込まれる恐れがあるため)。運用はこの節に書く。

| 役割 | 担当 | 権限 |
|---|---|---|
| `doc-author` | 方針書(01〜05)・README・CONTRIBUTING・TRS・`docs/records/`・`examples/kotlin-spring/docs/*.md` の地の文の執筆。`docs/testing/` の言語中立を保つ | 書き込み可 |
| `kotlin-sample-engineer` | `examples/kotlin-spring/` の `src/` と `examples/kotlin-spring/docs/*.md` のコード例の同期 | 書き込み可 |
| `spec-reviewer` | AC/UC 番号の一意性、期待値の出所、相互リンク、`examples/kotlin-spring/docs/*.md` と `examples/kotlin-spring/src/` の同期、`docs/testing/` の言語中立・自己完結、図を描画したときの見え方、非公開情報の混入 | 読み取り専用 |
| `gate-guard` | 保護対象に触れていないか、ゲートを弱める変更がないか。結果は「ゲート弱体化: なし / あり」と「承認: 要 / 不要(理由)」の2軸 | 読み取り専用 |

1. 作成側(`doc-author` / `kotlin-sample-engineer`)は、リーダーが worktree で起動する。Issue 本文、ブランチ名、実施範囲(PR 作成まで / マージまで)をプロンプトで渡す。作成側はコミットしない。
2. 作成側が品質ゲートを通した後、`spec-reviewer` と `gate-guard` を並列に起動する。指摘があれば作成側に戻して直し、再度ゲートとレビューを通す。
   - レビュー役の終了後、リーダーはレビュー対象の worktree で `git status -s` と `git log` を起動前と比べる。差分があれば原因を調べ、拒否リスト(`.claude/hooks/readonly-bash.sh`)に足す。見えるのは git の状態だけで、GitHub 側の書き込みと worktree の外は検知できない。
   - 追加のレビューは、再レビューで重大度「高」が残るか新たに出たときだけ、修正して行う。中・低はリーダーが直接確認する。同じ「高」が2回続いたら、設計方針の誤りを疑ってアドバイザーに相談する。解消しなければ PR 本文で人間に委ねる。
3. 保護対象(下記「変更してはいけないもの」)に触れる作業の後は、`gate-guard` を必ず起動する。`tools` は書き込めるパスの制限ではない。読み取り専用のレビュー役(`spec-reviewer` / `gate-guard`)にだけ、frontmatter の PreToolUse フック(`.claude/hooks/readonly-bash.sh`、書き込み系 Bash の拒否リスト)を足す。書き込み可の役割にはフックを足さない。フックが動く前提は、信頼済みのフォルダであること、対話セッションであること(`-p` では登録されない)、Claude Code v2.1.295 以上であることである。検出は `gate-guard` の独立レビューと、PR の段階の CODEOWNERS 承認でも行う。
4. `gate-guard` が「ゲート弱体化: あり」を返したら、リーダーは作成側に戻して直させる。「承認: 要」を返したら、リーダーは PR 本文に該当ファイルと理由を書き、単独でマージしない。
5. 定義の反映は即時とは限らない。`subagent_type` が解決しないときはセッションを再起動する。
6. mermaid の図か Markdown の表を追加・変更した PR は、マージ前に、リーダー(主セッション)が PR のブランチのファイル表示(`https://github.com/<owner>/<repo>/blob/<branch>/<path>`)を Chrome で開き、変更した図・表をスクリーンショットで確かめて、所見を PR 本文に書く(必須。それ以外の PR は不要)。理由は、GitHub の Mermaid は手元の `mmdc` より字幅が広く折り返し位置が変わること、図の右下に拡大縮小ボタンが重なること、表も GitHub 上で列幅・折り返しが崩れうることである。手元の `measure:mermaid` の結果は GitHub の表示と一致しないので代わりにならない。作成側(`doc-author`)は手元の描画で確かめ、報告に変更した図・表の一覧を書く。レビュー役(`spec-reviewer`)はブラウザを使わず、折り返しの最終確認をこの確認に委ねる。Chrome が使えないときは、その旨を PR 本文に書き、オーナーに確認を委ねる。

## Kizuna (Long-term Memory)

Memories are captured and recalled automatically via hooks. For active queries:

| Command | Description |
|---------|-------------|
| `kizuna search <query>` | Search this project's memories |
| `kizuna search <query> --cwd <path>` | Search another project's memories |
| `kizuna list --session <id>` | List chunks from a specific session |
| `kizuna stats` | Show database statistics |
