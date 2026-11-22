package com.example.todo.adapter.web

import com.example.todo.application.TaskService
import com.example.todo.config.JacksonConfiguration
import com.example.todo.support.InMemoryRepositories
import com.example.todo.support.SteppingClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration

/**
 * Spring コンテナなしで、境界の変換（DTO → ドメイン型 → HTTP ステータスと本文）を層1で確かめる。
 * 値の妥当性（タイトル・期限・識別子の形）と、区分・並び・絞り込みを、DB なしで確かめる AC は、テスト名の先頭に AC 番号を付ける。
 * これらの網羅を Integration でしない理由は、実現例 02-2 の 1.2.1 にある。
 * 先頭に AC 番号が無いテストは、AC に結び付かない技術的な検証である。成功のステータスは 2xx であることだけを確かめる。
 * AC が定めるのは 2xx で、登録の 201 と操作の 200 はこの実装の選択である。層1の他のテストと同じく Kotest（StringSpec）で書く。
 * HTTP と JSON の形、DB を通した保存は Integration（`TaskApiHttpTest` など）が確かめる。
 */
class TaskControllerTest : StringSpec({
    class Fixture {
        val repositories = InMemoryRepositories()
        val clock = SteppingClock()
        val controller = TaskController(TaskService(repositories, repositories, clock))
    }

    // 期限が 2026-10-31 のタスクは、固定の時計（2026-11-01 10:00 JST）では期限を過ぎている
    val pastDue = "2026-10-31"
    val registerMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).also(JacksonConfiguration().strictScalarCoercion()::customize).build()

    fun Fixture.registerOpen(title: String, due: String? = null) = controller.register(RegisterTaskRequest(title, due))

    fun Fixture.registerDone(title: String, due: String? = null) {
        registerOpen(title, due)
        controller.complete(repositories.findAll().maxOf { it.id.value }.toString())
    }

    fun codesOf(response: ResponseEntity<*>) =
        ((response.body as ProblemDetail).properties?.get(CODES_PROPERTY) as List<*>).toSet()

    fun taskOf(response: ResponseEntity<*>) = response.body as TaskResponse

    "有効なタイトルと期限で登録すると成功(2xx)で、未完了のタスクが返り、保存される" {
        val f = Fixture()

        val response = f.controller.register(RegisterTaskRequest("牛乳を買う", "2026-11-30"))

        response.statusCode.is2xxSuccessful shouldBe true
        taskOf(response) shouldBe TaskResponse(1, "牛乳を買う", "2026-11-30", "OPEN", "OPEN")
        // JSON になる各項目を読む（Jackson が呼ぶアクセサは、Kotlin からは呼ばれず、読まないと層1の検査に入らない）
        val body = taskOf(response)
        listOf(body.id.toString(), body.title, body.dueDate, body.status, body.category) shouldBe
            listOf("1", "牛乳を買う", "2026-11-30", "OPEN", "OPEN")
        f.repositories.findAll().size shouldBe 1
    }

    "期限が無い登録では、応答の期限も無い。前後の空白は除かれる" {
        val f = Fixture()

        val response = f.controller.register(RegisterTaskRequest("  牛乳を買う ", null))

        taskOf(response) shouldBe TaskResponse(1, "牛乳を買う", null, "OPEN", "OPEN")
    }

    "期限を過ぎた未完了のタスクの区分は OVERDUE で、年が 4 桁未満の期限も YYYY-MM-DD で返る" {
        val f = Fixture()

        taskOf(f.controller.register(RegisterTaskRequest("a", "2026-10-31"))).category shouldBe "OVERDUE"
        taskOf(f.controller.register(RegisterTaskRequest("b", "0001-01-01"))).dueDate shouldBe "0001-01-01"
    }

    "AC-01-16 不正なタイトルでは登録されない" {
        val f = Fixture()

        listOf("", " ", "\u200B", "あ".repeat(51)).forEach { title ->
            f.registerOpen(title).statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        f.repositories.findAll() shouldBe emptyList()
    }

    "AC-01-17 不正なタイトルは、400 と理由コードで拒否される" {
        val f = Fixture()
        // 期待値は制約表 5.2 の C-51（TITLE_TOO_LONG）・C-52（TITLE_BLANK）・C-65（TITLE_BLANK）から取った
        val cases = listOf(
            "" to "TITLE_BLANK",
            " " to "TITLE_BLANK",
            " 　\t\n" to "TITLE_BLANK",
            "\u200B" to "TITLE_BLANK",
            "あ".repeat(51) to "TITLE_TOO_LONG",
            " " + "あ".repeat(51) + " " to "TITLE_TOO_LONG",
        )

        cases.forEach { (title, code) ->
            val response = f.registerOpen(title)

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            (response.body as ProblemDetail).status shouldBe 400
            codesOf(response) shouldBe setOf(code)
        }
    }

    "AC-01-19 タイトルの項目が無い本文は、読み取って登録すると、400 と TITLE_BLANK で拒否される" {
        val f = Fixture()
        val request = registerMapper.readValue("{}", RegisterTaskRequest::class.java)

        val response = f.controller.register(request)

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        (response.body as ProblemDetail).status shouldBe 400
        codesOf(response) shouldBe setOf("TITLE_BLANK")
        f.repositories.findAll() shouldBe emptyList()
    }

    "AC-01-27 日付として不正な期限は、400 と DUE_DATE_INVALID で拒否され、登録されない" {
        val f = Fixture()

        listOf("2026-11-30T10:00:00", "2026/11/30", "2026-02-30").forEach { due ->
            val response = f.registerOpen("牛乳を買う", due)

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            (response.body as ProblemDetail).status shouldBe 400
            codesOf(response) shouldBe setOf("DUE_DATE_INVALID")
        }
        f.repositories.findAll() shouldBe emptyList()
    }

    "AC-01-28 タイトルと期限が同時に不正な要求は、codes が両方の理由コードの集合に等しい形で拒否され、登録されない" {
        val f = Fixture()

        val response = f.registerOpen("", "2026-02-30")

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        codesOf(response) shouldBe setOf("TITLE_BLANK", "DUE_DATE_INVALID")
        f.repositories.findAll() shouldBe emptyList()
    }

    "AC-01-34 期限が空文字列の要求は、400 と DUE_DATE_INVALID で拒否され、登録されない" {
        val f = Fixture()

        val response = f.registerOpen("牛乳を買う", "")

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        (response.body as ProblemDetail).status shouldBe 400
        codesOf(response) shouldBe setOf("DUE_DATE_INVALID")
        f.repositories.findAll() shouldBe emptyList()
    }

    "AC-01-37 0000 年の期限の要求は、400 と DUE_DATE_INVALID で拒否され、登録されない" {
        val f = Fixture()

        listOf("0000-01-01", "0000-02-29", "0000-12-31").forEach { due ->
            val response = f.registerOpen("牛乳を買う", due)

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            (response.body as ProblemDetail).status shouldBe 400
            codesOf(response) shouldBe setOf("DUE_DATE_INVALID")
        }
        f.repositories.findAll() shouldBe emptyList()
    }

    "AC-04-06 登録したタスクが 1 件も無いとき、一覧は空である" {
        Fixture().controller.list() shouldBe emptyList()
    }

    "AC-04-03 登録したタスクが、区分つきで一覧に現れる" {
        val f = Fixture()
        f.registerOpen("A")
        f.registerDone("B")
        f.registerOpen("C", pastDue)
        f.registerDone("D", pastDue)

        val tasks = f.controller.list()

        tasks.size shouldBe 4
        tasks.associate { it.title to it.category } shouldBe mapOf("A" to "OPEN", "B" to "DONE", "C" to "OVERDUE", "D" to "DONE")
    }

    "完了にすると成功(2xx)で完了のタスクが返り、再実行しても同じ" {
        val f = Fixture()
        f.controller.register(RegisterTaskRequest("牛乳を買う", "2026-11-30"))

        repeat(2) {
            val response = f.controller.complete("1")

            response.statusCode.is2xxSuccessful shouldBe true
            taskOf(response) shouldBe TaskResponse(1, "牛乳を買う", "2026-11-30", "DONE", "DONE")
        }
    }

    "未完了に戻すと成功(2xx)で未完了のタスクが返り、再実行しても同じ" {
        val f = Fixture()
        f.controller.register(RegisterTaskRequest("牛乳を買う", null))
        f.controller.complete("1")

        repeat(2) {
            val response = f.controller.reopen("1")

            response.statusCode.is2xxSuccessful shouldBe true
            taskOf(response) shouldBe TaskResponse(1, "牛乳を買う", null, "OPEN", "OPEN")
        }
    }

    "存在しないタスクの完了と未完了に戻す操作は 404 で、codes は TASK_NOT_FOUND だけ" {
        val f = Fixture()

        listOf(f.controller.complete("99"), f.controller.reopen("99")).forEach { response ->
            response.statusCode shouldBe HttpStatus.NOT_FOUND
            (response.body as ProblemDetail).status shouldBe 404
            codesOf(response) shouldBe setOf("TASK_NOT_FOUND")
        }
    }

    // 識別子の上限と 0 の扱い（基本設計書の Q-06）は未決なので、AC の名前のテストには、その値を置かない。境界の値は、AC 番号の無い `parseTaskId` のテストが確かめる
    val invalidIds = listOf("abc", "1.5", "+1", "-1", " 1", "1 ", "１", "0x1", "1a", "01", "007", "00")

    "AC-02-09 形が不正な識別子でタスクを完了にする要求は、404 と TASK_NOT_FOUND で拒否され、タスクは変わらない" {
        val f = Fixture()
        f.registerOpen("牛乳を買う", "2026-11-30")

        invalidIds.forEach { id ->
            val response = f.controller.complete(id)

            response.statusCode shouldBe HttpStatus.NOT_FOUND
            (response.body as ProblemDetail).status shouldBe 404
            codesOf(response) shouldBe setOf("TASK_NOT_FOUND")
        }
        f.controller.list().single() shouldBe TaskResponse(1, "牛乳を買う", "2026-11-30", "OPEN", "OPEN")
    }

    "AC-03-08 形が不正な識別子でタスクを未完了に戻す要求は、404 と TASK_NOT_FOUND で拒否され、タスクは変わらない" {
        val f = Fixture()
        f.registerDone("牛乳を買う", "2026-11-30")

        invalidIds.forEach { id ->
            val response = f.controller.reopen(id)

            response.statusCode shouldBe HttpStatus.NOT_FOUND
            (response.body as ProblemDetail).status shouldBe 404
            codesOf(response) shouldBe setOf("TASK_NOT_FOUND")
        }
        f.controller.list().single() shouldBe TaskResponse(1, "牛乳を買う", "2026-11-30", "DONE", "DONE")
    }

    "AC-02-11 期限を過ぎた未完了のタスクを完了にする要求は成功し、完了にした状態は保存され、読み戻した区分は完了である" {
        val f = Fixture()
        f.registerOpen("牛乳を買う", pastDue)
        f.controller.list().single().category shouldBe "OVERDUE" // Given: 期限を過ぎた未完了

        val response = f.controller.complete("1")

        response.statusCode.is2xxSuccessful shouldBe true
        f.controller.list().single() shouldBe TaskResponse(1, "牛乳を買う", pastDue, "DONE", "DONE")
    }

    "AC-03-11 期限を過ぎた完了のタスクを未完了に戻す要求は成功し、未完了に戻した状態は保存され、読み戻した区分は期限切れである" {
        val f = Fixture()
        f.registerDone("牛乳を買う", pastDue)
        f.controller.list().single().category shouldBe "DONE" // Given: 期限を過ぎた完了

        val response = f.controller.reopen("1")

        response.statusCode.is2xxSuccessful shouldBe true
        f.controller.list().single() shouldBe TaskResponse(1, "牛乳を買う", pastDue, "OPEN", "OVERDUE")
    }

    "識別子は先頭にゼロを置かない ASCII の 10 進数字の並びで、Long に収まる値だけが TaskId になる" {
        parseTaskId("1")?.value shouldBe 1L
        parseTaskId("0")?.value shouldBe 0L
        parseTaskId("10")?.value shouldBe 10L
        parseTaskId("007") shouldBe null
        parseTaskId("01") shouldBe null
        parseTaskId("9223372036854775807")?.value shouldBe Long.MAX_VALUE
        parseTaskId("9223372036854775808") shouldBe null
        parseTaskId("") shouldBe null
    }

    "AC-05-06 保存したタスクの一覧も、期限が近い順と登録の古い順に並び、完了済みは未完了の後ろにまとまる" {
        val f = Fixture()

        // 登録のたびに時計を 1 秒進める（登録時刻が異なる）
        fun step() = f.clock.advance(Duration.ofSeconds(1))
        f.registerOpen("P", "2026-11-10")
        step()
        f.registerOpen("Q")
        step()
        f.registerOpen("R", "2026-11-03")
        step()
        f.registerOpen("S", "2026-11-10")
        step()
        f.registerOpen("T")
        step()
        f.registerDone("U", "2026-11-05")
        step()
        f.registerDone("V")

        f.controller.list().map { it.title } shouldBe listOf("R", "P", "S", "Q", "T", "U", "V")
    }

    "AC-05-08 時計が固定された状態で続けて登録したタスクは、登録した順に並ぶ" {
        val f = Fixture()

        listOf("P", "Q", "R").forEach { f.registerOpen(it) }

        f.controller.list().map { it.title } shouldBe listOf("P", "Q", "R")
    }

    "AC-06-06 完了済みを隠す選択のとき、取得した一覧に完了済みは含まれない" {
        val f = Fixture()
        f.controller.changeCompletedVisibility(VisibilityBody(VisibilityValue.HIDE))
        f.registerDone("done")
        f.registerOpen("open")

        f.controller.list().map { it.title } shouldBe listOf("open")
    }

    "一覧は、保存された選択に従って絞り込まれ、期限が近い順に並ぶ" {
        val f = Fixture()
        f.controller.register(RegisterTaskRequest("late", "2026-12-01"))
        f.controller.register(RegisterTaskRequest("early", "2026-11-10"))
        f.controller.register(RegisterTaskRequest("done", null))
        f.controller.complete("3")

        f.controller.list().map { it.title } shouldBe listOf("early", "late", "done")

        f.controller.changeCompletedVisibility(VisibilityBody(VisibilityValue.HIDE))

        f.controller.list().map { it.title } shouldBe listOf("early", "late")
    }

    "完了済みの表示の選択は、保存されていなければ SHOW で、保存すると読み戻せ、応答はその選択を返す" {
        val f = Fixture()
        f.controller.completedVisibility().visibility shouldBe VisibilityValue.SHOW

        f.controller.changeCompletedVisibility(VisibilityBody(VisibilityValue.HIDE)).visibility shouldBe VisibilityValue.HIDE
        f.controller.completedVisibility().visibility shouldBe VisibilityValue.HIDE

        f.controller.changeCompletedVisibility(VisibilityBody(VisibilityValue.SHOW)).visibility shouldBe VisibilityValue.SHOW
        f.controller.completedVisibility().visibility shouldBe VisibilityValue.SHOW
    }
})
