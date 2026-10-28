package com.example.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.nio.file.Files
import java.nio.file.Path

/**
 * 注文追加フォーム（`static/index.html`）の E2E。起動した Spring（RANDOM_PORT）と Testcontainers の MySQL に、
 * 実ブラウザ（Playwright の Chromium、ヘッドレス）でつなぐ。
 *
 * 待機は固定の sleep を使わない。Playwright の auto-wait と、自動再試行する `assertThat(...)` で
 * 結果欄の `data-state`（idle / sending / success / error）と `codes` を判定する。一過性の `sending` は検査しない。
 * 失敗をテストの再実行で隠さない（隔離か修復か破棄を決める）。メッセージ文言は仕様ではないので検査しない。
 * 失敗したテストだけ、トレースとスクリーンショットを `build/e2e-artifacts` に保存する(CI が成果物として保存する。待機・再試行の方針は変えない)。
 *
 * 注文追加は MySQL を読み書きしない（追加処理はログ出力のみ）。MySQL は、アプリケーションの起動に DataSource と
 * Flyway が必要なために立てており、フォームから DB までの往復を検証するものではない。
 *
 * 想定実行時間の固定費: Spring と MySQL コンテナの起動、ブラウザの起動はクラスで 1 回かかる(ウォームで約 8 秒。MySQL イメージ取得済みの
 * 手元の実測で、各テストは 0.1〜0.6 秒)。CI の初回実行(ブラウザキャッシュなし)では
 * `e2e` ジョブ全体で 2 分 1 秒(10 分予算の約 20%)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2eMySqlContainerConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFormE2eTest {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var context: BrowserContext
    private lateinit var page: Page

    @BeforeAll
    fun launchBrowser() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch() // 既定はヘッドレス
    }

    @AfterAll
    fun closeBrowser() {
        browser.close()
        playwright.close()
    }

    /** テスト本体が失敗したときだけ、閉じる前の画面とトレースを保存する(成功したテストは何も保存しない)。例外は握りつぶさず投げ直す。 */
    @JvmField
    @RegisterExtension
    final val saveArtifactsOnFailure = TestExecutionExceptionHandler { extensionContext, failure ->
        // 保存に失敗しても、元のテスト失敗を隠さない（保存の例外は抑制例外として付ける）
        runCatching { saveArtifacts(extensionContext) }.onFailure(failure::addSuppressed)
        throw failure
    }

    private fun saveArtifacts(extensionContext: ExtensionContext) {
        val name = extensionContext.requiredTestMethod.name.substringBefore(' ').replace(Regex("[^A-Za-z0-9-]"), "_")
        Files.createDirectories(ARTIFACTS_DIR)
        page.screenshot(Page.ScreenshotOptions().setPath(ARTIFACTS_DIR.resolve("$name.png")).setFullPage(true))
        context.tracing().stop(Tracing.StopOptions().setPath(ARTIFACTS_DIR.resolve("$name-trace.zip")))
    }

    @BeforeEach
    fun openForm() {
        context = browser.newContext()
        context.tracing().start(Tracing.StartOptions().setScreenshots(true).setSnapshots(true))
        page = context.newPage()
        page.navigate("http://localhost:$port/")
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    private fun fill(sku: String, quantity: String) {
        page.getByTestId("sku-input").fill(sku)
        page.getByTestId("quantity-input").fill(quantity)
    }

    private fun result() = page.getByTestId("result")

    private fun codes() = page.getByTestId("result-codes").locator("li")

    /**
     * 主要ハッピーパス: 入力して送信すると、結果欄が success になり HTTP 204 が表示される。
     *
     * - 領域: 主要ハッピーパス(注文に商品を追加するユーザーフローの正常系 1 本)
     * - 下層で代替できない理由: 入力欄の値の取得、`fetch`、応答に応じた `data-state` の遷移と結果欄の描画までは、
     *   ブラウザでしか通らない。サーバー側の振る舞い(204、追加処理の呼び出し)は下層の Integration が検証済みで、
     *   「追加処理が sku・数量で呼ばれる」は画面から観測できないため本テストでは検査しない。
     * - AC: AC-01-04
     * - 想定実行時間: 1 秒未満(ブラウザとアプリケーションの起動はクラスで 1 回)。
     *   不安定要因への対策: 待機は auto-wait と `data-state` の自動再試行 assertion のみ。外部ネットワークに依存しない。
     */
    @Test
    fun `AC-01-04 有効な SKU と数量を送信すると、結果欄が success になり HTTP 204 が表示される`() {
        assertThat(result()).hasAttribute("data-state", "idle")

        fill("SKU-1", "3")
        page.getByTestId("submit-button").click()

        assertThat(result()).hasAttribute("data-state", "success")
        assertThat(page.getByTestId("result-status")).hasText("204")
        assertThat(codes()).hasCount(0)
    }

    /**
     * 通信: ブラウザが入力値から JSON を組み立てて実配線のサーバーに POST し、サーバーの応答(400 と `codes`)が画面に戻る。
     * 送信した要求は、AC の Given(`{"sku":"SKU-1","quantity":1000}`)と一致する。
     *
     * - 領域: 通信(ブラウザ〜起動した Spring の実配線の疎通)
     * - 下層で代替できない理由: 入力欄の値からの JSON の組み立て(数量を数値として送ること)と、実際の HTTP 要求
     *   (メソッド・`Content-Type`・本文)は、ブラウザでしか生成されない。Integration は本文を手書きで組み立てるため、
     *   フォームが送る要求とサーバーの設定の整合は確認できない。範囲外の数量そのものの検証(境界値・PBT)は下層が検証済みで、
     *   ここでは「要求が届き、応答の `codes` が画面に戻る」往復だけを 1 件で見る。
     *   AC-01-12 のテストとは、見るものが重ならない(本テストは要求の捕捉と JSON の照合による「通信」、AC-01-12 は複数の
     *   `codes` の描画による「表示」)。
     * - AC: AC-01-09
     * - 想定実行時間: 1 秒未満。
     *   不安定要因への対策: 要求の捕捉は送信操作と同時に待ち受ける(`waitForRequest`)。応答は `data-state` で待つ。
     *   JSON のキーの順序は仕様ではないので、本文は JSON として比較する。
     */
    @Test
    fun `AC-01-09 範囲外の数量を送信すると、要求がサーバーに届き、結果欄に OUT_OF_RANGE が表示される`() {
        fill("SKU-1", "1000")
        val request = page.waitForRequest("**/orders/items") {
            page.getByTestId("submit-button").click()
        }

        request.method() shouldBe "POST"
        request.headerValue("content-type") shouldBe "application/json"
        JSONAssert.assertEquals("""{"sku":"SKU-1","quantity":1000}""", request.postData(), JSONCompareMode.STRICT)

        assertThat(result()).hasAttribute("data-state", "error")
        assertThat(page.getByTestId("result-status")).hasText("400")
        assertThat(codes()).hasCount(1)
        codes().allTextContents() shouldContainExactlyInAnyOrder listOf("OUT_OF_RANGE")
    }

    /**
     * 表示: 複数の違反が同時に成立したとき、結果欄が error になり、`codes` が 1 件ずつ別の項目として描画される。
     *
     * - 領域: 表示(実ブラウザでの描画と表示状態)
     * - 下層で代替できない理由: 応答の `codes` 配列を `<li>` に展開して結果欄に描画するスクリプトと、
     *   `data-state` による表示状態の切り替えは、DOM のあるブラウザでしか検証できない。AC の Then のうち、
     *   `codes` が BLANK と OUT_OF_RANGE の両方を含むこと(順不同)を画面で確かめる。`codes` を決めるロジックは下層で検証済み。
     *   「追加処理は呼ばれない」は画面から観測できないため検査しない。
     *   AC-01-09 のテストとは、見るものが重ならない(AC-01-09 は要求の捕捉と JSON の照合による「通信」、本テストは複数の
     *   `codes` の描画による「表示」)。
     * - AC: AC-01-12
     * - 想定実行時間: 1 秒未満。
     *   不安定要因への対策: 描画の完了は `hasCount(2)` の自動再試行で待つ。`codes` の順序は仕様ではないので順不同で比較する。
     */
    @Test
    fun `AC-01-12 SKU が空白で数量が範囲外のとき、結果欄が error になり codes が 2 件描画される`() {
        fill(" ", "1000")
        page.getByTestId("submit-button").click()

        assertThat(result()).hasAttribute("data-state", "error")
        assertThat(page.getByTestId("result-status")).hasText("400")
        assertThat(codes()).hasCount(2)
        codes().allTextContents() shouldContainExactlyInAnyOrder listOf("BLANK", "OUT_OF_RANGE")
    }

    private companion object {
        val ARTIFACTS_DIR: Path = Path.of("build", "e2e-artifacts")
    }
}
