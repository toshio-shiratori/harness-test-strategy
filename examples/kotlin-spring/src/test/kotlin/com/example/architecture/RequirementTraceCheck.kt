package com.example.architecture

/**
 * 要件定義書の要件 ID と、TRS の US/UC の出典（要件 ID）の対応を検査する（03 §3.4.3・§3.4.4 の ⑤ ⑥）。
 * 入力は `(ファイル名, 本文)` の対応で、戻り値は違反の説明の一覧である（空なら合格）。実ファイルの検査と自己テストは、同じ [violations] を呼ぶ。
 *
 * 読み取りでは、コードブロックと HTML コメントの中を見出しにも表にも数えない。HTML コメントの中の要件 ID は、表の外の文字列として違反になる。
 *
 * 要件定義書の読み取り（[requirements]）:
 * - 要件として読むのは、見出しに「機能要件」「業務ルール」「非機能要件」のどれかを含む節の表だけ。節は、次の同じ深さ以上の見出しまで続く。
 * - 表ごとに、先頭の行を見出し行、その次を区切り行とみなして除く（区切り行が無い表は違反）。見出し行に要件 ID の形の文字列があれば違反とする。
 * - 残りの行は、先頭列が要件 ID の形の完全一致でなければ違反とする。
 * - 要件 ID の定義は先頭列だけである。備考などの列に書いた要件 ID は、定義として数えない。
 * - 要件として読む表の外（地の文・引用・コードブロック・見出し・未決事項などの表）に要件 ID の形の文字列があれば違反とする。
 * - 要件が 1 件も読めない要件定義書は違反とする（読み取りの空振りで素通りさせない）。
 *
 * TRS の読み取り（[sources]）:
 * - US は、見出し行に `出典（要件 ID）` の列がある表、または先頭列が `US-<数字>` の行を含む表の行。
 *   見出し行に列が無い、先頭列が `US-<数字>` でない行がある、列数が見出しと合わない行がある場合は違反とする。
 * - UC は、見出しが `UC-<数字>:` で始まる節。節の中に、項目名が `出典（要件 ID）` の行が無ければ違反とする。
 *   `UC-<数字>` で始まるのに `:` が続かない見出しと、どの UC の節にも属さない項目名 `出典（要件 ID）` の行も違反とする。
 * - US の行が 1 つも無い、UC が 1 つも無い TRS は違反とする。
 * - 出典の値は、半角のカンマで区切って 1 つずつ調べる。空欄・`—`・OI-ID・要件定義書に無い ID・要件 ID の形でない値は、すべて違反とする。
 *
 * 突き合わせ（[violations]）:
 * - ⑤ 全要件定義書の要件 ID のうち、どの US/UC の出典にも現れないものは違反とする。除外の手段は設けない。
 * - ⑥ 出典の値が実在しなければ違反とする。同じ要件 ID が全要件定義書で複数の行にあるときも違反とする。
 */
internal object RequirementTraceCheck {
    private const val SOURCE_NAME = "出典（要件 ID）"
    private val sectionWords = listOf("機能要件", "業務ルール", "非機能要件")
    private val idForm = Regex("""REQ-[A-Z]{2,6}-\d{2,}""")
    private val oiId = Regex("""^OI-\d+$""")
    private val usId = Regex("""^US-\d+$""")
    private val headingLine = Regex("""^(#{1,6})\s+(.*)$""")
    private val ucTitle = Regex("""^(UC-\d+)\s*[:：]""")
    private val ucLike = Regex("""^UC-\d+""")
    private val fenceOpen = Regex("""^(`{3,}|~{3,})(.*)$""")

    /** 1 行の読み取り結果。[level] は見出しの深さ（見出しでなければ 0）、[table] は表の通し番号（表の行でなければ -1）、[row] は表の中の行の位置 */
    private class Line(val no: Int, val text: String, val level: Int, val title: String, val table: Int, val row: Int, val cells: List<String>)

    class Requirement(val id: String, val line: Int)

    class Requirements(val items: List<Requirement>, val errors: List<String>)

    class Source(val label: String, val values: List<String>)

    class Sources(val items: List<Source>, val errors: List<String>)

