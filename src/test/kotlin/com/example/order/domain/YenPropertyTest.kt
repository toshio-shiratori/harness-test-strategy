package com.example.order.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

// ドメイン型ごとに Arb を 1 つ用意し、ファクトリの検証と同じ制約を満たす値だけを生成する
val arbYen: Arb<Yen> = Arb.long(0L..Yen.MAX / 2).map { (Yen.of(it) as Validated.Valid).value }

class YenPropertyTest : StringSpec({

    // 分類: 交換則・結合則
    "金額の加算は交換則を満たす" {
        checkAll(arbYen, arbYen) { a, b -> (a + b) shouldBe (b + a) }
    }

    "金額の加算は結合則を満たす" {
        checkAll(arbYen, arbYen, arbYen) { a, b, c -> ((a + b) + c) shouldBe (a + (b + c)) }
    }

    // 分類: 不変量（範囲外は必ず拒否される）
    "範囲外の金額は必ず Invalid になる" {
        checkAll(Arb.long().map { if (it in 0..Yen.MAX) -1L else it }) { n ->
            Yen.of(n).shouldBeInstanceOf<Validated.Invalid<*>>()
        }
    }
})
