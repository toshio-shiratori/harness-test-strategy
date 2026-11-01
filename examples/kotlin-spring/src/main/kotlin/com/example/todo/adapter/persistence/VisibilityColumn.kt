package com.example.todo.adapter.persistence

import com.example.todo.domain.CompletedVisibility

/** DB の `visibility` 列に保存する文字列。`CompletedVisibility` の名前とは独立した、永続化形式の契約。 */
internal enum class VisibilityColumn { SHOW, HIDE }

internal fun CompletedVisibility.toColumn(): String =
    when (this) {
        CompletedVisibility.SHOW -> VisibilityColumn.SHOW
        CompletedVisibility.HIDE -> VisibilityColumn.HIDE
    }.name

/** 列 → 選択。未知の文字列は例外で拒否する（外部の文字列を閉じた型へ）。 */
internal fun String.toVisibility(): CompletedVisibility =
    when (VisibilityColumn.valueOf(this)) {
        VisibilityColumn.SHOW -> CompletedVisibility.SHOW
        VisibilityColumn.HIDE -> CompletedVisibility.HIDE
    }
