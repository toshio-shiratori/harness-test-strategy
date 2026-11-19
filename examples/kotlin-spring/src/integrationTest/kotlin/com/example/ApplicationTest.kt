package com.example

import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.CompletedVisibilityRepository
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Spring Boot 4.1.x + Kotlin 2.4 でコンテキストが起動し、DataSource・Flyway・MyBatis がつながって動くことの確認（配線の確認）。
 * TRS の AC に紐づかない技術的な検証で、層2の対応表は変えないため、テスト名に AC 番号を付けない。
 */
@SpringBootTest
@Import(MySqlContainerConfiguration::class)
class ApplicationTest {
    @Autowired
    lateinit var visibilities: CompletedVisibilityRepository

    @Test
    fun `コンテキストが起動し、選択を保存すると DB を通して読み戻せる`() {
        visibilities.save(CompletedVisibility.SHOW)

        visibilities.find() shouldBe CompletedVisibility.SHOW
    }
}
