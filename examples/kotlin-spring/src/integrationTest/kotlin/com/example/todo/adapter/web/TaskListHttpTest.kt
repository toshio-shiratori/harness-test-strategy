package com.example.todo.adapter.web

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 一覧の完了済みの表示の選択（UC-06）の HTTP と DB の結合テスト（層2 Integration）。
 * 区分・並び順・絞り込みの規則そのものは層1（`TaskCategoryTest`・`TaskListTest`・`TaskControllerTest`）が確かめている。
 * ここは、選択を DB に保存して別の要求で読み戻すことと、選択の未知の値を Spring が拒否することに絞る。
 * 一覧（`GET /tasks`）の区分・並びは、層1で確かめられるので、ここでは確かめない。
 * 期待値は TRS TODO-001 の AC と制約表から取る。AC と制約表は、要件定義書・基本設計書・OI の決定から生成したもので、実装から逆生成しない（03 §3.1）。
 */
class TaskListHttpTest : TaskApiTestBase() {
    @Test
    fun `AC-06-03 保存された選択が無いとき、選択は表示するである`() {
        readVisibility() shouldBe "SHOW"
    }

    @Test
    fun `AC-06-04 選択は保存され、別の要求で読み戻しても保たれる`() {
        // 保存されていない -> 隠す
        setVisibility("HIDE").expectStatus().is2xxSuccessful
        readVisibility() shouldBe "HIDE"

        // 隠す -> 表示する
        setVisibility("SHOW").expectStatus().is2xxSuccessful
        readVisibility() shouldBe "SHOW"
    }

    @Test
    fun `AC-06-07 完了済みの表示の選択に未知の値を送ると、400 と MALFORMED_REQUEST で拒否され、選択は変わらない`() {
        setVisibility("HIDE").expectStatus().is2xxSuccessful

        setVisibility("UNKNOWN").expectProblem(400, "MALFORMED_REQUEST")

        readVisibility() shouldBe "HIDE"
    }
}
