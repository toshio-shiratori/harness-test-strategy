package com.example.todo.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * 期待値は TRS TODO-001 の AC-04-01 と制約表（5.2 の C-10〜C-15）を正とする。
 * 現在時刻の判定は Asia/Tokyo（+09:00）の日付で行い、期限の翌日の 0 時 0 分から期限切れになる。
 */
class TaskCategoryTest : FunSpec({
    val tokyo = ZoneOffset.ofHours(9)

    fun task(status: TaskStatus, due: String?) =
        Task(TaskId(1), title("牛乳を買う"), due?.let(::due), status, Instant.EPOCH)

    fun clockAt(now: String, zone: ZoneId = ZoneOffset.UTC): Clock = Clock.fixed(OffsetDateTime.parse(now).toInstant(), zone)

    // AC-04-01 の Examples の 1 行を 1 ケースにする。id は、その行が当たる制約表 5.2 の行（期待結果は制約表の値）
    data class Case(val id: String, val state: TaskStatus, val due: String?, val now: String, val expected: TaskCategory)

    context("AC-04-01 タスクの区分は、完了・期限・現在時刻で決まる（C-10〜C-13）") {
        withData(
            nameFn = { "${it.id} ${it.state} 期限 ${it.due} 現在 ${it.now} → ${it.expected}" },
            ts = listOf(
                Case("C-10", TaskStatus.Open, null, "2026-11-01T10:00+09:00", TaskCategory.Open),
                Case("C-11", TaskStatus.Open, "2026-11-02", "2026-11-01T10:00+09:00", TaskCategory.Open),
                Case("C-11", TaskStatus.Open, "2026-11-01", "2026-11-01T00:00+09:00", TaskCategory.Open),
                Case("C-11", TaskStatus.Open, "2026-11-01", "2026-11-01T23:59:59.999+09:00", TaskCategory.Open),
                Case("C-12", TaskStatus.Open, "2026-11-01", "2026-11-02T00:00+09:00", TaskCategory.Overdue),
                Case("C-12", TaskStatus.Open, "2026-11-01", "2026-11-05T12:00+09:00", TaskCategory.Overdue),
                Case("C-13", TaskStatus.Done, null, "2026-11-01T10:00+09:00", TaskCategory.Done),
                Case("C-13", TaskStatus.Done, "2026-11-01", "2026-11-01T10:00+09:00", TaskCategory.Done),
                Case("C-13", TaskStatus.Done, "2026-11-01", "2026-11-02T00:00+09:00", TaskCategory.Done),
                Case("C-13", TaskStatus.Done, "2026-11-01", "2026-11-05T12:00+09:00", TaskCategory.Done),
            ),
        ) { it ->
            task(it.state, it.due).category(clockAt(it.now)) shouldBe it.expected
        }
    }

    // 分類: 境界
    // 期限の日の 0 時 0 分（JST）から、期限の翌日の 0 時 0 分（JST）までを、境界の前後 1 ミリ秒で確かめる
    test("AC-04-01 期限の翌日の 0 時 0 分（JST）の 1 ミリ秒前は未完了、ちょうどで期限切れになる") {
        checkAll(arbModernDueDate) { dueDate ->
            val start = dueDate.value.atStartOfDay(tokyo).toInstant()
            val nextDay = dueDate.value.plusDays(1).atStartOfDay(tokyo).toInstant()
            val open = Task(TaskId(1), title("a"), dueDate, TaskStatus.Open, Instant.EPOCH)
            open.category(Clock.fixed(start, ZoneOffset.UTC)) shouldBe TaskCategory.Open
            open.category(Clock.fixed(nextDay.minusMillis(1), ZoneOffset.UTC)) shouldBe TaskCategory.Open
            open.category(Clock.fixed(nextDay, ZoneOffset.UTC)) shouldBe TaskCategory.Overdue
        }
    }

    // 分類: オラクル（完了・期限・現在時刻から区分を決める別の書き方と照合する）
    test("AC-04-02 AC-04-01 どのタスクも、区分は未完了・完了・期限切れのちょうど 1 つで、完了と期限と現在時刻のオラクルに一致する") {
        val arbNow = Arb.long(
            OffsetDateTime.parse("1990-01-01T00:00+09:00").toInstant().toEpochMilli()..OffsetDateTime.parse("2100-12-31T00:00+09:00").toInstant().toEpochMilli(),
        )
        checkAll(arbTaskId, arbModernDueDate, arbStatus, arbNow) { id, dueDate, status, nowMillis ->
            val now = Instant.ofEpochMilli(nowMillis)
            val withDue = Task(id, title("a"), dueDate, status, Instant.EPOCH)
            val withoutDue = withDue.copy(dueDate = null)
            val pastDue = !now.isBefore(dueDate.value.plusDays(1).atStartOfDay(tokyo).toInstant())
            val expected = when (status) {
                TaskStatus.Done -> TaskCategory.Done
                TaskStatus.Open -> if (pastDue) TaskCategory.Overdue else TaskCategory.Open
            }
            withDue.category(Clock.fixed(now, ZoneOffset.UTC)) shouldBe expected
            // 期限がなければ、完了か未完了のどちらかで、期限切れにはならない
            withoutDue.category(Clock.fixed(now, ZoneOffset.UTC)) shouldBe
                if (status == TaskStatus.Done) TaskCategory.Done else TaskCategory.Open
        }
    }

    // 分類: 単調性。現在時刻が進むと、未完了のタスクは「期限内 → 期限切れ」にだけ変わり、戻らない
    test("AC-04-01 現在時刻が進むと、期限切れのタスクが期限内に戻ることはない") {
        checkAll(arbModernDueDate, Arb.long(-10L * 86_400_000..10L * 86_400_000), Arb.long(0L..10L * 86_400_000)) { dueDate, offset, step ->
            val base = dueDate.value.atStartOfDay(tokyo).toInstant().plusMillis(offset)
            val open = Task(TaskId(1), title("a"), dueDate, TaskStatus.Open, Instant.EPOCH)
            if (open.category(Clock.fixed(base, ZoneOffset.UTC)) == TaskCategory.Overdue) {
                open.category(Clock.fixed(base.plusMillis(step), ZoneOffset.UTC)) shouldBe TaskCategory.Overdue
            }
        }
    }

    context("AC-04-04 期限切れの判定は、サーバーの既定のタイムゾーンによらず Asia/Tokyo の日付で行う（C-14、C-15）") {
        data class ZoneCase(val id: String, val zone: String, val now: String, val expected: TaskCategory)
        withData(
            nameFn = { "${it.id} ${it.zone} 現在 ${it.now} → ${it.expected}" },
            ts = listOf("UTC", "America/Los_Angeles", "Asia/Tokyo").flatMap { zone ->
                listOf(
                    ZoneCase("C-14", zone, "2026-11-01T14:59:59.999Z", TaskCategory.Open),
                    ZoneCase("C-15", zone, "2026-11-01T15:00:00Z", TaskCategory.Overdue),
                )
            },
        ) { it ->
            val open = task(TaskStatus.Open, "2026-11-01")
            val previous = TimeZone.getDefault()
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(it.zone))
                // 時計のゾーンも既定のタイムゾーンも、判定に使われない
                open.category(clockAt(it.now, ZoneId.of(it.zone))) shouldBe it.expected
            } finally {
                TimeZone.setDefault(previous)
            }
        }
    }

    // 分類: 不変量（時計のゾーンが何であっても、同じ時刻なら区分は同じ。LocalDate.now(clock) の誤用を見分ける）
    test("AC-04-04 時計のゾーンが何であっても、同じ時刻なら区分は同じである") {
        val zones = Arb.element(ZoneId.getAvailableZoneIds().toList().sorted())
        checkAll(arbModernDueDate, Arb.long(-10L * 86_400_000..10L * 86_400_000), Arb.bind(zones, zones) { a, b -> a to b }) { dueDate, offset, (a, b) ->
            val instant = dueDate.value.atStartOfDay(tokyo).toInstant().plusMillis(offset)
            val open = Task(TaskId(1), title("a"), dueDate, TaskStatus.Open, Instant.EPOCH)
            open.category(Clock.fixed(instant, ZoneId.of(a))) shouldBe open.category(Clock.fixed(instant, ZoneId.of(b)))
        }
    }
})
