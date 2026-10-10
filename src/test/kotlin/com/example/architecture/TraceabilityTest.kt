package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * 03 §3.4 / 01 の G5: TRS の AC に紐づくテストが無ければ失敗させる。
 * テスト名は先頭に `AC-xx-yy` を置く（複数なら空白区切り）。
 * AC ID は TRS 間で重複させない（別の TRS のテストで満たされたように見えるのを防ぐ。03 §3.4）。
 * Blocking の Open Issue により期待値が未確定の AC は、Gherkin のタグに `@pending:<OI-ID>` を付けて除外する。
 * このファイルは CODEOWNERS の対象（`@pending` の乱用や検査の弱体化を人間がレビューする）。
 */
class TraceabilityTest : StringSpec({
    val trsFiles = File("docs/testing/trs").listFiles { f -> f.extension == "md" && !f.name.startsWith("_") }.orEmpty()
    val acRow = Regex("""^\|\s*(AC-\d\d-\d\d)\s*\|""", RegexOption.MULTILINE)
    val pendingTag = Regex("""@(AC-\d\d-\d\d)\b[^\n]*@pending:""")

    // テスト名（Kotest の文字列名・JUnit のバッククォート名）の先頭に置かれた AC ID
    val testSources = Konsist.scopeFromTest().files.filterNot { it.name == "TraceabilityTest" }.joinToString("\n") { it.text }
    fun hasTest(ac: String) = Regex("""["`](?:AC-\d\d-\d\d )*$ac\b""").containsMatchIn(testSources)

    "TRS が1つ以上存在する（ディレクトリの移動で素通りしない）" {
        trsFiles.isNotEmpty() shouldBe true
    }

    "AC ID が複数の TRS で重複していない（別機能のテストで AC が満たされたように見えるのを防ぐ）" {
        val duplicated = trsFiles
            .flatMap { trs -> acRow.findAll(trs.readText()).map { it.groupValues[1] to trs.name }.toSet() }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .map { (ac, files) -> "$ac: ${files.joinToString()}" }
        duplicated.shouldBeEmpty()
    }

    "pending でない全ての AC に、先頭に AC ID を持つテストが紐づいている" {
        val missing = trsFiles.flatMap { trs ->
            val text = trs.readText()
            val pending = pendingTag.findAll(text).map { it.groupValues[1] }.toSet()
            acRow.findAll(text).map { it.groupValues[1] }.toSet()
                .filter { it !in pending && !hasTest(it) }
                .map { "${trs.name}: $it" }
        }
        missing.shouldBeEmpty()
    }
})
