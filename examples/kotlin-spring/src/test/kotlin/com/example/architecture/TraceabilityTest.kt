package com.example.architecture

import com.lemonappdev.konsist.api.Konsist
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * TRS ファイルの Open Issues 表（6. Open Issues）を読み、未解決の Blocking な Open Issue が指す AC に
 * `@pending:<OI-ID>` が付いているかを検査する（01 §5 の G5、03 §3.4）。
 * AC の表と Gherkin のタグ行は、組になる生成物のファイルから読む（[violations] の第 2 引数）。
 *
 * - 区分は `Blocking` / `Non-blocking`、状態は 03 §4.2 の 5 値（`Open` / `In Discussion` / `Resolved` /
 *   `Superseded` / `Won't Fix`。後ろの 2 つは `（置換先・理由）` の接尾辞を付けてよい）に分類する。
 *   どれにも当たらない値（`open`、`**Blocking**` など）は、分類できないので違反とする。
 * - 未解決 = 区分が `Blocking` で、状態が `Open` または `In Discussion`。
 * - 「指す AC」= 関連列の `AC-xx-yy`。範囲は `AC-03-01〜04` と `AC-03-01〜AC-03-04`（`〜` は `～` `~` も可）だけを展開する。
 *   範囲として読めない `〜` が残る場合は違反とする。関連列に AC が無い行は、指す先を決められないので違反とする。
 * - 該当 AC の Gherkin のタグ行に、その行の OI-ID と同じ `@pending:<OI-ID>` が無ければ違反。
 * - Open Issues 表に行が 1 つも無い TRS は、読み取りの空振りと区別できないので違反とする。見出し（ID・区分・関連・状態・反映先）が読めないときも違反とする。
 * - 関連列の AC ID は、行の区分と状態によらず、生成物のファイルの AC の表と照合する（範囲表記は展開し、読めない表記は違反）。
 *   照合から外すのは、未解決でない行の反映先に `AC-xx-yy 削除` と書いた AC だけである（[TrsSetCheck.exemptions]）。外すのは「AC の表にあるか」の照合だけで、
 *   未解決の Blocking の `@pending` の要否には効かない。未解決の Blocking の関連列の AC がすべて外れているときは、違反とする。
 * - `@pending:<ID>` に書けるのは「その TRS の Open Issues 表にある、未解決（`Open` / `In Discussion`）の `Blocking` な OI-ID」だけ
 *   （03 §3.4 の「Blocking の Open Issue が未解決の AC」と一致させ、除外の抜け道を作らない）。次は違反とする。
 *   `OI-<数字>` の形でない値（`ISSUE-n` を含む。実装待ちは Issue / PR で管理する）、表に無い OI-ID、
 *   未解決の Blocking でない OI-ID（`Resolved` / `Superseded` / `Won't Fix` / `Non-blocking`）。
 *   対象は、行内で `@AC-xx-yy` より後ろにある `@pending:` である（AC を除外する判定と同じ範囲。行頭が `@` の行に限らない）。
 *   `@AC-xx-yy` を伴わない説明文や履歴行の `@pending:` は、AC を除外しないので対象外。
 * - `@pending:<OI-ID>` を付けた AC は、その OI の関連列（`targets`）に含まれていなければ違反とする（無関係な AC を除外する抜け道を塞ぐ）。
 *   関連列が読めない（範囲表記エラー・逆順）OI と、関連列に AC が無い OI は、上の検査がすでに違反にしているので、ここでは二重に数えない。
 */
internal object UnresolvedBlockingCheck {
    private val unresolvedStates = setOf("Open", "In Discussion")
    private val resolvedState = Regex("""^(Resolved|Superseded(?:[（(].+[）)])?|Won't Fix(?:[（(].+[）)])?)$""")
    private val kinds = setOf("Blocking", "Non-blocking")
    private val acId = Regex("""AC-\d\d-\d\d""")
    private val acTagTail = Regex("""@AC-\d\d-\d\d\b[^\n]*""")
    private val pendingTag = Regex("""@pending:([^\s`|]*)""")
    private val oiId = Regex("""^OI-\d+$""")
    private val issueRef = Regex("""^ISSUE-\d+$""")

    /** 状態が `Resolved` / `Superseded` / `Won't Fix` のどれかか（未解決でない行。接尾辞を含む） */
    fun isResolvedState(state: String) = resolvedState.matches(state)

    class Row(val id: String, val kind: String, val state: String, val related: String, val reflect: String) {
        val blocking get() = kind == "Blocking"
        val unresolved get() = state in unresolvedStates
    }

