package com.example.order.adapter.web

import com.example.order.domain.Quantity
import com.example.order.domain.Sku
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
        override fun execute(sku: Sku, quantity: Quantity) {
            received += sku.value to quantity.value
        }
    }

    @Test
    fun `AC-01-04 正しい JSON は 204 で、ユースケースにドメイン型として渡る`() {
        recorder.received.clear()

        client.post().uri("/orders/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"sku":"SKU-1","quantity":3}""")
            .exchange()
            .expectStatus().isNoContent

        recorder.received shouldBe listOf("SKU-1" to 3)
    }

    @Test
    fun `AC-01-05 範囲外の数量は 400 の ProblemDetail JSON を返し、ユースケースは呼ばれない`() {
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
    fun `AC-01-06 不正な JSON は 400 になり、ユースケースは呼ばれない`() {
        recorder.received.clear()

        client.post().uri("/orders/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"sku":"SKU-1","quantity":""")
            .exchange()
            .expectStatus().isBadRequest

        recorder.received shouldBe emptyList()
    }

    @Test
    fun `AC-01-07 quantity が欠けた JSON は 400 になる（Kotlin の non-null を JSON 境界でも守る）`() {
        client.post().uri("/orders/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"sku":"SKU-1"}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    private fun post(body: String) = client.post().uri("/orders/items")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()

    /** `codes` は集合として扱う（順序は仕様にしない。TRS ORD-001 OI-006）。 */
    private fun RestTestClient.ResponseSpec.expectCodes(vararg expected: String) {
        expectStatus().isBadRequest
        expectBody()
            .jsonPath("$.codes").value<List<String>> { it.sorted() shouldBe expected.toList().sorted() }
    }

    @Test
    fun `AC-01-08 sku が空・空白のみなら 400 で BLANK になり、ユースケースは呼ばれない`() {
        listOf("", " ", "\u3000").forEach { sku ->
            recorder.received.clear()

            post("""{"sku":"$sku","quantity":3}""").expectCodes("BLANK")

            recorder.received shouldBe emptyList()
        }
    }

    @Test
    fun `AC-01-09 範囲外の数量の 400 には codes に OUT_OF_RANGE が付く`() {
        post("""{"sku":"SKU-1","quantity":1000}""").expectCodes("OUT_OF_RANGE")
    }

    @Test
    fun `AC-01-10 壊れた JSON と型不一致の 400 には codes に INVALID_JSON が付く`() {
        listOf(
            """{"sku":"SKU-1","quantity":""",
            """{"sku":"SKU-1","quantity":"3"}""",
            """{"sku":"SKU-1","quantity":1.5}""",
        ).forEach { body ->
            recorder.received.clear()

            post(body).expectCodes("INVALID_JSON")

            recorder.received shouldBe emptyList()
        }
    }

    @Test
    fun `AC-01-11 必須フィールドの欠落・null の 400 には codes に MISSING_FIELD が付く`() {
        listOf(
            """{"sku":"SKU-1"}""",
            """{"quantity":3}""",
            """{"sku":null,"quantity":3}""",
            """{"sku":"SKU-1","quantity":null}""",
        ).forEach { body ->
            recorder.received.clear()

            post(body).expectCodes("MISSING_FIELD")

            recorder.received shouldBe emptyList()
        }
    }

    @Test
    fun `AC-01-12 sku が空白かつ数量が範囲外なら codes に BLANK と OUT_OF_RANGE の両方が付く`() {
        recorder.received.clear()

        post("""{"sku":" ","quantity":1000}""").expectCodes("BLANK", "OUT_OF_RANGE")

        recorder.received shouldBe emptyList()
    }
}
