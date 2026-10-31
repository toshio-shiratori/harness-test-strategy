package com.example.todo.domain

import java.time.Clock
import java.time.Instant

/** タスク。登録時刻は一覧の並び順（登録の古い順）に使う。 */
data class Task(
    val id: TaskId,
    val title: TaskTitle,
    val dueDate: DueDate?,
    val status: TaskStatus,
    val createdAt: Instant,
) {
    companion object {
        /** 未完了のタスクを登録する。期限が過去の日付でも登録できる。登録時刻は注入された Clock の現在時刻。 */
        fun register(id: TaskId, title: TaskTitle, dueDate: DueDate?, clock: Clock): Task =
            Task(id, title, dueDate, TaskStatus.Open, clock.instant())
    }
}

/** 完了にする。すでに完了なら変わらない。 */
fun Task.complete(): Task = copy(status = TaskStatus.Done)

/** 未完了に戻す。すでに未完了なら変わらない。 */
fun Task.reopen(): Task = copy(status = TaskStatus.Open)
