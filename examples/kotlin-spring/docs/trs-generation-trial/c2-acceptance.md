# 受入条件

表記、水準、期待結果は `c2-factors.md` と `c2-constraints.md` に従う。期待値はここに写さず、C 番号で指す。

## ユースケース

AC の `xx` は UC の番号である。REQ-TODO-33 と REQ-TODO-34 は、入力の要件定義書に行が無い（c2-undecided.md の D-1）。

| ID | 名前 | 出典（要件 ID） |
|---|---|---|
| UC-01 | タスクを登録する | REQ-TODO-01, REQ-TODO-02, REQ-TODO-03, REQ-TODO-07, REQ-TODO-08, REQ-TODO-14, REQ-TODO-15, REQ-TODO-16, REQ-TODO-17, REQ-TODO-18, REQ-TODO-19, REQ-TODO-20, REQ-TODO-31 |
| UC-01-A1 | 入力が不正で、登録を拒否する | REQ-TODO-12, REQ-TODO-13, REQ-TODO-15, REQ-TODO-16, REQ-TODO-34 |
| UC-02 | タスクを完了にする | REQ-TODO-04, REQ-TODO-07, REQ-TODO-08, REQ-TODO-24 |
| UC-02-A1 | 対象が無く、完了を拒否する | REQ-TODO-33 |
| UC-03 | タスクを未完了に戻す | REQ-TODO-07, REQ-TODO-08, REQ-TODO-09, REQ-TODO-25 |
| UC-03-A1 | 対象が無く、戻す操作を拒否する | REQ-TODO-33 |
| UC-04 | 一覧を見る | REQ-TODO-05, REQ-TODO-06, REQ-TODO-07, REQ-TODO-21, REQ-TODO-22, REQ-TODO-23, REQ-TODO-26, REQ-TODO-27, REQ-TODO-28, REQ-TODO-32 |
| UC-05 | 完了済みの表示を切り替える | REQ-TODO-07, REQ-TODO-10, REQ-TODO-11, REQ-TODO-29, REQ-TODO-30 |
| UC-05-A1 | 不正な選択を拒否する | REQ-TODO-29 |

## 受入条件の一覧

「未決」と書いた AC は、OI の起票を待つ仮の AC である。期待値を書かず、起票後に `@pending:<OI-ID>` を付ける（03 §3.4.2 の ②）。

### UC-01: 登録

