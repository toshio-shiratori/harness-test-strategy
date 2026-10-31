package com.example.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

/** 期限の Property。制約表（C-56、C-58）の各行の例は [TaskConstraintTableTest] にある。入力形式と範囲は OI-005 の決定。 */
class DueDateTest : StringSpec({
    val rejected = Validated.Invalid(DueDateError.INVALID)

    // 分類: 不変量（実在する暦日を壊した入力は必ず拒否される）
    "AC-01-14 実在する暦日に時刻・空白・符号を足す、または区切りを替える・消すと拒否される" {
        checkAll(arbCalendarDate) { date ->
            val iso = date.toString()
            listOf(
                iso + "T10:00:00",
                iso.replace('-', '/'),
                iso.replace("-", ""),
                " $iso",
                "$iso ",
                "$iso\n",
                "+$iso",
            ).forEach { DueDate.of(it) shouldBe rejected }
        }
    }

    // 分類: ラウンドトリップ（桁を揃えて書いた暦日は、受理されて同じ日付に戻る）
    "AC-01-26 0001-01-01 から 9999-12-31 までの任意の暦日は、桁を揃えて書けば受理され、同じ日付になる" {
        checkAll(arbCalendarDate) { date ->
            val raw = "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
            (DueDate.of(raw) as Validated.Valid).value.value shouldBe date
        }
    }

    // 分類: 境界（月ごとの日数を 1 日超えた日は必ず拒否される）
    "AC-01-14 月ごとの日数を 1 日超えた日は拒否される" {
        checkAll(arbCalendarDate) { date ->
            val days = date.lengthOfMonth()
            DueDate.of("%04d-%02d-%02d".format(date.year, date.monthValue, days + 1)) shouldBe rejected
        }
    }
})
