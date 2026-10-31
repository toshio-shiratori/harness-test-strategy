package com.example.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 一覧の並び順と表示の絞り込みの Property。制約表（C-30〜C-32、C-40〜C-45）の各行の例は [TaskConstraintTableTest] にある。
 * 並び順は、期限の日付の昇順（期限切れを含み、現在時刻によらない。OI-003）、期限のないタスクは最後、
 * 登録の古い順、登録時刻が同じなら登録ごとに増える番号の小さい順（OI-009）。完了済みは未完了の後ろにまとまる。
 */
class TaskListTest : StringSpec({
    // 分類: オラクル（隣り合う 2 件が、TRS の並び順の規則 C-40〜C-45 を満たす）
    "AC-05-01 AC-05-02 AC-05-03 AC-05-05 AC-05-07 任意のタスクの一覧は、並べ替えても件数が保たれ、隣り合う 2 件が並び順の規則を満たす" {
        checkAll(arbTasks, arbVisibility) { tasks, visibility ->
            val arranged = tasks.arranged(visibility)
            val expectedSize = if (visibility == CompletedVisibility.SHOW) tasks.size else tasks.count { it.status == TaskStatus.Open }
            arranged.size shouldBe expectedSize
            arranged.zipWithNext().forEach { (earlier, later) -> precedes(earlier, later) shouldBe true }
        }
    }

    // 分類: 不変量（並び順は現在時刻によらない）
    "AC-05-04 AC-05-05 並び順は現在時刻によらない" {
        val instants = Arb.long(0L..4_000_000_000_000L)
        checkAll(arbTasks, arbVisibility, instants, instants) { tasks, visibility, first, second ->
            tasks.listed(visibility, Clock.fixed(Instant.ofEpochMilli(first), ZoneOffset.UTC)).map { it.task } shouldBe
                tasks.listed(visibility, Clock.fixed(Instant.ofEpochMilli(second), ZoneOffset.UTC)).map { it.task }
        }
    }

    // 分類: オラクル（隠す一覧は、表示する一覧から完了済みを除いたもの）
    "AC-06-01 AC-06-02 完了済みを隠す一覧は表示する一覧から完了済みを除いたもので、含まれるのは未完了だけである" {
        checkAll(arbTasks) { tasks ->
            val shown = tasks.arranged(CompletedVisibility.SHOW)
            val hidden = tasks.arranged(CompletedVisibility.HIDE)
            shown.toSet() shouldBe tasks.toSet()
            hidden shouldBe shown.filter { it.status == TaskStatus.Open }
        }
    }

    // 分類: 不変量。技術的な不変条件のテスト（AC に紐づけない）。保存された選択は、そのまま使われる。AC-06-04 の保存と読み戻しは Integration で確かめる
    "保存された選択があるときは、その選択になる" {
        CompletedVisibility.entries.forEach { resolveVisibility(it) shouldBe it }
    }
})

// TRS の並び順の規則（C-40〜C-45）を 2 件の比較として書く。実装の比較子とは別の書き方
private fun precedes(a: Task, b: Task): Boolean {
    val aDone = a.status == TaskStatus.Done
    val bDone = b.status == TaskStatus.Done
    if (aDone != bDone) return !aDone
    val aDue = a.dueDate?.value
    val bDue = b.dueDate?.value
    if ((aDue == null) != (bDue == null)) return aDue != null
    if (aDue != null && bDue != null && aDue != bDue) return aDue < bDue
    if (a.createdAt != b.createdAt) return a.createdAt < b.createdAt
    return a.id.value < b.id.value
}
