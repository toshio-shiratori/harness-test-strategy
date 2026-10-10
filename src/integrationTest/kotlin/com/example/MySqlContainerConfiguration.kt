package com.example

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

/**
 * Testcontainers で MySQL を起動し、`@ServiceConnection` で DataSource に自動接続する（02 §3.2）。
 * 本番と同じ系統の MySQL 8.4（LTS）を使い、CHECK 制約（8.0.16 以降で有効）を実 DB で検証する。
 */
@TestConfiguration(proxyBeanMethods = false)
class MySqlContainerConfiguration {
    @Bean
    @ServiceConnection
    fun mysql(): MySQLContainer = MySQLContainer("mysql:8.4")
}
