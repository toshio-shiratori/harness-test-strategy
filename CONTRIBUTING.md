# コントリビュート

このリポジトリに変更を加える人向けの手引きです。標準の概要と導入方法は [README.md](README.md) にあります。AI エージェント向けの作業ルールは [CLAUDE.md](CLAUDE.md) にあります。

## リポジトリの構成

| パス | 位置づけ | 配布 |
|---|---|---|
| `docs/testing/` | **配布物**。標準の本体(方針書 01〜05、TRS テンプレート、バグ報告・バグ修正 PR の雛形)。言語やライブラリによらず導入できるよう、言語中立で自己完結させている。プログラミング言語のコード例を置かず、`docs/testing/` の外へリンクしない。中立性は `npm run lint` の `lint:portable` が検査する | する |
| `examples/kotlin-spring/` | **実装例**。標準を Kotlin + Spring Boot に導入した一例で、コンパイルしてテストを通す検証用プロジェクト。標準の各文書に対応するコード例・実現例・実測(`docs/<文書番号>.md`)とサンプルの TRS(`docs/trs/`)もここにある | しない |
| `docs/records/` | **記録**。このリポジトリ自身の運用やパイロットの記録 | しない |

導入先がコピーするのは `docs/testing/` だけです。

## ローカルでの確認

ドキュメントの検査は、ローカルも CI(`.github/workflows/lint.yml`)も同じコマンドです。

```bash
# markdownlint、節番号参照とリンク、docs/testing の言語中立・自己完結、Mermaid の構文検査
npm ci && npm run lint

# 図を追加・変更したとき: 各図の幅・高さを測る(幅が約 900px を超えると印が付く。--png で画像も出す)
npm run measure:mermaid -- docs/testing/01-test-policy.md
```

サンプルコード(静的解析、テスト、ミューテーションテスト、E2E)の確認は、`examples/kotlin-spring/` で Gradle を実行します。コマンドとセットアップは [examples/kotlin-spring/README.md](examples/kotlin-spring/README.md) にあります。

## コントリビュートの流れ

- 方針・規程・期待値(`docs/testing/`)の変更は PR で行い、責任者(`.github/CODEOWNERS`)の承認を得ます。
- バグ修正の PR は、タイトルを `fix:` で始め、`gh pr create --template bug_fix.md` で作成します。
