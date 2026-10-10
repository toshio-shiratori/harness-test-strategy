package com.example.payment.domain

import com.example.order.domain.Validated
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 期待値は TRS PAY-001 の AC を正とする（実装から逆生成しない）。時刻は注入した Clock の現在時刻（OI-001）。 */
class PaymentTransitionsTest : StringSpec({
    val at = Instant.EPOCH
    val clock = Clock.fixed(at, ZoneOffset.UTC)
    val rejected = Validated.Invalid(TransitionError.INVALID_TRANSITION)

    "AC-02-01 Pending は authorize で Authorized になり、時刻は Clock の現在時刻になる" {
        PaymentState.Pending.authorize(clock).valid() shouldBe PaymentState.Authorized(at)
    }

    "AC-02-02 Pending は fail で Failed になり、理由が保たれる" {
        FailureReason.entries.forEach { reason ->
            PaymentState.Pending.fail(reason).valid() shouldBe PaymentState.Failed(reason)
        }
    }

    "AC-02-03 Authorized は capture で Captured になり、時刻は Clock の現在時刻になる" {
        val later = Clock.fixed(at.plusSeconds(60), ZoneOffset.UTC)

        PaymentState.Authorized(at).capture(later).valid() shouldBe PaymentState.Captured(at.plusSeconds(60))
    }

    "AC-02-04 Authorized は fail で Failed になり、理由が保たれる" {
        FailureReason.entries.forEach { reason ->
            PaymentState.Authorized(at).fail(reason).valid() shouldBe PaymentState.Failed(reason)
        }
    }

    "AC-02-05 許可されていない遷移は INVALID_TRANSITION で拒否される" {
        PaymentState.Pending.capture(clock) shouldBe rejected
        PaymentState.Authorized(at).authorize(clock) shouldBe rejected
    }

    "AC-02-06 終端状態からはどの操作でも遷移できない" {
        listOf(PaymentState.Captured(at), PaymentState.Failed(FailureReason.TIMEOUT)).forEach { terminal ->
            terminal.authorize(clock) shouldBe rejected
            terminal.capture(clock) shouldBe rejected
            terminal.fail(FailureReason.CARD_DECLINED) shouldBe rejected
        }
    }

    // --- 操作列 PBT（04 §2.2「状態遷移の抜け」の還元先） ---

    // 操作の種類と失敗理由を独立に生成し、種類に応じて Op を組み立てる
    val ops: Arb<Op> = Arb.bind(Arb.enum<OpKind>(), Arb.enum<FailureReason>()) { kind, reason ->
        when (kind) {
            OpKind.AUTHORIZE -> Op.Authorize
            OpKind.CAPTURE -> Op.Capture
            OpKind.FAIL -> Op.Fail(reason)
        }
    }
    val opSequences = Arb.list(ops, 0..20)

    "AC-02-07 任意の操作列で、許可された遷移だけが状態を変え、拒否された操作は状態を変えない" {
        checkAll(opSequences) { sequence ->
            var state: PaymentState = PaymentState.Pending
            sequence.forEach { op ->
                val result = state.apply(op, clock)
                val expected = allowed[kindOf(state) to op.kind]
                if (expected == null) {
                    result shouldBe rejected
                } else {
                    kindOf(result.valid()) shouldBe expected
                    state = result.valid()
                }
            }
        }
    }

    "AC-02-08 終端状態に達した後は、以降どんな操作列でも状態が変わらない" {
        checkAll(opSequences, opSequences) { before, after ->
            var state: PaymentState = PaymentState.Pending
            before.forEach { op -> (state.apply(op, clock) as? Validated.Valid)?.let { state = it.value } }
            if (kindOf(state) in terminalKinds) {
                val terminal = state
                after.forEach { op -> terminal.apply(op, clock) shouldBe rejected }
            }
        }
    }
})

private enum class StateKind { PENDING, AUTHORIZED, CAPTURED, FAILED }
private enum class OpKind { AUTHORIZE, CAPTURE, FAIL }

private sealed interface Op {
    val kind: OpKind

    data object Authorize : Op {
        override val kind = OpKind.AUTHORIZE
    }

    data object Capture : Op {
        override val kind = OpKind.CAPTURE
    }

    data class Fail(val reason: FailureReason) : Op {
        override val kind = OpKind.FAIL
    }
}

/** TRS PAY-001 の遷移表（独立したオラクル）。表に無い組合せは全て拒否される。 */
private val allowed = mapOf(
    (StateKind.PENDING to OpKind.AUTHORIZE) to StateKind.AUTHORIZED,
    (StateKind.PENDING to OpKind.FAIL) to StateKind.FAILED,
    (StateKind.AUTHORIZED to OpKind.CAPTURE) to StateKind.CAPTURED,
    (StateKind.AUTHORIZED to OpKind.FAIL) to StateKind.FAILED,
)

private val terminalKinds = setOf(StateKind.CAPTURED, StateKind.FAILED)

private fun kindOf(state: PaymentState): StateKind =
    when (state) {
        PaymentState.Pending -> StateKind.PENDING
        is PaymentState.Authorized -> StateKind.AUTHORIZED
        is PaymentState.Captured -> StateKind.CAPTURED
        is PaymentState.Failed -> StateKind.FAILED
    }

private fun PaymentState.apply(op: Op, clock: Clock): Validated<PaymentState, TransitionError> =
    when (op) {
        Op.Authorize -> authorize(clock)
        Op.Capture -> capture(clock)
        is Op.Fail -> fail(op.reason)
    }

private fun Validated<PaymentState, TransitionError>.valid(): PaymentState = (this as Validated.Valid).value
