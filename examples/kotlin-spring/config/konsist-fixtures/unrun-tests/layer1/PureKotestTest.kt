package fixtures

import io.kotest.core.spec.style.StringSpec

// UnrunTestDetectionTest の適合例: 層1の Kotest の Spec は実行される（コンパイル対象ではない。検査関数が判定できることを確認する用）
class PureKotestTest : StringSpec({
    "runs" {
    }
})
