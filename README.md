# harness-test-strategy

AI 駆動開発のためのテスト戦略の標準です。AI が大量にコードを生成しても、人間のレビュー速度に頼らず品質を保てるようにするため、機械的に検証できるガードレール(テストハーネス)の方針・技術標準・運用規程を文書にまとめています。

成果物は `docs/testing/` の Markdown です。アプリやライブラリではありません。`src/` は、`02a`・`02b` に載せた Kotlin のサンプルが実際にコンパイルされテストを通ることを保証する、検証用のプロジェクトです。

## 対象読者

AI(コーディングエージェント)を使った開発に、テスト戦略とガバナンスを導入したいチームです。

## 読む順番

| 順 | 文書 | 内容 |
|---|---|---|
| 1 | [01 基本方針書](docs/testing/01-test-policy.md) | 基本理念、W字モデル、テストの層構造 |
| 2a | [02a 型設計とPBT](docs/testing/02a-types-and-pbt.md) | 型で誤りを書けなくする設計、PBT(AIガードレール＆テストアーキテクチャ標準 1/3) |
| 2b | [02b 層1の実行境界とミューテーション](docs/testing/02b-execution-and-mutation.md) | 層1と Integration の実行境界、ミューテーションテスト、AI 向けガードレール設定(同 2/3) |
| 2c | [02c E2E 最小化原則](docs/testing/02c-e2e.md) | E2E を許可する領域、審査、予算(同 3/3) |
| 3 | [03 TRS 策定ガイドライン](docs/testing/03-trs-guideline.md) | テスト要件仕様書(TRS)を、期待値の正(SSOT)にする方法 |
| 4 | [04 リグレッション＆AIヒーリング運用規程](docs/testing/04-regression-governance.md) | 不具合の最速層への還元(Shift-Left)、AI による自動修復の範囲、E2E の破棄基準 |
| 5 | [05 既存プロジェクトへの導入手順書](docs/testing/05-adoption-guide.md) | 既存の仕組みとの重複を診断し、現状をベースラインに固定して段階的に導入する手順 |
| - | [TRS テンプレート](docs/testing/trs/_template.md) | 新しい TRS を書くときの雛形 |
| - | [ORD-001](docs/testing/trs/ORD-001-add-item.md) / [PAY-001](docs/testing/trs/PAY-001-payment-state-transition.md) / [PAY-002](docs/testing/trs/PAY-002-payment-persistence.md) | TRS の実例 |

## 中心となる考え方

- 誤りは、できるだけ上流の速い層で止める。検出は「書けない(型)、すぐ落ちる(Unit / PBT)、結合で落ちる、探索で見つかる」の順に遅くなり、コストが増える。
- 期待値の正は、人間が合意した仕様(TRS の受入条件(AC))にある。AI は既存コードの挙動から期待値を逆生成しない。
- テストの質はカバレッジではなくミューテーションテストで確かめる。
- AI は方針書・規程・期待値を単独で変更しない。変更は PR で人間が承認する。

## `src/`(サンプルの検証用プロジェクト)

`order` と `payment` は、TRS の実例(ORD-001 / PAY-001 / PAY-002)に対応するサンプル領域です。製品コードではありません。

| 項目 | 内容 |
|---|---|
| 言語・フレームワーク | Kotlin 2.4 系、Spring Boot 4.1 系、JDK 21 |
| テスト | Kotest 6.2 系(PBT・データ駆動を含む)、Konsist(アーキテクチャ検査・テスト保護・TRS との紐づけ検査) |
| 静的解析 | detekt、ktlint、`allWarningsAsErrors` |
| ミューテーション | PIT。閾値は PR(変更クラスのみ)が 80%、夜間の全体実行が 70% |

バージョンは系統(minor)で書いています。正確な版は `build.gradle.kts` と Gradle wrapper(`gradle/wrapper/gradle-wrapper.properties`)を正とします。

## ローカルでの確認

