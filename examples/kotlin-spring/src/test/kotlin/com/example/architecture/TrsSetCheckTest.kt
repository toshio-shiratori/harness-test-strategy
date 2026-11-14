package com.example.architecture

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * [TrsSetCheck] など、TRS ファイルと生成物のファイルの組を検査する部品の自己テスト（03 §3.4 の失敗条件ごとに、最小の文字列で失敗させて確かめる）。
 * 入力は文字列で組み立てる。実ファイルの検査は [TraceabilityTest] にある。
 * このファイルの AC ID（`AC-01-01` など）は検査の入力であり、テスト名ではないので、[selfTestFiles] として ① ⑦ の照合から除く。
 */
class TrsSetCheckTest : StringSpec({
    val req = mapOf("req.md" to reqDocRows(listOf("| REQ-TST-01 | 説明 | |", "| REQ-TST-02 | 説明 | |", "| REQ-TST-03 | 説明 | |")))
    val des = mapOf("design.md" to designDoc())

    fun check(
        trs: String = trsFile(),
        gen: String = generatedFile(),
        requirements: Map<String, String> = req,
        designs: Map<String, String> = des,
    ) = TrsSetCheck.violations(mapOf("t.md" to trs), mapOf("t.md" to gen), requirements, designs)

    fun List<String>.shouldHavePrefix(prefix: String) = any { it.startsWith(prefix) } shouldBe true

    "正例: 組になった TRS ファイルと生成物のファイルは、失敗条件に当たらなければ合格する（トレーサビリティ表は TRS ファイルにあってよい）" {
        check().shouldBeEmpty()
    }

    // ---- 検査の単位（03 §3.4 の冒頭の表） ----

    "反例: 対になる生成物のファイルが無い TRS ファイルは違反になる（生成物のフォルダが無いときも同じ）" {
        TrsSetCheck.violations(mapOf("t.md" to trsFile()), emptyMap(), req, des)
            .shouldHavePrefix("t.md: 対になる生成物のファイルが無い（TRS ファイルだけがある）")
    }

    "反例: 対になる TRS ファイルが無い生成物のファイルは違反になる" {
        TrsSetCheck.violations(emptyMap(), mapOf("t.md" to generatedFile()), req, des)
            .shouldContainExactly("t.md: 対になる TRS ファイルが無い（生成物のファイルだけがある）")
    }

    "反例: 名前が _ で始まる生成物のファイルも、対になる TRS ファイルが無ければ違反になる（名前で検査から外さない）" {
        TrsSetCheck.violations(emptyMap(), mapOf("_x.md" to generatedFile()), req, des)
            .shouldContainExactly("_x.md: 対になる TRS ファイルが無い（生成物のファイルだけがある）")
    }

    "反例: ファイル名が違う TRS ファイルと生成物のファイルは、組にならずどちらも違反になる" {
        val errors = TrsSetCheck.violations(mapOf("a.md" to trsFile()), mapOf("b.md" to generatedFile()), req, des)
        errors.shouldContain("a.md: 対になる生成物のファイルが無い（TRS ファイルだけがある）")
        errors.shouldContain("b.md: 対になる TRS ファイルが無い（生成物のファイルだけがある）")
    }

    "反例: TRS ファイルに US の表の行があると違反になる" {
        val trs = trsFile() + "\n| ID | ストーリー | 出典（要件 ID） |\n|---|---|---|\n| US-01 | 説明 | REQ-TST-01 |\n"
        check(trs = trs).shouldContainExactly("t.md: TRS ファイルに US の表の行がある（先頭は行 ${lineOf(trs, "| US-01")}、計 1 行。US は生成物のファイルに置く）")
    }

    "反例: TRS ファイルに UC の節があると違反になる" {
        val trs = trsFile() + "\n#### UC-01: 例\n"
        check(trs = trs).shouldContainExactly("t.md: TRS ファイルに UC の節がある（先頭は行 ${lineOf(trs, "#### UC-01")}、計 1 節。UC は生成物のファイルに置く）")
    }

    "反例: TRS ファイルに AC の表の行があると違反になる（7 列の見出しの表も、先頭列が AC ID の別の表も）" {
        val sevenColumns = trsFile() + "\n$AC_HEADER\n$AC_SEPARATOR\n${acRow("AC-01-04", "REQ-TST-01")}\n"
        check(trs = sevenColumns).shouldContainExactly(
            "t.md: TRS ファイルに AC の表の行がある（先頭は行 ${lineOf(sevenColumns, "| AC-01-04")}、計 1 行。AC は生成物のファイルに置く）",
        )
        val other = trsFile() + "\n| AC ID | 備考 |\n|---|---|\n| AC-01-01 | 例 |\n"
        check(trs = other).shouldContainExactly(
            "t.md: TRS ファイルに AC の表の行がある（先頭は行 ${lineOf(other, "| AC-01-01 | 例")}、計 1 行。AC は生成物のファイルに置く）",
        )
    }

    "反例: 生成物のファイルに US の行が無いと違反になる" {
        check(gen = generatedFile(us = emptyList())).shouldContainExactly("t.md: US の行が 1 つも無い（先頭列が US-<数字> の表の行）")
    }

    "反例: 生成物のファイルに UC が無いと違反になる" {
        check(gen = generatedFile(uc = emptyList())).shouldContainExactly("t.md: UC が 1 つも無い（見出しが UC-<数字>: で始まる節）")
    }

    "反例: 生成物のファイルに AC の表の行が無いと違反になる" {
        val gen = generatedFile(acRows = emptyList(), tags = emptyList())
        val errors = check(gen = gen)
        errors.shouldHavePrefix("t.md: AC の表の行が 1 つも無い")
    }

    "反例: 生成物のファイルに Open Issues の表・因子・水準・禁止組合せの表・トレーサビリティ表があると違反になる（見出しの列名で見分ける）" {
        listOf(
            OI_HEADER to "Open Issues の表",
            "| 因子ID | 因子 | 水準ID | 水準 | 区分 | 備考 |" to "因子・水準の表",
            "| 水準ID | 水準 |" to "因子・水準の表",
            "| 因子ID | 因子 |" to "因子・水準の表",
            "| 制約ID | 組合せ | 理由 | 扱い |" to "禁止組合せの表",
            AcTable.traceabilityHeader.joinToString(" | ", "| ", " |") to "トレーサビリティ表",
        ).forEach { (header, kind) ->
            val columns = header.split("|").size - 2
            val gen = generatedFile() + "\n$header\n|${"---|".repeat(columns)}\n"
            check(gen = gen).shouldContainExactly("t.md: 生成物のファイルに $kind がある（行 ${lineOf(gen, header)}。TRS ファイルに置く）")
        }
    }

    "正例: 生成物のファイルの US の表・5.2 の表は、見出しに ID や制約ID を含んでも、混ぜてはならない表に当たらない" {
        GeneratedFileCheck.violations(generatedFile()).shouldBeEmpty()
    }

    // ---- 読み落としで AC が黙って外れない（#293 の自己テスト） ----

    "読み落とし: 生成物のファイルが無いと、TRS ファイルに AC が（トレーサビリティ表に）あっても素通りせず違反になる" {
        val errors = TrsSetCheck.violations(mapOf("t.md" to trsFile()), emptyMap(), req, des)
        errors.shouldHavePrefix("t.md: 対になる生成物のファイルが無い")
        errors.isNotEmpty() shouldBe true
    }

    "読み落とし: AC がトレーサビリティ表（7）だけにあり、生成物のファイルの AC の表に無いと、素通りせず違反になる" {
        val gen = generatedFile(acRows = emptyList(), tags = emptyList())
        val errors = check(gen = gen)
        errors.shouldHavePrefix("t.md: AC の表の行が 1 つも無い")
        errors.shouldContain("t.md: トレーサビリティ表の AC-01-01 が、生成物のファイルの AC の表に無い（行 ${lineOf(trsFile(), "| AC-01-01")}）")
        TestNameCheck.untested(mapOf("t.md" to gen), "\"AC-01-01 説明\"").shouldBeEmpty() // 表に無い AC は ① では数えない。そのため上の違反が必要になる
    }

    "読み落とし: AC の表の見出しが 7 列の形でないと、AC が黙って外れず違反になる（列の名前の違い・列の欠け・順序の違い）" {
        listOf(
            AC_HEADER.replace("| AC ID |", "| AC |"),
            AC_HEADER.replace(" | 根拠 |", " |").replace(" | 関連 UC", ""),
            "| AC ID | テストレベル | 関連 UC | 検証層 | 優先度 | 検証する振る舞い | 根拠 |",
            AC_HEADER.replace("検証する振る舞い", "振る舞い"),
        ).forEach { header ->
            val gen = generatedFile().replace(AC_HEADER, header)
            val errors = check(gen = gen)
            errors.shouldHavePrefix("t.md: AC の表の見出しが読めない（行 ${lineOf(gen, header)}。")
            errors.shouldHavePrefix("t.md: AC の表の行が 1 つも無い")
        }
    }

    "反例: AC の表に先頭列が AC-xx-yy の形でない行があると違反になる" {
        listOf("AC-1", "**AC-01-04**", "AC-01-04〜05", "").forEach { first ->
            val gen = generatedFile(acRows = defaultAcRows + acRow(first, "REQ-TST-01"))
            check(gen = gen).shouldContainExactly("t.md: AC の表に先頭列が AC-xx-yy の形でない行がある（行 ${lineOf(gen, "| $first |")}）: $first")
        }
    }

    "反例: AC の表の行の列数が見出しと合わないと違反になる" {
        val gen = generatedFile(acRows = defaultAcRows + "| AC-01-04 | UC-01 | 単体 | 層1 | Must | 説明 |")
        check(gen = gen).shouldContainExactly("t.md: AC の表の列数が見出しと合わない（行 ${lineOf(gen, "| AC-01-04")}）")
    }

    "反例: AC の表に同じ AC ID が複数の行にあると違反になる" {
        check(gen = generatedFile(acRows = defaultAcRows + acRow("AC-01-01", "REQ-TST-01"))).shouldContainExactly("t.md: AC の表に同じ ID が複数の行にある: AC-01-01")
    }

    "反例: Gherkin に @AC-xx-yy があるのに、AC の表に行が無い AC は違反になる（流れの AC も表に 1 行置く）" {
        check(gen = generatedFile(tags = listOf("@AC-01-03 @layer:2 @e2e:happy-path", "@AC-01-09 @layer:2 @e2e:happy-path")))
            .shouldContainExactly("t.md: Gherkin の @AC-01-09 が、AC の表に無い")
    }

    "反例: 同じ AC ID が複数の TRS の AC の表にあると違反になる（④）" {
        val errors = TrsSetCheck.violations(
            mapOf("a.md" to trsFile(), "b.md" to trsFile()),
            mapOf("a.md" to generatedFile(), "b.md" to generatedFile()),
            req,
            des,
        )
        errors.shouldContainExactly(
            "AC ID AC-01-01 が複数の TRS にある（a.md, b.md）",
            "AC ID AC-01-02 が複数の TRS にある（a.md, b.md）",
            "AC ID AC-01-03 が複数の TRS にある（a.md, b.md）",
        )
    }

    "正例: 同じ AC ID が、同じ TRS のトレーサビリティ表（7）と AC の表にあっても、重複とは数えない" {
        check().shouldBeEmpty()
    }

    // ---- トレーサビリティ表（7）と Open Issues の関連列の AC ID の照合 ----

    "反例: トレーサビリティ表（7）の AC ID が、AC の表に無いと違反になる（行の状態は問わない）" {
        val trs = trsFile(trace = listOf("AC-01-01", "AC-01-02", "AC-01-03", "AC-01-09"))
        check(trs = trs).shouldContainExactly("t.md: トレーサビリティ表の AC-01-09 が、生成物のファイルの AC の表に無い（行 ${lineOf(trs, "| AC-01-09")}）")
    }

    "反例: トレーサビリティ表（7）の AC ID の範囲表記を読めないと違反になる" {
        listOf("AC-01-01〜", "AC-01-03〜01", "AC-01-01 〜 02").forEach { first ->
            val trs = trsFile(trace = listOf(first, "AC-01-02", "AC-01-03"))
            check(trs = trs).shouldContainExactly("t.md: トレーサビリティ表の AC ID の範囲表記を読めない（行 ${lineOf(trs, "| $first")}）: $first")
        }
    }

    "反例: トレーサビリティ表（7）の行の列数が見出しと合わない、先頭列に AC ID が無いと違反になる" {
        val short = trsFile() + "| AC-01-03 | `T::AC-01-03` |\n"
        check(trs = short).shouldContainExactly("t.md: トレーサビリティ表の列数が見出しと合わない（行 ${short.lines().indexOfFirst { it == "| AC-01-03 | `T::AC-01-03` |" } + 1}）")
        listOf("AC-1", "—", "").forEach { first ->
            val trs = trsFile(trace = listOf("AC-01-01", "AC-01-02", "AC-01-03", first))
            check(trs = trs).shouldContainExactly("t.md: トレーサビリティ表の先頭列に AC ID が無い（行 ${lineOf(trs, "| $first |")}）: $first")
        }
    }

    "正例: トレーサビリティ表（7）の範囲表記は展開して照合する" {
        check(trs = trsFile(trace = listOf("AC-01-01〜03"))).shouldBeEmpty()
        check(trs = trsFile(trace = listOf("AC-01-01〜AC-01-02", "AC-01-03"))).shouldBeEmpty()
        val trs = trsFile(trace = listOf("AC-01-02〜04"))
        check(trs = trs).shouldContainExactly("t.md: トレーサビリティ表の AC-01-04 が、生成物のファイルの AC の表に無い（行 ${lineOf(trs, "| AC-01-02〜04")}）")
    }

    "反例: Open Issues の関連列の AC ID が AC の表に無いと違反になる（区分と状態によらず）" {
        listOf("Blocking" to "Resolved", "Non-blocking" to "Resolved", "Non-blocking" to "Open", "Non-blocking" to "In Discussion", "Blocking" to "Superseded（OI-001）", "Non-blocking" to "Won't Fix（理由）")
            .forEach { (kind, state) ->
                val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-002", kind, "AC-01-09", state, ""))
                check(trs = trs).shouldContainExactly("t.md: OI-002: 関連列の AC-01-09 が AC 表に無い")
            }
    }

    "反例: Open Issues の関連列の範囲表記を読めない、または逆順だと、区分と状態によらず違反になる" {
        listOf("AC-01-01〜", "AC-01-01 〜 02").forEach { related ->
            check(trs = trsFile(oiRows = defaultOiRows + oiRow("OI-002", "Non-blocking", related, "Resolved", "")))
                .shouldContainExactly("t.md: OI-002: 関連列の範囲表記を読めない（AC-xx-yy〜zz または AC-xx-yy〜AC-xx-zz の形にする）: $related")
        }
        check(trs = trsFile(oiRows = defaultOiRows + oiRow("OI-002", "Non-blocking", "AC-01-03〜01", "Resolved", "")))
            .shouldContainExactly("t.md: OI-002: 関連列の範囲が逆順: AC-01-03〜01")
    }

    "正例: 未解決でない Open Issue の反映先に AC-xx-yy 削除 と書いた AC は、AC の表になくても照合から外れる（トレーサビリティ表と関連列の両方）" {
        listOf("Resolved", "Superseded（OI-001）", "Won't Fix（理由）").forEach { state ->
            listOf("AC-01-09 削除", "C-10 追加, AC-01-09 削除", "AC-01-09 削除, AC-01-08 削除").forEach { reflect ->
                val trs = trsFile(
                    oiRows = defaultOiRows + oiRow("OI-002", "Non-blocking", "AC-01-09, AC-01-08", state, reflect),
                    trace = listOf("AC-01-01", "AC-01-02", "AC-01-03", "AC-01-09"),
                )
                val expected = if ("AC-01-08 削除" in reflect) emptyList() else listOf("t.md: OI-002: 関連列の AC-01-08 が AC 表に無い")
                check(trs = trs).shouldContainExactly(expected)
            }
        }
    }

    "反例: 照合から外れるのは、未解決でない行の、AC-xx-yy 削除 の形に限る（未解決の行・範囲表記・書式の違い）" {
        listOf(
            "Open" to "AC-01-09 削除",
            "In Discussion" to "AC-01-09 削除",
            "Resolved" to "AC-01-09〜10 削除",
            "Resolved" to "AC-01-09削除",
            "Resolved" to "AC-01-09 を削除",
            "Resolved" to "削除",
        ).forEach { (state, reflect) ->
            val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-002", "Non-blocking", "AC-01-09", state, reflect))
            check(trs = trs).shouldContainExactly("t.md: OI-002: 関連列の AC-01-09 が AC 表に無い")
        }
    }

    "反例: 未解決でない行が AC-xx-yy 削除 と記録した AC が、AC の表に残っているとき、その AC を指す未解決の Blocking は @pending が無ければ違反になる（削除の記録は @pending の要否を外さない）" {
        val trs = trsFile(
            oiRows = defaultOiRows + oiRow("OI-002", "Non-blocking", "AC-01-01", "Resolved", "AC-01-03 削除") + oiRow("OI-005", "Blocking", "AC-01-03", "Open", ""),
        )
        check(trs = trs).shouldContainExactly("t.md: OI-005: 未解決の Blocking が指す AC-01-03 に @pending:OI-005 が無い")
        check(trs = trs, gen = generatedFile(tags = listOf("@AC-01-03 @layer:2 @pending:OI-005"))).shouldBeEmpty()
    }

    "反例: 未解決の Blocking の関連列が、削除を記録した AC だけを指していると違反になる（一部が表に残っていれば、残った AC を検査する）" {
        val resolved = oiRow("OI-002", "Non-blocking", "AC-01-01", "Resolved", "AC-01-09 削除")
        check(trs = trsFile(oiRows = defaultOiRows + resolved + oiRow("OI-005", "Blocking", "AC-01-09", "Open", ""))).shouldContainExactly(
            "t.md: OI-005: 未解決の Blocking の関連列の AC が、すべて照合から外れている（指す AC が AC の表に無い。削除の記録は、未解決の行の AC には使えない）",
        )
        val mixed = trsFile(oiRows = defaultOiRows + resolved + oiRow("OI-005", "Blocking", "AC-01-09, AC-01-03", "Open", ""))
        check(trs = mixed).shouldContainExactly("t.md: OI-005: 未解決の Blocking が指す AC-01-03 に @pending:OI-005 が無い")
        check(trs = mixed, gen = generatedFile(tags = listOf("@AC-01-03 @layer:2 @pending:OI-005"))).shouldBeEmpty()
    }

    "反例: @pending が TRS ファイルにだけあり、生成物のファイルに無いと、② の違反になる（@pending は生成物のファイルから読む）" {
        val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-005", "Blocking", "AC-01-02", "Open", "")) + "\n> @AC-01-02 @pending:OI-005\n"
        check(trs = trs).shouldContainExactly("t.md: OI-005: 未解決の Blocking が指す AC-01-02 に @pending:OI-005 が無い")
    }

    "反例: 未解決の Blocking があるのに、生成物のファイルに @pending が無いと違反になる（TRS ファイルと生成物のファイルで本文を分けて検査する）" {
        val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-005", "Blocking", "AC-01-03", "In Discussion", ""))
        check(trs = trs).shouldContainExactly("t.md: OI-005: 未解決の Blocking が指す AC-01-03 に @pending:OI-005 が無い")
        UnresolvedBlockingCheck.violations(trs, generatedFile()).shouldContainExactly("OI-005: 未解決の Blocking が指す AC-01-03 に @pending:OI-005 が無い")
    }

    "反例: Open Issues の表の見出しに反映先が無いと、TRS ファイル単位で違反になる" {
        val trs = trsFile().replace(OI_HEADER, OI_HEADER.replace(" | 反映先", ""))
        check(trs = trs).shouldHavePrefix("t.md: Open Issues 表の見出し（ID・区分・関連・状態・反映先）が見つからない")
    }

    // ---- 組合せ別の期待結果（5.2）の書式（03a §3） ----

    "反例: 期待結果に、ケースを生成しない文言があると違反になる（語は部分一致）" {
        listOf("テスト対象外", "テストの対象外", "テスト不要", "テストしない", "型で排除", "ケースを生成しない").forEach { word ->
            listOf(word, "${word}とする", "型で排除済み".takeIf { word == "型で排除" } ?: "（$word）扱い").forEach { cell ->
                check(gen = generatedFile(firstExpected = cell)).shouldContainExactly("t.md: C-10: 期待結果に、ケースを生成しない文言「$word」がある")
            }
        }
    }

    "反例: 期待結果に、期待値が未決の文言があると違反になる（語は部分一致）" {
        listOf(
            "未決" to listOf("未決"),
            "未決事項" to listOf("未決"),
            "未決定" to listOf("未決", "未決定"),
            "未定" to listOf("未定"),
            "決定後に書く" to listOf("決定後"),
            "決定待ち" to listOf("決定待ち"),
            "後で決める" to listOf("後で決める"),
            "要確認" to listOf("要確認"),
            "TBD" to listOf("TBD"),
        ).forEach { (cell, words) ->
            check(gen = generatedFile(firstExpected = cell)).shouldContainExactly(words.map { "t.md: C-10: 期待結果に、期待値が未決の文言「$it」がある" })
        }
    }

    "正例: 例外の語（未決済・未定義）の一部として現れる語は、期待値が未決の文言に当たらない" {
        check(gen = generatedFile(firstExpected = "未決済の注文は変わらない")).shouldBeEmpty()
        check(gen = generatedFile(firstExpected = "未定義の入力は、拒否される")).shouldBeEmpty()
        check(gen = generatedFile(firstExpected = "未決済の注文は変わらない。未決定ではない")).shouldContainExactly(
            "t.md: C-10: 期待結果に、期待値が未決の文言「未決」がある",
            "t.md: C-10: 期待結果に、期待値が未決の文言「未決定」がある",
        )
    }

    "反例: 期待結果が空、—、- だけだと違反になる" {
        listOf("", "—", "-", " — ").forEach { cell ->
            check(gen = generatedFile(firstExpected = cell)).shouldContainExactly("t.md: C-10: 期待結果に、具体的な期待結果が無い（空、—、-）")
        }
    }

    "反例: 期待結果の全体が丸括弧で囲まれていると違反になる（全角・半角・入れ子・「」と バッククォート の中の括弧は除いて判定する）" {
        listOf("（後述）", "(後述)", "（A（B）を後述）", "（「）」の扱い）", "（`)` の扱い）", " （後述） ").forEach { cell ->
            check(gen = generatedFile(firstExpected = cell)).shouldContainExactly("t.md: C-10: 期待結果に、具体的な期待結果が無い（全体が丸括弧）")
        }
    }

    "正例: 期待結果の一部だけが括弧のとき、または括弧が別々のとき、全体が丸括弧とは数えない" {
        listOf("成功する（注文は変わらない）", "（A）と（B）", "（注）成功する", "「（」を含む（後述）", "`(` を含む").forEach { cell ->
            check(gen = generatedFile(firstExpected = cell)).shouldBeEmpty()
        }
    }

    "反例: 期待結果に OI-数字 を書くと違反になる（OI への言及は根拠の列に書く）" {
        check(gen = generatedFile(firstExpected = "OI-001 の決定に従う")).shouldContainExactly("t.md: C-10: 期待結果に、OI（OI-001）を書いている。OI への言及は根拠の列に書く")
    }

    "反例: 根拠の列に、未解決の Blocking な OI-ID があると違反になる（Open・In Discussion）" {
        listOf("Open", "In Discussion").forEach { state ->
            val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-005", "Blocking", "AC-01-03", state, ""))
            val gen = generatedFile(tags = listOf("@AC-01-03 @layer:2 @pending:OI-005"), secondBasis = "REQ-TST-02, OI-005")
            check(trs = trs, gen = gen).shouldContainExactly(
                "t.md: C-11: 根拠が未解決の Blocking な OI-005 に依存している（期待値が未決の組合せは、行を置かず @pending の AC で表す）",
            )
        }
    }

    "正例: 根拠の列の OI-ID が、解決済みの Blocking・未解決の Non-blocking・解決済みの Non-blocking なら合格する" {
        listOf("Blocking" to "Resolved", "Non-blocking" to "Open", "Non-blocking" to "Resolved", "Blocking" to "Superseded（OI-001）").forEach { (kind, state) ->
            val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-005", kind, "AC-01-03", state, ""))
            check(trs = trs, gen = generatedFile(secondBasis = "REQ-TST-02, OI-005")).shouldBeEmpty()
        }
    }

    "反例: 組合せ別の期待結果の表の見出しが規定と違うと違反になる（読み落としで期待結果の検査が外れない）" {
        val renamed = EXPECTATION_HEADER.replace("根拠（要件 ID / 設計 ID / OI）", "根拠")
        val gen = generatedFile().replace(EXPECTATION_HEADER, renamed)
        val errors = check(gen = gen)
        errors.shouldHavePrefix("t.md: 組合せ別の期待結果の表の見出しが読めない（行 ${lineOf(gen, renamed)}。")
        errors.shouldHavePrefix("t.md: 組合せ別の期待結果（5.2）の表が見つからない")
    }

    "反例: 組合せ別の期待結果の表が無い生成物のファイルは違反になる" {
        val gen = generatedFile().substringBefore("#### 5.2")
        check(gen = gen).shouldHavePrefix("t.md: 組合せ別の期待結果（5.2）の表が見つからない")
    }

    "反例: 組合せ別の期待結果の表の行の列数が見出しと合わない、先頭列が C-xx の形でないと違反になる" {
        val short = generatedFile() + "| C-12 | 条件 | 期待結果 |\n"
        check(gen = short).shouldContainExactly("t.md: 組合せ別の期待結果の表の列数が見出しと合わない（行 ${lineOf(short, "| C-12")}）")
        val badId = generatedFile() + "| X-12 | 条件 | 成功する | REQ-TST-01 |\n"
        check(gen = badId).shouldContainExactly("t.md: 組合せ別の期待結果の表に先頭列が C-xx の形でない行がある（行 ${lineOf(badId, "| X-12")}）: X-12")
    }

    // ---- 根拠の実在（#293） ----

    "反例: AC の表の根拠の列が空欄だと違反になる（空の項目も）" {
        listOf("", "C-10,", "C-10, , REQ-TST-01").forEach { basis ->
            check(gen = generatedFile(firstBasis = basis)).shouldContainExactly("t.md: AC-01-01: 根拠が空欄")
        }
    }

    "反例: AC の表の根拠の C-xx が、5.2 に無いと違反になる" {
        check(gen = generatedFile(firstBasis = "C-99, REQ-TST-01")).shouldContainExactly("t.md: AC-01-01: 根拠 C-99 は組合せ別の期待結果（5.2）に無い制約 ID")
    }

    "反例: AC の表の根拠の要件 ID が、要件定義書に無いと違反になる" {
        check(gen = generatedFile(firstBasis = "C-10, REQ-TST-09")).shouldContainExactly("t.md: AC-01-01: 根拠 REQ-TST-09 は要件定義書に無い要件 ID")
    }

    "反例: AC の表の根拠の設計 ID が、基本設計書の節 2〜9 に無いと違反になる（無い ID・節 1 の ID・節 10 の ID）" {
        check(gen = generatedFile(firstBasis = "C-10, DES-TST-99")).shouldContainExactly("t.md: AC-01-01: 根拠 DES-TST-99 は基本設計書の節 2〜9 に無い設計 ID")
        // 節 1・節 10 の設計 ID の形は、設計 ID に数えない（根拠から指すと実在しない ID になる）。節の外に書いたこと自体も違反になる
        val outOfRange = designDoc(outOfRange = true)
        val outside = listOf(
            "design.md: 設計 ID の形の文字列が、節 2〜9 の表の外にある（行 ${lineOf(outOfRange, "| DES-TST-90")}）: DES-TST-90",
            "design.md: 設計 ID の形の文字列が、節 2〜9 の表の外にある（行 ${lineOf(outOfRange, "| DES-TST-91")}）: DES-TST-91",
        )
        listOf("DES-TST-90", "DES-TST-91").forEach { id ->
            check(gen = generatedFile(firstBasis = "C-10, $id"), designs = mapOf("design.md" to outOfRange))
                .shouldContainExactly(outside + "t.md: AC-01-01: 根拠 $id は基本設計書の節 2〜9 に無い設計 ID")
        }
        // 基本設計書が無いと、設計 ID はすべて実在しない。AC の表の 2 件と 5.2 の 2 件を、行ごとに報告する
        check(gen = generatedFile(firstBasis = "C-10, DES-TST-01"), designs = emptyMap()).shouldContainExactly(
            "t.md: AC-01-01: 根拠 DES-TST-01 は基本設計書の節 2〜9 に無い設計 ID",
            "t.md: AC-01-02: 根拠 DES-TST-02 は基本設計書の節 2〜9 に無い設計 ID",
            "t.md: C-10: 根拠 DES-TST-01 は基本設計書の節 2〜9 に無い設計 ID",
            "t.md: C-11: 根拠 DES-TST-02 は基本設計書の節 2〜9 に無い設計 ID",
        )
    }

    "⑨ 反例: 組合せ別の期待結果（5.2）の根拠の設計 ID が、基本設計書の節 2〜9 に無いと違反になる" {
        check(gen = generatedFile(secondBasis = "REQ-TST-02, DES-TST-02, DES-TST-99"))
            .shouldContainExactly("t.md: C-11: 根拠 DES-TST-99 は基本設計書の節 2〜9 に無い設計 ID")
    }

    "⑨ 反例: 5.2 の根拠が空欄だと違反になる（空の項目も）" {
        listOf("", "REQ-TST-02,", "REQ-TST-02, , DES-TST-02").forEach { basis ->
            check(gen = generatedFile(secondBasis = basis)).shouldContainExactly("t.md: C-11: 根拠が空欄")
        }
    }

    "⑨ 反例: 5.2 の根拠に C-xx を書くと違反になる（制約 ID は AC の表にだけ書ける。実在する行・自分の行・実在しない行のどれも）" {
        listOf("C-10", "C-11", "C-99").forEach { id ->
            check(gen = generatedFile(secondBasis = "REQ-TST-02, $id"))
                .shouldContainExactly("t.md: C-11: 根拠 $id は制約 ID である。組合せ別の期待結果の根拠には、制約 ID を書けない")
        }
    }

    "⑨ 反例: 5.2 の根拠の要件 ID が、要件定義書に無いと違反になる" {
        check(gen = generatedFile(secondBasis = "REQ-TST-09, DES-TST-02")).shouldContainExactly("t.md: C-11: 根拠 REQ-TST-09 は要件定義書に無い要件 ID")
    }

    "⑨ 反例: 5.2 の根拠の OI-ID が、組になる TRS ファイルの Open Issues 表に無いと違反になる" {
        check(gen = generatedFile(secondBasis = "REQ-TST-02, OI-099")).shouldContainExactly("t.md: C-11: 根拠 OI-099 は Open Issues 表に無い OI-ID")
    }

    "⑨ 反例: 5.2 の根拠が、どの種類の形にも当たらないと違反になる（範囲・装飾・全角の区切り・AC ID・—）" {
        listOf("C-10〜C-11", "`REQ-TST-02`", "REQ-TST-02、DES-TST-02", "AC-01-02", "—", "req-tst-02").forEach { value ->
            check(gen = generatedFile(secondBasis = value))
                .shouldContainExactly("t.md: C-11: 根拠 $value は C-xx・要件 ID・設計 ID・OI-ID のどれの形でもない（区切りは半角のカンマ）")
        }
    }

    "⑨ 反例: 根拠の値が DES- で始まるのに設計 ID の書式でないと違反になる（AC の表も 5.2 も。綴りの誤りが実在の検査をすり抜けない）" {
        val malformed = listOf("DES-", "DES-TST-1", "DES-tst-01", "DES-T-01", "DES-TOOLONGX-01", "DES-TST-01-02", "DES-TST01", "DES-TST-x1")
        malformed.forEach { value ->
            val message = "根拠 $value は DES- で始まるが、設計 ID の書式（DES-<略号>-<番号>。略号は英大文字 2〜6、番号は 2 桁以上）でない"
            check(gen = generatedFile(firstBasis = "C-10, $value")).shouldContainExactly("t.md: AC-01-01: $message")
            check(gen = generatedFile(secondBasis = "REQ-TST-02, DES-TST-02, $value")).shouldContainExactly("t.md: C-11: $message")
        }
    }

    "⑨ 正例: 5.2 の根拠に、要件 ID・設計 ID・OI-ID を並べても、すべて実在すれば合格する" {
        check(gen = generatedFile(secondBasis = "REQ-TST-02, DES-TST-02, OI-001")).shouldBeEmpty()
    }

    "⑨ 反例: 同じ設計 ID が、同じ基本設計書の複数の行にあると違反になる" {
        val doc = designDoc().replace("| DES-TST-02 | まだ無い", "| DES-TST-01 | まだ無い")
        check(designs = mapOf("design.md" to doc)).shouldContain(
            "設計 ID DES-TST-01 が複数の行にある（design.md:${lineOf(doc, "| DES-TST-01 | 登録")}, design.md:${lineOf(doc, "| DES-TST-01 | まだ無い")}）",
        )
    }

    "⑨ 反例: 同じ設計 ID が、別の基本設計書の間で複数の行にあると違反になる" {
        val other = designDoc()
        check(designs = mapOf("design.md" to designDoc(), "other.md" to other)).shouldContain(
            "設計 ID DES-TST-01 が複数の行にある（design.md:${lineOf(other, "| DES-TST-01")}, other.md:${lineOf(other, "| DES-TST-01")}）",
        )
    }

    "⑨ 書式の反例: 節 2〜9 の表の先頭列が、設計 ID の形でないと違反になる（節の番号・小見出しの表・見出し行と区切り行は除く）" {
        val doc = designDoc().replace("| DES-TST-02 | まだ無い", "| 状態 2 | まだ無い")
        DesignIds.violations(mapOf("design.md" to doc)).shouldContain("design.md: 節 2〜9 の表で先頭列が設計 ID の形でない行がある（行 ${lineOf(doc, "| 状態 2")}）: 状態 2")
        DesignIds.violations(mapOf("design.md" to designDoc())).shouldBeEmpty()
    }

    "⑨ 書式の反例: 設計 ID の形の文字列が、節 2〜9 の表の外（節 1・節 10・メタ情報・地の文・コードブロック・コメント）にあると違反になる" {
        val outside = listOf("節 1" to "| DES-TST-90 | 全体 |", "地の文" to "DES-TST-92 を参照する。", "メタ情報" to "| 対応 | DES-TST-93 |")
        outside.forEach { (place, line) ->
            val doc = designDoc().replace("## 2. 画面", "$line\n\n## 2. 画面")
            val id = Regex("""DES-TST-9\d""").find(line)!!.value
            withClue(place) {
                DesignIds.violations(mapOf("d.md" to doc)).shouldContain("d.md: 設計 ID の形の文字列が、節 2〜9 の表の外にある（行 ${lineOf(doc, line)}）: $id")
            }
        }
        val section10 = designDoc(outOfRange = true)
        DesignIds.violations(mapOf("d.md" to section10)).shouldContain("d.md: 設計 ID の形の文字列が、節 2〜9 の表の外にある（行 ${lineOf(section10, "| DES-TST-91")}）: DES-TST-91")
        val fenced = designDoc().replace("## 5. 状態と操作", "```text\n| DES-TST-94 | 例 |\n```\n\n## 5. 状態と操作")
        DesignIds.violations(mapOf("d.md" to fenced)).shouldContain("d.md: 設計 ID の形の文字列が、節 2〜9 の表の外にある（行 ${lineOf(fenced, "| DES-TST-94")}）: DES-TST-94")
        val commented = designDoc().replace("## 5. 状態と操作", "<!-- DES-TST-95 -->\n\n## 5. 状態と操作")
        DesignIds.violations(mapOf("d.md" to commented)).shouldContain("d.md: 設計 ID の形の文字列が、節 2〜9 の表の外にある（行 ${lineOf(commented, "<!-- DES-TST-95")}）: DES-TST-95")
    }

    "⑨ 書式の正例: 節 2〜9 の表の先頭列以外にある設計 ID の形の文字列は、定義に数えず、違反にもしない" {
        val doc = designDoc().replace("| DES-TST-01 | 登録 | 登録する |", "| DES-TST-01 | 登録 | DES-TST-02 の後に登録する |")
        DesignIds.violations(mapOf("d.md" to doc)).shouldBeEmpty()
        DesignIds.parse(doc).items.map { it.id } shouldBe listOf("DES-TST-01", "DES-TST-02")
    }

    "⑨ 書式の反例: 節 2〜9 の表の見出し行に設計 ID の形の文字列があると違反になる" {
        val doc = designDoc().replace("| ID | 画面 | 表示と操作 |", "| DES-TST-96 | 画面 | 表示と操作 |")
        DesignIds.violations(mapOf("d.md" to doc)).shouldContain("d.md: 節 2〜9 の表の見出し行に設計 ID の形の文字列がある（行 ${lineOf(doc, "| DES-TST-96")}）: DES-TST-96")
    }

    "⑨ 書式の反例: 基本設計書があるのに設計 ID が 1 件も読めないと違反になる（表が無い・節の番号が無い・節 1 と節 10 だけ）" {
        val none = "# 基本設計書\n\n## 2. 画面\n\n該当なし\n\n## 5. 状態と操作\n\n該当なし\n"
        val unnumbered = "# 基本設計書\n\n## 画面\n\n| ID | 画面 |\n|---|---|\n| X-01 | 登録 |\n"
        listOf(none, unnumbered, "").forEach {
            DesignIds.violations(mapOf("d.md" to it)).shouldContain("d.md: 設計 ID が 1 件も読めない（節 2〜9 の表に、先頭列が設計 ID の行を置く）")
        }
        check(designs = mapOf("design.md" to none)).shouldContain("design.md: 設計 ID が 1 件も読めない（節 2〜9 の表に、先頭列が設計 ID の行を置く）")
    }

    "⑧ 反例: 基本設計書の設計 ID が、どの TRS の根拠にも現れないと違反になる" {
        val doc = designDoc()
        check(gen = generatedFile(firstBasis = "C-10, REQ-TST-01", secondBasis = "REQ-TST-02, OI-001", acRows = listOf(acRow("AC-01-01", "C-10, REQ-TST-01"), acRow("AC-01-02", "C-11, REQ-TST-02, OI-001"), acRow("AC-01-03", "REQ-TST-03")), firstRowBasis = "REQ-TST-01"))
            .shouldContainExactly(
                "design.md: 設計 ID DES-TST-01 が、どの TRS の根拠にも現れない（行 ${lineOf(doc, "| DES-TST-01")}）",
                "design.md: 設計 ID DES-TST-02 が、どの TRS の根拠にも現れない（行 ${lineOf(doc, "| DES-TST-02")}）",
            )
    }

    "⑧ 正例: 設計 ID が、AC の表の根拠だけ、または 5.2 の根拠だけに現れても、現れたとみなす" {
        // DES-TST-02 は AC の表だけ（5.2 の C-11 には書かない）、DES-TST-01 は 5.2 だけ（AC の表には書かない）
        val gen = generatedFile(
            firstBasis = "C-10, REQ-TST-01",
            acRows = listOf(acRow("AC-01-01", "C-10, REQ-TST-01"), acRow("AC-01-02", "C-11, REQ-TST-02, DES-TST-02, OI-001"), acRow("AC-01-03", "REQ-TST-03")),
            secondBasis = "REQ-TST-02, OI-001",
        )
        check(gen = gen).shouldBeEmpty()
    }

    "⑧ 正例: @pending を付けた AC の根拠の設計 ID も、現れたとみなす" {
        val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-005", "Blocking", "AC-01-03", "Open", ""))
        val gen = generatedFile(
            acRows = listOf(acRow("AC-01-01", "C-10, REQ-TST-01, DES-TST-01"), acRow("AC-01-02", "C-11, REQ-TST-02, OI-001"), acRow("AC-01-03", "REQ-TST-03, DES-TST-02")),
            tags = listOf("@AC-01-03 @layer:2 @pending:OI-005"),
            secondBasis = "REQ-TST-02, OI-001",
        )
        check(trs = trs, gen = gen).shouldBeEmpty()
    }

    "⑧ 正例: 設計 ID が複数の TRS に分かれて現れても、全体で 1 回ずつ現れていればよい（全 TRS の根拠を合わせて照合する）" {
        val first = generatedFile(acRows = listOf(acRow("AC-01-01", "C-10, REQ-TST-01, DES-TST-01"), acRow("AC-01-02", "C-11, REQ-TST-02, OI-001"), acRow("AC-01-03", "REQ-TST-03")), secondBasis = "REQ-TST-02, OI-001")
        val second = generatedFile(acRows = listOf(acRow("AC-02-01", "C-10, REQ-TST-01, DES-TST-02")), tags = emptyList(), firstRowBasis = "REQ-TST-01", secondBasis = "REQ-TST-02")
        val secondTrs = trsFile(oiRows = listOf(oiRow("OI-001", "Blocking", "AC-02-01", "Resolved", "AC-02-01 追加")), trace = listOf("AC-02-01"))
        TrsSetCheck.violations(mapOf("t.md" to trsFile(), "o.md" to secondTrs), mapOf("t.md" to first, "o.md" to second), req, des).shouldBeEmpty()
        TrsSetCheck.violations(mapOf("t.md" to trsFile()), mapOf("t.md" to first), req, des)
            .shouldContainExactly("design.md: 設計 ID DES-TST-02 が、どの TRS の根拠にも現れない（行 ${lineOf(designDoc(), "| DES-TST-02")}）")
    }

    "⑨ 反例: @pending の AC の根拠に、実在しない値があっても違反になる（pending でも根拠は調べる）" {
        val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-005", "Blocking", "AC-01-03", "Open", ""))
        val gen = generatedFile(
            acRows = defaultAcRows.dropLast(1) + acRow("AC-01-03", "REQ-TST-99"),
            tags = listOf("@AC-01-03 @layer:2 @pending:OI-005"),
        )
        check(trs = trs, gen = gen).shouldContainExactly("t.md: AC-01-03: 根拠 REQ-TST-99 は要件定義書に無い要件 ID")
    }

    "反例: AC の表の根拠の OI-ID が、組になる TRS ファイルの Open Issues 表に無いと違反になる" {
        check(gen = generatedFile(firstBasis = "C-10, OI-099")).shouldContainExactly("t.md: AC-01-01: 根拠 OI-099 は Open Issues 表に無い OI-ID")
    }

    "反例: AC の表の根拠が、どの種類の ID の形でもないと違反になる（範囲・装飾・全角の区切り・AC ID・US・UC）" {
        listOf("C-10〜C-11", "`C-10`", "C-10、REQ-TST-01", "AC-01-02", "UC-01", "US-01", "要件定義書 3 章", "—", "req-tst-01").forEach { value ->
            check(gen = generatedFile(firstBasis = value))
                .shouldContainExactly("t.md: AC-01-01: 根拠 $value は C-xx・要件 ID・設計 ID・OI-ID のどれの形でもない（区切りは半角のカンマ）")
        }
    }

    "正例: AC の表の根拠が複数の種類にまたがっても、すべて実在すれば合格する（未解決の Blocking な OI は AC の表の根拠に書ける）" {
        val trs = trsFile(oiRows = defaultOiRows + oiRow("OI-005", "Blocking", "AC-01-03", "Open", ""))
        check(trs = trs, gen = generatedFile(firstBasis = "C-10, C-11, REQ-TST-01, DES-TST-01, DES-TST-02, OI-005", tags = listOf("@AC-01-03 @layer:2 @pending:OI-005"))).shouldBeEmpty()
    }

    // ---- ① AC に対応するテストの検査 と ⑦ テスト名の AC ID の実在 ----

    "① 反例: pending でない AC に、先頭に AC ID を持つテストが無いと違反になる" {
        val docs = mapOf("t.md" to generatedFile())
        TestNameCheck.untested(docs, "\"AC-01-01 説明\" { }\n`AC-01-02 AC-01-03 説明`").shouldBeEmpty()
        TestNameCheck.untested(docs, "\"AC-01-01 説明\" { }").shouldContainExactly("t.md: AC-01-02 にテストが無い", "t.md: AC-01-03 にテストが無い")
        TestNameCheck.untested(docs, "\"説明 AC-01-01\" { }\n// AC-01-02\n\"AC-01-0\"").shouldContainExactly(
            "t.md: AC-01-01 にテストが無い",
            "t.md: AC-01-02 にテストが無い",
            "t.md: AC-01-03 にテストが無い",
        )
    }

    "① 正例: @pending を付けた AC は、テストが無くても違反にならない" {
        val gen = generatedFile(tags = listOf("@AC-01-03 @layer:2 @pending:OI-005"))
        TestNameCheck.untested(mapOf("t.md" to gen), "\"AC-01-01 AC-01-02 説明\" { }").shouldBeEmpty()
    }

    "① 反例: AC の表を読めない（見出しが違う）生成物のファイルは、AC が 0 件になるので、AC の表の検査が違反にする" {
        val gen = generatedFile().replace(AC_HEADER, AC_HEADER.replace("AC ID", "AC"))
        TestNameCheck.untested(mapOf("t.md" to gen), "").shouldBeEmpty()
        check(gen = gen).shouldHavePrefix("t.md: AC の表の行が 1 つも無い")
    }

    "⑦ 反例: テスト名の先頭の AC ID が、どの TRS の AC の表にも無いと違反になる" {
        val docs = mapOf("t.md" to generatedFile())
        TestNameCheck.unknown(docs, "\"AC-01-01 説明\" { }\n\"AC-09-99 説明\" { }").shouldContainExactly("テスト名の先頭の AC-09-99 が、どの TRS の AC の表にも無い")
        TestNameCheck.unknown(docs, "`AC-09-99 説明`").shouldContainExactly("テスト名の先頭の AC-09-99 が、どの TRS の AC の表にも無い")
    }

    "⑦ 反例: 空白区切りで並ぶ AC ID は、すべて調べる" {
        TestNameCheck.unknown(mapOf("t.md" to generatedFile()), "\"AC-01-01 AC-09-98 AC-01-02 AC-09-97 説明\" { }")
            .shouldContainExactly("テスト名の先頭の AC-09-97 が、どの TRS の AC の表にも無い", "テスト名の先頭の AC-09-98 が、どの TRS の AC の表にも無い")
    }

    "⑦ 正例: 名前の先頭が AC-xx-yy の形でない文字列は対象外で、別の TRS の AC の表にある AC ID は実在とみなす" {
        val other = generatedFile(acRows = listOf(acRow("AC-02-01", "REQ-TST-01")), tags = emptyList())
        TestNameCheck.unknown(mapOf("t.md" to generatedFile(), "o.md" to other), "\"AC-02-01 説明\" { }\n\"説明 AC-09-99\" { }\n\"AC-09 説明\" { }").shouldBeEmpty()
    }

    "⑦ 反例: AC の表を読めない生成物のファイルだけがあると、テスト名の AC ID はすべて実在しないとみなされ違反になる（読み落としで素通りしない）" {
        val gen = generatedFile().replace(AC_HEADER, AC_HEADER.replace("AC ID", "AC"))
        TestNameCheck.unknown(mapOf("t.md" to gen), "\"AC-01-01 説明\" { }").shouldContainExactly("テスト名の先頭の AC-01-01 が、どの TRS の AC の表にも無い")
    }

    // ---- 部品の単体 ----

    "AC ID の範囲表記の展開: AC-xx-yy〜zz と AC-xx-yy〜AC-xx-zz を展開し、読めない区切りと逆順を問題にする" {
        AcRefs.expand("AC-01-01〜03, AC-02-05").ids.shouldContainExactly("AC-01-01", "AC-01-02", "AC-01-03", "AC-02-05")
        AcRefs.expand("AC-01-01〜AC-01-02").ids.shouldContainExactly("AC-01-01", "AC-01-02")
        AcRefs.expand("AC-01-01〜AC-02-02").problem shouldBe AcRefs.Problem.UNREADABLE
        AcRefs.expand("AC-01-03〜01").problem shouldBe AcRefs.Problem.REVERSED
        AcRefs.expand("F1-L2").ids.shouldBeEmpty()
    }

    "設計 ID は、基本設計書の節 2〜9 の表の先頭列だけから読む（節 1 と節 10 の設計 ID の形は数えない）" {
        DesignIds.read(designDoc()) shouldBe setOf("DES-TST-01", "DES-TST-02")
        DesignIds.read(designDoc(outOfRange = true)) shouldBe setOf("DES-TST-01", "DES-TST-02")
    }
})

private const val AC_HEADER = "| AC ID | 関連 UC | テストレベル | 検証層 | 優先度 | 検証する振る舞い | 根拠 |"
private const val AC_SEPARATOR = "|---|---|---|---|---|---|---|"
private const val OI_HEADER = "| ID | 起票日 | 起票者 | 区分 | 関連 | 内容 | 状態 | 決定内容 | 決定者 | 決定日 | 根拠 | 反映先 |"
private const val EXPECTATION_HEADER = "| 制約ID | 条件 | 期待結果 | 根拠（要件 ID / 設計 ID / OI） |"

private fun lineOf(text: String, prefix: String) = text.lines().indexOfFirst { it.startsWith(prefix) } + 1

private fun acRow(id: String, basis: String) = "| $id | UC-01 | 単体 | 層1 | Must | 説明 | $basis |"

private fun oiRow(id: String, kind: String, related: String, state: String, reflect: String) =
    "| $id | 2026-11-01 | AI | $kind | $related | 論点 | $state | 決定 | オーナー | 2026-11-02 | 決定 | $reflect |"

private val defaultOiRows = listOf(oiRow("OI-001", "Blocking", "AC-01-02", "Resolved", "AC-01-02 追加"))

private val defaultAcRows = listOf(
    acRow("AC-01-01", "C-10, REQ-TST-01, DES-TST-01"),
    acRow("AC-01-02", "C-11, REQ-TST-02, DES-TST-02, OI-001"),
    acRow("AC-01-03", "REQ-TST-03"),
)

/** TRS ファイルの最小の例。Open Issues 表（6）とトレーサビリティ表（7）だけを持つ。US・UC・AC の表は生成物のファイルにある */
private fun trsFile(oiRows: List<String> = defaultOiRows, trace: List<String> = listOf("AC-01-01", "AC-01-02", "AC-01-03")): String = buildString {
    appendLine("# TRS")
    appendLine()
    appendLine("### 6. Open Issues")
    appendLine()
    appendLine(OI_HEADER)
    appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
    oiRows.forEach { appendLine(it) }
    appendLine()
    appendLine("### 7. トレーサビリティ")
    appendLine()
    appendLine(AcTable.traceabilityHeader.joinToString(" | ", "| ", " |"))
    appendLine("|---|---|---|---|---|")
    trace.forEach { appendLine("| $it | `T::$it` | — | — | 実装済 |") }
}

/**
 * 生成物のファイルの最小の例。US・UC・AC の表・流れの Gherkin・5.2 を持つ。5.2 の最初の行（C-10）の期待結果は [firstExpected]、
 * 1 行目（C-10）の根拠は [firstRowBasis]、2 行目（C-11）の根拠は [secondBasis]、AC の表の最初の行の根拠は [firstBasis] で変えられる（[acRows] を指定したときは [firstBasis] は使わない）
 */
private fun generatedFile(
    us: List<String> = listOf("REQ-TST-01, REQ-TST-02, REQ-TST-03"),
    uc: List<String> = listOf("REQ-TST-01, REQ-TST-02, REQ-TST-03"),
    firstBasis: String = "C-10, REQ-TST-01, DES-TST-01",
    acRows: List<String> = listOf(acRow("AC-01-01", firstBasis)) + defaultAcRows.drop(1),
    tags: List<String> = listOf("@AC-01-03 @layer:2 @e2e:happy-path"),
    firstExpected: String = "成功する",
    secondBasis: String = "REQ-TST-02, DES-TST-02, OI-001",
    firstRowBasis: String = "REQ-TST-01, DES-TST-01",
): String = trsDoc(us, uc) + buildString {
    appendLine()
    appendLine("### 3. 受入条件（AC）")
    appendLine()
    appendLine(AC_HEADER)
    appendLine(AC_SEPARATOR)
    acRows.forEach { appendLine(it) }
    appendLine()
    appendLine("```gherkin")
    appendLine("Feature: 例")
    tags.forEach {
        appendLine()
        appendLine("  $it")
        appendLine("  Scenario: 流れ")
    }
    appendLine("```")
    appendLine()
    appendLine("#### 5.2 組合せ別の期待結果")
    appendLine()
    appendLine(EXPECTATION_HEADER)
    appendLine("|---|---|---|---|")
    appendLine("| C-10 | 条件 | $firstExpected | $firstRowBasis |")
    appendLine("| C-11 | 条件 | 拒否される | $secondBasis |")
}

/**
 * 基本設計書の最小の例。節 2〜9 に設計 ID（DES-TST-01、DES-TST-02）を置く。[outOfRange] が true のときは、
 * 節 1 に DES-TST-90、節 10 に DES-TST-91 も置く（節 2〜9 の表の外なので、設計 ID に数えず、書式の違反になる）
 */
private fun designDoc(outOfRange: Boolean = false): String = buildString {
    appendLine("# 基本設計書")
    appendLine()
    appendLine("## 1. 全体構成")
    appendLine()
    appendLine("| 構成要素 | 役割 |")
    appendLine("|---|---|")
    appendLine("| ${if (outOfRange) "DES-TST-90" else "登録画面"} | 全体 |")
    appendLine()
    appendLine("## 2. 画面")
    appendLine()
    appendLine("| ID | 画面 | 表示と操作 |")
    appendLine("|---|---|---|")
    appendLine("| DES-TST-01 | 登録 | 登録する |")
    appendLine()
    appendLine("## 5. 状態と操作")
    appendLine()
    appendLine("### 5.1 小見出し")
    appendLine()
    appendLine("| ID | 状態 | 操作 | 結果 |")
    appendLine("|---|---|---|---|")
    appendLine("| DES-TST-02 | まだ無い | 登録する | 保存する |")
    appendLine()
    appendLine("## 10. 未決事項")
    appendLine()
    appendLine("| ID | 内容 | 担当 | OI-ID |")
    appendLine("|---|---|---|---|")
    appendLine("| ${if (outOfRange) "DES-TST-91" else "Q-01"} | 問い | オーナー | — |")
}
