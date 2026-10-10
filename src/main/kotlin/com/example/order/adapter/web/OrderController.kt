package com.example.order.adapter.web

import com.example.order.domain.Quantity
import com.example.order.domain.Validated
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 外部入力の形そのもの。ドメインの意味は持たず、アダプタ層から外へ出さない。 */
data class AddItemRequest(val sku: String, val quantity: Int)

@RestController
@RequestMapping("/orders")
class OrderController(private val addItem: AddItemUseCase) {

    @PostMapping("/items")
    fun add(@RequestBody request: AddItemRequest): ResponseEntity<*> =
        when (val quantity = Quantity.of(request.quantity)) {
            is Validated.Valid -> {
                addItem.execute(request.sku, quantity.value)
                ResponseEntity.noContent().build<Unit>()
            }
            is Validated.Invalid -> ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "quantity は ${Quantity.MIN}〜${Quantity.MAX} の範囲で指定してください (${quantity.error})",
                ),
            )
        }
}

fun interface AddItemUseCase {
    fun execute(sku: String, quantity: Quantity)
}
