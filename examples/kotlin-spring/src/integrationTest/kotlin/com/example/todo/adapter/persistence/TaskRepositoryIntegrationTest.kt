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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * 永続化アダプタ（MyBatis + Flyway + MySQL）の結合テスト（層2 Integration）。Testcontainers の実 MySQL を使う。
 * このテストは、TRS の AC に紐づかない技術的な不変条件（行の往復、時刻の精度、CHECK 制約、迂回した行の読み込み拒否）を確かめる。
 * AC に紐づく保存と読み戻しは、HTTP を通すテスト（`TaskApiHttpTest` など）が受け持つため、層2の対応表は変えず、テスト名に AC 番号を付けない。
 * ここは、同じ呼び出し先を 3 通り以上の結果で確かめないよう、実 DB でしか確かめられないこと（保存と読み戻し、時刻の精度、日付の扱い、上書き、CHECK 制約とその迂回）に絞る。
 * 保存するタスクは、上書きのテストを除き、状態を完了に統一する。ほかの結果は、次のテストが実 DB で確かめる。
 * - 未完了の保存と読み戻し: `TaskOperationHttpTest`・`TaskApiHttpTest`（登録した後と、未完了に戻した後の一覧。AC-03-04）。状態の写像は層1の `TaskRowTest` も確かめる。
 * - 存在しない識別子: `TaskOperationHttpTest` の AC-02-03・AC-03-03（404）。層1の `MyBatisTaskRepositoryTest` は null を確かめる。
 * - 完了済みを隠す選択の保存と読み戻し: `TaskListHttpTest` の AC-06-04。
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

    private fun dueOf(date: LocalDate): DueDate = (DueDate.of(date) as Validated.Valid).value

    private fun taskId(value: Long): TaskId = (TaskId.of(value) as Validated.Valid).value

    private fun assertStored(found: Task, id: Long, due: LocalDate?) {
        found.id.value shouldBe id
        found.title.value shouldBe "牛乳を買う"
        found.dueDate?.value shouldBe due
        found.status shouldBe TaskStatus.Done
        found.createdAt shouldBe micros
    }

    // restore は internal でこのソースセットから呼べないので、登録（登録時刻は時計の現在時刻）と操作で、保存する状態のタスクを作る
    private fun task(id: Long, due: LocalDate? = null, status: TaskStatus = TaskStatus.Done, createdAt: Instant = micros): Task {
        val registered = Task.register(taskId(id), title("牛乳を買う"), due?.let(::dueOf), Clock.fixed(createdAt, ZoneOffset.UTC))
        return when (status) {
            TaskStatus.Open -> registered
            TaskStatus.Done -> registered.complete()
        }
    }

    // マイクロ秒精度の時刻（読み戻しで同一になる）
    private val micros = Instant.parse("2026-11-01T01:00:00.123456Z")

    // ナノ秒を含む時刻。末尾が 999 なので、四捨五入・切り上げなら桁が繰り上がって区別できる
    private val nanos = Instant.parse("2026-11-01T01:00:00.123456999Z")

    @Test
    fun `払い出す番号は、呼ぶたびに前より大きくなる`() {
        val first = tasks.nextId()
        val second = tasks.nextId()
        val third = tasks.nextId()

        (second.value - first.value) shouldBeGreaterThan 0L
        (third.value - second.value) shouldBeGreaterThan 0L
    }

    @Test
    fun `保存したタスクを読み戻すと、期限あり・なしのどちらも同じである`() {
        val cases = mapOf(10L to LocalDate.of(2026, 11, 30), 11L to null)
        cases.forEach { (id, due) -> tasks.save(task(id, due = due)) }

        cases.forEach { (id, due) -> assertStored(tasks.find(taskId(id)).shouldNotBeNull(), id, due) }
        val all = tasks.findAll().associateBy { it.id.value }
        all.keys shouldBe cases.keys
        cases.forEach { (id, due) -> assertStored(all.getValue(id), id, due) }
    }

    @Test
    fun `登録時刻はマイクロ秒に切り捨てて保存される`() {
        tasks.save(task(20, createdAt = nanos))

        tasks.find(taskId(20)).shouldNotBeNull().createdAt shouldBe Instant.parse("2026-11-01T01:00:00.123456Z")
    }

    @Test
    fun `期限の日付は、サーバーの既定のタイムゾーンと暦の境目によらず、同じ日付で読み戻せる`() {
        val original = TimeZone.getDefault()
        try {
            listOf("UTC", "America/Los_Angeles", "Asia/Tokyo").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                // 両端と、グレゴリオ暦の採用の境目（ユリウス暦の 1582-10-04 の翌日が 10-15）
                listOf("0001-01-01", "1582-10-04", "1582-10-15", "2026-11-01", "9999-12-31").forEach { date ->
                    tasks.save(task(30, due = LocalDate.parse(date)))

                    tasks.find(taskId(30)).shouldNotBeNull().dueDate?.value shouldBe LocalDate.parse(date)
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `同じ識別子で保存すると上書きされる`() {
        tasks.save(task(40, status = TaskStatus.Open))

        tasks.save(task(40, status = TaskStatus.Done))

        tasks.find(taskId(40)).shouldNotBeNull().status shouldBe TaskStatus.Done
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

        shouldThrow<Exception> { tasks.find(taskId(1)) }
        shouldThrow<Exception> { tasks.findAll() }
    }

    @Test
    fun `完了済みの表示の選択は、保存するまで無い`() {
        visibilities.find().shouldBeNull()
    }

    @Test
    fun `完了済みの表示の選択は、保存した後に別の値で上書きでき、読み戻せる`() {
        visibilities.save(CompletedVisibility.HIDE)

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
