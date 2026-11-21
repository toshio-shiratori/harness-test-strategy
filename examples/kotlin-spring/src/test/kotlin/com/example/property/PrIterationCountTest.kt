package com.example.property

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * 特定の AC に紐づかない技術的な不変条件のテスト（03-1 §1）。
 * 02-1 §3.3 規則4（実行回数）が、設定と Kotest の挙動で成り立っていることを検証する。
 * PR の実行や夜間の pitest など、-Pproptest.iterations を指定しない実行で走る。
 * もう片方の実行では、build.gradle.kts がクラスの完全修飾名で除外する。
 * 期待値は実行の構成ごとのリテラルで書き、環境の値（システムプロパティなど）を入れない。
 */
class PrIterationCountTest : StringSpec({
    "PR の実行では、Property の実行回数が Kotest の既定の 1000 回である" {
        var count = 0

        checkAll(Arb.int()) { count++ }

        count shouldBe 1000
    }
})
