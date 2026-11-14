package com.example.architecture

/**
 * TRS ファイルと生成物のファイルを組にして検査する（03 §3.4 の「検査の単位」と 03a §3 の書式）。
 * 入力は `(ファイル名, 本文)` の対応で、戻り値は違反の説明の一覧である（空なら合格）。実ファイルの検査と自己テストは、同じ [TrsSetCheck.violations] を呼ぶ。
 *
 * 読み取りの空振りで AC が黙って検査から外れないよう、次のどれも失敗にする。
 * - 組にならないファイル（どちら向きでも）
 * - TRS ファイルにある US の行・UC の節・AC の表の行
 * - 生成物のファイルに無い US・UC・AC の表の行
 * - 生成物のファイルにある Open Issues の表・因子・水準・禁止組合せの表（見出しの列名で見分ける）
 * - 見出しが規定と違う AC の表・組合せ別の期待結果の表
 *
 * 根拠の値の実在（03a §4 の ⑨）と、基本設計書の設計 ID との照合（同 ⑧）も、この [TrsSetCheck.violations] が行う。
 * AC の表と 5.2 の根拠の値の実在（[BasisCheck]）、設計 ID の書式と重複（[DesignIds.violations]）、設計 ID が全 TRS の根拠に現れること（[DesignIds.uncovered]）である。
 */

/** AC ID の範囲表記の展開（03a §3 の「② ③ の書式」）。Open Issues の関連列とトレーサビリティ表の先頭列で共用する */
internal object AcRefs {
    enum class Problem { UNREADABLE, REVERSED }

    class Expanded(val ids: List<String>, val problem: Problem?)

    private val acRange = Regex("""AC-(\d\d)-(\d\d)(?:[〜～~](?:AC-\1-)?(\d\d))?""")
    private val rangeMark = Regex("""[〜～~]""")

    /** `AC-xx-yy`、`AC-xx-yy〜zz`、`AC-xx-yy〜AC-xx-zz` を展開する。範囲として読めない区切りが残る、または逆順のときは、[Expanded.problem] に理由を返す */
    fun expand(text: String): Expanded {
        var reversed = false
        val ids = acRange.findAll(text).flatMap { m ->
            val (group, from, to) = m.destructured
            val last = to.ifEmpty { from }.toInt()
            if (last < from.toInt()) reversed = true
            (from.toInt()..last).map { "AC-$group-%02d".format(it) }
        }.toList()
        val leftover = rangeMark.containsMatchIn(acRange.replace(text, ""))
        val problem = when {
            leftover -> Problem.UNREADABLE
            reversed -> Problem.REVERSED
            else -> null
        }
        return Expanded(ids, problem)
    }
}

/** Markdown の表の読み取り。コードブロックと HTML コメントの中は表にしない（[RequirementTraceCheck.scan] と同じ範囲） */
internal object TrsTables {
    /** [header] は見出し行のセル、[rows] は見出し行と区切り行を除いたデータ行 */
    class Table(val headerLine: Int, val header: List<String>, val rows: List<RequirementTraceCheck.Line>)

    private fun isSeparator(cells: List<String>) = cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }

    fun read(text: String): List<Table> =
        RequirementTraceCheck.scan(text).filter { it.table >= 0 }.groupBy { it.table }.values.map { lines ->
            Table(lines.first().no, lines.first().cells, lines.drop(1).filterNot { isSeparator(it.cells) })
        }
}

/** 生成物のファイルの AC の表（テンプレート §3）。見出しの完全一致で読む（03a §3 の「① ④ の書式」） */
internal object AcTable {
    val header = listOf("AC ID", "関連 UC", "テストレベル", "検証層", "優先度", "検証する振る舞い", "根拠")

    /** TRS ファイルのトレーサビリティ表（テンプレート §7）の見出し。AC の表ではない */
    val traceabilityHeader = listOf("AC ID", "層1（型/Unit/PBT）", "層2（Integration/E2E）", "層3（探索）", "状態")

    private val acForm = Regex("""^AC-\d\d-\d\d$""")

    class Row(val id: String, val line: Int, val basis: String)

    class Read(val rows: List<Row>, val errors: List<String>)

