package fixtures

import org.junit.jupiter.api.*

// UnrunTestDetectionTest の違反例: スター import と、同じ行に書いた @Test（単純名の注釈で検出する）（コンパイル対象ではない。検査関数が判定できることを確認する用）
class Layer1StarImportTest {
    @Test fun neverRuns() {}
}
