package com.example.architecture

/**
 * 要件定義書と基本設計書の、文書の中身の検査（03a §5 の ⑩ 目次、⑪ 用語の言い換え）。
 * 入力は `(ファイル名, 本文)` の対応で、戻り値は違反の説明の一覧である（空なら合格）。実ファイルの検査と自己テストは、同じ関数を呼ぶ。
 * 節・表・見出しの読み取りは [RequirementTraceCheck.scan] と同じで、コードブロックと HTML コメントの中は見出しにも表にもしない。
 *
 * ⑩ 目次（[tocViolations]）:
 * - 節は、文書の題（`#`）の直下の見出し（`##`）のうち、`<番号>.` で始まるもの。番号の無い見出し（メタ情報、レビューチェックリスト）は読まない。
 * - 目次の正は、03b §2 の 2 つの一覧（[requirementToc]・[designToc]）である。節の番号・名前・順序・数のどれかが違えば違反とする。小見出しは照合しない。
 *
 * ⑪ 用語の言い換え（[termViolations]）:
 * - 要件定義書の節 2 の、見出しに「用語」を含む小見出しの表（見出し `用語` / `意味` / `使わない言い換え`）から、言い換えの語を集める。
 *   「該当なし」は語なし。語は読点（、）か半角のカンマで区切る。
 * - 同じファイル名の、要件定義書・基本設計書・TRS ファイル・生成物のファイルの本文に語があれば違反とする。部分一致で、英字の大小は区別しない。
 *   先に、その言い換えの語を含む用語だけを、本文から除く（長い用語から）。コードブロックとインラインコードも対象にする。
 * - 検査しない範囲: 要件定義書の用語の小見出し、要件定義書（8）と基本設計書（10）の未決事項の節、TRS ファイルの `###` の節 6 Open Issues・7 トレーサビリティ・8 不具合還元履歴・9 変更履歴。
 *   節は、見出しの深さ・番号・名前の完全一致で見分ける（題や UC の名前に同じ語があっても、本文を除外しない）。
 * - 言い換えの語が、用語の列のどれかの語と同じとき（大小の違いを除く）も違反とする。用語の表が矛盾し、その語は先に除かれて検出できないためである。
 * - 同じ名前の要件定義書が無い基本設計書・TRS ファイルは違反とする。用語の小見出し・表の見出しが読めないとき、「使わない言い換え」のセルが空のときも違反とする。
 */
internal object InputDocCheck {
    /** 03b §2 の目次（要件定義書）。節の番号は、先頭を 1 とした並びの位置である */
    val requirementToc = listOf("目的と範囲", "利用者と用語", "前提と制約", "業務フロー", "機能要件", "業務ルール", "非機能要件", "未決事項")

    /** 03b §2 の目次（基本設計書）。節の番号は、先頭を 1 とした並びの位置である */
    val designToc = listOf("全体構成", "画面", "帳票", "入力項目とデータ", "状態と操作", "バッチ", "外部インタフェース", "メッセージ", "非機能の実現方式", "未決事項")

    val termHeader = listOf("用語", "意味", "使わない言い換え")

    private const val NONE = "該当なし"
    private const val UNDECIDED = "未決事項"
    private val numbered = Regex("""^(\d+)\.\s*(.+)$""")
    private val wordSeparator = Regex("""[、,]""")

    class Section(val number: Int, val name: String, val line: Int)

    /** 文書の題の直下の、番号つきの見出し（`## <番号>. <名前>`） */
    fun sections(text: String): List<Section> =
        RequirementTraceCheck.scan(text).filter { it.level == 2 }.mapNotNull { l ->
            numbered.find(l.title.trim())?.let { Section(it.groupValues[1].toInt(), it.groupValues[2].trim(), l.no) }
        }

