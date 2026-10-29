package fixtures

// UnrunTestDetectionTest の違反例: import せず完全修飾名で付けた @Test。Jupiter のエンジンが無く黙って実行されない（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1QualifiedAnnotationTest {
    @org.junit.jupiter.api.Test
    fun neverRuns() {
    }
}
