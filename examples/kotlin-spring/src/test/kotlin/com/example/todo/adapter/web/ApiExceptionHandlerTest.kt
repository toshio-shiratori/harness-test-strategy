package com.example.todo.adapter.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** コントローラーに到達する前の 400 を `codes` つきの ProblemDetail にそろえることを、層1で確かめる。入力（読み取れない本文）から 400 に至る経路は、Integration（`TaskApiHttpTest`）が同じ AC で確かめる。ここは、例外ハンドラーの出力だけを確かめる。 */
class ApiExceptionHandlerTest : StringSpec({
    // 期待値は制約表 5.2 の C-69（400、本文は ProblemDetail で status は 400、codes は MALFORMED_REQUEST だけ）から取った
    "AC-01-18 読み取れない本文は、400 の ProblemDetail で、codes は MALFORMED_REQUEST だけ" {
        val problem = ApiExceptionHandler().onUnreadable()

        problem.status shouldBe 400
        problem.properties?.get(CODES_PROPERTY) shouldBe listOf("MALFORMED_REQUEST")
    }
})
