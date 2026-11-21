package com.example.property

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * 特定の AC に紐づかない技術的な不変条件のテスト（03-1 §1）。
 * 02-1 §3.3 規則4（実行回数）が、設定と Kotest の挙動で成り立っていることを検証する。
 * -Pproptest.iterations を指定した実行（夜間の pbt ジョブなど）で走る。
 * もう片方の実行では、build.gradle.kts がクラスの完全修飾名で除外する。
 * 期待値は実行の構成ごとのリテラルで書き、環境の値（システムプロパティなど）を入れない。
 * 回数は .github/workflows/nightly-mutation.yml と同じ値で、片方だけを変えると落ちる。
 * -Pproptest.iterations に 10000 以外を指定した実行でも落ちる。
 */
class NightlyIterationCountTest : StringSpec({
    "夜間の実行（-Pproptest.iterations=10000）では、Property の実行回数が 10000 回である" {
        var count = 0

        checkAll(Arb.int()) { count++ }

        count shouldBe 10000
    }
})
