package com.example.todo.adapter.web

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.TimeZone

/**
 * 一覧（UC-04・UC-05・UC-06）の HTTP と DB の結合テスト（層2 Integration）。
 * 区分・並び順・絞り込みの規則そのものは層1（`TaskCategoryTest`・`TaskListTest`）が確かめている。
 * ここでは、DB を通した保存と読み戻し、保存された選択による絞り込み、サーバーの既定のタイムゾーンに依存しないことを見る。
 * 期待値は TRS TODO-001 の AC と制約表を正とする。
 */
class TaskListHttpTest : TaskApiTestBase() {
    private fun registerDone(title: String, due: String? = null): TaskJson {
        val task = register(title, due)
        complete(task.id).expectStatus().is2xxSuccessful
        return task
    }

    @Test
    fun `AC-04-03 登録したタスクが、区分つきで一覧に現れる`() {
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        register("A")
        registerDone("B")
        register("C", "2026-10-31")
        registerDone("D", "2026-10-31")

        val tasks = list()

        tasks shouldHaveSize 4
        tasks.associate { it.title to it.category } shouldBe mapOf("A" to "OPEN", "B" to "DONE", "C" to "OVERDUE", "D" to "DONE")
    }

    @Test
    fun `AC-04-04 期限切れの判定は、サーバーの既定のタイムゾーンによらず Asia_Tokyo の日付で行う`() {
        val original = TimeZone.getDefault()
        try {
            listOf("UTC", "America/Los_Angeles", "Asia/Tokyo").forEach { zone ->
                // 2026-11-01 の JST の 24 時（UTC の 15:00:00）で期限切れになる。1 ミリ秒前は未完了
                listOf("2026-11-01T14:59:59.999Z" to "OPEN", "2026-11-01T15:00:00Z" to "OVERDUE").forEach { (now, category) ->
                    resetState()
                    TimeZone.setDefault(TimeZone.getTimeZone(zone))
                    register("牛乳を買う", "2026-11-01")
                    clock.set(Instant.parse(now))

                    list().single().category shouldBe category
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `AC-04-06 登録したタスクが 1 件も無いとき、一覧は空である`() {
        list().shouldBeEmpty()
    }

    @Test
    fun `AC-05-06 保存したタスクの一覧も、期限が近い順と登録の古い順に並び、完了済みは未完了の後ろにまとまる`() {
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        // 登録のたびに時計を 1 秒進める（登録時刻が異なる）
        fun step() = clock.advance(Duration.ofSeconds(1))
        register("P", "2026-11-10")
        step()
        register("Q")
        step()
        register("R", "2026-11-03")
        step()
        register("S", "2026-11-10")
        step()
        register("T")
        step()
        registerDone("U", "2026-11-05")
        step()
        registerDone("V")

        list().map { it.title } shouldBe listOf("R", "P", "S", "Q", "T", "U", "V")
    }

    @Test
    fun `AC-05-08 時計が固定された状態で続けて登録したタスクは、登録した順に並ぶ`() {
        listOf("P", "Q", "R").forEach { register(it) }

        list().map { it.title } shouldBe listOf("P", "Q", "R")
    }

    @Test
    fun `AC-06-03 保存された選択が無いとき、選択は表示するである`() {
        readVisibility() shouldBe "SHOW"
    }

    @Test
    fun `AC-06-04 選択は保存され、別の要求で読み戻しても保たれる`() {
        // 保存されていない -> 隠す
        setVisibility("HIDE").expectStatus().is2xxSuccessful
        readVisibility() shouldBe "HIDE"

        // 隠す -> 表示する
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        readVisibility() shouldBe "SHOW"
    }

    @Test
    fun `AC-06-07 完了済みの表示の選択に未知の値を送ると、400 と MALFORMED_REQUEST で拒否され、選択は変わらない`() {
        setVisibility("HIDE").expectStatus().is2xxSuccessful

        setVisibility("UNKNOWN").expectProblem(400, "MALFORMED_REQUEST")

        readVisibility() shouldBe "HIDE"
    }

    @Test
    fun `AC-06-06 完了済みを隠す選択のとき、取得した一覧に完了済みは含まれない`() {
        setVisibility("HIDE").expectStatus().is2xxSuccessful
        registerDone("done")
        register("open")

        list().map { it.title } shouldBe listOf("open")
    }
}
