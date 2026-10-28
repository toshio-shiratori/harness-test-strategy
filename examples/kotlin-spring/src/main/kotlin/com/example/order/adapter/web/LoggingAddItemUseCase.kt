package com.example.order.adapter.web

import com.example.order.domain.Quantity
import com.example.order.domain.Sku
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** サンプル用の実装。実処理は持たずログのみ。 */
@Service
class LoggingAddItemUseCase : AddItemUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(sku: Sku, quantity: Quantity) {
        log.info("add item sku={} quantity={}", sku.value, quantity.value)
    }
}
