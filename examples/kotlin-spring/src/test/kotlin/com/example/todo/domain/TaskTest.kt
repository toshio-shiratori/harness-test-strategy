package com.example.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 登録と、完了・未完了の操作列の Property。制約表（C-20〜C-23）の各行の例は [TaskConstraintTableTest] にある。
 * 期待値は TRS TODO-001 の AC と制約表から取る。AC と制約表は、要件定義書・基本設計書・OI の決定から生成したもので、実装から逆生成しない（03 §3.1）。時刻は注入した Clock の現在時刻。
 */
class TaskTest : StringSpec({
    val today = Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneOffset.UTC)
    val milk = title("牛乳を買う")

    fun registered(id: Long, title: TaskTitle, dueDate: DueDate?) = Task.register(taskId(id), title, dueDate, today)

    "AC-01-09 期限を付けて登録できる" {
        registered(1, milk, due("2026-11-30")).dueDate?.value shouldBe LocalDate.of(2026, 11, 30)
    }

    "AC-01-10 期限を付けずに登録できる" {
        registered(1, milk, null).dueDate shouldBe null
    }

    "AC-01-11 登録したタスクは未完了である" {
        listOf(null, due("2026-11-30")).forEach { registered(1, milk, it).status shouldBe TaskStatus.Open }
    }

    "AC-01-12 過去の日付の期限でも登録できる" {
        mapOf("2026-10-31" to LocalDate.of(2026, 10, 31), "2025-11-01" to LocalDate.of(2025, 11, 1)).forEach { (raw, expected) ->
            registered(1, milk, due(raw)).dueDate?.value shouldBe expected
        }
    }

    "AC-05-03 時計を進めて続けて登録したタスクは、登録の古い順に並ぶ" {
        val clock = Clock.fixed(Instant.parse("2026-11-01T01:00:00Z"), ZoneOffset.UTC)
        listOf(due("2026-11-05"), null).forEach { dueDate ->
            val p = Task.register(taskId(1), title("P"), dueDate, clock)
            val q = Task.register(taskId(2), title("Q"), dueDate, Clock.offset(clock, Duration.ofSeconds(1)))

            listOf(q, p).arranged(CompletedVisibility.SHOW).map { it.title.value } shouldBe listOf("P", "Q")
        }
    }

    // 分類: ラウンドトリップ（入力のとおりに登録され、未完了である）
    "AC-01-09 AC-01-10 AC-01-11 任意のタイトルと期限で登録でき、登録したタスクは入力のとおりで未完了である" {
        checkAll(arbTaskId, arbTitle, arbDueDate.orNull(0.3)) { id, title, dueDate ->
            val task = registered(id.value, title, dueDate)

            task.id shouldBe id
            task.title shouldBe title
            task.dueDate shouldBe dueDate
            task.status shouldBe TaskStatus.Open
            task.createdAt shouldBe today.instant()
        }
    }

    // 分類: 冪等性（制約表の C-21・C-23。同じ操作を続けて行っても、1 回だけ行ったときと同じ。TRS の AC-02-08・AC-03-06）
    "AC-02-08 AC-03-06 どのタスクも、完了にする操作と未完了に戻す操作は、続けて 2 回行っても 1 回と同じである" {
        checkAll(arbTask) { task ->
            val completed = task.complete()
            val reopened = task.reopen()

            assertOnlyStatusChanged(task, completed, TaskStatus.Done)
            assertOnlyStatusChanged(task, completed.complete(), TaskStatus.Done)
            assertOnlyStatusChanged(task, reopened, TaskStatus.Open)
            assertOnlyStatusChanged(task, reopened.reopen(), TaskStatus.Open)
        }
    }

    // 分類: オラクル（操作列。04 §2.2 の状態遷移の抜けの還元先）。オラクルは制約表の 2 × 2（C-20〜C-23）:
    // 完了にする操作は結果を完了に、未完了に戻す操作は結果を未完了にし、どちらも元の状態に関わらない。
    "AC-02-01 AC-02-08 AC-03-01 AC-03-06 任意の操作列の後、各タスクの状態は最後の操作で決まり、状態以外とほかのタスクは変わらない" {
        checkAll(arbTasks, Arb.list(arbOperation, 0..30)) { initial, operations ->
            val current = initial.toMutableList()
            val lastOperation = mutableMapOf<TaskId, Operation>()
            operations.forEach { operation ->
                if (current.isNotEmpty()) {
                    val index = operation.target % current.size
                    val target = current[index]
                    current[index] = operation.kind.applyTo(target)
                    lastOperation[target.id] = operation
                }
            }
            current.map { it.id } shouldBe initial.map { it.id }
            current.zip(initial).forEach { (after, before) ->
                val expected = lastOperation[before.id]?.kind?.resultingStatus ?: before.status
                assertOnlyStatusChanged(before, after, expected)
            }
        }
    }
})

private enum class OperationKind(val resultingStatus: TaskStatus) {
    COMPLETE(TaskStatus.Done),
    REOPEN(TaskStatus.Open),
    ;

    fun applyTo(task: Task): Task =
        when (this) {
            COMPLETE -> task.complete()
            REOPEN -> task.reopen()
        }
}

private data class Operation(val kind: OperationKind, val target: Int)

private val arbOperation: Arb<Operation> =
    Arb.bind(Arb.element(OperationKind.entries), Arb.int(0..99), ::Operation)