| AC ID | 題名（検証する振る舞い） | 関連 UC | 検証層 | 根拠 |
|---|---|---|---|---|
| AC-01-01 | タイトルを付けて作ったタスクは、そのタイトルを持ち、未完了で、期限なしである | UC-01 | 層1 | C-10, REQ-TODO-01, REQ-TODO-02, REQ-TODO-03 |
| AC-01-02 | 1 字から 50 字のタイトルを受け付ける | UC-01 | 層1 | C-11, C-12, C-13, REQ-TODO-15 |
| AC-01-03 | 字数を書記素クラスタで数え、結合文字と国旗の 50 字を受け付ける | UC-01 | 層1 | C-14, C-15, REQ-TODO-15, OI-008 |
| AC-01-04 | 51 字以上のタイトルを `TITLE_TOO_LONG` で拒否する | UC-01-A1 | 層1 | C-16, C-17, C-18, C-19, REQ-TODO-12, REQ-TODO-15 |
| AC-01-05 | 空と White_Space だけのタイトルを `TITLE_BLANK` で拒否する | UC-01-A1 | 層1 | C-20, C-21, REQ-TODO-12, REQ-TODO-16, REQ-TODO-31 |
| AC-01-06 | 表示されない文字だけのタイトルを `TITLE_BLANK` で拒否する | UC-01-A1 | 層1 | C-22, OI-017 |
| AC-01-07 | 組になる文字だけのタイトルを `TITLE_BLANK` で拒否する | UC-01-A1 | 層1 | C-23, OI-017 |
| AC-01-08 | タイトルの両端の White_Space を除いて保存する | UC-01 | 層1 | C-30, REQ-TODO-17, REQ-TODO-31 |
| AC-01-09 | タイトルの両端の表示されない文字を除いて保存する | UC-01 | 層1 | C-31, C-32, C-33, C-41, OI-017 |
| AC-01-10 | タイトルの途中の空白と表示されない文字を残す | UC-01 | 層1 | C-34, C-35, OI-017 |
| AC-01-11 | 最後の表示される文字の直後に続く組になる文字を残す | UC-01 | 層1 | C-36, C-37, C-42, OI-017 |
| AC-01-12 | 作用する相手が無い組になる文字と、範囲の隣の文字を除く | UC-01 | 層1 | C-38, C-39, C-40, C-43, OI-017 |
| AC-01-13 | 字数は、両端を除いた後の長さで判定する | UC-01 | 層1 | C-45, C-46, C-47, C-48, OI-004, OI-017 |
| AC-01-14 | タイトルの途中の空白を 1 字に数える | UC-01-A1 | 層1 | C-49, OI-008, OI-017 |
| AC-01-15 | タイトルを Unicode 正規化せずに保持する | UC-01 | 層1 | C-44, OI-008 |
| AC-01-16 | 実在する暦日の期限を、過去の日も含めて受け付ける | UC-01 | 層1 | C-54, C-55, C-56, C-57, C-58, C-59, C-60, REQ-TODO-19, REQ-TODO-20, OI-005, OI-011 |
| AC-01-17 | 実在しない日の期限を `DUE_DATE_INVALID` で拒否する | UC-01-A1 | 層1 | C-61, OI-005 |
| AC-01-18 | 0000 年の期限を `DUE_DATE_INVALID` で拒否する | UC-01-A1 | 層1 | C-62, OI-015 |
| AC-01-19 | 時刻つきの期限を `DUE_DATE_INVALID` で拒否する | UC-01-A1 | 層1 | C-63, REQ-TODO-19, OI-005 |
| AC-01-20 | `YYYY-MM-DD` でない形式の期限を `DUE_DATE_INVALID` で拒否する | UC-01-A1 | 層1 | C-64, OI-005 |
| AC-01-21 | 空文字列の期限を `DUE_DATE_INVALID` で拒否する | UC-01-A1 | 層1 | C-52, OI-012 |
| AC-01-22 | タイトルと期限の両方が不正なとき、理由をすべて集める | UC-01-A1 | 層1 | C-65, C-66, OI-011 |
| AC-01-23 | 理由のコードを、決められた文言に対応づける | UC-01-A1 | 層1 | C-121, C-122, C-123, REQ-TODO-13, OI-007 |
| AC-01-24 | 複数の理由があるとき、すべての文言を見せる | UC-01-A1 | 層1 | C-124, REQ-TODO-13, OI-007, OI-011 |
| AC-01-25 | 登録して一覧で読み戻すと、期限なしの未完了のタスクが増えている | UC-01 | 層2 Integration | C-10, C-50, REQ-TODO-01, REQ-TODO-03, REQ-TODO-08, OI-001 |
| AC-01-26 | 期限を `null` にして登録すると、期限なしで読み戻せる | UC-01 | 層2 Integration | C-51, REQ-TODO-02, OI-012 |
| AC-01-27 | 期限つきで登録すると、日付だけの期限と区分を読み戻せる | UC-01 | 層2 Integration | C-54, C-55, C-56, REQ-TODO-02, REQ-TODO-19, REQ-TODO-20 |
| AC-01-28 | 境界の暦日の期限を、保存して同じ値で読み戻せる | UC-01 | 層2 Integration | C-58, C-59, C-60, OI-011, OI-015 |
| AC-01-29 | 結合文字、国旗、組になる文字を含むタイトルを、符号位置まで同じ値で読み戻せる | UC-01 | 層2 Integration | C-13, C-14, C-15, C-35, C-36, C-37, C-44, REQ-TODO-08, OI-008 |
| AC-01-30 | 両端を除いたタイトルが保存され、読み戻せる | UC-01 | 層2 Integration | C-30, C-31, C-32, C-33, C-38, REQ-TODO-17, OI-017 |
| AC-01-31 | 登録したタスクは、接続を変えても DB から読み戻せる | UC-01 | 層2 Integration | REQ-TODO-08 |
| AC-01-32 | 同じタイトルのタスクを複数登録でき、別々のタスクとして読み戻せる | UC-01 | 層2 Integration | C-70, C-71, REQ-TODO-18 |
| AC-01-33 | 定義にない項目を無視して登録する | UC-01 | 層2 Integration | C-29, OI-013 |
| AC-01-34 | 登録しても、既存タスクと表示の選択は変わらない | UC-01 | 層2 Integration | C-10, REQ-TODO-01 |
| AC-01-35 | 空と空白だけのタイトルを 400 と `TITLE_BLANK` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-20, C-21, REQ-TODO-12, REQ-TODO-16, OI-001 |
| AC-01-36 | 表示されない文字と組になる文字だけのタイトルを 400 と `TITLE_BLANK` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-22, C-23, REQ-TODO-12, OI-017 |
| AC-01-37 | タイトルが欠落した本文を 400 と `TITLE_BLANK` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-24, REQ-TODO-12, OI-001 |
| AC-01-38 | 51 字以上のタイトルを 400 と `TITLE_TOO_LONG` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-16, C-17, C-18, C-19, C-48, REQ-TODO-12, OI-001 |
| AC-01-39 | 実在しない日の期限を 400 と `DUE_DATE_INVALID` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-61, REQ-TODO-12, OI-005 |
| AC-01-40 | 0000 年の期限を 400 と `DUE_DATE_INVALID` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-62, REQ-TODO-12, OI-015 |
| AC-01-41 | 時刻つきと別形式の期限を 400 と `DUE_DATE_INVALID` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-63, C-64, REQ-TODO-12, OI-005 |
| AC-01-42 | 空文字列の期限を 400 と `DUE_DATE_INVALID` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-52, REQ-TODO-12, OI-012 |
| AC-01-43 | 複数の理由を `codes` にすべて入れて拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-65, C-66, C-67, REQ-TODO-12, OI-011 |
| AC-01-44 | JSON として読めない本文を 400 と `MALFORMED_REQUEST` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-27, REQ-TODO-12, OI-001 |
| AC-01-45 | オブジェクトでない本文を 400 と `MALFORMED_REQUEST` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-28, REQ-TODO-12, OI-013 |
| AC-01-46 | タイトルの型が違う本文を 400 と `MALFORMED_REQUEST` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-25, REQ-TODO-12, OI-013 |
| AC-01-47 | 期限の型が違う本文を 400 と `MALFORMED_REQUEST` で拒否し、何も変わらない | UC-01-A1 | 層2 Integration | C-53, REQ-TODO-12, OI-013 |
| AC-01-48 | タイトルが `null` の登録（未決） | UC-01-A1 | 層2 Integration | C-26 |
| AC-01-49 | 型の違いとほかの理由が同時にある登録（未決） | UC-01-A1 | 層2 Integration | C-68, C-69 |
| AC-01-50 | 画面から、期限つきのタスクを登録できる | UC-01 | 層2 E2E（happy-path） | C-10, C-56, REQ-TODO-01, REQ-TODO-02, REQ-TODO-07 |
| AC-01-51 | 画面で期限を空のまま登録すると、拒否されず、期限なしのタスクになる | UC-01 | 層2 E2E（communication） | C-50, REQ-TODO-02, REQ-TODO-07, OI-012 |
| AC-01-52 | 日付を選ぶ部品で選んだ日が、期限として登録される | UC-01 | 層2 E2E（browser-operation） | C-56, REQ-TODO-14 |
| AC-01-53 | 入力が不正なとき、画面に理由が表示され、一覧は増えない | UC-01-A1 | 層2 E2E（display） | C-20, C-121, REQ-TODO-12, REQ-TODO-13 |
| AC-01-54 | 登録に成功すると、前の理由の表示が消え、タイトルと期限の入力欄が空に戻る | UC-01 | 層2 E2E（display） | OI-014 |

### UC-02: 完了