```bash
# ドキュメント(markdownlint と Mermaid の構文検査)
npm ci && npm run lint

# サンプルコード(静的解析、テスト、ミューテーションテスト)
./gradlew detekt ktlintCheck test
./gradlew integrationTest   # Docker が必要（Testcontainers）
./gradlew e2eTest           # Docker とブラウザ（Chromium）が必要。下の「E2E のセットアップ」を参照
./gradlew pitest
```

CI は `.github/workflows/` で、同じコマンドを実行します(`e2eTest` は `kotlin-samples.yml` の `e2e` ジョブで、PR で実行します)。

### E2E のセットアップ

E2E(`src/e2eTest`)は、Spring を起動して Testcontainers の MySQL につなぎます。Playwright for Java(Chromium、ヘッドレス)で、`static/index.html` の注文追加フォームを操作します。`test`・`integrationTest`・`check` には含まれません。`./gradlew e2eTest` で明示的に実行します。

- Docker を起動しておきます。MySQL 8.4 のコンテナを使います。
- ブラウザは、`./gradlew e2eTest` が実行前に `playwrightInstall`(Playwright CLI の `install chromium`)を呼んで取得します。取得済みならダウンロードは省かれます(CLI は毎回起動します)。`playwrightInstall` は単独でも実行できます。

  ```bash
  ./gradlew playwrightInstall
  ```

- 取得先は Playwright の既定のキャッシュです。macOS は `~/Library/Caches/ms-playwright`、Linux は `~/.cache/ms-playwright` です。`PLAYWRIGHT_BROWSERS_PATH` で変えられます。
- 初回は Chromium と Chrome Headless Shell をダウンロードします。展開後のディスク使用量は約 560 MiB です(macOS arm64 で Playwright 1.63.0 の取得物を `du -sh` で測った値。環境で変わります)。
- Playwright は `Playwright.create()` で Chromium・Firefox・WebKit をすべて取得しようとします。そのため `e2eTest` には `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` を渡し、Chromium だけにしています。
- Linux の CI などで OS の依存ライブラリが足りないときは、Playwright の `install-deps` を使います(Playwright for Java の公式ドキュメントを参照)。

### サンプルアプリを手元で動かす

テストが検証している挙動を、実際の画面で確かめる手順です。`src/main/resources/application.properties` には DB 接続の設定がないため、MySQL を自分で用意し、接続設定を環境変数で渡します。設定ファイルは変えません。

前提は、JDK 21、Docker、空いているポート 3306 と 8080 です(使用中なら、下の `-p` と `SPRING_DATASOURCE_URL` のポートを合わせて変えます)。次の環境で確かめました。macOS(Apple Silicon、arm64)、Docker 29.3.1、OpenJDK 21。

1. MySQL 8.4 を起動します。パスワード `sample` はローカル確認用の値で、他の用途に使い回しません。

   ```bash
   docker run -d --rm --name harness-sample-mysql \
     -e MYSQL_ROOT_PASSWORD=sample -e MYSQL_DATABASE=sample \
     -p 3306:3306 mysql:8.4

   # 接続を受け付けるまで待つ(初回はイメージの取得があるため時間がかかる)
   until docker exec harness-sample-mysql mysqladmin ping -h127.0.0.1 -uroot -psample --silent 2>/dev/null; do sleep 2; done
   ```

2. 接続設定を環境変数で渡して起動します。起動時に Flyway が `payment` テーブルを作ります(`order` 側は DB を使いません)。`Started ApplicationKt` が出たら起動済みです。止めるときは `Ctrl-C` です。

   ```bash
   SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/sample' \
   SPRING_DATASOURCE_USERNAME=root \
   SPRING_DATASOURCE_PASSWORD=sample \
   ./gradlew bootRun
   ```

   環境変数を渡さずに起動すると、`Failed to configure a DataSource: 'url' attribute is not specified` というエラーで、終了コード 1 で失敗します（`application.properties` に DB の設定がないため）。

