package com.example.payment.domain

/**
 * 決済の状態の保存と読み戻し（TRS PAY-002）。実装は Adapter 層（`payment.adapter.persistence`）。
 * 遷移ルール（PAY-001）の強制はドメインの責務で、実装はしない（`save` は渡された状態をそのまま保存する）。
 */
interface PaymentRepository {
    /** 同じ ID が保存済みなら上書きする。 */
    fun save(id: PaymentId, state: PaymentState)

    /** 未保存の ID は `null`（なし）。保存された行が不正なら例外（型は契約にしない。PAY-002 OI-005）。 */
    fun find(id: PaymentId): PaymentState?
}
