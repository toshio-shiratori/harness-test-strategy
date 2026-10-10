package com.example.payment.domain

import com.example.order.domain.Validated
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * TRS PAY-001 §5.2「組合せ別の期待結果」の各行を、データ駆動テストの1ケースにする（03 §3.5）。
 * 期待結果は制約表の値を使い、実装の挙動から逆生成しない。禁止組合せ（§5.1）は PAY-001 では該当なし。
 * コンテナ名の先頭に AC ID を置くので、TraceabilityTest で AC と紐づく（03 §3.4）。
 */
class PaymentTransitionTableTest : FunSpec({
    val at = Instant.EPOCH
    val clock = Clock.fixed(at, ZoneOffset.UTC)
    val rejected = Validated.Invalid(TransitionError.INVALID_TRANSITION)

    // 制約表の「条件」列（F1 × F2）。操作は水準 F2-L1〜L3 に対応する。
    data class Case(val id: String, val state: PaymentState, val operation: String, val apply: (PaymentState) -> Validated<PaymentState, TransitionError>)

    val authorize = "authorize" to { s: PaymentState -> s.authorize(clock) }
    val capture = "capture" to { s: PaymentState -> s.capture(clock) }
    val fail = "fail" to { s: PaymentState -> s.fail(FailureReason.TIMEOUT) }
    fun case(id: String, state: PaymentState, op: Pair<String, (PaymentState) -> Validated<PaymentState, TransitionError>>) =
        Case(id, state, op.first, op.second)

    context("AC-02-05 許可されていない遷移は INVALID_TRANSITION で拒否される（C-20, C-21）") {
        withData(
            nameFn = { "${it.id} ${it.state::class.simpleName} × ${it.operation}" },
            ts = listOf(
                case("C-20", PaymentState.Pending, capture),
                case("C-21", PaymentState.Authorized(at), authorize),
            ),
        ) { it.apply(it.state) shouldBe rejected }
    }

    context("AC-02-06 終端状態からはどの操作でも遷移できない（C-22）") {
        withData(
            nameFn = { "${it.id} ${it.state::class.simpleName} × ${it.operation}" },
            ts = listOf(PaymentState.Captured(at), PaymentState.Failed(FailureReason.TIMEOUT)).flatMap { state ->
                listOf(authorize, capture, fail).map { op -> case("C-22", state, op) }
            },
        ) { it.apply(it.state) shouldBe rejected }
    }
})
