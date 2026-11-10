package com.example.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File

/**
 * [GeneratedLineCheck]（生成物のファイルの各行が、ID に結び付く記述か、テンプレートの定型文のどちらかであること。03 §3.4.3）の検査と自己テスト。
 * 実ファイルの検査は、`docs/trs/generated/` の全ファイルを、`docs/testing/trs/_template.md` の定型文に通す。
 * 反例は、実際の生成物のファイルに 1 か所だけ足す・変えて作る。期待は 03 §3.4.3 の文面から決め、既存の挙動から逆生成しない。
 * このファイルは、文字列の中に、テスト名の先頭の ID の形（引用符かバッククォートの直後の AC-xx-yy）を置かない。置くと、AC の紐づけ検査がテスト名と取り違える。
 */
class GeneratedLineCheckTest : StringSpec({
    val templateText = File("../../docs/testing/trs/_template.md").readText()
    val template = GeneratedLineCheck.readTemplate(templateText)
    val generatedDocs = File("docs/trs/generated").listFiles { f -> f.isFile && f.extension == "md" }.orEmpty().sortedBy { it.name }.associate { it.name to it.readText() }
    val good = generatedDocs.values.first()

    /** 生成物のファイルと組になる、TRS ファイル（`docs/trs/` の同じ名前のファイル）の本文。無ければ null */
    fun trsOf(name: String) = File("docs/trs/$name").takeIf { it.isFile }?.readText()
    val goodTrs = trsOf(generatedDocs.keys.first())

    fun check(text: String, trs: String? = goodTrs) = GeneratedLineCheck.violations(text, template, trs)

    /** 実際の生成物のファイルの末尾に、空行を置いて [extra] を足す */
    fun appended(extra: String) = good.trimEnd() + "\n\n" + extra + "\n"

    fun lineOf(text: String, prefix: String) = text.lines().indexOfFirst { it.startsWith(prefix) } + 1

    /** [appended] が足した最初の行の、行番号 */
    fun firstExtraLine() = good.trimEnd().lines().size + 2

    fun fenced(language: String, vararg lines: String) = "```$language\n" + lines.joinToString("\n") + "\n```"

    fun gherkin(vararg lines: String) = fenced("gherkin", *lines)

    fun List<String>.shouldBeOneAt(no: Int, reason: String) {
        shouldHaveSize(1)
        first() shouldStartWith "行 $no: "
        first() shouldContain reason
    }

    val tagged = "  @AC-01-20 @layer:2"

    // ---- 実ファイル ----

    "実ファイル: 生成物のファイルが 1 つ以上あり、全ファイルの全行が、3 種（ID に結び付く記述、ID に結び付かない Gherkin、テンプレートの定型文）のどれかに当たる" {
        generatedDocs.isNotEmpty() shouldBe true
        generatedDocs.forEach { (name, text) ->
            val errors = check(text, trsOf(name))
            withClue("$name\n${errors.joinToString("\n")}") { errors.shouldBeEmpty() }
        }
    }

    "実ファイル: テンプレートから定型文を読めている（読めない・空のままで素通りしない）" {
        template.errors.shouldBeEmpty()
        (template.literals.size > 30) shouldBe true
        template.literals.contains("---") shouldBe true
    }

    "実ファイル: 空欄を含む定型文は、題の行だけである（定型文の行に、`` ` `` の外の <…> が無い）" {
        template.title shouldNotBe null
        template.errors.shouldBeEmpty() // 題の行以外に空欄があると、テンプレートが読めない扱いになる（下のテスト）
        template.accepts("# TRS-X-01 の生成物: 名前") shouldBe false // 題の行は、定型文の行としては取らない。1 行目の題として、別に調べる
    }

    "テンプレートが読めない: 題の行以外に、`` ` `` の外の <…> がある・題の行に <機能ID> と <機能名> が無い" {
        listOf(
            templateText.replace("書き方は 03 §2.1 に従う。", "書き方は <x> に従う。"),
            templateText.replace("理由は 03 §2.1 にある。", "理由は <x> にある。"), // 冒頭の定型文の、題以外の行
        ).forEach { broken ->
            val tpl = GeneratedLineCheck.readTemplate(broken)
            tpl.errors.shouldHaveSize(1)
            tpl.errors.first() shouldContain "題の行以外に"
            GeneratedLineCheck.violations(good, tpl, goodTrs) shouldBe tpl.errors
        }
        listOf("# TRS-<機能ID> の生成物", "# TRS-<機能名> の生成物: <機能ID>", "# TRS-<機能ID> の生成物: <機能名> <機能名>", "# TRS-X の生成物: Y").forEach { title ->
            val tpl = GeneratedLineCheck.readTemplate(templateText.replace("# TRS-<機能ID> の生成物: <機能名>", title))
            withClue(title) {
                tpl.errors.shouldHaveSize(1)
                tpl.errors.first() shouldContain "題の行"
            }
        }
    }

    // ---- 03 §3.4.3 の正規化と空欄 ----

    "正規化: 前後の空白を除き、連続する空白を 1 つにし、リンクの記法を文字にする" {
        GeneratedLineCheck.normalize("  a \t b　c  ") shouldBe "a b c"
        GeneratedLineCheck.normalize("> [03 §5.4](../../x/03.md#54) の 2") shouldBe "> 03 §5.4 の 2"
        GeneratedLineCheck.normalize("[a](u)と[b](v)") shouldBe "aとb"
    }

    "正規化: リンク先が違っても、リンクの文字が同じなら、定型文に一致する" {
        check(good.replace("(../TODO-001-task-management.md)", "(./other/path.md)")).shouldBeEmpty()
        check(good.replace("[同じ名前の TRS ファイル](", "[別の文字](")).shouldBeOneAt(lineOf(good, "> このファイルは"), "3 種のどれにも当たらない行")
    }

    "正規化: 空白の数が違うだけの引用行は、定型文に一致する" {
        check(good.replace("> 手で編集しない。入力の文書か", ">   手で編集しない。入力の文書か")).shouldBeEmpty()
    }

    "一致は全体: 定型文に語を足した行と、語を削った行は、一致しない" {
        val no = lineOf(good, "> 手で編集しない。")
        check(good.replace("> 手で編集しない。", "> なお、手で編集しない。")).shouldBeOneAt(no, "3 種のどれにも当たらない行")
        check(good.replace("手で編集しない。", "")).shouldBeOneAt(no, "3 種のどれにも当たらない行")
    }

    "題の行: <機能ID> と <機能名> は、空でない任意の文字列に一致し、組の TRS ファイルのメタ情報の値（`` ` `` の囲みは除く）と同じでなければならない" {
        val tpl = GeneratedLineCheck.readTemplate(miniTemplate(""))
        tpl.errors.shouldBeEmpty()
        val trs = miniTrs("`T-1`", "名前")
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: 名前", tpl, trs).shouldBeEmpty()
        GeneratedLineCheck.violations("# TRS-T-1 の生成物:　名前", tpl, trs).shouldBeEmpty() // 空白の数・種類は正規化される
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: あ い う", tpl, miniTrs("T-1", "あ い う")).shouldBeEmpty()
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: 名前", tpl, miniTrs("`T-1`", "`名前`")).shouldBeEmpty()
        GeneratedLineCheck.violations("# TRS-T-2 の生成物: 名前", tpl, trs).shouldBeOneAt(1, "題の <機能ID>（T-2）が、TRS ファイルのメタ情報の「TRS ID」（T-1）と違う")
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: 別名", tpl, trs).shouldBeOneAt(1, "題の <機能名>（別名）が、TRS ファイルのメタ情報の「機能名」（名前）と違う")
        GeneratedLineCheck.violations("# TRS-T-2 の生成物: 別名", tpl, trs).shouldHaveSize(2)
        // 空の <機能ID> と <機能名> は、一致しない
        GeneratedLineCheck.violations("# TRS- の生成物: 名前", tpl, trs).shouldBeOneAt(1, "題の行が、ファイルの 1 行目に無い")
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: ", tpl, trs).shouldBeOneAt(1, "題の行が、ファイルの 1 行目に無い")
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: 名前。", tpl, trs).shouldBeOneAt(1, "題の <機能名>（名前。）")
    }

    "題の行: ファイルの 1 行目に無い、または 2 行目以降にもあると、違反になる" {
        val tpl = GeneratedLineCheck.readTemplate(miniTemplate(""))
        val trs = miniTrs("T-1", "名前")
        val title = "# TRS-T-1 の生成物: 名前"
        GeneratedLineCheck.violations("# 別の題", tpl, trs).shouldBeOneAt(1, "題の行が、ファイルの 1 行目に無い")
        GeneratedLineCheck.violations("\n$title", tpl, trs).let { errors ->
            errors.shouldHaveSize(2) // 1 行目が空行で題が無いことと、題の形の行が 2 行目にあること
            errors[0] shouldStartWith "行 1: 題の行が、ファイルの 1 行目に無い"
            errors[1] shouldStartWith "行 2: 題の行は、ファイルの 1 行目に 1 回だけ置ける"
        }
        GeneratedLineCheck.violations("$title\n\n$title", tpl, trs).shouldBeOneAt(3, "題の行は、ファイルの 1 行目に 1 回だけ置ける")
        GeneratedLineCheck.violations("$title\n\n#### UC-01: 例\n\n$title", tpl, trs).shouldBeOneAt(5, "題の行は、ファイルの 1 行目に 1 回だけ置ける") // UC の節の中も同じ
        GeneratedLineCheck.violations("$title\n\n# TRS-T-9 の生成物: 別", tpl, trs).shouldBeOneAt(3, "題の行は、ファイルの 1 行目に 1 回だけ置ける") // 値が違っても、題の形なら同じ
    }

    "題の行: 組の TRS ファイルが読めない、メタ情報の値が無いときは、違反になる（黙って通さない）" {
        val tpl = GeneratedLineCheck.readTemplate(miniTemplate(""))
        val title = "# TRS-T-1 の生成物: 名前"
        GeneratedLineCheck.violations(title, tpl, null).shouldHaveSize(1).first() shouldContain "組の TRS ファイルが読めない"
        GeneratedLineCheck.violations(title, tpl, "").shouldHaveSize(2)
        GeneratedLineCheck.violations(title, tpl, miniTrs("", "名前")).shouldHaveSize(1).first() shouldContain "「TRS ID」の値が無い"
        GeneratedLineCheck.violations(title, tpl, miniTrs("T-1", "``")).shouldHaveSize(1).first() shouldContain "「機能名」の値が無い"
        GeneratedLineCheck.violations(title, tpl, "| 項目 | 内容 |\n|---|---|\n| TRS ID | T-1 |").shouldHaveSize(1).first() shouldContain "「機能名」の値が無い"
        // 実ファイルの組: TRS ファイルが無ければ違反
        check(good, null).shouldHaveSize(1).first() shouldContain "組の TRS ファイルが読めない"
    }

    "題の行: 実ファイルの題を、題の値だけ変えると違反になる" {
        val title = good.lines().first()
        title.startsWith("# TRS-") shouldBe true
        val no = 1
        check(good.replaceFirst(title, title.replace("TODO-001", "TODO-999"))).shouldBeOneAt(no, "題の <機能ID>（TODO-999）")
    }

    "空欄: `` ` `` の中の `<…>` は空欄にならず、そのままの文字に一致する" {
        val tpl = GeneratedLineCheck.readTemplate(miniTemplate("`<OI-ID>` を書く"))
        tpl.errors.shouldBeEmpty()
        val title = "# TRS-T-1 の生成物: 名前\n\n"
        val trs = miniTrs("T-1", "名前")
        GeneratedLineCheck.violations(title + "`<OI-ID>` を書く", tpl, trs).shouldBeEmpty()
        GeneratedLineCheck.violations(title + "`OI-001` を書く", tpl, trs).shouldHaveSize(1)
    }

    // ---- 03 §3.4.3 の、置けない行 ----

    "反例: 入力の値を写した地の文・リスト・小見出し・表は、定型文でも ID に結び付く記述でもないので、すべて違反になる" {
        val extra = listOf(
            "#### 検証層の選び方",
            "",
            "応答の形と値の約束は、次のとおりである。",
            "",
            "- 入力の拒否は 400、存在しない対象は 404、成功は 2xx である。",
            "",
            "| 層 | 置くもの | AC |",
            "|---|---|---|",
            "| 層1 | タイトルと期限の検証 | UC-01 の検証 |",
            "",
            "使わない番号は、番号を載せずに文だけで書く。",
        )
        val text = appended(extra.joinToString("\n"))
        val errors = check(text)
        errors.shouldHaveSize(6) // 区切り行 `|---|---|---|` は US の表の区切り行と同じ文字で、定型文に一致する。残る 6 行が違反
        listOf("#### 検証層の選び方", "応答の形と値の約束は", "- 入力の拒否は", "| 層 | 置くもの | AC |", "| 層1 |", "使わない番号は").forEach { prefix ->
            errors.any { it.startsWith("行 ${lineOf(text, prefix)}: ") } shouldBe true
        }
    }

    "反例: テンプレートの、生成物の範囲の外の行（第 3 部・節 4・5.1・メタ情報）は、そのままでも違反になる" {
        val outside = templateText.lines().filter { it.startsWith("## 第3部") || it.startsWith("### 4. ") || it.startsWith("#### 5.1 ") || it.startsWith("## メタ情報") }
        outside.shouldHaveSize(4)
        outside.forEach { line -> check(appended(line)).shouldBeOneAt(lineOf(appended(line), line), "3 種のどれにも当たらない行") }
        // 5.1 の下の引用と、節 4 の下の引用も取らない
        val quotes = templateText.lines().dropWhile { !it.startsWith("#### 5.1 ") }.drop(1).first { it.startsWith(">") }
        check(appended(quotes)).shouldBeOneAt(lineOf(appended(quotes), quotes), "3 種のどれにも当たらない行")
        val factorQuote = templateText.lines().dropWhile { !it.startsWith("### 4. ") }.drop(1).first { it.startsWith(">") }
        check(appended(factorQuote)).shouldBeOneAt(lineOf(appended(factorQuote), factorQuote), "3 種のどれにも当たらない行")
    }

    "反例: 見出しの無い箇条書きは、違反になる" {
        check(appended("* 補足")).shouldBeOneAt(firstExtraLine(), "3 種のどれにも当たらない行")
    }

    "HTML コメント: どこにあっても違反になる（UC の節の中も）。1 行でも、複数行でも、開始行から終了行までを 1 件にする" {
        check(appended("<!-- 補足 -->")).shouldBeOneAt(firstExtraLine(), "HTML コメントは置けない")
        val inUc = appended("#### UC-09: 例\n\n<!-- 隠れた文 -->\n\n自由な文。")
        check(inUc).shouldBeOneAt(lineOf(inUc, "<!--"), "HTML コメントは置けない")
        val multi = appended("#### UC-09: 例\n\n<!--\n隠れた文\n-->\n\n自由な文。")
        val errors = check(multi)
        errors.shouldHaveSize(1)
        errors.first() shouldStartWith "行 ${lineOf(multi, "<!--")}〜${lineOf(multi, "-->")}: HTML コメントは置けない"
        // 文の途中の開始も、コメントの行として数える
        check(appended("文 <!-- 補足 -->")).shouldBeOneAt(firstExtraLine(), "HTML コメントは置けない")
        // コードブロックの中の `<!--` は、コメントではなく、Gherkin の行として検査する
        val inBlock = appended(gherkin("Feature: 例", "<!-- 補足 -->"))
        check(inBlock).shouldBeOneAt(lineOf(inBlock, "<!--"), "Gherkin に置けない行")
    }

    "表の ID の行: 見出しがテンプレートと同じ US・AC・5.2 の表の、先頭列がその表の ID の形の行は、中身を問わず置ける" {
        check(good.replace("\n\n### 2. ユースケース（UC）", "\n| US-99 | 自由な文 |\n\n### 2. ユースケース（UC）")).shouldBeEmpty()
        check(good.replaceFirst("\n\n```gherkin", "\n| AC-99-99 | 自由な文 |\n\n```gherkin")).shouldBeEmpty()
        check(good.trimEnd() + "\n| C-99 | 自由な文 |\n").shouldBeEmpty() // 末尾の表は、5.2 の表。続けて書いた行は、同じ表の行
        val detached = appended("| C-99 | 自由な文 |") // 空行を挟むと、見出しの無い別の表になる
        check(detached).shouldBeOneAt(firstExtraLine(), "3 種のどれにも当たらない行")
    }

    "反例: ID の形の先頭列でも、表の種類が違う行と、テンプレートにない見出しの表の行は、違反になる" {
        // 5.2 の表に US の形の行
        val wrongKind = good.trimEnd() + "\n| US-99 | 自由な文 |\n"
        check(wrongKind).shouldBeOneAt(lineOf(wrongKind, "| US-99"), "3 種のどれにも当たらない行")
        // UC の節の外の、見出しがテンプレートの AC の表と違う表
        val other = appended("| 項目 | 内容 |\n|---|---|\n| C-99 | 自由な文 |")
        val errors = check(other)
        errors.shouldHaveSize(1)
        errors.first() shouldStartWith "行 ${lineOf(other, "| C-99 | 自由な文 |")}: "
    }

    "反例: ID の形ではない先頭列の行は、ID の表の中でも違反になる" {
        val text = good.trimEnd() + "\n| 99 | 自由な文 |\n"
        check(text).shouldBeOneAt(lineOf(text, "| 99"), "3 種のどれにも当たらない行")
    }

    "UC の節: 見出しが UC- で始まる節の中は、地の文・リスト・表・小見出しも置け、次の同じ深さ以上の見出しで終わる" {
        val inside = appended("#### UC-09: 例\n\n自由な文。\n\n- 項目\n\n##### 小見出し\n\n| a | b |\n|---|---|\n| 1 | 2 |")
        check(inside).shouldBeEmpty()
        val after = appended("#### UC-09: 例\n\n自由な文。\n\n### 4. 因子・水準\n\n自由な文。")
        val errors = check(after)
        errors.shouldHaveSize(2)
        errors.map { it.substringBefore(":") } shouldBe listOf("行 ${lineOf(after, "### 4.")}", "行 ${lineOf(after, "### 4.") + 2}")
    }

    "UC の節: 見出しの深さがテンプレートの UC の見出し（####）と同じものだけが UC の節になり、ほかの深さは UC の節にならない" {
        template.ucLevel shouldBe 4
        listOf("#", "##", "###", "#####", "######").forEach { marks ->
            val text = appended("$marks UC-99: x\n\n自由な文。")
            val errors = check(text)
            withClue(marks) {
                errors.shouldHaveSize(2) // 見出しの行と、中の文。どちらもほかの種類に当たらない
                errors.map { it.substringBefore(":") } shouldBe listOf("行 ${lineOf(text, "$marks UC-99")}", "行 ${lineOf(text, "$marks UC-99") + 2}")
            }
        }
        check(appended("#### UC-99: x\n\n自由な文。")).shouldBeEmpty()
        // UC の節の中の、より深い見出しは、節の中のまま。同じ深さ以上の見出しで終わる
        check(appended("#### UC-99: x\n\n##### UC-98: y\n\n自由な文。")).shouldBeEmpty()
        check(appended("#### UC-99: x\n\n#### 例\n\n自由な文。")).shouldHaveSize(2) // 次の #### は UC の見出しでないので、節が終わる
        // 深さは、テンプレートの UC の見出しから読む。深さを変えたテンプレートでは、実ファイルの #### の節は UC の節にならない
        val deeper = GeneratedLineCheck.readTemplate(templateText.replace("#### UC-01:", "##### UC-01:"))
        deeper.ucLevel shouldBe 5
        GeneratedLineCheck.violations(good, deeper, goodTrs).isNotEmpty() shouldBe true
        val none = GeneratedLineCheck.readTemplate(templateText.replace("#### UC-01:", "#### 例:"))
        none.errors.shouldHaveSize(1)
        none.errors.first() shouldContain "UC の見出し"
    }

    "リンク参照の定義の行: どこにあっても違反になる（UC の節の中も）。コードブロックの中は Gherkin の行として検査する" {
        check(appended("[//]: # (400)")).shouldBeOneAt(firstExtraLine(), "リンク参照の定義の行は置けない")
        check(appended("[ラベル]: ./other.md")).shouldBeOneAt(firstExtraLine(), "リンク参照の定義の行は置けない")
        check(appended("  [a]: https://example.com \"title\"")).shouldBeOneAt(firstExtraLine(), "リンク参照の定義の行は置けない")
        val inUc = appended("#### UC-09: 例\n\n[//]: # (400)\n\n自由な文。")
        check(inUc).shouldBeOneAt(lineOf(inUc, "[//]:"), "リンク参照の定義の行は置けない")
        val inBlock = appended(gherkin("Feature: 例", "[//]: # (400)"))
        check(inBlock).shouldBeOneAt(lineOf(inBlock, "[//]:"), "Gherkin に置けない行")
        // 定義ではない、`[` で始まる行は、定義として数えない（UC の節の中なら置ける）
        check(appended("#### UC-09: 例\n\n[注] 自由な文。\n\n[a](b.md) の文。")).shouldBeEmpty()
    }

    "定型文の行のリンク先: .md への相対パス（#アンカー可）に限り、title・絶対 URL・.md 以外・値を入れた形は違反になる" {
        val target = "(../TODO-001-task-management.md)"
        good shouldContain target
        listOf("(../other.md)", "(./other.md#anchor)", "(other.md#章の名前)", "(../../a/b.md#x-y)").forEach { ok ->
            withClue(ok) { check(good.replace(target, ok)).shouldBeEmpty() }
        }
        listOf(
            "(../x.md \"title\")", "(https://example.com/x.md)", "(http://example.com)", "(/abs/x.md)", "(../x.txt)", "(../x)", "(../x.md?v=1)", "()", "(#anchor)",
            "(mailto:a@example.com)", "(../x.md#a b)",
        ).forEach { bad ->
            val errors = check(good.replace(target, bad))
            withClue(bad) {
                errors.shouldHaveSize(1)
                errors.first() shouldStartWith "行 ${lineOf(good, "> このファイルは")}: 定型文の行のリンク先は、.md への相対パス"
            }
        }
        // 題の行のリンクも同じ
        val tpl = GeneratedLineCheck.readTemplate(miniTemplate(""))
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: [名前](./x.md)", tpl, miniTrs("T-1", "名前")).shouldBeEmpty()
        GeneratedLineCheck.violations("# TRS-T-1 の生成物: [名前](https://example.com)", tpl, miniTrs("T-1", "名前")).shouldBeOneAt(1, "リンク先は、.md への相対パス")
        // UC の節の中や表の ID の行は、定型文の行ではないので、リンク先を問わない
        check(appended("#### UC-09: 例\n\n[a](https://example.com \"t\") の文。")).shouldBeEmpty()
    }

    "UC の節の見出し: UC-<数字>: で始まるものだけ。全角のコロンも可で、コロンの無い見出しは UC の節にならない" {
        check(appended("#### UC-09： 例\n\n自由な文。")).shouldBeEmpty()
        check(appended("#### UC-09 : 例\n\n自由な文。")).shouldBeEmpty()
        val noColon = appended("#### UC-09 例\n\n自由な文。")
        val errors = check(noColon)
        errors.shouldHaveSize(2)
        errors.map { it.substringBefore(":") } shouldBe listOf("行 ${lineOf(noColon, "#### UC-09")}", "行 ${lineOf(noColon, "#### UC-09") + 2}")
        val subId = appended("#### UC-09-A1: 例\n\n自由な文。")
        check(subId).shouldHaveSize(2) // UC-09-A1: は、UC-<数字>: の形ではないので、UC の節にならない
    }

    "表の区切り行: 空白をすべて除いて比べる（空白の数・位置が違っても、全角の空白があっても一致し、中身が違えば一致しない）" {
        val separator = "| ID | ストーリー | 出典（要件 ID） |\n|---|---|---|"
        good shouldContain separator
        check(good.replace(separator, "| ID | ストーリー | 出典（要件 ID） |\n| --- | --- | --- |")).shouldBeEmpty()
        check(good.replace(separator, "| ID | ストーリー | 出典（要件 ID） |\n|  ---  |\t---|---  |")).shouldBeEmpty()
        check(good.replace(separator, "| ID | ストーリー | 出典（要件 ID） |\n|\u3000---\u3000|---|---|")).shouldBeEmpty()
        check(good.replace(separator, "| ID | ストーリー | 出典（要件 ID） |\n| - - - | --- | --- |")).shouldBeEmpty() // 空白をすべて除くので、ハイフンの間の空白も除く
        check(good.replace(separator, "| ID | ストーリー | 出典（要件 ID） |\n|:---|---|---|")).shouldHaveSize(1)
        check(good.replace(separator, "| ID | ストーリー | 出典（要件 ID） |\n|---|---|---|---|---|")).shouldHaveSize(1) // 列数が、テンプレートのどの区切り行とも違う
    }

    "表の区切り行: 普通の行の空白は、連続する空白を 1 つにするだけで、すべては除かない" {
        check(good.replace("> 手で編集しない。", "> 手で 編集しない。")).shouldHaveSize(1)
    }

    // ---- Gherkin ----

    "Gherkin: Feature の 1 行、Background と手順、タグ行に AC を持つシナリオと、その手順・Examples は置ける" {
        val block = gherkin(
            "Feature: 例",
            "",
            "  Background:",
            "    Given 前提",
            "",
            tagged,
            "  Scenario Outline: 例",
            "    Given <x> がある",
            "    When  操作する",
            "    Then  結果になる",
            "    And   別の結果になる",
            "    But   これはならない",
            "    * 記号の手順",
            "",
            "    Examples:",
            "      | x |",
            "      | 1 |",
            "",
            tagged,
            "  Scenario: 別の例",
            "    Given 前提",
            "",
            tagged,
            "  Scenario Template: 別名",
            "    Given 前提",
            "",
            tagged,
            "  Example: 別名",
            "    Given 前提",
        )
        check(appended(block)).shouldBeEmpty()
    }

    "反例（Gherkin）: Feature の説明文・Rule・タグ行のないシナリオ・# のコメントは違反になる" {
        val cases = listOf(
            "Feature: 例\n  説明文を書く" to "  説明文を書く",
            "Feature: 例\n  Rule: 規則" to "  Rule: 規則",
            "Feature: 例\n  Scenario: タグ行がない\n    Given 前提" to "  Scenario: タグ行がない",
            "Feature: 例\n  # コメント" to "  # コメント",
            "Feature: 例\n# language: ja" to "# language: ja", // # で始まる行は、language でも、位置によらず置けない
        )
        cases.forEach { (block, offending) ->
            val text = appended(gherkin(*block.split("\n").toTypedArray()))
            val errors = check(text)
            withClue(block) {
                errors.shouldHaveSize(1)
                errors.first() shouldStartWith "行 ${lineOf(text, offending)}: Gherkin に置けない行"
            }
        }
    }

    "反例（Gherkin）: @AC-xx-yy の無いタグ行・シナリオから離れたタグ行・説明文つきのタグ行は違反になる" {
        val noAc = appended(gherkin("Feature: 例", "  @layer:1", "  Scenario: AC のタグが無い", "    Given 前提"))
        check(noAc).shouldHaveSize(2) // タグ行と、直前のタグ行に AC が無いシナリオ
        val apart = appended(gherkin("Feature: 例", tagged, "  @layer:1", "  Scenario: タグ行が 2 行", "    Given 前提"))
        check(apart).shouldHaveSize(3) // AC を持つ上のタグ行は、シナリオの直前にない。下のタグ行は AC を持たない。シナリオは、直前のタグ行に AC が無い
        val dangling = appended(gherkin("Feature: 例", tagged))
        check(dangling).shouldHaveSize(1)
        val withText = appended(gherkin("Feature: 例", "  @AC-01-20 説明", "  Scenario: 説明つきのタグ行", "    Given 前提"))
        check(withText).shouldHaveSize(2) // 説明つきの行は、タグ行ではない。シナリオは、直前の行がタグ行ではない
    }

    "反例（Gherkin）: Feature が 2 つ、Background の外の手順、Examples の下以外の表、docstring、Scenarios: は違反になる" {
        listOf(
            gherkin("Feature: 例", "Feature: 例 2") to 1,
            gherkin("Feature: 例", "  Given 前提がシナリオの外にある") to 1,
            gherkin("Feature: 例", tagged, "  Scenario: 例", "    Given 前提", "      | a |") to 1,
            gherkin("Feature: 例", tagged, "  Scenario: 例", "    Given 前提", "      \"\"\"", "      長い文", "      \"\"\"") to 3,
            gherkin("Feature: 例", tagged, "  Scenario Outline: 例", "    Given 前提", "    Scenarios:", "      | a |") to 2,
            gherkin("Feature: 例", "  Examples:", "    | a |") to 2,
            gherkin("Feature: 例", tagged, "  Scenario Outline: 例", "    Examples:", "      | a |", "    Then 結果が Examples の後にある") to 1,
        ).forEach { (block, count) ->
            withClue(block) { check(appended(block)) shouldHaveSize count }
        }
    }

    "反例（Gherkin）: 失敗したシナリオの手順と Examples は、同じ理由で重ねて報告しない" {
        val text = appended(gherkin("Feature: 例", "  Scenario Outline: タグ行がない", "    Given 前提", "    Examples:", "      | a |", "      | 1 |"))
        check(text).shouldBeOneAt(lineOf(text, "  Scenario Outline:"), "直前のタグ行に @AC-xx-yy が無いシナリオ")
    }

    "Gherkin 以外のコードブロックは、言語を問わず、置けない（言語なし・kotlin・markdown。中の行は定型文として数えない）" {
        listOf("", "kotlin", "markdown", "text", "Gherkin").forEach { language ->
            val text = appended(fenced(language, "Feature: 例", "Scenario: 中の行"))
            withClue("言語: $language") {
                check(text).shouldBeOneAt(firstExtraLine(), "gherkin 以外のコードブロックは置けない")
            }
        }
    }

    "コードブロックの閉じ方は、既存の読み取りと同じ: 開きより長い囲みの中の短い囲みは、閉じにならない" {
        val text = appended("````gherkin\nFeature: 例\n```\n  説明文を書く\n````")
        val errors = check(text)
        errors.shouldHaveSize(2) // 内側の囲みの行と、説明文の行。どちらも Gherkin に置けない行
        errors.all { it.contains("Gherkin に置けない行") } shouldBe true
    }

    "Gherkin のコードブロックは、UC の節の中でも、同じ規則で検査する" {
        val text = appended("#### UC-09: 例\n\n" + gherkin("Feature: 例", "  説明文を書く") + "\n\n```kotlin\nval x = 1\n```")
        val errors = check(text)
        errors.shouldHaveSize(2)
        errors[0] shouldContain "Gherkin に置けない行"
        errors[1] shouldContain "gherkin 以外のコードブロックは置けない"
    }

    "Gherkin: シナリオの直前の行は、空行を挟まない、@AC-xx-yy を持つタグ行 1 行だけ（空行を挟む・タグ行が 2 行・タグ行がシナリオから離れていると違反）" {
        check(appended(gherkin("Feature: 例", tagged, "  Scenario: 例", "    Given 前提"))).shouldBeEmpty()
        val blank = appended(gherkin("Feature: 例", tagged, "", "  Scenario: 例", "    Given 前提"))
        val errors = check(blank)
        errors.shouldHaveSize(2) // タグ行は直後がシナリオでなく、シナリオは直前が空行
        errors.map { it.substringBefore(":") } shouldBe listOf("行 ${firstExtraLine() + 2}", "行 ${firstExtraLine() + 4}") // 足した Feature の 2 行後がタグ行、その 2 行後がシナリオ
        check(appended(gherkin("Feature: 例", tagged, "  @layer:1", "  Scenario: 例", "    Given 前提"))).shouldHaveSize(3)
        check(appended(gherkin("Feature: 例", "  @layer:1", tagged, "  Scenario: 例", "    Given 前提"))).shouldHaveSize(1) // 直前の 1 行が AC を持てば、その上の別のタグ行は、置けない
    }

    "Gherkin: Examples とその下の表の行は、Scenario Outline（Scenario Template を含む）の中でだけ置ける" {
        val outline = gherkin("Feature: 例", tagged, "  Scenario Outline: 例", "    Given <x> がある", "", "    Examples:", "      | x |", "      | 1 |", "", "    Examples:", "      | x |", "      | 2 |")
        check(appended(outline)).shouldBeEmpty()
        check(appended(gherkin("Feature: 例", tagged, "  Scenario Template: 例", "    Given <x> がある", "    Examples:", "      | x |", "      | 1 |"))).shouldBeEmpty()
        listOf("Scenario", "Example").forEach { keyword ->
            val text = appended(gherkin("Feature: 例", tagged, "  $keyword: 例", "    Given 前提", "    Examples:", "      | x |", "      | 1 |"))
            val errors = check(text)
            withClue(keyword) {
                errors.shouldHaveSize(3) // Examples と、その下の表の 2 行（Examples の下ではない行）
                errors.first() shouldContain "Scenario Outline の外の Examples"
            }
        }
        check(appended(gherkin("Feature: 例", "    Examples:", "      | x |"))).shouldHaveSize(2)
    }

    "表の区切り行: 縦線を含み、縦線・ハイフン・コロンだけでできた行だけを区切り行として扱う（縦線の無い行は、空白を除いて比べない）" {
        val separator = "|---|---|---|"
        good shouldContain separator
        check(appended("---")).shouldBeEmpty() // `---` の行は、テンプレートの定型文の行
        check(appended("- - -")).shouldBeOneAt(firstExtraLine(), "3 種のどれにも当たらない行")
        check(appended("-----")).shouldBeOneAt(firstExtraLine(), "3 種のどれにも当たらない行")
        check(appended("---|---|---")).shouldBeOneAt(firstExtraLine(), "3 種のどれにも当たらない行") // 縦線を含むが、テンプレートの区切り行とは、縦線の位置が違う
        check(appended("| --- | --- | --- |")).shouldBeEmpty()
    }

    "反例（Gherkin）: # language は、最初の行でも、en でも ja でも置けない（# で始まる行は、位置によらず違反）" {
        listOf("ja", "en", "fr").forEach { code ->
            val text = appended(gherkin("# language: $code", "Feature: 例"))
            withClue(code) { check(text).shouldBeOneAt(firstExtraLine() + 1, "Gherkin に置けない行") }
        }
        // 日本語のキーワードは、読み替えない。英語のキーワードだけが置ける
        val ja = appended(gherkin("機能: 例", "  背景:", "    前提 事前"))
        check(ja).shouldHaveSize(3)
        val mid = appended(gherkin("Feature: 例", "", "# language: ja", tagged, "  Scenario: 例", "    Given 前提"))
        check(mid).shouldBeOneAt(lineOf(mid, "# language"), "Gherkin に置けない行")
    }

    // ---- テンプレートが読めないとき ----

    "テンプレートが読めない: 範囲の見出しが無い・「生成物のファイルの冒頭」の節か、その markdown のコードブロックが無い・空のとき、生成物のファイルが定型文に当たっても違反になる（黙って通さない）" {
        val removals = mapOf(
            "## 第1部：仕様レイヤー" to "第1部",
            "## 第2部：テスト設計レイヤー" to "第2部",
            "### 1. ユーザーストーリー（US）" to "1.",
            "### 2. ユースケース（UC）" to "2.",
            "### 3. 受入条件（AC）" to "3.",
            "### 5. 因子制約ルール" to "5.",
            "#### 5.2 組合せ別の期待結果" to "5.2",
        )
        removals.forEach { (heading, anchor) ->
            val broken = GeneratedLineCheck.readTemplate(templateText.replace(heading, "## 見出しを変えた"))
            withClue(heading) {
                broken.errors.shouldHaveSize(1)
                broken.errors.first() shouldContain "（$anchor）"
                GeneratedLineCheck.violations(good, broken, goodTrs) shouldBe broken.errors
            }
        }
        listOf(
            templateText.replace("## 生成物のファイルの冒頭", "## 冒頭") to "の節",
            templateText.replace("```markdown", "```text") to "のコードブロックが見つからない",
            templateText.replace(Regex("""(?s)```markdown\n.*?\n```"""), "```markdown\n\n```") to "が空",
            "" to "見出し",
        ).forEach { (broken, reason) ->
            val tpl = GeneratedLineCheck.readTemplate(broken)
            withClue(reason) {
                tpl.errors.isNotEmpty() shouldBe true
                tpl.errors.any { it.contains(reason) } shouldBe true
                GeneratedLineCheck.violations(good, tpl, goodTrs) shouldBe tpl.errors
            }
        }
    }

    "取る範囲: 第 2 部は、見出しの直下の引用だけを取る（その下の節 4 の引用は、節 4 が範囲に無いので取らない）" {
        val tpl = GeneratedLineCheck.readTemplate(miniTemplate(""))
        miniCheck(tpl, "> 第 2 部の直下").shouldBeEmpty()
        miniCheck(tpl, "> 節 4 の引用").shouldHaveSize(1)
    }

    "取る範囲: 節 5 の下は、5.2 を含めて取り、5.1 の下は取らない" {
        val tpl = GeneratedLineCheck.readTemplate(miniTemplate(""))
        miniCheck(tpl, "> 節 5 の引用").shouldBeEmpty()
        miniCheck(tpl, "#### 5.2 F").shouldBeEmpty()
        miniCheck(tpl, "> 5.2 の下の引用").shouldBeEmpty()
        miniCheck(tpl, "#### 5.1 E").shouldHaveSize(1)
        miniCheck(tpl, "> 5.1 の引用").shouldHaveSize(1)
    }
})

private const val MINI_TITLE = "# TRS-T-1 の生成物: 名前"

private fun miniTrs(id: String, name: String) = "| 項目 | 内容 |\n|---|---|\n| TRS ID | $id |\n| 機能名 | $name |\n"

/** 最小のテンプレートと組の TRS ファイルで、題の行の後ろに [body] を置いた生成物のファイルを検査する */
private fun miniCheck(template: GeneratedLineCheck.Template, body: String) =
    GeneratedLineCheck.violations("$MINI_TITLE\n\n$body", template, miniTrs("T-1", "名前"))

/** 03 §3.4.3 の「取る範囲」の見出しと、「生成物のファイルの冒頭」の節（題の行の後ろの本文は [preamble]）だけを持つ、最小のテンプレート */
private fun miniTemplate(preamble: String): String = """
# TRS-<機能ID>: <機能名>

## 第1部：仕様レイヤー

> 第 1 部の引用

### 1. A

### 2. B

#### UC-01: 例

### 3. C

## 第2部：テスト設計レイヤー

> 第 2 部の直下

### 4. X

> 節 4 の引用

### 5. D

> 節 5 の引用

#### 5.1 E

> 5.1 の引用

#### 5.2 F

> 5.2 の下の引用

## 生成物のファイルの冒頭

```markdown
# TRS-<機能ID> の生成物: <機能名>
$preamble
```
""".trimIndent()
