package com.example.todo.adapter.web

import com.example.todo.application.TaskService
import com.example.todo.domain.ListedTask
import com.example.todo.domain.TaskId
import com.example.todo.domain.Validated
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * タスクの HTTP API。入力は境界でドメイン型に変換し、拒否は 400（ProblemDetail と `codes`）、
 * 存在しない対象は 404 で返す（TRS TODO-001 OI-001, OI-006）。成功は 2xx で、二重の操作も成功にする。
 */
@RestController
class TaskController(private val service: TaskService) {
    @PostMapping("/tasks")
    fun register(@RequestBody request: RegisterTaskRequest): ResponseEntity<*> =
        when (val parsed = request.parse()) {
            is Validated.Valid -> {
                val registered = service.register(parsed.value.title, parsed.value.dueDate)
                ResponseEntity.status(HttpStatus.CREATED).body(registered.toResponse())
            }
            is Validated.Invalid ->
                ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, parsed.error, "タイトルまたは期限が正しくありません"))
        }

    @GetMapping("/tasks")
    fun list(): List<TaskResponse> = service.list().map(ListedTask::toResponse)

    @PostMapping("/tasks/{id}/complete")
    fun complete(@PathVariable("id") id: Long): ResponseEntity<*> = respond(service.complete(TaskId(id)))

    @PostMapping("/tasks/{id}/reopen")
    fun reopen(@PathVariable("id") id: Long): ResponseEntity<*> = respond(service.reopen(TaskId(id)))

    @GetMapping("/settings/completed-visibility")
    fun completedVisibility(): VisibilityBody = service.completedVisibility().toBody()

    @PutMapping("/settings/completed-visibility")
    fun changeCompletedVisibility(@RequestBody body: VisibilityBody): VisibilityBody {
        service.changeCompletedVisibility(body.toDomain())
        return body
    }

    private fun respond(task: ListedTask?): ResponseEntity<*> =
        if (task == null) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, listOf(ErrorCode.TASK_NOT_FOUND), "タスクが見つかりません"))
        } else {
            ResponseEntity.ok(task.toResponse())
        }
}
