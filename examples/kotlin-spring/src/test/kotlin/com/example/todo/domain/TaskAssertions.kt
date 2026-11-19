package com.example.todo.domain

import io.kotest.matchers.shouldBe

/** 操作の後のタスクは、状態が [status] になり、状態以外の属性は操作の前と同じである。 */
fun assertOnlyStatusChanged(before: Task, after: Task, status: TaskStatus) {
    after.status shouldBe status
    after.id shouldBe before.id
    after.title shouldBe before.title
    after.dueDate shouldBe before.dueDate
    after.createdAt shouldBe before.createdAt
}
