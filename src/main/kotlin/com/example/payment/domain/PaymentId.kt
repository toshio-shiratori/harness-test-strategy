package com.example.payment.domain

import com.example.order.domain.Validated

/** 決済の ID。異なる意味の String と取り違えられなくする（02 §1.1）。 */
@JvmInline
value class PaymentId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Validated<PaymentId, PaymentIdError> =
            if (raw.isBlank()) Validated.Invalid(PaymentIdError.BLANK) else Validated.Valid(PaymentId(raw))
    }
}

enum class PaymentIdError { BLANK }