    private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private fun isSeparator(line: String) = cells(line).all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }

    /** Open Issues 表のデータ行と、表が読めなかった理由（見出しが無い・列数が合わない） */
    fun parse(text: String): Pair<List<Row>, List<String>> {
        val lines = text.lines()
        val header = lines.indexOfFirst { l ->
            l.startsWith("|") && cells(l).let { "ID" in it && "区分" in it && "状態" in it && "関連" in it && "反映先" in it }
        }
        if (header < 0) return emptyList<Row>() to listOf("Open Issues 表の見出し（ID・区分・関連・状態・反映先）が見つからない")
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
                Row(col("ID"), col("区分"), col("状態"), col("関連"), col("反映先"))
            }
        }
        return rows to errors
    }

    /** 関連列が指す AC。範囲として読めない区切りが残る、または範囲が逆順のときは、第 2 要素に理由を返す */
    private fun targets(related: String): Pair<List<String>, String?> {
        val expanded = AcRefs.expand(related)
        val problem = when (expanded.problem) {
            AcRefs.Problem.UNREADABLE -> "関連列の範囲表記を読めない（AC-xx-yy〜zz または AC-xx-yy〜AC-xx-zz の形にする）: $related"
            AcRefs.Problem.REVERSED -> "関連列の範囲が逆順: $related"
            null -> null
        }
        return expanded.ids to problem
    }

    /**
     * 行内で `@AC-xx-yy` より後ろにある `@pending:<ID>` の違反。次の 2 種類を返す。
     * - `<ID>` が未解決の Blocking な OI-ID でないもの（形・実在・状態）。AC を含まない文言なので、同じ ID は AC が違っても 1 件にまとめる。
     * - 未解決の Blocking な OI-ID だが、付いた AC がその OI の関連列に含まれないもの（[relatedViolation]）。(AC, ID) の組ごとに 1 件。
     * 「AC を紐づけ検査から除外する」判定（[TraceabilityTest] の `pendingTag`、[violations] の `@$ac ... @pending:`）と
     * 同じ範囲を見る。行頭が `@` の行に限ると、表や引用の中の `@AC-xx-yy @pending:ISSUE-n` が検査をすり抜けて除外だけ効いてしまう
     */
    private fun pendingViolations(text: String, rows: List<Row>): List<String> {
        val byId = rows.associateBy { it.id }
        val used = acTagTail.findAll(text).flatMap { tail ->
            val ac = acId.find(tail.value)!!.value
            pendingTag.findAll(tail.value).map { ac to it.groupValues[1] }
        }.toList().distinct()
        val invalidId = used.map { it.second }.distinct().mapNotNull { id ->
            val oi = byId[id]
            when {
                issueRef.matches(id) -> "@pending:$id は書けない（@pending には未解決の Blocking な OI-ID だけを書く。実装待ちは Issue / PR で管理する）"
                !oiId.matches(id) -> "@pending:$id は OI-ID の形（OI-<数字>）でない"
                oi == null -> "@pending:$id の $id が Open Issues 表に無い"
                !oi.blocking || !oi.unresolved ->
                    "@pending:$id の $id は未解決の Blocking ではない（区分 ${oi.kind}、状態 ${oi.state}。@pending は外す）"
                else -> null
            }
        }
        val unrelated = used.mapNotNull { (ac, id) -> byId[id]?.takeIf { it.blocking && it.unresolved }?.let { relatedViolation(ac, it) } }
        return invalidId + unrelated
    }

    /**
     * `@pending:<OI-ID>` を付けた [ac] が、その OI の関連列（[targets]）に含まれないとき、無関係な AC を紐づけ検査から外す抜け道になる。
     * 関連列が読めない（範囲表記エラー・逆順）、または AC が無い OI は、順方向の検査（[violations]）がすでに違反にしているので、二重には数えない
     */
    private fun relatedViolation(ac: String, oi: Row): String? {
        val (acList, problem) = targets(oi.related)
        return if (problem != null || acList.isEmpty() || ac in acList) null
        else "@pending:${oi.id} を付けた $ac は ${oi.id} の関連列に含まれない（関連列: ${oi.related}。@pending は関連列にある AC にだけ付ける）"
    }

    /** 1 つのファイルに TRS の全節がある本文（テンプレートなど）の違反。[violations] を、同じ本文を両方に渡して呼ぶ */
    fun violations(text: String): List<String> = violations(text, text)

    /**
     * 違反の説明。空なら合格。[trsText] は Open Issues 表を持つ TRS ファイル、[generatedText] は AC の表と Gherkin のタグ行を持つ生成物のファイル。
     * `@pending` の検査は、生成物のファイルだけを見る（① で AC を紐づけ検査から除外する判定と同じ本文）。
     */
    fun violations(trsText: String, generatedText: String): List<String> {
        val (rows, errors) = parse(trsText)
        val acs = AcTable.read(generatedText).rows.map { it.id }.toSet()
        val exempt = TrsSetCheck.exemptions(rows)
        val classification = rows.flatMap { oi ->
            listOfNotNull(
                "${oi.id}: 区分を分類できない（Blocking / Non-blocking のどちらかにする）: ${oi.kind}".takeIf { oi.kind !in kinds },
                "${oi.id}: 状態を分類できない（03 §4.2 の 5 値のどれかにする）: ${oi.state}"
                    .takeIf { !oi.unresolved && !resolvedState.matches(oi.state) },
            )
        }
        val emptyTable = if (rows.isEmpty() && errors.isEmpty()) listOf("Open Issues 表に行が 1 つも無い") else emptyList()
        val found = rows.flatMap { oi ->
            val (acList, problem) = targets(oi.related)
            val needsPending = oi.blocking && oi.unresolved
            when {
                problem != null -> listOf("${oi.id}: $problem")
                acList.isEmpty() -> listOf("${oi.id}: 未解決の Blocking だが、関連列に AC が無い（指す AC を書く）").takeIf { needsPending }.orEmpty()
                needsPending && acList.none { it in acs } && acList.all { it in exempt } ->
                    listOf("${oi.id}: 未解決の Blocking の関連列の AC が、すべて照合から外れている（指す AC が AC の表に無い。削除の記録は、未解決の行の AC には使えない）")
                else -> acList.mapNotNull { ac ->
                    when {
                        ac !in acs -> "${oi.id}: 関連列の $ac が AC 表に無い".takeIf { ac !in exempt }
                        needsPending && !Regex("""@$ac\b[^\n]*@pending:${Regex.escape(oi.id)}\b""").containsMatchIn(generatedText) ->
                            "${oi.id}: 未解決の Blocking が指す $ac に @pending:${oi.id} が無い"
                        else -> null
                    }
                }
            }
        }
        return errors + emptyTable + classification + found + pendingViolations(generatedText, rows)
    }
}

/**
 * 03 §3.4 / 01 の G5: TRS ファイル（`docs/trs/`）と、同じ名前の生成物のファイル（`docs/trs/generated/`）を組にして読み、失敗条件を検査する。
 * 組にならないファイル、TRS ファイルにある US・UC・AC の表の行、生成物のファイルに無い US・UC・AC の表の行、生成物のファイルにある
 * Open Issues・因子・水準・禁止組合せの表、組合せ別の期待結果（5.2）の書式、AC の表の根拠の実在、トレーサビリティ表（7）と Open Issues の
 * 関連列の AC ID が AC の表に無いことは、[TrsSetCheck] が検査する（自己テストは [TrsSetCheckTest]）。
 * AC に紐づくテストが無ければ失敗させる（①）。テスト名は先頭に `AC-xx-yy` を置く（複数なら空白区切り）。
 * テスト名の先頭の AC ID が、どの TRS の AC の表にも無ければ失敗させる（⑦）。
 * AC ID は TRS 間で重複させない（別の TRS のテストで満たされたように見えるのを防ぐ。03 §3.4）。
 * Blocking の Open Issue により期待値が未確定の AC は、Gherkin のタグに `@pending:<OI-ID>` を付けて除外する。
 * 未解決の Blocking な Open Issue が指す AC に `@pending:<OI-ID>` が無ければ失敗させる（[UnresolvedBlockingCheck]）。
 * 逆に `@pending:<ID>` が、表に無い OI-ID・未解決の Blocking でない OI-ID・`ISSUE-n` を指していても失敗させる（同上）。
 * `@pending:<OI-ID>` を付けた AC が、その OI の関連列に含まれなくても失敗させる（同上。無関係な AC を除外する抜け道を塞ぐ）。
 * 要件定義書（`docs/requirements/`）の要件 ID が、どの TRS の US/UC の出典にも現れなければ失敗させる（03 §3.4.4 の ⑤）。
 * US/UC の出典が実在する要件 ID でなければ（空欄・`—`・OI-ID を含む）、また同じ要件 ID が複数の行にあれば失敗させる（同 ⑥）。
 * US/UC は生成物のファイルから読む。要件定義書と TRS の書式が読めないとき（要件を読む表の外の要件 ID、要件 0 件、US/UC 0 件など）も失敗させる（[RequirementTraceCheck]）。
 * 基本設計書（`docs/basic-design/`）の設計 ID（節 2〜9 の表の先頭列）が、どの TRS の根拠にも現れなければ失敗させる（03 §3.4.4 の ⑧）。
 * TRS の根拠の値（C-xx・要件 ID・設計 ID・OI-ID）が空欄、どの形にも当たらない、実在しない、その表に書けないとき、また同じ設計 ID が複数の行にあるとき、失敗させる（同 ⑨）。
 * 設計 ID の書式が読めないときも失敗させる（[DesignIds]・[BasisCheck]）。
 * 要件定義書と基本設計書の目次が、03 §5.2 の目次と違えば失敗させる（同 §3.4.5 の ⑩）。
 * 要件定義書の「使わない言い換え」が、同じ名前の要件定義書・基本設計書・TRS・生成物のファイルに現れれば失敗させる（同 ⑪。[InputDocCheck]）。
 * このファイルは CODEOWNERS の対象（`@pending` の乱用や検査の弱体化を人間がレビューする）。
 */
