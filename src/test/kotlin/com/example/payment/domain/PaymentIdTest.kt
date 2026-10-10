package com.example.payment.domain

import com.example.order.domain.Validated
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class PaymentIdTest : StringSpec({
    "空白でない文字列は PaymentId として受理され、値が保たれる" {
        checkAll(Arb.string(1..20).filter { it.isNotBlank() }) { raw ->
            (PaymentId.of(raw) as Validated.Valid).value.value shouldBe raw
        }
    }

    "空文字・空白のみは BLANK で拒否される" {
        listOf("", " ", "\t\n").forEach { raw ->
            PaymentId.of(raw) shouldBe Validated.Invalid(PaymentIdError.BLANK)
        }
    }
})
