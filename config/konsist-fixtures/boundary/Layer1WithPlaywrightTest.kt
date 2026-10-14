package fixtures

import com.microsoft.playwright.Playwright

// ExecutionBoundaryTest の違反例（コンパイル対象ではない）
class Layer1WithPlaywrightTest {
    fun launch() = Playwright.create()
}
