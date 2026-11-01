package com.example.todo.adapter.persistence

import com.example.todo.domain.DueDate
import com.example.todo.domain.Task
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskStatus
import com.example.todo.domain.TaskTitle
import com.example.todo.domain.Validated
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** `task` テーブルの 1 行。MyBatis が引数なしコンストラクタで生成するため、全てデフォルト値付きの var にしている。 */
data class TaskRow(
    var id: Long = 0,
    var title: String = "",
    var dueDate: LocalDate? = null,
    var status: String = "",
    var createdAt: LocalDateTime = LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC),
)

/** DB の `status` 列に保存する文字列。`TaskStatus` の型名とは独立した、永続化形式の契約。 */
internal enum class TaskStatusColumn { OPEN, DONE }

// DATETIME(6) に合わせ、マイクロ秒未満は切り捨てて保存する。UTC の LocalDateTime で持つ（サーバーの既定のタイムゾーンに依存しない）。
private fun Instant.toColumn(): LocalDateTime = LocalDateTime.ofInstant(truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC)

private fun LocalDateTime.toInstant(): Instant = toInstant(ZoneOffset.UTC)

// when を「式」として使い、else を置かない。TaskStatus に状態を追加するとここがコンパイルエラーになる。
private fun TaskStatus.toColumn(): TaskStatusColumn =
    when (this) {
        TaskStatus.Open -> TaskStatusColumn.OPEN
        TaskStatus.Done -> TaskStatusColumn.DONE
    }

internal fun Task.toRow(): TaskRow =
    TaskRow(
        id = id.value,
        title = title.value,
        dueDate = dueDate?.value,
        status = status.toColumn().name,
        createdAt = createdAt.toColumn(),
    )

/**
 * 行 → タスク。未知の status と、タイトルの規則に合わない行は例外で拒否する（外部の文字列を閉じた型へ。02a §1.3 規則 5）。
 * 例外の型は契約にしない。
 */
internal fun TaskRow.toTask(): Task {
    val column = TaskStatusColumn.valueOf(status) // 未知の status は IllegalArgumentException
    val validTitle = TaskTitle.of(title)
    require(validTitle is Validated.Valid) { "タイトルが規則に合わない行: id=$id" }
    return Task(
        id = TaskId(id),
        title = validTitle.value,
        dueDate = dueDate?.let(DueDate::of),
        status = when (column) {
            TaskStatusColumn.OPEN -> TaskStatus.Open
            TaskStatusColumn.DONE -> TaskStatus.Done
        },
        createdAt = createdAt.toInstant(),
    )
}
