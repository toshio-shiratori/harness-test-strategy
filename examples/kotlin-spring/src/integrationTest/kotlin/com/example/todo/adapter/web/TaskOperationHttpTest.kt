package com.example.todo.adapter.web

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 完了にする・未完了に戻す（UC-02・UC-03）の HTTP と DB の結合テスト（層2 Integration）。
 * 状態の変更は HTTP で操作し、保存された状態は HTTP で一覧を取り直して確かめる。
 * 層1（`TaskTest`・`TaskConstraintTableTest`）は、状態遷移の規則を DB なしで確かめている。
 * 期待値は TRS TODO-001 の AC と制約表、OI-006・OI-012 の決定から取る。AC と制約表は、要件定義書・基本設計書・OI の決定から生成したもので、実装から逆生成しない（03 §3.1）。
 */
class TaskOperationHttpTest : TaskApiTestBase() {
    private val milk = "牛乳を買う"
    private val due = "2026-11-30"

    // 固定した時計は 2026-11-01 10:00 JST。この期限は、その日付より前なので期限切れになる
    private val pastDue = "2026-10-31"

    // 登録された識別子は、採番表の AUTO_INCREMENT で増える小さい番号。この値は払い出されない
    private val unknownId = 9_999_999_999L

    private fun openTask(): TaskJson = register(milk, due)

    /** 形が不正な識別子。数字以外を含むもの、符号つき、小数、先頭がゼロ、表せる範囲を超える値。実在する識別子を崩した綴りも含む。 */
    private fun invalidIds(existing: TaskJson): List<String> =
        listOf("abc", "1.5", "+${existing.id}", "0x${existing.id}", "0${existing.id}", "99999999999999999999")

    private fun doneTask(): TaskJson {
        val task = register(milk, due)
        complete(task.id).expectStatus().is2xxSuccessful
        return task
    }

    @Test
    fun `AC-02-02 完了済みのタスクをもう一度完了にしても、何も変わらない`() {
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        val task = doneTask()

        complete(task.id).expectStatus().is2xxSuccessful

        val tasks = list()
        tasks shouldHaveSize 1
        tasks.single() shouldBe TaskJson(task.id, milk, due, "DONE", "DONE")
    }

    @Test
    fun `AC-02-03 存在しないタスクを完了にする要求は、404 と TASK_NOT_FOUND で拒否される`() {
        complete(unknownId).expectProblem(404, "TASK_NOT_FOUND")
    }

    @Test
    fun `AC-02-09 形が不正な識別子でタスクを完了にする要求は、404 と TASK_NOT_FOUND で拒否され、タスクは変わらない`() {
        val task = openTask()

        invalidIds(task).forEach { id ->
            operate(id, "complete").expectProblem(404, "TASK_NOT_FOUND")

            list().single() shouldBe TaskJson(task.id, milk, due, "OPEN", "OPEN")
        }
    }

    @Test
    fun `AC-02-04 完了にした状態は保存され、読み戻しても完了である`() {
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        val task = openTask()

        complete(task.id).expectStatus().is2xxSuccessful

        list().single { it.id == task.id }.status shouldBe "DONE"
    }

    @Test
    fun `AC-02-07 完了済みのタスクをもう一度完了にする要求は、成功する`() {
        val task = doneTask()

        complete(task.id).expectStatus().is2xxSuccessful
    }

    @Test
    fun `AC-02-11 期限を過ぎた未完了のタスクを完了にする要求は成功し、読み戻した区分は完了である`() {
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        val task = register(milk, pastDue)
        list().single { it.id == task.id }.category shouldBe "OVERDUE" // Given: 期限を過ぎた未完了

        complete(task.id).expectStatus().is2xxSuccessful

        list().single { it.id == task.id } shouldBe TaskJson(task.id, milk, pastDue, "DONE", "DONE")
    }

    @Test
    fun `AC-03-02 未完了のタスクを未完了に戻しても、何も変わらない`() {
        val task = openTask()

        reopen(task.id).expectStatus().is2xxSuccessful

        val tasks = list()
        tasks shouldHaveSize 1
        tasks.single() shouldBe TaskJson(task.id, milk, due, "OPEN", "OPEN")
    }

    @Test
    fun `AC-03-03 存在しないタスクを未完了に戻す要求は、404 と TASK_NOT_FOUND で拒否される`() {
        reopen(unknownId).expectProblem(404, "TASK_NOT_FOUND")
    }

    @Test
    fun `AC-03-08 形が不正な識別子でタスクを未完了に戻す要求は、404 と TASK_NOT_FOUND で拒否され、タスクは変わらない`() {
        val task = doneTask()

        invalidIds(task).forEach { id ->
            operate(id, "reopen").expectProblem(404, "TASK_NOT_FOUND")

            list().single() shouldBe TaskJson(task.id, milk, due, "DONE", "DONE")
        }
    }

    @Test
    fun `AC-03-04 未完了に戻した状態は保存され、読み戻しても未完了である`() {
        val task = doneTask()

        reopen(task.id).expectStatus().is2xxSuccessful

        list().single { it.id == task.id }.status shouldBe "OPEN"
    }

    @Test
    fun `AC-03-11 期限を過ぎた完了のタスクを未完了に戻す要求は成功し、読み戻した区分は期限切れである`() {
        val task = register(milk, pastDue)
        complete(task.id).expectStatus().is2xxSuccessful
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        list().single { it.id == task.id }.category shouldBe "DONE" // Given: 期限を過ぎた完了

        reopen(task.id).expectStatus().is2xxSuccessful

        list().single { it.id == task.id } shouldBe TaskJson(task.id, milk, pastDue, "OPEN", "OVERDUE")
    }

    @Test
    fun `AC-03-05 未完了のタスクを未完了に戻す要求は、成功する`() {
        val task = openTask()

        reopen(task.id).expectStatus().is2xxSuccessful
    }
}