class TraceabilityTest : StringSpec({
    // `_` で始まるファイルを除くのは TRS ファイル（`_template.md` などの置き場の都合）だけ。要件定義書・生成物のファイルは拡張子だけで絞り、名前で検査から外さない
    fun docs(dir: String, skipUnderscore: Boolean = false) =
        File(dir).listFiles { f -> f.isFile && f.extension == "md" && !(skipUnderscore && f.name.startsWith("_")) }.orEmpty().sortedBy { it.name }
            .associate { it.name to it.readText() }
    val trsDocs = docs("docs/trs", skipUnderscore = true)
    val generatedDocs = docs("docs/trs/generated")
    val requirementDocs = docs("docs/requirements")
    val designDocs = docs("docs/basic-design")
    val a = "REQ-TST-01"
    val b = "REQ-TST-02"
    val c = "REQ-TST-03"
    val d = "REQ-TST-04"
    val reqOk = mapOf("req.md" to reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |", "| $c | 説明 | |")))
    val trsOk = mapOf("trs.md" to trsDoc(listOf("$a, $b"), listOf("$b, $c")))

    // テスト名（Kotest の文字列名・JUnit のバッククォート名）の先頭に置かれた AC ID。自己テストの文字列は、テスト名ではないので除く
    val testSources = Konsist.scopeFromTest().files.filterNot { it.name in selfTestFiles }.joinToString("\n") { it.text }

    "統合テスト（src/integrationTest）のテスト名も AC の紐づけ検査の対象に含まれる" {
        // 層2（Integration）の AC は src/integrationTest のテストに紐づく。ここが検査範囲から外れると、移した AC が「テスト無し」と誤判定される
        Konsist.scopeFromTest().files.any { "/src/integrationTest/" in it.path } shouldBe true
        testSources.contains("class TaskApiHttpTest") shouldBe true // src/integrationTest のテスト本文が照合対象に入っている
    }

    "E2E（src/e2eTest）のテスト名も AC の紐づけ検査の対象に含まれる" {
        // E2E のテストが検査範囲から外れると、E2E にだけ紐づく AC が「テスト無し」と誤判定される（逆に、E2E の AC 名が検査されなくなる）
        Konsist.scopeFromTest().files.any { "/src/e2eTest/" in it.path } shouldBe true
        testSources.contains("class TaskListE2eTest") shouldBe true // src/e2eTest のテスト本文が照合対象に入っている
    }

    "TRS ファイルと生成物のファイルが1つ以上存在する（ディレクトリの移動で素通りしない）" {
        trsDocs.isNotEmpty() shouldBe true
        generatedDocs.isNotEmpty() shouldBe true
    }

    "基本設計書が1つ以上存在し、設計 ID を読めている（根拠の実在の検査が空の照合先で素通りしない）" {
        designDocs.isNotEmpty() shouldBe true
        designDocs.values.sumOf { DesignIds.read(it).size } shouldBeGreaterThan 0
    }

    "TRS ファイルと生成物のファイルが組になり、03 §3.4 の失敗条件に当たらない（置き場・AC の表・期待結果・根拠の実在・照合）" {
        val violations = TrsSetCheck.violations(trsDocs, generatedDocs, requirementDocs, designDocs)
        withClue(violations.joinToString("\n")) { violations.shouldBeEmpty() } // 違反の全件を失敗メッセージに出す
    }

    "要件定義書と基本設計書の目次が、03 §5.2 のとおりである（⑩）" {
        val violations = InputDocCheck.tocViolations("要件定義書", requirementDocs, InputDocCheck.requirementToc) +
            InputDocCheck.tocViolations("基本設計書", designDocs, InputDocCheck.designToc)
        withClue(violations.joinToString("\n")) { violations.shouldBeEmpty() }
    }

    "要件定義書の「使わない言い換え」が、同じ名前の要件定義書・基本設計書・TRS ファイル・生成物のファイルに現れない（⑪）" {
        val violations = InputDocCheck.termViolations(requirementDocs, designDocs, trsDocs, generatedDocs)
        withClue(violations.joinToString("\n")) { violations.shouldBeEmpty() }
    }

    "目次の定数（InputDocCheck）が 03 §5.2 の目次と同じで、テンプレートも ⑩ を通る（目次の正が規範とずれない）" {
        // 03 §5.2 の番号つきの一覧を読む。一覧は、番号が 1 に戻るところで、要件定義書と基本設計書に分かれる
        val section = File("../../docs/testing/03-trs-guideline.md").readText().substringAfter("### 5.2 入力の文書").substringBefore("### 5.3")
        val lists = mutableListOf<MutableList<String>>()
        Regex("""^(\d+)\. (.+)$""", RegexOption.MULTILINE).findAll(section).forEach { m ->
            if (m.groupValues[1] == "1") lists += mutableListOf<String>()
            lists.last() += m.groupValues[2].trim()
        }
        lists shouldBe listOf(InputDocCheck.requirementToc, InputDocCheck.designToc)
        val templates = File("../../docs/testing/templates")
        val requirementTemplate = mapOf("requirements.md" to File(templates, "requirements.md").readText())
        InputDocCheck.tocViolations("要件定義書", requirementTemplate, InputDocCheck.requirementToc).shouldBeEmpty()
        InputDocCheck.tocViolations("基本設計書", mapOf("basic-design.md" to File(templates, "basic-design.md").readText()), InputDocCheck.designToc).shouldBeEmpty()
        // 用語の表の見出しと小見出しを、検査が読める（テンプレートの書式を変えたとき、検査から外れない）
        InputDocCheck.terms(requirementTemplate.getValue("requirements.md")).errors.shouldBeEmpty()
    }

    "pending でない全ての AC に、先頭に AC ID を持つテストが紐づいている（①）" {
        val missing = TestNameCheck.untested(generatedDocs, testSources)
        withClue(missing.joinToString("\n")) { missing.shouldBeEmpty() }
    }

    "テスト名の先頭の AC ID が、どれかの TRS の AC の表にある（⑦）" {
        val unknown = TestNameCheck.unknown(generatedDocs, testSources)
        withClue(unknown.joinToString("\n")) { unknown.shouldBeEmpty() }
    }

    "TRS ファイルの Open Issues 表を読めている（表の書式変更で検査が素通りしない）" {
        trsDocs.forEach { (name, text) ->
            val (rows, errors) = UnresolvedBlockingCheck.parse(text)
            withClue(name) { errors.shouldBeEmpty() }
            rows.isNotEmpty() shouldBe true
            rows.size shouldBe Regex("""^\|\s*OI-\d+\s*\|""", RegexOption.MULTILINE).findAll(text).count()
        }
    }

    "TRS のテンプレートも G5 の検査を通る（未解決の Blocking の例に @pending が付いている）" {
        UnresolvedBlockingCheck.violations(File("../../docs/testing/trs/_template.md").readText()).shouldBeEmpty()
    }

    "TRS のテンプレートも、AC の表・組合せ別の期待結果・トレーサビリティ表の検査を通る（全節を 1 ファイルで示すので、置き場の検査と根拠の実在は除く）" {
        val template = File("../../docs/testing/trs/_template.md").readText()
        val violations = TrsSetCheck.contentViolations(template, template)
        withClue(violations.joinToString("\n")) { violations.shouldBeEmpty() }
    }

    "要件定義書が1つ以上存在する（ディレクトリの移動で素通りしない）" {
        requirementDocs.isNotEmpty() shouldBe true
    }

    "要件定義書の要件 ID と TRS の US/UC の出典が対応している（⑤ 全 ID が出典に現れる、⑥ 出典が実在し、ID が一意）" {
        val violations = RequirementTraceCheck.violations(requirementDocs, generatedDocs)
        withClue(violations.joinToString("\n")) { violations.shouldBeEmpty() } // 違反の全件を失敗メッセージに出す
    }

    "テンプレートの TRS と要件定義書の組も ⑤ ⑥ の検査を通る" {
        RequirementTraceCheck.violations(
            mapOf("requirements.md" to File("../../docs/testing/templates/requirements.md").readText()),
            mapOf("_template.md" to File("../../docs/testing/trs/_template.md").readText()),
        ).shouldBeEmpty()
    }

    "未解決の Blocking な Open Issue が指す AC に、その OI-ID の @pending が付いている（G5）" {
        val violations = trsDocs.flatMap { (name, text) -> UnresolvedBlockingCheck.violations(text, generatedDocs[name].orEmpty()).map { "$name: $it" } }
        withClue(violations.joinToString("\n")) { violations.shouldBeEmpty() }
    }

    "反例: 未解決の Blocking があるのに @pending が無い TRS は違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1"))
            .shouldContainExactly("OI-001: 未解決の Blocking が指す AC-09-01 に @pending:OI-001 が無い")
    }

    "反例: 状態が In Discussion の Blocking も未解決として扱う" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "In Discussion", "@AC-09-01 @layer:1"))
            .shouldContainExactly("OI-001: 未解決の Blocking が指す AC-09-01 に @pending:OI-001 が無い")
    }

    "反例: @pending の OI-ID が表に無い別の ID だと、指す AC に自分の @pending が無いことと合わせて違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-002")).shouldContainExactly(
            "OI-001: 未解決の Blocking が指す AC-09-01 に @pending:OI-001 が無い",
            "@pending:OI-002 の OI-002 が Open Issues 表に無い",
        )
    }

    "反例: @pending が実在する別の未解決の Blocking（OI-002）だけを指していると、OI-001 の AC に OI-001 の @pending が無く、AC-09-01 も OI-002 の関連列に無いので違反になる" {
        UnresolvedBlockingCheck.violations(
            sampleTrsRows(
                listOf("| OI-001 | Blocking | AC-09-01 | Open | |", "| OI-002 | Blocking | AC-09-02 | Open | |"),
                "@AC-09-01 @layer:1 @pending:OI-002",
                "@AC-09-02 @layer:1 @pending:OI-002",
            ),
        ).shouldContainExactly(
            "OI-001: 未解決の Blocking が指す AC-09-01 に @pending:OI-001 が無い",
            "@pending:OI-002 を付けた AC-09-01 は OI-002 の関連列に含まれない（関連列: AC-09-02。@pending は関連列にある AC にだけ付ける）",
        )
    }

    "反例: @pending の OI-ID が実在の未解決の Blocking でも、その AC が OI の関連列に無ければ違反になる（無関係な AC を除外する抜け道）" {
        // 期待値の出所は Issue #101 の決定。AC-09-01 は OI-001 の関連列にあるので OI-001 は合格、OI-002 の関連列は AC-09-02 だけなので違反になる
        UnresolvedBlockingCheck.violations(
            sampleTrsRows(
                listOf("| OI-001 | Blocking | AC-09-01 | Open | |", "| OI-002 | Blocking | AC-09-02 | Open | |"),
                "@AC-09-01 @layer:1 @pending:OI-001 @pending:OI-002",
                "@AC-09-02 @layer:1 @pending:OI-002",
            ),
        ).shouldContainExactly(
            "@pending:OI-002 を付けた AC-09-01 は OI-002 の関連列に含まれない（関連列: AC-09-02。@pending は関連列にある AC にだけ付ける）",
        )
    }

    "反例: 範囲指定の関連列の外にある AC に、その OI の @pending を付けると違反になる" {
        UnresolvedBlockingCheck.violations(
            sampleTrs("Blocking", "AC-09-01〜02", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:OI-001", "@AC-09-03 @layer:1 @pending:OI-001"),
        ).shouldContainExactly(
            "@pending:OI-001 を付けた AC-09-03 は OI-001 の関連列に含まれない（関連列: AC-09-01〜02。@pending は関連列にある AC にだけ付ける）",
        )
    }

    "反例: 同じ (AC, OI) の組が 2 回現れても、関連列の違反は 1 件にまとまる" {
        UnresolvedBlockingCheck.violations(
            sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:OI-001 @pending:OI-001"),
        ).shouldContainExactly(
            "@pending:OI-001 を付けた AC-09-02 は OI-001 の関連列に含まれない（関連列: AC-09-01。@pending は関連列にある AC にだけ付ける）",
        )
    }

    "反例: 同じ不正な ID を別々の AC に付けても、ID の妥当性の違反は 1 件にまとまる" {
        listOf(
            "ISSUE-33" to "@pending:ISSUE-33 は書けない（@pending には未解決の Blocking な OI-ID だけを書く。実装待ちは Issue / PR で管理する）",
            "OI-099" to "@pending:OI-099 の OI-099 が Open Issues 表に無い",
            "oi-001" to "@pending:oi-001 は OI-ID の形（OI-<数字>）でない",
        ).forEach { (id, message) ->
            UnresolvedBlockingCheck.violations(
                sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:$id", "@AC-09-03 @layer:1 @pending:$id"),
            ).shouldContainExactly(message)
        }
        UnresolvedBlockingCheck.violations(
            sampleTrs("Non-blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:OI-001"),
        ).shouldContainExactly("@pending:OI-001 の OI-001 は未解決の Blocking ではない（区分 Non-blocking、状態 Open。@pending は外す）")
    }

    "反例: 同じ AC に付いた @pending の違反は 1 件にまとまり、別の AC なら別々に数える" {
        UnresolvedBlockingCheck.violations(
            sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:OI-001", "@AC-09-03 @layer:1 @pending:OI-001"),
        ).shouldContainExactly(
            "@pending:OI-001 を付けた AC-09-02 は OI-001 の関連列に含まれない（関連列: AC-09-01。@pending は関連列にある AC にだけ付ける）",
            "@pending:OI-001 を付けた AC-09-03 は OI-001 の関連列に含まれない（関連列: AC-09-01。@pending は関連列にある AC にだけ付ける）",
        )
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
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-07", "Open", "@AC-09-01 @layer:1 @pending:OI-001")).shouldContainExactly(
            "OI-001: 関連列の AC-09-07 が AC 表に無い",
            "@pending:OI-001 を付けた AC-09-01 は OI-001 の関連列に含まれない（関連列: AC-09-07。@pending は関連列にある AC にだけ付ける）",
        )
    }

    "反例: Open Issues 表の見出しが無い TRS は違反になる" {
        UnresolvedBlockingCheck.violations("| AC-09-01 | 説明 |\n")
            .shouldContainExactly("Open Issues 表の見出し（ID・区分・関連・状態・反映先）が見つからない")
    }

    "反例: Open Issues 表に行が 1 つも無いと違反になる" {
        UnresolvedBlockingCheck.violations("| ID | 区分 | 関連 | 状態 | 反映先 |\n|---|---|---|---|---|\n")
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

    "反例: @pending に ISSUE-n を書くと違反になる（実装待ちは Issue / PR で管理する）" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:ISSUE-33"))
            .shouldContainExactly("@pending:ISSUE-33 は書けない（@pending には未解決の Blocking な OI-ID だけを書く。実装待ちは Issue / PR で管理する）")
    }

    "反例: @pending の OI-ID が Open Issues 表に無いと違反になる" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:OI-099"))
            .shouldContainExactly("@pending:OI-099 の OI-099 が Open Issues 表に無い")
    }

    "反例: @pending の OI-ID が未解決の Blocking でない（Resolved・Superseded・Won't Fix・Non-blocking）と違反になる" {
        listOf(
            "Blocking" to "Resolved",
            "Blocking" to "Superseded（OI-002）",
            "Blocking" to "Won't Fix（理由）",
            "Non-blocking" to "Open",
            "Non-blocking" to "In Discussion",
        ).forEach { (kind, state) ->
            UnresolvedBlockingCheck.violations(sampleTrs(kind, "AC-09-01", state, "@AC-09-01 @layer:1 @pending:OI-001"))
                .shouldContainExactly("@pending:OI-001 の OI-001 は未解決の Blocking ではない（区分 $kind、状態 $state。@pending は外す）")
        }
    }

    "反例: @pending の値が OI-<数字> の形でないと違反になる（空・前方一致・接尾辞・小文字）" {
        listOf("", "OI-", "OI-001x", "OI-001-x", "oi-001", "ISSUE-", "<OI-ID>").forEach { id ->
            UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001", "@AC-09-02 @layer:1 @pending:$id"))
                .shouldContainExactly("@pending:$id は OI-ID の形（OI-<数字>）でない")
        }
    }

    "反例: 1 つのタグ行に複数の @pending があれば、それぞれを検査する" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001 @pending:ISSUE-33 @pending:OI-099"))
            .shouldContainExactly(
                "@pending:ISSUE-33 は書けない（@pending には未解決の Blocking な OI-ID だけを書く。実装待ちは Issue / PR で管理する）",
                "@pending:OI-099 の OI-099 が Open Issues 表に無い",
            )
    }

    "反例: 行頭が @ でない行（表・引用）でも、@AC-xx-yy の後ろの @pending は検査する（除外だけ効いて検査をすり抜けない）" {
        listOf("| 備考 | @AC-09-02 @pending:ISSUE-33 |", "> 例: @AC-09-02 @pending:ISSUE-33", "  - `@AC-09-02 @pending:ISSUE-33`").forEach { line ->
            val text = sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001") + "\n$line\n"
            UnresolvedBlockingCheck.violations(text)
                .shouldContainExactly("@pending:ISSUE-33 は書けない（@pending には未解決の Blocking な OI-ID だけを書く。実装待ちは Issue / PR で管理する）")
        }
    }

    "正例: @AC-xx-yy を伴わない説明文や履歴行に書いた @pending:<ID> は @pending の検査の対象外" {
        val text = sampleTrs("Blocking", "AC-09-01", "Open", "@AC-09-01 @layer:1 @pending:OI-001") +
            "\n> 説明: `@pending:<OI-ID>` を付ける。\n| 2026-10-10 | 0.2 | AI | 実装待ちの AC は `@pending:ISSUE-33` | 未承認 |\n"
        UnresolvedBlockingCheck.violations(text).shouldBeEmpty()
    }

    "正例: In Discussion の Blocking を指す @pending は合格する" {
        UnresolvedBlockingCheck.violations(sampleTrs("Blocking", "AC-09-01", "In Discussion", "@AC-09-01 @layer:1 @pending:OI-001")).shouldBeEmpty()
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

    "正例: 要件 ID が US か UC のどちらかの出典に現れれば合格する（備考列の要件 ID・節の中の小見出しの表・コードブロックの中は影響しない）" {
        RequirementTraceCheck.violations(reqOk, trsOk).shouldBeEmpty()
        val withSubsection = "# 要件\n\n## 3. 機能要件\n\n### 3.1 登録\n\n| ID | 要件 | 備考 |\n|---|---|---|\n| $a | 説明 | $b を参照 |\n| $b | 説明 | |\n\n## 5. 非機能要件\n\n| ID | 観点 | 要件 |\n|---|---|---|\n| $c | 性能 | 説明 |\n"
        RequirementTraceCheck.violations(mapOf("req.md" to withSubsection), trsOk).shouldBeEmpty()
        val fencedTrs = trsDoc(listOf("$a, $b"), listOf("$b, $c")) + "\n```gherkin\n# UC-09: 例\n| 出典（要件 ID） | $d |\n```\n"
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to fencedTrs)).shouldBeEmpty()
    }

    "反例: どの US/UC の出典にも現れない要件 ID があると違反になる（⑤ 要件 ID の漏れ）" {
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf("$a, $b"), listOf("$a, $b"))))
            .shouldContainExactly("要件 ID $c が、どの US/UC の出典にも現れない")
    }

    "反例: 要件 ID の漏れは、すべて報告する（複数の TRS の出典を合わせても残るもの）" {
        val trsA = trsDoc(listOf(a), listOf(a))
        val trsB = trsDoc(listOf(a), listOf(b))
        RequirementTraceCheck.violations(reqOk, mapOf("a.md" to trsA, "b.md" to trsB)).shouldContainExactly("要件 ID $c が、どの US/UC の出典にも現れない")
        RequirementTraceCheck.violations(reqOk, mapOf("a.md" to trsA)).shouldContainExactly(
            "要件 ID $b が、どの US/UC の出典にも現れない",
            "要件 ID $c が、どの US/UC の出典にも現れない",
        )
    }

    "反例: 存在しない要件 ID を US の出典に書くと違反になる（⑥ 綴りの誤り・要件の削除）" {
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf("$a, $b, REQ-TST-09"), listOf("$a, $b, $c"))))
            .shouldContainExactly("trs.md: US-01 の出典 REQ-TST-09 は要件定義書に無い要件 ID")
    }

    "反例: 存在しない要件 ID を UC の出典に書くと違反になる（⑥）" {
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf("$a, $b, $c"), listOf("$a, REQ-TST-09"))))
            .shouldContainExactly("trs.md: UC-01 の出典 REQ-TST-09 は要件定義書に無い要件 ID")
    }

    "反例: 出典が空欄・—・OI-ID・文書名や節番号・要件 ID の形でない値だと違反になる（⑥）" {
        listOf(
            "" to "出典が空欄",
            "—" to "出典が「—」（出典が無い）",
            "OI-001" to "出典 OI-001 は OI-ID（要件 ID ではない）",
            "03 §3.4" to "出典 03 §3.4 は要件 ID の形（REQ-<略号>-<番号>）でない",
            "REQ-TST-1" to "出典 REQ-TST-1 は要件 ID の形（REQ-<略号>-<番号>）でない",
            "req-tst-01" to "出典 req-tst-01 は要件 ID の形（REQ-<略号>-<番号>）でない",
            "`$a`" to "出典 `$a` は要件 ID の形（REQ-<略号>-<番号>）でない",
        ).forEach { (value, message) ->
            val all = "$a, $b, $c"
            RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf(all, value), listOf(all))))
                .shouldContainExactly("trs.md: US-02 の$message")
            RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf(all), listOf(all, value))))
                .shouldContainExactly("trs.md: UC-02 の$message")
        }
    }

    "反例: 区切りがカンマでない（全角の読点）、または末尾のカンマがあると違反になる（⑥）" {
        val all = "$a, $b, $c"
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf(all, "$a、$b"), listOf(all))))
            .shouldContainExactly("trs.md: US-02 の出典 $a、$b は要件 ID の形（REQ-<略号>-<番号>）でない")
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf(all, "$a,"), listOf(all))))
            .shouldContainExactly("trs.md: US-02 の出典が空欄")
    }

    "反例: 同じ要件 ID が複数の行にあると違反になる（⑥ 同じファイルの中・別のファイルの間）" {
        val sameFile = reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |", "| $a | 説明 | |", "| $c | 説明 | |"))
        RequirementTraceCheck.violations(mapOf("req.md" to sameFile), trsOk)
            .shouldContainExactly("要件 ID $a が複数の行にある（req.md:7, req.md:9）")
        val other = reqDocRows(listOf("| $c | 説明 | |"))
        RequirementTraceCheck.violations(mapOf("req1.md" to reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |")), "req2.md" to other, "req3.md" to other), trsOk)
            .shouldContainExactly("要件 ID $c が複数の行にある（req2.md:7, req3.md:7）")
    }

    "反例: 要件として読む表の外に要件 ID の形の文字列があると違反になる（地の文・引用・見出し・未決事項の表・コードブロック）" {
        listOf(
            "本文に REQ-TST-09 を書く。",
            "> 引用: REQ-TST-09",
            "## REQ-TST-09 の節",
            "| Q-02 | REQ-TST-09 を決める | オーナー | — |",
            "```text\nREQ-TST-09\n```",
            "- [ ] すべての要件に REQ-TST-09 が付いている",
        ).forEach { snippet ->
            val doc = reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |", "| $c | 説明 | |")) + "\n$snippet\n"
            val line = doc.lines().indexOfFirst { "REQ-TST-09" in it } + 1
            RequirementTraceCheck.violations(mapOf("req.md" to doc), trsOk)
                .shouldContainExactly("req.md: 要件として読む表の外に要件 ID の形の文字列がある（行 $line）: REQ-TST-09")
        }
    }

    "反例: 要件を読む節より前の地の文・メタ情報の表に要件 ID の形の文字列があっても違反になる" {
        val doc = "# 要件\n\n| 項目 | 内容 |\n|---|---|\n| 参考 | REQ-TST-09 |\n\n" + reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |", "| $c | 説明 | |"))
        RequirementTraceCheck.violations(mapOf("req.md" to doc), trsOk)
            .shouldContainExactly("req.md: 要件として読む表の外に要件 ID の形の文字列がある（行 5）: REQ-TST-09")
    }

    "反例: 要件の表で先頭列が要件 ID の形でない行があると違反になる" {
        listOf("REQ-TST-1", "**$d**", "`$d`", "TST-04", "", "REQ-TST-04x", "Q-04").forEach { first ->
            val doc = reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |", "| $c | 説明 | |", "| $first | 説明 | |"))
            RequirementTraceCheck.violations(mapOf("req.md" to doc), trsOk)
                .shouldContainExactly("req.md: 要件の表で先頭列が要件 ID の形でない行がある（行 10）: $first")
        }
    }

    "反例: 要件の表に区切り行が無いと違反になる（先頭の要件が見出し行として読み飛ばされない）" {
        val doc = "# 要件\n\n## 3. 機能要件\n\n| ID | 要件 | 備考 |\n| $a | 説明 | |\n| $b | 説明 | |\n| $c | 説明 | |\n"
        RequirementTraceCheck.violations(mapOf("req.md" to doc), trsOk).shouldContainExactly("req.md: 要件の表に区切り行が無い（行 6）")
    }

    "反例: 要件定義書があるのに要件が 1 件も読めないと違反になる（節が無い・表に行が無い）" {
        val message = "req.md: 要件が 1 件も読めない（機能要件・業務ルール・非機能要件の節の表に、先頭列が要件 ID の行を置く）"
        listOf("# 要件\n\n本文だけ。\n", reqDocRows(emptyList())).forEach { doc ->
            RequirementTraceCheck.violations(mapOf("req.md" to doc), mapOf("trs.md" to trsDoc(listOf(a), listOf(a)))).shouldContainExactly(
                message,
                "trs.md: US-01 の出典 $a は要件定義書に無い要件 ID",
                "trs.md: UC-01 の出典 $a は要件定義書に無い要件 ID",
            )
        }
    }

    "反例: 見出しに機能要件・業務ルール・非機能要件を含まない節の表は、要件として読まず違反になる" {
        val doc = "# 要件\n\n## 3. 機能\n\n| ID | 要件 |\n|---|---|\n| $a | 説明 |\n"
        RequirementTraceCheck.violations(mapOf("req.md" to doc), mapOf("trs.md" to trsDoc(listOf(a), listOf(a)))).shouldContainExactly(
            "req.md: 要件として読む表の外に要件 ID の形の文字列がある（行 7）: $a",
            "req.md: 要件が 1 件も読めない（機能要件・業務ルール・非機能要件の節の表に、先頭列が要件 ID の行を置く）",
            "trs.md: US-01 の出典 $a は要件定義書に無い要件 ID",
            "trs.md: UC-01 の出典 $a は要件定義書に無い要件 ID",
        )
    }

    "反例: TRS に US の行が無いと TRS 単位で違反になる" {
        val noUs = "# TRS\n\n#### UC-01: 例\n\n| 項目 | 内容 |\n|---|---|\n| 出典（要件 ID） | $a, $b, $c |\n"
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to noUs))
            .shouldContainExactly("trs.md: US の行が 1 つも無い（先頭列が US-<数字> の表の行）")
    }

    "反例: TRS に UC が無いと TRS 単位で違反になる（UC-01-A1 などの表の行は UC に数えない）" {
        val noUc = "# TRS\n\n| ID | ストーリー | 出典（要件 ID） |\n|---|---|---|\n| US-01 | 説明 | $a, $b, $c |\n\n| ID | 条件 |\n|---|---|\n| UC-01-A1 | 例 |\n"
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to noUc))
            .shouldContainExactly("trs.md: UC が 1 つも無い（見出しが UC-<数字>: で始まる節）")
    }

    "反例: US の表の見出しに出典（要件 ID）の列が無いと違反になる" {
        val noColumn = trsDoc(listOf("$a, $b, $c"), listOf("$a, $b, $c")).replace("| ID | ストーリー | 出典（要件 ID） |", "| ID | ストーリー | 出典 |")
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to noColumn))
            .shouldContainExactly("trs.md: US の表の見出しに「出典（要件 ID）」の列が無い（行 5）")
    }

    "反例: US の行の列数が見出しと合わないと違反になる" {
        val shortRow = trsDoc(listOf("$a, $b, $c"), listOf("$a, $b, $c")).replace("| US-01 | 説明 | $a, $b, $c |", "| US-01 | $a, $b, $c |")
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to shortRow))
            .shouldContainExactly("trs.md: US の表の列数が見出しと合わない: US-01")
    }

    "反例: UC に項目名が出典（要件 ID）の行が無いと違反になる（ほかの UC の行は数えない）" {
        val noRow = trsDoc(listOf("$a, $b, $c"), listOf("$a, $b, $c", "$a")).replace("| 出典（要件 ID） | $a, $b, $c |", "| 出典 | $a, $b, $c |")
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to noRow))
            .shouldContainExactly("trs.md: UC-01 に項目名「出典（要件 ID）」の行が無い")
    }

    "反例: UC の出典の行の列数が 2 でないと違反になる" {
        val threeColumns = trsDoc(listOf("$a, $b, $c"), listOf("$a, $b, $c")).replace("| 出典（要件 ID） | $a, $b, $c |", "| 出典（要件 ID） | $a, $b, $c | 余分 |")
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to threeColumns))
            .shouldContainExactly("trs.md: UC-01 の「出典（要件 ID）」の行の列数が 2 でない")
    }

    "正例: 業務ルールの節の表も要件として読む（機能要件・非機能要件と同じ）" {
        val rules = "# 要件\n\n## 4. 業務ルール\n\n| ID | ルール | 根拠 |\n|---|---|---|\n| $a | 説明 | 規約 |\n| $b | 説明 | 規約 |\n| $c | 説明 | 規約 |\n"
        RequirementTraceCheck.violations(mapOf("req.md" to rules), trsOk).shouldBeEmpty()
        RequirementTraceCheck.violations(mapOf("req.md" to rules), mapOf("trs.md" to trsDoc(listOf("$a, $b"), listOf("$b")))).shouldContainExactly("要件 ID $c が、どの US/UC の出典にも現れない")
    }

    "正例: 長いコードブロックの中の短い囲み・別の文字の囲み・HTML コメントの中は、見出しにも表にも読まない" {
        val hidden = "#### UC-09: 偽\n| 出典（要件 ID） | $d |\n"
        listOf("````text\n```\n$hidden```\n````", "~~~\n```\n$hidden~~~", "<!--\n$hidden-->", "<!-- #### UC-09: 偽 -->").forEach { block ->
            RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf("$a, $b"), listOf("$b, $c")) + "\n$block\n")).shouldBeEmpty()
        }
    }

    "反例: HTML コメントの中の要件 ID は、表の外の文字列として違反になる" {
        val doc = reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |", "| $c | 説明 | |", "<!-- | $d | 説明 | | -->"))
        RequirementTraceCheck.violations(mapOf("req.md" to doc), trsOk)
            .shouldContainExactly("req.md: 要件として読む表の外に要件 ID の形の文字列がある（行 10）: $d")
    }

    "反例: 要件の表の見出し行に要件 ID の形の文字列があると違反になる" {
        val doc = reqDocRows(listOf("| $a | 説明 | |", "| $b | 説明 | |", "| $c | 説明 | |")).replace("| ID | 要件 | 備考 |", "| ID | 要件 | $d |")
        RequirementTraceCheck.violations(mapOf("req.md" to doc), trsOk)
            .shouldContainExactly("req.md: 要件の表の見出し行に要件 ID の形の文字列がある（行 5）: $d")
    }

    "反例: 出典（要件 ID）の列がある表に、先頭列が US-<数字> でない行があると違反になる（形の崩れた US を読み飛ばさない）" {
        listOf("備考", "US-", "us-02", "**US-02**", "UC-02").forEach { first ->
            val trs = trsDoc(listOf("$a, $b, $c"), listOf("$a")).replace("| US-01 | 説明 | $a, $b, $c |", "| US-01 | 説明 | $a, $b, $c |\n| $first | 説明 | $a |")
            RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trs))
                .shouldContainExactly("trs.md: US の表に先頭列が US-<数字> の形でない行がある（行 8）: $first")
        }
    }

    "反例: 見出しが UC-<数字>: の形でない UC は違反になる（UC の節として読めないまま素通りしない）" {
        listOf("UC-07 削除する", "UC-07", "UC-07 - 削除する", "UC-7削除").forEach { title ->
            val trs = trsDoc(listOf("$a, $b, $c"), listOf("$a")) + "\n#### $title\n\n| 項目 | 内容 |\n|---|---|\n| 出典（要件 ID） | $a |\n"
            val line = trs.lines().indexOfFirst { it == "#### $title" } + 1
            RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trs)).shouldContainExactly(
                "trs.md: UC の見出しの形でない（行 $line。UC-<数字>: で始まる形にする）: $title",
                "trs.md: どの UC の節にも属さない「出典（要件 ID）」の行がある（行 ${line + 4}）",
            )
        }
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to trsDoc(listOf("$a, $b, $c"), listOf("$a")) + "\n#### UC-07：全角コロン\n\n| 項目 | 内容 |\n|---|---|\n| 出典（要件 ID） | $a |\n"))
            .shouldBeEmpty()
    }

    "反例: どの UC の節にも属さない 出典（要件 ID） の行があると違反になる" {
        val base = trsDoc(listOf("$a, $b, $c"), listOf("$a"))
        val row = "| 項目 | 内容 |\n|---|---|\n| 出典（要件 ID） | $a |\n"
        val afterUc = base + "\n### 3. その他\n\n$row"
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to afterUc))
            .shouldContainExactly("trs.md: どの UC の節にも属さない「出典（要件 ID）」の行がある（行 ${afterUc.lines().indexOfFirst { "その他" in it } + 5}）")
        val beforeUc = base.replace("### 2. ユースケース（UC）\n", "### 2. ユースケース（UC）\n\n$row")
        RequirementTraceCheck.violations(reqOk, mapOf("trs.md" to beforeUc))
            .shouldContainExactly("trs.md: どの UC の節にも属さない「出典（要件 ID）」の行がある（行 ${beforeUc.lines().indexOfFirst { "ユースケース（UC）" in it } + 5}）")
    }
})

