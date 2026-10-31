package com.example.todo.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * TRS TODO-001 の §5.2（組合せ別の期待結果）のうち、層1で確かめる行を、データ駆動テストのケースにする（03 §2.3）。
 * ケース名の先頭は制約 ID（C-xx）、コンテナ名の先頭は AC ID である。期待結果は制約表の値で、実装の挙動から逆生成しない。
 * §5.1（禁止の組合せ）は型で排除するので、ケースにしない。
 * 区分の判定（C-10〜C-15）は [TaskCategoryTest] にある。
 */
class TaskConstraintTableTest : FunSpec({
    val milk = "牛乳を買う"
    val combining = "é"
    val flag = "🇯🇵"

    context("AC-01-01 1〜50 字のタイトルは受理され、見た目の 1 文字を 1 字と数えた長さになる（C-50）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                TitleCase("C-50", "F1-L1 1 字", "a", saved = "a", length = 1),
                TitleCase("C-50", "F1-L2 2〜49 字", milk, saved = milk, length = 5),
                TitleCase("C-50", "F1-L3 50 字", "あ".repeat(50), saved = "あ".repeat(50), length = 50),
                TitleCase("C-50", "F1-L11 結合文字 50 個", combining.repeat(50), saved = combining.repeat(50), length = 50),
                TitleCase("C-50", "F1-L11 国旗 50 個", flag.repeat(50), saved = flag.repeat(50), length = 50),
            ),
        ) { it.verify() }
    }

    context("AC-01-02 51 字以上のタイトルは TOO_LONG で拒否される（C-51）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                TitleCase("C-51", "F1-L4 51 字", "あ".repeat(51), error = TitleError.TOO_LONG),
                TitleCase("C-51", "F1-L4 200 字", "あ".repeat(200), error = TitleError.TOO_LONG),
                TitleCase("C-51", "F1-L11 結合文字 51 個", combining.repeat(51), error = TitleError.TOO_LONG),
                TitleCase("C-51", "F1-L11 国旗 51 個", flag.repeat(51), error = TitleError.TOO_LONG),
                TitleCase("C-51", "F1-L12 前後の空白を除いても 51 字", " " + "あ".repeat(51) + " ", error = TitleError.TOO_LONG),
            ),
        ) { it.verify() }
    }

    context("AC-01-03 AC-01-04 空のタイトルと空白だけのタイトルは BLANK で拒否される（C-52）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                TitleCase("C-52", "F1-L5 空", "", error = TitleError.BLANK),
                TitleCase("C-52", "F1-L6 半角空白", " ", error = TitleError.BLANK),
                TitleCase("C-52", "F1-L6 全角空白", "　", error = TitleError.BLANK),
                TitleCase("C-52", "F1-L6 タブ", "\t", error = TitleError.BLANK),
                TitleCase("C-52", "F1-L6 改行", "\n", error = TitleError.BLANK),
                TitleCase("C-52", "F1-L6 空白の組合せ", " 　\t\n", error = TitleError.BLANK),
            ),
        ) { it.verify() }
    }

    context("AC-01-05 AC-01-06 上の AC に挙げた以外の White_Space は、だけなら拒否、前後なら除いて保存される（C-53）") {
        val only = listOf(
            "\u000B", "\u000C", "\u000D", "\u0085", " ", " ", " ",
            " ", " ", " ", " ", " ", "\u000D\u000A",
        ).map { TitleCase("C-53", "F1-L7 だけ ${it.map { c -> "U+%04X".format(c.code) }}", it, error = TitleError.BLANK) }
        val around = listOf(
            " $milk ",
            " $milk ",
            "\u000D\u000A$milk\u000D\u000A",
            "\u0085$milk ",
        ).map { TitleCase("C-53", "F1-L9 前後 ${it.length} 文字", it, saved = milk) }
        withData(nameFn = { "${it.id} ${it.label}" }, ts = only + around) { it.verify() }
    }

    context("AC-01-07 前後の空白だけを除いて保存される（C-54）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                TitleCase("C-54", "F1-L8 半角空白", " $milk ", saved = milk),
                TitleCase("C-54", "F1-L8 全角空白", "　$milk　", saved = milk),
                TitleCase("C-54", "F1-L8 タブと改行", "\t\n$milk\n\t", saved = milk),
                TitleCase("C-54", "F1-L10 途中の空白は残る", "牛乳 と　卵", saved = "牛乳 と　卵"),
            ),
        ) { it.verify() }
    }

    context("AC-01-02 AC-01-08 前後の空白を除いた後で数える（C-55）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                TitleCase("C-55", "F1-L12 除く前は 52 字、除いた後は 50 字", " " + "あ".repeat(50) + " ", saved = "あ".repeat(50), length = 50),
            ),
        ) { it.verify() }
    }

    context("AC-01-25 White_Space 属性を持たない文字は、空白として扱わず、そのまま保存される（C-57）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                TitleCase("C-57", "F1-L13 U+200B だけ", "​", saved = "​"),
                TitleCase("C-57", "F1-L13 U+FEFF だけ", "﻿", saved = "﻿"),
                TitleCase("C-57", "F1-L13 先頭に U+200B", "​$milk", saved = "​$milk"),
                TitleCase("C-57", "F1-L13 末尾に U+FEFF", "$milk﻿", saved = "$milk﻿"),
            ),
        ) { it.verify() }
    }

    context("AC-01-14 YYYY-MM-DD として読めない期限と、実在しない日の期限は拒否される（C-56）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                DueCase("C-56", "F2-L6 時刻つき", "2026-11-30T10:00:00", accepted = false),
                DueCase("C-56", "F2-L5 区切りが /", "2026/11/30", accepted = false),
                DueCase("C-56", "F2-L5 区切りなし", "20261130", accepted = false),
                DueCase("C-56", "F2-L5 桁が足りない", "2026-1-5", accepted = false),
                DueCase("C-56", "F2-L5 和暦", "令和8年11月30日", accepted = false),
                DueCase("C-56", "F2-L6 実在しない日（2026-02-30）", "2026-02-30", accepted = false),
                DueCase("C-56", "F2-L6 閏年でない年の 2 月 29 日", "2027-02-29", accepted = false),
                DueCase("C-56", "F2-L6 実在しない月", "2026-13-01", accepted = false),
            ),
        ) { it.verify() }
    }

    context("AC-01-26 YYYY-MM-DD の形で実在する暦日は、期限として受理される（C-58）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                DueCase("C-58", "F2-L7 通常の日", "2026-11-30", accepted = true),
                DueCase("C-58", "F2-L7 閏日", "2024-02-29", accepted = true),
                DueCase("C-58", "F2-L7 下限", "0001-01-01", accepted = true),
                DueCase("C-58", "F2-L7 上限", "9999-12-31", accepted = true),
            ),
        ) { it.verify() }
    }

    // 別のタスクが変わらないことは、Task が不変の値なので層1では自明である。保存した一覧で確かめる部分は Integration に委ねる
    context("AC-02-01 AC-02-08 AC-03-01 AC-03-06 完了にする・未完了に戻す操作は、結果の状態を決め、タイトルと期限を変えない（C-20〜C-23）") {
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                StatusCase("C-20", "F3-L1 × F5-L1 未完了を完了にする", TaskStatus.Open, complete = true, expected = TaskStatus.Done),
                StatusCase("C-21", "F3-L2 × F5-L1 完了を完了にしても変わらない", TaskStatus.Done, complete = true, expected = TaskStatus.Done),
                StatusCase("C-22", "F3-L2 × F5-L2 完了を未完了に戻す", TaskStatus.Done, complete = false, expected = TaskStatus.Open),
                StatusCase("C-23", "F3-L1 × F5-L2 未完了を戻しても変わらない", TaskStatus.Open, complete = false, expected = TaskStatus.Open),
            ),
        ) { it.verify() }
    }

    context("AC-06-01 AC-06-02 AC-06-03 完了済みの表示の選択に従って、一覧に載るタスクが決まる（C-30〜C-32）") {
        val clock = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneOffset.UTC)
        val tasks = listOf(
            task(1, "完了", "2026-10-01", TaskStatus.Done),
            task(2, "未完了", "2026-12-01"),
            task(3, "期限切れ", "2026-10-01"),
        )
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                VisibilityCase("C-30", "F6-L1 表示する: すべて", CompletedVisibility.SHOW, listOf("期限切れ", "未完了", "完了")),
                VisibilityCase("C-31", "F6-L2 隠す: 未完了と期限切れだけ", CompletedVisibility.HIDE, listOf("期限切れ", "未完了")),
                VisibilityCase("C-32", "F6-L3 保存されていない: 表示する", null, listOf("期限切れ", "未完了", "完了")),
            ),
        ) { case ->
            tasks.listed(resolveVisibility(case.stored), clock).map { it.task.title.value } shouldContainExactly case.expected
        }
    }

    context("AC-05-01 AC-05-02 AC-05-03 AC-05-04 AC-05-05 AC-05-07 一覧の並び順（C-40〜C-45）") {
        val mixed = listOf(
            task(1, "C", "2026-11-10"),
            task(2, "A", "2026-10-30"),
            task(3, "E", "2026-11-02", TaskStatus.Done),
            task(4, "B", "2026-11-03"),
            task(5, "D", "2026-10-29", TaskStatus.Done),
        )
        withData(
            nameFn = { "${it.id} ${it.label}" },
            ts = listOf(
                OrderCase(
                    "C-40",
                    "期限が今日以後は近い順",
                    listOf(task(1, "A", "2026-11-10"), task(2, "B", "2026-11-03"), task(3, "C", "2026-11-05")),
                    "2026-11-01",
                    listOf("B", "C", "A"),
                ),
                OrderCase(
                    "C-41",
                    "期限のないタスクは最後",
                    listOf(task(1, "X", null), task(2, "Y", "2026-12-01")),
                    "2026-11-01",
                    listOf("Y", "X"),
                ),
                OrderCase(
                    "C-42",
                    "同じ期限は登録の古い順",
                    listOf(task(2, "Q", "2026-11-05"), task(1, "P", "2026-11-05")),
                    "2026-11-01",
                    listOf("P", "Q"),
                ),
                OrderCase(
                    "C-42",
                    "期限のないタスク同士は登録の古い順",
                    listOf(task(2, "Q", null), task(1, "P", null)),
                    "2026-11-01",
                    listOf("P", "Q"),
                ),
                OrderCase("C-43", "期限切れを含む 今日 2026-11-01", mixed, "2026-11-01", listOf("A", "B", "C", "D", "E")),
                OrderCase("C-43", "期限切れを含む 今日 2026-11-20", mixed, "2026-11-20", listOf("A", "B", "C", "D", "E")),
                OrderCase(
                    "C-44",
                    "未完了の後ろに完了済みがまとまる",
                    listOf(
                        task(1, "A", "2026-11-10"),
                        task(2, "B", "2026-11-02", TaskStatus.Done),
                        task(3, "C", null),
                        task(4, "D", null, TaskStatus.Done),
                        task(5, "E", "2026-11-01", TaskStatus.Done),
                    ),
                    "2026-11-01",
                    listOf("A", "C", "E", "B", "D"),
                ),
                OrderCase(
                    "C-45",
                    "登録時刻が同じなら番号の小さい方が先 P=1 Q=2",
                    listOf(task(1, "P", null, createdAt = Instant.EPOCH), task(2, "Q", null, createdAt = Instant.EPOCH)),
                    "2026-11-01",
                    listOf("P", "Q"),
                ),
                OrderCase(
                    "C-45",
                    "登録時刻が同じなら番号の小さい方が先 P=2 Q=1",
                    listOf(task(2, "P", null, createdAt = Instant.EPOCH), task(1, "Q", null, createdAt = Instant.EPOCH)),
                    "2026-11-01",
                    listOf("Q", "P"),
                ),
            ),
        ) { case ->
            val clock = Clock.fixed(OffsetDateTime.parse("${case.today}T12:00+09:00").toInstant(), ZoneOffset.UTC)
            case.tasks.listed(CompletedVisibility.SHOW, clock).map { it.task.title.value } shouldContainExactly case.expected
        }
    }
})

