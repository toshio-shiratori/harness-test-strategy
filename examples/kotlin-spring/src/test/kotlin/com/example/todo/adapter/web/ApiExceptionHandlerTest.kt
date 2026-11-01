package com.example.todo.adapter.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** コントローラーに到達する前の 400 を `codes` つきの ProblemDetail にそろえることを、層1で確かめる技術的な検証。HTTP を通す確認は Integration（`TaskApiHttpTest`）が受け持つ。 */
class ApiExceptionHandlerTest : StringSpec({
    "読み取れない本文は、400 の ProblemDetail で、codes は MALFORMED_REQUEST だけ" {
        val problem = ApiExceptionHandler().onUnreadable()

        problem.status shouldBe 400
        problem.properties?.get(CODES_PROPERTY) shouldBe listOf("MALFORMED_REQUEST")
    }
})
