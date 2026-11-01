package com.example.todo.adapter.persistence

import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.CompletedVisibilityRepository
import com.example.todo.domain.Task
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskRepository
import org.springframework.stereotype.Repository

/**
 * [TaskRepository] と [CompletedVisibilityRepository] の MyBatis 実装。
 * 行 ↔ ドメイン型の変換は、`TaskRow.kt` と `VisibilityColumn.kt` にある。
 */
@Repository
class MyBatisTaskRepository(private val mapper: TaskMapper) : TaskRepository, CompletedVisibilityRepository {
    override fun nextId(): TaskId {
        val holder = IdHolder()
        mapper.insertSequence(holder)
        return TaskId(holder.id)
    }

    override fun save(task: Task) {
        mapper.upsert(task.toRow())
    }

    override fun find(id: TaskId): Task? = mapper.findById(id.value)?.toTask()

    override fun findAll(): List<Task> = mapper.findAll().map(TaskRow::toTask)

    override fun find(): CompletedVisibility? = mapper.findVisibility()?.toVisibility()

    override fun save(visibility: CompletedVisibility) {
        mapper.upsertVisibility(visibility.toColumn())
    }
}
