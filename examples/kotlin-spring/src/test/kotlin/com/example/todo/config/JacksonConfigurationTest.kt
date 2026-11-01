package com.example.todo.config

import com.example.todo.adapter.web.RegisterTaskRequest
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * 型の自動変換を止める設定を、Spring なしの JsonMapper で確かめる層1の技術的な検証。
 * HTTP を通した確認（AC-01-29）は Integration の `TaskApiHttpTest` が受け持つため、テスト名に AC 番号を付けない。
 */
class JacksonConfigurationTest : StringSpec({
    // bean として登録される customizer を通して設定する（Spring は起動しない）
    val customizer = JacksonConfiguration().strictScalarCoercion()
    val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).also(customizer::customize).build()

    fun read(json: String) = mapper.readValue(json, RegisterTaskRequest::class.java)

    "文字列の項目に、整数・小数・真偽値が来たら失敗する" {
        listOf("""{"title":123}""", """{"title":1.5}""", """{"title":true}""", """{"title":"a","dueDate":20261130}""").forEach {
            shouldThrowAny { read(it) }
        }
    }

    "文字列と null は、そのまま読める" {
        read("""{"title":"牛乳を買う","dueDate":"2026-11-30"}""") shouldBe RegisterTaskRequest("牛乳を買う", "2026-11-30")
        read("""{"title":null}""") shouldBe RegisterTaskRequest(null, null)
    }
})
