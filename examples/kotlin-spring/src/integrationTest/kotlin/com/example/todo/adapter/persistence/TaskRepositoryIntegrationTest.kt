package com.example.todo.adapter.persistence

import com.example.MySqlContainerConfiguration
import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.CompletedVisibilityRepository
import com.example.todo.domain.DueDate
import com.example.todo.domain.Task
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskRepository
import com.example.todo.domain.TaskStatus
import com.example.todo.domain.TaskTitle
import com.example.todo.domain.Validated
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeGreaterThan
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
import java.time.LocalDate
import java.util.TimeZone

/**
 * 永続化アダプタ（MyBatis + Flyway + MySQL）の結合テスト（層2 Integration）。Testcontainers の実 MySQL を使う。
 * このテストは、TRS の AC に紐づかない技術的な不変条件（行の往復、時刻の精度、CHECK 制約、迂回した行の読み込み拒否）を確かめる。
 * AC に紐づく保存と読み戻しは、HTTP を通すテスト（`TaskApiHttpTest` など）が受け持つため、層2の対応表は変えず、テスト名に AC 番号を付けない。
 */
@SpringBootTest(properties = ["spring.flyway.clean-disabled=false"]) // 矛盾行のテストで CHECK を外すため、毎回スキーマを作り直す
@Import(MySqlContainerConfiguration::class)
class TaskRepositoryIntegrationTest {
    @Autowired
    lateinit var tasks: TaskRepository

    @Autowired
    lateinit var visibilities: CompletedVisibilityRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var flyway: Flyway

    @BeforeEach
    fun resetSchema() {
        flyway.clean()
        flyway.migrate()
    }

    private fun title(raw: String): TaskTitle = (TaskTitle.of(raw) as Validated.Valid).value

    private fun task(id: TaskId, due: DueDate? = null, status: TaskStatus = TaskStatus.Open, createdAt: Instant = micros) =
        Task(id, title("牛乳を買う"), due, status, createdAt)

    // マイクロ秒精度の時刻（読み戻しで同一になる）
    private val micros = Instant.parse("2026-11-01T01:00:00.123456Z")

    // ナノ秒を含む時刻。末尾が 999 なので、四捨五入・切り上げなら桁が繰り上がって区別できる
    private val nanos = Instant.parse("2026-11-01T01:00:00.123456999Z")

    @Test
    fun `払い出す番号は、呼ぶたびに前より大きくなる`() {
        val first = tasks.nextId()
        val second = tasks.nextId()
        val third = tasks.nextId()

        second.value shouldBeGreaterThan first.value
        third.value shouldBeGreaterThan second.value
    }

    @Test
    fun `保存したタスクを読み戻すと、期限あり・なし、未完了・完了のどれも同じである`() {
        val due = DueDate.of(LocalDate.of(2026, 11, 30))
        val cases = listOf(
            task(tasks.nextId(), due = due, status = TaskStatus.Open),
            task(tasks.nextId(), due = null, status = TaskStatus.Open),
            task(tasks.nextId(), due = due, status = TaskStatus.Done),
            task(tasks.nextId(), due = null, status = TaskStatus.Done),
        )

        cases.forEach(tasks::save)

        cases.forEach { tasks.find(it.id) shouldBe it }
        tasks.findAll().toSet() shouldBe cases.toSet()
    }

    @Test
    fun `登録時刻はマイクロ秒に切り捨てて保存される`() {
        val saved = task(tasks.nextId(), createdAt = nanos)

        tasks.save(saved)

        tasks.find(saved.id) shouldBe saved.copy(createdAt = micros)
    }

    @Test
    fun `期限の日付は、サーバーの既定のタイムゾーンと暦の境目によらず、同じ日付で読み戻せる`() {
        val original = TimeZone.getDefault()
        try {
            listOf("UTC", "America/Los_Angeles", "Asia/Tokyo").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                // 両端と、グレゴリオ暦の採用の境目（ユリウス暦の 1582-10-04 の翌日が 10-15）
                listOf("0001-01-01", "1582-10-04", "1582-10-15", "2026-11-01", "9999-12-31").forEach { date ->
                    val saved = task(tasks.nextId(), due = DueDate.of(LocalDate.parse(date)))

                    tasks.save(saved)

                    tasks.find(saved.id) shouldBe saved
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `同じ識別子で保存すると上書きされる`() {
        val id = tasks.nextId()
        tasks.save(task(id, status = TaskStatus.Open))

        tasks.save(task(id, status = TaskStatus.Done))

        tasks.findAll().map { it.status } shouldBe listOf(TaskStatus.Done)
    }

    @Test
    fun `保存されていない識別子は読み込めない`() {
        tasks.save(task(tasks.nextId()))

        tasks.find(TaskId(Long.MAX_VALUE)).shouldBeNull()
    }

    @Test
    fun `未知の status は、DB の CHECK 制約が書き込みを拒否する`() {
        shouldThrow<Exception> {
            jdbc.execute("INSERT INTO task (id, title, due_date, status, created_at) VALUES (1, 'x', NULL, 'UNKNOWN', '2026-11-01 01:00:00.000000')")
        }

        jdbc.queryForObject("SELECT COUNT(*) FROM task", Int::class.java) shouldBe 0
    }

    @Test
    fun `CHECK 制約を迂回して入った未知の status の行は、読み込み時に例外で拒否される`() {
        jdbc.execute("ALTER TABLE task DROP CHECK chk_task_status")
        jdbc.execute("INSERT INTO task (id, title, due_date, status, created_at) VALUES (1, 'x', NULL, 'UNKNOWN', '2026-11-01 01:00:00.000000')")

        shouldThrow<Exception> { tasks.find(TaskId(1)) }
        shouldThrow<Exception> { tasks.findAll() }
    }

    @Test
    fun `完了済みの表示の選択は、保存するまで無く、保存すると読み戻せ、上書きできる`() {
        visibilities.find().shouldBeNull()

        visibilities.save(CompletedVisibility.HIDE)
        visibilities.find() shouldBe CompletedVisibility.HIDE

        visibilities.save(CompletedVisibility.SHOW)
        visibilities.find() shouldBe CompletedVisibility.SHOW
    }

    @Test
    fun `未知の表示の選択は、DB の CHECK 制約が書き込みを拒否する`() {
        shouldThrow<Exception> { jdbc.execute("INSERT INTO completed_visibility (id, visibility) VALUES (1, 'UNKNOWN')") }
        shouldThrow<Exception> { jdbc.execute("INSERT INTO completed_visibility (id, visibility) VALUES (2, 'SHOW')") }

        jdbc.queryForObject("SELECT COUNT(*) FROM completed_visibility", Int::class.java) shouldBe 0
    }
}
