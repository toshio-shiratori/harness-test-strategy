# TRS-TODO-001: タスク管理（登録・完了・一覧と期限）

> この TRS は、合意済みの[要件定義書](../requirements/TODO-001-task-management.md)を入力に、AI が起草した。オーナーが 2026-11-01 に AC に合意し、承認した。手順は [03 §5](../../../../docs/testing/03-trs-guideline.md#5-策定フロー) の 1 から 3 に当たる。
> Open Issues（OI-001〜014）は、2026-11-01 にオーナーがすべて決めた。サンプルなので、一般的なやり方を選んでよいと指示を受けた。決定を AC に反映した。OI-013 は永続化・HTTP の実装の際に、OI-014 は E2E のミューテーション確認の際に起票し、それぞれ同日に決めた。OI-012 は Non-blocking として起票し、AI 探索の気づき（#273）を受けて決め、AC を足した。
> OI-015 は、OI-012 の実装の際に起票し、2026-11-03 にオーナーが決めた。0000 年の期限を受け付ける OI-012 の決定を、改めている。
> OI-016 は、AI 探索の 2 回目の気づきを受けて起票し、2026-11-03 にオーナーが決めた。表示されない文字を空白として扱わない OI-002 の決定を、改めている。OI-002 は Superseded にした。
> OI-017 は、#281 のレビューで見つかった境界を受けて起票し、2026-11-03 にオーナーが決めた。タイトルの前後から除くものを、空白だけから広げ、OI-016 の「前後の表示されない文字は除かず保存する」を改めている。同日に、レビューを受けてオーナーが決定を 2 点足した（組になる相手がいるときだけ残す、モンゴル文字の自由異体字セレクタも対象にする）。OI-016 は Superseded にした。
> 期待値の出所は、要件定義書の要件と、OI の決定である。要件から導けない値は、決定が出るまで AC に書かなかった。
> 起草と同時に課題化した。`@pending` に OI-ID が要るためである。決定が出たので、`@pending` はすべて外した。
> 要件の本文はこの TRS に写さず、出典（要件 ID）で指す。理由は [03 §2.1](../../../../docs/testing/03-trs-guideline.md#21-仕様レイヤー) にある。

## メタ情報

| 項目 | 内容 |
|---|---|
| TRS ID | `TODO-001` |
| 機能名 | タスク管理（登録・完了・一覧と期限） |
| ステータス | Approved |
| 仕様の正（SSOT） | [要件定義書 TODO-001](../requirements/TODO-001-task-management.md)（合意済み、2026-11-01）。要件にない値は Open Issues で決める |
| 関連 TRS | なし |
| 作成日 / 最終更新日 | 2026-11-01 / 2026-11-03 |
| 仕様承認者 | リポジトリのオーナー（承認日 2026-11-01） |

---

## 第1部：仕様レイヤー

### 1. ユーザーストーリー（US）

| ID | ストーリー | 出典（要件 ID） |
|---|---|---|
| US-01 | タスクの持ち主として、タイトルと任意の期限を付けてタスクを登録したい。なぜなら、やるべきことを残しておきたいから | REQ-TODO-01, REQ-TODO-02, REQ-TODO-03, REQ-TODO-07, REQ-TODO-08, REQ-TODO-12, REQ-TODO-13, REQ-TODO-14, REQ-TODO-15, REQ-TODO-16, REQ-TODO-17, REQ-TODO-18, REQ-TODO-19, REQ-TODO-20, REQ-TODO-31, REQ-TODO-34 |
| US-02 | タスクの持ち主として、タスクを完了にし、誤って完了にしたときは未完了に戻したい。なぜなら、進み具合を正しく残したいから | REQ-TODO-04, REQ-TODO-07, REQ-TODO-08, REQ-TODO-09, REQ-TODO-24, REQ-TODO-25, REQ-TODO-33 |
| US-03 | タスクの持ち主として、一覧で未完了・完了・期限切れを見分け、期限が近い順に見たい。なぜなら、次にやることを選びたいから | REQ-TODO-05, REQ-TODO-06, REQ-TODO-07, REQ-TODO-21, REQ-TODO-22, REQ-TODO-23, REQ-TODO-26, REQ-TODO-27, REQ-TODO-28, REQ-TODO-32 |
| US-04 | タスクの持ち主として、一覧で完了済みを隠したり表示したりし、その選択を次回も保ちたい。なぜなら、未完了のタスクに集中したいから | REQ-TODO-07, REQ-TODO-10, REQ-TODO-11, REQ-TODO-29, REQ-TODO-30 |

### 2. ユースケース（UC）

UC の番号が、AC ID の `xx` になる。この TRS は `01`〜`06` を使う。次の TRS は `07` から採番する。根拠は [03 §3.4.2](../../../../docs/testing/03-trs-guideline.md#342-各検査の詳細) の ④ である。

#### UC-01: タスクを登録する

| 項目 | 内容 |
|---|---|
| 関連 US | US-01 |
| 出典（要件 ID） | REQ-TODO-01, REQ-TODO-02, REQ-TODO-03, REQ-TODO-07, REQ-TODO-08, REQ-TODO-12, REQ-TODO-13, REQ-TODO-14, REQ-TODO-15, REQ-TODO-16, REQ-TODO-17, REQ-TODO-18, REQ-TODO-19, REQ-TODO-20, REQ-TODO-31, REQ-TODO-34 |
| アクター | タスクの持ち主（利用者は 1 人で、認証はない） |
| 事前条件 | なし |
| 事後条件 | 入力が正しければ、タスクが未完了で保存される。入力が不正なら、何も保存されず、理由が示される |

**基本フロー**

1. 持ち主が、画面でタイトルを入力する。期限は、日付を選ぶ部品で選ぶか、選ばない
2. 持ち主が、登録を要求する
3. システムが、タイトルの前後の空白と表示されない文字を除き、タイトルと期限を検証する
4. 正しければ、システムがタスクを未完了で保存する
5. 保存したタスクが、一覧に現れる
6. 画面が、前の拒否の理由の表示を消し、タイトルと期限の入力欄を空に戻す

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-01-A1 | 手順3 | タイトルが空、空白と表示されない文字だけ、または 50 字を超えるとき、保存せず、400 と理由コード（`TITLE_BLANK`、`TITLE_TOO_LONG`）を返し、画面に理由を示す |
| UC-01-A2 | 手順3 | 期限が `YYYY-MM-DD` として読めない、または実在しない日のとき、保存せず、400 と `DUE_DATE_INVALID` を返す |
| UC-01-A3 | 手順2 | 本文が JSON として読めないとき `MALFORMED_REQUEST`、タイトルが無いとき `TITLE_BLANK` で、保存せず 400 を返す |
| UC-01-A4 | 手順2 | 本文の型や形が定義と違うとき（タイトルが文字列でない、期限が文字列でないなど）、保存せず、400 と `MALFORMED_REQUEST` を返す。定義にない項目は無視する |
| UC-01-A5 | 手順3 | 期限が空文字列のとき、保存せず、400 と `DUE_DATE_INVALID` を返す。期限の項目を省くか null にしたときは、期限なしとして登録する |
| UC-01-A6 | 手順3 | 期限が 0001-01-01 より前（0000 年）のとき、保存せず、400 と `DUE_DATE_INVALID` を返す |

#### UC-02: タスクを完了にする

| 項目 | 内容 |
|---|---|
| 関連 US | US-02 |
| 出典（要件 ID） | REQ-TODO-04, REQ-TODO-07, REQ-TODO-08, REQ-TODO-24, REQ-TODO-33 |
| アクター | タスクの持ち主 |
| 事前条件 | なし |
| 事後条件 | 対象のタスクが存在するとき、そのタスクが完了になり、その状態が保存される。ほかのタスクと、タイトル・期限は変わらない。存在しないときの扱いは UC-02-A1 |

**基本フロー**

1. 持ち主が、一覧で未完了のタスクを選び、完了にする
2. システムが、タスクを完了にして保存する
3. 一覧で、そのタスクが完了と見える

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-02-A1 | 手順1 | 対象のタスクが存在しないとき、何も変えず、404 と `TASK_NOT_FOUND` を返す |
| UC-02-A2 | 手順2 | 対象がすでに完了のとき、何も変えず、2xx を返す |
| UC-02-A3 | 手順1 | 対象の識別子の形が不正なとき、何も変えず、404 と `TASK_NOT_FOUND` を返す（存在しないタスクと同じ） |

#### UC-03: タスクを未完了に戻す

| 項目 | 内容 |
|---|---|
| 関連 US | US-02 |
| 出典（要件 ID） | REQ-TODO-07, REQ-TODO-08, REQ-TODO-09, REQ-TODO-25, REQ-TODO-33 |
| アクター | タスクの持ち主 |
| 事前条件 | なし |
| 事後条件 | 対象のタスクが存在するとき、そのタスクが未完了になり、その状態が保存される。ほかのタスクと、タイトル・期限は変わらない。存在しないときの扱いは UC-03-A1 |

**基本フロー**

1. 持ち主が、一覧で完了のタスクを選び、未完了に戻す
2. システムが、タスクを未完了にして保存する
3. 一覧で、そのタスクが未完了か期限切れと見える

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-03-A1 | 手順1 | 対象のタスクが存在しないとき、何も変えず、404 と `TASK_NOT_FOUND` を返す |
| UC-03-A2 | 手順2 | 対象がすでに未完了のとき、何も変えず、2xx を返す |
| UC-03-A3 | 手順1 | 対象の識別子の形が不正なとき、何も変えず、404 と `TASK_NOT_FOUND` を返す（存在しないタスクと同じ） |

#### UC-04: 一覧でタスクの状態を見分ける

| 項目 | 内容 |
|---|---|
| 関連 US | US-03 |
| 出典（要件 ID） | REQ-TODO-05, REQ-TODO-06, REQ-TODO-07, REQ-TODO-21, REQ-TODO-22, REQ-TODO-23, REQ-TODO-32 |
| アクター | タスクの持ち主 |
| 事前条件 | なし |
| 事後条件 | 一覧の各タスクが、未完了・完了・期限切れのちょうど 1 つとして見える |

**基本フロー**

1. 持ち主が、一覧を開く
2. システムが、現在時刻を Asia/Tokyo の日付で判断し、各タスクの区分を決める
3. 一覧に、登録したタスクが区分つきで現れる

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-04-A1 | 手順3 | 登録したタスクが 1 件も無いとき、一覧は空である |

#### UC-05: 一覧の並び順

| 項目 | 内容 |
|---|---|
| 関連 US | US-03 |
| 出典（要件 ID） | REQ-TODO-07, REQ-TODO-26, REQ-TODO-27, REQ-TODO-28 |
| アクター | タスクの持ち主 |
| 事前条件 | 一覧に 2 件以上のタスクがある |
| 事後条件 | 一覧が、期限が近い順に並ぶ |

**基本フロー**

1. 持ち主が、一覧を開く
2. システムが、期限の日付の早い順に並べる。期限のないタスクは最後にする
3. 期限が同じタスク同士と、期限のないタスク同士は、登録の古い順に並べる。登録時刻が同じときは、登録ごとに増える番号の小さい方を古いとする
4. 完了済みを表示しているときは、未完了のタスクの後ろに完了済みをまとめ、それぞれに同じ規則で並べる

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-05-A1 | 手順2 | 期限を過ぎたタスク（完了済みを含む）が混ざるとき、現在時刻によらず、期限の日付の昇順なので、期限内のタスクより前に並ぶ |

#### UC-06: 完了済みの表示を切り替える

| 項目 | 内容 |
|---|---|
| 関連 US | US-04 |
| 出典（要件 ID） | REQ-TODO-07, REQ-TODO-10, REQ-TODO-11, REQ-TODO-29, REQ-TODO-30 |
| アクター | タスクの持ち主 |
| 事前条件 | なし |
| 事後条件 | 選択が保存され、次に一覧を開いたときも保たれる |

**基本フロー**

1. 持ち主が、一覧で完了済みを隠す（または表示する）ように切り替える
2. システムが、選択を保存する
3. システムが、保存した選択に従って一覧を作り、完了済みを隠す（または表示する）
4. 持ち主が一覧を開き直すと、前回の選択が保たれている

**代替フロー／例外フロー**

| ID | 分岐点 | 内容 |
|---|---|---|
| UC-06-A1 | 手順4 | 保存された選択が無い（初めて開いた）とき、完了済みを表示する |
| UC-06-A2 | 手順1 | 選択に未知の値が送られたとき、選択を変えず、400 と `MALFORMED_REQUEST` を返す |

### 3. 受入条件（AC / Gherkin）

> ルール: 1 AC = 1 振る舞い／`When` は 1 つ／観測可能な結果のみ／曖昧語禁止／検証層タグを付与。
> 層2 は `@integration` / `@e2e:<領域>` で区別する。E2E は、画面・実ブラウザ・画面から DB までの配線でしか確かめられないものだけに置く（02c §5）。
> Blocking の Open Issue が未解決の AC は `@pending:<OI-ID>` とし、期待値を書かない。今は未解決の Blocking が無いので、`@pending` の AC は無い。
> 応答の形は OI-001 と OI-006 の決定に従う。入力の拒否は 400、存在しない対象は 404 である。
> 本文は ProblemDetail（RFC 9457）に、理由コードの配列 `codes` を足した形である。成功は 2xx である。
> 「codes は X である」は、要素の集合が X に等しいことを指す。順序は仕様にしない。
> 現在時刻を固定するのは、層1と Integration だけである。E2E は実時間で動くので、期限は「今日より前」のように相対で書く。
> 画面への見せ方は OI-010 の決定に従う。区分は「未完了」「完了」「期限切れ」のラベルで表示する。
> テスト名は、先頭に `AC-xx-yy` を置く。括弧やコロンで囲まない（03 §3.4）。

#### 検証層の選び方

| 層 | 置くもの | AC |
|---|---|---|
| 層1 | タイトルと期限の検証、区分の判定、状態の遷移、並び順、表示の絞り込み。値オブジェクトと純粋な関数で完結する | UC-01 の検証、UC-02〜06 の規則 |
| 層2 Integration | 保存と読み戻し、DB の並び順、日付の往復、拒否のときに何も保存されないこと、選択の保存。HTTP と DB の配線。保存の確認は、DB を通した読み戻しで行い、メモリだけの実装を通さない | 保存・応答・往復に関わるもの |
| 層2 E2E | 画面の描画、日付を選ぶ部品、画面から DB を通って画面に戻る往復 | 次の表の 10 本 |

| E2E の領域 | AC | E2E でしか確かめられない理由 |
|---|---|---|
| `happy-path` | AC-01-20, AC-02-05, AC-03-07 | 画面で登録し、完了にし、未完了に戻す主要な流れ（REQ-TODO-07）。ラベルの表示は OI-010 の決定に従う |
| `browser-operation` | AC-01-21 | 日付を選ぶ部品（REQ-TODO-14）の操作は、実ブラウザでしか再現できない |
| `display` | AC-04-05, AC-01-23 | 未完了・完了・期限切れの見分け（REQ-TODO-06）は、描画でしか確かめられない。ブラウザのタイムゾーンによらないことも、ブラウザでしか確かめられない |
| `communication` | AC-06-05 | 画面での選択が、サーバーと DB を経て、再読み込み後の画面に戻る往復 |
| `display` | AC-01-22 | 不正な入力の理由の表示（REQ-TODO-13）。文言は OI-007 の決定に従う。`DUE_DATE_INVALID` の文言は、日付を選ぶ部品が不正な日付を作れないので、E2E では確かめない |
| `display` | AC-01-31, AC-01-32 | 登録の成功後に、理由の表示が消え、入力欄が空に戻ること（REQ-TODO-07・13、OI-014）。画面のスクリプトが DOM を更新する結果は、ブラウザでしか確かめられない |

| AC ID | 関連 UC | 検証層 | 優先度 | 紐づくテスト |
|---|---|---|---|---|
| AC-01-01 | UC-01 | 層1 | Must | `TaskConstraintTableTest::AC-01-01`, `TaskTitleTest::AC-01-01` |
| AC-01-02 | UC-01-A1 | 層1 | Must | `TaskConstraintTableTest::AC-01-02`, `TaskTitleTest::AC-01-02` |
| AC-01-03 | UC-01-A1 | 層1 | Must | `TaskConstraintTableTest::AC-01-03` |
| AC-01-04 | UC-01-A1 | 層1 | Must | `TaskConstraintTableTest::AC-01-04`, `TaskTitleTest::AC-01-04` |
| AC-01-05 | UC-01-A1 | 層1 | Must | `TaskConstraintTableTest::AC-01-05`, `TaskTitleTest::AC-01-05` |
| AC-01-06 | UC-01 | 層1 | Must | `TaskConstraintTableTest::AC-01-06` |
| AC-01-07 | UC-01 | 層1 | Must | `TaskConstraintTableTest::AC-01-07`, `TaskTitleTest::AC-01-07` |
| AC-01-08 | UC-01 | 層1 | Must | `TaskConstraintTableTest::AC-01-08` |
| AC-01-09 | UC-01 | 層1 | Must | `TaskTest::AC-01-09` |
| AC-01-10 | UC-01 | 層1 | Must | `TaskTest::AC-01-10` |
| AC-01-11 | UC-01 | 層1 | Must | `TaskTest::AC-01-11` |
| AC-01-12 | UC-01 | 層1 | Must | `TaskTest::AC-01-12` |
| AC-01-13 | UC-01 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-13` |
| AC-01-14 | UC-01-A2 | 層1 | Must | `DueDateTest::AC-01-14`, `TaskConstraintTableTest::AC-01-14` |
| AC-01-15 | UC-01 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-15` |
| AC-01-16 | UC-01-A1 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-16` |
| AC-01-17 | UC-01-A1 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-17` |
| AC-01-18 | UC-01-A3 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-18` |
| AC-01-19 | UC-01-A3 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-19` |
| AC-01-20 | UC-01 | 層2（E2E） | Must | `TaskListE2eTest::AC-01-20` |
| AC-01-21 | UC-01 | 層2（E2E） | Must | `TaskListE2eTest::AC-01-21` |
| AC-01-22 | UC-01-A1 | 層2（E2E） | Must | `TaskListE2eTest::AC-01-22` |
| AC-01-23 | UC-01 | 層2（E2E） | Must | `TaskListE2eTest::AC-01-23` |
| AC-01-24 | UC-01-A3 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-24` |
| AC-01-25 | UC-01-A1 | 層1 | Must | `TaskConstraintTableTest::AC-01-25`, `TaskTitleTest::AC-01-25` |
| AC-01-26 | UC-01 | 層1 | Must | `DueDateTest::AC-01-26`, `TaskConstraintTableTest::AC-01-26` |
| AC-01-27 | UC-01-A2 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-27` |
| AC-01-28 | UC-01-A1 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-28` |
| AC-01-29 | UC-01-A4 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-29` |
| AC-01-30 | UC-01-A4 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-30` |
| AC-01-31 | UC-01 | 層2（E2E） | Must | `TaskListE2eTest::AC-01-31` |
| AC-01-32 | UC-01 | 層2（E2E） | Must | `TaskListE2eTest::AC-01-32` |
| AC-01-33 | UC-01-A5 | 層1 | Must | `TaskConstraintTableTest::AC-01-33`, `DueDateTest::AC-01-33` |
| AC-01-34 | UC-01-A5 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-34` |
| AC-01-35 | UC-01-A5 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-35` |
| AC-01-36 | UC-01-A6 | 層1 | Must | `TaskConstraintTableTest::AC-01-36`, `DueDateTest::AC-01-36` |
| AC-01-37 | UC-01-A6 | 層2（Integration） | Must | `TaskApiHttpTest::AC-01-37` |
| AC-01-38 | UC-01-A1 | 層1 | Must | `TaskConstraintTableTest::AC-01-38`, `TaskTitleTest::AC-01-38` |
| AC-01-39 | UC-01 | 層1 | Must | `TaskConstraintTableTest::AC-01-39`, `TaskTitleTest::AC-01-39` |
| AC-01-40 | UC-01 | 層1 | Must | `TaskConstraintTableTest::AC-01-40`, `TaskTitleTest::AC-01-40` |
| AC-01-41 | UC-01 | 層1 | Must | `TaskConstraintTableTest::AC-01-41`, `TaskTitleTest::AC-01-41` |
| AC-02-01 | UC-02 | 層1 | Must | `TaskConstraintTableTest::AC-02-01`, `TaskTest::AC-02-01` |
| AC-02-02 | UC-02-A2 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-02-02` |
| AC-02-03 | UC-02-A1 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-02-03` |
| AC-02-04 | UC-02 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-02-04` |
| AC-02-05 | UC-02 | 層2（E2E） | Must | `TaskListE2eTest::AC-02-05` |
| AC-02-07 | UC-02-A2 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-02-07` |
| AC-02-08 | UC-02-A2 | 層1 | Must | `TaskConstraintTableTest::AC-02-08`, `TaskTest::AC-02-08` |
| AC-02-09 | UC-02-A3 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-02-09` |
| AC-03-01 | UC-03 | 層1 | Must | `TaskConstraintTableTest::AC-03-01`, `TaskTest::AC-03-01` |
| AC-03-02 | UC-03-A2 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-03-02` |
| AC-03-03 | UC-03-A1 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-03-03` |
| AC-03-04 | UC-03 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-03-04` |
| AC-03-05 | UC-03-A2 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-03-05` |
| AC-03-06 | UC-03-A2 | 層1 | Must | `TaskConstraintTableTest::AC-03-06`, `TaskTest::AC-03-06` |
| AC-03-07 | UC-03 | 層2（E2E） | Must | `TaskListE2eTest::AC-03-07` |
| AC-03-08 | UC-03-A3 | 層2（Integration） | Must | `TaskOperationHttpTest::AC-03-08` |
| AC-04-01 | UC-04 | 層1 | Must | `TaskCategoryTest::AC-04-01` |
| AC-04-02 | UC-04 | 層1 | Must | `TaskCategoryTest::AC-04-02` |
| AC-04-03 | UC-04 | 層2（Integration） | Must | `TaskListHttpTest::AC-04-03` |
| AC-04-04 | UC-04 | 層1 / 層2（Integration） | Must | 層1: `TaskCategoryTest::AC-04-04`、層2: `TaskListHttpTest::AC-04-04` |
| AC-04-05 | UC-04 | 層2（E2E） | Must | `TaskListE2eTest::AC-04-05` |
| AC-04-06 | UC-04-A1 | 層2（Integration） | Must | `TaskListHttpTest::AC-04-06` |
| AC-05-01 | UC-05 | 層1 | Must | `TaskConstraintTableTest::AC-05-01`, `TaskListTest::AC-05-01` |
| AC-05-02 | UC-05 | 層1 | Must | `TaskConstraintTableTest::AC-05-02`, `TaskListTest::AC-05-02` |
| AC-05-03 | UC-05 | 層1 | Must | `TaskConstraintTableTest::AC-05-03`, `TaskListTest::AC-05-03`, `TaskTest::AC-05-03` |
| AC-05-04 | UC-05-A1 | 層1 | Must | `TaskConstraintTableTest::AC-05-04`, `TaskListTest::AC-05-04` |
| AC-05-05 | UC-05 | 層1 | Must | `TaskConstraintTableTest::AC-05-05`, `TaskListTest::AC-05-05` |
| AC-05-06 | UC-05 | 層2（Integration） | Must | `TaskListHttpTest::AC-05-06` |
| AC-05-07 | UC-05 | 層1 | Must | `TaskConstraintTableTest::AC-05-07`, `TaskListTest::AC-05-07` |
| AC-05-08 | UC-05 | 層2（Integration） | Must | `TaskListHttpTest::AC-05-08` |
| AC-06-01 | UC-06 | 層1 | Must | `TaskConstraintTableTest::AC-06-01`, `TaskListTest::AC-06-01` |
| AC-06-02 | UC-06 | 層1 | Must | `TaskConstraintTableTest::AC-06-02`, `TaskListTest::AC-06-02` |
| AC-06-03 | UC-06-A1 | 層1 / 層2（Integration） | Must | 層1: `TaskConstraintTableTest::AC-06-03`、層2: `TaskListHttpTest::AC-06-03` |
| AC-06-04 | UC-06 | 層2（Integration） | Must | `TaskListHttpTest::AC-06-04` |
| AC-06-05 | UC-06 | 層2（E2E） | Must | `TaskListE2eTest::AC-06-05` |
| AC-06-06 | UC-06 | 層2（Integration） | Must | `TaskListHttpTest::AC-06-06` |
| AC-06-07 | UC-06-A2 | 層2（Integration） | Must | `TaskListHttpTest::AC-06-07` |

AC-02-06 は使わない。旧サンプルが使っていた番号で、履歴の記録と取り違えないためである。

テストをこれから書く AC は「未実装」と書く。実装待ちを `@pending` で除外しない。理由は [03 §3.4.2](../../../../docs/testing/03-trs-guideline.md#342-各検査の詳細) の ③ にある。テストが無い間は、トレーサビリティ検査が失敗する。

**記法**: Examples の `[半角空白]`（U+0020）、`[全角空白]`（U+3000）、`[タブ]`（U+0009）、`[改行]`（U+000A）、`[U+XXXX]`（そのコードポイント）は、その文字 1 個を表す。
「あ×50」は、「あ」を 50 個並べた文字列を表す。「[U+200B]×51」のように、文字に個数を付けた書き方も同じである。時刻は Asia/Tokyo（+09:00）か UTC（`Z`）で書く。

登録の古い順の例は、時計を進めて、登録時刻が異なるように登録した例に限る。登録時刻が完全に同じ場合の順は、AC-05-07 と AC-05-08 で決める。タイトルは Unicode 正規化をしない（OI-008）。読み戻したタイトルは、前後の空白と表示されない文字を除いた以外は、入力と同じコードポイント列である（OI-017）。

```gherkin
Feature: タスク管理（登録・完了・一覧と期限）

  # ---- UC-01 タスクを登録する ----

  @AC-01-01 @layer:1
  Scenario Outline: 1〜50 字のタイトルは受理される
    Given タイトルが "<title>" である
    When  タイトルを検証する
    Then  タイトルは受理される
    And   見た目の 1 文字を 1 字と数えた長さは <length> である

    Examples:
      | title                          | length |
      | a                              | 1      |
      | 牛乳を買う                     | 5      |
      | あ×50                          | 50     |
      | "é"（e に結合文字 U+0301）×50  | 50     |
      | "🇯🇵"（国旗 1 個）×50           | 50     |

  @AC-01-02 @layer:1
  Scenario Outline: 51 字以上のタイトルは拒否される
    Given タイトルが "<title>" である
    When  タイトルを検証する
    Then  タイトルは拒否される

    Examples:
      | title                          |
      | あ×51                          |
      | あ×200                         |
      | "é"（e に結合文字 U+0301）×51  |
      | "🇯🇵"（国旗 1 個）×51           |
      | [半角空白]あ×51[半角空白]      |
      | あ×51[U+200B]                  |
      | [U+200B]あ×51                  |

  @AC-01-03 @layer:1
  Scenario: 空のタイトルは拒否される
    Given タイトルが空である
    When  タイトルを検証する
    Then  タイトルは拒否される

  @AC-01-04 @layer:1
  Scenario Outline: 空白だけのタイトルは拒否される
    Given タイトルが "<title>" である
    When  タイトルを検証する
    Then  タイトルは拒否される

    Examples:
      | title                                |
      | [半角空白]                           |
      | [全角空白]                           |
      | [タブ]                               |
      | [改行]                               |
      | [半角空白][全角空白][タブ][改行]     |

  @AC-01-05 @layer:1
  Scenario Outline: 上の AC に挙げた以外の White_Space 属性を持つ文字だけのタイトルは拒否される
    Given タイトルが "<title>" である
    When  タイトルを検証する
    Then  タイトルは拒否される

    Examples:
      | title            |
      | [U+000B]         |
      | [U+000C]         |
      | [U+000D]         |
      | [U+0085]         |
      | [U+00A0]         |
      | [U+1680]         |
      | [U+2000]         |
      | [U+200A]         |
      | [U+2028]         |
      | [U+2029]         |
      | [U+202F]         |
      | [U+205F]         |
      | [U+000D][U+000A] |

  @AC-01-06 @layer:1
  Scenario Outline: 前後の、上の AC に挙げた以外の White_Space 属性を持つ文字は、除いて保存される
    Given タイトルが "<input>" である
    When  タイトルを検証する
    Then  保存されるタイトルは "牛乳を買う" である

    Examples:
      | input                                    |
      | [U+00A0]牛乳を買う[U+00A0]               |
      | [U+2003]牛乳を買う[U+2003]               |
      | [U+000D][U+000A]牛乳を買う[U+000D][U+000A] |
      | [U+0085]牛乳を買う[U+2028]               |

  @AC-01-07 @layer:1
  Scenario Outline: タイトルの前後の空白は除いて保存され、途中の空白は残る
    Given タイトルが "<input>" である
    When  タイトルを検証する
    Then  保存されるタイトルは "<saved>" である

    Examples:
      | input                                  | saved            |
      | [半角空白]牛乳を買う[半角空白]         | 牛乳を買う       |
      | [全角空白]牛乳を買う[全角空白]         | 牛乳を買う       |
      | [タブ][改行]牛乳を買う[改行][タブ]     | 牛乳を買う       |
      | 牛乳[半角空白]と[全角空白]卵           | 牛乳[半角空白]と[全角空白]卵 |

  @AC-01-08 @layer:1
  Scenario: 前後の空白を除く前は 51 字以上で、除いた後は 50 字以内のタイトルは受理される
    Given タイトルが、あ×50 の前後に半角空白がそれぞれ 1 個ある
    When  タイトルを検証する
    Then  タイトルは受理される
    And   保存されるタイトルは あ×50 である

  @AC-01-09 @layer:1
  Scenario: 期限を付けて登録できる
    Given タイトルが "牛乳を買う" で、期限が 2026-11-30 である
    When  タスクを登録する
    Then  登録したタスクの期限は 2026-11-30 である

  @AC-01-10 @layer:1
  Scenario: 期限を付けずに登録できる
    Given タイトルが "牛乳を買う" で、期限が無い
    When  タスクを登録する
    Then  登録したタスクに期限は無い

  @AC-01-11 @layer:1
  Scenario Outline: 登録したタスクは未完了である
    Given タイトルが "牛乳を買う" で、期限が <due> である
    When  タスクを登録する
    Then  登録したタスクは未完了である

    Examples:
      | due        |
      | 無い       |
      | 2026-11-30 |

  @AC-01-12 @layer:1
  Scenario Outline: 過去の日付の期限でも登録できる
    Given 今日は 2026-11-01 である
    And   タイトルが "牛乳を買う" で、期限が <due> である
    When  タスクを登録する
    Then  登録したタスクの期限は <due> である

    Examples:
      | due        |
      | 2026-10-31 |
      | 2025-11-01 |

  @AC-01-13 @layer:2 @integration
  Scenario: 同じタイトルのタスクを複数登録できる
    Given タイトル "牛乳を買う" のタスクが 1 件登録されている
    When  同じタイトル "牛乳を買う" で、もう 1 件登録する
    Then  応答のステータスは 2xx である
    And   一覧を取り直すと、同じタイトルの別々のタスクが 2 件ある

  @AC-01-14 @layer:1
  Scenario Outline: YYYY-MM-DD として読めない期限と、実在しない日の期限は拒否される
    Given 期限が "<due>" である
    When  期限を検証する
    Then  期限は拒否される

    Examples:
      | due                 |
      | 2026-11-30T10:00:00 |
      | 2026/11/30          |
      | 20261130            |
      | 2026-1-5            |
      | 令和8年11月30日     |
      | 2026-02-30          |
      | 2027-02-29          |
      | 2026-13-01          |

  @AC-01-15 @layer:2 @integration
  Scenario Outline: 登録したタスクは保存され、読み戻しても同じである
    Given 今日は 2026-11-01 である
    And   登録済みのタスクが 0 件である
    And   タイトルが "<title>" で、期限が <due> である
    When  タスクを登録する
    Then  応答のステータスは 2xx である
    And   一覧を取り直すと、タイトル "<saved>"・期限 <due> の、完了の状態が未完了のタスクが 1 件ある

    Examples:
      | title                          | saved      | due        |
      | 牛乳を買う                     | 牛乳を買う | 2026-11-30 |
      | 牛乳を買う                     | 牛乳を買う | 2026-10-31 |
      | 牛乳を買う                     | 牛乳を買う | 0001-01-01 |
      | 牛乳を買う                     | 牛乳を買う | 9999-12-31 |
      | 牛乳を買う                     | 牛乳を買う | 無い       |
      | [半角空白]牛乳を買う[半角空白] | 牛乳を買う | 無い       |
      | [U+200B][半角空白]牛乳を買う[U+200B] | 牛乳を買う | 無い |
      | "é"（e に結合文字 U+0301）×50  | 同じ       | 無い       |
      | "🇯🇵"（国旗 1 個）×50           | 同じ       | 無い       |

  @AC-01-16 @layer:2 @integration
  Scenario Outline: 不正なタイトルでは登録されない
    Given 登録済みのタスクが 0 件である
    And   タイトルが "<title>" である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   一覧を取り直すと、タスクは 0 件のままである

    Examples:
      | title      |
      | （空）     |
      | [半角空白] |
      | [U+200B]   |
      | あ×51      |

  @AC-01-17 @layer:2 @integration
  Scenario Outline: 不正なタイトルは、400 と理由コードで拒否される
    Given タイトルが "<title>" である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["<code>"] である

    Examples:
      | title                     | code           |
      | （空）                    | TITLE_BLANK    |
      | [半角空白]                | TITLE_BLANK    |
      | [U+200B]                  | TITLE_BLANK    |
      | あ×51                     | TITLE_TOO_LONG |
      | [半角空白]あ×51[半角空白] | TITLE_TOO_LONG |

  @AC-01-18 @layer:2 @integration
  Scenario: 本文が JSON として読めない要求は、400 と MALFORMED_REQUEST で拒否される
    Given 登録の要求の本文が、JSON として読めない
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["MALFORMED_REQUEST"] である

  @AC-01-19 @layer:2 @integration
  Scenario: タイトルが無い要求は、400 と TITLE_BLANK で拒否される
    Given 登録の要求の本文が、タイトルを含まない
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["TITLE_BLANK"] である

  @AC-01-20 @layer:2 @e2e:happy-path
  Scenario: 画面からタスクを登録できる
    Given 持ち主が一覧の画面を開いている
    When  タイトル "牛乳を買う" を入力して、期限を選ばずに登録する
    Then  一覧に "牛乳を買う" が表示される

  @AC-01-21 @layer:2 @e2e:browser-operation
  Scenario: 日付を選ぶ部品で期限を選んで登録できる
    Given 持ち主が一覧の画面を開いている
    When  タイトル "牛乳を買う" を入力し、日付を選ぶ部品で、Asia/Tokyo の日付で今日より前の日付を選んで登録する
    Then  "牛乳を買う" の期限として、選んだ日付が YYYY-MM-DD の形で表示される
    And   "牛乳を買う" に「期限切れ」のラベルが表示される

  @AC-01-22 @layer:2 @e2e:display
  Scenario Outline: 不正なタイトルの理由が画面に表示される
    Given 持ち主が一覧の画面を開いている
    When  タイトル "<title>" を入力して登録する
    Then  理由として「<message>」が表示される

    Examples:
      | title  | message                                  |
      | （空） | タイトルを入力してください               |
      | あ×51  | タイトルは 50 文字以内で入力してください |

  @AC-01-23 @layer:2 @e2e:display
  Scenario: 画面から登録したタスクに、未完了のラベルが表示される
    Given 持ち主が一覧の画面を開いている
    When  タイトル "牛乳を買う" を入力して、期限を選ばずに登録する
    Then  "牛乳を買う" に「未完了」のラベルが表示される

  @AC-01-24 @layer:2 @integration
  Scenario Outline: 本文が壊れている、またはタイトルが無い要求では登録されない
    Given 登録済みのタスクが 0 件である
    And   登録の要求の本文が <body> である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   一覧を取り直すと、タスクは 0 件のままである

    Examples:
      | body               |
      | 壊れている         |
      | タイトルを含まない |

  @AC-01-25 @layer:1
  Scenario Outline: 表示される文字があれば、途中の表示されない文字は、除かず、そのまま保存される
    Given タイトルが "<input>" である
    When  タイトルを検証する
    Then  タイトルは受理される
    And   保存されるタイトルは "<saved>" である

    Examples:
      | input                                                      | saved                  |
      | 牛乳[U+200B]を買う                                         | 同じ                   |
      | 👨[U+200D]👩[U+200D]👧                                     | 同じ                   |
      | a[U+FE0F]b                                                 | 同じ                   |
      | a[U+E0001]b                                                | 同じ                   |
      | [半角空白][U+200B]a[U+200B]b[U+200B][半角空白]             | a[U+200B]b             |

  @AC-01-38 @layer:1
  Scenario Outline: 空白と、表示されない文字だけのタイトルは、空として拒否される
    Given タイトルが "<title>" である
    When  タイトルを検証する
    Then  タイトルは拒否され、理由は「空」である

    Examples:
      | title                            |
      | [U+200B]                         |
      | [U+FEFF]                         |
      | [U+2060]                         |
      | [U+200D]                         |
      | [U+FE0F]                         |
      | [U+E0001]                        |
      | [U+E0100]                        |
      | [U+180B]                         |
      | [U+200B][U+200D][U+FEFF][U+2060] |
      | [半角空白][U+200B][半角空白]     |
      | [U+200B][半角空白][U+200B]       |
      | [U+200B][U+FE0F]                 |
      | [U+FE0F][半角空白]               |
      | [U+200B]×51                      |

  @AC-01-39 @layer:1
  Scenario Outline: 前後の空白と、表示されない文字は、除いて保存される
    Given タイトルが "<input>" である
    When  タイトルを検証する
    Then  タイトルは受理される
    And   保存されるタイトルは "牛乳を買う" である

    Examples:
      | input                                                        |
      | [U+200B]牛乳を買う                                           |
      | 牛乳を買う[U+FEFF]                                           |
      | [U+2060]牛乳を買う[U+2060]                                   |
      | [U+200B][半角空白]牛乳を買う                                 |
      | [半角空白][U+200B][半角空白]牛乳を買う[U+200B][半角空白]     |
      | [U+200D]牛乳を買う[U+200D]                                   |
      | [U+E0001]牛乳を買う                                          |
      | 牛乳を買う[U+E0001]                                          |

  @AC-01-40 @layer:1
  Scenario Outline: 表示される文字の直後の異体字セレクタとタグ文字は残り、それ以外の位置のものは除かれる
    Given タイトルが "<input>" である
    When  タイトルを検証する
    Then  タイトルは受理される
    And   保存されるタイトルは "<saved>" である

    Examples:
      | input                                         | saved                                |
      | [U+2764][U+FE0F]                              | 同じ                                 |
      | [U+2764][U+FE0F][U+200B]                      | [U+2764][U+FE0F]                     |
      | [U+845B][U+E0100]                             | 同じ                                 |
      | 🏴[U+E0067][U+E0062][U+E0065][U+E006E][U+E0067][U+E007F]                                          | 同じ                                 |
      | 🏴[U+E0067][U+E0062][U+E0065][U+E006E][U+E0067][U+E007F][半角空白][U+200B]                        | 🏴[U+E0067][U+E0062][U+E0065][U+E006E][U+E0067][U+E007F]                                 |
      | 牛乳を買う[U+180B]                            | 同じ                                 |
      | 牛乳を買う[U+FE0F][U+FE0E]                    | 同じ                                 |
      | あ×50[U+FE0F]                                 | 同じ                                 |
      | 牛乳を買う[U+FE0F][U+200B][U+FE0F]            | 牛乳を買う[U+FE0F]                   |
      | [U+FE0F]牛乳を買う                            | 牛乳を買う                           |
      | [U+FE0F][半角空白]牛乳を買う                  | 牛乳を買う                           |
      | [U+180B]牛乳を買う                            | 牛乳を買う                           |
      | 牛乳を買う[U+200B][U+FE0F][U+200B]            | 牛乳を買う                           |

  # JDK 21 の BreakIterator で、除く前の字数を実測した。[U+FE0F]あ×50 と あ×50[半角空白][U+FE0F] は、どちらも 51 字である。
  # 除いた後は、どちらも 50 字である。あ×50[U+FE0F] は、除く前も 50 字である。
  @AC-01-41 @layer:1
  Scenario Outline: 前後の空白と表示されない文字を除く前は 51 字以上で、除いた後は 50 字以内のタイトルは受理される
    Given タイトルが "<input>" である
    When  タイトルを検証する
    Then  タイトルは受理される
    And   保存されるタイトルは あ×50 である

    Examples:
      | input                                             |
      | あ×50[U+200B]                                     |
      | [U+200B]あ×50                                     |
      | [半角空白][U+200B]あ×50[U+200B][半角空白]         |
      | [U+FE0F]あ×50                                     |
      | あ×50[半角空白][U+FE0F]                           |

  @AC-01-26 @layer:1
  Scenario Outline: YYYY-MM-DD の形で実在する暦日は、期限として受理される
    Given 期限が "<due>" である
    When  期限を検証する
    Then  期限は受理される

    Examples:
      | due        |
      | 2026-11-30 |
      | 2024-02-29 |
      | 0001-01-01 |
      | 9999-12-31 |

  @AC-01-27 @layer:2 @integration
  Scenario Outline: 期限が日付として不正な要求は、400 と DUE_DATE_INVALID で拒否され、登録されない
    Given 登録済みのタスクが 0 件である
    And   タイトルが "牛乳を買う" で、期限が "<due>" である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["DUE_DATE_INVALID"] である
    And   一覧を取り直すと、タスクは 0 件のままである

    Examples:
      | due                 |
      | 2026-11-30T10:00:00 |
      | 2026/11/30          |
      | 2026-02-30          |

  @AC-01-28 @layer:2 @integration
  Scenario: タイトルと期限が同時に不正な要求は、codes に両方の理由コードを含めて拒否される
    Given 登録済みのタスクが 0 件である
    And   タイトルが空で、期限が "2026-02-30" である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["TITLE_BLANK", "DUE_DATE_INVALID"] である
    And   一覧を取り直すと、タスクは 0 件のままである

  @AC-01-29 @layer:2 @integration
  Scenario Outline: 型や形が定義と違う本文は、400 と MALFORMED_REQUEST で拒否され、登録されない
    Given 登録済みのタスクが 0 件である
    And   登録の要求の本文が <body> である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["MALFORMED_REQUEST"] である
    And   一覧を取り直すと、タスクは 0 件のままである

    Examples:
      | body                                             |
      | タイトルが数値                                   |
      | タイトルが真偽値                                 |
      | タイトルが配列                                   |
      | タイトルがオブジェクト                           |
      | タイトルは文字列で、期限が数値                   |
      | タイトルは文字列で、期限が真偽値                 |
      | タイトルは文字列で、期限がオブジェクト           |

  @AC-01-30 @layer:2 @integration
  Scenario: 定義にない項目は無視されて登録される
    Given 登録済みのタスクが 0 件である
    And   登録の要求の本文が、タイトルと期限のほかに、定義にない項目を含む
    When  タスクの登録を要求する
    Then  応答のステータスは 2xx である
    And   一覧を取り直すと、そのタイトルと期限のタスクが 1 件ある

  @AC-01-31 @layer:2 @e2e:display
  Scenario: 登録に成功すると、前の拒否の理由の表示が消える
    Given 持ち主が一覧の画面を開いている
    And   タイトルを空にして登録し、理由が表示されている
    When  タイトル "牛乳を買う" を入力して、期限を選ばずに登録する
    Then  理由の表示は 0 件になる

  @AC-01-32 @layer:2 @e2e:display
  Scenario: 登録に成功すると、入力欄が空に戻る
    Given 持ち主が一覧の画面を開いている
    When  タイトル "牛乳を買う" を入力し、期限を選んで登録する
    Then  タイトルの入力欄と期限の入力欄は、どちらも空である

  @AC-01-33 @layer:1
  Scenario: 空文字列の期限は拒否される
    Given 期限が "" である
    When  期限を検証する
    Then  期限は拒否される

  @AC-01-34 @layer:2 @integration
  Scenario: 期限が空文字列の要求は、400 と DUE_DATE_INVALID で拒否され、登録されない
    Given 登録済みのタスクが 0 件である
    And   タイトルが "牛乳を買う" で、期限が "" である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["DUE_DATE_INVALID"] である
    And   一覧を取り直すと、タスクは 0 件のままである

  @AC-01-35 @layer:2 @integration
  Scenario Outline: 期限の項目を省く、または null の要求は、期限なしで登録される
    Given 登録済みのタスクが 0 件である
    And   登録の要求の本文が、タイトル "牛乳を買う" と、<due> を含む
    When  タスクの登録を要求する
    Then  応答のステータスは 2xx である
    And   一覧を取り直すと、タイトル "牛乳を買う"・期限なしのタスクが 1 件ある

    Examples:
      | due                   |
      | 期限の項目が無い      |
      | 期限の項目が null     |

  @AC-01-36 @layer:1
  Scenario Outline: 0000 年の暦日は、期限の下限より前なので拒否される
    Given 期限が "<due>" である
    When  期限を検証する
    Then  期限は拒否される

    Examples:
      | due        |
      | 0000-01-01 |
      | 0000-02-29 |
      | 0000-12-31 |

  @AC-01-37 @layer:2 @integration
  Scenario Outline: 0000 年の期限の要求は、400 と DUE_DATE_INVALID で拒否され、登録されない
    Given 登録済みのタスクが 0 件である
    And   タイトルが "牛乳を買う" で、期限が "<due>" である
    When  タスクの登録を要求する
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["DUE_DATE_INVALID"] である
    And   一覧を取り直すと、タスクは 0 件のままである

    Examples:
      | due        |
      | 0000-01-01 |
      | 0000-02-29 |
      | 0000-12-31 |

  # ---- UC-02 タスクを完了にする ----

  @AC-02-01 @layer:1
  Scenario: 未完了のタスクを完了にできる
    Given 未完了のタスク "牛乳を買う"（期限 2026-11-30）と、別の未完了のタスク "卵を買う" がある
    When  "牛乳を買う" を完了にする
    Then  "牛乳を買う" は完了になる
    And   "牛乳を買う" のタイトルと期限は変わらない
    And   "卵を買う" は未完了のままである

  @AC-02-02 @layer:2 @integration
  Scenario: 完了済みのタスクをもう一度完了にしても、何も変わらない
    Given 完了済みを表示する選択である
    And   完了のタスク "牛乳を買う"（期限 2026-11-30）がある
    When  "牛乳を買う" をもう一度完了にする
    Then  一覧を取り直すと、"牛乳を買う" は完了のままで、タイトルと期限は変わらない
    And   一覧は 1 件のままである

  @AC-02-03 @layer:2 @integration
  Scenario: 存在しないタスクを完了にする要求は、404 と TASK_NOT_FOUND で拒否される
    Given 存在しないタスクの識別子がある
    When  そのタスクを完了にする
    Then  応答のステータスは 404 である
    And   本文は ProblemDetail で、status は 404 である
    And   本文の codes は ["TASK_NOT_FOUND"] である

  @AC-02-04 @layer:2 @integration
  Scenario: 完了にした状態は保存され、読み戻しても完了である
    Given 完了済みを表示する選択である
    And   未完了のタスク "牛乳を買う" がある
    When  "牛乳を買う" を完了にする
    Then  応答のステータスは 2xx である
    And   一覧を取り直すと、"牛乳を買う" は完了である

  @AC-02-05 @layer:2 @e2e:happy-path
  Scenario: 画面からタスクを完了にできる
    Given 一覧に、未完了のタスク "牛乳を買う" がある
    When  持ち主が画面で "牛乳を買う" を完了にする
    Then  "牛乳を買う" に「完了」のラベルが表示される

  @AC-02-07 @layer:2 @integration
  Scenario: 完了済みのタスクをもう一度完了にする要求は、成功する
    Given 完了のタスク "牛乳を買う" がある
    When  "牛乳を買う" をもう一度完了にする
    Then  応答のステータスは 2xx である

  @AC-02-08 @layer:1
  Scenario: 完了済みのタスクをもう一度完了にしても、そのタスクは変わらない
    Given 完了のタスク "牛乳を買う"（期限 2026-11-30）がある
    When  "牛乳を買う" をもう一度完了にする
    Then  "牛乳を買う" は完了のままで、タイトルと期限は変わらない

  @AC-02-09 @layer:2 @integration
  Scenario Outline: 形が不正な識別子でタスクを完了にする要求は、404 と TASK_NOT_FOUND で拒否され、タスクは変わらない
    Given 未完了のタスク "牛乳を買う"（期限 2026-11-30）がある
    And   タスクの識別子が "<id>" である
    When  そのタスクを完了にする
    Then  応答のステータスは 404 である
    And   本文は ProblemDetail で、status は 404 である
    And   本文の codes は ["TASK_NOT_FOUND"] である
    And   一覧を取り直すと、"牛乳を買う" は未完了のままで、タイトルと期限は変わらない

    Examples:
      | id                                  |
      | abc                                 |
      | 1.5                                 |
      | +（存在するタスクの識別子）         |
      | 0x（存在するタスクの識別子）        |
      | 先頭にゼロを付けた、存在するタスクの識別子 |
      | 99999999999999999999                |

  # ---- UC-03 タスクを未完了に戻す ----

  @AC-03-01 @layer:1
  Scenario: 完了のタスクを未完了に戻せる
    Given 完了のタスク "牛乳を買う"（期限 2026-11-30）と、別の完了のタスク "卵を買う" がある
    When  "牛乳を買う" を未完了に戻す
    Then  "牛乳を買う" は未完了になる
    And   "牛乳を買う" のタイトルと期限は変わらない
    And   "卵を買う" は完了のままである

  @AC-03-02 @layer:2 @integration
  Scenario: 未完了のタスクを未完了に戻しても、何も変わらない
    Given 未完了のタスク "牛乳を買う"（期限 2026-11-30）がある
    When  "牛乳を買う" を未完了に戻す
    Then  一覧を取り直すと、"牛乳を買う" は未完了のままで、タイトルと期限は変わらない
    And   一覧は 1 件のままである

  @AC-03-03 @layer:2 @integration
  Scenario: 存在しないタスクを未完了に戻す要求は、404 と TASK_NOT_FOUND で拒否される
    Given 存在しないタスクの識別子がある
    When  そのタスクを未完了に戻す
    Then  応答のステータスは 404 である
    And   本文は ProblemDetail で、status は 404 である
    And   本文の codes は ["TASK_NOT_FOUND"] である

  @AC-03-04 @layer:2 @integration
  Scenario: 未完了に戻した状態は保存され、読み戻しても未完了である
    Given 完了のタスク "牛乳を買う" がある
    When  "牛乳を買う" を未完了に戻す
    Then  応答のステータスは 2xx である
    And   一覧を取り直すと、"牛乳を買う" は未完了である

  @AC-03-05 @layer:2 @integration
  Scenario: 未完了のタスクを未完了に戻す要求は、成功する
    Given 未完了のタスク "牛乳を買う" がある
    When  "牛乳を買う" を未完了に戻す
    Then  応答のステータスは 2xx である

  @AC-03-06 @layer:1
  Scenario: 未完了のタスクを未完了に戻しても、そのタスクは変わらない
    Given 未完了のタスク "牛乳を買う"（期限 2026-11-30）がある
    When  "牛乳を買う" を未完了に戻す
    Then  "牛乳を買う" は未完了のままで、タイトルと期限は変わらない

  @AC-03-07 @layer:2 @e2e:happy-path
  Scenario: 画面からタスクを未完了に戻せる
    Given 一覧に、完了のタスク "牛乳を買う" がある
    When  持ち主が画面で "牛乳を買う" を未完了に戻す
    Then  "牛乳を買う" に「未完了」のラベルが表示される

  @AC-03-08 @layer:2 @integration
  Scenario Outline: 形が不正な識別子でタスクを未完了に戻す要求は、404 と TASK_NOT_FOUND で拒否され、タスクは変わらない
    Given 完了のタスク "牛乳を買う"（期限 2026-11-30）がある
    And   タスクの識別子が "<id>" である
    When  そのタスクを未完了に戻す
    Then  応答のステータスは 404 である
    And   本文は ProblemDetail で、status は 404 である
    And   本文の codes は ["TASK_NOT_FOUND"] である
    And   一覧を取り直すと、"牛乳を買う" は完了のままで、タイトルと期限は変わらない

    Examples:
      | id                                  |
      | abc                                 |
      | 1.5                                 |
      | +（存在するタスクの識別子）         |
      | 0x（存在するタスクの識別子）        |
      | 先頭にゼロを付けた、存在するタスクの識別子 |
      | 99999999999999999999                |

  # ---- UC-04 一覧でタスクの状態を見分ける ----

  @AC-04-01 @layer:1
  Scenario Outline: タスクの区分は、完了・期限・現在時刻で決まる
    Given タスクが <state> で、期限が <due> である
    And   現在時刻が <now> である
    When  タスクの区分を判定する
    Then  区分は<label>である

    Examples:
      | state  | due        | now                       | label    |
      | 未完了 | 無い       | 2026-11-01T10:00+09:00    | 未完了   |
      | 未完了 | 2026-11-02 | 2026-11-01T10:00+09:00    | 未完了   |
      | 未完了 | 2026-11-01 | 2026-11-01T00:00+09:00    | 未完了   |
      | 未完了 | 2026-11-01 | 2026-11-01T23:59:59.999+09:00 | 未完了 |
      | 未完了 | 2026-11-01 | 2026-11-02T00:00+09:00    | 期限切れ |
      | 未完了 | 2026-11-01 | 2026-11-05T12:00+09:00    | 期限切れ |
      | 完了   | 無い       | 2026-11-01T10:00+09:00    | 完了     |
      | 完了   | 2026-11-01 | 2026-11-01T10:00+09:00    | 完了     |
      | 完了   | 2026-11-01 | 2026-11-02T00:00+09:00    | 完了     |
      | 完了   | 2026-11-01 | 2026-11-05T12:00+09:00    | 完了     |

  @AC-04-02 @layer:1
  Scenario: どのタスクも、区分は未完了・完了・期限切れのちょうど 1 つである
    Given 任意のタスクと、任意の現在時刻がある
    When  タスクの区分を判定する
    Then  区分は、未完了・完了・期限切れのうち、ちょうど 1 つである

  @AC-04-03 @layer:2 @integration
  Scenario: 登録したタスクが、区分つきで一覧に現れる
    Given 現在時刻が 2026-11-01T10:00+09:00 に固定されている
    And   完了済みを表示する選択である
    And   次のタスクが登録されている
      | タイトル | 期限       | 状態   |
      | A        | 無い       | 未完了 |
      | B        | 無い       | 完了   |
      | C        | 2026-10-31 | 未完了 |
      | D        | 2026-10-31 | 完了   |
    When  一覧を取得する
    Then  一覧は 4 件で、A の区分は未完了、B の区分は完了、C の区分は期限切れ、D の区分は完了である

  @AC-04-04 @layer:1 @layer:2 @integration
  Scenario Outline: 期限切れの判定は、サーバーの既定のタイムゾーンによらず Asia/Tokyo の日付で行う
    Given サーバーの既定のタイムゾーンが <tz> である
    And   未完了のタスクの期限が 2026-11-01 である
    And   現在時刻が <now> である
    When  タスクの区分を判定する
    Then  区分は<label>である

    Examples:
      | tz                  | now                  | label    |
      | UTC                 | 2026-11-01T14:59:59.999Z | 未完了   |
      | UTC                 | 2026-11-01T15:00:00Z     | 期限切れ |
      | America/Los_Angeles | 2026-11-01T14:59:59.999Z | 未完了   |
      | America/Los_Angeles | 2026-11-01T15:00:00Z     | 期限切れ |
      | Asia/Tokyo          | 2026-11-01T14:59:59.999Z | 未完了   |
      | Asia/Tokyo          | 2026-11-01T15:00:00Z     | 期限切れ |

  @AC-04-05 @layer:2 @e2e:display
  Scenario Outline: 一覧で、未完了・完了・期限切れを見分けられる
    Given ブラウザのタイムゾーンが <tz> である
    And   一覧に、期限の無い未完了の "A"、期限が Asia/Tokyo の日付で今日より前の完了の "B"、期限が Asia/Tokyo の日付で今日より前の未完了の "C" がある
    When  持ち主が一覧の画面を開く
    Then  "A" に「未完了」、"B" に「完了」、"C" に「期限切れ」のラベルが表示される

    Examples:
      | tz                  |
      | Asia/Tokyo          |
      | America/Los_Angeles |

  @AC-04-06 @layer:2 @integration
  Scenario: 登録したタスクが 1 件も無いとき、一覧は空である
    Given 登録済みのタスクが 0 件である
    When  一覧を取得する
    Then  一覧は 0 件である

  # ---- UC-05 一覧の並び順 ----

  @AC-05-01 @layer:1
  Scenario: 期限が今日以後のタスクは、期限が近い順に並ぶ
    Given 今日は 2026-11-01 である
    And   次のタスクが、この順に登録されている
      | タイトル | 期限       |
      | A        | 2026-11-10 |
      | B        | 2026-11-03 |
      | C        | 2026-11-05 |
    When  未完了のタスクを並べる
    Then  順序は B, C, A である

  @AC-05-02 @layer:1
  Scenario: 期限のないタスクは最後に並ぶ
    Given 今日は 2026-11-01 である
    And   次のタスクが、この順に登録されている
      | タイトル | 期限       |
      | X        | 無い       |
      | Y        | 2026-12-01 |
    When  未完了のタスクを並べる
    Then  順序は Y, X である

  @AC-05-03 @layer:1
  Scenario Outline: 期限が同じタスク同士と、期限のないタスク同士は、登録の古い順に並ぶ
    Given 今日は 2026-11-01 である
    And   期限が <due> のタスク P, Q が、この順に、Q の登録時刻が P より後になるよう登録されている
    When  未完了のタスクを並べる
    Then  順序は P, Q である

    Examples:
      | due        |
      | 2026-11-05 |
      | 無い       |

  @AC-05-04 @layer:1
  Scenario Outline: 期限を過ぎたタスクも、期限の日付の昇順に並び、現在時刻によらない
    Given 今日は <today> である
    And   完了済みを表示する選択である
    And   次のタスクが、この順に、登録時刻を進めながら登録されている
      | タイトル | 期限       | 状態   |
      | C        | 2026-11-10 | 未完了 |
      | A        | 2026-10-30 | 未完了 |
      | E        | 2026-11-02 | 完了   |
      | B        | 2026-11-03 | 未完了 |
      | D        | 2026-10-29 | 完了   |
    When  一覧を並べる
    Then  順序は A, B, C, D, E である

    Examples:
      | today      |
      | 2026-11-01 |
      | 2026-11-20 |

  @AC-05-05 @layer:1
  Scenario: 完了済みを表示するとき、未完了の後ろに完了済みがまとまり、それぞれ同じ規則で並ぶ
    Given 今日は 2026-11-01 である
    And   完了済みを表示する選択である
    And   次のタスクが、この順に、登録時刻を進めながら登録されている
      | タイトル | 期限       | 状態   |
      | A        | 2026-11-10 | 未完了 |
      | B        | 2026-11-02 | 完了   |
      | C        | 無い       | 未完了 |
      | D        | 無い       | 完了   |
      | E        | 2026-11-01 | 完了   |
    When  一覧を並べる
    Then  順序は A, C, E, B, D である

  @AC-05-06 @layer:2 @integration
  Scenario: 保存したタスクの一覧も、期限が近い順・登録の古い順に並び、完了済みは未完了の後ろにまとまる
    Given 今日は 2026-11-01 である
    And   完了済みを表示する選択である
    And   次のタスクが、この順に、登録時刻を進めながら保存されている
      | タイトル | 期限       | 状態   |
      | P        | 2026-11-10 | 未完了 |
      | Q        | 無い       | 未完了 |
      | R        | 2026-11-03 | 未完了 |
      | S        | 2026-11-10 | 未完了 |
      | T        | 無い       | 未完了 |
      | U        | 2026-11-05 | 完了   |
      | V        | 無い       | 完了   |
    When  一覧を取得する
    Then  順序は R, P, S, Q, T, U, V である

  @AC-05-07 @layer:1
  Scenario Outline: 登録時刻が完全に同じタスクは、登録ごとに増える番号の小さい方が先に並ぶ
    Given 今日は 2026-11-01 である
    And   期限が無いタスク P, Q が、同じ登録時刻で、P の番号が <p_no>、Q の番号が <q_no> で登録されている
    When  未完了のタスクを並べる
    Then  順序は <order> である

    Examples:
      | p_no | q_no | order |
      | 1    | 2    | P, Q  |
      | 2    | 1    | Q, P  |

  @AC-05-08 @layer:2 @integration
  Scenario: 時計が固定された状態で続けて登録したタスクは、登録した順に並ぶ
    Given 現在時刻が 2026-11-01T10:00+09:00 に固定されている
    And   登録済みのタスクが 0 件である
    And   期限が無いタスク P, Q, R を、この順に登録した
    When  一覧を取得する
    Then  順序は P, Q, R である

  # ---- UC-06 完了済みの表示を切り替える ----

  @AC-06-01 @layer:1
  Scenario: 完了済みを隠すと、未完了のタスクだけが表示される
    Given 完了のタスクと、未完了のタスクと、期限切れの未完了のタスクがある
    And   完了済みを隠す選択である
    When  一覧を作る
    Then  未完了のタスクと、期限切れの未完了のタスクだけが表示される

  @AC-06-02 @layer:1
  Scenario: 完了済みを表示すると、すべてのタスクが表示される
    Given 完了のタスクと、未完了のタスクと、期限切れの未完了のタスクがある
    And   完了済みを表示する選択である
    When  一覧を作る
    Then  3 件すべてが表示される

  @AC-06-03 @layer:1 @layer:2 @integration
  Scenario: 保存された選択が無い（初めて開いた）とき、選択は「表示する」である
    Given 完了済みの表示・非表示の選択が、保存されていない
    When  選択を読み戻す
    Then  選択は「表示する」である

  @AC-06-04 @layer:2 @integration
  Scenario Outline: 選択は保存され、別の要求で読み戻しても保たれる
    Given 完了済みの選択が <before> である
    And   選択を「<after>」に切り替えてある
    When  別の要求で選択を読み戻す
    Then  選択は「<after>」である

    Examples:
      | before           | after    |
      | 保存されていない | 隠す     |
      | 「隠す」         | 表示する |

  @AC-06-05 @layer:2 @e2e:communication
  Scenario: 画面で完了済みを隠すと、画面を開き直しても隠れたままである
    Given 一覧に、完了のタスク "B" と、未完了のタスク "A" がある
    And   持ち主が画面で完了済みを隠してある
    When  画面を再読み込みする
    Then  "A" は表示され、"B" は表示されない

  @AC-06-06 @layer:2 @integration
  Scenario: 完了済みを隠す選択のとき、取得した一覧に完了済みは含まれない
    Given 完了済みを隠す選択に切り替えてある
    And   完了のタスクと、未完了のタスクがある
    When  一覧を取得する
    Then  一覧は、未完了のタスクだけである

  @AC-06-07 @layer:2 @integration
  Scenario: 完了済みの表示の選択に未知の値を送ると、400 と MALFORMED_REQUEST で拒否され、選択は変わらない
    Given 完了済みを隠す選択に切り替えてある
    When  選択に未知の値を送る
    Then  応答のステータスは 400 である
    And   本文は ProblemDetail で、status は 400 である
    And   本文の codes は ["MALFORMED_REQUEST"] である
    And   別の要求で選択を読み戻すと、選択は「隠す」のままである
```

---

## 第2部：テスト設計レイヤー

> どう検証するか。AI が展開し、人間がレビューする。仕様の新規決定をここで行わない（未決は Open Issues へ）。

### 4. 因子・水準

> 期限切れの区分判定（F3 × F2 × F4）が、組合せ制約の題材である。`PBT` は、連続・大規模な値域で、ジェネレータの制約になる。

| 因子ID | 因子 | 水準ID | 水準 | 区分 | 備考 |
|---|---|---|---|---|---|
| F1 | タイトル | F1-L1 | 1 字（1 書記素） | 境界（下限） | |
| | | F1-L2 | 2〜49 字 | 有効 | PBT |
| | | F1-L3 | 50 字 | 境界（上限） | |
| | | F1-L4 | 51 字以上 | 無効（拒否） | PBT |
| | | F1-L5 | 空 | 無効（拒否） | |
| | | F1-L6 | 空白だけ（半角・全角・タブ・改行） | 無効（拒否） | |
| | | F1-L7 | 空白だけ（上記以外の White_Space 属性を持つ文字） | 無効（拒否） | OI-002 で確定。OI-016、OI-017 が引き継いだ |
| | | F1-L8 | 前後に空白がある（半角・全角・タブ・改行） | 有効（除去） | |
| | | F1-L9 | 前後に空白がある（上記以外の White_Space 属性を持つ文字） | 有効（除去） | OI-002 で確定。OI-016、OI-017 が引き継いだ |
| | | F1-L10 | 途中に空白がある | 有効（保持） | |
| | | F1-L11 | 書記素の数がコードポイントの数より少ない（結合文字・国旗） | 境界 | OI-008 で確定 |
| | | F1-L12 | 前後の空白を除く前は 51 字以上、除いた後は 50 字以内 | 有効（除いた後で数える） | OI-004 で確定 |
| | | F1-L13 | 途中に表示されない文字（Default_Ignorable_Code_Point。U+200B、U+FEFF、U+200D など）があり、前後は表示される文字 | 有効（除かず保持） | OI-016 で確定。OI-002 の決定を改めた。OI-017 が引き継いだ |
| | | F1-L14 | 空白と表示されない文字だけ（表示されない文字を 1 個以上含む。組になる文字だけも含む） | 無効（拒否） | OI-016 で確定。OI-017 が引き継いだ。組になる文字は OI-017 の範囲 |
| | | F1-L15 | 先頭に空白か表示されない文字がある。または、最後の表示される文字の後ろに、空白か表示されない文字がある。組になる文字（異体字セレクタとタグ文字。OI-017 の範囲）は、先頭や、空白と除く文字の後ろにあっても、これに当たる | 有効（除去） | OI-017 で確定。OI-016 の決定（除かず保持）を改めた |
| | | F1-L16 | 最後の表示される文字の直後に、組になる文字（異体字セレクタとタグ文字。OI-017 の範囲）が続く | 有効（その連なりは除かず保持） | OI-017 で確定 |
| | | F1-L17 | 前後の空白と表示されない文字を除く前は 51 字以上、除いた後は 50 字以内 | 有効（除いた後で数える） | OI-017 で確定。F1-L12 に表示されない文字を足したもの |
| F2 | 期限 | F2-L1 | 無い | 有効 | |
| | | F2-L2 | 今日より前 | 有効 | PBT |
| | | F2-L3 | 今日 | 境界 | |
| | | F2-L4 | 今日より後 | 有効 | PBT |
| | | F2-L5 | `YYYY-MM-DD` として読めない（別形式） | 無効（拒否） | OI-005 で確定 |
| | | F2-L6 | 時刻を含む、または実在しない日 | 無効（拒否） | OI-005 で確定 |
| | | F2-L7 | 実在する暦日（閏日、0001-01-01、9999-12-31 を含む） | 有効 | OI-005 と OI-011 で確定。0000 年は F2-L9 に分ける |
| | | F2-L8 | 空文字列 | 無効（拒否） | OI-012 で確定。項目を省く・null は F2-L1 |
| | | F2-L9 | 0000 年の暦日（先発グレゴリオ暦で実在する日。閏日の 0000-02-29 を含む） | 無効（拒否） | OI-015 で確定。下限 0001-01-01 より前 |
| F3 | 完了の状態 | F3-L1 | 未完了 | 有効 | |
| | | F3-L2 | 完了 | 有効 | |
| F4 | 現在時刻と期限の日付の関係 | F4-L1 | 期限の日の 0 時 0 分（JST） | 境界 | |
| | | F4-L2 | 期限の日の 23 時 59 分 59.999 秒（JST） | 境界 | |
| | | F4-L3 | 期限の翌日の 0 時 0 分（JST） | 境界 | |
| | | F4-L4 | 期限の翌日より後 | 有効 | PBT |
| | | F4-L5 | 期限の日より前 | 有効 | PBT |
| F5 | 操作 | F5-L1 | 完了にする | 有効 | |
| | | F5-L2 | 未完了に戻す | 有効 | |
| F6 | 完了済みの表示の選択 | F6-L1 | 表示する | 有効 | |
| | | F6-L2 | 隠す | 有効 | |
| | | F6-L3 | 保存されていない | 有効（初期値は表示） | |
| F7 | サーバーの既定のタイムゾーン | F7-L1 | UTC | 有効 | 判定は変わらない |
| | | F7-L2 | Asia/Tokyo | 有効 | |
| | | F7-L3 | その他（例: America/Los_Angeles） | 有効 | |
| F8 | 操作の対象 | F8-L1 | 存在するタスク | 有効 | |
| | | F8-L2 | 存在しないタスク | 無効（拒否） | OI-006 で確定 |
| | | F8-L3 | 識別子の形が不正（ASCII の 10 進数字以外を含む〔空白、全角数字を含む〕、符号つき、小数、先頭がゼロ、識別子として表せる範囲を超える値） | 無効（拒否） | OI-012 で確定 |

**組合せ生成方針**

- 登録の検証（F1 × F2）は、因子が独立なので、組合せを展開しない。F1 は F1 の水準ごと、F2 は F2 の水準ごとに検証する。
- 区分の判定（F3 × F2 × F4）は、水準が少ないので、全組合せを制約表（5.2）に書く。連続値は PBT で補う。
- 状態の遷移（F3 × F5）は 2 × 2 の全組合せである。
- 表示の選択（F6）は、完了と未完了のタスクとの全組合せを検証する。
- F7 は、F4 の境界（F4-L2、F4-L3）とだけ組み合わせる。

### 5. 因子制約ルール

> 成立しない組合せ（テスト対象外）と、組合せごとの期待結果を明示する。AI はここに無い期待結果を推測しない。
> 5.2 の各行は、データ駆動テストの 1 ケースに展開する。展開の標準形は [03 §2.3](../../../../docs/testing/03-trs-guideline.md#23-因子水準制約表からデータ駆動テストへの展開)。

#### 5.1 禁止（成立しない）組合せ

| 制約ID | 組合せ | 理由 | 扱い |
|---|---|---|---|
| C-01 | F3-L2（完了）× 区分「期限切れ」 | 3 区分は重ならない（REQ-TODO-32）。完了は、期限を過ぎていても「完了」である（REQ-TODO-23） | 型で排除（区分を閉じた型で表す） |
| C-02 | ドメインの期限の型 × 時刻を持つ値 | 期限は日付だけを持つ（REQ-TODO-19） | 型で排除（ドメインの期限の型は日付だけを持つ）。入力に時刻が含まれるときは、AC-01-14 で拒否する |
| C-03 | F1-L4、F1-L5、F1-L6、F1-L7、F1-L14 × 保存されたタイトル | 検証を通らない値は、タイトルの型を作れない | 型で排除。検証そのものは AC-01-02〜05 と AC-01-38 で確かめる |

#### 5.2 組合せ別の期待結果

| 制約ID | 条件 | 期待結果 | 根拠（AC / OI） |
|---|---|---|---|
| C-10 | F3-L1 × F2-L1（期限が無い）× 任意の時刻 | 未完了 | AC-04-01 |
| C-11 | F3-L1 × F2 あり × F4-L1 または F4-L2 または F4-L5 | 未完了 | AC-04-01 |
| C-12 | F3-L1 × F2 あり × F4-L3 または F4-L4 | 期限切れ | AC-04-01 |
| C-13 | F3-L2 × 任意の期限 × 任意の時刻 | 完了 | AC-04-01 |
| C-14 | F3-L1 × F2 あり × F4-L2（UTC の 14:59:59.999）× F7 の任意の水準 | 未完了 | AC-04-04 |
| C-15 | F3-L1 × F2 あり × F4-L3（UTC の 15:00:00）× F7 の任意の水準 | 期限切れ | AC-04-04 |
| C-20 | F3-L1 × F5-L1 × F8-L1 | 完了になる。タイトルと期限は変わらない | AC-02-01 |
| C-21 | F3-L2 × F5-L1 × F8-L1 | 何も変えず、2xx | AC-02-02, AC-02-07, AC-02-08 |
| C-22 | F3-L2 × F5-L2 × F8-L1 | 未完了になる。タイトルと期限は変わらない | AC-03-01 |
| C-23 | F3-L1 × F5-L2 × F8-L1 | 何も変えず、2xx | AC-03-02, AC-03-05, AC-03-06 |
| C-24 | F5 の任意の水準 × F8-L2 | 何も変えず、404 と `TASK_NOT_FOUND` | OI-006（AC-02-03、AC-03-03） |
| C-30 | F6-L1 | 完了済みを含む、すべてのタスクを表示 | AC-06-02 |
| C-31 | F6-L2 | 未完了のタスク（期限切れを含む）だけを表示 | AC-06-01 |
| C-32 | F6-L3 | 完了済みを表示 | AC-06-03 |
| C-40 | 期限が今日以後のタスクだけ | 期限の日付が早い順 | AC-05-01 |
| C-41 | 期限のあるタスクと、期限のないタスク | 期限のないタスクが最後 | AC-05-02 |
| C-42 | 同じ期限のタスク同士、または期限のないタスク同士（登録時刻が異なる） | 登録の古い順 | AC-05-03, AC-05-06 |
| C-43 | 期限を過ぎたタスク（完了済みを含む）と、期限内のタスク | 期限の日付の昇順。期限切れは期限内より前。現在時刻によらない | OI-003（AC-05-04） |
| C-44 | F6-L1 または F6-L3 × 完了済みと未完了が混在 | 未完了の後ろに完了済み。それぞれ C-40〜C-42 の規則 | AC-05-05, AC-05-06 |
| C-45 | 登録時刻が完全に同じタスク同士 | 登録ごとに増える番号の小さい方が先 | OI-009（AC-05-07、AC-05-08） |
| C-50 | F1-L1、F1-L2、F1-L3 | 受理 | AC-01-01 |
| C-51 | F1-L4 | 拒否。400 と `TITLE_TOO_LONG` | AC-01-02, AC-01-17 |
| C-52 | F1-L5、F1-L6 | 拒否。400 と `TITLE_BLANK` | AC-01-03, AC-01-04, AC-01-17 |
| C-53 | F1-L7、F1-L9 | F1-L7 は拒否、F1-L9 は前後を除いて保存 | OI-017（AC-01-05、AC-01-06） |
| C-54 | F1-L8、F1-L10 | 前後の空白を除いて保存。途中の空白は残る | AC-01-07 |
| C-55 | F1-L12 | 受理。除いた後の値を保存 | OI-004（AC-01-02、AC-01-08） |
| C-56 | F2-L5、F2-L6 | 拒否。400 と `DUE_DATE_INVALID` | OI-005（AC-01-14、AC-01-27） |
| C-57 | F1-L13 | 受理。途中の表示されない文字は除かず、そのまま保存 | OI-017（AC-01-25） |
| C-58 | F2-L7 | 受理 | OI-005（AC-01-26） |
| C-59 | 本文の型や形が定義と違う（タイトルが文字列でない、期限が文字列でない） | 拒否。400 と `MALFORMED_REQUEST`。型の自動変換はしない | OI-013（AC-01-29） |
| C-60 | 本文に定義にない項目がある | 無視して、残りの項目で処理する | OI-013（AC-01-30） |
| C-61 | 完了済みの表示の選択に未知の値 | 拒否。400 と `MALFORMED_REQUEST`。選択は変えない | OI-013（AC-06-07） |
| C-62 | F2-L8 | 拒否。400 と `DUE_DATE_INVALID`。期限の項目を省く・null（F2-L1）は期限なしで受理 | OI-012（AC-01-33、AC-01-34、AC-01-35） |
| C-63 | F2-L9 | 拒否。400 と `DUE_DATE_INVALID` | OI-015（AC-01-36、AC-01-37） |
| C-64 | F5 の任意の水準 × F8-L3 | 何も変えず、404 と `TASK_NOT_FOUND`（C-24 と同じ） | OI-012（AC-02-09、AC-03-08） |
| C-65 | F1-L14 | 拒否。400 と `TITLE_BLANK` | OI-017（AC-01-16、AC-01-17、AC-01-38） |
| C-66 | F1-L15 | 受理。先頭の空白と表示されない文字、最後の表示される文字より後ろの空白と表示されない文字を除いて保存。先頭や、除く文字の後ろにある組になる文字も除く | OI-017（AC-01-39、AC-01-40） |
| C-67 | F1-L16 | 受理。最後の表示される文字の直後に続く組になる文字（異体字セレクタとタグ文字。OI-017 の範囲）は残し、その後ろの空白と表示されない文字を除いて保存 | OI-017（AC-01-40） |
| C-68 | F1-L17 | 受理。除いた後の値を保存し、除いた後の字数を数える | OI-017（AC-01-41） |

### 6. Open Issues

> **削除禁止。** 解決しても行を残し、状態を `Resolved` にして決定内容・決定者・決定日・反映先を記録する。
> 解決（Resolved）にできるのは人間のみ。決定が覆った場合は旧行を `Superseded` にして新規行を追加する。ただし `Won't Fix` と `Superseded` の行は終端なので、覆すときは行を変えず新規行を追加する（03 §4.2）。
> 区分 `Blocking` が未解決の AC に対するテストは、期待値を確定せず `pending` とする。今は未解決の Blocking が無い。
> OI-001〜014 は 2026-11-01 に、OI-015〜017 は 2026-11-03 にオーナーが決めた。オーナーは、サンプルなので一般的なやり方（ベストプラクティス）を選ぶと指示し、AI が示した案を了承した。
> OI-001〜003 は、要件定義書の Q-19〜Q-21 を起票したものである。OI-004〜012、OI-015〜017 は、起草中や合意、実装の際に見つけた、要件から導けない点である。
> 決定で範囲が増えるときは、先に要件定義書へ要件を足し、その要件 ID を出典にする。根拠は [03 §2.1](../../../../docs/testing/03-trs-guideline.md#21-仕様レイヤー) の 2 である。
> 各 OI の選択肢と AI の推奨は、表の後の 6.1 にある。推奨は決定ではない。決定は表の「決定内容」を正とする。

| ID | 起票日 | 起票者 | 区分 | 関連 | 内容（疑問・論点） | 状態 | 決定内容 | 決定者 | 決定日 | 根拠 | 反映先 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| OI-001 | 2026-11-01 | AI | Blocking | AC-01-17〜19, AC-02-07, AC-03-05 | 不正な入力の扱いをどう表すか（成功と拒否の判定方法、応答の形、理由の区分と表し方）。REQ-TODO-24・25 の成功の観測方法も含む。要件定義書の Q-19 | Resolved | 拒否は 400 で、本文は ProblemDetail（RFC 9457）に理由コードの配列 `codes` を足した形にする（順序は仕様にしない）。理由は 2 区分で、`TITLE_BLANK`（空・空白だけ・タイトル欠落）と `TITLE_TOO_LONG`（50 字超）。本文が JSON として読めないときは、同じ形で `MALFORMED_REQUEST`。成否は HTTP ステータスで判定し、成功は 2xx。REQ-TODO-24・25 の二重操作も 2xx で、内容は変わらない | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-01-16〜19, AC-01-24, AC-01-28, AC-02-07, AC-03-05, UC-01-A1, UC-01-A3, C-21, C-23, C-51, C-52 |
| OI-002 | 2026-11-01 | AI | Blocking | F1-L7, F1-L9, AC-01-05〜06 | タイトルの「Unicode の空白文字」の厳密な範囲は何か。要件定義書の Q-20 | Superseded（OI-016） | 空白は、Unicode の White_Space 属性を持つ文字すべて。U+200B・U+FEFF など、持たない文字は空白として扱わない | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-01-05, AC-01-06, AC-01-25, F1-L7, F1-L9, F1-L13, C-53, C-57 |
| OI-003 | 2026-11-01 | AI | Blocking | AC-05-04 | 期限を過ぎたタスク（完了済みを含む）は、「期限が近い順」のどこに並ぶか。要件定義書の Q-21 | Resolved | 期限の日付の昇順。期限切れは期限内より前に並ぶ。完了済みにも同じ規則を使う。現在時刻によらない | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-05-04, UC-05-A1, C-43 |
| OI-004 | 2026-11-01 | AI | Blocking | F1-L12, AC-01-08 | 50 字の数えは、前後の空白を除く前か後か | Resolved | 前後の空白を除いた後の長さを数える | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-01-02, AC-01-08, F1-L12, C-55 |
| OI-005 | 2026-11-01 | AI | Blocking | F2-L5, F2-L6, AC-01-14 | 期限の入力形式、日付として不正な値の扱い、受け付ける日付の範囲は何か | Resolved | 入力は `YYYY-MM-DD` だけ。実在する暦日ならすべて受け付ける。それ以外（時刻つき、別形式、実在しない日）は 400 で `DUE_DATE_INVALID`。日付の範囲は定めない（案 i）ので、要件の追加は要らない。下限は、後に OI-015 で定めた | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-01-14, AC-01-26, AC-01-27, UC-01-A2, F2-L5〜L7, C-56, C-58 |
| OI-006 | 2026-11-01 | AI | Blocking | F8-L2, AC-02-03, AC-03-03 | 存在しないタスクを完了にする、または未完了に戻そうとしたら、どうなるか | Resolved | 存在しないタスクを完了にする・未完了に戻す操作は 404 で、本文は ProblemDetail の `codes` に `TASK_NOT_FOUND`。範囲が増えるので、要件定義書に機能要件 REQ-TODO-33 を足した | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-02-03, AC-03-03, UC-02・UC-03 の出典（REQ-TODO-33）, F8-L2, C-24, 要件定義書の REQ-TODO-33 |
| OI-007 | 2026-11-01 | AI | Blocking | AC-01-22 | 不正な入力の理由として、画面に表示する文言は何か（OI-001 の理由の区分に従う） | Resolved | 画面の理由の文言は、`TITLE_BLANK`「タイトルを入力してください」、`TITLE_TOO_LONG`「タイトルは 50 文字以内で入力してください」、`DUE_DATE_INVALID`「期限の日付が正しくありません」 | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-01-22（`DUE_DATE_INVALID` の文言は、日付を選ぶ部品が不正な日付を作れないので、E2E で確かめない） |
| OI-008 | 2026-11-01 | AI | Non-blocking | F1-L11, AC-01-01〜02, AC-01-15 | 書記素クラスタの判定は、どの規則と版に従うか。AC の例に含めてよい文字はどれか。タイトルを Unicode 正規化しない前提（AC-01-15）でよいか | Resolved | Unicode UAX #29 の拡張書記素クラスタに従う。Unicode 正規化はしない。AC の例は、結合文字と国旗に限る | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-01-01, AC-01-02, AC-01-15, F1-L11, 記法の注記 |
| OI-009 | 2026-11-01 | AI | Non-blocking | UC-05, C-42 | 登録時刻が完全に同じタスクの間で、登録の新旧をどう決めるか。AC-05-03 と AC-05-06 は、登録時刻が異なる例だけを書く | Resolved | 登録ごとに増える番号を 2 番目の基準にする。登録時刻が同じなら、番号の小さい方を古いとする | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-05-07, AC-05-08, C-45, UC-05 の基本フロー 3 |
| OI-010 | 2026-11-01 | AI | Blocking | AC-01-21, AC-01-23, AC-02-05, AC-03-07, AC-04-05 | 未完了・完了・期限切れを、画面にどう見せるか（手段と文言）。期限の日付を画面にどう見せるか。「完了」を文言として扱うか（REQ-TODO-23） | Resolved | 区分は、文言のラベル「未完了」「完了」「期限切れ」で表示する。期限は、一覧に `YYYY-MM-DD` の形で表示する | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ | AC-01-21, AC-01-23, AC-02-05, AC-03-07, AC-04-05 |
| OI-011 | 2026-11-01 | AI | Blocking | AC-01-26, AC-01-28, F2-L7 | 複数の理由が同時にあるときの `codes` の中身と、0001-01-01・9999-12-31 の扱い | Resolved | 理由はすべて集めて `codes` に入れる。0001-01-01 と 9999-12-31 も、実在する暦日として受け付ける | オーナー | 2026-11-01 | AC の合意（2026-11-01）の際にオーナーが了承。#247 のコメント | AC-01-15, AC-01-26, AC-01-28, F2-L7 |
| OI-012 | 2026-11-01 | AI | Non-blocking | UC-01, UC-02, UC-03 | 期限が空文字列のとき、存在しないタスクの識別子の形が不正なとき、0000 年の期限を受け付けるかを、どう扱うか。AC は足していない | Resolved | 期限の空文字列 `""` は 400 で `DUE_DATE_INVALID`（期限の項目を省くか null なら期限なし）。識別子の形が不正なとき（`/tasks/abc/complete`、`/tasks/abc/reopen`）は 404 で、本文は ProblemDetail の `codes` に `TASK_NOT_FOUND`（存在しないタスク OI-006 と同じ）。0000 年の期限は、実在する暦日として受け付ける。この論点は、OI-015 で改めた（0000 年の期限は拒否する） | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ。空文字列は期限を選ばなかった状態と区別できず誤入力の可能性が高い。識別子は中身を見せない値なので、形が不正でもそのタスクは無いとする。当時は範囲の規則が要件に無く、ISO 8601 で実在する日として受け付けた（0001-01-01 を受け付ける OI-011 と一貫）。要件 REQ-TODO-12・13・33 の範囲内なので、要件定義書は変えなかった | AC-01-33〜AC-01-35, AC-02-09, AC-03-08, UC-01-A5, UC-02-A3, UC-03-A3, F2-L8, F8-L3, C-62, C-64。0000 年の論点は OI-015 |
| OI-013 | 2026-11-01 | AI | Blocking | AC-01-29, AC-01-30, AC-06-07 | JSON の型・形が定義と違う要求の扱い。タイトルに数値・真偽値・配列・オブジェクト、期限に数値などが来たとき、完了済みの表示の選択に未知の値が来たとき、定義にない項目が来たとき、どうなるか | Resolved | 型の自動変換はしない。タイトルに数値・真偽値・配列・オブジェクト、期限に数値などの、型や形が定義と違う本文は、400 で `codes` に `MALFORMED_REQUEST`（JSON として読めない本文と同じ扱い）。完了済みの表示の選択に未知の値は、400 で `MALFORMED_REQUEST`。定義にない項目は無視する | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ。要件 REQ-TODO-12・13（不正な入力は登録されず理由が表示される）の範囲内なので、要件定義書は変えない | AC-01-29, AC-01-30, AC-06-07, UC-01-A4, UC-06-A2, C-59〜C-61 |
| OI-014 | 2026-11-01 | AI | Blocking | AC-01-31, AC-01-32 | 登録に成功したとき、前の拒否の理由の表示と、入力欄の内容をどうするか。E2E のミューテーション確認で、どちらも AC が定めていないことに気づいた | Resolved | 登録に成功したら、前の拒否の理由の表示を消し、タイトルと期限の入力欄を空に戻す | オーナー | 2026-11-01 | オーナーの決定。サンプルのため一般的なやり方を選んだ。要件 REQ-TODO-07・13（画面の操作と、不正な入力の理由の表示）の範囲内なので、要件定義書は変えない | AC-01-31, AC-01-32, UC-01 の基本フロー手順 6 |
| OI-015 | 2026-11-01 | AI | Non-blocking | AC-01-36, AC-01-37, F2-L9 | OI-012 で受け付けると決めた 0000 年の期限のうち、閏日の 0000-02-29 は、ドメイン型（先発グレゴリオ暦で 0 年は閏年）では受理されるが、保存先の MySQL 8.4 の DATE 型が保存できず、登録が 500 で失敗する。0000-02-29 をどう扱うか（例: 保存形式を変えて受け付ける、閏日だけ拒否する、年の下限を定める） | Resolved | 期限の下限を 0001-01-01 にする。0000 年の期限は、400 で `DUE_DATE_INVALID` として拒否する。OI-012 の「0000 年の期限を受け付ける」を改める。範囲の規則なので、要件定義書に REQ-TODO-34 を足した | オーナー | 2026-11-03 | オーナーの決定。MySQL 8.4 は 0 年を閏年として扱わない。素の DATE 列で、`'0000-02-28'` は保存でき、`'0000-02-29'` は `ERROR 1292 (22007) Incorrect date value` になった。原因は DB 側で、ドライバではない。多くの日付型も最小値は 0001-01-01 で、0001-01-01 を受け付ける OI-011 と矛盾しない | AC-01-36, AC-01-37, UC-01-A6, F2-L7, F2-L9, C-63, 要件定義書の REQ-TODO-34 |
| OI-016 | 2026-11-03 | AI | Blocking | F1-L7, F1-L9, F1-L13, F1-L14, AC-01-05, AC-01-25, AC-01-38 | OI-002 は、空白を White_Space 属性を持つ文字に限り、U+200B・U+FEFF など表示されない文字を空白として扱わないと決めた。そのため U+200B だけのタイトルが 201 で登録され、一覧でタイトルが見えない行になる。このアプリにはタスクの編集も削除もなく、その行は消せない。NBSP だけのタイトルは拒否されるのに、U+200B だけは通る。表示されない文字だけのタイトルをどう扱うか | Superseded（OI-017） | 空白（White_Space）と、表示されない文字（Unicode の Default_Ignorable_Code_Point。U+200B・U+FEFF・U+2060・異体字セレクタなど）だけでできたタイトルは、400 と `TITLE_BLANK` で拒否する。表示される文字があるタイトルの前後や途中の表示されない文字（絵文字の組み立てに使う U+200D など）は、除かず、そのまま保存する。前後から除くのは、これまでどおり White_Space だけである。空白の範囲（White_Space）は OI-002 から変わらない。OI-002 の決定のうち、表示されない文字を空白として扱わない部分を改めた | オーナー | 2026-11-03 | オーナーの決定。見えないタイトルの行は消せず、NBSP だけは拒否されるのに U+200B だけは通る扱いは、ユーザーから見て分かれる。AI 探索の 2 回目の気づきを受けた。判定の範囲は Unicode の DerivedCoreProperties.txt（15.0.0 と 18.0.0 で同じ）による | AC-01-16, AC-01-17, AC-01-25, AC-01-38, UC-01-A1, F1-L13, F1-L14, C-03, C-53, C-57, C-65, 要件定義書の REQ-TODO-31 |
| OI-017 | 2026-11-03 | AI | Blocking | F1-L13, F1-L15, F1-L16, F1-L17, AC-01-02, AC-01-05, AC-01-06, AC-01-15, AC-01-16, AC-01-17, AC-01-25, AC-01-38, AC-01-39, AC-01-40, AC-01-41 | OI-016 は、前後から除くのを White_Space だけにし、表示される文字があるタイトルの前後の表示されない文字は、除かず保存すると決めた。そのため、次の 2 つの境界が決まっていなかった。(a) 見た目が 50 字のタイトルの末尾に U+200B があると、51 字と数えて `TITLE_TOO_LONG` になる。(b) `[U+200B][半角空白]牛乳を買う` は、前後の空白の除去が U+200B で止まり、見える半角空白が先頭に残って保存される。タイトルの前後の表示されない文字をどう扱うか。前後から除く文字に、例外を設けるか。例外の文字は、どの位置まで残すか | Resolved | タイトルの両端から、空白（White_Space）と表示されない文字（Default_Ignorable_Code_Point）を除いてから、空の判定と字数の判定をし、除いた値を保存する。途中の空白と表示されない文字は、除かず、そのまま保存する。例外として、組になる文字は残す。組になる文字は、異体字セレクタ（Unicode の Variation_Selector 属性を持つ 260 個。U+180B〜180D、U+180F、U+FE00〜FE0F、U+E0100〜E01EF）と、タグ文字（U+E0020〜E007F の 96 個）で、合計 356 個である。残すのは、最後の表示される文字（空白でも表示されない文字でもない文字）の直後に続く、組になる文字の連なりだけである。先頭にあるもの、空白や除く文字の後ろにあるものは、作用する相手が無いので除く。先頭からは、最初の表示される文字より前をすべて除く。末尾からは、最後の表示される文字の直後に続く組になる文字の連なりを残し、その後ろをすべて除く。空の判定（空白と表示されない文字だけなら、400 と `TITLE_BLANK`）は変えない。組になる文字だけのタイトルも、空として拒否する。空白と表示されない文字の範囲は、OI-016 から変わらない。OI-016 の決定のうち、前後から除くのは White_Space だけで、表示される文字があれば前後の表示されない文字も保存する部分を改めた | オーナー | 2026-11-03 | オーナーの決定。#281 のレビューで見つかった境界の (a) 見た目が 50 字なのに長すぎると拒否される、(b) 見える空白が先頭に残る、を解く。例外の理由は、直前の文字と組になって意味を持ち、除くと見た目が変わるため（`❤️` の U+FE0F、地域の旗の絵文字のタグ文字）。組になる相手が無い位置では、意味を持たないので除く。同日にレビューを受けて、この 2 点を足した。範囲は Unicode の DerivedCoreProperties.txt と PropList.txt（15.0.0）による | AC-01-02, AC-01-15, AC-01-25, AC-01-38, AC-01-39, AC-01-40, AC-01-41, UC-01 の基本フロー 3, 記法の注記, F1-L7, F1-L9, F1-L13〜L17, C-53, C-54, C-57, C-65〜C-68, 要件定義書の REQ-TODO-17, REQ-TODO-31 |

**状態の凡例**: `Open` / `In Discussion` / `Resolved` / `Superseded` / `Won't Fix`。`Superseded` は置換先 ID を記載し、`Won't Fix` は理由を必須とする。遷移は 03 §4.2

#### 6.1 起票した Open Issue の選択肢と AI の推奨

> オーナーが判断しやすいように、AI が選択肢と推奨を添えた。**推奨は決定ではない。** 決定は上の表の「決定内容」に記録する。この節は履歴として残す。2026-11-01 の決定は、AI の推奨を基に、リーダーが具体値（理由コード名、404、文言、表示形式など）を足した一覧を、オーナーが了承したものである。了承は #247 のコメントに記録した。

**OI-001（Q-19）不正な入力の扱いの表し方**

決めることは、次の 4 つである。

| 論点 | 案 |
|---|---|
| 応答の形 | A: 400 とし、本文は ProblemDetail（RFC 9457）に、理由コードの配列 `codes` を足した形にする。順序は仕様にしない。B: 400 とし、本文は ProblemDetail に、項目ごとの `errors`（項目名と理由コードの組）を足した形にする。C: 422 とし、本文は A または B の形にする |
| 理由の区分 | i: 空、空白だけ、50 字超の 3 区分にする。ii: 空または空白だけ、50 字超の 2 区分にする。REQ-TODO-16 が「空」と「空白だけ」を 1 つの規則として書いているので、この分け方もあり得る |
| 成功と拒否の判定方法 | ステータスで判定するか、本文で判定するか。REQ-TODO-24 と REQ-TODO-25 の「成功」の観測方法も、ここで決める |
| 本文の不備 | 壊れた本文とタイトルの欠落を、上の形で返すか。別の形にするか |

- AI の推奨: 応答の形は案 A、理由の区分は ii、本文の不備は同じ形で返す。成功と拒否は、ステータスで判定する。
- 理由: 入力項目がタイトルと期限だけで、項目ごとの構造が要らない。要件が「空」と「空白だけ」を 1 つの規則にしている。画面は理由コードを文言に写すので、文言を変えても応答は変わらない。

**OI-002（Q-20）空白の厳密な範囲**

| 案 | 内容 |
|---|---|
| A | Unicode の White_Space 属性を持つ文字すべて。タブ・改行・復帰・垂直タブ・改ページ・U+0085・U+00A0・U+2028 などを含む |
| B | 一般カテゴリの Zs・Zl・Zp に、タブ・改行などの制御文字の空白を、列挙して足したもの |
| C | A に加えて、幅のない文字（U+200B・U+FEFF など）も空白として扱う |

- AI の推奨: 案 A。
- 理由: REQ-TODO-31 が「タブ、改行を含む」と書いており、これらは White_Space 属性を持つ。Unicode が 1 つの一覧として定めているので、範囲を自分で列挙しなくてよい。案 C は幅のない文字だけのタイトルを拒否できるが、要件の「Unicode の空白文字」を超えるので、先に要件を足す必要がある。案 A では、幅のない文字だけのタイトルは受理され、見た目が空になる。

後に OI-016 で、表示されない文字だけのタイトルを拒否すると改めた。White_Space の範囲（案 A）は引き継いでいる。

**OI-003（Q-21）期限切れの並び位置**

| 案 | 内容 |
|---|---|
| A | 期限の日付の昇順。期限切れは、期限内より前に並ぶ。現在時刻によらない |
| B | 今日からの距離の昇順。期限切れと期限内を距離で混ぜる。日が変わると並びが変わる |
| C | 期限内を期限の昇順で先に並べ、期限切れを後ろに並べる |

完了済みで期限を過ぎたタスクにも、同じ規則が及ぶ。

- AI の推奨: 案 A。
- 理由: 現在時刻によらない 1 つの規則で、「期限が近い順」を「期限の日が早い順」と読める。期限を過ぎて放置されたタスクが先頭に出る。DB の並べ替えでも、層1の並べ替えでも、同じ規則で書ける。

**OI-004 50 字の数えと空白の除去の順**

| 案 | 内容 |
|---|---|
| A | 前後の空白を除いた後の長さを数える |
| B | 除く前の長さを数える |

- AI の推奨: 案 A。
- 理由: 保存するタイトルは、前後の空白を除いたものである（REQ-TODO-17）。上限は保存するタイトルへの制約と読める。利用者が、見えない前後の空白のせいで拒否されない。

**OI-005 期限の入力形式と範囲**

| 論点 | 案 |
|---|---|
| 入力形式 | A: ISO 8601 の暦日（YYYY-MM-DD）だけを受け付け、時刻つきや別の形式は拒否する。B: 時刻つきも受け付け、日付の部分だけを使う |
| 範囲 | i: 実在する暦日ならすべて受け付ける。ii: 年の範囲を定める |

- AI の推奨: 入力形式は案 A、範囲は案 i。
- 理由: 期限は日付だけを持つ（REQ-TODO-19）ので、時刻つきを黙って切り捨てると、入力と保存が食い違う。日付を選ぶ部品は、暦日の形で値を渡す。
- 注意: 範囲の案 ii は、年の範囲を業務ルールとして定めるので、要件の追加が要る。範囲を案 i にすると、上限の日の翌日を扱えない実装があり得る。実装の前に、実測で確かめる。

**OI-006 存在しないタスクへの操作**

| 案 | 内容 |
|---|---|
| A | 拒否する。応答の形は OI-001 に従う |
| B | 何も変えず成功とする |

どちらの案でも、存在しない対象の扱いは要件定義書にないので、先に要件を足す（03 §2.1 の 2）。

- AI の推奨: 案 A。
- 理由: REQ-TODO-24 と REQ-TODO-25 の「何も変えず成功」は、存在するタスクへの再操作についての規則である。存在しない対象を成功にすると、取り違えに気づけない。

**OI-007 画面に表示する理由の文言**

| 案 | 内容 |
|---|---|
| A | 理由ごとに固定の文言を決め、AC に書く |
| B | 文言は AC に書かず、理由が表示され、理由ごとに異なることだけを E2E で確かめる |

案 B では、利用者に見える文言が仕様として合意されず、文言の誤りを E2E が検出しない。

- AI の推奨: 案 A。文言の案: 空または空白だけ「タイトルを入力してください」、50 字超「タイトルは 50 字以内で入力してください」。この案は、OI-001 の理由の区分が ii のときのものである。
- 理由: 文言を仕様にすれば、AC で確かめられる。文言はオーナーが決める。文言の網羅は E2E に置かない（02c §5.2）ので、E2E は代表の 1 本だけにする。

**OI-008 書記素クラスタの判定**

| 案 | 内容 |
|---|---|
| A | Unicode UAX #29 の拡張書記素クラスタに従う。AC の例は、結合文字と国旗に限る |
| B | A に加えて、ZWJ で連結した絵文字（例: 家族の絵文字）も AC の例に入れる |

- AI の推奨: 案 A。
- 理由: ZWJ で連結した絵文字は、判定に使う実装の Unicode の版によって 1 字と数えるかが変わり得る。実装を実測してから、AC の例に足すかを決める。

**OI-009 同じ時刻に登録されたタスクの順**

| 案 | 内容 |
|---|---|
| A | 登録ごとに増える番号を 2 番目の基準にする。時刻が同じなら、番号の小さい方を古いとする |
| B | 登録日時だけで決める。同じ時刻の順は決めない。同じ時刻の順が定まらないので、REQ-TODO-27 の変更が要る |

- AI の推奨: 案 A。
- 理由: 時計を固定したテストでは、複数のタスクが完全に同じ時刻に登録され得る。順序が決まらないと、テストが不安定になる。

**OI-010 区分の画面での見せ方**

| 論点 | 案 |
|---|---|
| 手段 | A: 区分ごとに固定の文言ラベル（未完了・完了・期限切れ）を表示する。B: 色・アイコン・取り消し線など、文言以外で表す。C: A と B を併用する |
| 期限の日付 | 一覧に期限の日付を表示するか。表示するなら、その形式。AC-01-21 は、期限切れの区分か期限の日付の表示かのどちらで、選んだ日付が期限になったことを確かめるか |
| 「完了」の扱い | REQ-TODO-23 は「完了と見せる」と書く。「完了」を文言として表示するか、完了を示す印で足りるか |

- AI の推奨: 手段は案 A。期限の日付は一覧に表示する。「完了」も文言として表示する。
- 理由: REQ-TODO-06 と REQ-TODO-23 が区分の名前を挙げている。文言なら、AC に書いて E2E で機械的に確かめられる。色だけだと、見分けが視覚に依存し、確かめにくい。

**OI-011 複数の理由と、暦日の両端**

AC の合意の際に、AC-01-28 と AC-01-26 を確かめ、オーナーが了承した。起票と同じ日に Resolved にした。

| 論点 | 決定した案 |
|---|---|
| 複数の理由 | すべて集めて `codes` に入れる |
| 暦日の両端 | 0001-01-01 と 9999-12-31 も、実在する暦日として受け付ける。受け付ける範囲の規則は書かない |

**OI-013 JSON の型・形が定義と違う要求の扱い**

永続化・HTTP の実装の際に、型の違う本文（タイトルが数値など）が、仕様にないまま登録される振る舞いに気づいて起票した。AI の案は添えず、オーナーが同日に決めた。決定は上の表の「決定内容」を正とする。

**OI-014 登録に成功したときの、理由の表示と入力欄**

E2E のミューテーション確認で、登録の成功後に理由の表示を消さない変更と、入力欄を空に戻さない変更が、どのテストにも検出されなかった。どちらも AC が定めていないためである。AI の案は添えず、オーナーが同日に決めた。決定は上の表の「決定内容」を正とする。

**OI-012 空文字列の期限、識別子の形、0000 年の期限**

AI 探索の気づき（#273）を受けて、3 論点ともオーナーが案 A に決め、AC を足した。決定は上の表の「決定内容」を正とする。次の表は、決める前に示した案である。0000 年の論点は、後に OI-015 で改めた。

| 論点 | 案 |
|---|---|
| 期限の空文字列 | A: 期限の項目を省くか null のときは期限なし。`""` は `DUE_DATE_INVALID`。B: `""` も期限なしにする |
| 識別子の形が不正 | A: 404 と `TASK_NOT_FOUND`。B: 400 |
| 0000 年の期限 | A: ISO 8601 の先発グレゴリオ暦では実在するので受け付ける。B: 0001-01-01 より前は受け付けない。B は範囲の規則になるので、先に要件を足す |

- AI の推奨: 期限の空文字列は案 A、識別子の形は案 A。0000 年の期限は、範囲を定めないので案 A。
- 理由: 空文字列は、期限を選ばなかった状態と区別できず、誤入力の可能性が高い。識別子の形が不正なタスクは、存在しないタスクと同じに扱える。

**OI-015 0000 年の期限と下限**

OI-012 の AC を実装する際に、0000-02-29 だけが登録に失敗することに気づいて起票した。AI の案は添えず、オーナーが 2026-11-03 に決めた。決定は上の表の「決定内容」を正とする。

**OI-016 表示されない文字だけのタイトル**

AI 探索の 2 回目の気づきを受けて起票した。OI-002 の決定を改めるので、OI-002 を Superseded にして、この行を足した（03 §4.2）。AI の案は添えず、オーナーが 2026-11-03 に決めた。決定は上の表の「決定内容」を正とする。

後に OI-017 で、前後から除くものを空白だけから広げた。空の判定は引き継いでいる。

**OI-017 前後から除く表示されない文字**

起票のきっかけは、#281 のレビューで見つかった境界である。OI-016 の保存する値の決定を改めるので、OI-016 を Superseded にして、この行を足した（03 §4.2）。オーナーが先に、前後の空白をトリミングして登録する方針を示した。AI は、表示されない文字も前後から除く案と、除く文字に例外を設けるかの選択肢を示し、オーナーが推奨どおり、2026-11-03 に決めた。決定は上の表の「決定内容」を正とする。次の表は、決める前に示した例外の案である。

| 文字 | 前後から除いたときの影響 | AI の推奨 |
|---|---|---|
| 異体字セレクタ（U+FE00〜FE0F、U+E0100〜E01EF） | `❤️`（U+2764 U+FE0F）が文字の形の `❤` に、異体字（IVS）の漢字が標準の字形に変わる | 除かない |
| タグ文字（U+E0020〜E007F） | 地域の旗の絵文字が、黒い旗だけになる | 除かない |
| それ以外（U+200B・U+FEFF・U+2060・U+00AD・U+200D など） | 見た目は変わらない | 除く |

同日に、レビューを受けて、オーナーが決定を 2 点足した（#283 の 2 つ目のコメント）。

- 例外の文字は、最後の表示される文字の直後に続くときだけ残す。先頭にあるもの、空白や除く文字の後ろにあるものは、相手が無いので除く。前の案は、例外の文字に当たったら、そこで止めていた。
- モンゴル文字の自由異体字セレクタ（U+180B〜180D、U+180F）も例外にする。例外は、Variation_Selector 属性を持つ 260 個とタグ文字の 96 個で、合計 356 個になる。
- 2 つ目のコメントの「Variation_Selector の全体（356 個）」は、オーナーが 3 つ目のコメントで訂正した。Variation_Selector は 260 個で、タグ文字の 96 個を足した合計が 356 個である。決定の中身は変わらない。

---

## 第3部：運用情報

### 7. トレーサビリティ

すべての AC にテストが紐づいている。層1と層2（Integration / E2E）のテストは実装済みである。

| AC ID | 層1（型/Unit/PBT） | 層2（Integration/E2E） | 層3（探索） | 状態 |
|---|---|---|---|---|
| AC-01-01 | `TaskConstraintTableTest::AC-01-01`, `TaskTitleTest::AC-01-01` | — | — | 実装済 |
| AC-01-02 | `TaskConstraintTableTest::AC-01-02`, `TaskTitleTest::AC-01-02` | — | — | 実装済 |
| AC-01-03 | `TaskConstraintTableTest::AC-01-03` | — | — | 実装済 |
| AC-01-04 | `TaskConstraintTableTest::AC-01-04`, `TaskTitleTest::AC-01-04` | — | — | 実装済 |
| AC-01-05 | `TaskConstraintTableTest::AC-01-05`, `TaskTitleTest::AC-01-05` | — | — | 実装済 |
| AC-01-06 | `TaskConstraintTableTest::AC-01-06` | — | — | 実装済 |
| AC-01-07 | `TaskConstraintTableTest::AC-01-07`, `TaskTitleTest::AC-01-07` | — | — | 実装済 |
| AC-01-08 | `TaskConstraintTableTest::AC-01-08` | — | — | 実装済 |
| AC-01-09 | `TaskTest::AC-01-09` | — | — | 実装済 |
| AC-01-10 | `TaskTest::AC-01-10` | — | — | 実装済 |
| AC-01-11 | `TaskTest::AC-01-11` | — | — | 実装済 |
| AC-01-12 | `TaskTest::AC-01-12` | — | — | 実装済 |
| AC-01-13 | — | `TaskApiHttpTest::AC-01-13` | — | 実装済 |
| AC-01-14 | `DueDateTest::AC-01-14`, `TaskConstraintTableTest::AC-01-14` | — | — | 実装済 |
| AC-01-15 | — | `TaskApiHttpTest::AC-01-15` | — | 実装済 |
| AC-01-16 | — | `TaskApiHttpTest::AC-01-16` | — | 実装済 |
| AC-01-17 | — | `TaskApiHttpTest::AC-01-17` | — | 実装済 |
| AC-01-18 | — | `TaskApiHttpTest::AC-01-18` | — | 実装済 |
| AC-01-19 | — | `TaskApiHttpTest::AC-01-19` | — | 実装済 |
| AC-01-20 | — | `TaskListE2eTest::AC-01-20` | 探索対象 | 実装済 |
| AC-01-21 | — | `TaskListE2eTest::AC-01-21` | 探索対象 | 実装済 |
| AC-01-22 | — | `TaskListE2eTest::AC-01-22` | 探索対象 | 実装済 |
| AC-01-23 | — | `TaskListE2eTest::AC-01-23` | 探索対象 | 実装済 |
| AC-01-24 | — | `TaskApiHttpTest::AC-01-24` | — | 実装済 |
| AC-01-25 | `TaskConstraintTableTest::AC-01-25`, `TaskTitleTest::AC-01-25` | — | — | 実装済 |
| AC-01-26 | `DueDateTest::AC-01-26`, `TaskConstraintTableTest::AC-01-26` | — | — | 実装済 |
| AC-01-27 | — | `TaskApiHttpTest::AC-01-27` | — | 実装済 |
| AC-01-28 | — | `TaskApiHttpTest::AC-01-28` | — | 実装済 |
| AC-01-29 | — | `TaskApiHttpTest::AC-01-29` | — | 実装済 |
| AC-01-30 | — | `TaskApiHttpTest::AC-01-30` | — | 実装済 |
| AC-01-31 | — | `TaskListE2eTest::AC-01-31` | 探索対象 | 実装済 |
| AC-01-32 | — | `TaskListE2eTest::AC-01-32` | 探索対象 | 実装済 |
| AC-01-33 | `TaskConstraintTableTest::AC-01-33`, `DueDateTest::AC-01-33` | — | — | 実装済 |
| AC-01-34 | — | `TaskApiHttpTest::AC-01-34` | — | 実装済 |
| AC-01-35 | — | `TaskApiHttpTest::AC-01-35` | — | 実装済 |
| AC-01-36 | `TaskConstraintTableTest::AC-01-36`, `DueDateTest::AC-01-36` | — | — | 実装済 |
| AC-01-37 | — | `TaskApiHttpTest::AC-01-37` | — | 実装済 |
| AC-01-38 | `TaskConstraintTableTest::AC-01-38`, `TaskTitleTest::AC-01-38` | — | — | 実装済 |
| AC-01-39 | `TaskConstraintTableTest::AC-01-39`, `TaskTitleTest::AC-01-39` | — | — | 実装済 |
| AC-01-40 | `TaskConstraintTableTest::AC-01-40`, `TaskTitleTest::AC-01-40` | — | — | 実装済 |
| AC-01-41 | `TaskConstraintTableTest::AC-01-41`, `TaskTitleTest::AC-01-41` | — | — | 実装済 |
| AC-02-01 | `TaskConstraintTableTest::AC-02-01`, `TaskTest::AC-02-01` | — | — | 実装済 |
| AC-02-02 | — | `TaskOperationHttpTest::AC-02-02` | — | 実装済 |
| AC-02-03 | — | `TaskOperationHttpTest::AC-02-03` | — | 実装済 |
| AC-02-04 | — | `TaskOperationHttpTest::AC-02-04` | — | 実装済 |
| AC-02-05 | — | `TaskListE2eTest::AC-02-05` | 探索対象 | 実装済 |
| AC-02-07 | — | `TaskOperationHttpTest::AC-02-07` | — | 実装済 |
| AC-02-08 | `TaskConstraintTableTest::AC-02-08`, `TaskTest::AC-02-08` | — | — | 実装済 |
| AC-02-09 | — | `TaskOperationHttpTest::AC-02-09` | — | 実装済 |
| AC-03-01 | `TaskConstraintTableTest::AC-03-01`, `TaskTest::AC-03-01` | — | — | 実装済 |
| AC-03-02 | — | `TaskOperationHttpTest::AC-03-02` | — | 実装済 |
| AC-03-03 | — | `TaskOperationHttpTest::AC-03-03` | — | 実装済 |
| AC-03-04 | — | `TaskOperationHttpTest::AC-03-04` | — | 実装済 |
| AC-03-05 | — | `TaskOperationHttpTest::AC-03-05` | — | 実装済 |
| AC-03-06 | `TaskConstraintTableTest::AC-03-06`, `TaskTest::AC-03-06` | — | — | 実装済 |
| AC-03-07 | — | `TaskListE2eTest::AC-03-07` | 探索対象 | 実装済 |
| AC-03-08 | — | `TaskOperationHttpTest::AC-03-08` | — | 実装済 |
| AC-04-01 | `TaskCategoryTest::AC-04-01` | — | — | 実装済 |
| AC-04-02 | `TaskCategoryTest::AC-04-02` | — | — | 実装済 |
| AC-04-03 | — | `TaskListHttpTest::AC-04-03` | — | 実装済 |
| AC-04-04 | `TaskCategoryTest::AC-04-04` | `TaskListHttpTest::AC-04-04` | — | 実装済 |
| AC-04-05 | — | `TaskListE2eTest::AC-04-05` | 探索対象 | 実装済 |
| AC-04-06 | — | `TaskListHttpTest::AC-04-06` | — | 実装済 |
| AC-05-01 | `TaskConstraintTableTest::AC-05-01`, `TaskListTest::AC-05-01` | — | — | 実装済 |
| AC-05-02 | `TaskConstraintTableTest::AC-05-02`, `TaskListTest::AC-05-02` | — | — | 実装済 |
| AC-05-03 | `TaskConstraintTableTest::AC-05-03`, `TaskListTest::AC-05-03`, `TaskTest::AC-05-03` | — | — | 実装済 |
| AC-05-04 | `TaskConstraintTableTest::AC-05-04`, `TaskListTest::AC-05-04` | — | — | 実装済 |
| AC-05-05 | `TaskConstraintTableTest::AC-05-05`, `TaskListTest::AC-05-05` | — | — | 実装済 |
| AC-05-06 | — | `TaskListHttpTest::AC-05-06` | — | 実装済 |
| AC-05-07 | `TaskConstraintTableTest::AC-05-07`, `TaskListTest::AC-05-07` | — | — | 実装済 |
| AC-05-08 | — | `TaskListHttpTest::AC-05-08` | — | 実装済 |
| AC-06-01 | `TaskConstraintTableTest::AC-06-01`, `TaskListTest::AC-06-01` | — | — | 実装済 |
| AC-06-02 | `TaskConstraintTableTest::AC-06-02`, `TaskListTest::AC-06-02` | — | — | 実装済 |
| AC-06-03 | `TaskConstraintTableTest::AC-06-03` | `TaskListHttpTest::AC-06-03` | — | 実装済 |
| AC-06-04 | — | `TaskListHttpTest::AC-06-04` | — | 実装済 |
| AC-06-05 | — | `TaskListE2eTest::AC-06-05` | 探索対象 | 実装済 |
| AC-06-06 | — | `TaskListHttpTest::AC-06-06` | — | 実装済 |
| AC-06-07 | — | `TaskListHttpTest::AC-06-07` | — | 実装済 |

### 8. 不具合還元履歴

> 04 の不具合還元プロトコルで本機能に対して行った還元を記録する（削除禁止）。

| BUG ID | 発見日 | 発見層 | 根本原因の分類 | 還元先 | 追加テスト | 反映した TRS 項目 |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

### 9. 変更履歴

| 日付 | 版 | 変更者 | 変更内容 | 承認者 |
|---|---|---|---|---|
| 2026-11-01 | 0.1 | AI | 初版の案を、要件定義書 TODO-001 から起草。Open Issues OI-001〜010 を起票 | 未承認 |
| 2026-11-01 | 0.2 | AI | OI-001〜010 のオーナーの決定を反映。`@pending` を外し、Then を書いた。AC を 6 本足した | 未承認 |
| 2026-11-01 | 1.0 | AI | AC の合意の際の 2 点を OI-011 に記録し、AC-01-15・AC-01-28 などを直した。OI-012 を起票した | オーナー |
| 2026-11-01 | 1.1 | AI | ドメイン層（層1）の実装に合わせて、紐づくテストとトレーサビリティの表を更新した。AC の内容は変えていない | — |
| 2026-11-01 | 1.2 | AI | 永続化・HTTP・画面の実装に合わせて、層2（Integration / E2E）の紐づくテストとトレーサビリティの表を更新した。AC の内容は変えていない | — |
| 2026-11-01 | 1.3 | AI | OI-013（JSON の型・形が定義と違う要求の扱い）を起票し、オーナーの決定を反映した。UC-01-A4・UC-06-A2、AC-01-29・AC-01-30・AC-06-07、C-59〜C-61 を足した。既存の AC は変えていない。AC の追加はオーナーが OI-013 の決定とあわせて了承した(2026-11-01、#249 のコメント) | オーナー |
| 2026-11-01 | 1.4 | AI | OI-014（登録に成功したときの、理由の表示と入力欄）を起票し、オーナーの決定を反映した。UC-01 の基本フローに手順 6、AC-01-31・AC-01-32 を足した。既存の AC は変えていない。決定と AC の追加はオーナーが了承した(2026-11-01、#251 のコメント) | オーナー |
| 2026-11-01 | 1.5 | AI | OI-012（空文字列の期限、識別子の形、0000 年の期限）にオーナーの決定を反映した。UC-01-A5・UC-02-A3・UC-03-A3、AC-01-33〜AC-01-37・AC-02-09・AC-03-08、F2-L8・F2-L9・F8-L3、C-62〜C-64 を足した。既存の AC は変えていない。決定はオーナーが了承した(2026-11-01、#273 のコメント) | オーナー |
| 2026-11-03 | 1.6 | AI | OI-015（0000 年の期限と下限）を起票し、オーナーの決定を反映した。OI-012 の 0000 年の論点を改め、AC-01-36・AC-01-37 を「0000 年の期限は拒否」に作り直した。UC-01-A6、F2-L9・C-63 を直し、要件定義書に REQ-TODO-34 を足した。AC-01-36・AC-01-37 以外の AC は変えていない。決定はオーナーが了承した(2026-11-03、#273 のコメント) | オーナー |
| 2026-11-03 | 1.7 | AI | OI-016（表示されない文字だけのタイトル）を起票し、オーナーの決定を反映した。OI-002 を Superseded にした。AC-01-38 を足し、AC-01-25 は「表示される文字があれば、表示されない文字は除かず保存する」に絞って、U+200B だけと U+FEFF だけの例を AC-01-38 に移した（受理から拒否へ変わる）。AC-01-16・AC-01-17 に U+200B の例を足した。UC-01-A1、F1-L13、F1-L14、C-03、C-53、C-57、C-65 を直した。上のほかの AC は変えていない。決定はオーナーが了承した(2026-11-03、#281 の本文の決定) | オーナー |
| 2026-11-03 | 1.8 | AI | OI-017（前後から除く表示されない文字）を起票し、オーナーの決定を反映した。OI-016 を Superseded にした。AC-01-25 は「途中の表示されない文字は除かず保存する」に絞り、前後の例を AC-01-39 に移した（前後の U+200B などは、受理のまま、保存される値が除かれた値に変わる）。AC-01-39（前後の空白と表示されない文字を除く）、AC-01-40（表示される文字の直後の異体字セレクタとタグ文字は残し、それ以外の位置のものは除く）、AC-01-41（除いた後の字数で受理する）を足した。AC-01-02・AC-01-15・AC-01-38 に例を足した。UC-01 の基本フロー 3、記法の段落、F1-L7・F1-L9 の備考、F1-L13〜L17、C-53・C-54・C-57・C-65〜C-68 を直した。上のほかの AC は変えていない。決定はオーナーが了承した(2026-11-03、#283 の 1 つ目と 2 つ目のコメントの決定。3 つ目のコメントで例外の個数を訂正した) | オーナー |

---

## レビューチェックリスト

- [ ] 仕様レイヤーとテスト設計レイヤーが分離されている
- [ ] US/UC の出典が実在する要件 ID で、要件定義書の全要件 ID がどれかの出典に現れる
- [ ] 全 AC が Gherkin 形式で、検証層タグがある
- [ ] 期待値の根拠が仕様／デザインであり、既存コード由来ではない
- [ ] 因子の無効水準と、型で排除できる制約が明記されている
- [ ] 因子制約に、禁止組合せと組合せ別の期待結果がある
- [ ] Blocking の Open Issue が未解決のまま実装に進んでいない
- [ ] Open Issues の履歴（解決済みを含む）が残っている
- [ ] `@pending` の AC を除く全 AC にテストが紐づいている
