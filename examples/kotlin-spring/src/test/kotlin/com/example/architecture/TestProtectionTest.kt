package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * 01 §4 / 04 §3.4 / 02-2 §3 のテスト保護。テストの無効化（スキップ）を検出して失敗させる。
 * 意図的に無効化する場合は 04 §3.4.1 の手順（理由・期限・人間の承認）を踏み、
 * 承認された箇所を [allowed] に追加する（このファイルは CODEOWNERS の対象）。
 */
class TestProtectionTest : StringSpec({
    // 承認済みの無効化。ファイル名を、理由・期限つきで列挙する。現在は 0 件。
    val allowed = emptySet<String>()

    // 検査から外す置き場（02-2 §1.4）は、このファイル自身の 1 ファイルだけである。
    // ① 理由: このファイルは検査対象の記法（@Disabled、xdescribe 等）を、テスト名とコメントに含むため、
    //    走査すると常に失敗する。ファイル名でなく src/test 内のパスで特定し、同名の別ファイルを巻き込まない。
    // ② 取り違えの検査: 下の「除外は…だけ」と「除外の判定は…」のテストが、除外が広がると失敗させる。
    // ③ 承認の記録: この除外は、CODEOWNERS の承認を経た PR で追加した。追加・変更も同じ承認を要する。
    val selfPath = "/src/test/kotlin/com/example/architecture/TestProtectionTest.kt"
    fun isExcluded(path: String) = path.endsWith(selfPath)

    val allFiles = Konsist.scopeFromTest().files
    val excluded = allFiles.filter { isExcluded(it.path) }
    val tests = allFiles.filterNot { isExcluded(it.path) }

    val annotationRegex = Regex("""@(Disabled\w*|Ignore[d]?)\b""")
    // Kotest: xdescribe / xcontext / xit / xtest / xgiven / xwhen / xthen、テスト名先頭の "!"、enabled = false
    val kotestDisabling = Regex("""\bx(describe|context|it|test|should|given|when|then|expect|feature|scenario)\b|\(\s*"!|^\s*"!|enabled\s*=\s*false|\.config\([^)]*enabled""")

    "検査対象のテストファイルが空ではない" {
        tests.isNotEmpty() shouldBe true
    }

    "Integration（src/integrationTest）も検査対象に含まれる（source set の分離で検査から外れない）" {
        tests.any { "/src/integrationTest/" in it.path } shouldBe true
    }

    "E2E（src/e2eTest）も検査対象に含まれる（source set の分離で検査から外れない）" {
        tests.any { "/src/e2eTest/" in it.path } shouldBe true
    }

    "除外は TestProtectionTest 自身の 1 ファイルだけで、ほかのテストを巻き込まない（02-2 §1.4 の取り違えの検査）" {
        excluded.map { it.path.substringAfterLast("/src/") } shouldBe listOf("test/kotlin/com/example/architecture/TestProtectionTest.kt")
        tests.size shouldBe allFiles.size - 1
        // 同じ architecture の検査ファイルと、層1のほかのテストが走査対象に残っている
        tests.any { "/src/test/kotlin/com/example/architecture/" in it.path } shouldBe true
        tests.any { "/src/test/" in it.path && "/architecture/" !in it.path } shouldBe true
    }

    "除外の判定は、同名でも別の置き場のファイルに効かない" {
        isExcluded("/p/src/test/kotlin/com/example/architecture/TestProtectionTest.kt") shouldBe true
        isExcluded("/p/src/integrationTest/kotlin/com/example/architecture/TestProtectionTest.kt") shouldBe false
        isExcluded("/p/src/e2eTest/kotlin/com/example/TestProtectionTest.kt") shouldBe false
        isExcluded("/p/src/main/kotlin/com/example/TestProtectionTest.kt") shouldBe false
        isExcluded("/p/src/test/kotlin/com/example/architecture/PitestExclusionTest.kt") shouldBe false
    }

    "無効化アノテーション（@Disabled / @Ignore 等）がない" {
        // 関数・クラス・ファイルのどこに付いても検出できるよう、ソーステキストで検査する
        tests.assertFalse { file ->
            file.text.lines().any { annotationRegex.containsMatchIn(it) } && file.name !in allowed
        }
    }

    "Kotest の無効化（xdescribe・! 接頭辞・enabled = false）がない" {
        tests.assertFalse { file ->
            file.text.lines().any { kotestDisabling.containsMatchIn(it) } && file.name !in allowed
        }
    }
})
