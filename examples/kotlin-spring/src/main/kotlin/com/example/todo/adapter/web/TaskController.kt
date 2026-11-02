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
 * 識別子の形が不正なときも、その識別子のタスクは無いので、存在しないタスクと同じ 404 と `TASK_NOT_FOUND` にする（OI-012）。
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
    fun complete(@PathVariable("id") id: String): ResponseEntity<*> = respond(parseTaskId(id)?.let(service::complete))

    @PostMapping("/tasks/{id}/reopen")
    fun reopen(@PathVariable("id") id: String): ResponseEntity<*> = respond(parseTaskId(id)?.let(service::reopen))

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

// 識別子の正規の形は、先頭にゼロを置かない ASCII の 10 進数字の並びで、Long に収まるもの。
// 符号・小数点・空白・全角数字・先頭のゼロや、Long を超える値は形が不正（同じタスクを指す別の綴りを作らない）
private val TASK_ID_FORMAT = Regex("0|[1-9][0-9]*")

/** パスの識別子を境界でドメイン型にする。形が不正なら null で、呼び出し側は存在しないタスクと同じに扱う。 */
internal fun parseTaskId(raw: String): TaskId? =
    if (TASK_ID_FORMAT.matches(raw)) raw.toLongOrNull()?.let(::TaskId) else null
