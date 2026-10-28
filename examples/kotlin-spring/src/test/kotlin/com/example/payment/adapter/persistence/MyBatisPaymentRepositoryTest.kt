package com.example.payment.adapter.persistence

import com.example.order.domain.Validated
import com.example.payment.domain.FailureReason
import com.example.payment.domain.PaymentId
import com.example.payment.domain.PaymentState
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * `MyBatisPaymentRepository` を、メモリ上のマッパーで検証する層1のテスト。Spring・DB は使わない。
 * 実 DB での保存・読み戻し（TRS PAY-002 AC-03-01〜09）は `PaymentRepositoryIntegrationTest`（層2）が受け持つため、
 * ここでは Repository がマッパーへ委譲する振る舞いだけを確認する。層2の対応表は変えないため、テスト名に AC 番号は付けない。
 */
class MyBatisPaymentRepositoryTest : StringSpec({
    /** 行をそのまま保持するマッパー。upsert は同じ ID を上書きし、呼び出しを順に記録する。 */
    class InMemoryMapper : PaymentMapper {
        val rows = mutableMapOf<String, PaymentRow>()
        val upserted = mutableListOf<PaymentRow>()

        override fun upsert(row: PaymentRow) {
            upserted += row.copy()
            rows[row.id] = row.copy()
        }

        override fun findById(id: String): PaymentRow? = rows[id]?.copy()
    }

    fun id(raw: String): PaymentId = (PaymentId.of(raw) as Validated.Valid).value

    "保存した状態を、同じ ID で読み戻せる" {
        val repository = MyBatisPaymentRepository(InMemoryMapper())
        val at = Instant.parse("2026-10-11T00:00:00.123456Z")
        val states = listOf(
            PaymentState.Pending,
            PaymentState.Authorized(at),
            PaymentState.Captured(at),
            PaymentState.Failed(FailureReason.TIMEOUT),
        )

        states.forEachIndexed { i, state ->
            repository.save(id("p-$i"), state)
        }

        states.forEachIndexed { i, state ->
            repository.find(id("p-$i")) shouldBe state
        }
    }

    "保存した行は、渡した ID をキーにマッパーへ届く" {
        val mapper = InMemoryMapper()
        MyBatisPaymentRepository(mapper).save(id("p-1"), PaymentState.Pending)

        mapper.rows.keys shouldBe setOf("p-1")
    }

    "保存されていない ID は null（なし）で、例外にならない" {
        val repository = MyBatisPaymentRepository(InMemoryMapper())
        repository.save(id("p-1"), PaymentState.Pending)

        repository.find(id("p-unknown")).shouldBeNull()
    }

    "同じ ID で保存し直すたびに、渡した状態の行がそのままマッパーへ渡り、遷移ルールは検査されない" {
        // 上書き自体（同じ ID の行を置き換える）は DB の upsert の責務で、層2（PAY-002 AC-03-06）が受け持つ。ここは委譲だけを見る
        val mapper = InMemoryMapper()
        val repository = MyBatisPaymentRepository(mapper)

        repository.save(id("p-1"), PaymentState.Failed(FailureReason.TIMEOUT))
        repository.save(id("p-1"), PaymentState.Pending) // Failed から Pending への保存も拒否しない

        mapper.upserted.map { it.id to it.status } shouldBe listOf("p-1" to "FAILED", "p-1" to "PENDING")
    }

    "状態と列が矛盾する行は、読み込みが例外で失敗する" {
        val mapper = InMemoryMapper()
        mapper.rows["p-1"] = PaymentRow(id = "p-1", status = "AUTHORIZED")

        shouldThrowAny { MyBatisPaymentRepository(mapper).find(id("p-1")) }
    }
})
