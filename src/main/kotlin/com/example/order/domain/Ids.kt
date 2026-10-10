package com.example.order.domain

// 異なる意味の String を取り違えられなくする（ブランド型相当）
@JvmInline
value class UserId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Validated<UserId, IdError> =
            if (raw.isBlank()) Validated.Invalid(IdError.BLANK) else Validated.Valid(UserId(raw))
    }
}

@JvmInline
value class OrderId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Validated<OrderId, IdError> =
            if (raw.isBlank()) Validated.Invalid(IdError.BLANK) else Validated.Valid(OrderId(raw))
    }
}

enum class IdError { BLANK }

fun cancelOrder(orderId: OrderId, by: UserId) {
    check(orderId.value.isNotEmpty() && by.value.isNotEmpty()) // 実処理は省略
}
// cancelOrder(userId, orderId)  // ← コンパイルエラー（引数の取り違えを排除）
