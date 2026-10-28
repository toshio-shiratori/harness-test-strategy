package com.example.payment.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PaymentStateTest : StringSpec({
    "全ての状態に表示ラベルがある" {
        label(PaymentState.Pending) shouldBe "保留"
        label(PaymentState.Authorized(Instant.EPOCH)) shouldBe "与信済"
        label(PaymentState.Captured(Instant.EPOCH)) shouldBe "確定"
        label(PaymentState.Failed(FailureReason.TIMEOUT)) shouldBe "失敗"
    }
})