    fun isAcId(value: String) = acForm.matches(value)

    /** 見出しが 7 列の形ではないが、AC の表として書かれたように見える表（見出しの先頭が `AC ID`、または先頭列が AC ID の行がある） */
    fun looksLikeAcTable(table: TrsTables.Table) = table.header.firstOrNull() == "AC ID" || table.rows.any { isAcId(it.cells.firstOrNull().orEmpty()) }

    fun read(text: String): Read {
        val rows = mutableListOf<Row>()
        val errors = mutableListOf<String>()
        TrsTables.read(text).forEach { table ->
            when {
                table.header == header -> table.rows.forEach { l ->
                    when {
                        l.cells.size != header.size -> errors += "AC の表の列数が見出しと合わない（行 ${l.no}）"
                        !isAcId(l.cells.first()) -> errors += "AC の表に先頭列が AC-xx-yy の形でない行がある（行 ${l.no}）: ${l.cells.first()}"
                        else -> rows += Row(l.cells.first(), l.no, l.cells.last())
                    }
                }
                table.header == traceabilityHeader -> Unit // AC の表ではない。TRS ファイルにだけ置く（[GeneratedFileCheck] が、生成物のファイルにあれば失敗にする）
                looksLikeAcTable(table) ->
                    errors += "AC の表の見出しが読めない（行 ${table.headerLine}。見出しは ${header.joinToString(" | ")} にする）: ${table.header.joinToString(" | ")}"
                else -> Unit
            }
        }
        return Read(rows, errors)
    }
}

/** TRS ファイルに置いてはならないもの（US の行・UC の節・AC の表の行）。AC を生成物のファイルだけに置くため（03 §3.4） */
internal object TrsFileCheck {
    private val usId = Regex("""^US-\d+$""")
    private val ucHeading = Regex("""^UC-\d+""")

    fun violations(text: String): List<String> {
        val lines = RequirementTraceCheck.scan(text)
        val usRows = lines.filter { it.table >= 0 && it.row >= 1 && usId.matches(it.cells.first()) }
        val ucSections = lines.filter { it.level > 0 && ucHeading.containsMatchIn(it.title) }
        val acRows = TrsTables.read(text).filter { it.header != AcTable.traceabilityHeader }.flatMap { table ->
            val asAcTable = table.header == AcTable.header
            table.rows.filter { asAcTable || AcTable.isAcId(it.cells.first()) }
        }
        return listOfNotNull(
            usRows.firstOrNull()?.let { "TRS ファイルに US の表の行がある（先頭は行 ${it.no}、計 ${usRows.size} 行。US は生成物のファイルに置く）" },
            ucSections.firstOrNull()?.let { "TRS ファイルに UC の節がある（先頭は行 ${it.no}、計 ${ucSections.size} 節。UC は生成物のファイルに置く）" },
            acRows.firstOrNull()?.let { "TRS ファイルに AC の表の行がある（先頭は行 ${it.no}、計 ${acRows.size} 行。AC は生成物のファイルに置く）" },
        )
    }
}

/**
 * 生成物のファイルに置いてはならない表（Open Issues・因子・水準・禁止組合せ）。見出しの列名（セルの完全一致）で見分ける（03a §3）。
 * トレーサビリティ表（テンプレート §7）も、運用情報なので TRS ファイルにだけ置く（03b §4）。
 */
internal object GeneratedFileCheck {
    fun violations(text: String): List<String> = TrsTables.read(text).mapNotNull { table ->
        val names = table.header.toSet()
        val kind = when {
            table.header == AcTable.traceabilityHeader -> "トレーサビリティ表"
            setOf("ID", "区分", "関連", "状態").all { it in names } -> "Open Issues の表"
            "因子ID" in names || "水準ID" in names -> "因子・水準の表"
            setOf("制約ID", "組合せ", "扱い").all { it in names } -> "禁止組合せの表"
            else -> null
        }
        kind?.let { "生成物のファイルに $it がある（行 ${table.headerLine}。TRS ファイルに置く）" }
    }
}

/** 組合せ別の期待結果（テンプレート §5.2）の表と、期待結果・根拠の判定（03a §3） */
internal object ExpectationTable {
    val header = listOf("制約ID", "条件", "期待結果", "根拠（要件 ID / 設計 ID / OI）")

