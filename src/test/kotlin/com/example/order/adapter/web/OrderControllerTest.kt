package com.example.order.adapter.web

import com.example.order.domain.Quantity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/** Spring コンテナなしで、境界の変換（DTO → ドメイン型 → HTTP ステータス）を層1で検証する。 */
class OrderControllerTest {
    private val received = mutableListOf<Pair<String, Int>>()
    private val controller = OrderController { sku, quantity -> received += sku to quantity.value }

    @Test
    fun `範囲内の数量は 204 で、ユースケースにドメイン型として渡る`() {
        val response = controller.add(AddItemRequest("SKU-1", 3))

        response.statusCode shouldBe HttpStatus.NO_CONTENT
        received shouldBe listOf("SKU-1" to 3)
    }

    @Test
    fun `範囲外の数量は 400 の ProblemDetail で、ユースケースは呼ばれない`() {
        val response = controller.add(AddItemRequest("SKU-1", Quantity.MAX + 1))

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        (response.body as ProblemDetail).status shouldBe 400
        received shouldBe emptyList()
    }
}
