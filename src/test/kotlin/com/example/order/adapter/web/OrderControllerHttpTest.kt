package com.example.order.adapter.web

import com.example.order.domain.Quantity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * HTTP/JSON の結合テスト（Integration 層）。Web スライスだけを起動し、
 * Jackson 3 による JSON ⇔ data class 変換と、ProblemDetail の JSON 形を実際に通して検証する。
 * ユースケースは記録用の実装に差し替える（Mockito は value class 引数と相性が悪いため使わない）。
 */
@WebMvcTest(OrderController::class)
@AutoConfigureRestTestClient
@Import(OrderControllerHttpTest.TestConfig::class)
class OrderControllerHttpTest {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var recorder: RecordingAddItemUseCase

    @TestConfiguration
    class TestConfig {
        @Bean
        fun recorder() = RecordingAddItemUseCase()
    }

    class RecordingAddItemUseCase : AddItemUseCase {
        val received = mutableListOf<Pair<String, Int>>()
        override fun execute(sku: String, quantity: Quantity) {
            received += sku to quantity.value
        }
    }

    @Test
    fun `正しい JSON は 204 で、ユースケースにドメイン型として渡る`() {
        recorder.received.clear()

        client.post().uri("/orders/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"sku":"SKU-1","quantity":3}""")
            .exchange()
            .expectStatus().isNoContent

        recorder.received shouldBe listOf("SKU-1" to 3)
    }

    @Test
    fun `範囲外の数量は 400 の ProblemDetail JSON を返し、ユースケースは呼ばれない`() {
        recorder.received.clear()

        client.post().uri("/orders/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"sku":"SKU-1","quantity":1000}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.title").isEqualTo("Bad Request")
            .jsonPath("$.detail").isNotEmpty

        recorder.received shouldBe emptyList()
    }

    @Test
    fun `不正な JSON は 400 になり、ユースケースは呼ばれない`() {
        recorder.received.clear()

        client.post().uri("/orders/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"sku":"SKU-1","quantity":""")
            .exchange()
            .expectStatus().isBadRequest

        recorder.received shouldBe emptyList()
    }

    @Test
    fun `quantity が欠けた JSON は 400 になる（Kotlin の non-null を JSON 境界でも守る）`() {
        client.post().uri("/orders/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"sku":"SKU-1"}""")
            .exchange()
            .expectStatus().isBadRequest
    }
}