    private val constraintId = Regex("""^C-\d\d$""")
    private val oiRef = Regex("""OI-\d+""")
    private val quoted = Regex("""「[^」]*」|`[^`]*`""")
    private val generationWords = listOf("テスト対象外", "テストの対象外", "テスト不要", "テストしない", "型で排除", "ケースを生成しない")
    private val undecidedWords = listOf("未決", "未決定", "未定", "決定後", "決定待ち", "後で決める", "要確認", "TBD")
    private val exceptionWords = listOf("未決済", "未定義")
    private val open = setOf('(', '（')
    private val close = setOf(')', '）')

    class Row(val id: String, val line: Int, val expected: String, val basis: String)

    class Read(val rows: List<Row>, val errors: List<String>)

    fun read(text: String): Read {
        val rows = mutableListOf<Row>()
        val errors = mutableListOf<String>()
        var found = false
        TrsTables.read(text).forEach { table ->
            val names = table.header.toSet()
            val forbiddenTable = setOf("制約ID", "組合せ", "扱い").all { it in names }
            when {
                table.header == header -> {
                    found = true
                    table.rows.forEach { l ->
                        when {
                            l.cells.size != header.size -> errors += "組合せ別の期待結果の表の列数が見出しと合わない（行 ${l.no}）"
                            !constraintId.matches(l.cells.first()) -> errors += "組合せ別の期待結果の表に先頭列が C-xx の形でない行がある（行 ${l.no}）: ${l.cells.first()}"
                            else -> rows += Row(l.cells.first(), l.no, l.cells[2], l.cells[3])
                        }
                    }
                }
                "制約ID" in names && !forbiddenTable ->
                    errors += "組合せ別の期待結果の表の見出しが読めない（行 ${table.headerLine}。見出しは ${header.joinToString(" | ")} にする）: ${table.header.joinToString(" | ")}"
                else -> Unit
            }
        }
        if (!found) errors += "組合せ別の期待結果（5.2）の表が見つからない（見出しは ${header.joinToString(" | ")}）"
        return Read(rows, errors)
    }

    /** 全体が丸括弧で囲まれているか。「」と `` ` `` で囲んだ部分は、先に除いてから見る。先頭の開き括弧に対応する閉じ括弧が、末尾にあることを条件にする */
    private fun wholeParenthesized(raw: String): Boolean {
        val s = quoted.replace(raw, "").trim()
        if (s.isEmpty() || s.first() !in open) return false
        var depth = 0
        for ((i, ch) in s.withIndex()) {
            if (ch in open) depth++
            if (ch in close) {
                depth--
                if (depth == 0) return i == s.lastIndex
            }
        }
        return false
    }

    /** 期待結果のセルの違反。語は部分一致で、例外の語（未決済・未定義）の一部として現れるときは当たらない */
    private fun expectedProblems(raw: String): List<String> {
        val words = exceptionWords.fold(raw) { acc, w -> acc.replace(w, "") }
        val problems = mutableListOf<String>()
        if (raw.isBlank() || raw.trim() == "—" || raw.trim() == "-") problems += "具体的な期待結果が無い（空、—、-）"
        if (wholeParenthesized(raw)) problems += "具体的な期待結果が無い（全体が丸括弧）"
        problems += generationWords.filter { it in words }.map { "ケースを生成しない文言「$it」がある" }
        problems += undecidedWords.filter { it in words }.map { "期待値が未決の文言「$it」がある" }
        problems += oiRef.findAll(raw).map { it.value }.distinct().map { "OI（$it）を書いている。OI への言及は根拠の列に書く" }
        return problems
    }

    /** 期待結果と根拠の違反。[unresolvedBlocking] は、組になる TRS ファイルの Open Issues 表の、未解決の Blocking な OI-ID */
    fun violations(read: Read, unresolvedBlocking: Set<String>): List<String> {
        val perRow = read.rows.flatMap { row ->
            val expected = expectedProblems(row.expected).map { "${row.id}: 期待結果に、$it" }
            val basis = oiRef.findAll(row.basis).map { it.value }.distinct().filter { it in unresolvedBlocking }
                .map { "${row.id}: 根拠が未解決の Blocking な $it に依存している（期待値が未決の組合せは、行を置かず @pending の AC で表す）" }
            expected + basis
        }
        return read.errors + perRow
    }
}

