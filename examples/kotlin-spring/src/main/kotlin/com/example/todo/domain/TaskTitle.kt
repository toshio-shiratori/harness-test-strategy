package com.example.todo.domain

import java.text.BreakIterator

enum class TitleError { BLANK, TOO_LONG }

/**
 * タスクのタイトル。両端の空白と表示されない文字を除いた後の、書記素（見た目の 1 文字）で 1〜50 字。
 * 空白は Unicode の White_Space 属性を持つ文字で、表示されない文字は Default_Ignorable_Code_Point 属性を持つ文字（U+200B など）。
 * 先頭からは、最初の表示される文字（空白でも表示されない文字でもない文字）より前を、すべて除く。
 * 末尾からは、最後の表示される文字より後ろを除く。ただし、その文字の直後に続く、異体字セレクタとタグ文字（OI-017 の範囲）は残す。
 * 直前の文字と組になって意味を持つので、組になる相手が無い位置のものは除く。
 * 途中の空白と表示されない文字は除かず、そのまま保存する。Unicode 正規化はしない。
 * 空白と表示されない文字だけでできたタイトルは、異体字セレクタとタグ文字だけでも、BLANK で拒否する。
 */
@JvmInline
value class TaskTitle private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 50

        fun of(raw: String): Validated<TaskTitle, TitleError> {
            val trimmed = raw.trimEdges()
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

/**
 * 両端の空白と表示されない文字を除く。表示される文字が無ければ、空文字列を返す。
 * 末尾は、最後の表示される文字の直後に続く、組になる文字（異体字セレクタとタグ文字）の連なりだけを残す。
 * 補助面の文字はサロゲートペアなので、`Char` ではなくコードポイント単位で見る。
 */
private fun String.trimEdges(): String {
    var start = 0
    while (start < length && !codePointAt(start).isVisible()) start += Character.charCount(codePointAt(start))
    if (start == length) return ""
    var visibleEnd = length
    while (!codePointBefore(visibleEnd).isVisible()) visibleEnd -= Character.charCount(codePointBefore(visibleEnd))
    var end = visibleEnd
    while (end < length && codePointAt(end).isKeptAfterVisible()) end += Character.charCount(codePointAt(end))
    return substring(start, end)
}

/** 表示される文字。空白でも、表示されない文字でもない。 */
private fun Int.isVisible(): Boolean = !isUnicodeWhiteSpace() && !isDefaultIgnorableCodePoint()

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
private fun Int.isUnicodeWhiteSpace(): Boolean =
    this in '\u0009'.code..'\u000D'.code || this == '\u0085'.code || Character.isSpaceChar(this)

// Default_Ignorable_Code_Point 属性を持つ 4174 コードポイント。Java・Kotlin の標準ライブラリに判定がないので、範囲を持つ。
// 出典は Unicode 15.0.0（JDK 21 が従う版）の DerivedCoreProperties.txt で、Unicode 18.0.0 でも同じ範囲である。1 行が出典の 1 行に当たる。
// 未割り当ての U+2065、U+FFF0〜FFF8、U+E0000、U+E0002〜E001F、U+E0080〜E00FF、U+E01F0〜E0FFF も、この属性を持つ。
private val defaultIgnorableRanges: List<IntRange> = listOf(
    0x00AD..0x00AD, 0x034F..0x034F, 0x061C..0x061C, 0x115F..0x1160, 0x17B4..0x17B5,
    0x180B..0x180D, 0x180E..0x180E, 0x180F..0x180F, 0x200B..0x200F, 0x202A..0x202E,
    0x2060..0x2064, 0x2065..0x2065, 0x2066..0x206F, 0x3164..0x3164, 0xFE00..0xFE0F,
    0xFEFF..0xFEFF, 0xFFA0..0xFFA0, 0xFFF0..0xFFF8, 0x1BCA0..0x1BCA3, 0x1D173..0x1D17A,
    0xE0000..0xE0000, 0xE0001..0xE0001, 0xE0002..0xE001F, 0xE0020..0xE007F, 0xE0080..0xE00FF,
    0xE0100..0xE01EF, 0xE01F0..0xE0FFF,
)

private fun Int.isDefaultIgnorableCodePoint(): Boolean = defaultIgnorableRanges.any { this in it }

// 表示される文字の直後なら、末尾でも除かない、表示されない文字の範囲。直前の文字と組になって意味を持ち、除くと見た目が変わる。
// 異体字セレクタ（Variation_Selector 属性を持つ 260 個。U+180B〜180D、U+180F、U+FE00〜FE0F、U+E0100〜E01EF）は絵文字や漢字の字形を選び、
// タグ文字（U+E0020〜E007F の 96 個）は地域の旗の絵文字を組み立てる。合計は 356 個で、どれも Default_Ignorable_Code_Point に含まれる。
// 出典は Unicode 15.0.0 の PropList.txt。
private val keptAfterVisibleRanges: List<IntRange> =
    listOf(0x180B..0x180D, 0x180F..0x180F, 0xFE00..0xFE0F, 0xE0100..0xE01EF, 0xE0020..0xE007F)

private fun Int.isKeptAfterVisible(): Boolean = keptAfterVisibleRanges.any { this in it }
