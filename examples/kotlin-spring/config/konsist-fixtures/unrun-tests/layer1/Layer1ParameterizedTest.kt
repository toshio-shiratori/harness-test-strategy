package fixtures

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

// UnrunTestDetectionTest の違反例: 層1の @ParameterizedTest。現在は junit-jupiter-params が無くコンパイルが通らない。依存が足されたとき、エンジンが無いまま黙って 0 件になるのを防ぐ（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1ParameterizedTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun neverRuns(n: Int) {
    }
}