/**
 * 根拠の列の値の実在の検査（`C-xx`、要件 ID、設計 ID、OI-ID。03a §4 の ⑨）。意味の一致は検査できないので、AI のレビューが補う。
 * AC の表（[violations]）と組合せ別の期待結果 5.2（[expectationViolations]）の根拠を、同じ判定（[problem]）で調べる。
 * 空欄、実在しない値、どの形にも当たらない値が違反である。制約 ID は AC の表にだけ書け、5.2 の根拠に書くと違反である。`DES-` で始まるのに設計 ID の書式でない値は、綴りの誤りが実在の検査をすり抜けないよう、書式の違反として別の文言で報告する。
 */
internal object BasisCheck {
    private const val DESIGN_PREFIX = "DES-"
    private val constraintId = Regex("""^C-\d\d$""")
    private val requirementId = Regex("""^REQ-[A-Z]{2,6}-\d{2,}$""")
    private val oiId = Regex("""^OI-\d+$""")

    class Known(val constraints: Set<String>, val requirements: Set<String>, val designs: Set<String>, val openIssues: Set<String>)

    private fun designProblem(value: String, designs: Set<String>): String? = when {
        !DesignIds.isId(value) -> "根拠 $value は DES- で始まるが、設計 ID の書式（DES-<略号>-<番号>。略号は英大文字 2〜6、番号は 2 桁以上）でない"
        value !in designs -> "根拠 $value は基本設計書の節 2〜9 に無い設計 ID"
        else -> null
    }

    private fun problem(value: String, known: Known): String? = when {
        value.isEmpty() -> "根拠が空欄"
        value.startsWith(DESIGN_PREFIX) -> designProblem(value, known.designs)
        constraintId.matches(value) -> "根拠 $value は組合せ別の期待結果（5.2）に無い制約 ID".takeIf { value !in known.constraints }
        requirementId.matches(value) -> "根拠 $value は要件定義書に無い要件 ID".takeIf { value !in known.requirements }
        oiId.matches(value) -> "根拠 $value は Open Issues 表に無い OI-ID".takeIf { value !in known.openIssues }
        else -> "根拠 $value は C-xx・要件 ID・設計 ID・OI-ID のどれの形でもない（区切りは半角のカンマ）"
    }

    private fun values(basis: String) = basis.split(",").map { it.trim() }

    fun violations(rows: List<AcTable.Row>, known: Known): List<String> =
        rows.flatMap { row -> values(row.basis).mapNotNull { problem(it, known) }.distinct().map { "${row.id}: $it" } }

    /** 5.2 の根拠の値の、空欄・形・実在（AC の表と同じ判定）。制約 ID は、実在するものでも書けない（03a §4 の ⑨） */
    fun expectationViolations(rows: List<ExpectationTable.Row>, known: Known): List<String> =
        rows.flatMap { row ->
            values(row.basis).mapNotNull { value ->
                if (constraintId.matches(value)) "根拠 $value は制約 ID である。組合せ別の期待結果の根拠には、制約 ID を書けない" else problem(value, known)
            }.distinct().map { "${row.id}: $it" }
        }

    /** ⑧ 生成物のファイルの、AC の表と 5.2 の根拠に現れた設計 ID（`@pending` の AC の根拠も数える）。書式の違う値は数えない */
    fun cited(generatedText: String): Set<String> =
        (AcTable.read(generatedText).rows.map { it.basis } + ExpectationTable.read(generatedText).rows.map { it.basis })
            .flatMap { values(it) }.filter { DesignIds.isId(it) }.toSet()
}

/** TRS ファイルのトレーサビリティ表（テンプレート §7）の AC ID を、AC の表と照合する（03 §3.4 の検査の単位） */
internal object TraceabilityTable {
    class Entry(val id: String, val line: Int)

    class Read(val entries: List<Entry>, val errors: List<String>)