| AC ID | 題名（検証する振る舞い） | 関連 UC | 検証層 | 根拠 |
|---|---|---|---|---|
| AC-02-01 | 未完了のタスクを完了にでき、タイトルと期限は変わらない | UC-02 | 層1 | C-81, REQ-TODO-04 |
| AC-02-02 | 期限切れのタスクを完了にでき、区分は「完了」になる | UC-02 | 層1 | C-82, REQ-TODO-04, REQ-TODO-23 |
| AC-02-03 | 完了済みのタスクを完了にしても、何も変えず成功する | UC-02 | 層1 | C-83, REQ-TODO-24 |
| AC-02-04 | 完了にして一覧で読み戻すと、対象だけが「完了」になっている | UC-02 | 層2 Integration | C-81, REQ-TODO-04, REQ-TODO-08, OI-001 |
| AC-02-05 | 期限切れのタスクを完了にして読み戻すと、「完了」になっている | UC-02 | 層2 Integration | C-82, REQ-TODO-23 |
| AC-02-06 | 2 回目の完了も 2xx で、読み戻した内容は変わらない | UC-02 | 層2 Integration | C-83, REQ-TODO-24, OI-001 |
| AC-02-07 | 完了の状態は、接続を変えても DB から読み戻せる | UC-02 | 層2 Integration | C-81, REQ-TODO-08 |
| AC-02-08 | 存在しないタスクの完了を 404 と `TASK_NOT_FOUND` で拒否し、何も変わらない | UC-02-A1 | 層2 Integration | C-88, OI-006 |
| AC-02-09 | 識別子の形が不正な完了を 404 と `TASK_NOT_FOUND` で拒否し、何も変わらない | UC-02-A1 | 層2 Integration | C-90, OI-012 |
| AC-02-10 | 画面からタスクを完了にでき、行のラベルが「完了」になる | UC-02 | 層2 E2E（happy-path） | C-81, REQ-TODO-04, REQ-TODO-07 |

### UC-03: 未完了に戻す

| AC ID | 題名（検証する振る舞い） | 関連 UC | 検証層 | 根拠 |
|---|---|---|---|---|
| AC-03-01 | 完了のタスクを未完了に戻せ、タイトルと期限は変わらない | UC-03 | 層1 | C-84, REQ-TODO-09 |
| AC-03-02 | 期限を過ぎた完了のタスクを戻すと、区分は「期限切れ」になる | UC-03 | 層1 | C-85, REQ-TODO-09, REQ-TODO-21 |
| AC-03-03 | 未完了のタスクを未完了に戻しても、何も変えず成功する | UC-03 | 層1 | C-86, REQ-TODO-25 |
| AC-03-04 | 期限切れのタスクを未完了に戻しても、何も変えず成功する | UC-03 | 層1 | C-87, REQ-TODO-25 |
| AC-03-05 | 未完了に戻して一覧で読み戻すと、対象だけが「未完了」になっている | UC-03 | 層2 Integration | C-84, REQ-TODO-08, REQ-TODO-09, OI-001 |
| AC-03-06 | 期限を過ぎた完了のタスクを戻して読み戻すと、「期限切れ」になっている | UC-03 | 層2 Integration | C-85, REQ-TODO-09, REQ-TODO-21 |
| AC-03-07 | 未完了のタスクを戻す操作も 2xx で、読み戻した内容は変わらない | UC-03 | 層2 Integration | C-86, C-87, REQ-TODO-25, OI-001 |
| AC-03-08 | 戻した状態は、接続を変えても DB から読み戻せる | UC-03 | 層2 Integration | C-84, REQ-TODO-08 |
| AC-03-09 | 存在しないタスクを戻す操作を 404 と `TASK_NOT_FOUND` で拒否し、何も変わらない | UC-03-A1 | 層2 Integration | C-89, OI-006 |
| AC-03-10 | 識別子の形が不正な戻す操作を 404 と `TASK_NOT_FOUND` で拒否し、何も変わらない | UC-03-A1 | 層2 Integration | C-91, OI-012 |
| AC-03-11 | 戻したタスクは、未完了のタスクの中で規則どおりの位置に並ぶ | UC-03 | 層2 Integration | C-103, REQ-TODO-26, REQ-TODO-28 |
| AC-03-12 | 画面から完了のタスクを未完了に戻せ、行のラベルが「未完了」になる | UC-03 | 層2 E2E（happy-path） | C-84, REQ-TODO-07, REQ-TODO-09 |

### UC-04: 一覧

