package com.example.todo.adapter.web

import com.example.MySqlContainerConfiguration
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** テストが進められる時計。`Clock.fixed` は進められないので、時刻を持つ時計を自作する。 */
class MutableClock(private var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now

    fun set(instant: Instant) {
        now = instant
    }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/**
 * 本番の `Clock` の代わりに、テストが固定・進行できる時計を使う。
 * Spring Boot は既定で bean の上書きを禁止しているので、本番の `clock` とは別名の `@Primary` で入れる。
 */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {
    @Bean
    @Primary
    fun mutableClock(): MutableClock = MutableClock(DEFAULT_NOW)

    companion object {
        /** 2026-11-01 10:00 JST。 */
        val DEFAULT_NOW: Instant = Instant.parse("2026-11-01T01:00:00Z")
    }
}

/** 完了済みの表示の選択（応答の JSON を読み取った形）。 */
data class VisibilityJson(val visibility: String)

/** 一覧の 1 件（応答の JSON を読み取った形）。 */
data class TaskJson(val id: Long, val title: String, val dueDate: String?, val status: String, val category: String)

/**
 * HTTP と実 DB を通す Integration（層2）の共通部分。MockMvc に束ねた `RestTestClient` で、Controller → Service → MyBatis → MySQL（Testcontainers）まで通す。
 * 保存の確認は、HTTP で登録し、HTTP で一覧を取り直す形で行う（メモリだけの実装を通さない）。
 * 各テストの前に、タスクと完了済みの表示の選択を消し、時計を既定の時刻に戻す。
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@Import(MySqlContainerConfiguration::class, TestClockConfiguration::class)
abstract class TaskApiTestBase {
    @Autowired
    lateinit var client: RestTestClient

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var clock: MutableClock

    @BeforeEach
    fun resetState() {
        jdbc.update("DELETE FROM task")
        jdbc.update("DELETE FROM completed_visibility")
        clock.set(TestClockConfiguration.DEFAULT_NOW)
    }

    /** 登録の要求を送る。[body] が文字列なら本文をそのまま、それ以外なら JSON にして送る。 */
    protected fun postRegister(body: Any): RestTestClient.ResponseSpec =
        client.post().uri("/tasks").contentType(MediaType.APPLICATION_JSON).body(body).exchange()

    /** 登録の本文。[title] と [dueDate] は、null なら項目ごと省く。 */
    protected fun registerBody(title: String?, dueDate: String?): Map<String, String> =
        buildMap {
            if (title != null) put("title", title)
            if (dueDate != null) put("dueDate", dueDate)
        }

    /** タイトルと期限（無ければ省く）で登録し、応答の 1 件を返す。成功（2xx）を前提にする。 */
    protected fun register(title: String, dueDate: String? = null): TaskJson {
        val result = postRegister(registerBody(title, dueDate))
            .expectStatus().is2xxSuccessful
            .expectBody(TaskJson::class.java)
            .returnResult()
        return requireNotNull(result.responseBody)
    }

    protected fun complete(id: Long): RestTestClient.ResponseSpec = client.post().uri("/tasks/$id/complete").exchange()

    protected fun reopen(id: Long): RestTestClient.ResponseSpec = client.post().uri("/tasks/$id/reopen").exchange()

    /** 一覧を取り直す（保存された「完了済みの表示」の選択に従う）。 */
    protected fun list(): List<TaskJson> {
        val result = client.get().uri("/tasks").exchange()
            .expectStatus().is2xxSuccessful
            .expectBody(object : ParameterizedTypeReference<List<TaskJson>>() {})
            .returnResult()
        return requireNotNull(result.responseBody)
    }

    protected fun setVisibility(value: String): RestTestClient.ResponseSpec =
        client.put().uri("/settings/completed-visibility")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"visibility":"$value"}""")
            .exchange()

    /** 保存された「完了済みの表示」の選択を、別の要求で読み戻す。 */
    protected fun readVisibility(): String {
        val result = client.get().uri("/settings/completed-visibility").exchange()
            .expectStatus().is2xxSuccessful
            .expectBody(VisibilityJson::class.java)
            .returnResult()
        return requireNotNull(result.responseBody).visibility
    }

    /** 本文が ProblemDetail で、status が [status]、`codes` が集合として [codes] に等しいことを確かめる。順序は仕様にしない。 */
    protected fun RestTestClient.ResponseSpec.expectProblem(status: Int, vararg codes: String) {
        expectStatus().isEqualTo(status)
        expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        expectBody()
            .jsonPath("$.status").isEqualTo(status)
            .jsonPath("$.codes").value<List<String>> { it.sorted() shouldBe codes.toList().sorted() }
    }
}
