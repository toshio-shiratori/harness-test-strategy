package com.example.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

/**
 * タイトルの Property。制約表（C-50〜C-57、C-65〜C-68）の各行の例は [TaskConstraintTableTest] にある。
 * 期待値は TRS TODO-001 の AC と制約表を正とする（実装から逆生成しない）。
 * 空白と表示されない文字の範囲は OI-016、両端から除くものは OI-017（OI-016 の保存する値を改めた）、書記素の数えは OI-008、50 字の数えと除去の順は OI-004 の決定。
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

    // 分類: 不変量（途中の表示されない文字は、どの文字でも除かれない。両端は表示される文字なので、前後の除去の対象外）
    "AC-01-25 表示される文字に挟まれた、表示されない文字は、除かれずそのまま残る" {
        checkAll(arbUnits(1..22), arbInvisibleMixed, arbUnits(1..22)) { head, middle, tail ->
            val raw = head.joinToString("") + middle + tail.joinToString("")
            saved(raw) shouldBe raw
        }
    }

    // 分類: 不変量（先頭は最初の表示される文字より前をすべて除く。末尾は、最後の表示される文字より後ろを除く）
    "AC-01-39 前後の空白と表示されない文字の並びは、除かれる" {
        checkAll(arbUnits(1..22), arbRemovedAtStart, arbRemovedAtEdge) { units, before, after ->
            val core = units.joinToString("")
            saved(before + core + after) shouldBe core
            saved(before + core) shouldBe core
            saved(core + after) shouldBe core
        }
    }

    // 分類: 不変量（組になる文字は、表示される文字の直後に続くときだけ残る。その後ろは除かれる。先頭や、除く文字の後ろのものは除かれる）
    "AC-01-40 表示される文字の直後の異体字セレクタとタグ文字は残り、それ以外の位置のものは除かれる" {
        checkAll(arbUnits(1..22), arbKeptAtEdge, arbRemovedAtStart, arbRemovedAfterKept) { units, kept, before, after ->
            val core = units.joinToString("")
            saved(core + kept) shouldBe core + kept
            saved(before + core + kept) shouldBe core + kept
            saved(before + core + kept + after) shouldBe core + kept
            // 先頭や、除く文字の後ろにある組になる文字は、相手が無いので除かれる
            saved(kept + core) shouldBe core
            saved(core + after) shouldBe core
        }
    }

    // 分類: 不変量（字数は両端を除いた後で数える。除く前は 51 字以上でも、除いた後が 50 字なら受理される）
    "AC-01-41 前後の表示されない文字を除いた後の字数で、受理と TOO_LONG が決まる" {
        checkAll(arbUnits(TaskTitle.MAX_LENGTH..TaskTitle.MAX_LENGTH), arbRemovedAtStart, arbRemovedAtEdge) { units, before, after ->
            val core = units.joinToString("")
            saved(before + core + after) shouldBe core
            TaskTitle.of(before + core + "あ" + after) shouldBe Validated.Invalid(TitleError.TOO_LONG)
        }
    }

    // 分類: オラクル（全ての表示されない文字について、表示される文字の直後・先頭・空白の後ろで、残るか除かれるかを、手で写した範囲と照合する）
    "AC-01-39 AC-01-40 Default_Ignorable_Code_Point の 4174 個は、表示される文字の直後では 356 個だけが残り、先頭と空白の後ろでは全部除かれる" {
        keptAtEdgeCodePoints.size shouldBe 356
        val kept = keptAtEdgeCodePoints.toSet()
        defaultIgnorableCodePoints.forEach { codePoint ->
            val invisible = String(Character.toChars(codePoint))
            saved("牛$invisible") shouldBe if (codePoint in kept) "牛$invisible" else "牛"
            saved("${invisible}牛") shouldBe "牛"
            saved("牛 $invisible") shouldBe "牛"
            saved("牛\u200B$invisible") shouldBe "牛"
            saved("牛$invisible\u200B") shouldBe if (codePoint in kept) "牛$invisible" else "牛"
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
