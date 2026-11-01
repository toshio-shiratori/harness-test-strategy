# examples/kotlin-spring

`docs/testing/` の標準を、Kotlin + Spring Boot のプロジェクトに導入した一例です。標準の各文書に対応するコード例(`docs/<文書番号>.md`)をコンパイルしてテストを通し、サンプルの TRS(`docs/trs/`)の受入条件(AC)がテストに紐づいていることを検証します。

このディレクトリは導入の一例で、配布の対象ではありません。`todo` は TRS の実例(TODO-001)に対応するサンプル領域で、製品コードではありません。

## 技術構成

| 項目 | 内容 |
|---|---|
| 言語・フレームワーク | Kotlin 2.4 系、Spring Boot 4.1 系、JDK 21 |
| テスト | Kotest 6.2 系(PBT・データ駆動を含む)、Konsist(アーキテクチャ検査・テスト保護・TRS との紐づけ検査) |
| 静的解析 | detekt、ktlint、`allWarningsAsErrors` |
| ミューテーション | PIT。閾値は PR(変更クラス。`ALL` のときは層1の全対象)と main への push が 80%、夜間の実行(層1の全対象)が 70%。ドメイン層のクラスが対象に入るときは、ドメイン層だけの実行も同じ閾値で併走する |

バージョンは系統(minor)で書いています。正確な版は `build.gradle.kts` と Gradle wrapper(`gradle/wrapper/gradle-wrapper.properties`)を正とします。

## 動かし方

Gradle で自己完結したプロジェクトです。コマンドはこのディレクトリで実行します(以降の節も同じ)。

```bash
cd examples/kotlin-spring
./gradlew detekt ktlintCheck test
./gradlew integrationTest   # Docker が必要（Testcontainers）
./gradlew e2eTest           # Docker とブラウザ（Chromium）が必要。下の「E2E のセットアップ」を参照
./gradlew pitest            # ミューテーションテスト（閾値 80%）
./gradlew pitest -Ppitest.domainOnly=true   # ドメイン層だけ（閾値 80%。全体の実行と、ドメイン層のクラスを含む差分実行で CI も併走する）
```

CI は `.github/workflows/` で、同じコマンドを実行します(`e2eTest` は `kotlin-samples.yml` の `e2e` ジョブで、PR で実行します)。ドキュメントの検査(`npm run lint`)はリポジトリ直下で実行します。[CONTRIBUTING.md](../../CONTRIBUTING.md) を参照してください。

## pre-commit フック

コミットの前に静的検査と層1テストを流すフックを、リポジトリ直下の `.githooks/pre-commit` で管理しています。クローンごとに 1 度、リポジトリ直下で次を実行すると有効になります。

```bash
git config core.hooksPath .githooks
```

- 対象は、`examples/kotlin-spring/` と、`test` の入力になる 2 つのテンプレート(`docs/testing/trs/_template.md`、`docs/testing/templates/requirements.md`)です。ステージした変更に対象が含まれるときだけ、`./gradlew detekt ktlintCheck test` を実行します。
- 層1のテストは全件を流します。Gradle には変更に影響するテストだけを選ぶ仕組みが無く、全件でも数秒で終わるためです。
- それ以外の変更(`docs/testing/` の方針書など)では何もしません。Markdown の検査は `npm run lint` の担当です。
- 一時的に省くときは `git commit --no-verify` を使います。PR の CI では省けません。
- 実行するのはワーキングツリーの内容で、ステージした内容だけではありません。
- `integrationTest`・`e2eTest`・`pitest` は、時間がかかるので含めません。PR の CI で実行します。

## E2E のセットアップ

E2E(`src/e2eTest`)は、Spring を起動して Testcontainers の MySQL につなぎ、Playwright for Java(Chromium、ヘッドレス)で `static/index.html` のタスク管理画面を操作します。`test`・`integrationTest`・`check` には含まれず、`./gradlew e2eTest` で明示的に実行します。

