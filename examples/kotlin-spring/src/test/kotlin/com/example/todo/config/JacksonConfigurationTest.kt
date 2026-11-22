package com.example.todo.config

import com.example.todo.adapter.web.RegisterTaskRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * 型の自動変換を止める設定を、Spring なしの JsonMapper で確かめる層1の検証。
 * AC-01-29 は、型や形が定義と違う本文の入力を、層1でまとめて確かめる。Integration の `TaskApiHttpTest` は、代表 1 つを HTTP で確かめる。
 * この JsonMapper は `application.properties` の `spring.jackson.*` を読まない。その設定（スカラーの変換の禁止、定義にない項目の無視）を含む配線は、層2が確かめる。
 * AC 番号を持たないテストは、AC に結び付かない技術的な検証である。
 */
class JacksonConfigurationTest : StringSpec({
    // bean として登録される customizer を通して設定する（Spring は起動しない）
    val customizer = JacksonConfiguration().strictScalarCoercion()
    val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).also(customizer::customize).build()

    fun read(json: String) = mapper.readValue(json, RegisterTaskRequest::class.java)

    "文字列の項目に、小数が来たら失敗する" {
        shouldThrowAny { read("""{"title":1.5}""") }
    }

    "AC-01-29 型や形が定義と違う本文は、読み取りが JacksonException で失敗する（Spring はこれを、読み取れない本文として 400 と MALFORMED_REQUEST にする）" {
        val bodies = listOf(
            """{"title":123}""",
            """{"title":true}""",
            """{"title":["a"]}""",
            """{"title":{"a":1}}""",
            """{"title":"牛乳を買う","dueDate":20261130}""",
            """{"title":"牛乳を買う","dueDate":true}""",
            """{"title":"牛乳を買う","dueDate":{"a":1}}""",
        )
        bodies.forEach { body ->
            shouldThrow<JacksonException> { read(body) }
        }
    }

    "文字列と null は、そのまま読める" {
        val withDue = read("""{"title":"牛乳を買う","dueDate":"2026-11-30"}""")
        withDue.title shouldBe "牛乳を買う"
        withDue.dueDate shouldBe "2026-11-30"
        val nullTitle = read("""{"title":null}""")
        nullTitle.title shouldBe null
        nullTitle.dueDate shouldBe null
    }
})
