package com.example.todo.adapter.persistence

import com.example.todo.domain.arbVisibility
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

/**
 * 完了済みの表示の選択と、DB の列の文字列の変換を、層1で確かめる技術的な検証。層2の対応表は変えないため、テスト名に AC 番号を付けない。
 * 列の文字列（SHOW / HIDE）は `MyBatisTaskRepositoryTest` が確かめる。
 */
class VisibilityColumnTest : StringSpec({
    // 分類: ラウンドトリップ（選択を列の文字列にして戻すと、同じ選択になる）
    "完了済みの表示の選択を列の文字列にして戻すと、同じ選択になる（ラウンドトリップ）" {
        checkAll(arbVisibility) { visibility ->
            visibility.toColumn().toVisibility() shouldBe visibility
        }
    }
})
