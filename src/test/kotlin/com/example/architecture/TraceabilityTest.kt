package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * TRS の Open Issues 表（6. Open Issues）を読み、未解決の Blocking な Open Issue が指す AC に
 * `@pending:<OI-ID>` が付いているかを検査する（01 §5 の G5、03 §3.4）。
 *
 * - 区分は `Blocking` / `Non-blocking`、状態は 03 §4.2 の 5 値（`Open` / `In Discussion` / `Resolved` /
 *   `Superseded` / `Won't Fix`。後ろの 2 つは `（置換先・理由）` の接尾辞を付けてよい）に分類する。
 *   どれにも当たらない値（`open`、`**Blocking**` など）は、分類できないので違反とする。
 * - 未解決 = 区分が `Blocking` で、状態が `Open` または `In Discussion`。
 * - 「指す AC」= 関連列の `AC-xx-yy`。範囲は `AC-03-01〜04` と `AC-03-01〜AC-03-04`（`〜` は `～` `~` も可）だけを展開する。
 *   範囲として読めない `〜` が残る場合は違反とする。関連列に AC が無い行は、指す先を決められないので違反とする。
 * - 該当 AC の Gherkin のタグ行に、その行の OI-ID と同じ `@pending:<OI-ID>` が無ければ違反。
 * - Open Issues 表に行が 1 つも無い TRS は、読み取りの空振りと区別できないので違反とする。
 */
internal object UnresolvedBlockingCheck {
    private val unresolvedStates = setOf("Open", "In Discussion")
    private val resolvedState = Regex("""^(Resolved|Superseded(?:[（(].+[）)])?|Won't Fix(?:[（(].+[）)])?)$""")
    private val kinds = setOf("Blocking", "Non-blocking")
    private val acRange = Regex("""AC-(\d\d)-(\d\d)(?:[〜～~](?:AC-\1-)?(\d\d))?""")
    private val rangeMark = Regex("""[〜～~]""")
    private val acRow = Regex("""^\|\s*(AC-\d\d-\d\d)\s*\|""", RegexOption.MULTILINE)

    class Row(val id: String, val kind: String, val state: String, val related: String) {
        val blocking get() = kind == "Blocking"
        val unresolved get() = state in unresolvedStates
    }

