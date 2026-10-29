package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * 実行されないテストの検出（01 §5 の G5 を「宣言されたテストが実際に走る」ことで支える）。
 * テストは source set ごとに別のエンジンで走る。エンジンが無い書き方のテストは、コンパイルが通れば、`./gradlew <task>` で黙って 0 件になる。
 *
 * 実行環境（`build.gradle.kts` と `./gradlew dependencies --configuration <実行時クラスパス>` で確認した構成）:
 * - 層1（`src/test`）: Kotest のランナー（kotest-runner-junit5）だけ。`junit-jupiter-api` は推移的に入るが、Jupiter のエンジンは無い。
 * - `src/integrationTest`・`src/e2eTest`: `useJUnitJupiter()` で Jupiter のエンジンがある。JUnit 5 の `@Test` は実行される。
 *   Kotest は kotest-assertions-core だけで、ランナーは無い。
 *
 * 現在の構成でコンパイルが通り、かつ黙って実行されないのは、層1の `junit-jupiter-api` の注釈（`@Test` / `@RepeatedTest` / `@TestFactory` / `@TestTemplate`）である。
 * 次は現在の依存ではコンパイルが通らない（`@ParameterizedTest` は junit-jupiter-params、JUnit 4 は junit:junit、
 * 統合テスト・E2E の Kotest の Spec は kotest-framework が、それぞれクラスパスに無い）。
 * 依存が後から足されると、コンパイルが通るのにエンジンが無いまま黙って 0 件になるので、先に違反として固定しておく。
 *
 * 検査する規則:
 * 1. 層1に JUnit のテスト注釈（JUnit 5 の `@Test` / `@ParameterizedTest` / `@TestFactory` / `@RepeatedTest` / `@TestTemplate` と、JUnit 4 の `org.junit.*`）を置かない。
 *    主な目的は、`junit-jupiter-api` の注釈が黙って実行されないことの検出である。
 * 2. 全 source set で、エンジンの無い JUnit 4（`org.junit.Test` 等）を使わない。
 * 3. `src/integrationTest`・`src/e2eTest` に Kotest の Spec（`io.kotest.core.spec`）を置かない。
 *    integrationTest / e2eTest に Kotest のランナーを足すなら、この規則 3 を見直す。
 *
 * 制約:
 * - 層1では Kotest の AnnotationSpec を使わない（この検査が違反にする）。AnnotationSpec は import なしで入れ子の `@Test` を使うため、JUnit の `@Test` と区別しない。
 * - 検査は行単位。行コメントとブロックコメントの記号で始まる行は除く。除くのは行頭だけなので、ブロックコメントを閉じた同じ行の
 *   後ろに置いた注釈は検出できず、`@[Test]` のような角括弧つきの注釈も検出できない。
 * - 文字列リテラルの中の注釈名は区別しない（誤検出は、書き換えで避けられる側に倒す）。
 * - 「Kotest の Spec クラスが全て結果を出したか」はテスト実行の外（レポート）でしか分からないので、ここでは扱わない。
 *
 * このファイルは CODEOWNERS の対象。検査を弱める変更（注釈の削除・対象 source set の除外等）には人間の承認が要る。
 */
