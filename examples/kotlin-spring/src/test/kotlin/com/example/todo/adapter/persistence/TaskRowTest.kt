package com.example.todo.adapter.persistence

import com.example.todo.domain.Task
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskStatus
import com.example.todo.domain.arbDueDate
import com.example.todo.domain.arbStatus
import com.example.todo.domain.arbTaskId
import com.example.todo.domain.arbTitle
import com.example.todo.domain.due
import com.example.todo.domain.title
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * タスクと DB の行の変換を、層1で確かめる技術的な検証。実 DB での保存と読み戻しは、Integration が確かめる。
 * 層2の対応表は変えないため、テスト名に AC 番号を付けない。
 */
class TaskRowTest : StringSpec({
    val created = Instant.parse("2026-11-01T01:00:00.123456Z")

    "タスクを行にして戻すと、同じタスクになる（ラウンドトリップ）" {
        checkAll(arbTaskId, arbTitle, arbDueDate.orNull(0.3), arbStatus) { id, title, dueDate, status ->
            val task = Task(id, title, dueDate, status, created)

            task.toRow().toTask() shouldBe task
        }
    }

    "行の列は、型の名前とは独立した永続化の形式（OPEN / DONE、UTC の日時、日付）になる" {
        val open = Task(TaskId(7), title("牛乳を買う"), due("2026-11-30"), TaskStatus.Open, created).toRow()
        val done = Task(TaskId(8), title("卵"), null, TaskStatus.Done, created).toRow()

        open shouldBe TaskRow(7, "牛乳を買う", LocalDate.of(2026, 11, 30), "OPEN", LocalDateTime.of(2026, 11, 1, 1, 0, 0, 123_456_000))
        done shouldBe TaskRow(8, "卵", null, "DONE", LocalDateTime.of(2026, 11, 1, 1, 0, 0, 123_456_000))
    }

    "登録時刻は、マイクロ秒未満を切り捨てて行にする（DATETIME(6) に合わせる）" {
        val nanos = Instant.parse("2026-11-01T01:00:00.123456999Z")

        Task(TaskId(1), title("a"), null, TaskStatus.Open, nanos).toRow().createdAt shouldBe
            LocalDateTime.of(2026, 11, 1, 1, 0, 0, 123_456_000)
    }

    "未知の status の行は、例外で拒否される" {
        listOf("", "UNKNOWN", "open", "Done").forEach { status ->
            shouldThrowAny { TaskRow(1, "a", null, status).toTask() }
        }
    }

    "タイトルの規則に合わない行（空・空白だけ・51 字）は、例外で拒否される" {
        listOf("", " ", "あ".repeat(51)).forEach { title ->
            shouldThrowAny { TaskRow(1, title, null, "OPEN").toTask() }
        }
    }

    "既定の行は、MyBatis が引数なしで生成できる" {
        TaskRow() shouldBe TaskRow(0, "", null, "", LocalDateTime.of(1970, 1, 1, 0, 0, 0))
    }
})
