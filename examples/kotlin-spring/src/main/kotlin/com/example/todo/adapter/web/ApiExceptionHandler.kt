package com.example.todo.adapter.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * コントローラーに到達する前に Spring が落とす 400 を、`codes` つきの ProblemDetail にそろえる。
 * 本文が JSON として読めないときは MALFORMED_REQUEST（TRS TODO-001 OI-001）。
 */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onUnreadable(): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, listOf(ErrorCode.MALFORMED_REQUEST), "リクエスト本文を読み取れません")
}