    private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private fun isSeparator(line: String) = cells(line).all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }

    /** Open Issues 表のデータ行と、表が読めなかった理由（見出しが無い・列数が合わない） */
    fun parse(text: String): Pair<List<Row>, List<String>> {
        val lines = text.lines()
        val header = lines.indexOfFirst { l ->
            l.startsWith("|") && cells(l).let { "ID" in it && "区分" in it && "状態" in it && "関連" in it }
        }
        if (header < 0) return emptyList<Row>() to listOf("Open Issues 表の見出し（ID・区分・関連・状態）が見つからない")
        val names = cells(lines[header])
        val body = lines.drop(header + 1).takeWhile { it.startsWith("|") }.filterNot(::isSeparator)
        val errors = mutableListOf<String>()
        val rows = body.mapNotNull { line ->
            val c = cells(line)
            if (c.size != names.size) {
                errors += "Open Issues 表の列数が見出しと合わない: ${c.first()}"
                null
            } else {
                fun col(name: String) = c[names.indexOf(name)]
                Row(col("ID"), col("区分"), col("状態"), col("関連"))
            }
        }
        return rows to errors
    }

    /** 関連列が指す AC。範囲として読めない区切りが残る、または範囲が逆順のときは、第 2 要素に理由を返す */
    private fun targets(related: String): Pair<List<String>, String?> {
        var reversed = false
        val acs = acRange.findAll(related).flatMap { m ->
            val (group, from, to) = m.destructured
            val last = to.ifEmpty { from }.toInt()
            if (last < from.toInt()) reversed = true
            (from.toInt()..last).map { "AC-$group-%02d".format(it) }
        }.toList()
        val leftover = rangeMark.containsMatchIn(acRange.replace(related, ""))
        val problem = when {
            leftover -> "関連列の範囲表記を読めない（AC-xx-yy〜zz または AC-xx-yy〜AC-xx-zz の形にする）: $related"
            reversed -> "関連列の範囲が逆順: $related"
            else -> null
        }
        return acs to problem
    }

    /** 違反の説明。空なら合格 */
    fun violations(text: String): List<String> {
        val (rows, errors) = parse(text)
        val acs = acRow.findAll(text).map { it.groupValues[1] }.toSet()
        val classification = rows.flatMap { oi ->
            listOfNotNull(
                "${oi.id}: 区分を分類できない（Blocking / Non-blocking のどちらかにする）: ${oi.kind}".takeIf { oi.kind !in kinds },
                "${oi.id}: 状態を分類できない（03 §4.2 の 5 値のどれかにする）: ${oi.state}"
                    .takeIf { !oi.unresolved && !resolvedState.matches(oi.state) },
            )
        }
        val emptyTable = if (rows.isEmpty() && errors.isEmpty()) listOf("Open Issues 表に行が 1 つも無い") else emptyList()
        val found = rows.filter { it.blocking && it.unresolved }.flatMap { oi ->
            val (acList, problem) = targets(oi.related)
            when {
                problem != null -> listOf("${oi.id}: $problem")
                acList.isEmpty() -> listOf("${oi.id}: 未解決の Blocking だが、関連列に AC が無い（指す AC を書く）")
                else -> acList.mapNotNull { ac ->
                    when {
                        ac !in acs -> "${oi.id}: 関連列の $ac が AC 表に無い"
                        !Regex("""@$ac\b[^\n]*@pending:${Regex.escape(oi.id)}\b""").containsMatchIn(text) ->
                            "${oi.id}: 未解決の Blocking が指す $ac に @pending:${oi.id} が無い"
                        else -> null
                    }
                }
            }
        }
        return errors + emptyTable + classification + found
    }
}

/**
 * 03 §3.4 / 01 の G5: TRS の AC に紐づくテストが無ければ失敗させる。
 * テスト名は先頭に `AC-xx-yy` を置く（複数なら空白区切り）。
 * AC ID は TRS 間で重複させない（別の TRS のテストで満たされたように見えるのを防ぐ。03 §3.4）。
 * Blocking の Open Issue により期待値が未確定の AC は、Gherkin のタグに `@pending:<OI-ID>` を付けて除外する。
 * 未解決の Blocking な Open Issue が指す AC に `@pending:<OI-ID>` が無ければ失敗させる（[UnresolvedBlockingCheck]）。
 * このファイルは CODEOWNERS の対象（`@pending` の乱用や検査の弱体化を人間がレビューする）。
 */
