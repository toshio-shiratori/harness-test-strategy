package com.example.todo.domain

/** タスクの完了の状態。完了時刻などの状態ごとのデータは、要件にないので持たない。 */
sealed interface TaskStatus {
    data object Open : TaskStatus
    data object Done : TaskStatus
}
