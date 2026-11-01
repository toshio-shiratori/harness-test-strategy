package com.example.todo.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.LogicalType

/**
 * JSON の型の自動変換を止める設定（TRS TODO-001 OI-013）。
 * 文字列の項目に数値・真偽値が来ても、"123" や "true" に変換せず、デシリアライズを失敗させる（本文が読めない扱いの 400）。
 * `application.properties` の `allow-coercion-of-scalars=false` だけでは、文字列への変換は止まらない（実測）。
 */
@Configuration
class JacksonConfiguration {
    @Bean
    fun strictScalarCoercion(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer(::rejectScalarToString)
}

/** 文字列の項目への、整数・小数・真偽値の変換を拒否する。 */
fun rejectScalarToString(builder: JsonMapper.Builder) {
    builder.withCoercionConfig(LogicalType.Textual) { config ->
        config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
    }
}
