package com.example.todo.adapter.web

import com.example.todo.application.TaskService
import com.example.todo.support.InMemoryRepositories
import com.example.todo.support.SteppingClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity

/**
 * Spring コンテナなしで、境界の変換（DTO → ドメイン型 → HTTP ステータスと本文）を層1で確かめる。技術的な検証である。
 * HTTP と JSON の形、DB を通した保存は Integration（`TaskApiHttpTest` など）が AC に紐づけて確かめるため、
 * ここは層2の対応表を変えないように、テスト名に AC 番号を付けない。層1の他のテストと同じく Kotest（StringSpec）で書く。
 * 成功のステータスは 2xx であることだけを確かめる。AC が定めるのは 2xx で、登録の 201 と操作の 200 はこの実装の選択である。
 */
class TaskControllerTest : StringSpec({
    class Fixture {
        val repositories = InMemoryRepositories()
        val controller = TaskController(TaskService(repositories, repositories, SteppingClock()))
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

    "タイトルの不備は 400 で、タイトルの理由コードだけが付き、保存されない" {
        val f = Fixture()
        val cases = mapOf(
            RegisterTaskRequest(null, null) to "TITLE_BLANK",
            RegisterTaskRequest("", null) to "TITLE_BLANK",
            RegisterTaskRequest(" 　", null) to "TITLE_BLANK",
            RegisterTaskRequest("あ".repeat(51), null) to "TITLE_TOO_LONG",
        )

        cases.forEach { (request, code) ->
            val response = f.controller.register(request)

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            (response.body as ProblemDetail).status shouldBe 400
            codesOf(response) shouldBe setOf(code)
        }
        f.repositories.findAll() shouldBe emptyList()
    }

    "期限の不備は 400 で、DUE_DATE_INVALID だけが付き、保存されない" {
        val f = Fixture()

        listOf("2026-11-30T10:00:00", "2026/11/30", "2026-02-30").forEach { due ->
            val response = f.controller.register(RegisterTaskRequest("牛乳を買う", due))

            response.statusCode shouldBe HttpStatus.BAD_REQUEST
            codesOf(response) shouldBe setOf("DUE_DATE_INVALID")
        }
        f.repositories.findAll() shouldBe emptyList()
    }

    "タイトルと期限が同時に不正なら、codes に両方が付く" {
        val f = Fixture()

        val response = f.controller.register(RegisterTaskRequest("", "2026-02-30"))

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        codesOf(response) shouldBe setOf("TITLE_BLANK", "DUE_DATE_INVALID")
    }

    "完了にすると成功(2xx)で完了のタスクが返り、再実行しても同じ" {
        val f = Fixture()
        f.controller.register(RegisterTaskRequest("牛乳を買う", "2026-11-30"))

        repeat(2) {
            val response = f.controller.complete(1)

            response.statusCode.is2xxSuccessful shouldBe true
            taskOf(response) shouldBe TaskResponse(1, "牛乳を買う", "2026-11-30", "DONE", "DONE")
        }
    }

    "未完了に戻すと成功(2xx)で未完了のタスクが返り、再実行しても同じ" {
        val f = Fixture()
        f.controller.register(RegisterTaskRequest("牛乳を買う", null))
        f.controller.complete(1)

        repeat(2) {
            val response = f.controller.reopen(1)

            response.statusCode.is2xxSuccessful shouldBe true
            taskOf(response) shouldBe TaskResponse(1, "牛乳を買う", null, "OPEN", "OPEN")
        }
    }

    "存在しないタスクの完了と未完了に戻す操作は 404 で、codes は TASK_NOT_FOUND だけ" {
        val f = Fixture()

        listOf(f.controller.complete(99), f.controller.reopen(99)).forEach { response ->
            response.statusCode shouldBe HttpStatus.NOT_FOUND
            (response.body as ProblemDetail).status shouldBe 404
            codesOf(response) shouldBe setOf("TASK_NOT_FOUND")
        }
    }

    "一覧は、保存された選択に従って絞り込まれ、期限が近い順に並ぶ" {
        val f = Fixture()
        f.controller.register(RegisterTaskRequest("late", "2026-12-01"))
        f.controller.register(RegisterTaskRequest("early", "2026-11-10"))
        f.controller.register(RegisterTaskRequest("done", null))
        f.controller.complete(3)

        f.controller.list().map { it.title } shouldBe listOf("early", "late", "done")

        f.controller.changeCompletedVisibility(VisibilityBody(VisibilityValue.HIDE))

        f.controller.list().map { it.title } shouldBe listOf("early", "late")
    }

    "完了済みの表示の選択は、保存されていなければ SHOW で、保存すると読み戻せ、応答はその選択を返す" {
        val f = Fixture()
        f.controller.completedVisibility() shouldBe VisibilityBody(VisibilityValue.SHOW)

        f.controller.changeCompletedVisibility(VisibilityBody(VisibilityValue.HIDE)) shouldBe VisibilityBody(VisibilityValue.HIDE)
        f.controller.completedVisibility() shouldBe VisibilityBody(VisibilityValue.HIDE)

        f.controller.changeCompletedVisibility(VisibilityBody(VisibilityValue.SHOW)) shouldBe VisibilityBody(VisibilityValue.SHOW)
        f.controller.completedVisibility() shouldBe VisibilityBody(VisibilityValue.SHOW)
    }
})
