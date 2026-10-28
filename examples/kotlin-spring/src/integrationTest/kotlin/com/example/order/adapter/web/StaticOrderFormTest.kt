package com.example.order.adapter.web

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * 注文追加フォーム（`static/index.html`）を Spring が静的リソースとして配信することの確認（Integration 層）。
 * Web スライスの `WebMvcAutoConfiguration` が静的リソースを配線するため、`@SpringBootTest` は不要（実測済み）。
 * ルートパス `/` のウェルカムページは MockMvc 上では `index.html` への forward になり本文を取れないので、`/index.html` で確認する。
 * コントローラーの依存（ユースケース）には、サンプルの実装 `LoggingAddItemUseCase` をそのまま使う。
 * 画面の振る舞い（入力・送信・表示）はブラウザ E2E の対象で、ここでは配信と、E2E が使う要素の存在だけを見る。
 */
@WebMvcTest(OrderController::class)
@AutoConfigureRestTestClient
@Import(LoggingAddItemUseCase::class)
class StaticOrderFormTest {
    @Autowired
    lateinit var client: RestTestClient

    private fun html(path: String): String {
        val result = client.get().uri(path)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .returnResult(String::class.java)
        return requireNotNull(result.responseBody)
    }

    @Test
    fun `フォームが index_html として静的リソースから配信される`() {
        val body = html("/index.html")

        listOf(
            """data-testid="order-form"""",
            """data-testid="sku-input"""",
            """data-testid="quantity-input"""",
            """data-testid="submit-button"""",
            """data-testid="result"""",
            """data-testid="result-codes"""",
        ).forEach { body shouldContain it }
        body shouldContain "/orders/items" // 既存の追加 API を呼ぶ
    }
}
