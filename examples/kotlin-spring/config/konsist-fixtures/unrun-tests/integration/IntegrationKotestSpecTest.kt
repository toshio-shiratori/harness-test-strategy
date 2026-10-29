package fixtures

import io.kotest.core.spec.style.StringSpec

// UnrunTestDetectionTest の違反例: 統合テスト・E2E の Kotest の Spec。現在は kotest-framework が無くコンパイルが通らない。ランナーを足さずに依存だけ足されると、黙って実行されない（コンパイル対象ではない。検査関数が判定できることを確認する用）
class IntegrationKotestSpecTest : StringSpec({
    "neverRuns" {
    }
})