    /** ⑩ 目次の違反。[kind] は文書の種類（メッセージ用）、[toc] は目次の名前を順に並べたもの */
    fun tocViolations(kind: String, docs: Map<String, String>, toc: List<String>): List<String> = docs.flatMap { (name, text) ->
        val actual = sections(text)
        val names = actual.map { it.name }
        val listing = toc.withIndex().joinToString("、") { (i, n) -> "${i + 1}. $n" }
        val missing = toc.filter { it !in names }.map { "$name: $kind に節「$it」が無い（目次: $listing）" }
        val extra = actual.filter { it.name !in toc }.map { "$name: $kind に目次に無い節「${it.number}. ${it.name}」がある（行 ${it.line}。目次: $listing）" }
        val duplicated = actual.groupBy { it.name }.filter { (n, v) -> n in toc && v.size > 1 }.keys.map { "$name: $kind に同じ名前の節「$it」が複数ある" }
        val expected = toc.withIndex().map { (i, n) -> (i + 1) to n }
        val order = listOf("$name: $kind の節の番号か順序が、目次と違う（目次: $listing。実際: ${actual.joinToString("、") { "${it.number}. ${it.name}" }}）")
            .takeIf { missing.isEmpty() && extra.isEmpty() && duplicated.isEmpty() && actual.map { it.number to it.name } != expected }
            .orEmpty()
        missing + extra + duplicated + order
    }

    /** 見出し行 [heading] から、次の同じ深さ以上の見出しの手前までの行番号 */
    private fun subtree(lines: List<RequirementTraceCheck.Line>, heading: RequirementTraceCheck.Line): Set<Int> =
        lines.dropWhile { it.no <= heading.no }.takeWhile { it.level == 0 || it.level > heading.level }.map { it.no }.toSet() + heading.no

    /** 見出しが [matches] に当たる節（小見出しを含む）の行番号 */
    private fun excluded(lines: List<RequirementTraceCheck.Line>, matches: (RequirementTraceCheck.Line) -> Boolean): Set<Int> =
        lines.filter { it.level > 0 && matches(it) }.flatMap { subtree(lines, it) }.toSet()

    /** 見出しの `<番号>. <名前>` を、番号と名前に分ける。番号が無ければ null */
    private fun numberAndName(title: String): Pair<Int, String>? = numbered.find(title.trim())?.let { it.groupValues[1].toInt() to it.groupValues[2].trim() }

    /** TRS ファイルの検査しない節（03a §5）。6 Open Issues、7 トレーサビリティ、8 不具合還元履歴、9 変更履歴 */
    private val operationalSections = setOf(6 to "Open Issues", 7 to "トレーサビリティ", 8 to "不具合還元履歴", 9 to "変更履歴")

    /**
     * 要件定義書から読んだ、用語と言い換え。[excludedLines] は検査しない行（用語の小見出し）で、
     * [errors] は読めなかった理由。[terms] は用語の列の語、[words] は「使わない言い換え」の語
     */
    class Terms(val terms: List<String>, val words: List<String>, val excludedLines: Set<Int>, val errors: List<String>)

