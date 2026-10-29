# examples/kotlin-spring

`docs/testing/` の標準を、Kotlin + Spring Boot のプロジェクトに導入した一例です。標準の各文書に対応するコード例(`docs/<文書番号>.md`)をコンパイルしてテストを通し、サンプルの TRS(`docs/trs/`)の受入条件(AC)がテストに紐づいていることを検証します。

このディレクトリは導入の一例で、配布の対象ではありません。`order` / `payment` は TRS の実例(ORD-001 / PAY-001 / PAY-002)に対応するサンプル領域で、製品コードではありません。

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

## E2E のセットアップ

E2E(`src/e2eTest`)は、Spring を起動して Testcontainers の MySQL につなぎ、Playwright for Java(Chromium、ヘッドレス)で `static/index.html` の注文追加フォームを操作します。`test`・`integrationTest`・`check` には含まれず、`./gradlew e2eTest` で明示的に実行します。

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

前提は、JDK 21、Docker、空いているポート 3306 と 8080 です(使用中なら、下の `-p` と `SPRING_DATASOURCE_URL` のポートを合わせて変えます)。次の環境で確かめました。macOS(Apple Silicon、arm64)、Docker 29.3.1、OpenJDK 21。

1. MySQL 8.4 を起動します。パスワード `sample` はローカル確認用の値で、他の用途に使い回しません。

   ```bash
   docker run -d --rm --name harness-sample-mysql \
     -e MYSQL_ROOT_PASSWORD=sample -e MYSQL_DATABASE=sample \
     -p 3306:3306 mysql:8.4

   # 接続を受け付けるまで待つ(初回はイメージの取得があるため時間がかかる)
   until docker exec harness-sample-mysql mysqladmin ping -h127.0.0.1 -uroot -psample --silent 2>/dev/null; do sleep 2; done
   ```

2. 接続設定を環境変数で渡して起動します。起動時に Flyway が `payment` テーブルを作ります(`order` 側は DB を使いません)。`Started ApplicationKt` が出たら起動済みです。

   ```bash
   SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/sample' \
   SPRING_DATASOURCE_USERNAME=root \
   SPRING_DATASOURCE_PASSWORD=sample \
   ./gradlew bootRun
   ```

   環境変数を渡さずに起動すると、`Failed to configure a DataSource: 'url' attribute is not specified` というエラーで、終了コード 1 で失敗します(`application.properties` に DB の設定がないため)。

3. ブラウザで <http://localhost:8080/> を開きます。SKU と数量を入れて「追加」を押すと、結果欄に HTTP ステータスと `codes` が出ます。画面は API(`POST /orders/items`)の応答をそのまま表示するだけで、検証は API が行います。

4. 終わったら、アプリを `Ctrl-C` で止め、コンテナを止めます(`--rm` を付けたので、止めると削除されます)。

   ```bash
   docker stop harness-sample-mysql
   ```

入力と応答の例です。AC は [ORD-001](docs/trs/ORD-001-add-item.md) の受入条件です。`curl` でも同じ応答を確認できます。

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

- 画面に出る状態表示(成功・失敗の別)、ステータスの表示、`codes` の 1 件ずつの表示は、AC ではなく画面の設計です([ORD-001 の OI-007](docs/trs/ORD-001-add-item.md))。AC が定めるのは HTTP ステータスと `codes` の値までで、`detail` の文言は仕様ではありません。
- 数量を空欄にすると `MISSING_FIELD`(AC-01-11)、`1.5` のような小数を入れると `INVALID_JSON`(AC-01-10)が返ります(画面は数値でない入力を文字列として送るので、型の不一致として扱われます)。
- 追加処理はログ出力のみで、注文は保存されません(ORD-001 OI-004)。成功すると、`bootRun` のログに `add item sku=SKU-1 quantity=3` が出ます。

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
