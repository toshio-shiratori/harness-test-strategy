package com.example.todo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** 本番の時計。登録時刻と期限切れの判定は、この時計の現在時刻を使う（判定の日付は Asia/Tokyo。ゾーンは時計に依存しない）。 */
@Configuration
class ClockConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
