package com.example.architecture

/**
 * TRS ファイルのトレーサビリティ表（テンプレート §7）を、テストコードと生成物のファイルの AC の表から作る。
 * 表のテスト名は手で書かず、[expected] が作った表と、TRS ファイルの表が一致しなければ [violations] が失敗にする。
 * 手で直した値は、次の検査で必ず見つかり、残らない。
 *
 * 列の導き方:
 * - 層1: `src/test` のテストファイルのうち、テスト名の先頭に、その AC ID を持つもの。項目は `<クラス名>::<AC ID>`。クラス名はファイル名と同じ。
 * - 層2: `src/integrationTest` と `src/e2eTest` の同じもの。
 * - 層3: 検証層の列に `E2E` を含む AC は `探索対象`、含まない AC は `—`。探索は画面を操作するため、画面で検証する AC を対象にする。
 * - 状態: 生成物のファイルの Gherkin で `@pending:<OI-ID>` を付けた AC は `pending（<OI-ID>）`。付けておらず、テストがあれば `実装済`、無ければ `未着手`。
 * - 行の集合と並びは、AC の表と同じにする。項目の並びは、クラス名の辞書順にする。
 *
 * テスト名の読み方は、AC に対応するテストの検査（① ⑦）と同じ [TestNameCheck.testedIds] を使う。
 */
internal object TraceabilityMatrix {
    enum class Layer { UNIT, INTEGRATION_E2E }

    /** [className] はテストファイル名（拡張子なし）、[text] はその本文 */
    class TestFile(val className: String, val layer: Layer, val text: String)

    class Row(val acId: String, val unit: List<String>, val integration: List<String>, val exploration: Boolean, val state: String) {
        val cells: List<String>
            get() = listOf(acId, cell(unit), cell(integration), if (exploration) EXPLORATION else NONE, state)

        private fun cell(items: List<String>) = if (items.isEmpty()) NONE else items.joinToString(", ") { "`$it`" }
    }

    private const val NONE = "—"
    private const val EXPLORATION = "探索対象"
    private const val LAYER_COLUMN = 3
    private val pendingTag = Regex("""@(AC-\d\d-\d\d)\b[^\n]*@pending:([^\s`|]*)""")

    /** 生成物のファイルの AC の表の順に、テストから作った行を返す */
    fun expected(generatedText: String, tests: List<TestFile>): List<Row> {
        val pending = pendingTag.findAll(generatedText).associate { it.groupValues[1] to it.groupValues[2] }
        val named = tests.map { it to TestNameCheck.testedIds(it.text) }
        fun classes(acId: String, layer: Layer) =
            named.filter { (file, ids) -> file.layer == layer && acId in ids }.map { (file, _) -> "${file.className}::$acId" }.sorted()
        val acRows = TrsTables.read(generatedText).filter { it.header == AcTable.header }.flatMap { it.rows }
            .filter { it.cells.size == AcTable.header.size }
        return acRows.distinctBy { it.cells.first() }.map { line ->
            val acId = line.cells.first()
            val unit = classes(acId, Layer.UNIT)
            val integration = classes(acId, Layer.INTEGRATION_E2E)
            val state = when {
                acId in pending -> "pending（${pending.getValue(acId)}）"
                unit.isNotEmpty() || integration.isNotEmpty() -> "実装済"
                else -> "未着手"
            }
            Row(acId, unit, integration, "E2E" in line.cells[LAYER_COLUMN], state)
        }
    }

    /** [rows] を、TRS ファイルに置くトレーサビリティ表（見出し行・区切り行・データ行）の形にする */
    fun render(rows: List<Row>): String = (
        listOf(AcTable.traceabilityHeader, AcTable.traceabilityHeader.map { "---" }) + rows.map { it.cells }
        ).joinToString("\n") { cells -> cells.joinToString(" | ", "| ", " |") }

    /**
     * TRS ファイルのトレーサビリティ表が、テストから作った表と違う点。空なら合格。
     * 表が 1 つでないとき、行の列数が違うとき、AC ID の重複、AC の表に無い行、無い行、値の違い、並びの違いを、すべて返す。
     * 違反があるときは、最後の要素に、置き換えるべき表の全文を付ける。
     */
    fun violations(trsText: String, generatedText: String, tests: List<TestFile>): List<String> {
        val tables = TrsTables.read(trsText).filter { it.header == AcTable.traceabilityHeader }
        if (tables.size != 1) return listOf("トレーサビリティ表（見出し: ${AcTable.traceabilityHeader.joinToString(" | ")}）が ${tables.size} 個ある（1 個にする）")
        val rows = expected(generatedText, tests)
        val actual = tables.single().rows
        val byId = actual.groupBy { it.cells.first() }
        val problems = mutableListOf<String>()
        actual.filter { it.cells.size != AcTable.traceabilityHeader.size }.forEach { problems += "トレーサビリティ表の列数が見出しと合わない（行 ${it.no}）" }
        byId.filter { it.value.size > 1 }.forEach { (id, lines) -> problems += "トレーサビリティ表に $id が重複している（行 ${lines.joinToString { l -> "${l.no}" }}）" }
        val expectedIds = rows.map { it.acId }.toSet()
        actual.filter { it.cells.first() !in expectedIds }
            .forEach { problems += "トレーサビリティ表の ${it.cells.first()} が、生成物のファイルの AC の表に無い（行 ${it.no}）" }
        rows.forEach { row ->
            val line = byId[row.acId]?.firstOrNull()
            when {
                line == null -> problems += "トレーサビリティ表に ${row.acId} の行が無い（期待: ${row.cells.joinToString(" | ")}）"
                line.cells != row.cells ->
                    problems += "トレーサビリティ表の ${row.acId}（行 ${line.no}）が、テストから作った値と違う。表: ${line.cells.drop(1).joinToString(" | ")}、期待: ${row.cells.drop(1).joinToString(" | ")}"
            }
        }
        val actualOrder = actual.map { it.cells.first() }.filter { it in expectedIds }.distinct()
        if (problems.isEmpty() && actualOrder != rows.map { it.acId }) problems += "トレーサビリティ表の行の並びが、AC の表と違う"
        return if (problems.isEmpty()) problems else problems + "トレーサビリティ表を、次の表に置き換える（テストから作った表）:\n${render(rows)}"
    }
}
