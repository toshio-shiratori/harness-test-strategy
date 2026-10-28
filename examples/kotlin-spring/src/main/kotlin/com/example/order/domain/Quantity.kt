package com.example.order.domain

enum class QuantityError { OUT_OF_RANGE }

@JvmInline
value class Quantity private constructor(val value: Int) {
    companion object {
        const val MIN = 1
        const val MAX = 999

        fun of(value: Int): Validated<Quantity, QuantityError> =
            if (value in MIN..MAX) Validated.Valid(Quantity(value))
            else Validated.Invalid(QuantityError.OUT_OF_RANGE)
    }
}