| AC ID | 題名（検証する振る舞い） | 関連 UC | 検証層 | 根拠 |
|---|---|---|---|---|
| AC-04-01 | 期限なしと、期限の日の終わりまでの未完了のタスクは「未完了」である | UC-04 | 層1 | C-72, C-73, REQ-TODO-06, REQ-TODO-21 |
| AC-04-02 | 期限の翌日 0 時からの未完了のタスクは「期限切れ」である | UC-04 | 層1 | C-74, REQ-TODO-06, REQ-TODO-21 |
| AC-04-03 | 期限切れの判定は Asia/Tokyo の日付を使う | UC-04 | 層1 | C-78, C-79, REQ-TODO-22 |
| AC-04-04 | 完了のタスクは、期限を過ぎていても「完了」である | UC-04 | 層1 | C-75, C-76, C-77, REQ-TODO-06, REQ-TODO-23 |
| AC-04-05 | どのタスクの区分も、3 区分のちょうど 1 つである | UC-04 | 層1 | C-05, C-72, C-73, C-74, C-75, C-76, C-77, REQ-TODO-32 |
| AC-04-06 | 期限が 0001-01-01 と 9999-12-31 のタスクの区分が決まる | UC-04 | 層1 | C-80, REQ-TODO-21, OI-011 |
| AC-04-07 | 期限が近い順に並ぶ | UC-04 | 層1 | C-93, REQ-TODO-26 |
| AC-04-08 | 期限のないタスクは最後に並ぶ | UC-04 | 層1 | C-94, REQ-TODO-26 |
| AC-04-09 | 期限切れのタスクは日付の昇順で期限内より前に並び、現在時刻によらない | UC-04 | 層1 | C-95, OI-003 |
| AC-04-10 | 期限が同じタスクは登録の古い順に並ぶ | UC-04 | 層1 | C-96, REQ-TODO-27 |
| AC-04-11 | 期限のないタスク同士は登録の古い順に並ぶ | UC-04 | 層1 | C-97, REQ-TODO-27 |
| AC-04-12 | 登録時刻が同じタスクは、番号の小さい順に並ぶ | UC-04 | 層1 | C-98, C-99, OI-009 |
| AC-04-13 | 未完了のタスクの後ろに完了済みをまとめる | UC-04 | 層1 | C-100, REQ-TODO-28, OI-003 |
| AC-04-14 | 完了済みの中も同じ規則で並ぶ | UC-04 | 層1 | C-101, REQ-TODO-28, OI-003 |
| AC-04-15 | 一覧の応答に、登録した全タスクのタイトル、期限、区分がある | UC-04 | 層2 Integration | C-10, C-54, C-72, C-74, C-75, REQ-TODO-05, REQ-TODO-06, OI-010 |
| AC-04-16 | 一覧の応答の区分が、期限の日の 23:59:59.999 と翌日 0 時で切り替わる | UC-04 | 層2 Integration | C-73, C-74, REQ-TODO-21 |
| AC-04-17 | サーバーの既定のタイムゾーンが UTC でも、Asia/Tokyo で判定した区分を返す | UC-04 | 層2 Integration | C-78, C-79, REQ-TODO-21, REQ-TODO-22 |
| AC-04-18 | 完了のタスクは、期限を過ぎても一覧の応答で「完了」である | UC-04 | 層2 Integration | C-77, REQ-TODO-23 |
| AC-04-19 | 保存したタスクを、一覧の応答が規則どおりの順に返す | UC-04 | 層2 Integration | C-93, C-94, C-95, C-96, C-97, C-100, C-101, REQ-TODO-26, REQ-TODO-27, REQ-TODO-28 |
| AC-04-20 | 登録時刻が同じタスクを、保存した番号の順に返す | UC-04 | 層2 Integration | C-98, C-99, OI-009 |
| AC-04-21 | 区分のラベルと `YYYY-MM-DD` の期限が画面に描画される | UC-04 | 層2 E2E（display） | C-72, C-74, C-75, C-126, REQ-TODO-05, REQ-TODO-06, REQ-TODO-07, OI-010 |
| AC-04-22 | 一覧の行が、規則どおりの順に画面に描画される | UC-04 | 層2 E2E（display） | C-94, C-100, REQ-TODO-05, REQ-TODO-26, REQ-TODO-28 |
| AC-04-23 | 期限なしの行の期限の欄の表示（未決） | UC-04 | 層2 E2E（display） | C-127 |

### UC-05: 完了済みの表示

| AC ID | 題名（検証する振る舞い） | 関連 UC | 検証層 | 根拠 |
|---|---|---|---|---|
| AC-05-01 | 選択が「表示」のとき、完了済みを含めて並べる | UC-05 | 層1 | C-111, REQ-TODO-10 |
| AC-05-02 | 選択が「非表示」のとき、完了済みだけを除く | UC-05 | 層1 | C-112, C-102, REQ-TODO-10 |
| AC-05-03 | 前回の選択がないとき、「表示」として扱う | UC-05 | 層1 | C-110, REQ-TODO-30 |
| AC-05-04 | 初めて取得する選択は「表示」である | UC-05 | 層2 Integration | C-110, REQ-TODO-30 |
| AC-05-05 | 「非表示」を保存すると、「非表示」を読み戻せる | UC-05 | 層2 Integration | C-114, REQ-TODO-11, REQ-TODO-29, OI-001 |
| AC-05-06 | 「非表示」の後に「表示」を保存すると、「表示」を読み戻せる | UC-05 | 層2 Integration | C-113, REQ-TODO-11, REQ-TODO-29 |
| AC-05-07 | 保存した選択は、接続を変えても DB から読み戻せる | UC-05 | 層2 Integration | C-114, REQ-TODO-29 |
| AC-05-08 | 選択を変えても、タスクは変わらない | UC-05 | 層2 Integration | C-113, C-114, REQ-TODO-10 |
| AC-05-09 | 未知の値の選択を 400 と `MALFORMED_REQUEST` で拒否し、保存済みの選択は変わらない | UC-05-A1 | 層2 Integration | C-115, OI-013 |
| AC-05-10 | 型が違う選択を 400 と `MALFORMED_REQUEST` で拒否し、保存済みの選択は変わらない | UC-05-A1 | 層2 Integration | C-116, OI-013 |
| AC-05-11 | JSON として読めない本文を 400 と `MALFORMED_REQUEST` で拒否し、保存済みの選択は変わらない | UC-05-A1 | 層2 Integration | C-117, OI-001 |
| AC-05-12 | 定義にない項目を無視して、選択を保存する | UC-05 | 層2 Integration | C-118, OI-013 |
| AC-05-13 | 選択の値が無い要求（未決） | UC-05-A1 | 層2 Integration | C-119 |
| AC-05-14 | 画面で完了済みを隠すと、完了のタスクの行だけが消える | UC-05 | 層2 E2E（happy-path） | C-112, REQ-TODO-07, REQ-TODO-10 |
| AC-05-15 | 隠した後に画面を開き直しても、完了済みは隠れたままである | UC-05 | 層2 E2E（communication） | C-112, REQ-TODO-11, REQ-TODO-29 |
| AC-05-16 | 選択を保存していない状態で画面を開くと、完了のタスクの行が見える | UC-05 | 層2 E2E（display） | C-110, REQ-TODO-30 |

## 拒否の水準と層2 の例（G3）

どの行も、期待結果は C 番号の「拒否 A」か「拒否 B」である。読み戻して、状態が変わらないことも確かめる。

