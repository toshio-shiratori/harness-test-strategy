package com.example.todo.adapter.persistence

import com.example.todo.domain.CompletedVisibility
import com.example.todo.domain.Task
import com.example.todo.domain.TaskId
import com.example.todo.domain.TaskStatus
import com.example.todo.domain.title
import com.example.todo.support.InMemoryTaskMapper
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * `MyBatisTaskRepository` を、メモリ上のマッパーで確かめる層1のテスト。Spring・DB は使わない。
 * 実 DB での保存・読み戻し・CHECK 制約は `TaskRepositoryIntegrationTest`（層2）が受け持つため、
 * ここでは Repository がマッパーへ委譲する振る舞いだけを確認する。層2の対応表は変えないため、テスト名に AC 番号は付けない。
 */
class MyBatisTaskRepositoryTest : StringSpec({
    val created = Instant.parse("2026-11-01T01:00:00Z")

    fun task(id: Long, status: TaskStatus = TaskStatus.Open) = Task(TaskId(id), title("牛乳を買う"), null, status, created)

    "番号は、採番表が払い出した番号をそのまま、呼ぶたびに返す" {
        val mapper = InMemoryTaskMapper()
        val repository = MyBatisTaskRepository(mapper)

        repository.nextId() shouldBe TaskId(1)
        repository.nextId() shouldBe TaskId(2)
        mapper.lastSequence shouldBe 2
    }

    "保存したタスクを、同じ ID で読み戻せる。一覧にも出る" {
        val repository = MyBatisTaskRepository(InMemoryTaskMapper())

        repository.save(task(1))
        repository.save(task(2, TaskStatus.Done))

        repository.find(TaskId(1)) shouldBe task(1)
        repository.find(TaskId(2)) shouldBe task(2, TaskStatus.Done)
        repository.findAll() shouldBe listOf(task(1), task(2, TaskStatus.Done))
    }

    "保存した行は、渡したタスクの ID をキーにマッパーへ届く" {
        val mapper = InMemoryTaskMapper()
        MyBatisTaskRepository(mapper).save(task(5))

        mapper.rows.keys shouldBe setOf(5L)
        mapper.upserted.map { it.status } shouldBe listOf("OPEN")
    }

    "保存されていない ID は null（なし）で、例外にならない" {
        val repository = MyBatisTaskRepository(InMemoryTaskMapper())
        repository.save(task(1))

        repository.find(TaskId(99)).shouldBeNull()
    }

    "未知の status の行は、読み込みが例外で失敗する" {
        val mapper = InMemoryTaskMapper()
        mapper.rows[1] = TaskRow(id = 1, title = "牛乳を買う", status = "UNKNOWN")
        val repository = MyBatisTaskRepository(mapper)

        shouldThrowAny { repository.find(TaskId(1)) }
        shouldThrowAny { repository.findAll() }
    }

    "完了済みの表示の選択は、保存されていなければ null で、保存した選択を読み戻せ、列の文字列で保存される" {
        val mapper = InMemoryTaskMapper()
        val repository = MyBatisTaskRepository(mapper)
        repository.find().shouldBeNull()

        repository.save(CompletedVisibility.HIDE)
        repository.find() shouldBe CompletedVisibility.HIDE
        repository.save(CompletedVisibility.SHOW)
        repository.find() shouldBe CompletedVisibility.SHOW

        mapper.visibilityWrites shouldBe listOf("HIDE", "SHOW")
    }

    "未知の表示の選択の列は、読み込みが例外で失敗する" {
        val mapper = InMemoryTaskMapper()
        mapper.visibility = "UNKNOWN"

        shouldThrowAny { MyBatisTaskRepository(mapper).find() }
    }
})