    fun read(text: String): Read {
        val entries = mutableListOf<Entry>()
        val errors = mutableListOf<String>()
        TrsTables.read(text).filter { it.header == AcTable.traceabilityHeader }.forEach { table ->
            table.rows.forEach { l ->
                val expanded = AcRefs.expand(l.cells.first())
                when {
                    l.cells.size != table.header.size -> errors += "トレーサビリティ表の列数が見出しと合わない（行 ${l.no}）"
                    expanded.problem != null -> errors += "トレーサビリティ表の AC ID の範囲表記を読めない（行 ${l.no}）: ${l.cells.first()}"
                    expanded.ids.isEmpty() -> errors += "トレーサビリティ表の先頭列に AC ID が無い（行 ${l.no}）: ${l.cells.first()}"
                    else -> entries += expanded.ids.map { Entry(it, l.no) }
                }
            }
        }
        return Read(entries, errors)
    }

    /** [exempt] は、照合から外す AC（未解決でない Open Issue の反映先に `削除` と書いたもの） */
    fun violations(trsText: String, acIds: Set<String>, exempt: Set<String>): List<String> {
        val read = read(trsText)
        return read.errors + read.entries.filter { it.id !in acIds && it.id !in exempt }
            .map { "トレーサビリティ表の ${it.id} が、生成物のファイルの AC の表に無い（行 ${it.line}）" }
    }
}

/** ① AC に対応するテストの検査と、⑦ テスト名の AC ID の実在の検査（03a §2） */
internal object TestNameCheck {
    private val pendingTag = Regex("""@(AC-\d\d-\d\d)\b[^\n]*@pending:""")
    private val leadingIds = Regex("""["`](AC-\d\d-\d\d(?:[ \t]+AC-\d\d-\d\d)*)""")
    private val acId = Regex("""AC-\d\d-\d\d""")

    /** テスト名（Kotest の文字列名・JUnit のバッククォート名）の先頭に置かれた AC ID */
    fun testedIds(testSources: String): Set<String> = leadingIds.findAll(testSources).flatMap { acId.findAll(it.groupValues[1]).map { m -> m.value } }.toSet()

    /** ① `@pending` を付けた AC 以外の AC に、先頭に AC ID を持つテストが無いもの */
    fun untested(generatedDocs: Map<String, String>, testSources: String): List<String> {
        val tested = testedIds(testSources)
        return generatedDocs.flatMap { (name, text) ->
            val pending = pendingTag.findAll(text).map { it.groupValues[1] }.toSet()
            AcTable.read(text).rows.map { it.id }.distinct().filter { it !in pending && it !in tested }.map { "$name: $it にテストが無い" }
        }
    }

    /** ⑦ テスト名の先頭の AC ID が、どの TRS の AC の表にも無いもの */
    fun unknown(generatedDocs: Map<String, String>, testSources: String): List<String> {
        val known = generatedDocs.values.flatMap { text -> AcTable.read(text).rows.map { it.id } }.toSet()
        return (testedIds(testSources) - known).sorted().map { "テスト名の先頭の $it が、どの TRS の AC の表にも無い" }
    }
}

/** TRS ファイルと生成物のファイルの組を、03 §3.4 の失敗条件で検査する */
internal object TrsSetCheck {
    private val gherkinTag = Regex("""@(AC-\d\d-\d\d)\b""")
    private val deletion = Regex("""^(AC-\d\d-\d\d) 削除$""")

    /** 組にしたうえで、TRS ファイルと生成物のファイルの内容の検査（[contentViolations]）以外の、構造の検査をする */
    private fun structureViolations(trsText: String, generatedText: String): List<String> =
        TrsFileCheck.violations(trsText) + GeneratedFileCheck.violations(generatedText)

    /** 未解決でない Open Issue の反映先に、`AC-xx-yy 削除` と書いた AC。照合から外す（03 §3.4 の検査の単位） */
    fun exemptions(rows: List<UnresolvedBlockingCheck.Row>): Set<String> =
        rows.filter { !it.unresolved && UnresolvedBlockingCheck.isResolvedState(it.state) }
            .flatMap { row -> row.reflect.split(",").mapNotNull { deletion.find(it.trim())?.groupValues?.get(1) } }.toSet()

