package com.example.todo.support

import com.example.todo.adapter.persistence.IdHolder
import com.example.todo.adapter.persistence.TaskMapper
import com.example.todo.adapter.persistence.TaskRow
import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.CompletedVisibilityRepository
import com.example.todo.domain.Task
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// 層1（Spring・DB なし）で、ユースケースとアダプタを確かめるための偽の実装。実 DB を通す確認は Integration（src/integrationTest）が受け持つ。

/** メモリ上のリポジトリ。番号は 1 から増える。 */
class InMemoryRepositories : TaskRepository, CompletedVisibilityRepository {
    private var lastId = 0L
    private val tasks = linkedMapOf<TaskId, Task>()
    private var visibility: CompletedVisibility? = null

    override fun nextId(): TaskId = TaskId(++lastId)

    override fun save(task: Task) {
        tasks[task.id] = task
    }

    override fun find(id: TaskId): Task? = tasks[id]

    override fun findAll(): List<Task> = tasks.values.toList()

    override fun find(): CompletedVisibility? = visibility

    override fun save(visibility: CompletedVisibility) {
        this.visibility = visibility
    }
}

/** 行をそのまま保持するマッパー。upsert は同じ ID を上書きし、呼び出しを順に記録する。 */
class InMemoryTaskMapper : TaskMapper {
    var lastSequence = 0L
    val rows = linkedMapOf<Long, TaskRow>()
    val upserted = mutableListOf<TaskRow>()
    var visibility: String? = null
    val visibilityWrites = mutableListOf<String>()

    override fun insertSequence(holder: IdHolder): Int {
        holder.id = ++lastSequence
        return 1
    }

    override fun upsert(row: TaskRow) {
        upserted += row.copy()
        rows[row.id] = row.copy()
    }

    override fun findById(id: Long): TaskRow? = rows[id]?.copy()

    override fun findAll(): List<TaskRow> = rows.values.map { it.copy() }

    override fun findVisibility(): String? = visibility

    override fun upsertVisibility(visibility: String) {
        visibilityWrites += visibility
        this.visibility = visibility
    }
}

/** テストが進められる時計（層1用）。 */
class SteppingClock(private var now: Instant = Instant.parse("2026-11-01T01:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}
