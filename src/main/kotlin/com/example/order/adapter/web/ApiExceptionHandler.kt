package com.example.order.adapter.web

import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * コントローラーに到達する前に Spring が落とす 400 を、`codes` つきの ProblemDetail にそろえる。
 * 壊れた JSON と型不一致は INVALID_JSON。解析段階の失敗は単独のコードで返し、他のコードとは混ぜない（TRS ORD-001 OI-006）。
 */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onUnreadable(): ProblemDetail = problem(listOf(ErrorCode.INVALID_JSON), "リクエスト本文を読み取れません")
}
