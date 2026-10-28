package com.example.payment.adapter.persistence

import com.example.payment.domain.PaymentId
import com.example.payment.domain.PaymentRepository
import com.example.payment.domain.PaymentState
import org.springframework.stereotype.Repository

/** [PaymentRepository] の MyBatis 実装。`PaymentState` ↔ 行の変換は [PaymentRow] のファイルにある。 */
@Repository
class MyBatisPaymentRepository(private val mapper: PaymentMapper) : PaymentRepository {
    override fun save(id: PaymentId, state: PaymentState) {
        mapper.upsert(state.toRow(id.value))
    }

    override fun find(id: PaymentId): PaymentState? = mapper.findById(id.value)?.toState()
}
