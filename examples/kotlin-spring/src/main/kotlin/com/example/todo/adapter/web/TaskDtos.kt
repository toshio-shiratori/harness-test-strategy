package com.example.todo.adapter.web

import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.DueDate
import com.example.todo.domain.ListedTask
import com.example.todo.domain.TaskCategory
import com.example.todo.domain.TaskStatus
import com.example.todo.domain.TaskTitle
import com.example.todo.domain.TitleError
import com.example.todo.domain.Validated

/**
 * 登録の要求の本文。外部入力の形そのもので、ドメインの意味は持たず、アダプタ層から外へ出さない。
 * 欠落・null は Jackson に任せず null として受け、タイトルが無ければ TITLE_BLANK、期限が無ければ期限なしとして扱う。
 */
data class RegisterTaskRequest(val title: String?, val dueDate: String?)

/** 一覧と登録・完了・未完了に戻す操作の応答の 1 件。`dueDate` は `YYYY-MM-DD`、期限が無ければ null。 */
data class TaskResponse(val id: Long, val title: String, val dueDate: String?, val status: String, val category: String)

/** 完了済みの表示の選択。ドメインの [CompletedVisibility] とは別に、応答の契約として持つ。 */
enum class VisibilityValue { SHOW, HIDE }

data class VisibilityBody(val visibility: VisibilityValue)

/** 境界で一度だけドメイン型に変換した、登録の入力。 */
internal data class Registration(val title: TaskTitle, val dueDate: DueDate?)

/**
 * 登録の要求をドメイン型に変換する（Parse, don't validate）。失敗は、同時に成立している理由をすべて集めて返す（OI-011）。
 * 期限の項目が無い・null なら期限なし。空文字列を含む値は [DueDate.of] に渡す。空文字列の扱いは OI-012（Open）で未決で、これは今の実装の説明であり、仕様ではない。
 */
internal fun RegisterTaskRequest.parse(): Validated<Registration, List<ErrorCode>> {
    val parsedTitle = TaskTitle.of(title.orEmpty())
    val parsedDue: Validated<DueDate?, *> = dueDate?.let { DueDate.of(it) } ?: Validated.Valid(null)
    if (parsedTitle is Validated.Valid && parsedDue is Validated.Valid) {
        return Validated.Valid(Registration(parsedTitle.value, parsedDue.value))
    }
    val codes = buildList {
        if (parsedTitle is Validated.Invalid) add(parsedTitle.error.toCode())
        if (parsedDue is Validated.Invalid) add(ErrorCode.DUE_DATE_INVALID)
    }
    return Validated.Invalid(codes)
}

private fun TitleError.toCode(): ErrorCode =
    when (this) {
        TitleError.BLANK -> ErrorCode.TITLE_BLANK
        TitleError.TOO_LONG -> ErrorCode.TITLE_TOO_LONG
    }

internal fun ListedTask.toResponse(): TaskResponse =
    TaskResponse(
        id = task.id.value,
        title = task.title.value,
        dueDate = task.dueDate?.value?.toString(),
        status = when (task.status) {
            TaskStatus.Open -> "OPEN"
            TaskStatus.Done -> "DONE"
        },
        category = when (category) {
            TaskCategory.Open -> "OPEN"
            TaskCategory.Done -> "DONE"
            TaskCategory.Overdue -> "OVERDUE"
        },
    )

internal fun CompletedVisibility.toBody(): VisibilityBody =
    VisibilityBody(
        when (this) {
            CompletedVisibility.SHOW -> VisibilityValue.SHOW
            CompletedVisibility.HIDE -> VisibilityValue.HIDE
        },
    )

internal fun VisibilityBody.toDomain(): CompletedVisibility =
    when (visibility) {
        VisibilityValue.SHOW -> CompletedVisibility.SHOW
        VisibilityValue.HIDE -> CompletedVisibility.HIDE
    }