class UnrunTestDetectionTest : StringSpec({
    // JUnit 5 のテスト注釈。完全修飾名（import・直接参照）と、import 済みの単純名の `@Xxx` の両方を見る
    val junit5Qualified =
        Regex("""\borg\.junit\.jupiter\.(api\.(Test|RepeatedTest|TestFactory|TestTemplate)|params\.ParameterizedTest)\b""")
    val simpleAnnotation = Regex("""@(Test|ParameterizedTest|TestFactory|RepeatedTest|TestTemplate)\b""")

    // JUnit 4 は `org.junit.` 直下（Jupiter は org.junit.jupiter、Platform は org.junit.platform なので除く）
    val junit4 = Regex("""\borg\.junit\.(?!jupiter\b|platform\b)\w""")
    val kotestSpec = Regex("""\bio\.kotest\.core\.spec\b""")

    fun codeLines(file: KoFileDeclaration) = file.text.lines().filterNot {
        val t = it.trim()
        t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
    }

    fun usesJUnit5TestAnnotation(file: KoFileDeclaration) =
        codeLines(file).any { junit5Qualified.containsMatchIn(it) || simpleAnnotation.containsMatchIn(it) }

    fun usesJUnit4(file: KoFileDeclaration) = codeLines(file).any { junit4.containsMatchIn(it) }

    fun usesKotestSpec(file: KoFileDeclaration) = codeLines(file).any { kotestSpec.containsMatchIn(it) }

    fun inLayer1(file: KoFileDeclaration) = "/src/test/" in file.path

    fun inJUnitSourceSet(file: KoFileDeclaration) = "/src/integrationTest/" in file.path || "/src/e2eTest/" in file.path

    // 違反の一覧（空であるべき）。source set の絞り込みは呼び出し側で行い、fixtures の違反例でも同じ関数を使って検出できることを確認する
    fun unrunInLayer1(layer1Files: List<KoFileDeclaration>) =
        layer1Files.filter { usesJUnit5TestAnnotation(it) || usesJUnit4(it) }.map { it.name }

    fun unrunJUnit4(files: List<KoFileDeclaration>) = files.filter(::usesJUnit4).map { it.name }

    fun unrunKotestSpec(junitSourceSetFiles: List<KoFileDeclaration>) =
        junitSourceSetFiles.filter(::usesKotestSpec).map { it.name }

    // 注釈名の文字列を含むため、このファイル自身は走査から除く
    val testFiles = Konsist.scopeFromTest().files.filterNot { it.name == "UnrunTestDetectionTest" }

    "層1（src/test）に JUnit のテスト注釈がない（Jupiter のエンジンが無く、実行されない）" {
        unrunInLayer1(testFiles.filter(::inLayer1)).shouldBeEmpty()
    }

    "どの source set でも JUnit 4 を使わない（Vintage のエンジンが無く、実行されない）" {
        unrunJUnit4(testFiles).shouldBeEmpty()
    }

    "src/integrationTest・src/e2eTest に Kotest の Spec がない（Kotest のエンジンが無く、実行されない）" {
        unrunKotestSpec(testFiles.filter(::inJUnitSourceSet)).shouldBeEmpty()
    }

    "検査の健全性: 層1・統合テスト・E2E のすべてに検査対象のファイルがある" {
        testFiles.any(::inLayer1) shouldBe true
        testFiles.any { "/src/integrationTest/" in it.path } shouldBe true
        testFiles.any { "/src/e2eTest/" in it.path } shouldBe true
    }

    "検査の健全性: 実行される書き方（層1の Kotest・統合テストと E2E の JUnit 5）は違反にならない" {
        // 統合テスト・E2E は JUnit 5 の @Test を実際に使っている。それが違反に数えられていないこと（検査が広すぎない）
        testFiles.filter(::inJUnitSourceSet).any(::usesJUnit5TestAnnotation) shouldBe true
        testFiles.filter(::inLayer1).any(::usesKotestSpec) shouldBe true
    }

    "違反例（fixtures）を検出できる: 層1の JUnit 注釈・JUnit 4・統合テストの Kotest Spec は違反になる" {
        val layer1 = Konsist.scopeFromDirectory("config/konsist-fixtures/unrun-tests/layer1").files
        val junitSourceSet = Konsist.scopeFromDirectory("config/konsist-fixtures/unrun-tests/integration").files
        layer1.map { it.name } shouldContainExactlyInAnyOrder
            listOf(
                "Layer1JUnit5Test",
                "Layer1ParameterizedTest",
                "Layer1QualifiedAnnotationTest",
                "Layer1JUnit4Test",
                "Layer1AliasImportTest",
                "Layer1StarImportTest",
                "Layer1RepeatedTest",
                "Layer1AnnotationSpecTest",
                "PureKotestTest",
                "CommentOnlyTest",
            )
        junitSourceSet.map { it.name } shouldContainExactlyInAnyOrder
            listOf("IntegrationJUnit5Test", "IntegrationJUnit4Test", "IntegrationKotestSpecTest")

        unrunInLayer1(layer1) shouldContainExactlyInAnyOrder
            listOf(
                "Layer1JUnit5Test",
                "Layer1ParameterizedTest",
                "Layer1QualifiedAnnotationTest",
                "Layer1JUnit4Test",
                "Layer1AliasImportTest",
                "Layer1StarImportTest",
                "Layer1RepeatedTest",
                "Layer1AnnotationSpecTest",
            )
        unrunJUnit4(layer1 + junitSourceSet) shouldContainExactlyInAnyOrder listOf("Layer1JUnit4Test", "IntegrationJUnit4Test")
        unrunKotestSpec(junitSourceSet) shouldContainExactlyInAnyOrder listOf("IntegrationKotestSpecTest")
    }
})