| 水準 | 例の値 | AC | C | status | codes |
|---|---|---|---|---|---|
| F3-L1 | `""` | AC-01-35 | C-20 | 400 | `TITLE_BLANK` |
| F3-L2 | 半角空白 3 個 | AC-01-35 | C-21 | 400 | `TITLE_BLANK` |
| F3-L3 | ⟨U+3000⟩ | AC-01-35 | C-21 | 400 | `TITLE_BLANK` |
| F3-L4 | ⟨U+0009⟩ | AC-01-35 | C-21 | 400 | `TITLE_BLANK` |
| F3-L5 | ⟨U+000A⟩ | AC-01-35 | C-21 | 400 | `TITLE_BLANK` |
| F3-L6 | ⟨U+00A0⟩ | AC-01-35 | C-21 | 400 | `TITLE_BLANK` |
| F3-L7 | ⟨U+200B⟩ | AC-01-36 | C-22 | 400 | `TITLE_BLANK` |
| F3-L8 | ⟨U+FEFF⟩ | AC-01-36 | C-22 | 400 | `TITLE_BLANK` |
| F3-L9 | ⟨U+2060⟩ | AC-01-36 | C-22 | 400 | `TITLE_BLANK` |
| F3-L10 | ` ⟨U+200B⟩ ` | AC-01-36 | C-22 | 400 | `TITLE_BLANK` |
| F3-L11 | ⟨U+FE0F⟩ | AC-01-36 | C-23 | 400 | `TITLE_BLANK` |
| F3-L12 | ⟨U+E0067⟩ | AC-01-36 | C-23 | 400 | `TITLE_BLANK` |
| F3-L13 | `⟨U+FE0F⟩ ⟨U+E0067⟩` | AC-01-36 | C-23 | 400 | `TITLE_BLANK` |
| F2-L2 | `{}` | AC-01-37 | C-24 | 400 | `TITLE_BLANK` |
| F4-L6 | 基底文字 51 字 | AC-01-38 | C-16 | 400 | `TITLE_TOO_LONG` |
| F4-L7 | `か⟨U+3099⟩` を 51 個 | AC-01-38 | C-17 | 400 | `TITLE_TOO_LONG` |
| F4-L8 | 🇯🇵 を 51 個 | AC-01-38 | C-18 | 400 | `TITLE_TOO_LONG` |
| F4-L9 | 基底文字 52 字 | AC-01-38 | C-19 | 400 | `TITLE_TOO_LONG` |
| F7-L8 | `2025-02-29`、`2100-02-29` | AC-01-39 | C-61 | 400 | `DUE_DATE_INVALID` |
| F7-L9 | `2026-13-01` | AC-01-39 | C-61 | 400 | `DUE_DATE_INVALID` |
| F7-L10 | `2026-00-10`、`2026-01-00` | AC-01-39 | C-61 | 400 | `DUE_DATE_INVALID` |
| F7-L11 | `2026-04-31` | AC-01-39 | C-61 | 400 | `DUE_DATE_INVALID` |
| F7-L12 | `0000-01-01` | AC-01-40 | C-62 | 400 | `DUE_DATE_INVALID` |
| F7-L13 | `0000-02-29` | AC-01-40 | C-62 | 400 | `DUE_DATE_INVALID` |
| F7-L14 | `0000-12-31` | AC-01-40 | C-62 | 400 | `DUE_DATE_INVALID` |
| F7-L15 | `2026-11-10T00:00:00` | AC-01-41 | C-63 | 400 | `DUE_DATE_INVALID` |
| F7-L16 | `2026-11-10T00:00:00+09:00` | AC-01-41 | C-63 | 400 | `DUE_DATE_INVALID` |
| F7-L17 | `2026/11/10` | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F7-L18 | `20261110` | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F7-L19 | `2026-1-5` | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F7-L20 | ` 2026-11-10 ` | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F7-L21 | `10000-01-01` | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F7-L22 | `２０２６-１１-１０` | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F7-L23 | 半角空白 3 個 | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F7-L24 | `-0001-01-01` | AC-01-41 | C-64 | 400 | `DUE_DATE_INVALID` |
| F6-L3 | `""` | AC-01-42 | C-52 | 400 | `DUE_DATE_INVALID` |
| F3-L1 × F7-L9 | タイトル `""`、期限 `2026-13-01` | AC-01-43 | C-65 | 400 | `TITLE_BLANK`、`DUE_DATE_INVALID` |
| F4-L6 × F7-L12 | 51 字、期限 `0000-01-01` | AC-01-43 | C-66 | 400 | `TITLE_TOO_LONG`、`DUE_DATE_INVALID` |
| F2-L2 × F6-L3 | タイトルなし、期限 `""` | AC-01-43 | C-67 | 400 | `TITLE_BLANK`、`DUE_DATE_INVALID` |
| F1-L3 | `{"title":` | AC-01-44 | C-27 | 400 | `MALFORMED_REQUEST` |
| F1-L4 | 空の本文 | AC-01-44 | C-27 | 400 | `MALFORMED_REQUEST` |
| F1-L5 | `[]`、`"牛乳"`、`1` | AC-01-45 | C-28 | 400 | `MALFORMED_REQUEST` |
| F2-L3 | `1` | AC-01-46 | C-25 | 400 | `MALFORMED_REQUEST` |
| F2-L4 | `true` | AC-01-46 | C-25 | 400 | `MALFORMED_REQUEST` |
| F2-L5 | `["牛乳"]` | AC-01-46 | C-25 | 400 | `MALFORMED_REQUEST` |
| F2-L6 | `{"a":1}` | AC-01-46 | C-25 | 400 | `MALFORMED_REQUEST` |
| F6-L5 | `20261110` | AC-01-47 | C-53 | 400 | `MALFORMED_REQUEST` |
| F6-L6 | `true` | AC-01-47 | C-53 | 400 | `MALFORMED_REQUEST` |
| F6-L7 | `["2026-11-10"]` | AC-01-47 | C-53 | 400 | `MALFORMED_REQUEST` |
| F6-L8 | `{"y":2026}` | AC-01-47 | C-53 | 400 | `MALFORMED_REQUEST` |
| F11-L1 × F12-L2 | 登録に使われていない識別子 | AC-02-08 | C-88 | 404 | `TASK_NOT_FOUND` |
| F11-L1 × F12-L3 | `/tasks/abc/complete` | AC-02-09 | C-90 | 404 | `TASK_NOT_FOUND` |
| F11-L2 × F12-L2 | 登録に使われていない識別子 | AC-03-09 | C-89 | 404 | `TASK_NOT_FOUND` |
| F11-L2 × F12-L3 | `/tasks/abc/reopen` | AC-03-10 | C-91 | 404 | `TASK_NOT_FOUND` |
| F14-L3 | 未知の値（形は U-10） | AC-05-09 | C-115 | 400 | `MALFORMED_REQUEST` |
| F14-L4 | 型が定義と違う値（形は U-10） | AC-05-10 | C-116 | 400 | `MALFORMED_REQUEST` |
| F14-L5 | `{"x":` | AC-05-11 | C-117 | 400 | `MALFORMED_REQUEST` |

