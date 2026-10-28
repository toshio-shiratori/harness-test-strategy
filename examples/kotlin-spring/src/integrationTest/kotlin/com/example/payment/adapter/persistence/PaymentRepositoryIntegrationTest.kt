package com.example.payment.adapter.persistence

import com.example.MySqlContainerConfiguration
import com.example.order.domain.Validated
import com.example.payment.domain.FailureReason
import com.example.payment.domain.PaymentId
import com.example.payment.domain.PaymentRepository
import com.example.payment.domain.PaymentState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/**
 * TRS PAY-002 の Integration テスト（層2）。Testcontainers の実 MySQL に MyBatis + Flyway で接続し、
 * 保存・読み戻し・DB の CHECK 制約を検証する。期待値は TRS の AC（仕様）が正で、実装の挙動から逆生成していない。
 */
@SpringBootTest(properties = ["spring.flyway.clean-disabled=false"]) // 矛盾行のテストで CHECK を外すため、毎回スキーマを作り直す
@Import(MySqlContainerConfiguration::class)
class PaymentRepositoryIntegrationTest {
    @Autowired
    lateinit var repository: PaymentRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var flyway: Flyway

    @BeforeEach
    fun resetSchema() {
        flyway.clean()
        flyway.migrate()
    }

    private fun id(raw: String): PaymentId = (PaymentId.of(raw) as Validated.Valid).value

    private val pid = id("p-1")

    // マイクロ秒精度の時刻（読み戻しで同一になる）
    private val micros = Instant.parse("2026-10-11T00:00:00.123456Z")

    // ナノ秒を含む時刻。末尾が 999 なので、四捨五入・切り上げなら桁が繰り上がって区別できる
    private val nanos = Instant.parse("2026-10-11T00:00:00.123456999Z")

    @Test
    fun `AC-03-01 Pending を保存して読み戻すと Pending である`() {
        repository.save(pid, PaymentState.Pending)

        repository.find(pid) shouldBe PaymentState.Pending
    }

    @Test
    fun `AC-03-02 Authorized を保存して読み戻すと、同じ時刻の Authorized である`() {
        repository.save(pid, PaymentState.Authorized(micros))

        repository.find(pid) shouldBe PaymentState.Authorized(micros)
    }

    @Test
    fun `AC-03-03 Captured を保存して読み戻すと、同じ時刻の Captured である`() {
        repository.save(pid, PaymentState.Captured(micros))

        repository.find(pid) shouldBe PaymentState.Captured(micros)
    }

    @Test
    fun `AC-03-04 Failed を保存して読み戻すと、同じ理由の Failed である`() {
        FailureReason.entries.forEach { reason ->
            repository.save(pid, PaymentState.Failed(reason))

            repository.find(pid) shouldBe PaymentState.Failed(reason)
        }
    }

    @Test
    fun `AC-03-05 時刻はマイクロ秒に切り捨てて保存される`() {
        val truncated = Instant.parse("2026-10-11T00:00:00.123456Z")

        repository.save(pid, PaymentState.Authorized(nanos))
        repository.find(pid) shouldBe PaymentState.Authorized(truncated)

        repository.save(pid, PaymentState.Captured(nanos))
        repository.find(pid) shouldBe PaymentState.Captured(truncated)
    }

    @Test
    fun `AC-03-06 同じ ID で保存すると上書きされ、遷移ルールは検査されない`() {
        repository.save(pid, PaymentState.Pending)
        repository.save(pid, PaymentState.Failed(FailureReason.TIMEOUT))
        repository.find(pid) shouldBe PaymentState.Failed(FailureReason.TIMEOUT)

        // Failed（終端）から Pending への上書きも拒否しない。遷移ルールの強制はドメインの責務（PAY-002 OI-004）
        repository.save(pid, PaymentState.Pending)
        repository.find(pid) shouldBe PaymentState.Pending
    }

    @Test
    fun `AC-03-07 保存されていない ID は読み込めない`() {
        repository.save(pid, PaymentState.Pending)

        repository.find(id("p-unknown")).shouldBeNull()
    }

    @Test
    fun `AC-03-08 CHECK 制約を迂回して入った矛盾行は、読み込み時に例外で拒否される`() {
        // CHECK を外してから、状態と列が矛盾する行・未知の status を直接 INSERT する
        jdbc.execute("ALTER TABLE payment DROP CHECK chk_payment_columns")
        jdbc.execute("ALTER TABLE payment DROP CHECK chk_payment_status")
        val now = "'2026-10-11 00:00:00.000000'"
        val rows = listOf(
            Triple("AUTHORIZED", "NULL, NULL, NULL", "authorized_at が無い"),
            Triple("CAPTURED", "NULL, NULL, NULL", "captured_at が無い"),
            Triple("FAILED", "NULL, NULL, NULL", "failure_reason が無い"),
            Triple("PENDING", "$now, NULL, NULL", "PENDING なのに時刻がある"),
            Triple("UNKNOWN", "NULL, NULL, NULL", "未知の status"),
        )
        rows.forEachIndexed { index, (status, columns, _) ->
            jdbc.execute(
                "INSERT INTO payment (id, status, authorized_at, captured_at, failure_reason) VALUES ('bad-$index', '$status', $columns)",
            )
        }

        rows.indices.forEach { index ->
            shouldThrow<Exception> { repository.find(id("bad-$index")) }
        }
    }

    @Test
    fun `AC-03-09 状態と列が矛盾する行・未知の status は、DB の CHECK 制約が書き込みを拒否する`() {
        val now = "'2026-10-11 00:00:00.000000'"
        val rows = listOf(
            "'AUTHORIZED', NULL, NULL, NULL",
            "'CAPTURED', NULL, NULL, NULL",
            "'FAILED', NULL, NULL, NULL",
            "'PENDING', $now, NULL, NULL",
            "'UNKNOWN', NULL, NULL, NULL",
        )

        rows.forEachIndexed { index, values ->
            shouldThrow<Exception> {
                jdbc.execute(
                    "INSERT INTO payment (id, status, authorized_at, captured_at, failure_reason) VALUES ('bad-$index', $values)",
                )
            }
        }
        jdbc.queryForObject("SELECT COUNT(*) FROM payment", Int::class.java) shouldBe 0
    }
}
