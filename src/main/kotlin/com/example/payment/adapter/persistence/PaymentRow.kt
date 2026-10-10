package com.example.payment.adapter.persistence

import com.example.payment.domain.FailureReason
import com.example.payment.domain.PaymentState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** `payment` テーブルの1行。MyBatis が引数なしコンストラクタで生成するため、全てデフォルト値付きの var にしている。 */
data class PaymentRow(
    var id: String = "",
    var status: String = "",
    var authorizedAt: LocalDateTime? = null,
    var capturedAt: LocalDateTime? = null,
    var failureReason: String? = null,
)

/** DB の `status` 列に保存する文字列。`PaymentState` の型名とは独立した、永続化形式の契約。 */
internal enum class PaymentStatus { PENDING, AUTHORIZED, CAPTURED, FAILED }

// DATETIME(6) に合わせ、マイクロ秒未満は切り捨てて保存する（PAY-002 OI-003）。UTC の LocalDateTime で持つ（タイムゾーンに依存しない）。
private fun Instant.toColumn(): LocalDateTime = LocalDateTime.ofInstant(truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC)

private fun LocalDateTime.toInstant(): Instant = toInstant(ZoneOffset.UTC)

// when を「式」として使い、else を置かない。PaymentState に状態を追加するとここがコンパイルエラーになる。
internal fun PaymentState.toRow(id: String): PaymentRow =
    when (this) {
        PaymentState.Pending -> PaymentRow(id = id, status = PaymentStatus.PENDING.name)
        is PaymentState.Authorized ->
            PaymentRow(id = id, status = PaymentStatus.AUTHORIZED.name, authorizedAt = authorizedAt.toColumn())
        is PaymentState.Captured ->
            PaymentRow(id = id, status = PaymentStatus.CAPTURED.name, capturedAt = capturedAt.toColumn())
        is PaymentState.Failed ->
            PaymentRow(id = id, status = PaymentStatus.FAILED.name, failureReason = reason.name)
    }

/**
 * 行 → 状態。状態と列が矛盾する行・未知の status・未知の理由は例外で拒否する（PAY-002 AC-03-08）。
 * 例外の型は契約にしない（OI-005）。
 */
internal fun PaymentRow.toState(): PaymentState {
    val kind = PaymentStatus.valueOf(status) // 未知の status は IllegalArgumentException
    return when (kind) {
        PaymentStatus.PENDING -> {
            requireColumns(authorized = false, captured = false, reason = false)
            PaymentState.Pending
        }
        PaymentStatus.AUTHORIZED -> {
            requireColumns(authorized = true, captured = false, reason = false)
            PaymentState.Authorized(requireNotNull(authorizedAt).toInstant())
        }
        PaymentStatus.CAPTURED -> {
            requireColumns(authorized = false, captured = true, reason = false)
            PaymentState.Captured(requireNotNull(capturedAt).toInstant())
        }
        PaymentStatus.FAILED -> {
            requireColumns(authorized = false, captured = false, reason = true)
            PaymentState.Failed(FailureReason.valueOf(requireNotNull(failureReason)))
        }
    }
}

/** 状態ごとに、持つべき列だけが NULL でないことを要求する（DB の CHECK 制約と同じ規則）。 */
private fun PaymentRow.requireColumns(authorized: Boolean, captured: Boolean, reason: Boolean) {
    val consistent = (authorizedAt != null) == authorized &&
        (capturedAt != null) == captured &&
        (failureReason != null) == reason
    require(consistent) { "状態 $status と列が矛盾している: id=$id" }
}