## E2E の選定（G5）

下層で代替できない理由を示す。文言の網羅、検証の網羅、異常系の網羅は E2E に置かない（層1 と Integration の AC が担う）。

| AC | 領域 | 下層で代替できない理由 |
|---|---|---|
| AC-01-50 | happy-path | 画面の入力から一覧の表示までの流れは、画面を通さないと確かめられない |
| AC-01-51 | communication | 空の入力欄の値を画面がどう送るかは、実配線でしか分からない。`""` を送ると 400 になる（C-52）ので、画面と API の間の取り決めを確かめる |
| AC-01-52 | browser-operation | 日付を選ぶ部品の操作は、実ブラウザでしか再現できない |
| AC-01-53 | display | 400 の応答が画面の理由の表示になるかは、実ブラウザの描画でしか確かめられない。代表の 1 水準だけを置く |
| AC-01-54 | display | 入力欄と理由の表示状態は、実ブラウザの描画でしか確かめられない |
| AC-02-10 | happy-path | 完了の操作から行のラベルの変化までの流れは、画面を通さないと確かめられない |
| AC-03-12 | happy-path | 戻す操作から行のラベルの変化までの流れは、画面を通さないと確かめられない |
| AC-04-21 | display | ラベルと期限の描画は、実ブラウザの確認である |
| AC-04-22 | display | 応答の順が行の描画順になることは、実ブラウザの確認である。並びの規則の網羅は層1 にある |
| AC-04-23 | display | 未決（U-04）。決定後に、下層で足りるかを見直す |
| AC-05-14 | happy-path | 切り替えの操作から行の表示の変化までの流れは、画面を通さないと確かめられない |
| AC-05-15 | communication | 画面を開くときに保存済みの選択が画面まで届くことは、画面と API と DB の実配線でしか確かめられない |
| AC-05-16 | display | 既定の選択が画面の表示状態になることは、実ブラウザの確認である |

## 値の組合せで表せない流れの Given/When/Then

