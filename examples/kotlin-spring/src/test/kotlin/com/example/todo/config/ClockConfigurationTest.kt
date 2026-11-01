package com.example.todo.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** 本番の時計の設定を、層1で確かめる技術的な検証。時計は UTC のシステム時計で、現在時刻を返す（期限切れの判定の日付は時計のゾーンに依存しない）。 */
class ClockConfigurationTest : StringSpec({
    "本番の時計は UTC のシステム時計で、現在時刻を返す" {
        val clock = ClockConfiguration().clock()
        val before = Instant.now()

        val now = clock.instant()

        clock.zone shouldBe ZoneOffset.UTC
        (Duration.between(before, now) < Duration.ofSeconds(5) && !now.isBefore(before)) shouldBe true
    }
})
