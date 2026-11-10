package com.example.architecture

/**
 * 生成物のファイルの行が、次の 3 種のどれかに当たることを検査する（03 §3.4 の「検査の単位」と §3.4.3 の「生成物のファイルに置ける行」）。
 * 入力は生成物のファイルの本文と、TRS のテンプレートの本文で、戻り値は違反の説明の一覧である（空なら合格）。実ファイルの検査と自己テストは、同じ [GeneratedLineCheck.violations] を呼ぶ。
 * 空行は検査しない。
 *
 * 1. ID に結び付く記述
 *    - 表の ID の行。US の表・AC の表・組合せ別の期待結果（5.2）の表の行で、先頭列がその表の ID の形（`US-<数字>`、`AC-xx-yy`、`C-xx`）のもの。見出しは、テンプレートと同じ列のものに限る。
 *    - UC の節。見出しが `UC-<数字>:`（全角のコロンも可）で始まり、深さがテンプレートの UC の見出しと同じ（`####`）節で、次の同じ深さ以上の見出しの手前まで。中の文は問わない。ほかの深さの見出しは、UC の節にならない。
 *    - Gherkin のシナリオ。`gherkin` のコードブロックの中の、`Scenario`・`Scenario Outline`・`Scenario Template`・`Example` で、空行を挟まない直前の 1 行が `@AC-xx-yy` を持つタグ行のもの。
 *      中の手順と、`Scenario Outline`（`Scenario Template` を含む）の `Examples` とその下の表の行を含む。
 * 2. ID に結び付かない Gherkin。`Feature` の 1 行と、`Background` とその手順。
 * 3. テンプレートの定型文。正規化した行が、取った行のどれかと、全体で一致するもの。空欄を含む定型文は、題の行だけである。
 *    - 取る行: テンプレートの、見出し行、引用行、表の見出し行と区切り行、`---` の行。コードブロックの中は取らない。ただし「生成物のファイルの冒頭」の節の `markdown` のコードブロックは、全行を取る。
 *    - 取る範囲: 第 1 部と第 2 部は、見出しとその直下の行だけ。節 1・2・3・5 は、次の同じ深さ以上の見出しの手前までの、下位の見出しを含む行。5.1 の下は取らない。
 *    - 正規化: 前後の空白を除き、連続する空白を 1 つにする。`[文字](リンク先)` は `文字` にする。リンク先は `.md` への相対パス（`#アンカー` 可）に限り、title を付けた形、絶対 URL、`.md` 以外は違反にする。
 *      表の区切り行（縦線を含み、縦線・ハイフン・コロンだけでできた行）は、空白をすべて除いて比べる。
 *    - 題の行: 「生成物のファイルの冒頭」の最初の行。ファイルの 1 行目に 1 回だけ置け、`<機能ID>` と `<機能名>` は、空でない任意の文字列に一致する。
 *      組の TRS ファイルのメタ情報の「TRS ID」と「機能名」と同じ値でなければ違反（`` ` `` の囲みは除いて比べる）。TRS ファイルが読めない、値が無いときも違反にする。
 *      題の行以外の定型文に、`` ` `` の外の `<…>` があるテンプレートは、読めないものとして違反にする。
 *
 * すべての行に適用する細目:
 * - コードブロックは `gherkin` だけを置ける。ほかの言語は、UC の節の中でも置けない（UC の節の「文は問わない」より、この規則を優先する）。
 * - HTML コメントと、リンク参照の定義の行（`[ラベル]: リンク先`、`[//]: # (…)` を含む）は、どこにも置けない。UC の節の中も同じである。コメントは、開始行から終了行までを 1 件の違反にする。
 * - Gherkin に置けない要素は、`Feature` の説明文、`Rule`、タグ行の無いシナリオ、手順の表、docstring、`#` で始まる行（`# language` を含む）である。タグ行は、シナリオの直前の 1 行だけである。
 *
 * テンプレートが読めないとき（範囲の見出しや「生成物のファイルの冒頭」の `markdown` のコードブロックが無い）は、黙って通さず、そのことを違反にする。
 */
internal object GeneratedLineCheck {
    /**
     * 取った定型文。[literals] は正規化した行、[separators] は表の区切り行（空白をすべて除いた形）。
     * [ucLevel] は UC の見出しの深さ（テンプレートの UC の見出しと同じ深さの見出しだけが、UC の節を始める）。
     * [title] は題の行の照合（空欄 `<機能ID>` と `<機能名>` を含む、定型文の中で唯一の行）。[errors] はテンプレートが読めなかった理由。
     */
    class Template(val literals: Set<String>, val separators: Set<String>, val title: Regex?, val ucLevel: Int, val errors: List<String>) {
        fun accepts(normalized: String) =
            if (isSeparatorLine(normalized)) withoutSpaces(normalized) in separators else normalized in literals
    }

