package com.example.todo.adapter.web

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * 画面（`static/index.html`）を Spring が静的リソースとして配信することの確認（層2 Integration）。
 * 画面の振る舞い（入力・登録・表示）はブラウザ E2E の対象で、ここでは配信と、E2E が使う要素と呼ぶ API の存在だけを見る。
 * ルートパス `/` のウェルカムページは MockMvc 上では `index.html` への forward になり本文を取れないので、`/index.html` で確認する。
 */
class StaticTaskPageTest : TaskApiTestBase() {
    private fun html(path: String): String {
        val result = client.get().uri(path)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .returnResult(String::class.java)
        return requireNotNull(result.responseBody)
    }

    @Test
    fun `画面が index_html として静的リソースから配信される`() {
        val body = html("/index.html")

        listOf(
            """data-testid="task-form"""",
            """data-testid="title-input"""",
            """data-testid="due-date-input"""",
            """data-testid="register-button"""",
            """data-testid="errors"""",
            """data-testid="hide-completed-toggle"""",
            """data-testid="task-list"""",
        ).forEach { body shouldContain it }
        // 画面が呼ぶ API
        listOf("/tasks", "/settings/completed-visibility").forEach { body shouldContain it }
    }
}