- Docker を起動しておきます。MySQL 8.4 のコンテナを使います。
- ブラウザは、`./gradlew e2eTest` が実行前に `playwrightInstall`(Playwright CLI の `install chromium`)を呼んで取得します。取得済みならダウンロードを省きます(CLI は毎回起動します)。`playwrightInstall` は単独でも実行できます。

  ```bash
  ./gradlew playwrightInstall
  ```

- 取得先は Playwright の既定のキャッシュです。macOS は `~/Library/Caches/ms-playwright`、Linux は `~/.cache/ms-playwright` です。`PLAYWRIGHT_BROWSERS_PATH` で変えられます。
- 初回は Chromium と Chrome Headless Shell をダウンロードします。展開後のディスク使用量は約 560 MiB です(macOS arm64 で Playwright 1.63.0 の取得物を `du -sh` で測った値。環境で変わります)。
- Playwright は `Playwright.create()` で Chromium・Firefox・WebKit をすべて取得しようとします。そのため `e2eTest` には `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` を渡し、Chromium だけにしています。
- Linux の CI などで OS の依存ライブラリが足りないときは、Playwright の `install-deps` を使います(Playwright for Java の公式ドキュメントを参照)。

## サンプルアプリを手元で動かす

テストが検証している挙動を、実際の画面で確かめる手順です。

`src/main/resources/application.properties` には DB 接続の設定がないため、MySQL を自分で用意し、接続設定を環境変数で渡します。設定ファイルは変えません。

前提は、JDK 21、Docker、空いているポート 3306 と 8080 です(使用中なら、下の `-p` と `SPRING_DATASOURCE_URL` のポートを合わせて変えます。アプリのポートは `SERVER_PORT` で変えられます)。次の環境で確かめました。macOS(Apple Silicon、arm64)、Docker 29.3.1、OpenJDK 21。ただし、ポート 3306 と 8080 が使用中だったため、確かめたときは MySQL を 3307、アプリを 8081 にしました。

1. MySQL 8.4 を起動します。パスワード `sample` はローカル確認用の値で、他の用途に使い回しません。

   ```bash
   docker run -d --rm --name harness-sample-mysql \
     -e MYSQL_ROOT_PASSWORD=sample -e MYSQL_DATABASE=sample \
     -p 3306:3306 mysql:8.4

   # 接続を受け付けるまで待つ(初回はイメージの取得があるため時間がかかる)
   until docker exec harness-sample-mysql mysqladmin ping -h127.0.0.1 -uroot -psample --silent 2>/dev/null; do sleep 2; done
   ```

2. 接続設定を環境変数で渡して起動します。起動時に Flyway が `task`・`task_id_sequence`・`completed_visibility` のテーブルを作ります。`Started ApplicationKt` が出たら起動済みです。

   ```bash
   SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/sample' \
   SPRING_DATASOURCE_USERNAME=root \
   SPRING_DATASOURCE_PASSWORD=sample \
   ./gradlew bootRun
   ```

   環境変数を渡さずに起動すると、`Failed to configure a DataSource: 'url' attribute is not specified` というエラーで、終了コード 1 で失敗します(`application.properties` に DB の設定がないため)。

3. ブラウザで <http://localhost:8080/> を開きます。タイトルと、日付を選ぶ部品で期限(省略できます)を入れて「登録」を押すと、一覧にタスクが「未完了」か「期限切れ」のラベルつきで現れます。「完了にする」「未完了に戻す」で状態を切り替え、「完了済みを隠す」で一覧から完了済みを外せます。この選択は DB に保存され、画面を開き直しても保たれます。画面の検証はせず、API の拒否の理由(`codes`)を文言にして表示します。

4. 終わったら、アプリを `Ctrl-C` で止め、コンテナを止めます(`--rm` を付けたので、止めると削除されます)。

   ```bash
   docker stop harness-sample-mysql
   ```

API の例です。AC は [TODO-001](docs/trs/TODO-001-task-management.md) の受入条件です。`curl` で確かめられます。

