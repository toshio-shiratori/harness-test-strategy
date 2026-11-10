package com.example.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * [InputDocCheck] の自己テスト（03 §3.4.5 の ⑩ 目次と ⑪ 用語の言い換え）。入力は文字列で組み立てる。実ファイルの検査は [TraceabilityTest] にある。
 */
class InputDocCheckTest : StringSpec({
    val requirementToc = InputDocCheck.requirementToc
    val designToc = InputDocCheck.designToc

    fun tocOfRequirement(text: String) = InputDocCheck.tocViolations("要件定義書", mapOf("d.md" to text), requirementToc)

    fun tocOfDesign(text: String) = InputDocCheck.tocViolations("基本設計書", mapOf("d.md" to text), designToc)

    fun List<String>.shouldBeSingleWithPrefix(prefix: String) {
        size shouldBe 1
        first().startsWith(prefix) shouldBe true
    }

    // ---- ⑩ 目次 ----

    "⑩ 正例: 目次の節がすべて、番号・名前・順序・数のとおりにあれば合格する（番号の無い見出しとコードブロックの中の見出しは節に数えない）" {
        tocOfRequirement(requirementDoc() + "\n```text\n## 9. 付録\n```\n").shouldBeEmpty()
        tocOfDesign(designDoc() + "\n<!--\n## 11. 付録\n-->\n").shouldBeEmpty()
    }

    "⑩ 反例: 節が欠けていると違反になる（要件定義書・基本設計書）" {
        tocOfDesign(designDoc().replace("## 3. 帳票", "### 3. 帳票")).shouldBeSingleWithPrefix("d.md: 基本設計書 に節「帳票」が無い")
        tocOfRequirement(requirementDoc().replace("## 6. 業務ルール", "### 6. 業務ルール")).shouldBeSingleWithPrefix("d.md: 要件定義書 に節「業務ルール」が無い")
    }

    "⑩ 反例: 文書が空、または節に番号が無いと、すべての節が欠けているので違反になる" {
        tocOfDesign("").size shouldBe designToc.size
        tocOfDesign("# 基本設計書\n\n## 画面\n\n## 帳票\n").size shouldBe designToc.size
    }

    "⑩ 反例: 節の順序が入れ替わっていると違反になる" {
        val swapped = designDoc().replace("## 2. 画面", "## 2. 帳票X").replace("## 3. 帳票", "## 3. 画面").replace("## 2. 帳票X", "## 2. 帳票")
        tocOfDesign(swapped).shouldBeSingleWithPrefix("d.md: 基本設計書 の節の番号か順序が、目次と違う")
    }

    "⑩ 反例: 節の番号が目次とずれていると違反になる（名前は同じ）" {
        tocOfRequirement(requirementDoc().replace("## 4. 業務フロー", "## 5. 業務フロー")).shouldBeSingleWithPrefix("d.md: 要件定義書 の節の番号か順序が、目次と違う")
    }

    "⑩ 反例: 目次に無い節があると違反になる（余分な節）" {
        tocOfDesign(designDoc() + "\n## 11. 付録\n").shouldBeSingleWithPrefix("d.md: 基本設計書 に目次に無い節「11. 付録」がある")
    }

    "⑩ 反例: 節の名前が違うと、欠けた節と余分な節の両方で違反になる（小見出しの名前は照合しない）" {
        val renamed = designDoc().replace("## 2. 画面", "## 2. 画面一覧").replace("## 2. 画面一覧", "## 2. 画面一覧\n\n### 2.1 どんな名前でもよい小見出し")
        tocOfDesign(renamed).also {
            it.size shouldBe 2
            it[0].startsWith("d.md: 基本設計書 に節「画面」が無い") shouldBe true
            it[1].startsWith("d.md: 基本設計書 に目次に無い節「2. 画面一覧」がある") shouldBe true
        }
    }

    "⑩ 反例: 同じ名前の節が複数あると違反になる" {
        tocOfDesign(designDoc() + "\n## 11. 画面\n").also { v -> v.any { it.contains("同じ名前の節「画面」が複数ある") } shouldBe true }
    }

    // ---- ⑪ 用語の言い換え ----

    fun terms(requirement: String = requirementDoc(), design: String = designDoc(), trs: String = trsFile(), generated: String = "# 生成物\n\n本文\n") =
        InputDocCheck.termViolations(mapOf("t.md" to requirement), mapOf("t.md" to design), mapOf("t.md" to trs), mapOf("t.md" to generated))

    fun lineOf(text: String, prefix: String) = text.lines().indexOfFirst { it.startsWith(prefix) } + 1

    "⑪ 正例: 言い換えの語がどの文書にも現れなければ合格する" {
        terms().shouldBeEmpty()
    }

    "⑪ 反例: 要件定義書の本文に言い換えの語があると違反になる" {
        val req = requirementDoc(body = "契約を結ぶ。")
        terms(requirement = req).shouldContainExactly("t.md: 要件定義書 の行 ${lineOf(req, "契約を結ぶ")} に、使わない言い換え「契約」がある")
    }

    "⑪ 反例: 基本設計書・TRS ファイル・生成物のファイルに言い換えの語があると違反になる" {
        val design = designDoc(body = "売買を行う。")
        terms(design = design).shouldContainExactly("t.md: 基本設計書 の行 ${lineOf(design, "売買を行う")} に、使わない言い換え「売買」がある")
        val trs = trsFile(body = "契約の AC を足す。")
        terms(trs = trs).shouldContainExactly("t.md: TRS ファイル の行 ${lineOf(trs, "契約の AC")} に、使わない言い換え「契約」がある")
        val generated = "# 生成物\n\n本文\n売買の流れ\n"
        terms(generated = generated).shouldContainExactly("t.md: 生成物のファイル の行 ${lineOf(generated, "売買の流れ")} に、使わない言い換え「売買」がある")
    }

    "⑪ 反例: コードブロックとインラインコードの中の言い換えも違反になる" {
        val design = designDoc(body = "`契約` を使う。\n\n```gherkin\nGiven 売買が成立している\n```")
        terms(design = design).shouldContainExactly(
            "t.md: 基本設計書 の行 ${lineOf(design, "`契約`")} に、使わない言い換え「契約」がある",
            "t.md: 基本設計書 の行 ${lineOf(design, "Given 売買")} に、使わない言い換え「売買」がある",
        )
    }

    "⑪ 反例: 英字は大文字と小文字を区別せず、部分一致で判定する" {
        listOf("deal", "DEAL", "Deal", "ideal").forEach { word ->
            val design = designDoc(body = "これは $word です。")
            terms(design = design).shouldContainExactly("t.md: 基本設計書 の行 ${lineOf(design, "これは")} に、使わない言い換え「Deal」がある")
        }
        // 「契約書」は用語なので除く。残った「売買契約」から、部分一致で「契約」と「売買」の両方が見つかる
        val partial = designDoc(body = "契約書を出す。売買契約を結ぶ。")
        terms(design = partial).shouldContainExactly(
            "t.md: 基本設計書 の行 ${lineOf(partial, "契約書を出す")} に、使わない言い換え「契約」がある",
            "t.md: 基本設計書 の行 ${lineOf(partial, "契約書を出す")} に、使わない言い換え「売買」がある",
        )
    }

    "⑪ 正例: 言い換えの語が、用語の列の語の一部として現れるときは違反にしない（用語の列の語を先に除く）" {
        // 用語「契約書」に「契約」が含まれる。「契約書」を除いたあとの本文を調べる
        terms(design = designDoc(body = "契約書を出す。")).shouldBeEmpty()
        // 長い用語から除く。「タスク」を先に除くと「一覧」が残り、言い換えの「一覧」に当たってしまう
        val req = requirementDoc(glossary = listOf("| タスク | やること | 該当なし |", "| タスク一覧 | 並べた表 | 一覧 |"))
        terms(requirement = req, design = designDoc(body = "タスク一覧を表示する。")).shouldBeEmpty()
    }

    "⑪ 反例: 用語の列の語を除いても、言い換えが別に残っていれば違反になる" {
        val design = designDoc(body = "契約書と契約を扱う。")
        terms(design = design).shouldContainExactly("t.md: 基本設計書 の行 ${lineOf(design, "契約書と契約")} に、使わない言い換え「契約」がある")
    }

    "⑪ 正例: 言い換えの語は、読点（、）と半角のカンマのどちらで区切ってもよく、「該当なし」は語なしとして読む" {
        val comma = InputDocCheck.terms(requirementDoc(glossary = listOf("| 取引 | 意味 | 契約,売買、 Deal |", "| 条件 | 意味 | 該当なし |")))
        comma.errors.shouldBeEmpty()
        comma.words shouldBe listOf("契約", "売買", "Deal")
        comma.terms shouldBe listOf("取引", "条件")
    }

    "⑪ 正例: 検査しない範囲（要件定義書の用語の小見出し、要件定義書と基本設計書の未決事項、TRS ファイルの Open Issues・運用情報）では、言い換えがあっても違反にしない" {
        val req = requirementDoc(glossary = listOf("| 取引 | 契約のこと | 契約、売買 |"), undecided = "| Q-01 | 契約を結ぶか | オーナー |")
        val design = designDoc(undecided = "| Q-01 | 売買を扱うか | オーナー | — |")
        val trs = trsFile(openIssues = "| OI-001 | 契約の扱い |", trace = "| AC-01-01 | 売買 |", reproduction = "契約の不具合", history = "売買を変更")
        terms(requirement = req, design = design, trs = trs).shouldBeEmpty()
    }

    "⑪ 反例: 検査しない範囲の外では、同じ文書の言い換えが違反になる（除外が節の外へ広がらない）" {
        val req = requirementDoc(glossary = listOf("| 取引 | 契約のこと | 契約、売買 |"), undecided = "| Q-01 | 契約を結ぶか | オーナー |", body = "売買を行う。")
        terms(requirement = req).shouldContainExactly("t.md: 要件定義書 の行 ${lineOf(req, "売買を行う")} に、使わない言い換え「売買」がある")
        val trs = trsFile(openIssues = "| OI-001 | 契約の扱い |", body = "売買を足す。")
        terms(trs = trs).shouldContainExactly("t.md: TRS ファイル の行 ${lineOf(trs, "売買を足す")} に、使わない言い換え「売買」がある")
        // 生成物のファイルには除外が無い。見出しが「変更履歴」でも、本文を検査する
        terms(generated = "# 生成物\n\n## 変更履歴\n\n契約を変更\n").shouldContainExactly("t.md: 生成物のファイル の行 5 に、使わない言い換え「契約」がある")
    }

    "⑪ 反例: TRS ファイルの題や UC の名前に、検査しない節の名前を含んでも、本文は検査する（除外は深さ・番号・名前の完全一致だけ）" {
        val title = trsFile(title = "TRS-TST-001: 変更履歴の表示", body = "契約を足す。")
        terms(trs = title).shouldContainExactly("t.md: TRS ファイル の行 ${lineOf(title, "契約を足す")} に、使わない言い換え「契約」がある")
        listOf("#### UC-01: トレーサビリティの確認", "#### UC-02: Open Issues の一覧", "## 9. 変更履歴", "### 5. 変更履歴", "### 変更履歴", "### 8. 変更履歴").forEach { heading ->
            val trs = trsFile(body = "$heading\n\n契約を足す。")
            withClue(heading) { terms(trs = trs).shouldContainExactly("t.md: TRS ファイル の行 ${lineOf(trs, "契約を足す")} に、使わない言い換え「契約」がある") }
        }
    }

    "⑪ 反例: 未決事項の節の判定は、深さ・番号・名前の完全一致である（要件定義書は節 8、基本設計書は節 10）" {
        val req = requirementDoc(body = "### 未決事項\n\n契約を結ぶ。")
        terms(requirement = req).shouldContainExactly("t.md: 要件定義書 の行 ${lineOf(req, "契約を結ぶ")} に、使わない言い換え「契約」がある")
        listOf("## 5. 未決事項", "## 未決事項", "### 10. 未決事項", "## 10. 未決事項の一覧").forEach { heading ->
            val design = designDoc(body = "$heading\n\n売買を扱う。")
            withClue(heading) { terms(design = design).shouldContainExactly("t.md: 基本設計書 の行 ${lineOf(design, "売買を扱う")} に、使わない言い換え「売買」がある") }
        }
    }

    "⑪ 反例: 言い換えが用語を含むときは、用語を除いても言い換えが残る（用語「期限」、言い換え「期限日」）" {
        val req = requirementDoc(glossary = listOf("| 期限 | 日付 | 期限日 |"))
        val design = designDoc(body = "期限日を表示する。期限を表示する。")
        terms(requirement = req, design = design).shouldContainExactly("t.md: 基本設計書 の行 ${lineOf(design, "期限日を表示")} に、使わない言い換え「期限日」がある")
        // 用語だけの行は違反にしない。言い換え「期限日」を含む用語（「期限日一覧」）に含まれるときは、その用語を除くので違反にしない
        terms(requirement = req, design = designDoc(body = "期限を表示する。")).shouldBeEmpty()
        val withTerm = requirementDoc(glossary = listOf("| 期限 | 日付 | 期限日 |", "| 期限日一覧 | 表 | 該当なし |"))
        terms(requirement = withTerm, design = designDoc(body = "期限日一覧を表示する。")).shouldBeEmpty()
    }

    "⑪ 反例: 同じ名前の要件定義書が無い基本設計書と TRS ファイルは、用語を確かめられないので違反になる" {
        InputDocCheck.termViolations(mapOf("t.md" to requirementDoc()), mapOf("x.md" to designDoc()), mapOf("y.md" to trsFile()), emptyMap()).shouldContainExactly(
            "x.md: 同じ名前の要件定義書が無い（基本設計書。用語を確かめられない）",
            "y.md: 同じ名前の要件定義書が無い（TRS ファイル。用語を確かめられない）",
        )
    }

    "⑪ 反例: 用語の小見出しが無い、または節 2 の外にあると違反になる（言い換えを読めないまま素通りしない）" {
        val message = "t.md: 要件定義書の用語の小見出しが読めない（節 2 に、見出しに「用語」を含む小見出しを置く）"
        terms(requirement = requirementDoc().replace("### 2.2 用語", "### 2.2 言葉")).shouldContainExactly(message)
        terms(requirement = requirementDoc().replace("### 2.2 用語", "### 2.2 言葉") + "\n### 用語\n\n| 用語 | 意味 | 使わない言い換え |\n|---|---|---|\n| a | b | c |\n").shouldContainExactly(message)
    }

    "⑪ 反例: 用語の表の見出しが規定と違う、または表が無いと違反になる" {
        val message = "t.md: 要件定義書の用語の表の見出しが読めない（見出しは 用語 | 意味 | 使わない言い換え にする）"
        terms(requirement = requirementDoc().replace("| 用語 | 意味 | 使わない言い換え |", "| 用語 | 意味 | 言い換え |")).shouldContainExactly(message)
        terms(requirement = requirementDoc().replace("| 用語 | 意味 | 使わない言い換え |", "| 用語 | 意味 |")).shouldContainExactly(message)
        terms(requirement = requirementDoc(glossary = emptyList())).shouldContainExactly(message)
    }

    "⑪ 反例: 言い換えの語が、用語の列のどれかの語と同じだと、用語の表の矛盾として違反になる（その語は先に除かれ、検出できないため）" {
        val message = { w: String -> "t.md: 要件定義書の用語の表が矛盾している（言い換え「$w」が、用語の列の語と同じ。用語は本文から先に除かれ、言い換えを検出できない）" }
        terms(requirement = requirementDoc(glossary = listOf("| 取引 | 意味 | 契約 |", "| 契約 | 意味 | 該当なし |"))).shouldContainExactly(message("契約"))
        terms(requirement = requirementDoc(glossary = listOf("| Deal | 意味 | 該当なし |", "| 取引 | 意味 | deal、売買 |"))).shouldContainExactly(message("deal"))
        // 自分の行の用語と同じ語も矛盾である。一部が同じだけ（用語「契約書」、言い換え「契約」）なら矛盾ではない
        terms(requirement = requirementDoc(glossary = listOf("| 契約 | 意味 | 契約 |"))).shouldContainExactly(message("契約"))
        terms(requirement = requirementDoc(glossary = listOf("| 契約書 | 意味 | 契約 |"))).shouldBeEmpty()
    }

    "⑪ 反例: 「使わない言い換え」のセルが空、空の語を含む、列数が合わないと違反になる" {
        val empty = requirementDoc(glossary = listOf("| 取引 | 意味 |  |"))
        terms(requirement = empty).shouldContainExactly("t.md: 要件定義書の用語の表の「使わない言い換え」が空（行 ${lineOf(empty, "| 取引")}。無いときは「該当なし」と書く）: 取引")
        val blankWord = requirementDoc(glossary = listOf("| 取引 | 意味 | 契約、 |"))
        terms(requirement = blankWord).shouldContainExactly("t.md: 要件定義書の用語の表の「使わない言い換え」に空の語がある（行 ${lineOf(blankWord, "| 取引")}。区切りは読点か半角のカンマ）: 契約、")
        val columns = requirementDoc(glossary = listOf("| 取引 | 意味 |"))
        terms(requirement = columns).shouldContainExactly("t.md: 要件定義書の用語の表の列数が見出しと合わない（行 ${lineOf(columns, "| 取引")}）")
    }
})

