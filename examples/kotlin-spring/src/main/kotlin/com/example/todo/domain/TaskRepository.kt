package com.example.todo.domain

/**
 * タスクの保存先（ポート）。実装は永続化のアダプタにある。
 * 識別子は、リポジトリが登録ごとに増える番号として払い出す（[nextId]）。
 */
interface TaskRepository {
    /** 次に登録するタスクの識別子。呼ぶたびに、前に払い出した番号より大きい番号を返す。 */
    fun nextId(): TaskId

    /** 保存する。同じ識別子のタスクがあれば置き換える。 */
    fun save(task: Task)

    fun find(id: TaskId): Task?

    fun findAll(): List<Task>
}

/** 完了済みの表示・非表示の選択の保存先（ポート）。利用者は 1 人なので、選択は 1 つだけ保存する。 */
interface CompletedVisibilityRepository {
    /** 保存された選択。まだ保存されていなければ null。 */
    fun find(): CompletedVisibility?

    fun save(visibility: CompletedVisibility)
}