| 操作 | HTTP ステータス | 本文 | AC |
|---|---|---|---|
| `POST /tasks` `{"title":"牛乳を買う","dueDate":"2026-11-30"}` | 2xx(この実装は 201) | 登録したタスク(`status` は `OPEN`) | AC-01-15 |
| `POST /tasks` `{"title":""}` | 400 | `codes` は `["TITLE_BLANK"]` | AC-01-17 |
| `POST /tasks` `{"title":123}` | 400 | `codes` は `["MALFORMED_REQUEST"]`(型の自動変換はしない) | AC-01-29 |
| `POST /tasks` `{"title":"","dueDate":"2026-02-30"}` | 400 | `codes` は `TITLE_BLANK` と `DUE_DATE_INVALID`(順序は問わない) | AC-01-28 |
| `POST /tasks/{id}/complete`(存在しない `id`) | 404 | `codes` は `["TASK_NOT_FOUND"]` | AC-02-03 |
| `POST /tasks/{id}/complete`(完了済みの `id`) | 2xx(この実装は 200) | 何も変わらない | AC-02-07 |
| `GET /tasks` | 2xx(この実装は 200) | 区分(`category`)つきの一覧。保存された選択に従って絞り込み、期限が近い順に並ぶ | AC-04-03、AC-05-06 |
| `PUT /settings/completed-visibility` `{"visibility":"HIDE"}` | 2xx(この実装は 200) | 完了済みを一覧から外す選択を保存する | AC-06-04、AC-06-06 |

```bash
curl -i -X POST http://localhost:8080/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"牛乳を買う","dueDate":"2026-11-30"}'
```

- API のパス、項目名、値の綴りと、画面の部品の配置、`data-testid` は AC ではなく設計です。AC が定めるのは、成功が 2xx であること(201 と 200 はこの実装の選択)、拒否の 400 と 404、`codes` の値です。
- 画面について AC が定めるのは、次の 3 つです。区分のラベル(「未完了」「完了」「期限切れ」)、期限の表示形式(`YYYY-MM-DD`)、理由の文言です([TODO-001 の OI-007・OI-010](docs/trs/TODO-001-task-management.md))。
- 期限切れの判定は、サーバーの既定のタイムゾーンやブラウザのタイムゾーンによらず、Asia/Tokyo の日付で行います(AC-04-04)。

## 配置

| パス | 内容 |
|---|---|
| `src/` | サンプルのソースとテスト(層1は `src/test`、統合テストは `src/integrationTest`、E2E は `src/e2eTest`) |
| `docs/01.md` | 01 基本方針書の実現例(G1〜G5 をこのサンプルがどう実現しているか) |
| `docs/02a.md` | 02a 型設計と PBT の Kotlin のコード例(`src/` と同期。変えるときは両方を更新する) |
| `docs/02b.md` | 02b 層1の実行境界とミューテーションの実装(設定・コード例・実測。`src/`・`build.gradle.kts` と同期) |
| `docs/02c.md` | 02c E2E 最小化原則の実例(タスク管理画面の E2E) |
| `docs/03.md` | 03 TRS ガイドラインのサンプルでの実現 |
| `docs/04.md` | 04 リグレッション運用規程のサンプルでの実装 |
| `exploration/` | 層3の AI 探索の指示・報告の型・実行スクリプト。手元で動かすスキルは `.claude/skills/ai-exploration/`(手順書は `docs/02b.md` の §3.1) |
| `docs/requirements/` | サンプルの要件定義書(TODO-001)。TRS の入力で、`TraceabilityTest` が要件 ID と US/UC の出典の対応を検査する |
| `docs/trs/` | サンプルの TRS(TODO-001)。`TraceabilityTest` が読む |
| `config/` | detekt の設定と、Konsist の検査を確かめる違反例 |
| `build.gradle.kts` | ビルドと、PIT・detekt の設定 |

TRS のテンプレート(`_template.md`)は標準の側にあり、`TraceabilityTest` は `../../docs/testing/trs/_template.md` を読みます。
