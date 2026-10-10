package com.example.order.domain

enum class SkuError { BLANK }

/** 商品の識別子。空・空白のみ（`isBlank` 相当。全角空白を含む）は作れない。 */
@JvmInline
value class Sku private constructor(val value: String) {
    companion object {
        fun of(raw: String): Validated<Sku, SkuError> =
            if (raw.isBlank()) Validated.Invalid(SkuError.BLANK) else Validated.Valid(Sku(raw))
    }
}
