package com.example.architecture

import com.example.architecture.TraceabilityMatrix.Layer
import com.example.architecture.TraceabilityMatrix.TestFile
import com.lemonappdev.konsist.api.Konsist
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * TRS ファイルのトレーサビリティ表（テンプレート §7）が、テストコードから作った表と一致することを検査する（[TraceabilityMatrix]）。
 *
 * 自己テストの AC ID は、`ac(n)` で組み立てる。ソースに `"AC-xx-yy` の形の文字列を書くと、AC に対応するテストの検査（① ⑦）が、
 * このファイルの文字列をテスト名と取り違えるため。検査から外す例外（`selfTestFiles`）は足さない。
 */
class TraceabilityMatrixTest : StringSpec({
    fun ac(n: Int) = "AC-09-%02d".format(n)

    /** テスト名（Kotest の文字列名）の先頭に AC ID を置いた、テストファイルの本文 */
    fun source(vararg ids: String) = ids.joinToString("\n") { "context(\"$it 振る舞い\") { }" }

    fun generated(vararg rows: Pair<String, String>, gherkin: String = "") = buildString {
        appendLine("### 3. 受入条件（AC）")
        appendLine()
        appendLine(AcTable.header.joinToString(" | ", "| ", " |"))
        appendLine(AcTable.header.joinToString(" | ", "| ", " |") { "---" })
        rows.forEach { (id, layer) -> appendLine("| $id | UC-09 | 単体 | $layer | Must | 振る舞い | C-10 |") }
        appendLine()
        appendLine(gherkin)
    }

    fun trs(vararg rows: String) = buildString {
        appendLine("### 7. トレーサビリティ")
        appendLine()
        appendLine(AcTable.traceabilityHeader.joinToString(" | ", "| ", " |"))
        appendLine(AcTable.traceabilityHeader.joinToString(" | ", "| ", " |") { "---" })
        rows.forEach { appendLine(it) }
    }

    val unitTest = TestFile("FooTest", Layer.UNIT, source(ac(1), ac(2)))
    val httpTest = TestFile("BarHttpTest", Layer.INTEGRATION_E2E, source(ac(2)))
    val screenTest = TestFile("BazE2eTest", Layer.INTEGRATION_E2E, source(ac(3)))
    val tests = listOf(unitTest, httpTest, screenTest)
    val gen = generated(ac(1) to "層1", ac(2) to "層1、層2（Integration）", ac(3) to "層2（E2E: happy-path）")
    val ok = trs(
        "| ${ac(1)} | `FooTest::${ac(1)}` | — | — | 実装済 |",
        "| ${ac(2)} | `FooTest::${ac(2)}` | `BarHttpTest::${ac(2)}` | — | 実装済 |",
        "| ${ac(3)} | — | `BazE2eTest::${ac(3)}` | 探索対象 | 実装済 |",
    )

    "実ファイル: TRS ファイルのトレーサビリティ表は、テストコードから作った表と一致する" {
        fun docs(dir: String) = File(dir).listFiles { f -> f.isFile && f.extension == "md" }.orEmpty().sortedBy { it.name }.associate { it.name to it.readText() }
        val trsDocs = docs("docs/trs").filterKeys { !it.startsWith("_") }
        val generatedDocs = docs("docs/trs/generated")
        val testFiles = Konsist.scopeFromTest().files.filterNot { it.name in selfTestFiles }.mapNotNull { f ->
            when {
                "/src/test/" in f.path -> TestFile(f.name, Layer.UNIT, f.text)
                "/src/integrationTest/" in f.path || "/src/e2eTest/" in f.path -> TestFile(f.name, Layer.INTEGRATION_E2E, f.text)
                else -> null
            }
        }
        trsDocs.isNotEmpty() shouldBe true
        testFiles.any { it.layer == Layer.UNIT } shouldBe true
        testFiles.any { it.layer == Layer.INTEGRATION_E2E } shouldBe true
        val violations = trsDocs.flatMap { (name, text) ->
            val pair = generatedDocs[name]
            if (pair == null) listOf("$name: 組の生成物のファイルが無い") else TraceabilityMatrix.violations(text, pair, testFiles).map { "$name: $it" }
        }
        withClue(violations.joinToString("\n")) { violations.shouldBeEmpty() }
    }

    "テストから作った表と一致すれば合格する（層1と層2の両方、層3、状態）" {
        TraceabilityMatrix.violations(ok, gen, tests).shouldBeEmpty()
    }

    "反例: テスト名を手で書き換えると失敗になる" {
        val edited = ok.replace("`FooTest::${ac(1)}`", "`OldTest::${ac(1)}`")
        TraceabilityMatrix.violations(edited, gen, tests).first() shouldContain "${ac(1)}（行"
    }

    "反例: テストが増えたのに表を直していないと失敗になる" {
        val more = tests + TestFile("MoreTest", Layer.UNIT, source(ac(3)))
        TraceabilityMatrix.violations(ok, gen, more).first() shouldContain ac(3)
    }

    "反例: AC の表にある AC の行が、表に無いと失敗になる" {
        val missing = trs("| ${ac(1)} | `FooTest::${ac(1)}` | — | — | 実装済 |")
        val violations = TraceabilityMatrix.violations(missing, gen, tests)
        violations[0] shouldBe "トレーサビリティ表に ${ac(2)} の行が無い（期待: ${ac(2)} | `FooTest::${ac(2)}` | `BarHttpTest::${ac(2)}` | — | 実装済）"
    }

    "反例: AC の表に無い AC の行が、表にあると失敗になる" {
        val extra = ok + "| ${ac(9)} | — | — | — | 実装済 |\n"
        TraceabilityMatrix.violations(extra, gen, tests).first() shouldContain "${ac(9)} が、生成物のファイルの AC の表に無い"
    }

    "反例: 同じ AC の行が重複すると失敗になる" {
        val dup = ok + "| ${ac(1)} | `FooTest::${ac(1)}` | — | — | 実装済 |\n"
        TraceabilityMatrix.violations(dup, gen, tests).first() shouldContain "${ac(1)} が重複している"
    }

    "反例: 列の数が足りない行は失敗になる" {
        val short = trs("| ${ac(1)} | `FooTest::${ac(1)}` | — |", ok.lines().filter { it.startsWith("| ${ac(2)}") || it.startsWith("| ${ac(3)}") }.joinToString("\n"))
        TraceabilityMatrix.violations(short, gen, tests).first() shouldContain "列数が見出しと合わない"
    }

    "反例: 行の並びが AC の表と違うと失敗になる" {
        val reversed = trs(*ok.lines().filter { it.startsWith("| AC-") }.reversed().toTypedArray())
        TraceabilityMatrix.violations(reversed, gen, tests).first() shouldBe "トレーサビリティ表の行の並びが、AC の表と違う"
    }

    "反例: トレーサビリティ表が無い、または 2 つあると失敗になる" {
        TraceabilityMatrix.violations("# 表なし", gen, tests).shouldHaveSize(1)
        TraceabilityMatrix.violations(ok + "\n" + ok, gen, tests).first() shouldContain "2 個ある"
    }

    "違反の最後に、置き換えるべき表の全文が付く" {
        val violations = TraceabilityMatrix.violations(trs(), gen, tests)
        violations.last() shouldContain "| ${ac(2)} | `FooTest::${ac(2)}` | `BarHttpTest::${ac(2)}` | — | 実装済 |"
    }

    "テストが無い AC は、状態が未着手になる" {
        val rows = TraceabilityMatrix.expected(gen, listOf(unitTest))
        rows.single { it.acId == ac(3) }.cells shouldBe listOf(ac(3), "—", "—", "探索対象", "未着手")
    }

    "@pending を付けた AC は、状態が pending（OI-ID）になる。テストがあっても pending である" {
        val pendingGen = generated(ac(1) to "層1", gherkin = "@${ac(1)} @layer:1 @pending:OI-001")
        val rows = TraceabilityMatrix.expected(pendingGen, listOf(unitTest))
        rows.single().state shouldBe "pending（OI-001）"
    }

    "検証層に E2E を含む AC だけが探索対象になる（層2 Integration は対象外）" {
        val rows = TraceabilityMatrix.expected(gen, tests)
        rows.map { it.exploration } shouldBe listOf(false, false, true)
    }

    "1 つのテストファイルが複数の AC に名前を付けるとき、AC ごとに項目になる。クラス名は辞書順に並ぶ" {
        val rows = TraceabilityMatrix.expected(gen, listOf(TestFile("ZedTest", Layer.UNIT, source(ac(2))), unitTest))
        rows.single { it.acId == ac(2) }.unit shouldBe listOf("FooTest::${ac(2)}", "ZedTest::${ac(2)}")
    }

    "名前の先頭が AC ID でない文字列は、テストに数えない" {
        val rows = TraceabilityMatrix.expected(gen, listOf(TestFile("NoneTest", Layer.UNIT, "context(\"説明 ${ac(1)}\") { }")))
        rows.first().state shouldBe "未着手"
    }
})