/** [toc] の節を順に並べた最小の文書。節 [extras] に本文を足す。節の本文は、無ければ「該当なし」 */
private fun docOf(title: String, toc: List<String>, extras: Map<String, String>): String = buildString {
    appendLine("# $title")
    appendLine()
    appendLine("## メタ情報")
    appendLine()
    appendLine("| 項目 | 内容 |")
    appendLine("|---|---|")
    appendLine("| 機能名 | 例 |")
    appendLine()
    toc.forEachIndexed { i, name ->
        appendLine("## ${i + 1}. $name")
        appendLine()
        appendLine(extras[name] ?: "該当なし")
        appendLine()
    }
    appendLine("## レビューチェックリスト")
    appendLine()
    appendLine("- [ ] 確認した")
}

/**
 * 要件定義書の最小の例。用語の表（節 2）の行は [glossary]（既定は、言い換えが「契約、売買、Deal」の用語「取引」と、用語「契約書」）。
 * [body] は節 5（機能要件）の地の文、[undecided] は節 8（未決事項）に置く表の行
 */
private fun requirementDoc(
    glossary: List<String> = listOf("| 取引 | お金のやり取り | 契約、売買、Deal |", "| 契約書 | 取引の書面 | 該当なし |"),
    body: String = "該当なし",
    undecided: String = "該当なし",
): String {
    val table = if (glossary.isEmpty()) "" else "| 用語 | 意味 | 使わない言い換え |\n|---|---|---|\n" + glossary.joinToString("\n")
    return docOf(
        "要件定義書: 例",
        InputDocCheck.requirementToc,
        mapOf(
            "利用者と用語" to "### 2.1 利用者\n\n| 利用者 | 説明 |\n|---|---|\n| 持ち主 | 本人 |\n\n### 2.2 用語\n\n用語の説明。\n\n$table\n",
            "機能要件" to body,
            "未決事項" to if (undecided == "該当なし") undecided else "| ID | 内容 | 担当 |\n|---|---|---|\n$undecided",
        ),
    )
}

