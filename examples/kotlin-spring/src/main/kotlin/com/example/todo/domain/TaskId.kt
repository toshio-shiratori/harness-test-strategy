package com.example.todo.domain

/** タスクの識別子。登録ごとに増える番号で、登録時刻が同じタスクの新旧を決める。別の識別子・番号との取り違えをコンパイルエラーにする。 */
@JvmInline
value class TaskId(val value: Long)
