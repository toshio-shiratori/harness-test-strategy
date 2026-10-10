package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * 01 §4 / 04 §3.4 / 02 §6 のテスト保護。テストの無効化（スキップ）を検出して失敗させる。
 * 意図的に無効化する場合は 04 §3.4.1 の手順（理由・期限・人間の承認）を踏み、
 * 承認された箇所を [allowed] に追加する（このファイルは CODEOWNERS の対象）。
 */
class TestProtectionTest : StringSpec({
    // 承認済みの無効化。ファイル名を、理由・期限つきで列挙する。現在は 0 件。
    val allowed = emptySet<String>()

    val tests = Konsist.scopeFromTest().files.filterNot { it.name == "TestProtectionTest" }

    val annotationRegex = Regex("""@(Disabled\w*|Ignore[d]?)\b""")
    // Kotest: xdescribe / xcontext / xit / xtest / xgiven / xwhen / xthen、テスト名先頭の "!"、enabled = false
    val kotestDisabling = Regex("""\bx(describe|context|it|test|should|given|when|then|expect|feature|scenario)\b|\(\s*"!|^\s*"!|enabled\s*=\s*false|\.config\([^)]*enabled""")

    "検査対象のテストファイルが空ではない" {
        tests.isNotEmpty() shouldBe true
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
