package com.example.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/**
 * タイトルの Property。制約表（C-50〜C-57）の各行の例は [TaskConstraintTableTest] にある。
 * 期待値は TRS TODO-001 の AC と制約表を正とする（実装から逆生成しない）。
 * 空白の範囲は OI-002、書記素の数えは OI-008、50 字の数えと空白の除去の順は OI-004 の決定。
 */
class TaskTitleTest : StringSpec({
    val bmp = (Char.MIN_VALUE..Char.MAX_VALUE).toList()

    fun saved(raw: String): String? = (TaskTitle.of(raw) as? Validated.Valid)?.value?.value

    // 分類: 不変量（受理した値は不変条件を満たす。長さは単位の数になる）
    "AC-01-01 任意の 1〜50 個の単位のタイトルは受理され、長さは単位の数になる" {
        checkAll(arbUnits(1..TaskTitle.MAX_LENGTH)) { units ->
            val result = TaskTitle.of(units.joinToString("")) as Validated.Valid
            result.value.length() shouldBe units.size
            result.value.value shouldBe units.joinToString("")
        }
    }

    // 分類: 不変量（範囲外は必ず拒否される）
    "AC-01-02 任意の 51 個以上の単位のタイトルは、前後に空白があっても TOO_LONG で拒否される" {
        checkAll(arbUnits(TaskTitle.MAX_LENGTH + 1..120), arbWhiteSpace, arbWhiteSpace) { units, before, after ->
            TaskTitle.of(before + units.joinToString("") + after) shouldBe Validated.Invalid(TitleError.TOO_LONG)
        }
    }

    // 分類: 不変量（空白だけのタイトルは必ず拒否される）
    "AC-01-04 AC-01-05 White_Space 属性を持つ文字だけで、任意の長さの並びは BLANK で拒否される" {
        checkAll(Arb.element(whiteSpaceChars), arbWhiteSpace) { first, rest ->
            TaskTitle.of(first + rest) shouldBe Validated.Invalid(TitleError.BLANK)
        }
    }

    // 分類: オラクル（独立のオラクルは、Unicode の一覧から手で並べた whiteSpaceChars。正規表現の \p{IsWhite_Space} は補助）
    "AC-01-05 AC-01-25 BMP の全文字のうち、Unicode の White_Space の 25 文字だけが空白として扱われる" {
        val property = Regex("""\p{IsWhite_Space}""")
        val rejected = bmp.filter { TaskTitle.of(it.toString()) == Validated.Invalid(TitleError.BLANK) }
        rejected shouldBe whiteSpaceChars
        rejected shouldBe bmp.filter { property.matches(it.toString()) }
    }

    // 分類: 不変量（前後の空白は除かれ、タイトルは変わらない）
    "AC-01-07 任意のタイトルの前後に任意の White_Space を足しても、除いて保存される" {
        checkAll(arbTitle, arbWhiteSpace, arbWhiteSpace) { valid, before, after ->
            saved(before + valid.value + after) shouldBe valid.value
        }
    }

    // 分類: 不変量（途中の空白は残る）
    "AC-01-07 途中の空白は残る" {
        // 途中の空白も 1 字と数えるので、全体が 50 字以内（22 + 5 + 22 以下）に収まる範囲にする
        checkAll(arbUnits(1..22), arbWhiteSpace, arbUnits(1..22)) { head, middle, tail ->
            val raw = head.joinToString("") + middle + tail.joinToString("")
            saved(raw) shouldBe raw
        }
    }
})
