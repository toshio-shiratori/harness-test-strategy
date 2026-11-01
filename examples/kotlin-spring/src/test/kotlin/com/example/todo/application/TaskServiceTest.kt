package com.example.todo.application

import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.TaskCategory
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskStatus
import com.example.todo.domain.due
import com.example.todo.domain.title
import com.example.todo.support.InMemoryRepositories
import com.example.todo.support.SteppingClock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

/**
 * ユースケースを、メモリ上のリポジトリと進められる時計で確かめる層1のテスト（Spring・DB は使わない）。
 * TRS の AC に紐づく振る舞いの規則は、ドメイン層のテスト（`TaskTest` など）と Integration（`TaskApiHttpTest` など）が受け持つ。
 * ここは、ユースケースが番号・時計・保存を正しく組み合わせることを見る技術的な検証で、層2の対応表は変えないためテスト名に AC 番号を付けない。
 */
class TaskServiceTest : StringSpec({
    class Fixture {
        val repositories = InMemoryRepositories()
        val clock = SteppingClock()
        val service = TaskService(repositories, repositories, clock)
    }

    "登録すると、払い出された番号と時計の現在時刻で、未完了のタスクが保存される" {
        val f = Fixture()

        val registered = f.service.register(title("牛乳を買う"), due("2026-11-30"))

        registered.task.id shouldBe TaskId(1)
        registered.task.createdAt shouldBe f.clock.instant()
        registered.task.status shouldBe TaskStatus.Open
        registered.category shouldBe TaskCategory.Open
        f.repositories.find(TaskId(1)) shouldBe registered.task
    }

    "登録のたびに、番号は増える" {
        val f = Fixture()

        val ids = (1..3).map { f.service.register(title("t$it"), null).task.id }

        ids shouldBe listOf(TaskId(1), TaskId(2), TaskId(3))
    }

    "登録した応答の区分は、現在時刻で決まる（期限切れ）" {
        val f = Fixture()

        f.service.register(title("a"), due("2026-10-31")).category shouldBe TaskCategory.Overdue
    }

    "完了にすると、保存された状態が完了になる。再実行しても同じ" {
        val f = Fixture()
        val id = f.service.register(title("a"), null).task.id

        f.service.complete(id)?.category shouldBe TaskCategory.Done
        f.repositories.find(id)?.status shouldBe TaskStatus.Done
        f.service.complete(id)?.category shouldBe TaskCategory.Done
        f.repositories.find(id)?.status shouldBe TaskStatus.Done
    }

    "未完了に戻すと、保存された状態が未完了になる。再実行しても同じ" {
        val f = Fixture()
        val id = f.service.register(title("a"), null).task.id
        f.service.complete(id)

        f.service.reopen(id)?.category shouldBe TaskCategory.Open
        f.repositories.find(id)?.status shouldBe TaskStatus.Open
        f.service.reopen(id)?.category shouldBe TaskCategory.Open
        f.repositories.find(id)?.status shouldBe TaskStatus.Open
    }

    "存在しないタスクの完了と未完了に戻す操作は null で、何も保存しない" {
        val f = Fixture()
        f.service.register(title("a"), null)

        f.service.complete(TaskId(99)).shouldBeNull()
        f.service.reopen(TaskId(99)).shouldBeNull()
        f.repositories.findAll().size shouldBe 1
    }

    "完了にする操作は、ほかのタスクとタイトル・期限を変えない" {
        val f = Fixture()
        val target = f.service.register(title("a"), due("2026-11-30")).task
        val other = f.service.register(title("b"), null).task

        f.service.complete(target.id)

        f.repositories.find(target.id) shouldBe target.copy(status = TaskStatus.Done)
        f.repositories.find(other.id) shouldBe other
    }

    "一覧は、選択が保存されていなければ完了済みも表示し、期限が近い順に並ぶ" {
        val f = Fixture()
        val late = f.service.register(title("late"), due("2026-12-01")).task
        f.clock.advance(Duration.ofSeconds(1))
        val early = f.service.register(title("early"), due("2026-11-10")).task
        f.service.complete(late.id)

        f.service.completedVisibility() shouldBe CompletedVisibility.SHOW
        f.service.list().map { it.task.id } shouldBe listOf(early.id, late.id)
    }

    "完了済みを隠す選択を保存すると、一覧から完了済みが除かれ、選択を読み戻せる" {
        val f = Fixture()
        val done = f.service.register(title("done"), null).task
        val open = f.service.register(title("open"), null).task
        f.service.complete(done.id)

        f.service.changeCompletedVisibility(CompletedVisibility.HIDE)

        f.service.completedVisibility() shouldBe CompletedVisibility.HIDE
        f.service.list().map { it.task.id } shouldBe listOf(open.id)
    }
})