    /**
     * AC の表・Gherkin・Open Issues・組合せ別の期待結果・トレーサビリティ表の検査。TRS のテンプレートのように、1 つのファイルに全節を置く本文にも使える。
     * 組にならないファイルや、置き場の検査（[structureViolations]）と、根拠の実在の検査（[BasisCheck]）は含まない。
     */
    fun contentViolations(trsText: String, generatedText: String): List<String> {
        val ac = AcTable.read(generatedText)
        val ids = ac.rows.map { it.id }
        val (oiRows, _) = UnresolvedBlockingCheck.parse(trsText)
        val unresolvedBlocking = oiRows.filter { it.blocking && it.unresolved }.map { it.id }.toSet()
        return ac.errors +
            listOfNotNull("AC の表の行が 1 つも無い（見出しが ${AcTable.header.joinToString(" | ")} の表に、AC の行を 1 つ以上置く）".takeIf { ids.isEmpty() }) +
            ids.groupBy { it }.filterValues { it.size > 1 }.keys.map { "AC の表に同じ ID が複数の行にある: $it" } +
            gherkinTag.findAll(generatedText).map { it.groupValues[1] }.distinct().filter { it !in ids }.map { "Gherkin の @$it が、AC の表に無い" } +
            UnresolvedBlockingCheck.violations(trsText, generatedText) +
            ExpectationTable.violations(ExpectationTable.read(generatedText), unresolvedBlocking) +
            TraceabilityTable.violations(trsText, ids.toSet(), exemptions(oiRows))
    }

    /** 1 組の検査。根拠の実在の照合先のうち、要件 ID と設計 ID は全文書から集めた [requirements]・[designs]、制約 ID（5.2）と OI-ID は組から読む */
    private fun pairViolations(trsText: String, generatedText: String, requirements: Set<String>, designs: Set<String>): List<String> {
        val (oiRows, _) = UnresolvedBlockingCheck.parse(trsText)
        val known = BasisCheck.Known(
            constraints = ExpectationTable.read(generatedText).rows.map { it.id }.toSet(),
            requirements = requirements,
            designs = designs,
            openIssues = oiRows.map { it.id }.toSet(),
        )
        return structureViolations(trsText, generatedText) +
            contentViolations(trsText, generatedText) +
            BasisCheck.violations(AcTable.read(generatedText).rows, known) +
            BasisCheck.expectationViolations(ExpectationTable.read(generatedText).rows, known)
    }

    /** 違反の説明。空なら合格。引数は、TRS ファイル・生成物のファイル・要件定義書・基本設計書の `(ファイル名, 本文)`。ファイル名は TRS ファイルと生成物のファイルで同じになる */
    fun violations(
        trsDocs: Map<String, String>,
        generatedDocs: Map<String, String>,
        requirementDocs: Map<String, String>,
        designDocs: Map<String, String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        (trsDocs.keys - generatedDocs.keys).sorted().forEach { errors += "$it: 対になる生成物のファイルが無い（TRS ファイルだけがある）" }
        (generatedDocs.keys - trsDocs.keys).sorted().forEach { errors += "$it: 対になる TRS ファイルが無い（生成物のファイルだけがある）" }
        val requirements = requirementDocs.values.flatMap { RequirementTraceCheck.requirements(it).items.map { r -> r.id } }.toSet()
        val designs = designDocs.values.flatMap { DesignIds.read(it) }.toSet()
        errors += DesignIds.violations(designDocs)
        errors += DesignIds.uncovered(designDocs, generatedDocs.values.flatMap { BasisCheck.cited(it) }.toSet())
        (trsDocs.keys intersect generatedDocs.keys).sorted().forEach { name ->
            errors += pairViolations(trsDocs.getValue(name), generatedDocs.getValue(name), requirements, designs).map { "$name: $it" }
        }
        generatedDocs.flatMap { (name, text) -> AcTable.read(text).rows.map { it.id to name }.toSet() }
            .groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
            .forEach { (ac, files) -> errors += "AC ID $ac が複数の TRS にある（${files.joinToString()}）" }
        errors += RequirementTraceCheck.violations(requirementDocs, generatedDocs)
        return errors.distinct()
    }
}