3. ブラウザで <http://localhost:8080/> を開きます。SKU と数量を入れて「追加」を押すと、結果欄に HTTP ステータスと `codes` が出ます。画面は API(`POST /orders/items`)の応答をそのまま表示するだけで、検証は API が行います。

4. 終わったら、アプリを `Ctrl-C` で止め、コンテナを止めます(`--rm` を付けたので、止めると削除されます)。

   ```bash
   docker stop harness-sample-mysql
   ```

入力と応答の例です。AC は [ORD-001](docs/testing/trs/ORD-001-add-item.md) の受入条件です。`curl` でも同じ応答を確認できます。

| SKU | 数量 | HTTP ステータス | `codes` | AC |
|---|---|---|---|---|
| `SKU-1` | `3` | 204(本文なし) | なし | AC-01-04 |
| `SKU-1` | `1000` | 400 | `OUT_OF_RANGE` | AC-01-05、AC-01-09 |
| `␣`(半角空白 1 文字) | `1000` | 400 | `BLANK` と `OUT_OF_RANGE`(順序は問わない) | AC-01-12 |

```bash
curl -i -X POST http://localhost:8080/orders/items \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1","quantity":1000}'
```

- 画面に出る状態表示（成功・失敗の別）、ステータスの表示、`codes` の 1 件ずつの表示は、AC ではなく画面の設計です([ORD-001 の OI-007](docs/testing/trs/ORD-001-add-item.md))。AC が定めるのは HTTP ステータスと `codes` の値までです。`detail` の文言は仕様ではありません。
- 数量を空欄にすると `MISSING_FIELD`(AC-01-11)、`1.5` のような小数を入れると `INVALID_JSON`（AC-01-10）が返ります（画面は数値でない入力を文字列として送るので、型の不一致として扱われます）。
- 追加処理はログ出力のみで、注文は保存されません(ORD-001 OI-004)。成功すると、`bootRun` のログに `add item sku=SKU-1 quantity=3` が出ます。

## 自分のプロジェクトで使うには

導入用のツールやパッケージはありません。文書を読み、自分のプロジェクトに合わせて取り入れます。

新規のプロジェクトは、次の順に進めます。

1. 01 と 02a〜02c を読み、層構造と型ガードレールの方針をチームで合意する。
2. TRS テンプレートを複製し、最初の機能の TRS を書く。実例の ORD-001 が参考になる。
3. 次を自分のプロジェクトにコピーして調整する。
   - `src/` の Konsist テスト(`architecture/` 配下)
   - `build.gradle.kts` の PIT・detekt 設定
   - `CLAUDE.md`
4. 不具合の運用は 04 とバグ報告テンプレート(`.github/ISSUE_TEMPLATE/bug_report.yml`)を使う。

すでにテスト・静的解析・CI があるプロジェクトは、次のように進めます。

- 上の手順 2 より前に、[05 導入手順書](docs/testing/05-adoption-guide.md) で既存の仕組みと重複しないかを診断します。以降は 05 の順に進めます。
- 上の手順 3 のコピーは、診断で「新規導入」または「補完」と判断した項目の、足りない部分に限ります。
- 現状値をベースラインに固定して悪化のみを禁止し、既存の仕組みに接続しながら段階的に取り入れます。
- `CLAUDE.md` は、既存のエージェント指示を置き換えません。必要な参照(本標準と 04 のヒーリング規程への参照など。02b §6)を追記します。

コード例は Kotlin + Spring Boot 前提ですが、原則そのものは言語非依存です。

## コントリビュート

- 方針・規程・期待値(`docs/testing/`)の変更は PR で行い、責任者(`.github/CODEOWNERS`)の承認を得ます。
- バグ修正の PR は、タイトルを `fix:` で始め、`gh pr create --template bug_fix.md` で作成します。
- AI エージェント向けの作業ルールは [CLAUDE.md](CLAUDE.md) にあります。

## ライセンス

[MIT](LICENSE)