    private val link = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val linkTarget = Regex("""^(?![A-Za-z][A-Za-z0-9+.-]*:)(?!/)[^\s#]*\.md(#\S*)?$""")
    private val linkDefinition = Regex("""^ {0,3}\[[^\]]+]:\s*\S""")
    private val linkWithTarget = Regex("""\[[^\]]*]\(([^)]*)\)""")
    private val whitespace = Regex("""[\p{javaWhitespace}]+""")
    private val placeholderOrCode = Regex("""`[^`]*`|<[^<>]+>""")
    private val partTitle = Regex("""^第([12])部""")
    private val sectionTitle = Regex("""^([1235])\.\s""")
    private val subsectionTitle = Regex("""^5\.([12])\s""")
    private const val PREAMBLE_TITLE = "生成物のファイルの冒頭"
    private const val ID_PLACEHOLDER = "<機能ID>"
    private const val NAME_PLACEHOLDER = "<機能名>"
    private const val PREAMBLE_LANGUAGE = "markdown"
    private val requiredAnchors = listOf("第1部", "第2部", "1.", "2.", "3.", "5.", "5.2")

    private val usHeader = listOf("ID", "ストーリー", "出典（要件 ID）")
    private val usForm = Regex("""^US-\d+$""")
    private val acForm = Regex("""^AC-\d\d-\d\d$""")
    private val constraintForm = Regex("""^C-\d\d$""")
    private val ucTitle = Regex("""^UC-\d+\s*[:：]""")

    private const val GHERKIN = "gherkin"

    private val featureLine = Regex("""^Feature:""")
    private val backgroundLine = Regex("""^Background:""")
    private val scenarioLine = Regex("""^(Scenario Outline|Scenario Template|Scenario|Example):""")
    private val examplesLine = Regex("""^Examples:""")
    private val stepLine = Regex("""^(Given|When|Then|And|But|\*)(\s|$)""")
    private val tagLine = Regex("""^@\S+(\s+@\S+)*$""")
    private val acTag = Regex("""(^|\s)@AC-\d\d-\d\d(\s|$)""")
    private const val EXCERPT = 40

    /** 前後の空白を除き、連続する空白を 1 つにして、`[文字](リンク先)` を `文字` にする（03 §3.4.3 の「正規化」） */
    fun normalize(line: String): String = whitespace.replace(link.replace(line, "$1"), " ").trim()

    /** 行の、`[文字](リンク先)` のリンク先のうち、`.md` への相対パス（`#アンカー` 可）でないもの。title を付けた形、絶対 URL、`.md` 以外が当たる */
    private fun badLinkTargets(line: String) = linkWithTarget.findAll(line).map { it.groupValues[1] }.filterNot { linkTarget.matches(it) }.toList()

    private fun withoutSpaces(line: String) = line.filterNot { it.isWhitespace() }

    /** 縦線、ハイフン、コロンと空白だけでできた、縦線を含む行（表の区切り行） */
    private fun isSeparatorLine(line: String) = withoutSpaces(line).let { it.isNotEmpty() && '|' in it && it.all { c -> c == '|' || c == '-' || c == ':' } }

