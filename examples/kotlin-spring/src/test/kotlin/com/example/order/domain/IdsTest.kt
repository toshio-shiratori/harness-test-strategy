package com.example.order.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class IdsTest : StringSpec({

    "空白でない文字列は UserId / OrderId として受理され、値が保たれる" {
        checkAll(Arb.string(1..20).filter { it.isNotBlank() }) { raw ->
            (UserId.of(raw) as Validated.Valid).value.value shouldBe raw
            (OrderId.of(raw) as Validated.Valid).value.value shouldBe raw
        }
    }

    "空文字・空白のみは BLANK で拒否される" {
        listOf("", " ", "\t\n").forEach { raw ->
            UserId.of(raw) shouldBe Validated.Invalid(IdError.BLANK)
            OrderId.of(raw) shouldBe Validated.Invalid(IdError.BLANK)
        }
    }

    "cancelOrder は型の合った引数を受け付ける" {
        val orderId = (OrderId.of("o-1") as Validated.Valid).value
        val userId = (UserId.of("u-1") as Validated.Valid).value
        cancelOrder(orderId, userId)
    }
})
