package com.example.architecture

/**
 * 基本設計書の設計 ID の読み取りと、書式・重複の検査（03 §3.4.3 の「⑧ ⑨ の書式」と §3.4.4 の ⑨）。
 * 読み取りの規則は [RequirementTraceCheck.requirements] と同じ流儀で、コードブロックと HTML コメントの中は表にしない（中の設計 ID の形は、表の外の文字列として失敗にする）。
 *
 * - 設計 ID として読むのは、節 2〜9 の表の先頭列だけ。節は `## <番号>.` の見出しの番号で見分ける。節 1・節 10・メタ情報は読まない。
 * - 先頭列が設計 ID の形（`DES-<略号>-<番号>`、略号は英大文字 2〜6、番号は 2 桁以上）でない行は失敗にする。見出し行と区切り行は除く。
 * - 見出し行に設計 ID の形の文字列があるときも失敗にする。
 * - 設計 ID の形の文字列が、節 2〜9 の表の外（節 1・節 10・メタ情報・地の文・コードブロック）にあるときも失敗にする。
 * - 節 2〜9 の表の、先頭列以外の列にある設計 ID の形の文字列は、定義に数えない。失敗にもしない（ほかの項目を指す参照として書ける）。
 * - 設計 ID が 1 件も読めない基本設計書は失敗にする（読み取りの空振りで素通りさせない）。
 * - 同じ設計 ID が、全基本設計書で複数の行にあるときは失敗にする。
 */
internal object DesignIds {
    private val idForm = Regex("""^DES-[A-Z]{2,6}-\d{2,}$""")
    private val idShape = Regex("""DES-[A-Z]{2,6}-\d{2,}""")
    private val sectionNumber = Regex("""^(\d+)\.""")

    /** 設計 ID の定義の書式。TRS の根拠の列の判定（[BasisCheck]）と共用する */
    fun isId(value: String) = idForm.matches(value)

    fun findAllIn(text: String): List<String> = idShape.findAll(text).map { it.value }.toList()

    class Item(val id: String, val line: Int)

    class Read(val items: List<Item>, val errors: List<String>)

    private fun isSeparator(cells: List<String>) = cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }

    /** 1 つの基本設計書から読んだ設計 ID と、読めなかった理由 */
    fun parse(text: String): Read {
        val items = mutableListOf<Item>()
        val errors = mutableListOf<String>()
        var inRange = false
        RequirementTraceCheck.scan(text).forEach { l ->
            if (l.level == 1) inRange = false
            if (l.level == 2) inRange = sectionNumber.find(l.title.trim())?.groupValues?.get(1)?.toInt() in 2..9
            if (inRange && l.table >= 0) {
                if (l.row == 0) {
                    findAllIn(l.text).distinct().forEach { errors += "節 2〜9 の表の見出し行に設計 ID の形の文字列がある（行 ${l.no}）: $it" }
                    return@forEach
                }
                if (isSeparator(l.cells)) return@forEach
                val first = l.cells.first()
                if (isId(first)) items += Item(first, l.no) else errors += "節 2〜9 の表で先頭列が設計 ID の形でない行がある（行 ${l.no}）: $first"
            } else {
                findAllIn(l.text).distinct().forEach { errors += "設計 ID の形の文字列が、節 2〜9 の表の外にある（行 ${l.no}）: $it" }
            }
        }
        return Read(items, errors)
    }

    /** 設計 ID の集合。書式の違反は [violations] が調べる */
    fun read(text: String): Set<String> = parse(text).items.map { it.id }.toSet()

    /** 書式・件数・重複の違反。引数は、全基本設計書の `(ファイル名, 本文)` */
    fun violations(designDocs: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        val defined = mutableListOf<Pair<String, String>>()
        designDocs.forEach { (name, text) ->
            val read = parse(text)
            errors += read.errors.map { "$name: $it" }
            if (read.items.isEmpty()) errors += "$name: 設計 ID が 1 件も読めない（節 2〜9 の表に、先頭列が設計 ID の行を置く）"
            defined += read.items.map { it.id to "$name:${it.line}" }
        }
        defined.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
            .forEach { (id, places) -> errors += "設計 ID $id が複数の行にある（${places.joinToString()}）" }
        return errors
    }

    /** ⑧ 全基本設計書の設計 ID のうち、[cited]（全 TRS の根拠に現れた設計 ID）に無いもの */
    fun uncovered(designDocs: Map<String, String>, cited: Set<String>): List<String> =
        designDocs.flatMap { (name, text) ->
            parse(text).items.filter { it.id !in cited }.map { "$name: 設計 ID ${it.id} が、どの TRS の根拠にも現れない（行 ${it.line}）" }
        }
}
