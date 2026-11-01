package com.example.todo.adapter.web

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * 登録（UC-01）の HTTP と DB の結合テスト（層2 Integration）。
 * Controller → Service → MyBatis → MySQL（Testcontainers）まで通し、保存は HTTP で一覧を取り直して確かめる。
 * 期待値は TRS TODO-001 の AC と OI の決定を正とし、実装の挙動から逆生成していない。
 * タイトルの検証そのものの網羅は層1（`TaskTitleTest`）が受け持つ。ここでは、拒否が 400 と理由コードで返り、何も保存されないことを見る。
 */
class TaskApiHttpTest : TaskApiTestBase() {
    private val milk = "牛乳を買う"

    // 結合文字つきの「é」を 50 個。書記素で 50 字、コードポイントで 100
    private val combining50 = "é".repeat(50)

    // 国旗（地域指示子 2 個）を 50 個
    private val flags50 = "🇯🇵".repeat(50)

    @Test
    fun `AC-01-13 同じタイトルのタスクを複数登録でき、別々のタスクとして一覧に出る`() {
        val first = register(milk)

        val second = register(milk)

        second.id shouldNotBe first.id
        val tasks = list()
        tasks shouldHaveSize 2
        tasks.map { it.title } shouldBe listOf(milk, milk)
        tasks.map { it.id }.toSet() shouldBe setOf(first.id, second.id)
    }

    @Test
    fun `AC-01-15 登録したタスクは保存され、読み戻しても同じである`() {
        // 期待値は TRS の Examples の表から転記した（title, saved, due）。saved が null のものは入力と同じ
        val cases = listOf(
            Triple(milk, milk, "2026-11-30"),
            Triple(milk, milk, "2026-10-31"),
            Triple(milk, milk, "0001-01-01"),
            Triple(milk, milk, "9999-12-31"),
            Triple(milk, milk, null),
            Triple(" $milk ", milk, null),
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
    fun `AC-01-16 不正なタイトルでは登録されない`() {
        listOf("", " ", "あ".repeat(51)).forEach { title ->
            postRegister(registerBody(title, null)).expectStatus().isBadRequest

            list().shouldBeEmpty()
        }
    }

    @Test
    fun `AC-01-17 不正なタイトルは、400 と理由コードで拒否される`() {
        val cases = listOf(
            "" to "TITLE_BLANK",
            " " to "TITLE_BLANK",
            "あ".repeat(51) to "TITLE_TOO_LONG",
            " " + "あ".repeat(51) + " " to "TITLE_TOO_LONG",
        )
        cases.forEach { (title, code) ->
            postRegister(registerBody(title, null)).expectProblem(400, code)
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
        listOf("""{"title":""", "{}").forEach { body ->
            postRegister(body).expectStatus().isBadRequest

            list().shouldBeEmpty()
        }
    }

    @Test
    fun `AC-01-27 期限が日付として不正な要求は、400 と DUE_DATE_INVALID で拒否され、登録されない`() {
        listOf("2026-11-30T10:00:00", "2026/11/30", "2026-02-30").forEach { due ->
            postRegister(registerBody(milk, due)).expectProblem(400, "DUE_DATE_INVALID")

            list().shouldBeEmpty()
        }
    }

    @Test
    fun `AC-01-28 タイトルと期限が同時に不正な要求は、codes に両方の理由コードを含めて拒否される`() {
        postRegister(registerBody("", "2026-02-30")).expectProblem(400, "TITLE_BLANK", "DUE_DATE_INVALID")

        list().shouldBeEmpty()
    }

    @Test
    fun `AC-01-29 型や形が定義と違う本文は、400 と MALFORMED_REQUEST で拒否され、登録されない`() {
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
            postRegister(body).expectProblem(400, "MALFORMED_REQUEST")

            list().shouldBeEmpty()
        }
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
}
