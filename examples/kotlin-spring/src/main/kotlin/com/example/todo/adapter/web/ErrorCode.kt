package com.example.todo.adapter.web

/** 4xx 応答の `codes` に載せる機械可読な理由コード。`detail` の文言は仕様にしない（TRS TODO-001 OI-001, OI-006）。 */
enum class ErrorCode {
    TITLE_BLANK,
    TITLE_TOO_LONG,
    DUE_DATE_INVALID,
    MALFORMED_REQUEST,
    TASK_NOT_FOUND,
}

const val CODES_PROPERTY = "codes"
