package com.example.todo.application

import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.CompletedVisibilityRepository
import com.example.todo.domain.DueDate
import com.example.todo.domain.ListedTask
import com.example.todo.domain.Task
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskRepository
import com.example.todo.domain.TaskTitle
import com.example.todo.domain.category
import com.example.todo.domain.complete
import com.example.todo.domain.listed
import com.example.todo.domain.reopen
import com.example.todo.domain.resolveVisibility
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * ユースケース。入力はアダプタ層でドメイン型に変換済みで、ここは検証をしない。
 * 時刻は注入された [Clock] の現在時刻だけを使う。
 */
@Service
class TaskService(
    private val tasks: TaskRepository,
    private val preferences: CompletedVisibilityRepository,
    private val clock: Clock,
) {
    @Transactional
    fun register(title: TaskTitle, dueDate: DueDate?): ListedTask {
        val task = Task.register(tasks.nextId(), title, dueDate, clock)
        tasks.save(task)
        return listedOf(task)
    }

    /** 完了にする。対象が存在しなければ null。すでに完了なら何も変えない。 */
    @Transactional
    fun complete(id: TaskId): ListedTask? = change(id, Task::complete)

    /** 未完了に戻す。対象が存在しなければ null。すでに未完了なら何も変えない。 */
    @Transactional
    fun reopen(id: TaskId): ListedTask? = change(id, Task::reopen)

    /** 保存された選択に従って絞り込み、並べた一覧。 */
    @Transactional(readOnly = true)
    fun list(): List<ListedTask> = tasks.findAll().listed(completedVisibility(), clock)

    @Transactional(readOnly = true)
    fun completedVisibility(): CompletedVisibility = resolveVisibility(preferences.find())

    @Transactional
    fun changeCompletedVisibility(visibility: CompletedVisibility) = preferences.save(visibility)

    private fun change(id: TaskId, operation: (Task) -> Task): ListedTask? {
        val current = tasks.find(id) ?: return null
        val changed = operation(current)
        tasks.save(changed)
        return listedOf(changed)
    }

    private fun listedOf(task: Task) = ListedTask(task, task.category(clock))
}
