package com.example.todo.adapter.persistence

import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/** 採番表に 1 行入れたときに、DB が払い出した番号を受け取る入れ物。MyBatis が書き込むため var にしている。 */
class IdHolder(var id: Long = 0)

/** MyBatis のマッパー。SQL は注釈で書く（カラム名は snake_case、`map-underscore-to-camel-case` で行の property に対応させる）。 */
@Mapper
interface TaskMapper {
    // 採番専用の表の AUTO_INCREMENT で、登録ごとに増える番号を払い出す（MySQL に SEQUENCE は無い）。使った番号は戻らない
    @Insert("INSERT INTO task_id_sequence () VALUES ()")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insertSequence(@Param("holder") holder: IdHolder): Int

    // 同じ ID は上書きする
    @Insert(
        """
        INSERT INTO task (id, title, due_date, status, created_at)
        VALUES (#{row.id}, #{row.title}, #{row.dueDate}, #{row.status}, #{row.createdAt})
        ON DUPLICATE KEY UPDATE
            title = VALUES(title),
            due_date = VALUES(due_date),
            status = VALUES(status),
            created_at = VALUES(created_at)
        """,
    )
    fun upsert(@Param("row") row: TaskRow)

    @Select("SELECT id, title, due_date, status, created_at FROM task WHERE id = #{id}")
    fun findById(@Param("id") id: Long): TaskRow?

    @Select("SELECT id, title, due_date, status, created_at FROM task")
    fun findAll(): List<TaskRow>

    @Select("SELECT visibility FROM completed_visibility WHERE id = 1")
    fun findVisibility(): String?

    @Insert(
        """
        INSERT INTO completed_visibility (id, visibility) VALUES (1, #{visibility})
        ON DUPLICATE KEY UPDATE visibility = VALUES(visibility)
        """,
    )
    fun upsertVisibility(@Param("visibility") visibility: String)
}
