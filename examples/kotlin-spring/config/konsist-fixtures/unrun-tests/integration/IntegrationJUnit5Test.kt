package fixtures

import org.junit.jupiter.api.Test

// UnrunTestDetectionTest の適合例: 統合テスト・E2E の JUnit 5 の @Test は Jupiter のエンジンで実行される（コンパイル対象ではない。検査関数が判定できることを確認する用）
class IntegrationJUnit5Test {
    @Test
    fun runs() {
    }
}
