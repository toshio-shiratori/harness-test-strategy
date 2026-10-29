package fixtures

import org.junit.Test

// UnrunTestDetectionTest の違反例: JUnit 4 の @Test。現在は junit:junit が無くコンパイルが通らない。依存が足されても Vintage のエンジンが無く、黙って実行されない（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1JUnit4Test {
    @Test
    fun neverRuns() {
    }
}
