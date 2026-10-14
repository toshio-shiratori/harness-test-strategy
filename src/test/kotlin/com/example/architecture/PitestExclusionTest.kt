package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * 02 §4.4 の検査。`build.gradle.kts` の pitest `excludedClasses` に載ったクラスは、ミューテーションの対象外になる。
 * 除外が黙って広がってゲートが弱まらないよう、列挙されたクラスが「コンパイラ生成アクセサ以外のメンバ（ロジック）を持たない」ことを検査する。
 *
 * 除外リストは `build.gradle.kts` を直接読んで取り出す（テスト用の共有定義は作らない。ビルドスクリプトの構造を変えずに済み、
 * 検査と実際の除外が別の定義に分かれて食い違うこともない）。`excludedClasses` は 1 箇所の `listOf(...)` に文字列リテラルで列挙する。
 * 変数・スプレッド・`.plus(...)` 等の別の書き方は、取り出せない要素を黙って捨てず、失敗にする。
 *
 * 許すのは次の構成だけ。
 * - 主コンストラクタのプロパティ（アクセサはコンパイラが生成する）。引数の既定値は許さない（既定値用の合成コンストラクタが生成される）
 * - 引数なしのスーパータイプ呼び出し。引数つきの呼び出しとインターフェース委譲は許さない
 * - companion object（別の JVM クラス `X$Companion` になり、除外されない。検証ロジックはここに置く）
 *   ただし、外側のクラスにコードを生成させるもの（`init` ブロック・`@JvmStatic`・`const` でないプロパティ）は許さない。
 *   companion の `init` は外側クラスの `static {}` に入ることを `javap` で確認した。
 *
 * 制約: `build.gradle.kts` のコメント除去は正規表現による簡易処理で、文字列リテラルの中にあるブロックコメントの開始記号や、
 * スラッシュ 2 つ（URL の `://` 等）をコメントと誤認する。誤認すると `excludedClasses` の記述が壊れて取り出しに失敗するだけで、
 * 検査が黙って通る方向には働かないが、`excludedClasses` の周辺にそのような文字列を置かないこと。
 *
 * このファイルは CODEOWNERS の対象。検査を弱める変更には人間の承認が要る。
 */
class PitestExclusionTest : StringSpec({
    val production = Konsist.scopeFromProduction()
    val allClasses = production.classes(includeNested = true)

    "build.gradle.kts から excludedClasses を取り出せる（書式の変更で素通りしない）" {
        readExcludedClasses(File("build.gradle.kts").readText()).isNotEmpty() shouldBe true
    }

    "excludedClasses の全てが本番コードに実在する（綴り違いや削除済みのクラスが残らない）" {
        val excluded = readExcludedClasses(File("build.gradle.kts").readText())
        val known = allClasses.mapNotNull { it.fullyQualifiedName }.toSet()
        excluded.filterNot { it in known }.shouldBeEmpty()
    }

    "excludedClasses のクラスは、アクセサ以外のメンバ（ロジック）を持たない" {
        val excluded = readExcludedClasses(File("build.gradle.kts").readText())
        val violations = allClasses
            .filter { it.fullyQualifiedName in excluded }
            .flatMap { cls -> logicMembers(cls).map { "${cls.fullyQualifiedName}: $it" } }
        withClue("ロジックを持つクラスは excludedClasses に入れず、ミューテーションの対象にする:\n${violations.joinToString("\n")}\n") {
            violations.shouldBeEmpty()
        }
    }

    // readExcludedClasses の単体テスト。取り出せない書き方は、要素を黙って捨てずに失敗する
    fun script(body: String) = "pitest {\n    excludedClasses.set($body)\n}\n"

    "readExcludedClasses: 文字列リテラルの listOf を取り出す（\$ のエスケープ・末尾カンマ・コメントを扱う）" {
        readExcludedClasses(
            script(
                """
                listOf(
                    "a.B", // 行コメント
                    /* ブロックコメント */ "a.C\${'$'}D",
                    "a.E\${'$'}F",
                )
                """.trimIndent(),
            ),
        ) shouldContainExactlyInAnyOrder listOf("a.B", "a.C.D", "a.E.F")
    }

    "readExcludedClasses: リテラル以外の要素（変数・スプレッド・plus・+・定数）があると失敗する" {
        listOf(
            """listOf("a.B", other)""",
            """listOf("a.B", *others)""",
            """listOf("a.B").plus(listOf("a.C"))""",
            """listOf("a.B") + listOf("a.C")""",
            """listOf("a.B", NAME)""",
            """listOf("a.B", "a.C" + "D")""",
            """listOf("a.${'$'}{name}")""",
            """others""",
        ).forEach { body ->
            withClue(body) { shouldThrow<IllegalStateException> { readExcludedClasses(script(body)) } }
        }
    }

    "readExcludedClasses: 0 件・2 箇所・excludedClasses が無いと失敗する" {
        shouldThrow<IllegalStateException> { readExcludedClasses(script("listOf()")) }
        shouldThrow<IllegalStateException> { readExcludedClasses(script("""listOf("a.B")""") + script("""listOf("a.C")""")) }
        shouldThrow<IllegalStateException> { readExcludedClasses("pitest {\n}\n") }
        shouldThrow<IllegalStateException> { readExcludedClasses("""excludedClasses.addAll(listOf("a.B"))""") }
    }

    // logicMembers の単体テスト。違反例（config/konsist-fixtures/pitest-exclusion）で、ロジックを持つクラスを検出できることを固定する
    val fixtureClasses = Konsist.scopeFromDirectory("config/konsist-fixtures/pitest-exclusion").classes(includeNested = true)
    val fixtureViolations = fixtureClasses
        .filter { it.name.startsWith("With") }
        .associate { it.name to logicMembers(it) }

    "logicMembers: 適合例は違反なし" {
        val clean = fixtureClasses.filter { it.name.startsWith("Clean") || it.name == "ArgParent" }
        clean.map { it.name } shouldContainExactlyInAnyOrder listOf("CleanValue", "CleanParent", "CleanChild", "ArgParent")
        clean.flatMap { logicMembers(it) }.shouldBeEmpty()
    }

    "logicMembers: 違反例をそれぞれ検出する" {
        val expected = mapOf(
            "WithFunction" to "関数 twice",
            "WithInit" to "init ブロック",
            "WithBodyProperty" to "本体のプロパティ doubled",
            "WithCustomGetter" to "本体のプロパティ doubled",
            "WithDefaultValue" to "既定値",
            "WithSecondaryConstructor" to "副コンストラクタ",
            "WithParentArguments" to "スーパータイプ呼び出しの引数",
            "WithInterfaceDelegation" to "インターフェース委譲",
            "WithNestedClass" to "ネストしたクラス Inner",
            "WithCompanionInit" to "companion の init ブロック",
            "WithCompanionJvmStatic" to "@JvmStatic",
            "WithCompanionProperty" to "const でないプロパティ limit",
        )
        fixtureViolations.keys shouldContainExactlyInAnyOrder expected.keys
        expected.forEach { (name, message) ->
            withClue(name) { fixtureViolations.getValue(name).joinToString() shouldContain message }
        }
    }
})

