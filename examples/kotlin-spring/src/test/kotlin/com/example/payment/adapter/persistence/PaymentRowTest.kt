package com.example.payment.adapter.persistence

import com.example.payment.domain.FailureReason
import com.example.payment.domain.PaymentState
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.LocalDateTime

/**
 * `PaymentState` ↔ `PaymentRow` の変換を、DB なしの層1で検証する。
 * TRS PAY-002 の AC（AC-03-01〜06・08）は層2（実 DB）の受け入れ基準で、ここは変換単体の確認である。
 * 期待値は TRS（OI-002 状態と列の整合、OI-003 マイクロ秒への切り捨て、OI-005 例外の型を契約にしない）が正で、
 * 例外の型は指定しない。層2の対応表は変えないため、テスト名に AC 番号は付けない。
 */
class PaymentRowTest : StringSpec({
    val micros = Instant.parse("2026-10-11T00:00:00.123456Z")
    val nanos = Instant.parse("2026-10-11T00:00:00.123456999Z") // 末尾 999 は、四捨五入・切り上げなら繰り上がる
    val someTime: LocalDateTime = LocalDateTime.parse("2026-10-11T00:00:00.123456")

    val states = listOf(
        PaymentState.Pending,
        PaymentState.Authorized(micros),
        PaymentState.Captured(micros),
        PaymentState.Failed(FailureReason.CARD_DECLINED),
        PaymentState.Failed(FailureReason.TIMEOUT),
    )

    "行に変換して状態へ戻すと、全ての状態が元と同じになる" {
        states.forEach { state ->
            state.toRow("p-1").toState() shouldBe state
        }
    }

    "ID と状態の種別が行に入る。種別は PENDING・AUTHORIZED・CAPTURED・FAILED の文字列である" {
        PaymentState.Pending.toRow("p-1").let {
            it.id shouldBe "p-1"
            it.status shouldBe "PENDING"
        }
        PaymentState.Authorized(micros).toRow("p-2").let {
            it.id shouldBe "p-2"
            it.status shouldBe "AUTHORIZED"
        }
        PaymentState.Captured(micros).toRow("p-3").let {
            it.id shouldBe "p-3"
            it.status shouldBe "CAPTURED"
        }
        PaymentState.Failed(FailureReason.TIMEOUT).toRow("p-4").let {
            it.id shouldBe "p-4"
            it.status shouldBe "FAILED"
        }
    }

    "状態は、持つべき列だけを埋めた行になる" {
        PaymentState.Pending.toRow("p").let {
            (it.authorizedAt == null && it.capturedAt == null && it.failureReason == null) shouldBe true
        }
        PaymentState.Authorized(micros).toRow("p").let {
            (it.authorizedAt != null && it.capturedAt == null && it.failureReason == null) shouldBe true
        }
        PaymentState.Captured(micros).toRow("p").let {
            (it.authorizedAt == null && it.capturedAt != null && it.failureReason == null) shouldBe true
        }
        PaymentState.Failed(FailureReason.CARD_DECLINED).toRow("p").let {
            (it.authorizedAt == null && it.capturedAt == null) shouldBe true
            it.failureReason shouldBe "CARD_DECLINED"
        }
    }

    "時刻はマイクロ秒に切り捨てて保存され、読み戻した時刻は切り捨てた値と等しい" {
        PaymentState.Authorized(nanos).toRow("p").toState() shouldBe PaymentState.Authorized(micros)
        PaymentState.Captured(nanos).toRow("p").toState() shouldBe PaymentState.Captured(micros)
    }

    "状態と列が整合した行は読み込める" {
        PaymentRow(id = "p", status = "PENDING").toState() shouldBe PaymentState.Pending
        PaymentRow(id = "p", status = "AUTHORIZED", authorizedAt = someTime).toState().shouldBeInstanceOf<PaymentState.Authorized>()
        PaymentRow(id = "p", status = "CAPTURED", capturedAt = someTime).toState().shouldBeInstanceOf<PaymentState.Captured>()
        PaymentRow(id = "p", status = "FAILED", failureReason = "TIMEOUT").toState() shouldBe
            PaymentState.Failed(FailureReason.TIMEOUT)
    }

    // 次の 3 つのテストの行（状態に必要な列が無い・不要な列がある・別の状態の列だけがある）の根拠は、
    // PAY-002 OI-002「状態と列が矛盾する行は、読み込みも拒否する」と、それを決めた CHECK 制約（V1__create_payment.sql。
    // 状態ごとに持つべき列だけが NULL でない）である。AC-03-08 の表の例（時刻 NULL の AUTHORIZED 等・時刻つきの PENDING）を、
    // CHECK が拒否する全ての組み合わせへ広げて、列ごとの判定が欠けていないことを確かめる。
    "状態に必要な列が NULL の行は、読み込みが例外で拒否される" {
        listOf(
            PaymentRow(id = "p", status = "AUTHORIZED"),
            PaymentRow(id = "p", status = "CAPTURED"),
            PaymentRow(id = "p", status = "FAILED"),
        ).forEach { row -> shouldThrowAny { row.toState() } }
    }

    "状態に不要な列が埋まっている行は、読み込みが例外で拒否される" {
        listOf(
            PaymentRow(id = "p", status = "PENDING", authorizedAt = someTime),
            PaymentRow(id = "p", status = "PENDING", capturedAt = someTime),
            PaymentRow(id = "p", status = "PENDING", failureReason = "TIMEOUT"),
            PaymentRow(id = "p", status = "AUTHORIZED", authorizedAt = someTime, capturedAt = someTime),
            PaymentRow(id = "p", status = "AUTHORIZED", authorizedAt = someTime, failureReason = "TIMEOUT"),
            PaymentRow(id = "p", status = "CAPTURED", capturedAt = someTime, authorizedAt = someTime),
            PaymentRow(id = "p", status = "CAPTURED", capturedAt = someTime, failureReason = "TIMEOUT"),
            PaymentRow(id = "p", status = "FAILED", failureReason = "TIMEOUT", authorizedAt = someTime),
            PaymentRow(id = "p", status = "FAILED", failureReason = "TIMEOUT", capturedAt = someTime),
        ).forEach { row -> shouldThrowAny { row.toState() } }
    }

    "別の状態の列だけを持つ行は、読み込みが例外で拒否される" {
        listOf(
            PaymentRow(id = "p", status = "AUTHORIZED", capturedAt = someTime),
            PaymentRow(id = "p", status = "CAPTURED", authorizedAt = someTime),
            PaymentRow(id = "p", status = "FAILED", capturedAt = someTime),
        ).forEach { row -> shouldThrowAny { row.toState() } }
    }

    "未知の status の行は、読み込みが例外で拒否される" {
        // PAY-002 AC-03-08 の例（status "UNKNOWN"）。OI-005: 未知の status は DB の CHECK も拒否し、読み込みでも拒否する
        shouldThrowAny { PaymentRow(id = "p", status = "UNKNOWN").toState() }
    }
})
