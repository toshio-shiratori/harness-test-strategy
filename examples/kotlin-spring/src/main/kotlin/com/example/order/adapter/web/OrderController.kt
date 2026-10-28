package com.example.order.adapter.web

import com.example.order.domain.Quantity
import com.example.order.domain.Sku
import com.example.order.domain.Validated
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 外部入力の形そのもの。ドメインの意味は持たず、アダプタ層から外へ出さない。
 * 欠落・null は Jackson に任せず、null として受けてコントローラーで MISSING_FIELD にする。
 */
data class AddItemRequest(val sku: String?, val quantity: Int?)

@RestController
@RequestMapping("/orders")
class OrderController(private val addItem: AddItemUseCase) {

    @PostMapping("/items")
    fun add(@RequestBody request: AddItemRequest): ResponseEntity<*> {
        // 必須フィールドの欠落・null は単独で返す。欠けたフィールド名は返さない（TRS ORD-001 OI-003, OI-006）
        if (request.sku == null || request.quantity == null) {
            val missing = problem(listOf(ErrorCode.MISSING_FIELD), "必須フィールドが指定されていません")
            return ResponseEntity.badRequest().body(missing)
        }
        val sku = Sku.of(request.sku)
        val quantity = Quantity.of(request.quantity)
        // 検証段階の失敗は、同時に成立していれば全て返す（TRS ORD-001 OI-005 c）
        return if (sku is Validated.Valid && quantity is Validated.Valid) {
            addItem.execute(sku.value, quantity.value)
            ResponseEntity.noContent().build<Unit>()
        } else {
            val codes = listOfNotNull(
                ErrorCode.BLANK.takeIf { sku is Validated.Invalid },
                ErrorCode.OUT_OF_RANGE.takeIf { quantity is Validated.Invalid },
            )
            val detail = "sku は空にできず、quantity は ${Quantity.MIN}〜${Quantity.MAX} の範囲で指定してください"
            ResponseEntity.badRequest().body(problem(codes, detail))
        }
    }
}

internal fun problem(codes: List<ErrorCode>, detail: String): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail).also {
        it.setProperty(CODES_PROPERTY, codes.map(ErrorCode::name))
    }

fun interface AddItemUseCase {
    fun execute(sku: Sku, quantity: Quantity)
}