/** 検査の入力用の最小の TRS。AC 表（`tags` の先頭の AC ID）、Gherkin のタグ行、Open Issues 表（OI-001 の1行）を持つ */
private fun sampleTrs(kind: String, related: String, state: String, vararg tags: String): String =
    sampleTrsRows(listOf("| OI-001 | $kind | $related | $state | |"), *tags)

/** [sampleTrs] の Open Issues 表の行（`| ID | 区分 | 関連 | 状態 |` の順）を複数指定できる版 */
private fun sampleTrsRows(oiRows: List<String>, vararg tags: String): String = buildString {
    appendLine("| AC ID | 関連 UC | テストレベル | 検証層 | 優先度 | 検証する振る舞い | 根拠 |")
    appendLine("|---|---|---|---|---|---|---|")
    tags.forEach { appendLine("| ${it.substringBefore(' ').removePrefix("@")} | UC-01 | 単体 | 層1 | Must | 説明 | REQ-TST-01 |") }
    appendLine()
    tags.forEach { appendLine(it) }
    appendLine()
    appendLine("| ID | 区分 | 関連 | 状態 | 反映先 |")
    appendLine("|---|---|---|---|---|")
    oiRows.forEach { appendLine(it) }
}

/** ⑤ ⑥ の検査の入力用の最小の要件定義書。機能要件の表に [rows]（行全体）を置き、未決事項の表（Q-01）を持つ。表の先頭の行は 7 行目 */
internal fun reqDocRows(rows: List<String>): String = buildString {
    appendLine("# 要件")
    appendLine()
    appendLine("## 3. 機能要件")
    appendLine()
    appendLine("| ID | 要件 | 備考 |")
    appendLine("|---|---|---|")
    rows.forEach { appendLine(it) }
    appendLine()
    appendLine("## 6. 未決事項")
    appendLine()
    appendLine("| ID | 内容 | 担当 | OI-ID |")
    appendLine("|---|---|---|---|")
    appendLine("| Q-01 | 問い | オーナー | — |")
}

