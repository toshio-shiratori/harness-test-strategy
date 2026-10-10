-- TRS PAY-002: 決済の状態の保存。状態と列の整合は DB の CHECK 制約でも守る（アプリを経由しない書き込みも拒否する）。
CREATE TABLE payment (
    id             VARCHAR(64) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    authorized_at  DATETIME(6) NULL,
    captured_at    DATETIME(6) NULL,
    failure_reason VARCHAR(16) NULL,
    PRIMARY KEY (id),
    -- 未知の status を拒否する（PAY-002 OI-005）
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'FAILED')),
    -- 状態ごとに、持つべき列だけが NULL でない（PAY-002 AC-03-09）
    CONSTRAINT chk_payment_columns CHECK (
        (status = 'PENDING'    AND authorized_at IS NULL     AND captured_at IS NULL     AND failure_reason IS NULL)
        OR (status = 'AUTHORIZED' AND authorized_at IS NOT NULL AND captured_at IS NULL     AND failure_reason IS NULL)
        OR (status = 'CAPTURED'   AND authorized_at IS NULL     AND captured_at IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED'     AND authorized_at IS NULL     AND captured_at IS NULL     AND failure_reason IS NOT NULL)
    )
);
