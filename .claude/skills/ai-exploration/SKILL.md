---
name: ai-exploration
description: サンプルアプリ(TODO)に対する層3の AI 探索を、手元で一度だけ実行し、気づきの報告を出す。人が頼んだときだけ実行する。
disable-model-invocation: true
argument-hint: "[MAX_TURNS=… TIMEOUT_SEC=… MODEL=… BROWSER=… NO_SANDBOX=… MYSQL_PORT=… APP_PORT=… OUT_DIR=…]"
---

# ai-exploration

層3(探索・発見層)の AI 探索を、`examples/kotlin-spring/exploration/run-local.sh` で一度だけ実行する。手順書は `examples/kotlin-spring/docs/02b.md` の §3.1 である。

## 守ること

- **このセッションでは探索しない。** Playwright の道具を使わず、アプリの画面を自分で操作しない。探索は、スクリプトが起動する別の `claude -p` が行う。探索の入力を UC と AC の一覧に限る境界(§3.1.2)を、このセッションの文脈で崩さないためである。
- 気づきを Issue にしない。ファイルも直さない。
- 定期実行の仕組みにしない。人が頼んだときだけ実行する。
- 認証は、利用者の Claude Code のログインを使う。API キーは要らない。外す環境変数は `examples/kotlin-spring/docs/02b.md` の §3.1.4 に従う。

## 前提

前提は `examples/kotlin-spring/docs/02b.md` の §3.1.8 に従う。動いているアプリを再利用すると、その DB が書き換わる点も含む。満たしていなければ、スクリプトが止まる。

## 手順

1. 利用者の引数(`$ARGUMENTS`)を、次の環境変数に写す。この表が、変数と既定の正である。ここに無い名前は使わない。値の検証はスクリプトが行い、不正なら止まる。

   | 変数 | 意味 | 既定 |
   |---|---|---|
   | `MAX_TURNS` | 探索するエージェントの最大ターン数 | 100 |
   | `TIMEOUT_SEC` | 探索を止めるまでの秒数。時間切れは報告に終了コード 124 と出る | 1200 |
   | `MODEL` | 探索に使うモデルの ID | `claude-sonnet-5-5` |
   | `BROWSER` | Playwright の `--browser`。`chrome`・`firefox`・`webkit`・`msedge` | `chrome` |
   | `NO_SANDBOX` | 1 のとき `--no-sandbox` を付ける。root で動かす Linux 向け | 0 |
   | `MYSQL_PORT`・`APP_PORT` | MySQL とアプリのポート | 3306・8080 |
   | `OUT_DIR` | 出力先 | `examples/kotlin-spring/build/exploration/<日時>` |

2. リポジトリのルートから、スクリプトを**バックグラウンドで**実行する。MySQL と Gradle の起動を含み、探索で最大 `TIMEOUT_SEC` 秒かかるため、Bash の時間の上限を超えうる。終了の通知を待つ。

   ```bash
   examples/kotlin-spring/exploration/run-local.sh
   ```

   `MAX_TURNS` を小さくしすぎない。`prompt.md` は道具の呼び出しを約 40 回で切り上げて報告に移るよう指示している。それより小さいと、報告が返らずに終わる。小さい値は、動作確認のときだけにする。

   スクリプトの動作と、終了時に止める範囲は、`examples/kotlin-spring/docs/02b.md` の §3.1.2 から §3.1.4 に従う。

3. 終了したら、標準出力の報告と、出力先(`出力先:` の行)を利用者に示す。出力先の中身と、`docs/records/` への写し方は、`examples/kotlin-spring/docs/02b.md` の §3.1.3 に従う。
4. 終了コードが 0 でないときは、標準出力の備考(時間切れ、構造化された報告が返らない)と `claude-stderr.log` の要点を示す。ターン数や時間の上限に達した可能性があるときは、上限を上げて再実行するかを利用者に尋ねる。勝手に再実行しない。
5. 最後に、次を利用者に伝える。気づきは起票していない。再現できたものを、人が `examples/kotlin-spring/docs/02b.md` の §3.1.5 の表に従って扱う。

## 失敗したとき

- `エラー:` で始まる行は、スクリプトが検証や起動で止めた理由である。その内容を伝える。
- 自分で原因を直して探索を続けない。ポートの競合やブラウザの不在は、変数を変えて再実行するかを利用者に尋ねる。
