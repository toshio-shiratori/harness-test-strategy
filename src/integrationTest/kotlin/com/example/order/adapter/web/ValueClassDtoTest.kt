package com.example.order.adapter.web

import com.example.order.domain.Quantity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** 検証用: value class をそのまま受ける DTO。本番では使わない（02a §1.2）。 */
data class ValueClassRequest(val quantity: Quantity)

@RestController
class ValueClassProbeController {
    @PostMapping("/probe/value-class")
    fun probe(@RequestBody request: ValueClassRequest): Int = request.quantity.value
}

/**
 * 02a §1.2 の根拠となる実測。Jackson 3 + jackson-module-kotlin は value class を private コンストラクタ経由で
 * 直接生成するため、`Quantity.of` の検証（1〜999）を素通りして不変条件を破れる。
 * このテストが失敗するようになった（検証が効くようになった）場合は、02a §1.2 のルールを見直すこと。
 */
@WebMvcTest(ValueClassProbeController::class)
@AutoConfigureRestTestClient
class ValueClassDtoTest {
    @Autowired
    lateinit var client: RestTestClient

    @Test
    fun `value class を DTO に直接使うと、範囲外の値がファクトリの検証を素通りする`() {
        val result = client.post().uri("/probe/value-class")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"quantity":5000}""")
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)

        result.responseBody shouldBe "5000" // MAX(999) を超える値で Quantity が存在してしまう
    }
}
