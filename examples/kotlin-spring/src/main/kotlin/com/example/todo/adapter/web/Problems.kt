package com.example.todo.adapter.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/** ProblemDetail（RFC 9457）に、理由コードの配列 `codes` を足した本文。順序は仕様にしない。 */
internal fun problem(status: HttpStatus, codes: List<ErrorCode>, detail: String): ProblemDetail =
    ProblemDetail.forStatusAndDetail(status, detail).also {
        it.setProperty(CODES_PROPERTY, codes.map(ErrorCode::name))
    }
