package com.example.e2e

import com.microsoft.playwright.APIRequest
import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.RequestOptions
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.testcontainers.mysql.MySQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId

/**
 * タスク管理の画面（`static/index.html`）の E2E。起動した Spring（RANDOM_PORT）と Testcontainers の MySQL に、
 * 実ブラウザ（Playwright の Chromium、ヘッドレス）でつなぐ。画面から API を通って DB に保存され、画面に戻る実配線を通す。
 *
 * 時計は実時間で動く（固定しない）。期限は「Asia/Tokyo の今日の 2 日前」のように相対で決める。2 日前にするのは、
 * テストとサーバーの日付の計算の間で日が変わっても、期限切れの判定が変わらないようにするため。
 *
 * 待機は固定の sleep を使わない。Playwright の auto-wait と、自動再試行する `assertThat(...)` で判定する。
 * 失敗をテストの再実行で隠さない（隔離か修復か破棄を決める）。
 * 失敗したテストだけ、トレースとスクリーンショットを `build/e2e-artifacts` に保存する（CI が成果物として保存する。待機・再試行の方針は変えない）。
 *
 * Given のデータ（一覧に既にあるタスク）は、原則として画面ではなく API で作る。成功は 2xx であることだけを確かめる（201 や 200 は実装の選択）。
 * 例外は AC-06-05 で、Given が「持ち主が画面で完了済みを隠してある」と画面の操作を求めるので、画面で隠す。
 * 各テストの前に、タスクと完了済みの表示の選択を DB から消す（テスト間の干渉を防ぐ）。
 *
 * 想定実行時間の固定費: Spring と MySQL コンテナの起動、ブラウザの起動はクラスで 1 回かかる。
 * ローカルのウォーム実行（macOS arm64、MySQL イメージと Chromium は取得済み）の実測で、クラス全体は 9〜10 秒、各テストは 0.13〜0.78 秒。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2eMySqlContainerConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskListE2eTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var mysql: MySQLContainer

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var api: APIRequestContext
    private lateinit var context: BrowserContext
    private lateinit var page: Page

    // 1 つのテストが複数の文脈を開くことがある（AC-04-05）。後始末ですべて閉じる
    private val openedContexts = mutableListOf<BrowserContext>()

    @BeforeAll
    fun launchBrowser() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch() // 既定はヘッドレス
        api = playwright.request().newContext(APIRequest.NewContextOptions().setBaseURL("http://localhost:$port"))
    }

    @AfterAll
    fun closeBrowser() {
        api.dispose()
        browser.close()
        playwright.close()
    }

    /** テスト本体が失敗したときだけ、閉じる前の画面とトレースを保存する（成功したテストは何も保存しない）。例外は握りつぶさず投げ直す。 */
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
    fun resetData() {
        mysql.createConnection("").use { connection ->
            connection.createStatement().use {
                it.executeUpdate("DELETE FROM task")
                it.executeUpdate("DELETE FROM completed_visibility")
            }
        }
    }

    @AfterEach
    fun closeContexts() {
        openedContexts.forEach(BrowserContext::close)
        openedContexts.clear()
    }

    /** ブラウザのタイムゾーンを [timezoneId] にした画面を開く。 */
    private fun openPage(timezoneId: String? = null) {
        val options = Browser.NewContextOptions()
        if (timezoneId != null) options.setTimezoneId(timezoneId)
        context = browser.newContext(options)
        openedContexts += context
        context.tracing().start(Tracing.StartOptions().setScreenshots(true).setSnapshots(true))
        page = context.newPage()
        page.navigate("http://localhost:$port/")
    }

    private fun titleInput() = page.getByTestId("title-input")

    private fun register(title: String) {
        titleInput().fill(title)
        page.getByTestId("register-button").click()
    }

    private fun task(title: String): Locator = page.locator("[data-testid='task'][data-title='$title']")

    private fun label(title: String) = task(title).getByTestId("task-label")

    /** Asia/Tokyo の今日の 2 日前（期限切れになる日付）。 */
    private fun pastDate(): String = LocalDate.now(ZoneId.of("Asia/Tokyo")).minusDays(2).toString()

    /** Given: API でタスクを作る。[done] が true なら、続けて完了にする。 */
    private fun givenTask(title: String, dueDate: String? = null, done: Boolean = false) {
        val body = if (dueDate == null) """{"title":"$title"}""" else """{"title":"$title","dueDate":"$dueDate"}"""
        val created = api.post("/tasks", RequestOptions.create().setHeader("Content-Type", "application/json").setData(body))
        created.ok() shouldBe true
        if (done) {
            val id = Regex(""""id"\s*:\s*(\d+)""").find(created.text())?.groupValues?.get(1)
            api.post("/tasks/$id/complete").ok() shouldBe true
        }
    }

    /**
     * 主要ハッピーパス: 画面でタイトルを入力して期限を選ばずに登録すると、一覧に現れる。
     *
     * - 領域: 主要ハッピーパス（`@e2e:happy-path`）。タスクを登録する主要な流れの正常系 1 本。
     * - 下層で代替できない理由: 入力欄の値の取得、`fetch` による登録、登録後の一覧の再取得と描画までは、ブラウザでしか通らない。
     *   検証の規則や保存の内容は下層（層1と Integration）が確かめ済みで、ここでは画面から DB を通って画面に戻る流れだけを見る。
     * - AC: AC-01-20
     * - 想定実行時間: 1 秒未満。不安定要因への対策: 待機は auto-wait と自動再試行する assertion のみ。外部ネットワークに依存しない。
     */
    @Test
    fun `AC-01-20 画面からタスクを登録できる`() {
        openPage()

        register("牛乳を買う")

        assertThat(task("牛乳を買う").getByTestId("task-title")).hasText("牛乳を買う")
    }

    /**
     * 実ブラウザ操作: 日付を選ぶ部品で期限を選んで登録すると、選んだ日付が `YYYY-MM-DD` で表示され、期限切れのラベルが付く。
     *
     * - 領域: 実ブラウザ操作（`@e2e:browser-operation`）。日付を選ぶ部品（`input type="date"`）の値の受け渡し。
     * - 下層で代替できない理由: 日付を選ぶ部品はブラウザの組み込みの部品で、その `value` が `YYYY-MM-DD` の形で要求に載り、
     *   戻った値が表示される配線は、実ブラウザでしか再現できない。操作は Playwright の `fill` で、部品の `value` を直接設定する
     *   （`input` と `change` のイベントは発火する。実測）。`YYYY-MM-DD` でない値は Playwright が拒否する（実測）。
     *   カレンダーのポップアップは操作しない。期限切れの判定そのものは層1が確かめ済み。
     * - AC: AC-01-21
     * - 想定実行時間: 1 秒未満。不安定要因への対策: 日付は Asia/Tokyo の今日の 2 日前で、日をまたいでも判定が変わらない。待機は auto-wait のみ。
     */
    @Test
    fun `AC-01-21 日付を選ぶ部品で期限を選んで登録できる`() {
        openPage()
        val date = pastDate()

        titleInput().fill("牛乳を買う")
        page.getByTestId("due-date-input").fill(date)
        page.getByTestId("register-button").click()

        assertThat(task("牛乳を買う").getByTestId("task-due")).hasText(date)
        assertThat(label("牛乳を買う")).hasText("期限切れ")
    }

    /**
     * 表示: 不正なタイトルの理由が、決められた文言で画面に表示される。
     *
     * - 領域: 表示（`@e2e:display`）。サーバーが返した理由コードを、画面が文言に写して描画する。
     * - 下層で代替できない理由: 応答の `codes` を文言に写して `<li>` に描画するスクリプトは、DOM のあるブラウザでしか検証できない。
     *   理由コードを決める検証は下層で確かめ済み。文言は代表の 2 件（`TITLE_BLANK`・`TITLE_TOO_LONG`）だけを見て、網羅は置かない（02c §5.2）。
     *   `DUE_DATE_INVALID` の文言は、日付を選ぶ部品が不正な日付を作れないので、ここでは確かめない。
     * - AC: AC-01-22
     * - 想定実行時間: 1 秒未満。不安定要因への対策: 描画は `hasText` の自動再試行で待つ。
     */
    @Test
    fun `AC-01-22 不正なタイトルの理由が画面に表示される`() {
        openPage()
        val cases = listOf("" to "タイトルを入力してください", "あ".repeat(51) to "タイトルは 50 文字以内で入力してください")

        cases.forEach { (title, message) ->
            register(title)

            assertThat(page.getByTestId("errors").locator("li")).hasText(message)
        }
    }

    /**
     * 表示: 画面から登録したタスクに「未完了」のラベルが表示される。
     *
     * - 領域: 表示（`@e2e:display`）。区分のラベルの描画。
     * - 下層で代替できない理由: 区分をラベルの文言として描画するのは、ブラウザでしか確かめられない。区分の判定は層1が確かめ済み。
     * - AC: AC-01-23
     * - 想定実行時間: 1 秒未満。不安定要因への対策: 描画は `hasText` の自動再試行で待つ。
     */
    @Test
    fun `AC-01-23 画面から登録したタスクに、未完了のラベルが表示される`() {
        openPage()

        register("牛乳を買う")

        assertThat(label("牛乳を買う")).hasText("未完了")
    }

    /**
     * 主要ハッピーパス: 画面で未完了のタスクを完了にすると、「完了」のラベルが表示される。
     *
     * - 領域: 主要ハッピーパス（`@e2e:happy-path`）。タスクを完了にする主要な流れの正常系。
     * - 下層で代替できない理由: ボタンの操作から API を通って DB に保存され、画面に戻る流れは、ブラウザと実配線でしか通らない。
     *   状態遷移の規則と保存の内容は下層が確かめ済み。
     * - AC: AC-02-05
     * - 想定実行時間: 1 秒未満。不安定要因への対策: 待機は auto-wait と自動再試行する assertion のみ。
     */
    @Test
    fun `AC-02-05 画面からタスクを完了にできる`() {
        givenTask("牛乳を買う")
        openPage()

        task("牛乳を買う").getByTestId("complete-button").click()

        assertThat(label("牛乳を買う")).hasText("完了")
    }

    /**
     * 主要ハッピーパス: 画面で完了のタスクを未完了に戻すと、「未完了」のラベルが表示される。
     *
     * - 領域: 主要ハッピーパス（`@e2e:happy-path`）。誤って完了にしたタスクを戻す流れの正常系。
     * - 下層で代替できない理由: AC-02-05 と同じ。ボタンの操作から DB を通って画面に戻る流れは、ブラウザと実配線でしか通らない。
     * - AC: AC-03-07
     * - 想定実行時間: 1 秒未満。不安定要因への対策: 待機は auto-wait と自動再試行する assertion のみ。
     */
    @Test
    fun `AC-03-07 画面からタスクを未完了に戻せる`() {
        givenTask("牛乳を買う", done = true)
        openPage()

        task("牛乳を買う").getByTestId("reopen-button").click()

        assertThat(label("牛乳を買う")).hasText("未完了")
    }

    /**
     * 表示: 一覧で、未完了・完了・期限切れを、ブラウザのタイムゾーンによらず見分けられる。
     *
     * - 領域: 表示（`@e2e:display`）。3 つの区分のラベルの描画と、ブラウザのタイムゾーンに依存しないこと。
     * - 下層で代替できない理由: ブラウザのタイムゾーンが描画に影響しないことは、ブラウザでしか確かめられない。
     *   区分の判定は、サーバーが Asia/Tokyo の日付で行う（層1と Integration が確かめ済み）。画面は、サーバーが返した区分を表示するだけである。
     * - AC: AC-04-05
     * - 想定実行時間: タイムゾーンごとに 1 秒未満（2 回）。不安定要因への対策: 期限は Asia/Tokyo の今日の 2 日前で、日をまたいでも判定が変わらない。
     */
    @Test
    fun `AC-04-05 一覧で、未完了と完了と期限切れを見分けられる`() {
        givenTask("A")
        givenTask("B", dueDate = pastDate(), done = true)
        givenTask("C", dueDate = pastDate())

        listOf("Asia/Tokyo", "America/Los_Angeles").forEach { timezoneId ->
            openPage(timezoneId)

            assertThat(label("A")).hasText("未完了")
            assertThat(label("B")).hasText("完了")
            assertThat(label("C")).hasText("期限切れ")
        }
    }

    /**
     * 通信: 画面で完了済みを隠すと、サーバーと DB に保存され、画面を再読み込みしても隠れたままである。
     *
     * - 領域: 通信（`@e2e:communication`）。画面での選択が、サーバーと DB を経て、再読み込み後の画面に戻る往復。
     * - 下層で代替できない理由: 選択の保存と読み戻し、保存された選択による絞り込みは Integration が確かめ済み。
     *   画面の操作が API に届き、再読み込み後に画面の状態へ戻る実配線は、ブラウザでしか通らない。
     * - AC: AC-06-05
     * - 想定実行時間: 1 秒未満。不安定要因への対策: 切り替えの完了は、一覧から完了済みが消えるまで自動再試行で待ち、その後に再読み込みする。
     *   再読み込みの後は、未完了のタスクが現れるまで待ってから、完了済みが無いことを確かめる。
     */
    @Test
    fun `AC-06-05 画面で完了済みを隠すと、画面を開き直しても隠れたままである`() {
        givenTask("B", done = true)
        givenTask("A")
        openPage()
        assertThat(task("A")).isVisible()
        assertThat(task("B")).isVisible()
        page.getByTestId("hide-completed-toggle").check()
        assertThat(task("B")).hasCount(0) // 保存と一覧の取り直しが終わった

        page.reload()

        assertThat(task("A").getByTestId("task-title")).hasText("A")
        assertThat(task("B")).hasCount(0)
        // 前回の選択が保たれている（UC-06 の基本フロー 4）
        assertThat(page.getByTestId("hide-completed-toggle")).isChecked()
    }

    private companion object {
        val ARTIFACTS_DIR: Path = Path.of("build", "e2e-artifacts")
    }
}