/** 番号の順に登録時刻が進むタスク。 */
private fun task(
    number: Long,
    name: String,
    dueDate: String?,
    status: TaskStatus = TaskStatus.Open,
    createdAt: Instant = Instant.EPOCH.plusSeconds(number),
) = Task(TaskId(number), title(name), dueDate?.let(::due), status, createdAt)

/** 入力と、保存されるタイトル・長さ、または拒否される理由。[saved] が null なら拒否を期待する。 */
private data class TitleCase(
    val id: String,
    val label: String,
    val raw: String,
    val saved: String? = null,
    val error: TitleError? = null,
    val length: Int? = null,
) {
    fun verify() {
        when (val result = TaskTitle.of(raw)) {
            is Validated.Valid -> {
                result.value.value shouldBe saved
                length?.let { result.value.length() shouldBe it }
            }
            is Validated.Invalid -> result.error shouldBe error
        }
    }
}

private data class DueCase(val id: String, val label: String, val raw: String, val accepted: Boolean) {
    fun verify() {
        when (val result = DueDate.of(raw)) {
            is Validated.Valid -> {
                accepted shouldBe true
                result.value.value.toString() shouldBe raw
            }
            is Validated.Invalid -> {
                accepted shouldBe false
                result.error shouldBe DueDateError.INVALID
            }
        }
    }
}

private data class StatusCase(
    val id: String,
    val label: String,
    val from: TaskStatus,
    val complete: Boolean,
    val expected: TaskStatus,
) {
    fun verify() {
        val before = task(1, "牛乳を買う", "2026-11-30", from)
        val after = if (complete) before.complete() else before.reopen()
        after.status shouldBe expected
        after.copy(status = from) shouldBe before
    }
}

private data class VisibilityCase(val id: String, val label: String, val stored: CompletedVisibility?, val expected: List<String>)

private data class OrderCase(val id: String, val label: String, val tasks: List<Task>, val today: String, val expected: List<String>)
