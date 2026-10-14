package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * 02b §3.2 の実行境界。`./gradlew test`（層1）は Spring・Docker 不要に保ち、
 * Spring コンテキストを起動するテストと Testcontainers は `src/integrationTest`（と、実配線でブラウザを使う `src/e2eTest`）にだけ置く。
 * ブラウザ操作（Playwright）は `src/e2eTest` にだけ置き、層1と `src/integrationTest` には入れない。
 * このファイルは CODEOWNERS の対象。境界を緩める変更（禁止パッケージの削除等）は人間が承認する。
 */
class ExecutionBoundaryTest : StringSpec({
    // コンテキスト起動・コンテナ起動に使う API。org.springframework.http / web 等の「型だけ」の利用は許可する
    val forbiddenPackages = listOf(
        "org.springframework.boot",
        "org.springframework.test",
        "org.springframework.context",
        "org.springframework.beans.factory.annotation",
        "org.testcontainers",
    )
    val forbiddenAnnotations =
        Regex("""@(SpringBootTest|WebMvcTest|DataJdbcTest|JdbcTest|MybatisTest|Testcontainers|ServiceConnection)\b""")

    fun usesContextOrContainer(file: KoFileDeclaration) =
        file.imports.any { import -> forbiddenPackages.any { import.name.startsWith(it) } } ||
            forbiddenAnnotations.containsMatchIn(file.text)

    fun inE2eSourceSet(file: KoFileDeclaration) = "/src/e2eTest/" in file.path

    // Spring / Testcontainers を使ってよい source set（統合テストと E2E）
    fun inContextSourceSet(file: KoFileDeclaration) = "/src/integrationTest/" in file.path || inE2eSourceSet(file)

    fun usesBrowser(file: KoFileDeclaration) = file.imports.any { it.name.startsWith("com.microsoft.playwright") }

    // 違反の一覧（空であるべき）。fixtures の違反例でも同じ関数を使い、検出できることを確認する
    fun layer1Violations(files: List<KoFileDeclaration>) =
        files.filterNot(::inContextSourceSet).filter(::usesContextOrContainer).map { it.name }

    fun misplacedIntegrationTests(files: List<KoFileDeclaration>) =
        files.filter(::usesContextOrContainer).filterNot(::inContextSourceSet).map { it.name }

    fun misplacedBrowserTests(files: List<KoFileDeclaration>) =
        files.filter(::usesBrowser).filterNot(::inE2eSourceSet).map { it.name }

    // 禁止パッケージ名の文字列を含むため、このファイル自身は走査から除く
    val testFiles = Konsist.scopeFromTest().files.filterNot { it.name == "ExecutionBoundaryTest" }

    "層1（src/test）のテストが Spring のコンテキスト起動や Testcontainers を使わない" {
        layer1Violations(testFiles).shouldBeEmpty()
    }

    "Spring / Testcontainers を使うテストは src/integrationTest か src/e2eTest にだけ置かれている" {
        misplacedIntegrationTests(testFiles).shouldBeEmpty()
    }

    "ブラウザ操作（Playwright）を使うテストは src/e2eTest にだけ置かれている（層1と統合テストに入れない）" {
        misplacedBrowserTests(testFiles).shouldBeEmpty()
    }

    "検査の健全性: 層1・統合テスト・E2E のすべてに検査対象のファイルがある" {
        testFiles.any { "/src/test/" in it.path } shouldBe true
        testFiles.any { "/src/integrationTest/" in it.path } shouldBe true
        testFiles.any(::inE2eSourceSet) shouldBe true
        testFiles.any { inE2eSourceSet(it) && usesBrowser(it) } shouldBe true // E2E が実際にブラウザを使っている
    }

    "違反例（fixtures）を検出できる: 層1に置かれた Spring / Testcontainers の利用は違反になる" {
        val fixtures = Konsist.scopeFromDirectory("config/konsist-fixtures/boundary").files
        fixtures.map { it.name } shouldContainExactlyInAnyOrder
            listOf("Layer1WithSpringTest", "Layer1WithTestcontainersTest", "Layer1WithPlaywrightTest", "PureLayer1Test")
        layer1Violations(fixtures) shouldContainExactlyInAnyOrder
            listOf("Layer1WithSpringTest", "Layer1WithTestcontainersTest")
        misplacedIntegrationTests(fixtures) shouldContainExactlyInAnyOrder
            listOf("Layer1WithSpringTest", "Layer1WithTestcontainersTest")
        misplacedBrowserTests(fixtures) shouldContainExactlyInAnyOrder listOf("Layer1WithPlaywrightTest")
    }
})
