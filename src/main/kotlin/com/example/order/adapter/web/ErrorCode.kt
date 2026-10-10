package com.example.order.adapter.web

/** 400 応答の `codes` に載せる機械可読なエラーコード。`detail` の文言は仕様にしない（TRS ORD-001 OI-003, OI-006）。 */
enum class ErrorCode {
    OUT_OF_RANGE,
    BLANK,
    INVALID_JSON,
    MISSING_FIELD,
}

const val CODES_PROPERTY = "codes"
