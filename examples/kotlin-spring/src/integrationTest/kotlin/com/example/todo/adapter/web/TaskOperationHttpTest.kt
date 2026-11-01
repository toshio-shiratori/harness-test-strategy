package com.example.todo.adapter.web

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 完了にする・未完了に戻す（UC-02・UC-03）の HTTP と DB の結合テスト（層2 Integration）。
 * 状態の変更は HTTP で操作し、保存された状態は HTTP で一覧を取り直して確かめる。
 * 層1（`TaskTest`・`TaskConstraintTableTest`）は、状態遷移の規則を DB なしで確かめている。
 * 期待値は TRS TODO-001 の AC と OI-006 の決定を正とする。
 */
class TaskOperationHttpTest : TaskApiTestBase() {
    private val milk = "牛乳を買う"
    private val due = "2026-11-30"

    // 登録された識別子は、採番表の AUTO_INCREMENT で増える小さい番号。この値は払い出されない
    private val unknownId = 9_999_999_999L

    private fun openTask(): TaskJson = register(milk, due)

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
    fun `AC-03-04 未完了に戻した状態は保存され、読み戻しても未完了である`() {
        val task = doneTask()

        reopen(task.id).expectStatus().is2xxSuccessful

        list().single { it.id == task.id }.status shouldBe "OPEN"
    }

    @Test
    fun `AC-03-05 未完了のタスクを未完了に戻す要求は、成功する`() {
        val task = openTask()

        reopen(task.id).expectStatus().is2xxSuccessful
    }
}