/** 基本設計書の最小の例。[body] は節 2（画面）の地の文、[undecided] は節 10（未決事項）に置く表の行 */
private fun designDoc(body: String = "該当なし", undecided: String = "該当なし"): String = docOf(
    "基本設計書: 例",
    InputDocCheck.designToc,
    mapOf(
        "画面" to body,
        "未決事項" to if (undecided == "該当なし") undecided else "| ID | 内容 | 担当 | OI-ID |\n|---|---|---|---|\n$undecided",
    ),
)

/**
 * TRS ファイルの最小の例。[body] は検査する本文、[openIssues]・[trace]・[reproduction]・[history] は、
 * 検査しない節（6 Open Issues、7 トレーサビリティ、8 不具合還元履歴、9 変更履歴）の本文
 */
private fun trsFile(
    title: String = "TRS-TST-001: 例",
    body: String = "仕様の本文。",
    openIssues: String = "| OI-001 | 論点 |",
    trace: String = "| AC-01-01 | テスト |",
    reproduction: String = "なし",
    history: String = "初版",
): String = """
# $title

## 第1部：仕様レイヤー

$body

## 第2部：テスト設計レイヤー

### 5. 因子制約ルール

#### 5.1 禁止（成立しない）組合せ

該当なし

### 6. Open Issues

$openIssues

#### 6.1 起票した Open Issue の選択肢

$openIssues

## 第3部：運用情報

### 7. トレーサビリティ

$trace

#### 7.1 採番しない ID

$trace

### 8. 不具合還元履歴

$reproduction

### 9. 変更履歴

$history

## レビューチェックリスト

- [ ] 確認した
""".trimStart()