/**
 * `excludedClasses.set(listOf("a.B", "a.C\$D", ...))` の文字列を、Konsist の完全修飾名（ネストは `.` 区切り）にして返す。
 * `listOf(...)` の中身が文字列リテラルとカンマと空白だけでなければ、取り出せない要素を捨てずに例外にする。
 * 0 件・複数箇所・書式違いも例外にする。
 */
internal fun readExcludedClasses(buildScript: String): Set<String> {
    val withoutComments = buildScript
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
    val occurrences = Regex("""\bexcludedClasses\b""").findAll(withoutComments).count()
    check(occurrences == 1) { "build.gradle.kts の excludedClasses が $occurrences 箇所ある。1 箇所の listOf(...) に限る" }
    // listOf(...) の直後で set(...) が閉じていること（`.plus(...)` や `+` が続く書き方はここで一致しない）
    val list = Regex("""excludedClasses\s*\.set\(\s*listOf\(((?:\s*"(?:[^"\\]|\\.)*"\s*,?)*)\s*\)\s*,?\s*\)""")
        .find(withoutComments)
        ?.groupValues
        ?.get(1)
        ?: error("excludedClasses.set(listOf(...)) を文字列リテラルだけで書いていない（変数・スプレッド・plus 等は読み取れない）")
    val literals = Regex(""""((?:[^"\\]|\\.)*)"""").findAll(list).map { it.groupValues[1] }.toList()
    val rest = list.replace(Regex(""""(?:[^"\\]|\\.)*""""), "").replace(Regex("""[,\s]"""), "")
    check(rest.isEmpty()) { "excludedClasses に文字列リテラル以外の要素がある: $rest" }
    val names = literals.map { literal ->
        check(!literal.replace("\\$", "").contains('$')) { "エスケープされていない \$ を含む: $literal" }
        literal.replace("\\$", "$").replace('$', '.')
    }
    check(names.isNotEmpty()) { "excludedClasses が 0 件" }
    return names.toSet()
}

/** アクセサ以外のメンバを説明する文字列で返す。空ならロジックなし。 */
internal fun logicMembers(cls: KoClassDeclaration): List<String> {
    val found = mutableListOf<String>()
    cls.functions(includeNested = false, includeLocal = false).forEach { found += "関数 ${it.name}" }
    cls.initBlocks.forEach { _ -> found += "init ブロック" }
    cls.secondaryConstructors.forEach { _ -> found += "副コンストラクタ" }
    cls.primaryConstructor?.parameters.orEmpty()
        .filter { it.defaultValue != null }
        .forEach { found += "主コンストラクタ引数 ${it.name} の既定値" }
    cls.parents(indirectParents = false).forEach { parent ->
        if (parent.arguments.isNotEmpty()) found += "スーパータイプ呼び出しの引数 ${parent.name}"
        if (Regex("""\bby\b""").containsMatchIn(parent.text)) found += "インターフェース委譲 ${parent.name}"
    }
    cls.properties(includeNested = false)
        .filterNot { it.isConstructorDefined }
        .forEach { found += "本体のプロパティ ${it.name}（初期化式・getter・delegate はロジックになる）" }
    cls.classes(includeNested = false).forEach { found += "ネストしたクラス ${it.name}" }
    cls.interfaces(includeNested = false).forEach { found += "ネストしたインターフェース ${it.name}" }
    cls.objects(includeNested = false).filterNot { it.hasCompanionModifier }.forEach { found += "ネストしたオブジェクト ${it.name}" }
    cls.companionObject?.let { companion ->
        companion.initBlocks.forEach { _ -> found += "companion の init ブロック（外側のクラスの static 初期化子に入る）" }
        companion.functions(includeNested = false, includeLocal = false)
            .filter { it.hasAnnotationWithName("JvmStatic") }
            .forEach { found += "companion の @JvmStatic 関数 ${it.name}（外側のクラスに生成される）" }
        companion.properties(includeNested = false)
            .filterNot { it.hasConstModifier }
            .forEach { found += "companion の const でないプロパティ ${it.name}（外側のクラスに生成される）" }
    }
    return found
}
