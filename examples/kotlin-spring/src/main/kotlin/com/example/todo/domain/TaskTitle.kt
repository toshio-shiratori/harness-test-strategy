package com.example.todo.domain

import java.text.BreakIterator

enum class TitleError { BLANK, TOO_LONG }

/**
 * タスクのタイトル。前後の空白を除いた後の、書記素（見た目の 1 文字）で 1〜50 字。
 * 空白は Unicode の White_Space 属性を持つ文字で、持たない文字（U+200B など）は空白として扱わない。Unicode 正規化はしない。
 */
@JvmInline
value class TaskTitle private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 50

        fun of(raw: String): Validated<TaskTitle, TitleError> {
            val trimmed = raw.trim { it.isUnicodeWhiteSpace() }
            return if (trimmed.isEmpty()) {
                Validated.Invalid(TitleError.BLANK)
            } else if (trimmed.graphemeCount() > MAX_LENGTH) {
                Validated.Invalid(TitleError.TOO_LONG)
            } else {
                Validated.Valid(TaskTitle(trimmed))
            }
        }
    }
}

/** 見た目の 1 文字（Unicode UAX #29 の拡張書記素クラスタ）を 1 字と数えた長さ。 */
fun TaskTitle.length(): Int = value.graphemeCount()

private fun String.graphemeCount(): Int {
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(this)
    var count = 0
    while (boundaries.next() != BreakIterator.DONE) count++
    return count
}

// White_Space 属性を持つ 25 文字: U+0009〜000D、U+0020、U+0085、U+00A0、U+1680、U+2000〜200A、U+2028、U+2029、U+202F、U+205F、U+3000。
// Kotlin の Char.isWhitespace（trim・isBlank が使う）は、White_Space でない U+001C〜001F を含み、White_Space の U+0085 を含まないので使わない。
// U+0009〜000D と U+0085 以外の 19 文字は、一般カテゴリ Zs・Zl・Zp と一致する
private fun Char.isUnicodeWhiteSpace(): Boolean =
    this in '\u0009'..'\u000D' || this == '\u0085' || Character.isSpaceChar(this)
