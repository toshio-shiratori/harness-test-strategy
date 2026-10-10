package com.example.order.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class SkuTest : StringSpec({
    "AC-01-08 空白でない文字列は Sku として受理され、値が保たれる" {
        checkAll(Arb.string(1..20).filter { it.isNotBlank() }) { raw ->
            (Sku.of(raw) as Validated.Valid).value.value shouldBe raw
        }
    }

    "AC-01-08 空文字・空白のみ（全角空白を含む）は BLANK で拒否される" {
        listOf("", " ", "\t\n", "　").forEach { raw ->
            Sku.of(raw) shouldBe Validated.Invalid(SkuError.BLANK)
        }
    }
})
