package com.example.todo.adapter.web

import com.example.todo.domain.TaskTitle
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
data class ValueClassRequest(val title: TaskTitle)

@RestController
class ValueClassProbeController {
    @PostMapping("/probe/value-class")
    fun probe(@RequestBody request: ValueClassRequest): Int = request.title.value.length
}

/**
 * 02a §1.2 の根拠となる実測（AC に紐づかない技術的な検証）。Jackson 3 + jackson-module-kotlin が、
 * 文字列を包む value class を `private constructor` 経由で直接生成して `TaskTitle.of` の検証を素通りするかを確かめる。
 * 結果は 02a.md の §1.2 に書いている。このテストが失敗するようになった（検証が効くようになった）場合は、02a §1.2 の規則 5 を見直すこと。
 */
@WebMvcTest(ValueClassProbeController::class)
@AutoConfigureRestTestClient
class ValueClassDtoTest {
    @Autowired
    lateinit var client: RestTestClient

    private fun probe(body: String): String? =
        client.post().uri("/probe/value-class")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBody

    @Test
    fun `value class を DTO に直接使うと、空のタイトルと 51 字のタイトルがファクトリの検証を素通りする`() {
        probe("""{"title":""}""") shouldBe "0" // 空のタイトルで TaskTitle が存在してしまう
        probe("""{"title":"${"あ".repeat(51)}"}""") shouldBe "51" // 上限 50 字を超える TaskTitle が存在してしまう
    }
}