    private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private fun isSeparator(cells: List<String>) = cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }

    /**
     * コードブロックと HTML コメントの中は、見出しにも表にもしない。
     * コードブロックは、開いた文字（`` ` `` か `~`）と同じ文字を、開いた長さ以上に並べた行で閉じる。
     * 表は連続する `|` 始まりの行で、通し番号と行の位置を付ける。
     */
    private fun scan(text: String): List<Line> {
        var fenceChar = ' '
        var fenceLen = 0
        var inComment = false
        var table = -1
        var nextTable = 0
        var row = 0
        return text.lines().mapIndexed { i, raw ->
            val t = raw.trimStart()
            val fenced = fenceLen > 0
            val fenceMark = if (inComment) null else fenceOpen.find(t)
            val closesFence = fenced && fenceMark != null && fenceMark.groupValues[1][0] == fenceChar && fenceMark.groupValues[1].length >= fenceLen && fenceMark.groupValues[2].isBlank()
            val fence = if (fenced) closesFence else fenceMark != null
            if (fence) {
                fenceChar = fenceMark!!.groupValues[1][0]
                fenceLen = if (fenced) 0 else fenceMark.groupValues[1].length
            }
            val comment = !fence && !fenced && (inComment || "<!--" in raw)
            if (comment) {
                val open = raw.lastIndexOf("<!--")
                val close = raw.lastIndexOf("-->")
                if (open >= 0 || close >= 0) inComment = open > close
            }
            val plain = fence || fenced || comment
            val heading = if (plain) null else headingLine.find(t)
            val isRow = !plain && heading == null && t.startsWith("|")
            when {
                isRow && table < 0 -> {
                    table = nextTable++
                    row = 0
                }
                isRow -> row++
                else -> table = -1
            }
            Line(i + 1, raw, heading?.groupValues?.get(1)?.length ?: 0, heading?.groupValues?.get(2).orEmpty(), table, row, if (isRow) cells(t) else emptyList())
        }
    }

    /** 1 つの要件定義書から読んだ要件と、読めなかった理由 */
    fun requirements(text: String): Requirements {
        val items = mutableListOf<Requirement>()
        val errors = mutableListOf<String>()
        var sectionLevel = 0
        scan(text).forEach { l ->
            if (l.level > 0) {
                if (sectionLevel > 0 && l.level <= sectionLevel) sectionLevel = 0
                if (sectionLevel == 0 && sectionWords.any { it in l.title }) sectionLevel = l.level
            }
            if (sectionLevel > 0 && l.table >= 0) {
                if (l.row == 0) {
                    idForm.findAll(l.text).map { it.value }.distinct().forEach { errors += "要件の表の見出し行に要件 ID の形の文字列がある（行 ${l.no}）: $it" }
                    return@forEach
                }
                if (isSeparator(l.cells)) return@forEach
                if (l.row == 1) errors += "要件の表に区切り行が無い（行 ${l.no}）"
                val first = l.cells.first()
                if (idForm.matches(first)) items += Requirement(first, l.no) else errors += "要件の表で先頭列が要件 ID の形でない行がある（行 ${l.no}）: $first"
            } else {
                idForm.findAll(l.text).map { it.value }.distinct().forEach { errors += "要件として読む表の外に要件 ID の形の文字列がある（行 ${l.no}）: $it" }
            }
        }
        return Requirements(items, errors)
    }

    private fun values(cell: String) = cell.split(",").map { it.trim() }

    /** 1 つの TRS から読んだ US/UC の出典と、読めなかった理由 */
    fun sources(text: String): Sources {
        val lines = scan(text)
        val items = mutableListOf<Source>()
        val errors = mutableListOf<String>()

        val usLines = lines.filter { it.table >= 0 && it.row >= 1 && usId.matches(it.cells.first()) }
        if (usLines.isEmpty()) errors += "US の行が 1 つも無い（先頭列が US-<数字> の表の行）"
        val usTables = (usLines.map { it.table } + lines.filter { it.table >= 0 && it.row == 0 && SOURCE_NAME in it.cells }.map { it.table }).distinct()
        usTables.forEach { table ->
            val headLine = lines.first { it.table == table && it.row == 0 }
            val header = headLine.cells
            val column = header.indexOf(SOURCE_NAME)
            if (column < 0) {
                errors += "US の表の見出しに「$SOURCE_NAME」の列が無い（行 ${headLine.no}）"
                return@forEach
            }
            lines.filter { it.table == table && it.row >= 1 && !isSeparator(it.cells) }.forEach { l ->
                when {
                    !usId.matches(l.cells.first()) -> errors += "US の表に先頭列が US-<数字> の形でない行がある（行 ${l.no}）: ${l.cells.first()}"
                    l.cells.size != header.size -> errors += "US の表の列数が見出しと合わない: ${l.cells.first()}"
                    else -> items += Source(l.cells.first(), values(l.cells[column]))
                }
            }
        }

        val ucs = lines.withIndex().filter { (_, l) -> l.level > 0 && ucTitle.containsMatchIn(l.title) }
        if (ucs.isEmpty()) errors += "UC が 1 つも無い（見出しが UC-<数字>: で始まる節）"
        lines.filter { it.level > 0 && ucLike.containsMatchIn(it.title) && !ucTitle.containsMatchIn(it.title) }
            .forEach { errors += "UC の見出しの形でない（行 ${it.no}。UC-<数字>: で始まる形にする）: ${it.title}" }
        val covered = mutableSetOf<Int>()
        ucs.forEach { (start, heading) ->
            val label = ucTitle.find(heading.title)!!.groupValues[1]
            val section = lines.drop(start + 1).takeWhile { it.level == 0 || it.level > heading.level }
            covered += section.map { it.no }
            val named = section.filter { it.table >= 0 && it.row >= 1 && it.cells.first() == SOURCE_NAME }
            if (named.isEmpty()) errors += "$label に項目名「$SOURCE_NAME」の行が無い"
            named.forEach { l ->
                if (l.cells.size == 2) items += Source(label, values(l.cells[1])) else errors += "$label の「$SOURCE_NAME」の行の列数が 2 でない"
            }
        }
        lines.filter { it.table >= 0 && it.row >= 1 && it.cells.first() == SOURCE_NAME && it.no !in covered }
            .forEach { errors += "どの UC の節にも属さない「$SOURCE_NAME」の行がある（行 ${it.no}）" }
        return Sources(items, errors)
    }

    private fun sourceProblem(value: String, existing: Set<String>): String? = when {
        value in existing -> null
        value.isEmpty() -> "出典が空欄"
        value == "—" -> "出典が「—」（出典が無い）"
        oiId.matches(value) -> "出典 $value は OI-ID（要件 ID ではない）"
        idForm.matches(value) -> "出典 $value は要件定義書に無い要件 ID"
        else -> "出典 $value は要件 ID の形（REQ-<略号>-<番号>）でない"
    }

    /** 違反の説明。空なら合格。引数は、全要件定義書と全 TRS の `(ファイル名, 本文)` */
    fun violations(requirementDocs: Map<String, String>, trsDocs: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        val defined = mutableListOf<Pair<String, String>>()
        requirementDocs.forEach { (name, text) ->
            val read = requirements(text)
            errors += read.errors.map { "$name: $it" }
            if (read.items.isEmpty()) errors += "$name: 要件が 1 件も読めない（機能要件・業務ルール・非機能要件の節の表に、先頭列が要件 ID の行を置く）"
            defined += read.items.map { it.id to "$name:${it.line}" }
        }
        defined.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
            .forEach { (id, places) -> errors += "要件 ID $id が複数の行にある（${places.joinToString()}）" }
        val existing = defined.map { it.first }.toSet()

        val cited = mutableSetOf<String>()
        trsDocs.forEach { (name, text) ->
            val read = sources(text)
            errors += read.errors.map { "$name: $it" }
            read.items.forEach { source ->
                source.values.forEach { value ->
                    val problem = sourceProblem(value, existing)
                    if (problem == null) cited += value else errors += "$name: ${source.label} の$problem"
                }
            }
        }
        (existing - cited).sorted().forEach { errors += "要件 ID $it が、どの US/UC の出典にも現れない" }
        return errors.distinct()
    }
}
