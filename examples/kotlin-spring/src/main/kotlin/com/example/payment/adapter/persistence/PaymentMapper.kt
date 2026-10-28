package com.example.payment.adapter.persistence

import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

/** MyBatis のマッパー。SQL は注釈で書く（カラム名は snake_case、`map-underscore-to-camel-case` で行の property に対応させる）。 */
@Mapper
interface PaymentMapper {
    // 同じ ID は上書きする（PAY-002 AC-03-06）。遷移ルールの強制はしない
    @Insert(
        """
        INSERT INTO payment (id, status, authorized_at, captured_at, failure_reason)
        VALUES (#{row.id}, #{row.status}, #{row.authorizedAt}, #{row.capturedAt}, #{row.failureReason})
        ON DUPLICATE KEY UPDATE
            status = VALUES(status),
            authorized_at = VALUES(authorized_at),
            captured_at = VALUES(captured_at),
            failure_reason = VALUES(failure_reason)
        """,
    )
    fun upsert(@Param("row") row: PaymentRow)

    @Select("SELECT id, status, authorized_at, captured_at, failure_reason FROM payment WHERE id = #{id}")
    fun findById(@Param("id") id: String): PaymentRow?
}
