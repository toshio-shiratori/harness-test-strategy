package com.example.property

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * 02a §2.3 の項目3・4（失敗時のシード出力、夜間の実行回数増量）が、設定と Kotest の挙動で成り立っていることの検証。
 * 特定の AC に紐づかない技術的な不変条件のテスト（03 §3.4）。Kotest の更新で挙動や設定名が変わったときに落ちる。
 */
// PropTestConfig は ExperimentalKotest。実験的 API なので、使う範囲を本クラスに限る
@OptIn(ExperimentalKotest::class)
class PropertyTestingConfigTest : StringSpec({
    // わざと落ちる Property（反例は 500 以上）
    val failing: suspend (PropTestConfig) -> Unit = { config ->
        checkAll(config, Arb.int(0..1000)) { check(it < 500) { "boom" } }
    }
    val seedInMessage = Regex("""(?s).*Repeat this test by using seed (-?\d+).*""")

    // 例: "Arg 0: 500 (shrunk from 988)"。縮小後の値は括弧の前まで
    val shrunkArg0 = Regex("""Arg 0: (.+?)(?: \(shrunk from .*\))?\s*$""", RegexOption.MULTILINE)

    "失敗した Property のメッセージに、再現用のシードが含まれる" {
        val error = shouldThrow<AssertionError> { failing(PropTestConfig()) }

        error.message.orEmpty() shouldMatch seedInMessage
    }

    "出力されたシードを PropTestConfig(seed = ...) に渡すと、同じ失敗を再現できる" {
        val first = shouldThrow<AssertionError> { failing(PropTestConfig()) }
        val seed = seedInMessage.matchEntire(first.message.orEmpty())!!.groupValues[1].toLong()

        val replayed = shouldThrow<AssertionError> { failing(PropTestConfig(seed = seed)) }

        replayed.message shouldBe first.message
    }

    "失敗した Property のメッセージから、縮小後の反例（Arg 0）を取り出せる" {
        val error = shouldThrow<AssertionError> { failing(PropTestConfig()) }

        val match = shrunkArg0.find(error.message.orEmpty())!!
        // 反例は 500 以上の値のうち最小の 500 まで縮小される。縮小前の値（shrunk from）は含まれない
        match.groupValues[1] shouldBe "500"
    }

    "実行回数は kotest.proptest.default.iteration.count で切り替わる（未指定なら Kotest の既定の 1000）" {
        var count = 0

        checkAll(Arb.int()) { count++ }

        count shouldBe (System.getProperty("kotest.proptest.default.iteration.count")?.toInt() ?: 1000)
    }
})