```gherkin
Feature: タスク管理

  @AC-01-25 @layer:2 @integration
  Scenario: 登録して一覧で読み戻す
    Given タスクが 0 件である
    When  タイトル "牛乳を買う" で、期限の項目を省いて登録を要求する
    Then  応答のステータスは 2xx である
    And   一覧の応答のタスクは 1 件である
    And   そのタスクのタイトルは "牛乳を買う" である
    And   そのタスクに期限が無い
    And   そのタスクの区分は「未完了」である

  @AC-01-31 @layer:2 @integration
  Scenario: 登録したタスクを別の接続で読み戻す
    Given タイトル "牛乳を買う"、期限 "2030-05-15" のタスクを登録した
    When  アプリの DB 接続を開き直して一覧を取得する
    Then  タイトル "牛乳を買う"、期限 "2030-05-15"、区分「未完了」のタスクが 1 件ある

  @AC-01-34 @layer:2 @integration
  Scenario: 登録は既存タスクと表示の選択を変えない
    Given 完了のタスク A と未完了のタスク B があり、表示の選択は「非表示」である
    When  タイトル "牛乳を買う" のタスクの登録を要求する
    Then  応答のステータスは 2xx である
    And   A と B のタイトル、期限、区分は要求の前と同じである
    And   読み戻した表示の選択は「非表示」である

  @AC-01-48 @layer:2 @integration
  Scenario: タイトルが null の登録（期待値は OI の決定後に書く）
    Given タスクが 1 件ある
    When  タイトルを null にして登録を要求する

  @AC-01-49 @layer:2 @integration
  Scenario: 型の違いとほかの理由が同時にある登録（期待値は OI の決定後に書く）
    Given タスクが 1 件ある
    When  タイトルを数値にし、期限を "2026-13-01" にして登録を要求する

  @AC-01-50 @layer:2 @e2e:happy-path
  Scenario: 画面から期限つきのタスクを登録する
    Given 一覧の画面を開いている
    When  タイトル "牛乳を買う" と期限 2030-05-15 を入力して登録する
    Then  一覧に "牛乳を買う" の行が 1 行増える
    And   その行の期限は "2030-05-15" である
    And   その行のラベルは「未完了」である

  @AC-01-51 @layer:2 @e2e:communication
  Scenario: 画面で期限を空のまま登録する
    Given 一覧の画面を開いている
    When  タイトル "牛乳を買う" を入力し、期限を空のまま登録する
    Then  理由は表示されない
    And   一覧に "牛乳を買う" の行が 1 行増える
    And   その行のラベルは「未完了」である
    And   その行に YYYY-MM-DD の形の文字列が無い

  @AC-01-52 @layer:2 @e2e:browser-operation
  Scenario: 日付を選ぶ部品で期限を入力する
    Given 一覧の画面を開いている
    And   タイトルの入力欄に "牛乳を買う" を入力している
    When  日付を選ぶ部品で 2030-05-15 を選んで登録する
    Then  "牛乳を買う" の行の期限は "2030-05-15" である

  @AC-01-53 @layer:2 @e2e:display
  Scenario: 不正な入力の理由が画面に表示される
    Given 一覧の画面を開いていて、一覧にタスクが 1 件ある
    When  タイトルを空のまま登録する
    Then  画面に「タイトルを入力してください」と表示される
    And   一覧の行は 1 行のままである

  @AC-01-54 @layer:2 @e2e:display
  Scenario: 登録に成功すると理由と入力欄が消える
    Given 空のタイトルで登録して、画面に「タイトルを入力してください」と表示されている
    When  タイトル "牛乳を買う" と期限 2030-05-15 を入力して登録する
    Then  画面に「タイトルを入力してください」は表示されていない
    And   タイトルの入力欄は空である
    And   期限の入力欄は空である

  @AC-02-04 @layer:2 @integration
  Scenario: 完了にして一覧で読み戻す
    Given 未完了のタスク A（期限 2030-05-15）と、未完了のタスク B があり、表示の選択は「表示」である
    When  A の完了を要求する
    Then  応答のステータスは 2xx である
    And   一覧の応答で、A の区分は「完了」である
    And   A のタイトルと期限は要求の前と同じである
    And   B のタイトル、期限、区分は要求の前と同じである
    And   読み戻した表示の選択は「表示」である

  @AC-02-06 @layer:2 @integration
  Scenario: 2 回目の完了
    Given タスク A を完了にした
    When  A の完了をもう一度要求する
    Then  応答のステータスは 2xx である
    And   一覧の応答は、要求の前と同じである

  @AC-02-07 @layer:2 @integration
  Scenario: 完了の状態を別の接続で読み戻す
    Given タスク A を完了にした
    When  アプリの DB 接続を開き直して一覧を取得する
    Then  A の区分は「完了」である

  @AC-02-10 @layer:2 @e2e:happy-path
  Scenario: 画面からタスクを完了にする
    Given 一覧に、ラベルが「未完了」のタスク "牛乳を買う" の行がある
    When  その行を完了にする操作をする
    Then  その行のラベルは「完了」である

  @AC-03-05 @layer:2 @integration
  Scenario: 未完了に戻して一覧で読み戻す
    Given 完了のタスク A（期限 2030-05-15）と、完了のタスク B がある
    When  A を未完了に戻す要求をする
    Then  応答のステータスは 2xx である
    And   一覧の応答で、A の区分は「未完了」である
    And   A のタイトルと期限は要求の前と同じである
    And   B のタイトル、期限、区分は要求の前と同じである

  @AC-03-07 @layer:2 @integration
  Scenario Outline: 未完了のタスクを戻す操作
    Given タスク A の区分が "<区分>" である
    When  A を未完了に戻す要求をする
    Then  応答のステータスは 2xx である
    And   一覧の応答は、要求の前と同じである

    Examples:
      | 区分     |
      | 未完了   |
      | 期限切れ |

  @AC-03-08 @layer:2 @integration
  Scenario: 戻した状態を別の接続で読み戻す
    Given 完了のタスク A を未完了に戻した
    When  アプリの DB 接続を開き直して一覧を取得する
    Then  A の区分は「未完了」である

  @AC-03-11 @layer:2 @integration
  Scenario: 戻したタスクの位置
    Given 期限 2030-01-01 の完了のタスク A と、期限 2030-06-01 の未完了のタスク B と、期限なしの未完了のタスク C がある
    When  A を未完了に戻す要求をする
    Then  一覧の応答の順は A、B、C である

  @AC-03-12 @layer:2 @e2e:happy-path
  Scenario: 画面からタスクを未完了に戻す
    Given 一覧に、ラベルが「完了」のタスク "牛乳を買う" の行がある
    When  その行を未完了に戻す操作をする
    Then  その行のラベルは「未完了」である

  @AC-04-16 @layer:2 @integration
  Scenario Outline: 区分の切り替わる時刻
    Given 未完了のタスクの期限が 2026-11-10 である
    And   現在時刻が "<現在時刻>" である
    When  一覧を取得する
    Then  そのタスクの区分は "<区分>" である

    Examples:
      | 現在時刻                      | 区分     |
      | 2026-11-10T23:59:59.999+09:00 | 未完了   |
      | 2026-11-11T00:00:00+09:00     | 期限切れ |

  @AC-04-21 @layer:2 @e2e:display
  Scenario: 区分のラベルと期限の描画
    Given 期限 2030-05-15 の未完了のタスク、期限 2000-01-01 の未完了のタスク、期限 2000-01-01 の完了のタスクがある
    When  一覧の画面を開く
    Then  3 行のラベルは、それぞれ「未完了」「期限切れ」「完了」である
    And   3 行の期限は、それぞれ "2030-05-15"、"2000-01-01"、"2000-01-01" である

  @AC-04-22 @layer:2 @e2e:display
  Scenario: 行の描画の順
    Given 期限 2030-01-01 の完了のタスク A、期限なしの未完了のタスク B、期限 2030-06-01 の未完了のタスク C がある
    And   表示の選択は「表示」である
    When  一覧の画面を開く
    Then  行の順は C、B、A である

  @AC-04-23 @layer:2 @e2e:display
  Scenario: 期限なしの行の期限の欄（期待値は OI の決定後に書く）
    Given 期限なしのタスクがある
    When  一覧の画面を開く

  @AC-05-04 @layer:2 @integration
  Scenario: 初めて取得する選択
    Given 表示の選択が一度も保存されていない
    When  表示の選択を取得する
    Then  選択は「表示」である

  @AC-05-05 @layer:2 @integration
  Scenario: 「非表示」を保存して読み戻す
    Given 表示の選択が「表示」である
    When  「非表示」の保存を要求する
    Then  応答のステータスは 2xx である
    And   読み戻した選択は「非表示」である

  @AC-05-06 @layer:2 @integration
  Scenario: 「表示」に戻して読み戻す
    Given 表示の選択が「非表示」である
    When  「表示」の保存を要求する
    Then  応答のステータスは 2xx である
    And   読み戻した選択は「表示」である

  @AC-05-07 @layer:2 @integration
  Scenario: 選択を別の接続で読み戻す
    Given 「非表示」を保存した
    When  アプリの DB 接続を開き直して表示の選択を取得する
    Then  選択は「非表示」である

  @AC-05-08 @layer:2 @integration
  Scenario: 選択の変更はタスクを変えない
    Given 未完了のタスク A と完了のタスク B がある
    When  「非表示」の保存を要求する
    Then  A と B のタイトル、期限、完了の状態は要求の前と同じである

  @AC-05-13 @layer:2 @integration
  Scenario: 選択の値が無い要求（期待値は OI の決定後に書く）
    Given 表示の選択が「非表示」である
    When  値の項目が無い本文で保存を要求する

  @AC-05-14 @layer:2 @e2e:happy-path
  Scenario: 画面で完了済みを隠す
    Given 一覧に、「未完了」「期限切れ」「完了」のタスクの行が 1 行ずつあり、完了済みは表示している
    When  完了済みを隠す切り替えをする
    Then  「完了」の行は表示されていない
    And   「未完了」と「期限切れ」の行は表示されている

  @AC-05-15 @layer:2 @e2e:communication
  Scenario: 開き直しても隠れたままである
    Given 完了のタスクがあり、画面で完了済みを隠した
    When  画面を開き直す
    Then  「完了」の行は表示されていない

  @AC-05-16 @layer:2 @e2e:display
  Scenario: 初めて開いた画面では完了済みが見える
    Given 完了のタスクがあり、表示の選択が一度も保存されていない
    When  一覧の画面を開く
    Then  「完了」の行が表示されている
```

