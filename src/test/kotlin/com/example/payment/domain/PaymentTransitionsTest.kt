package com.example.payment.domain

import com.example.order.domain.Validated
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.time.Instant

/** 期待値は TRS PAY-001 の AC を正とする（実装から逆生成しない）。時刻の値は検証しない（OI-001）。 */
class PaymentTransitionsTest : StringSpec({
    val at = Instant.EPOCH
    val rejected = Validated.Invalid(TransitionError.INVALID_TRANSITION)

    "AC-02-01 Pending は authorize で Authorized になる" {
        PaymentState.Pending.authorize(at).valid().shouldBeInstanceOf<PaymentState.Authorized>()
    }

    "AC-02-02 Pending は fail で Failed になり、理由が保たれる" {
        FailureReason.entries.forEach { reason ->
            PaymentState.Pending.fail(reason).valid() shouldBe PaymentState.Failed(reason)
        }
    }

    "AC-02-03 Authorized は capture で Captured になる" {
        PaymentState.Authorized(at).capture(at).valid().shouldBeInstanceOf<PaymentState.Captured>()
    }

    "AC-02-04 Authorized は fail で Failed になり、理由が保たれる" {
        FailureReason.entries.forEach { reason ->
            PaymentState.Authorized(at).fail(reason).valid() shouldBe PaymentState.Failed(reason)
        }
    }

    "AC-02-05 許可されていない遷移は INVALID_TRANSITION で拒否される" {
        PaymentState.Pending.capture(at) shouldBe rejected
        PaymentState.Authorized(at).authorize(at) shouldBe rejected
    }

    "AC-02-06 終端状態からはどの操作でも遷移できない" {
        listOf(PaymentState.Captured(at), PaymentState.Failed(FailureReason.TIMEOUT)).forEach { terminal ->
            terminal.authorize(at) shouldBe rejected
            terminal.capture(at) shouldBe rejected
            terminal.fail(FailureReason.CARD_DECLINED) shouldBe rejected
        }
    }

    // --- 操作列 PBT（04 §2.2「状態遷移の抜け」の還元先） ---

    // 操作の種類・時刻・失敗理由を独立に生成し、種類に応じて Op を組み立てる
    val ops: Arb<Op> = Arb.bind(
        Arb.enum<OpKind>(),
        Arb.long(0L..1_000_000L).map { Instant.ofEpochSecond(it) },
        Arb.enum<FailureReason>(),
    ) { kind, time, reason ->
        when (kind) {
            OpKind.AUTHORIZE -> Op.Authorize(time)
            OpKind.CAPTURE -> Op.Capture(time)
            OpKind.FAIL -> Op.Fail(reason)
        }
    }
    val opSequences = Arb.list(ops, 0..20)

    "AC-02-07 任意の操作列で、許可された遷移だけが状態を変え、拒否された操作は状態を変えない" {
        checkAll(opSequences) { sequence ->
            var state: PaymentState = PaymentState.Pending
            sequence.forEach { op ->
                val result = state.apply(op)
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
            before.forEach { op -> (state.apply(op) as? Validated.Valid)?.let { state = it.value } }
            if (kindOf(state) in terminalKinds) {
                val terminal = state
                after.forEach { op -> terminal.apply(op) shouldBe rejected }
            }
        }
    }
})

private enum class StateKind { PENDING, AUTHORIZED, CAPTURED, FAILED }
private enum class OpKind { AUTHORIZE, CAPTURE, FAIL }

private sealed interface Op {
    val kind: OpKind

    data class Authorize(val at: Instant) : Op {
        override val kind = OpKind.AUTHORIZE
    }

    data class Capture(val at: Instant) : Op {
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

private fun PaymentState.apply(op: Op): Validated<PaymentState, TransitionError> =
    when (op) {
        is Op.Authorize -> authorize(op.at)
        is Op.Capture -> capture(op.at)
        is Op.Fail -> fail(op.reason)
    }

private fun Validated<PaymentState, TransitionError>.valid(): PaymentState = (this as Validated.Valid).value
