package com.example

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/** Spring Boot 4.1.x + Kotlin 2.4 でコンテキストが起動すること（配線の確認。DataSource・Flyway・MyBatis を含む）。 */
@SpringBootTest
@Import(MySqlContainerConfiguration::class)
class ApplicationTest {
    @Test
    fun `コンテキストが起動する`() {
    }
}
