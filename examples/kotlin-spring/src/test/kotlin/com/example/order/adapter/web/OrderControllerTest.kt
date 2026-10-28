package com.example.order.adapter.web

import com.example.order.domain.Quantity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity

/**
 * Spring コンテナなしで、境界の変換（DTO → ドメイン型 → HTTP ステータス）を層1で検証する。
 * 層1の他のテストと同じく Kotest（StringSpec）で書く。JUnit の `@Test` は `./gradlew test` のエンジン構成では実行されない。
 */
class OrderControllerTest : StringSpec({

    /** ユースケースに渡った (sku, quantity) を記録するコントローラー。テストごとに作る。 */
    class Fixture {
        val received = mutableListOf<Pair<String, Int>>()
        val controller = OrderController { sku, quantity -> received += sku.value to quantity.value }
    }

    fun codesOf(response: ResponseEntity<*>) =
        ((response.body as ProblemDetail).properties?.get(CODES_PROPERTY) as List<*>).toSet()

    "AC-01-04 範囲内の数量は 204 で、ユースケースにドメイン型として渡る" {
        val f = Fixture()

        val response = f.controller.add(AddItemRequest("SKU-1", 3))

        response.statusCode shouldBe HttpStatus.NO_CONTENT
        f.received shouldBe listOf("SKU-1" to 3)
    }

    "AC-01-05 範囲外の数量は 400 の ProblemDetail で、ユースケースは呼ばれない" {
        val f = Fixture()

        val response = f.controller.add(AddItemRequest("SKU-1", Quantity.MAX + 1))

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        (response.body as ProblemDetail).status shouldBe 400
        f.received shouldBe emptyList()
    }

    "AC-01-08 sku が空白なら 400 で codes は BLANK のみ、ユースケースは呼ばれない" {
        val f = Fixture()

        listOf("", " ", "\u3000").forEach { sku ->
            val response = f.controller.add(AddItemRequest(sku, 3))

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            codesOf(response) shouldBe setOf("BLANK")
        }
        f.received shouldBe emptyList()
    }

    "AC-01-12 sku が空白かつ数量が範囲外なら codes に両方が付く" {
        val f = Fixture()

        val response = f.controller.add(AddItemRequest(" ", Quantity.MAX + 1))

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        codesOf(response) shouldBe setOf("BLANK", "OUT_OF_RANGE")
        f.received shouldBe emptyList()
    }

    "AC-01-11 sku か quantity が null なら 400 で codes は MISSING_FIELD のみ" {
        val f = Fixture()

        listOf(AddItemRequest(null, 3), AddItemRequest("SKU-1", null), AddItemRequest(null, null)).forEach { request ->
            val response = f.controller.add(request)

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            codesOf(response) shouldBe setOf("MISSING_FIELD")
        }
        f.received shouldBe emptyList()
    }
})