## 網羅の対応表

### 要件

| 要件 ID | AC |
|---|---|
| REQ-TODO-01 | AC-01-01, AC-01-25, AC-01-34, AC-01-50 |
| REQ-TODO-02 | AC-01-01, AC-01-26, AC-01-27, AC-01-50, AC-01-51 |
| REQ-TODO-03 | AC-01-01, AC-01-25 |
| REQ-TODO-04 | AC-02-01, AC-02-02, AC-02-04, AC-02-10 |
| REQ-TODO-05 | AC-04-15, AC-04-21, AC-04-22 |
| REQ-TODO-06 | AC-04-01, AC-04-02, AC-04-04, AC-04-15, AC-04-21 |
| REQ-TODO-07 | AC-01-50, AC-01-51, AC-02-10, AC-03-12, AC-04-21, AC-05-14 |
| REQ-TODO-08 | AC-01-25, AC-01-29, AC-01-31, AC-02-04, AC-02-07, AC-03-05, AC-03-08 |
| REQ-TODO-09 | AC-03-01, AC-03-02, AC-03-05, AC-03-06, AC-03-12 |
| REQ-TODO-10 | AC-05-01, AC-05-02, AC-05-08, AC-05-14 |
| REQ-TODO-11 | AC-05-05, AC-05-06, AC-05-15 |
| REQ-TODO-12 | AC-01-04, AC-01-05, AC-01-35〜47, AC-01-53 |
| REQ-TODO-13 | AC-01-23, AC-01-24, AC-01-53 |
| REQ-TODO-14 | AC-01-52 |
| REQ-TODO-15 | AC-01-02, AC-01-03, AC-01-04 |
| REQ-TODO-16 | AC-01-05, AC-01-35 |
| REQ-TODO-17 | AC-01-08, AC-01-30 |
| REQ-TODO-18 | AC-01-32 |
| REQ-TODO-19 | AC-01-16, AC-01-19, AC-01-27 |
| REQ-TODO-20 | AC-01-16, AC-01-27 |
| REQ-TODO-21 | AC-03-02, AC-03-06, AC-04-01, AC-04-02, AC-04-06, AC-04-16, AC-04-17 |
| REQ-TODO-22 | AC-04-03, AC-04-17 |
| REQ-TODO-23 | AC-02-02, AC-02-05, AC-04-04, AC-04-18 |
| REQ-TODO-24 | AC-02-03, AC-02-06 |
| REQ-TODO-25 | AC-03-03, AC-03-04, AC-03-07 |
| REQ-TODO-26 | AC-03-11, AC-04-07, AC-04-08, AC-04-19, AC-04-22 |
| REQ-TODO-27 | AC-04-10, AC-04-11, AC-04-19 |
| REQ-TODO-28 | AC-03-11, AC-04-13, AC-04-14, AC-04-19, AC-04-22 |
| REQ-TODO-29 | AC-05-05, AC-05-06, AC-05-07, AC-05-15 |
| REQ-TODO-30 | AC-05-03, AC-05-04, AC-05-16 |
| REQ-TODO-31 | AC-01-05, AC-01-08 |
| REQ-TODO-32 | AC-04-05 |

### 決定

| 決定 | AC |
|---|---|
| OI-001 | AC-01-25, AC-01-35, AC-01-37, AC-01-38, AC-01-44, AC-02-04, AC-02-06, AC-03-05, AC-03-07, AC-05-05, AC-05-11 |
| OI-003 | AC-04-09, AC-04-13, AC-04-14 |
| OI-004 | AC-01-13 |
| OI-005 | AC-01-16, AC-01-17, AC-01-19, AC-01-20, AC-01-39, AC-01-41 |
| OI-006（REQ-TODO-33） | AC-02-08, AC-03-09 |
| OI-007 | AC-01-23, AC-01-24 |
| OI-008 | AC-01-03, AC-01-14, AC-01-15, AC-01-29 |
| OI-009 | AC-04-12, AC-04-20 |
| OI-010 | AC-04-15, AC-04-21 |
| OI-011 | AC-01-16, AC-01-22, AC-01-24, AC-01-28, AC-01-43, AC-04-06 |
| OI-012 | AC-01-21, AC-01-26, AC-01-42, AC-01-51, AC-02-09, AC-03-10 |
| OI-013 | AC-01-33, AC-01-45, AC-01-46, AC-01-47, AC-05-09, AC-05-10, AC-05-12 |
| OI-014 | AC-01-54 |
| OI-015（REQ-TODO-34） | AC-01-18, AC-01-28, AC-01-40 |
| OI-017 | AC-01-06, AC-01-07, AC-01-09〜13, AC-01-14, AC-01-30, AC-01-36 |

OI-002 と OI-016 は Superseded なので、根拠にしない。Q-09 と Q-15 は要件の行が無いので、AC を置かない（c2-undecided.md の D-10）。