class TraceabilityTest : StringSpec({
    val trsFiles = File("docs/testing/trs").listFiles { f -> f.extension == "md" && !f.name.startsWith("_") }.orEmpty()
    val acRow = Regex("""^\|\s*(AC-\d\d-\d\d)\s*\|""", RegexOption.MULTILINE)
    val pendingTag = Regex("""@(AC-\d\d-\d\d)\b[^\n]*@pending:""")

    // テスト名（Kotest の文字列名・JUnit のバッククォート名）の先頭に置かれた AC ID
    val testSources = Konsist.scopeFromTest().files.filterNot { it.name == "TraceabilityTest" }.joinToString("\n") { it.text }
    fun hasTest(ac: String) = Regex("""["`](?:AC-\d\d-\d\d )*$ac\b""").containsMatchIn(testSources)

    "統合テスト（src/integrationTest）のテスト名も AC の紐づけ検査の対象に含まれる" {
        // 層2（Integration）の AC は src/integrationTest のテストに紐づく。ここが検査範囲から外れると、移した AC が「テスト無し」と誤判定される
        Konsist.scopeFromTest().files.any { "/src/integrationTest/" in it.path } shouldBe true
        testSources.contains("class OrderControllerHttpTest") shouldBe true // src/integrationTest のテスト本文が照合対象に入っている
    }

    "E2E（src/e2eTest）のテスト名も AC の紐づけ検査の対象に含まれる" {
        // E2E のテストが検査範囲から外れると、E2E にだけ紐づく AC が「テスト無し」と誤判定される（逆に、E2E の AC 名が検査されなくなる）
        Konsist.scopeFromTest().files.any { "/src/e2eTest/" in it.path } shouldBe true
        testSources.contains("class OrderFormE2eTest") shouldBe true // src/e2eTest のテスト本文が照合対象に入っている
    }

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

    "TRS の Open Issues 表を読めている（表の書式変更で検査が素通りしない）" {
        trsFiles.forEach { trs ->
            val text = trs.readText()
            val (rows, errors) = UnresolvedBlockingCheck.parse(text)
            errors.shouldBeEmpty()
            rows.isNotEmpty() shouldBe true
            rows.size shouldBe Regex("""^\|\s*OI-\d+\s*\|""", RegexOption.MULTILINE).findAll(text).count()
        }
    }

    "TRS のテンプレートも G5 の検査を通る（未解決の Blocking の例に @pending が付いている）" {
        UnresolvedBlockingCheck.violations(File("docs/testing/trs/_template.md").readText()).shouldBeEmpty()
    }

    "未解決の Blocking な Open Issue が指す AC に、その OI-ID の @pending が付いている（G5）" {
        val violations = trsFiles.flatMap { trs -> UnresolvedBlockingCheck.violations(trs.readText()).map { "${trs.name}: $it" } }
        violations.shouldBeEmpty()
    }

    "反例: 未解決の Blocking があるのに @pending が無い TRS は違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1"))
            .shouldContainExactly("OI-001: 未解決の Blocking が指す AC-09-01 に @pending:OI-001 が無い")
    }

    "反例: 状態が In Discussion の Blocking も未解決として扱う" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "In Discussion", "@AC-09-01 @layer:1"))
            .shouldContainExactly("OI-001: 未解決の Blocking が指す AC-09-01 に @pending:OI-001 が無い")
    }

    "反例: @pending の OI-ID が別の Open Issue のものだと違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-002"))
            .shouldContainExactly("OI-001: 未解決の Blocking が指す AC-09-01 に @pending:OI-001 が無い")
    }

    "反例: 範囲指定（AC-09-01〜02）の一部にだけ @pending があっても違反になる" {
        UnresolvedBlockingCheck.violations(
            sampleTrs("Blocking", "AC-09-01〜02", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1"),
        ).shouldContainExactly("OI-001: 未解決の Blocking が指す AC-09-02 に @pending:OI-001 が無い")
    }

    "反例: 未解決の Blocking の関連列に AC が無いと違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "F1-L2", "Open", "@AC-09-01 @layer:1 @pending:OI-001"))
            .shouldContainExactly("OI-001: 未解決の Blocking だが、関連列に AC が無い（指す AC を書く）")
    }

    "反例: 関連列の AC が AC 表に無いと違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-07", "Open", "@AC-09-01 @layer:1 @pending:OI-001"))
            .shouldContainExactly("OI-001: 関連列の AC-09-07 が AC 表に無い")
    }

    "反例: Open Issues 表の見出しが無い TRS は違反になる" {
        UnresolvedBlockingCheck.violations("| AC-09-01 | 説明 |\n")
            .shouldContainExactly("Open Issues 表の見出し（ID・区分・関連・状態）が見つからない")
    }

    "反例: Open Issues 表に行が 1 つも無いと違反になる" {
        UnresolvedBlockingCheck.violations("| ID | 区分 | 関連 | 状態 |\n|---|---|---|---|\n")
            .shouldContainExactly("Open Issues 表に行が 1 つも無い")
    }

    "反例: 状態が 03 §4.2 の 5 値に分類できないと違反になる（大文字小文字・装飾・未知の値）" {
        listOf("open", "**Open**", "Opened", "Done", "Superseded OI-002", "Resolved（OI-002）").forEach { state ->
            UnresolvedBlockingCheck.violations(sampleTrs("Non-blocking", "AC-09-01", state, "@AC-09-01 @layer:1"))
                .shouldContainExactly("OI-001: 状態を分類できない（03 §4.2 の 5 値のどれかにする）: $state")
        }
    }

    "反例: 区分が Blocking / Non-blocking に分類できないと違反になる（大文字小文字・装飾）" {
        listOf("blocking", "**Blocking**", "Non-Blocking", "Nonblocking").forEach { kind ->
            UnresolvedBlockingCheck.violations(sampleTrs(kind, "AC-09-01", "Open", "@AC-09-01 @layer:1"))
                .shouldContainExactly("OI-001: 区分を分類できない（Blocking / Non-blocking のどちらかにする）: $kind")
        }
    }

    "反例: 範囲として読めない 〜 が関連列に残ると違反になる" {
        listOf("AC-09-01〜", "〜AC-09-02", "AC-09-01〜AC-08-02", "AC-09-01 〜 02").forEach { related ->
            UnresolvedBlockingCheck.violations(sampleTrs("Blocking", related, "Open", "@AC-09-01 @layer:1 @pending:OI-001"))
                .shouldContainExactly("OI-001: 関連列の範囲表記を読めない（AC-xx-yy〜zz または AC-xx-yy〜AC-xx-zz の形にする）: $related")
        }
    }

    "反例: 範囲が逆順だと違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-02〜01", "Open", "@AC-09-01 @layer:1 @pending:OI-001"))
            .shouldContainExactly("OI-001: 関連列の範囲が逆順: AC-09-02〜01")
    }

    "正例: 範囲は AC-09-01〜02、AC-09-01〜AC-09-02、全角の ～ のどれでも同じに展開される" {
        listOf("AC-09-01〜02", "AC-09-01〜AC-09-02", "AC-09-01～02", "AC-09-01~AC-09-02").forEach { related ->
            UnresolvedBlockingCheck.violations(
                sampleTrs("Blocking", related, "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:2 @pending:OI-001"),
            ).shouldBeEmpty()
            UnresolvedBlockingCheck.violations(sampleTrs("Blocking", related, "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:2"))
                .shouldContainExactly("OI-001: 未解決の Blocking が指す AC-09-02 に @pending:OI-001 が無い")
        }
    }

    "正例: @pending:<OI-ID> が付いていれば合格する（範囲指定も展開される）" {
        UnresolvedBlockingCheck.violations(
            sampleTrs("Blocking", "AC-09-01〜02", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:2 @pending:OI-001"),
        ).shouldBeEmpty()
    }

    "正例: Resolved・Superseded の Blocking と Open の Non-blocking は @pending が無くても合格する" {
        listOf("Blocking" to "Resolved", "Blocking" to "Superseded（OI-002）", "Blocking" to "Won't Fix（理由）", "Non-blocking" to "Open").forEach { (kind, state) ->
            UnresolvedBlockingCheck.violations(sampleTrs(kind, "AC-09-01", state, "@AC-09-01 @layer:1")).shouldBeEmpty()
        }
    }
})

/** 検査の入力用の最小の TRS。AC 表（`tags` の先頭の AC ID）、Gherkin のタグ行、Open Issues 表（OI-001 の1行）を持つ */
private fun sampleTrs(kind: String, related: String, state: String, vararg tags: String): String = buildString {
    appendLine("| AC | 内容 |")
    appendLine("|---|---|")
    tags.forEach { appendLine("| ${it.substringBefore(' ').removePrefix("@")} | 説明 |") }
    appendLine()
    tags.forEach { appendLine(it) }
    appendLine()
    appendLine("| ID | 区分 | 関連 | 状態 |")
    appendLine("|---|---|---|---|")
    appendLine("| OI-001 | $kind | $related | $state |")
}
