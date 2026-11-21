package com.example.todo.adapter.web

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 登録（UC-01）の HTTP と DB の結合テスト（層2 Integration）。
 * Controller → Service → MyBatis → MySQL（Testcontainers）まで通し、保存は HTTP で一覧を取り直して確かめる。
 * 期待値は TRS TODO-001 の AC と制約表、OI の決定から取る。AC と制約表は、要件定義書・基本設計書・OI の決定から生成したもので、実装の挙動から逆生成していない（03 §3.1）。
 * タイトル・期限の値の妥当性の網羅は、層1（`TaskControllerTest`・`TaskTitleTest`・`DueDateTest`）が受け持つ。
 * ここは、拒否に至る水準ごとの代表と、保存して読み戻した結果を確かめる。入力の置き方と水準の対応は、実現例 02-2 の 1.2.1 にある。
 * 「何も登録されない」は、HTTP の一覧ではなく DB の行数で確かめる（一覧の結果を増やさないため）。
 */
class TaskApiHttpTest : TaskApiTestBase() {
    private val milk = "牛乳を買う"

    // 結合文字つきの「é」を 50 個。書記素で 50 字、コードポイントで 100
    private val combining50 = "e\u0301".repeat(50)

    // 国旗（地域指示子 2 個）を 50 個
    private val flags50 = "🇯🇵".repeat(50)

    @Test
    fun `AC-01-13 同じタイトルのタスクを複数登録でき、別々のタスクとして一覧に出る`() {
        register(milk)
        register(milk)

        val tasks = list()
        tasks shouldHaveSize 2
        tasks.all { it.title == milk } shouldBe true
        tasks.map { it.id }.toSet() shouldHaveSize 2
    }

    @Test
    fun `AC-01-15 登録したタスクは保存され、読み戻しても同じである`() {
        // 期待値は、AC-01-15 が根拠にする制約表 5.2 の C-50・C-54・C-58・C-66 から取った（title, saved, due）。saved が null のものは入力と同じ
        val cases = listOf(
            Triple(milk, milk, "2026-11-30"),
            Triple(milk, milk, "2026-10-31"),
            Triple(milk, milk, "0001-01-01"),
            Triple(milk, milk, "9999-12-31"),
            Triple(milk, milk, null),
            Triple(" $milk ", milk, null),
            Triple("\u200B $milk\u200B", milk, null),
            Triple(combining50, combining50, null),
            Triple(flags50, flags50, null),
        )
        cases.forEach { (title, saved, due) ->
            resetState()

            postRegister(registerBody(title, due)).expectStatus().is2xxSuccessful

            val tasks = list()
            tasks shouldHaveSize 1
            tasks.single().title shouldBe saved
            tasks.single().dueDate shouldBe due
            tasks.single().status shouldBe "OPEN"
        }
    }

    @Test
    fun `AC-01-05 空白だけ（半角・全角・タブ・改行以外の White_Space 属性を持つ文字）のタイトルは、400 と TITLE_BLANK で拒否され、登録されない`() {
        // F1-L7。期待値は制約表 5.2 の C-53（400 と TITLE_BLANK、何も保存しない）から取った。U+00A0（NBSP）は White_Space 属性を持ち、半角・全角の空白・タブ・改行ではない
        postRegister(registerBody("\u00A0", null)).expectProblem(400, "TITLE_BLANK")

        storedTaskCount() shouldBe 0
    }

    @Test
    fun `AC-01-17 不正なタイトルは、400 と理由コードで拒否され、登録されない`() {
        // 水準ごとに 1 つ。期待値は制約表 5.2 の C-51（F1-L4 は TITLE_TOO_LONG）・C-52（F1-L5・F1-L6 は TITLE_BLANK）・C-65（F1-L14 は TITLE_BLANK）から取った
        // F1-L14 の U+200B（ゼロ幅スペース）は表示されない文字（Default_Ignorable_Code_Point）で、White_Space 属性を持たない
        val cases = listOf(
            "あ".repeat(51) to "TITLE_TOO_LONG", // F1-L4
            "" to "TITLE_BLANK", // F1-L5
            " " to "TITLE_BLANK", // F1-L6
            "\u200B" to "TITLE_BLANK", // F1-L14
        )
        cases.forEach { (title, code) ->
            postRegister(registerBody(title, null)).expectProblem(400, code)

            storedTaskCount() shouldBe 0
        }
    }

    @Test
    fun `AC-01-18 本文が JSON として読めない要求は、400 と MALFORMED_REQUEST で拒否される`() {
        postRegister("""{"title":""").expectProblem(400, "MALFORMED_REQUEST")
    }

    @Test
    fun `AC-01-19 タイトルが無い要求は、400 と TITLE_BLANK で拒否される`() {
        postRegister("{}").expectProblem(400, "TITLE_BLANK")
    }

    @Test
    fun `AC-01-24 本文が壊れている、またはタイトルが無い要求では登録されない`() {
        listOf("""{"title":""" to "MALFORMED_REQUEST", "{}" to "TITLE_BLANK").forEach { (body, code) ->
            postRegister(body).expectProblem(400, code)

            storedTaskCount() shouldBe 0
        }
    }

    @Test
    fun `AC-01-27 期限が日付として不正な要求は、400 と DUE_DATE_INVALID で拒否され、登録されない`() {
        // 水準ごとに 1 つ。F2-L5（別形式）と F2-L6（実在しない日）
        listOf("2026/11/30", "2026-02-30").forEach { due ->
            postRegister(registerBody(milk, due)).expectProblem(400, "DUE_DATE_INVALID")

            storedTaskCount() shouldBe 0
        }
    }

    @Test
    fun `AC-01-34 期限が空文字列の要求は、400 と DUE_DATE_INVALID で拒否され、登録されない`() {
        postRegister(registerBody(milk, "")).expectProblem(400, "DUE_DATE_INVALID") // F2-L8

        storedTaskCount() shouldBe 0
    }

    @Test
    fun `AC-01-37 0000 年の期限の要求は、400 と DUE_DATE_INVALID で拒否され、登録されない`() {
        postRegister(registerBody(milk, "0000-02-29")).expectProblem(400, "DUE_DATE_INVALID") // F2-L9

        storedTaskCount() shouldBe 0
    }

    @Test
    fun `AC-01-29 型や形が定義と違う本文は、400 と MALFORMED_REQUEST で拒否され、登録されない`() {
        // 代表は 1 つ（タイトルが数値）。ほかの型や形の入力は、層1の `JacksonConfigurationTest` が確かめる
        postRegister("""{"title":123}""").expectProblem(400, "MALFORMED_REQUEST")

        storedTaskCount() shouldBe 0
    }

    @Test
    fun `AC-01-30 定義にない項目は無視されて登録される`() {
        postRegister("""{"title":"牛乳を買う","dueDate":"2026-11-30","unknown":1,"extra":{"a":[1,2]}}""")
            .expectStatus().is2xxSuccessful

        val tasks = list()
        tasks shouldHaveSize 1
        tasks.single().title shouldBe milk
        tasks.single().dueDate shouldBe "2026-11-30"
    }

    @Test
    fun `AC-01-35 期限の項目を省く、または null の要求は、期限なしで登録される`() {
        listOf("""{"title":"$milk"}""", """{"title":"$milk","dueDate":null}""").forEach { body ->
            resetState()

            postRegister(body).expectStatus().is2xxSuccessful

            val tasks = list()
            tasks shouldHaveSize 1
            tasks.single().title shouldBe milk
            tasks.single().dueDate shouldBe null
        }
    }
}
