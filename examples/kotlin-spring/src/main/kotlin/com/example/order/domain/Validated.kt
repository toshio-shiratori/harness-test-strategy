package com.example.order.domain

/** 検証結果。失敗を例外ではなく値として返し、呼び出し側に分岐を強制する。 */
sealed interface Validated<out T, out E> {
    data class Valid<out T>(val value: T) : Validated<T, Nothing>
    data class Invalid<out E>(val error: E) : Validated<Nothing, E>
}
