package com.example.todo.domain

enum class TaskIdError { NEGATIVE }

/**
 * タスクの識別子。登録ごとに増える番号で、登録時刻が同じタスクの新旧を決める。別の識別子・番号との取り違えをコンパイルエラーにする。
 * 負の値だけを拒否する。理由と、0・上限を拒否しない理由は、実現例 02-1 の §2.1「サンプルの判断」にある。
 */
@JvmInline
value class TaskId private constructor(val value: Long) {
    companion object {
        fun of(value: Long): Validated<TaskId, TaskIdError> =
            if (value < 0) Validated.Invalid(TaskIdError.NEGATIVE) else Validated.Valid(TaskId(value))
    }
}
