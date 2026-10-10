package com.example.payment.domain

import com.example.order.domain.Validated
import java.time.Clock

// いずれも when を「式」として使い、else を置かない。PaymentState に状態を追加すると、全ての遷移関数がコンパイルエラーになる。
// 許可する遷移は PAY-001 の 4 つだけ。同じ状態への再実行を含め、それ以外は全て拒否する（OI-002, OI-003）。

/** 与信。Pending だけが Authorized になれる。時刻は注入された Clock から取る（PAY-001 OI-001）。 */
fun PaymentState.authorize(clock: Clock): Validated<PaymentState, TransitionError> =
    when (this) {
        PaymentState.Pending -> Validated.Valid(PaymentState.Authorized(clock.instant()))
        is PaymentState.Authorized, is PaymentState.Captured, is PaymentState.Failed -> invalidTransition()
    }

/** 確定。Authorized だけが Captured になれる（与信を飛ばした確定は不可）。 */
fun PaymentState.capture(clock: Clock): Validated<PaymentState, TransitionError> =
    when (this) {
        is PaymentState.Authorized -> Validated.Valid(PaymentState.Captured(clock.instant()))
        PaymentState.Pending, is PaymentState.Captured, is PaymentState.Failed -> invalidTransition()
    }

/** 失敗。Pending と Authorized だけが Failed になれ、理由が保たれる。 */
fun PaymentState.fail(reason: FailureReason): Validated<PaymentState, TransitionError> =
    when (this) {
        PaymentState.Pending, is PaymentState.Authorized -> Validated.Valid(PaymentState.Failed(reason))
        is PaymentState.Captured, is PaymentState.Failed -> invalidTransition()
    }

private fun invalidTransition(): Validated<PaymentState, TransitionError> =
    Validated.Invalid(TransitionError.INVALID_TRANSITION)
