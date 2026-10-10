package com.example.payment.domain

import java.time.Instant

sealed interface PaymentState {
    data object Pending : PaymentState
    data class Authorized(val authorizedAt: Instant) : PaymentState
    data class Captured(val capturedAt: Instant) : PaymentState
    data class Failed(val reason: FailureReason) : PaymentState
}

enum class FailureReason { CARD_DECLINED, TIMEOUT }

// when を「式」として使い、else を置かない。
// PaymentState に状態を追加すると、この関数を含む全ての when がコンパイルエラーになる。
fun label(state: PaymentState): String =
    when (state) {
        PaymentState.Pending -> "保留"
        is PaymentState.Authorized -> "与信済"
        is PaymentState.Captured -> "確定"
        is PaymentState.Failed -> "失敗"
    }
