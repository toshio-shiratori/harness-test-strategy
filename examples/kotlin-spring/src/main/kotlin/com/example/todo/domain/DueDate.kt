package com.example.todo.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class DueDateError { INVALID }

/** タスクの期限。日付だけを持ち、時刻は持たない。 */
@JvmInline
value class DueDate private constructor(val value: LocalDate) {
    companion object {
        /** 日付からつくる。`LocalDate` は実在する暦日だけなので、拒否する値は無い。 */
        fun of(date: LocalDate): DueDate = DueDate(date)

        /**
         * `YYYY-MM-DD` として読めて、実在する暦日だけを受け付ける。時刻つき・別形式・実在しない日は拒否する。
         * 0000 年と空文字列の扱いは、Open Issue の OI-012（Open）で未決である。
         */
        fun of(raw: String): Validated<DueDate, DueDateError> {
            if (!FORMAT.matches(raw)) return Validated.Invalid(DueDateError.INVALID)
            return try {
                Validated.Valid(of(LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)))
            } catch (_: DateTimeParseException) {
                Validated.Invalid(DueDateError.INVALID)
            }
        }
    }
}

// 4 桁の年、2 桁の月日（ASCII の数字だけ）。ISO_LOCAL_DATE は 5 桁以上の年や符号つきの年も読むので、先に形を絞る
private val FORMAT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
