package fixtures

import org.junit.jupiter.api.Test as JTest

// UnrunTestDetectionTest の違反例: 別名で import した @Test（別名の注釈でも import 文で検出する）（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1AliasImportTest {
    @JTest
    fun neverRuns() {
    }
}
