package com.example.todo.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

/**
 * 識別子の生成の Property。技術的な検証で、AC には紐づけない（層2の対応表は変えないため、テスト名に AC 番号を付けない）。
 * 拒否する値と、0・上限を固定しない理由は、実現例 02-1 の §2.1「サンプルの判断」にある。
 */
class TaskIdTest : StringSpec({
    // 分類: 境界。負の値は拒否される。受理の確認は、0 と上限を避ける
    "識別子は、負の値が拒否され、通常の番号が受理される" {
        (TaskId.of(1L) as Validated.Valid).value.value shouldBe 1L
        (TaskId.of(1_000_000L) as Validated.Valid).value.value shouldBe 1_000_000L
        TaskId.of(-1L) shouldBe Validated.Invalid(TaskIdError.NEGATIVE)
        TaskId.of(Long.MIN_VALUE) shouldBe Validated.Invalid(TaskIdError.NEGATIVE)
    }

    // 分類: ラウンドトリップ（通常の番号は、受理されて同じ値に戻る）
    "1 から 1,000,000 の任意の値は、受理されて同じ値の識別子になる" {
        checkAll(Arb.long(1L..1_000_000L)) { value ->
            (TaskId.of(value) as Validated.Valid).value.value shouldBe value
        }
    }

    // 分類: 不変量（負の値は必ず拒否される）
    "負の任意の値は、NEGATIVE で拒否される" {
        checkAll(Arb.long(Long.MIN_VALUE..-1L)) { value ->
            TaskId.of(value) shouldBe Validated.Invalid(TaskIdError.NEGATIVE)
        }
    }
})