    fun terms(text: String): Terms {
        val lines = RequirementTraceCheck.scan(text)
        val errors = mutableListOf<String>()
        val section2 = lines.firstOrNull { it.level == 2 && numbered.find(it.title.trim())?.groupValues?.get(1) == "2" }
        val glossary = if (section2 == null) emptyList() else {
            val inside = subtree(lines, section2)
            lines.filter { it.no in inside && it.level > 2 && "用語" in it.title }
        }
        if (glossary.isEmpty()) errors += "用語の小見出しが読めない（節 2 に、見出しに「用語」を含む小見出しを置く）"
        val range = glossary.flatMap { subtree(lines, it) }.toSet()
        val terms = mutableListOf<String>()
        val words = mutableListOf<String>()
        var found = false
        lines.filter { it.no in range && it.table >= 0 }.groupBy { it.table }.values.forEach { table ->
            if (table.first().cells != termHeader) return@forEach
            found = true
            table.drop(1).filterNot { l -> l.cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } } }.forEach { l ->
                if (l.cells.size != termHeader.size) {
                    errors += "用語の表の列数が見出しと合わない（行 ${l.no}）"
                    return@forEach
                }
                l.cells[0].takeIf { it.isNotEmpty() }?.let { terms += it }
                val cell = l.cells[2]
                when {
                    cell.isEmpty() -> errors += "用語の表の「使わない言い換え」が空（行 ${l.no}。無いときは「$NONE」と書く）: ${l.cells[0]}"
                    cell == NONE -> Unit
                    else -> cell.split(wordSeparator).map { it.trim() }.forEach { w ->
                        if (w.isEmpty()) errors += "用語の表の「使わない言い換え」に空の語がある（行 ${l.no}。区切りは読点か半角のカンマ）: $cell" else words += w
                    }
                }
            }
        }
        if (glossary.isNotEmpty() && !found) errors += "用語の表の見出しが読めない（見出しは ${termHeader.joinToString(" | ")} にする）"
        val termSet = terms.map { it.lowercase() }.toSet()
        words.distinct().filter { it.lowercase() in termSet }
            .forEach { errors += "用語の表が矛盾している（言い換え「$it」が、用語の列の語と同じ。用語は本文から先に除かれ、言い換えを検出できない）" }
        return Terms(terms, words, range, errors)
    }

    /**
     * 検査しない節を除いた本文で、言い換えの語が現れる行を調べる。語ごとに、その語を含む用語だけを、長いものから先に除いてから判定する。
     * 用語を全部除くと、言い換えが用語を含むとき（用語「期限」、言い換え「期限日」）に、本文の言い換えまで消えてしまうためである
     */
    private fun wordViolations(label: String, name: String, text: String, skip: Set<Int>, read: Terms): List<String> {
        val removable = read.words.distinct().associateWith { w -> read.terms.filter { w.lowercase() in it.lowercase() }.sortedByDescending { it.length } }
        return RequirementTraceCheck.scan(text).filter { it.no !in skip }.flatMap { l ->
            val lower = l.text.lowercase()
            removable.filter { (w, ts) -> w.lowercase() in ts.fold(lower) { acc, t -> acc.replace(t.lowercase(), "") } }
                .keys.map { "$name: $label の行 ${l.no} に、使わない言い換え「$it」がある" }
        }
    }

    /** 未決事項の節。深さ 2 で、見出しの番号が [number]、名前が `未決事項` に完全一致するもの。要件定義書は 8、基本設計書は 10 */
    private fun undecided(text: String, number: Int): Set<Int> = RequirementTraceCheck.scan(text).let { lines ->
        excluded(lines) { it.level == 2 && numberAndName(it.title) == (number to UNDECIDED) }
    }

    /** TRS ファイルの検査しない節。深さ 3 で、見出しの番号と名前が [operationalSections] のどれかに完全一致するもの */
    private fun operational(text: String): Set<Int> = RequirementTraceCheck.scan(text).let { lines ->
        excluded(lines) { it.level == 3 && numberAndName(it.title) in operationalSections }
    }

    /** ⑪ の違反。引数は、全要件定義書・基本設計書・TRS ファイル・生成物のファイルの `(ファイル名, 本文)` */
    fun termViolations(
        requirementDocs: Map<String, String>,
        designDocs: Map<String, String>,
        trsDocs: Map<String, String>,
        generatedDocs: Map<String, String>,
    ): List<String> {
        val errors = mutableListOf<String>()
        (designDocs.keys - requirementDocs.keys).sorted().forEach { errors += "$it: 同じ名前の要件定義書が無い（基本設計書。用語を確かめられない）" }
        (trsDocs.keys - requirementDocs.keys).sorted().forEach { errors += "$it: 同じ名前の要件定義書が無い（TRS ファイル。用語を確かめられない）" }
        requirementDocs.forEach { (name, text) ->
            val read = terms(text)
            errors += read.errors.map { "$name: 要件定義書の$it" }
            errors += wordViolations("要件定義書", name, text, read.excludedLines + undecided(text, requirementToc.size), read)
            designDocs[name]?.let { errors += wordViolations("基本設計書", name, it, undecided(it, designToc.size), read) }
            trsDocs[name]?.let { errors += wordViolations("TRS ファイル", name, it, operational(it), read) }
            generatedDocs[name]?.let { errors += wordViolations("生成物のファイル", name, it, emptySet(), read) }
        }
        return errors
    }
}
