package com.example.e2e

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

/**
 * E2E 用の MySQL（Testcontainers）。`@ServiceConnection` で DataSource に自動接続し、Flyway がスキーマを作る。
 * `src/integrationTest` の同名の設定と同じ構成を、ソースセットを分けたまま使うために複製している（互いの出力に依存させない）。
 * 本番と同じ系統の MySQL 8.4（LTS）を使う。
 */
@TestConfiguration(proxyBeanMethods = false)
class E2eMySqlContainerConfiguration {
    @Bean
    @ServiceConnection
    fun mysql(): MySQLContainer = MySQLContainer("mysql:8.4")
}
