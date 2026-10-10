package com.example.order.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class QuantityTest : StringSpec({

    "範囲内の整数は受理され、値が保たれる" {
        checkAll(Arb.int(Quantity.MIN..Quantity.MAX)) { n ->
            (Quantity.of(n) as Validated.Valid).value.value shouldBe n
        }
    }

    "範囲外の整数は OUT_OF_RANGE で拒否される" {
        checkAll(Arb.int().filter { it !in Quantity.MIN..Quantity.MAX }) { n ->
            Quantity.of(n) shouldBe Validated.Invalid(QuantityError.OUT_OF_RANGE)
        }
    }

    "境界値 1 と 999 は受理、0 と 1000 は拒否" {
        listOf(1, 999).forEach { (Quantity.of(it) is Validated.Valid<*>) shouldBe true }
        listOf(0, 1000).forEach { Quantity.of(it) shouldBe Validated.Invalid(QuantityError.OUT_OF_RANGE) }
    }
})