/** ⑤ ⑥ の検査の入力用の最小の TRS。[us] の i 番目が US-0i の出典、[uc] の i 番目が UC-0i の出典になる */
internal fun trsDoc(us: List<String>, uc: List<String>): String = buildString {
    appendLine("# TRS")
    appendLine()
    appendLine("### 1. ユーザーストーリー（US）")
    appendLine()
    appendLine("| ID | ストーリー | 出典（要件 ID） |")
    appendLine("|---|---|---|")
    us.forEachIndexed { i, sources -> appendLine("| US-%02d | 説明 | $sources |".format(i + 1)) }
    appendLine()
    appendLine("### 2. ユースケース（UC）")
    uc.forEachIndexed { i, sources ->
        appendLine()
        appendLine("#### UC-%02d: 例".format(i + 1))
        appendLine()
        appendLine("| 項目 | 内容 |")
        appendLine("|---|---|")
        appendLine("| 関連 US | US-01 |")
        appendLine("| 出典（要件 ID） | $sources |")
        appendLine("| アクター | 本人 |")
    }
}

/** テスト名の検査（① ⑦）が、自己テストの入力の文字列をテスト名と取り違えないよう除くファイル */
internal val selfTestFiles = setOf("TraceabilityTest", "TrsSetCheckTest")
