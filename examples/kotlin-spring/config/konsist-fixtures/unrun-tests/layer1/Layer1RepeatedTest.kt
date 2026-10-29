package fixtures

import org.junit.jupiter.api.RepeatedTest

// UnrunTestDetectionTest の違反例: 層1の @RepeatedTest。Jupiter のエンジンが無く、コンパイルは通るが黙って実行されない（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1RepeatedTest {
    @RepeatedTest(3)
    fun neverRuns() {
    }
}
