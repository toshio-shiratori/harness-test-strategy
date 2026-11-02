package com.example.todo.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class DueDateError { INVALID }

/** タスクの期限。日付だけを持ち、時刻は持たない。 */
@JvmInline
value class DueDate private constructor(val value: LocalDate) {
    companion object {
        /** 日付からつくる。下限（0001-01-01）より前の日付は拒否する（OI-015）。 */
        fun of(date: LocalDate): Validated<DueDate, DueDateError> =
            if (date < MIN) Validated.Invalid(DueDateError.INVALID) else Validated.Valid(DueDate(date))

        /**
         * `YYYY-MM-DD` として読めて、実在する暦日だけを受け付ける。時刻つき・別形式・実在しない日は拒否する。
         * 空文字列は形が合わないので拒否する（OI-012）。0000 年は下限より前なので拒否する（OI-015）。
         */
        fun of(raw: String): Validated<DueDate, DueDateError> {
            if (!FORMAT.matches(raw)) return Validated.Invalid(DueDateError.INVALID)
            return try {
                of(LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE))
            } catch (_: DateTimeParseException) {
                Validated.Invalid(DueDateError.INVALID)
            }
        }
    }
}

// 期限の下限。MySQL の DATE は 0 年を閏年として扱わず 0000-02-29 を保存できないので、0000 年は受け付けない（OI-015）
private val MIN = LocalDate.of(1, 1, 1)

// 4 桁の年、2 桁の月日（ASCII の数字だけ）。ISO_LOCAL_DATE は 5 桁以上の年や符号つきの年も読むので、先に形を絞る
private val FORMAT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
