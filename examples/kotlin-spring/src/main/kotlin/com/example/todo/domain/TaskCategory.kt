package com.example.todo.domain

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** 一覧での区分。未完了・完了・期限切れのちょうど 1 つ。 */
sealed interface TaskCategory {
    data object Open : TaskCategory
    data object Done : TaskCategory
    data object Overdue : TaskCategory
}

/** 期限切れの判定に使うタイムゾーン。サーバーの既定のタイムゾーンや Clock のゾーンによらず、この日付で判定する。 */
val BUSINESS_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

/**
 * 区分を判定する。完了は期限によらず完了。未完了は、Asia/Tokyo の今日が期限の日付より後（期限の翌日の 0 時以降）なら期限切れ。
 * `when` に `else` を置かないので、状態を増やすとここがコンパイルエラーになる。
 */
fun Task.category(clock: Clock): TaskCategory =
    when (status) {
        TaskStatus.Done -> TaskCategory.Done
        TaskStatus.Open -> if (isPastDue(clock)) TaskCategory.Overdue else TaskCategory.Open
    }

private fun Task.isPastDue(clock: Clock): Boolean {
    val due = dueDate ?: return false
    return LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE).isAfter(due.value)
}
