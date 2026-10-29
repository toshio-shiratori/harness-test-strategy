package fixtures

import org.junit.jupiter.api.Test

// UnrunTestDetectionTest の違反例: 層1の JUnit 5 の @Test。Jupiter のエンジンが無く、コンパイルは通るが黙って実行されない（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1JUnit5Test {
    @Test
    fun neverRuns() {
    }
}
