-- TRS TODO-001: タスクと、完了済みの表示の選択の保存。

-- 登録ごとに増える番号（TaskId）を払い出す採番専用の表（OI-009）。MySQL に SEQUENCE は無いので AUTO_INCREMENT を使う。
CREATE TABLE task_id_sequence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (id)
);

CREATE TABLE task (
    id         BIGINT      NOT NULL,
    -- タイトルは書記素で 50 字までだが、コードポイントの数には上限が無い（結合文字はいくつでも付けられる）。
    -- 列の長さで有効なタイトルを拒否しないよう、長さの上限が無い型にする。文字集合は 4 バイト文字（絵文字・国旗）を保存できる utf8mb4。
    title      LONGTEXT CHARACTER SET utf8mb4 NOT NULL,
    -- 期限は日付だけ（時刻を持たない）
    due_date   DATE        NULL,
    status     VARCHAR(16) NOT NULL,
    -- 登録時刻。UTC。マイクロ秒未満は保存しない
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 未知の status を拒否する
    CONSTRAINT chk_task_status CHECK (status IN ('OPEN', 'DONE'))
);

-- 完了済みの表示の選択。利用者は 1 人なので 1 行だけ（id = 1）。行が無ければ「保存されていない」
CREATE TABLE completed_visibility (
    id         TINYINT     NOT NULL,
    visibility VARCHAR(8)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_completed_visibility_id CHECK (id = 1),
    CONSTRAINT chk_completed_visibility_value CHECK (visibility IN ('SHOW', 'HIDE'))
);
