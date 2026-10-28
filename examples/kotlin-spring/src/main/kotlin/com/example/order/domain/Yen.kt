package com.example.order.domain

@JvmInline
value class Yen private constructor(val amount: Long) {
    operator fun plus(other: Yen): Yen = Yen(Math.addExact(amount, other.amount))

    companion object {
        const val MAX = 1_000_000_000L

        fun of(amount: Long): Validated<Yen, YenError> =
            if (amount in 0..MAX) Validated.Valid(Yen(amount)) else Validated.Invalid(YenError.OUT_OF_RANGE)
    }
}

enum class YenError { OUT_OF_RANGE }