    private fun isSeparator(cells: List<String>) = cells.isNotEmpty() && cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }

    private fun excerpt(text: String) = text.trim().let { if (it.length > EXCERPT) it.take(EXCERPT) + "…" else it }

    private fun anchor(level: Int, title: String): String? = when (level) {
        2 -> partTitle.find(title)?.let { "第${it.groupValues[1]}部" }
        3 -> sectionTitle.find(title)?.let { "${it.groupValues[1]}." }
        4 -> subsectionTitle.find(title)?.let { "5.${it.groupValues[1]}" }
        else -> null
    }

    /** 行の、`` ` `` の外にある `<…>`（空欄）。`` ` `` の中は数えない */
    private fun placeholders(line: String) = placeholderOrCode.findAll(line).map { it.value }.filter { it.startsWith("<") }.toList()

    /** 題の行の照合。`<機能ID>` と `<機能名>` を、この順に 1 つずつ取り出す（空でない任意の文字列）。形が違えば null */
    private fun compileTitle(line: String): Regex? {
        val spans = placeholderOrCode.findAll(line).filter { it.value.startsWith("<") }.toList()
        if (spans.map { it.value } != listOf(ID_PLACEHOLDER, NAME_PLACEHOLDER)) return null
        val pattern = StringBuilder()
        var last = 0
        spans.forEachIndexed { i, m ->
            pattern.append(Regex.escape(line.substring(last, m.range.first)))
            pattern.append(if (i == 0) "(.+?)" else "(.+)")
            last = m.range.last + 1
        }
        pattern.append(Regex.escape(line.substring(last)))
        return Regex(pattern.toString())
    }

    /** テンプレートの「取る範囲」の、「取る行」を読む */
    private fun takenInRange(lines: List<RequirementTraceCheck.Line>, found: MutableSet<String>): List<String> {
        val taken = mutableListOf<String>()
        var subtreeLevel = 0
        var excludedLevel = 0
        var ownOnly = false
        lines.filter { it.fence == RequirementTraceCheck.Fence.NONE }.forEach { l ->
            if (l.level > 0) {
                if (subtreeLevel > 0 && l.level <= subtreeLevel) subtreeLevel = 0
                if (excludedLevel > 0 && l.level <= excludedLevel) excludedLevel = 0
                ownOnly = false
                val name = anchor(l.level, l.title)
                name?.let { found += it }
                when (name) {
                    "第1部", "第2部" -> ownOnly = true
                    "5.1" -> excludedLevel = l.level
                    null -> Unit
                    else -> if (subtreeLevel == 0) subtreeLevel = l.level
                }
            }
            val inRange = (ownOnly || subtreeLevel > 0) && excludedLevel == 0
            val text = l.text.trim()
            val isTaken = l.level > 0 || text.startsWith(">") || text == "---" || (l.table >= 0 && (l.row == 0 || isSeparator(l.cells)))
            if (inRange && isTaken) taken += text
        }
        return taken
    }

    /** 「生成物のファイルの冒頭」の節の、最初の `markdown` のコードブロックの全行。節か、コードブロックが無ければ null */
    private fun preamble(lines: List<RequirementTraceCheck.Line>): List<String>? {
        val start = lines.indexOfFirst { it.level == 2 && it.title.startsWith(PREAMBLE_TITLE) }
        if (start < 0) return null
        val section = lines.drop(start + 1).takeWhile { it.level == 0 || it.level > 2 }
        val open = section.indexOfFirst { it.fence == RequirementTraceCheck.Fence.OPEN && it.fenceInfo == PREAMBLE_LANGUAGE }
        if (open < 0) return null
        return section.drop(open + 1).takeWhile { it.fence == RequirementTraceCheck.Fence.BODY }.map { it.text }
    }

    /** テンプレートの定型文を読む。読めないものは、[Template.errors] に理由を入れる */
    fun readTemplate(text: String): Template {
        val lines = RequirementTraceCheck.scan(text)
        val errors = mutableListOf<String>()
        val found = mutableSetOf<String>()
        val taken = takenInRange(lines, found).toMutableList()
        (requiredAnchors - found).forEach { errors += "テンプレートに、定型文を取る範囲の見出し（$it）が見つからない" }
        val head = preamble(lines)
        var title: Regex? = null
        when {
            head == null -> errors += "テンプレートに、「$PREAMBLE_TITLE」の節の $PREAMBLE_LANGUAGE のコードブロックが見つからない"
            head.none { it.isNotBlank() } -> errors += "テンプレートの「$PREAMBLE_TITLE」の $PREAMBLE_LANGUAGE のコードブロックが空"
            else -> {
                val titleLine = normalize(head.first { it.isNotBlank() })
                title = compileTitle(titleLine)
                if (title == null) errors += "テンプレートの題の行（$PREAMBLE_TITLE の最初の行）に、$ID_PLACEHOLDER と $NAME_PLACEHOLDER が、この順に 1 つずつ無い: $titleLine"
                taken += head.filter { it.isNotBlank() }.drop(1)
            }
        }
        val normalized = taken.map(::normalize).filter { it.isNotEmpty() }
        normalized.filter { placeholders(it).isNotEmpty() }.forEach { errors += "テンプレートの、題の行以外に、`` ` `` の外の <…> がある: $it" }
        val separators = normalized.filter(::isSeparatorLine).map(::withoutSpaces).toSet()
        val literals = normalized.filterNot(::isSeparatorLine).toSet()
        val ucLevel = lines.firstOrNull { it.fence == RequirementTraceCheck.Fence.NONE && it.level > 0 && ucTitle.containsMatchIn(it.title) }?.level ?: 0
        if (ucLevel == 0) errors += "テンプレートに、UC の見出し（UC-<数字>: で始まる見出し）が見つからない"
        return Template(literals, separators, title, ucLevel, errors)
    }

    /** 表の ID の行の、行番号。見出しがテンプレートと同じ列の、US の表・AC の表・5.2 の表だけを数える */
    private fun idRowLines(text: String): Set<Int> = TrsTables.read(text).flatMap { table ->
        val form = when (table.header) {
            usHeader -> usForm
            AcTable.header -> acForm
            ExpectationTable.header -> constraintForm
            else -> null
        }
        table.rows.filter { form != null && form.matches(it.cells.first()) }.map { it.no }
    }.toSet()

    private enum class Context { NONE, BACKGROUND, SCENARIO, OUTLINE, EXAMPLES, REJECTED }

    private val outlineKeywords = setOf("Scenario Outline", "Scenario Template")

    /** Gherkin のコードブロックの中の、置けない行（行番号つきの説明）。[body] はコードブロックの中の行 */
    private fun gherkinViolations(body: List<RequirementTraceCheck.Line>): List<String> {
        val errors = mutableListOf<String>()
        fun reject(no: Int, text: String, reason: String) {
            errors += "行 $no: Gherkin に置けない行（$reason）: ${excerpt(text)}"
        }
        var feature = false
        var context = Context.NONE
        body.forEachIndexed { i, line ->
            val text = line.text.trim()
            if (text.isEmpty()) return@forEachIndexed
            val no = line.no
            // 直前・直後の行。空行は挟めないので、空行ならば、その行は無いものとして扱う
            val previous = body.getOrNull(i - 1)?.text?.trim()?.takeIf { it.isNotEmpty() }
            val next = body.getOrNull(i + 1)?.text?.trim()?.takeIf { it.isNotEmpty() }
            when {
                featureLine.containsMatchIn(text) -> {
                    if (feature) reject(no, text, "Feature が 2 つある") else feature = true
                    context = Context.NONE
                }
                backgroundLine.containsMatchIn(text) -> context = Context.BACKGROUND
                scenarioLine.containsMatchIn(text) ->
                    if (previous != null && tagLine.matches(previous) && acTag.containsMatchIn(previous)) {
                        context = if (scenarioLine.find(text)!!.groupValues[1] in outlineKeywords) Context.OUTLINE else Context.SCENARIO
                    } else {
                        reject(no, text, "空行を挟まない直前のタグ行に @AC-xx-yy が無いシナリオ")
                        context = Context.REJECTED
                    }
                tagLine.matches(text) ->
                    if (!(acTag.containsMatchIn(text) && next != null && scenarioLine.containsMatchIn(next))) {
                        reject(no, text, "空行を挟まずシナリオの直前にある、@AC-xx-yy を持つタグ行ではない")
                    }
                examplesLine.containsMatchIn(text) ->
                    if (context == Context.OUTLINE || context == Context.EXAMPLES) {
                        context = Context.EXAMPLES
                    } else if (context != Context.REJECTED) {
                        reject(no, text, "Scenario Outline の外の Examples")
                    }
                stepLine.containsMatchIn(text) ->
                    if (context == Context.NONE || context == Context.EXAMPLES) reject(no, text, "Background かシナリオの外の手順")
                text.startsWith("|") ->
                    if (context != Context.EXAMPLES && context != Context.REJECTED) reject(no, text, "Examples の下ではない表の行")
                else -> reject(no, text, "説明文・Rule・コメントなど、置ける要素に当たらない")
            }
        }
        return errors
    }

    /** 組の TRS ファイルのメタ情報（見出しが `項目` / `内容` の表）の、[label] の行の値。`` ` `` の囲みを除き、正規化する。無ければ null */
    private fun metaValue(trsText: String, label: String): String? =
        TrsTables.read(trsText).filter { it.header == listOf("項目", "内容") }.flatMap { it.rows }
            .firstOrNull { it.cells.first() == label }?.cells?.getOrNull(1)?.let { normalize(it.trim().trim('`')) }?.takeIf { it.isNotEmpty() }

    /** 題の行（ファイルの 1 行目）の違反。[trsText] は組の TRS ファイルの本文で、読めなければ null */
    private fun titleViolations(first: String, title: Regex, trsText: String?): List<String> {
        val match = title.matchEntire(normalize(first))
            ?: return listOf("行 1: 題の行が、ファイルの 1 行目に無い（テンプレートの「$PREAMBLE_TITLE」の最初の行の形にする）: ${excerpt(first)}")
        if (trsText == null) return listOf("組の TRS ファイルが読めない。題の $ID_PLACEHOLDER と $NAME_PLACEHOLDER を、メタ情報の値と照合できない")
        return listOf("TRS ID" to ID_PLACEHOLDER, "機能名" to NAME_PLACEHOLDER).mapIndexedNotNull { i, (label, placeholder) ->
            val expected = metaValue(trsText, label)
            val actual = match.groupValues[i + 1]
            when {
                expected == null -> "TRS ファイルのメタ情報に、「$label」の値が無い。題の $placeholder を照合できない"
                actual != expected -> "行 1: 題の $placeholder（$actual）が、TRS ファイルのメタ情報の「$label」（$expected）と違う"
                else -> null
            }
        }
    }

    /**
     * 生成物のファイルの本文の、3 種のどれにも当たらない行の説明。空なら合格。[template] が読めないときは、その理由だけを返す。
     * [trsText] は、組の TRS ファイル（`docs/trs/` の同じ名前のファイル）の本文で、題の `<機能ID>` と `<機能名>` の照合に使う。読めなければ null。
     */
    fun violations(generatedText: String, template: Template, trsText: String?): List<String> {
        if (template.errors.isNotEmpty()) return template.errors
        val title = checkNotNull(template.title) { "題の行の照合が無いテンプレートは、errors に理由がある" }
        val lines = RequirementTraceCheck.scan(generatedText)
        val idRows = idRowLines(generatedText)
        val errors = mutableListOf<String>()
        errors += titleViolations(lines.first().text, title, trsText)
        badLinkTargets(lines.first().text).takeIf { it.isNotEmpty() }?.let { errors += "行 1: 定型文の行のリンク先は、.md への相対パス（#アンカー可）に限る（${it.joinToString("、")}）" }
        var ucLevel = 0
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (l.fence == RequirementTraceCheck.Fence.OPEN) {
                val body = lines.drop(i + 1).takeWhile { it.fence == RequirementTraceCheck.Fence.BODY }
                if (l.fenceInfo == GHERKIN) {
                    errors += gherkinViolations(body)
                } else {
                    errors += "行 ${l.no}: $GHERKIN 以外のコードブロックは置けない（言語: ${l.fenceInfo.ifEmpty { "なし" }}）"
                }
                i += 1 + body.size
                continue
            }
            if (l.fence != RequirementTraceCheck.Fence.NONE) {
                i++ // 閉じの行
                continue
            }
            if (l.comment) {
                val comment = lines.drop(i).takeWhile { it.comment }
                errors += if (comment.size == 1) "行 ${l.no}: HTML コメントは置けない" else "行 ${l.no}〜${comment.last().no}: HTML コメントは置けない"
                i += comment.size
                continue
            }
            if (linkDefinition.containsMatchIn(l.text)) {
                errors += "行 ${l.no}: リンク参照の定義の行は置けない: ${excerpt(l.text)}"
                i++
                continue
            }
            if (l.level > 0) {
                if (ucLevel > 0 && l.level <= ucLevel) ucLevel = 0
                if (l.level == template.ucLevel && ucTitle.containsMatchIn(l.title)) ucLevel = l.level
            }
            val isTitleShape = l.text.isNotBlank() && title.matches(normalize(l.text))
            when {
                l.no == 1 -> Unit // 題の行は、titleViolations が調べた
                isTitleShape -> errors += "行 ${l.no}: 題の行は、ファイルの 1 行目に 1 回だけ置ける: ${excerpt(l.text)}"
                l.text.isBlank() || ucLevel > 0 || l.no in idRows -> Unit
                badLinkTargets(l.text).isNotEmpty() && template.accepts(normalize(l.text)) ->
                    errors += "行 ${l.no}: 定型文の行のリンク先は、.md への相対パス（#アンカー可）に限る（${badLinkTargets(l.text).joinToString("、")}）: ${excerpt(l.text)}"
                template.accepts(normalize(l.text)) -> Unit
                else -> errors += "行 ${l.no}: 3 種のどれにも当たらない行（ID に結び付く記述、ID に結び付かない Gherkin、テンプレートの定型文）: ${excerpt(l.text)}"
            }
            i++
        }
        return errors
    }
}
