package com.example.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

/**
 * タイトルの Property。制約表（C-50〜C-57、C-65）の各行の例は [TaskConstraintTableTest] にある。
 * 期待値は TRS TODO-001 の AC と制約表を正とする（実装から逆生成しない）。
 * 空白と表示されない文字の範囲は OI-016（OI-002 を置換）、書記素の数えは OI-008、50 字の数えと空白の除去の順は OI-004 の決定。
 */
class TaskTitleTest : StringSpec({
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

    // 分類: 不変量（空白と表示されない文字だけのタイトルは必ず拒否される）
    "AC-01-04 AC-01-05 AC-01-38 White_Space 属性を持つ文字と表示されない文字だけの、任意の並びは BLANK で拒否される" {
        checkAll(arbBlankOrInvisible, arbWhiteSpace, arbInvisible) { blank, space, invisible ->
            TaskTitle.of(blank) shouldBe Validated.Invalid(TitleError.BLANK)
            TaskTitle.of(space) shouldBe Validated.Invalid(TitleError.BLANK)
            TaskTitle.of(space + invisible) shouldBe Validated.Invalid(TitleError.BLANK)
            TaskTitle.of(invisible + space + invisible) shouldBe Validated.Invalid(TitleError.BLANK)
        }
    }

    // 分類: オラクル（独立のオラクルは、Unicode の一覧から手で写した whiteSpaceChars と defaultIgnorableCodePoints。正規表現の \p{IsWhite_Space} は補助）
    "AC-01-05 AC-01-38 全コードポイントのうち、White_Space の 25 文字と Default_Ignorable_Code_Point の 4174 個だけが、1 文字で BLANK になる" {
        val allCodePoints = (Character.MIN_CODE_POINT..Character.MAX_CODE_POINT).toList()
        val rejected = allCodePoints.filter { TaskTitle.of(String(Character.toChars(it))) == Validated.Invalid(TitleError.BLANK) }
        defaultIgnorableCodePoints.size shouldBe 4174
        rejected shouldBe (whiteSpaceChars.map { it.code } + defaultIgnorableCodePoints).sorted()

        val property = Regex("""\p{IsWhite_Space}""")
        val whiteSpaceOnly = rejected - defaultIgnorableCodePoints.toSet()
        whiteSpaceOnly shouldBe allCodePoints.filter { property.matches(String(Character.toChars(it))) }
    }

    // 分類: 不変量（表示される文字があれば、表示されない文字は前後でも除かれない。除くのは前後の空白だけ）
    "AC-01-25 表示される文字があるタイトルの、前後の表示されない文字は除かれず、その外側の空白だけが除かれる" {
        checkAll(arbUnits(1..22), arbInvisible, arbInvisible, arbWhiteSpace, arbWhiteSpace) { units, before, after, spaceBefore, spaceAfter ->
            val core = before + units.joinToString("") + after
            saved(core) shouldBe core
            saved(spaceBefore + core + spaceAfter) shouldBe core
        }
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
