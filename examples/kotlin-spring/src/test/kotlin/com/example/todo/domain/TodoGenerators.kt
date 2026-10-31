package com.example.todo.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import java.time.Instant
import java.time.LocalDate

// ドメイン型ごとに Arb をここに集める（02a §2.3 規則 1）。ファクトリの検証と同じ制約を満たす値だけを生成する。
// 生成した値の期待値は、生成の仕方（使った単位の数など）から決め、実装の計算を写さない（規則 5）。
// テストは `as` で Valid を取り出してよい（detekt の対象は本番ソースのみ）。

fun title(raw: String): TaskTitle = (TaskTitle.of(raw) as Validated.Valid).value

fun due(raw: String): DueDate = (DueDate.of(raw) as Validated.Valid).value

/** 連結しても隣と結合しない、見た目 1 文字の単位。結合文字つき・国旗（地域指示子 2 個）・絵文字を含む。 */
val titleUnits: List<String> = listOf("a", "Z", "あ", "牛", "e\u0301", "🇯🇵", "🇺🇸", "😀")

/** White_Space 属性を持つ 25 文字（Unicode の定義）。 */
val whiteSpaceChars: List<Char> =
    ('\u0009'..'\u000D').toList() +
        listOf(' ', '\u0085', '\u00A0', '\u1680') +
        ('\u2000'..'\u200A').toList() +
        listOf('\u2028', '\u2029', '\u202F', '\u205F', '\u3000')

/** 空白の並び（1〜5 文字）。 */
val arbWhiteSpace: Arb<String> = Arb.list(Arb.element(whiteSpaceChars), 1..5).map { it.joinToString("") }

/** 単位を [count] 個並べた文字列の生成器。見た目の長さは必ず単位の数になる。 */
fun arbUnits(count: IntRange): Arb<List<String>> = Arb.list(Arb.element(titleUnits), count)

val arbTitle: Arb<TaskTitle> = arbUnits(1..TaskTitle.MAX_LENGTH).map { title(it.joinToString("")) }

val arbTaskId: Arb<TaskId> = Arb.long(1L..1_000_000L).map(::TaskId)

private val firstDay = LocalDate.of(1, 1, 1).toEpochDay()
private val lastDay = LocalDate.of(9999, 12, 31).toEpochDay()

/** 実在する暦日（0001-01-01〜9999-12-31）。 */
val arbCalendarDate: Arb<LocalDate> = Arb.long(firstDay..lastDay).map(LocalDate::ofEpochDay)

/** 実在する暦日の期限。 */
val arbDueDate: Arb<DueDate> = arbCalendarDate.map { DueDate.of(it) }

/** Asia/Tokyo の規則が一定の期間（1952〜2100 年）の期限。+09:00 のオラクルで検証できる。 */
val arbModernDueDate: Arb<DueDate> =
    Arb.long(LocalDate.of(1952, 1, 1).toEpochDay()..LocalDate.of(2100, 12, 31).toEpochDay()).map { DueDate.of(LocalDate.ofEpochDay(it)) }

/** 同じ期限が重なりやすい期限（2026-11-01 から 10 日間）。並び順の同順位を起こす。 */
val arbNearDueDate: Arb<DueDate> = Arb.int(0..9).map { DueDate.of(LocalDate.of(2026, 11, 1).plusDays(it.toLong())) }

val arbStatus: Arb<TaskStatus> = Arb.element(TaskStatus.Open, TaskStatus.Done)

/** 登録時刻が重なりやすい時刻（0〜3 秒）。 */
val arbNearCreatedAt: Arb<Instant> = Arb.int(0..3).map { Instant.EPOCH.plusSeconds(it.toLong()) }

/** 識別子が重ならないタスクの一覧。期限と登録時刻は重なりやすい。 */
val arbTasks: Arb<List<Task>> =
    Arb.list(
        Arb.bind(arbTaskId, arbTitle, arbNearDueDate.orNull(0.4), arbStatus, arbNearCreatedAt, ::Task),
        0..12,
    ).map { tasks -> tasks.distinctBy { it.id } }

val arbVisibility: Arb<CompletedVisibility> = Arb.element(CompletedVisibility.entries)
