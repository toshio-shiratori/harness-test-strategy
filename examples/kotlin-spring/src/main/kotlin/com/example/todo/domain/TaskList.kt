package com.example.todo.domain

import java.time.Clock

/** 完了済みを一覧に表示するか隠すか。 */
enum class CompletedVisibility { SHOW, HIDE }

/** 保存された選択が無い（初めて開いた）ときは「表示する」。 */
fun resolveVisibility(stored: CompletedVisibility?): CompletedVisibility = stored ?: CompletedVisibility.SHOW

/** 一覧に載せるタスクと、その区分。 */
data class ListedTask(val task: Task, val category: TaskCategory)

// 未完了を先に、完了を後ろにまとめる。それぞれ期限の日付の昇順（期限のないタスクは最後）、登録の古い順、登録ごとに増える番号の小さい順。
// 現在時刻に依存しないので、期限切れのタスクは期限内のタスクより前に並ぶ
private val listOrder: Comparator<Task> = compareBy<Task>(
    { it.status == TaskStatus.Done },
    { it.dueDate == null },
    { it.dueDate?.value },
    { it.createdAt },
    { it.id.value },
)

/** 選択に従って完了済みを除き、一覧の順に並べる。 */
fun List<Task>.arranged(visibility: CompletedVisibility): List<Task> {
    val shown = when (visibility) {
        CompletedVisibility.SHOW -> this
        CompletedVisibility.HIDE -> filter { it.status == TaskStatus.Open }
    }
    return shown.sortedWith(listOrder)
}

/** 一覧を作る。並び順は現在時刻によらず、区分だけが Clock の現在時刻で決まる。 */
fun List<Task>.listed(visibility: CompletedVisibility, clock: Clock): List<ListedTask> =
    arranged(visibility).map { ListedTask(it, it.category(clock)) }
