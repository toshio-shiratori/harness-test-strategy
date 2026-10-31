package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * 02b §6 / 02a §1.4 のアーキテクチャ検査。違反は本番コードを書き換えた時点でこのテストが失敗する。
 * 対象はドメイン層（`..domain..` パッケージ）の本番コード。
 */
class DomainArchitectureTest : StringSpec({
    val production = Konsist.scopeFromProduction()
    val domain = production.files.filter { it.packagee?.name?.contains(".domain") == true }
    val primitives = setOf("String", "Int", "Long", "Boolean")

    "検査対象のドメイン層ファイルが空ではない（パッケージ構成の変更で素通りしない）" {
        domain.isNotEmpty() shouldBe true
    }

    "ドメイン層のプロパティにプリミティブ型が露出していない" {
        // 例外: value class が内部に保持する値（TaskTitle の value: String 等）と const 定数。
        // value class はまさにプリミティブを包んでドメイン型にするための仕組みで、内部保持は避けられない。
        domain
            .flatMap { it.properties(includeNested = true) }
            .filterNot { it.hasConstModifier }
            .filterNot { (it.containingDeclaration as? KoClassDeclaration)?.hasValueModifier == true }
            .assertFalse { it.type?.name in primitives }
    }

    "ドメイン層の関数・コンストラクタの引数にプリミティブ型が露出していない" {
        // 例外: 外部入力を一度だけパースする境界の `of` ファクトリ（Parse, don't validate）。
        domain
            .flatMap { it.functions(includeNested = true, includeLocal = false) }
            .filterNot { it.name == "of" }
            .flatMap { it.parameters }
            .assertFalse { it.type.name in primitives }
        domain
            .flatMap { it.classes(includeNested = true) }
            .filterNot { it.hasValueModifier }
            .flatMap { it.primaryConstructor?.parameters.orEmpty() }
            .assertFalse { it.type.name in primitives }
    }

    "ドメイン層が Spring に依存しない" {
        domain.assertFalse { file -> file.imports.any { it.name.startsWith("org.springframework") } }
    }

    "sealed / enum の when に else がない" {
        // ドメイン層だけでなく本番コード全体で、when 式の else 分岐を禁止する（網羅性をコンパイラに任せる）。
        production.files
            .flatMap { it.functions(includeNested = true, includeLocal = true) }
            .assertFalse { fn -> fn.text.contains(Regex("""\belse\s*->""")) }
    }
})
