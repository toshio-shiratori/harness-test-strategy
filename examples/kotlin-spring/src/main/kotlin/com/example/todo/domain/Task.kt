package com.example.todo.domain

import java.time.Clock
import java.time.Instant

/**
 * タスク。登録時刻は一覧の並び順（登録の古い順）に使う。
 * 生成は [register]（新しく登録する）と [restore]（保存された値から戻す。同じモジュールの永続化アダプタ用）に限る。どちらも検証済みの型だけを受け取る。
 */
@ConsistentCopyVisibility
data class Task private constructor(
    val id: TaskId,
    val title: TaskTitle,
    val dueDate: DueDate?,
    val status: TaskStatus,
    val createdAt: Instant,
) {
    /** 完了にする。すでに完了なら変わらない。 */
    fun complete(): Task = Task(id, title, dueDate, TaskStatus.Done, createdAt)

    /** 未完了に戻す。すでに未完了なら変わらない。 */
    fun reopen(): Task = Task(id, title, dueDate, TaskStatus.Open, createdAt)

    companion object {
        /** 未完了のタスクを登録する。期限が過去の日付でも登録できる。登録時刻は注入された Clock の現在時刻。 */
        fun register(id: TaskId, title: TaskTitle, dueDate: DueDate?, clock: Clock): Task =
            Task(id, title, dueDate, TaskStatus.Open, clock.instant())

        /** 保存された値からタスクを戻す。各項目は検証済みの型なので、ここでは検証しない。 */
        internal fun restore(
            id: TaskId,
            title: TaskTitle,
            dueDate: DueDate?,
            status: TaskStatus,
            createdAt: Instant,
        ): Task = Task(id, title, dueDate, status, createdAt)
    }
}
