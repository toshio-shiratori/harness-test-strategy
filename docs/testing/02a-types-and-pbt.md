# 02a. 型設計とPBT（AIガードレール＆テストアーキテクチャ標準 1/3）

| 項目 | 内容 |
|---|---|
| 文書ID | TEST-02a |
| 目的 | AIが生成するコードの誤りを「書けない」「すぐ落ちる」状態にするための技術標準のうち、型設計と PBT を定める |
| 前提 | [01 基本方針書](./01-test-policy.md) の層1（最速・確定層）を中心に規定する |
| 関連 | [05 導入手順書](./05-adoption-guide.md)（既存プロジェクトへ導入するときの診断と段階導入） |

| ファイル | 扱う節 | 内容 | 分けた理由 |
|---|---|---|---|
| [02a 型設計とPBT](./02a-types-and-pbt.md)（本書） | §0 技術前提、§1 型設計、§2 PBT | 誤りを書けなくする型設計と、不変条件を検査する PBT の標準 | ドメインのコード例が中心で、型と PBT を続けて読む |
| [02b 層1の実行境界とミューテーション](./02b-execution-and-mutation.md) | §3 層1の構成と実行境界、§4 ミューテーション、§6 AI向けガードレール設定 | テストをいつ・どこで・どの強度で回すか、それを機械的に強制する設定 | ビルド・CI の設定を変えるときに参照する。§6 は、ビルド・CI で機械的に強制する設定（コンパイラ・detekt・ktlint・Konsist）を扱うため、§3・§4 と同じくここに置く |
| [02c E2E 最小化原則](./02c-e2e.md) | §5 E2E 最小化原則 | E2E の許可領域・審査・予算 | 独立した運用規則で、他の節と別に編集できるようにする |

節番号は分割前の `02` の番号をそのまま使う（02b は第5節が欠番で、02c は第5節のみ）。参照は `02a §2.3` のように書く。

> コード例は **Kotlin + Spring Boot 4.1.x** で示す。原則そのものは言語非依存である。

## 0. 技術前提（コード例のベースライン）

| 項目 | 前提 |
|---|---|
| Spring Boot | 4.1.x（Spring Framework 7.0.x、Jakarta EE 11） |
| Kotlin | 2.4 系（Spring Boot 4.1 の BOM は 2.3 系を基準とするが、2.4 系で動作確認済み。`kotlin-reflect` / `kotlin-stdlib` は Kotlin プラグインと同じ版に揃うことを依存解決で確認） |
| JDK | 21（`jvmToolchain(21)`。CI も 21） |
| ビルド | Gradle 9 系（Kotlin DSL） |
| JSON | Jackson 3（パッケージは `tools.jackson.*`。ただし `jackson-annotations` は `com.fasterxml.jackson.annotation` のまま） |
| テスト | JUnit Platform + Kotest（Property / Assertions）。Spring を起動するテスト（`@WebMvcTest`・`@SpringBootTest`・DB 統合）は `src/integrationTest`、実ブラウザで操作する E2E は `src/e2eTest`（[02b §3.2](./02b-execution-and-mutation.md#32-層1統合テストe2e-の実行境界)） |
| モック | `@MockitoBean` / `@MockitoSpyBean`（`org.springframework.test.context.bean.override.mockito`。Spring Boot 3 の `@MockBean` / `@SpyBean` は廃止済み） |

表のバージョンは系統（minor）で書いている。正確な版は `build.gradle.kts` と Gradle wrapper（`gradle/wrapper/gradle-wrapper.properties`）を正とする。

> Spring Boot 4 では自動構成とテスト支援が機能別モジュールに分割された。4.1.1 で次を実測確認済み（`src/integrationTest/.../adapter/web/OrderControllerHttpTest.kt`）。
>
> | 用途 | 内容 |
> |---|---|
> | テスト用スターター | `org.springframework.boot:spring-boot-starter-webmvc-test` |
> | `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
> | `@AutoConfigureRestTestClient` | `org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient` |
> | `RestTestClient` | `org.springframework.test.web.servlet.client.RestTestClient`（Spring Framework 7） |
> | `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`（存在のみ確認。本リポジトリでは未使用） |
>
> 本書のコード例は、ドメイン層を Spring 非依存の純粋な Kotlin とすることで、フレームワークの変更に影響されない構成にしている。

```kotlin
// build.gradle.kts（抜粋。バージョンは例であり、実際の版は検証用プロジェクトのルートにある build.gradle.kts を正とする）
plugins {
    kotlin("jvm") version "2.4.21"             // BOM（2.3 系）より新しい。動作確認済み
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.1"   // 4.1.x の最新パッチを使う
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)          // 警告（冗長な else 等）をエラー化
    }
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.kotest:kotest-runner-junit5:6.2.5")
    testImplementation("io.kotest:kotest-assertions-core:6.2.5")
    testImplementation("io.kotest:kotest-property:6.2.5")
}

tasks.withType<Test> { useJUnitPlatform() }
```

> 本書の §1〜§2 のコード例は、リポジトリ直下の検証用プロジェクト（`src/`）に同一内容で置いてあり、`./gradlew test` でコンパイルとテストを実行できる（CI: `.github/workflows/kotlin-samples.yml`）。コード例を変更するときは `src/` 側も同時に更新すること。

---

## 1. 「誤りを書けなくする」型設計

テストで誤りを検出するより、**誤りがコンパイルできない**状態を優先する。AI は型エラーを即座にフィードバックとして受け取り自己修復できるため、型は最も安価で強力なガードレールである。

### 1.1 プリミティブ型の排除（ドメイン型 / Value Object 化）

ドメインの意味を持つ値に `String` / `Int` / `Long` / `Boolean` を直接使わない。

| 禁止 | 推奨 |
|---|---|
| `userId: String` | `userId: UserId` |
| `amount: Long` | `amount: Yen`（単位・精度・上限を内包） |
| `email: String` | `email: EmailAddress` |
| `status: String` | `status: OrderStatus`（sealed interface） |

Kotlin では `@JvmInline value class` を使うと、実行時のオブジェクト生成コストを抑えつつ、コンパイル時に型を区別できる。

```kotlin
package com.example.order.domain

// 異なる意味の String を取り違えられなくする（ブランド型相当）
@JvmInline
value class UserId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Validated<UserId, IdError> =
            if (raw.isBlank()) Validated.Invalid(IdError.BLANK) else Validated.Valid(UserId(raw))
    }
}

@JvmInline
value class OrderId private constructor(val value: String) {
    companion object {
        fun of(raw: String): Validated<OrderId, IdError> =
            if (raw.isBlank()) Validated.Invalid(IdError.BLANK) else Validated.Valid(OrderId(raw))
    }
}

enum class IdError { BLANK }

fun cancelOrder(orderId: OrderId, by: UserId) { /* ... */ }
// cancelOrder(userId, orderId)  // ← コンパイルエラー（引数の取り違えを排除）
```

**ルール**

- API 境界（外部入力）で一度だけパースし、以降は内部にドメイン型のみを流通させる（Parse, don't validate）。
- ドメイン型の生成は `private constructor` + ファクトリ経由に限定する。`as` による強制キャストで型を偽装してはならない。
- `!!`（非 null アサーション）、根拠のない `as`、`@Suppress`、`lateinit` の乱用は静的解析（detekt）で禁止する。
- Spring の `@RequestBody` 等で受ける DTO はプリミティブでよい。ただし、**DTO はアダプタ層に閉じ込め**、ユースケースへ渡す前にドメイン型へ変換する（§1.2 の例を参照）。

### 1.2 コンストラクタ防御

不正な値でオブジェクトが**存在できない**ようにする。

```kotlin
package com.example.order.domain

/** 検証結果。失敗を例外ではなく値として返し、呼び出し側に分岐を強制する。 */
sealed interface Validated<out T, out E> {
    data class Valid<out T>(val value: T) : Validated<T, Nothing>
    data class Invalid<out E>(val error: E) : Validated<Nothing, E>
}

enum class QuantityError { OUT_OF_RANGE }

@JvmInline
value class Quantity private constructor(val value: Int) {
    companion object {
        const val MIN = 1
        const val MAX = 999

        fun of(value: Int): Validated<Quantity, QuantityError> =
            if (value in MIN..MAX) Validated.Valid(Quantity(value))
            else Validated.Invalid(QuantityError.OUT_OF_RANGE)
    }
}
```

アダプタ層（Spring MVC）では、DTO を境界で一度だけドメイン型へ変換し、失敗はそのまま HTTP 400 に写像する。

```kotlin
package com.example.order.adapter.web

import com.example.order.domain.Quantity
import com.example.order.domain.Sku
import com.example.order.domain.Validated
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 外部入力の形そのもの。ドメインの意味は持たず、アダプタ層から外へ出さない。
 * 欠落・null は Jackson に任せず、null として受けてコントローラーで MISSING_FIELD にする。
 */
data class AddItemRequest(val sku: String?, val quantity: Int?)

@RestController
@RequestMapping("/orders")
class OrderController(private val addItem: AddItemUseCase) {

    @PostMapping("/items")
    fun add(@RequestBody request: AddItemRequest): ResponseEntity<*> {
        if (request.sku == null || request.quantity == null) {
            val missing = problem(listOf(ErrorCode.MISSING_FIELD), "必須フィールドが指定されていません")
            return ResponseEntity.badRequest().body(missing)
        }
        val sku = Sku.of(request.sku)
        val quantity = Quantity.of(request.quantity)
        // 検証段階の失敗は、同時に成立していれば全て返す
        return if (sku is Validated.Valid && quantity is Validated.Valid) {
            addItem.execute(sku.value, quantity.value)
            ResponseEntity.noContent().build<Unit>()
        } else {
            val codes = listOfNotNull(
                ErrorCode.BLANK.takeIf { sku is Validated.Invalid },
                ErrorCode.OUT_OF_RANGE.takeIf { quantity is Validated.Invalid },
            )
            val detail = "sku は空にできず、quantity は ${Quantity.MIN}〜${Quantity.MAX} の範囲で指定してください"
            ResponseEntity.badRequest().body(problem(codes, detail))
        }
    }
}

enum class ErrorCode { OUT_OF_RANGE, BLANK, INVALID_JSON, MISSING_FIELD }

const val CODES_PROPERTY = "codes"

internal fun problem(codes: List<ErrorCode>, detail: String): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail).also {
        it.setProperty(CODES_PROPERTY, codes.map(ErrorCode::name))
    }

fun interface AddItemUseCase {
    fun execute(sku: Sku, quantity: Quantity)
}
```

コントローラーに到達する前に Spring が落とす 400（壊れた JSON・型不一致）は `@RestControllerAdvice` で同じ `codes` 形式にそろえる。

```kotlin
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onUnreadable(): ProblemDetail = problem(listOf(ErrorCode.INVALID_JSON), "リクエスト本文を読み取れません")
}
```

> 400 応答は `detail` の文言ではなく、機械可読な `codes`（集合として扱い、順序は仕様にしない）だけを契約とする（TRS ORD-001 OI-003, OI-006）。
>
> `"3"` や `1.5` を `Int` に黙って変換しないよう、`application.properties` で次を指定している。
>
> - `spring.jackson.mapper.allow-coercion-of-scalars=false`
> - `spring.jackson.deserialization.accept-float-as-int=false`
>
> 指定しないと 204 になることを `OrderControllerHttpTest` で実測した。分岐は `if` の式で書き、`Validated` の判定は smart cast に任せている。

**ルール**

- コンストラクタは `private` とし、検証付きファクトリ（`of` / `parse`）経由でのみ生成する。
- 検証は生成時の1箇所に集約し、利用側で再検証しない（二重管理の防止）。
- 不変（immutable）を基本とし、状態変更は新しい値の生成で表現する（`val` のみ、`var` を避ける）。
- 失敗は例外ではなく `Validated`（または `Result` 相当）で返し、呼び出し側に処理を強制する。Spring の `@Valid` / Bean Validation による検証は DTO の形式チェックに留め、**ドメインの不変条件はドメイン型のファクトリを正**とする。
- value class を DTO に直接デシリアライズする構成は避け、DTO はプリミティブで受けて境界で変換する。**実測**（Spring Boot 4.1.1、Jackson 3 + jackson-module-kotlin）では、`private constructor` の `Quantity` を DTO のプロパティにすると `{"quantity":5000}` が `Quantity.of` の検証を素通りして生成された（`ValueClassDtoTest`）。不変条件が破られるため、DTO にドメイン型を直接使ってはならない。

### 1.3 sealed 型と網羅的 `when`（網羅性チェック）

状態・種別・イベントは `sealed interface`（または `sealed class`）で表現し、分岐の網羅性をコンパイラに検査させる。Kotlin では **`when` を式として使い、`else` を書かない**ことが、TypeScript の `never` による網羅性チェックに相当する。

```kotlin
package com.example.payment.domain

import java.time.Instant

sealed interface PaymentState {
    data object Pending : PaymentState
    data class Authorized(val authorizedAt: Instant) : PaymentState
    data class Captured(val capturedAt: Instant) : PaymentState
    data class Failed(val reason: FailureReason) : PaymentState
}

enum class FailureReason { CARD_DECLINED, TIMEOUT }

// when を「式」として使い、else を置かない。
// PaymentState に状態を追加すると、この関数を含む全ての when がコンパイルエラーになる。
fun label(state: PaymentState): String =
    when (state) {
        PaymentState.Pending -> "保留"
        is PaymentState.Authorized -> "与信済"
        is PaymentState.Captured -> "確定"
        is PaymentState.Failed -> "失敗"
    }
```

遷移も同じ作りで書く。許可する遷移（TRS PAY-001）以外は、同じ状態への再実行を含めて全て `INVALID_TRANSITION` で拒否し、失敗は例外ではなく `Validated` で返す。

```kotlin
enum class TransitionError { INVALID_TRANSITION }

/** 確定。Authorized だけが Captured になれる（与信を飛ばした確定は不可）。時刻は注入された Clock から取る。 */
fun PaymentState.capture(clock: Clock): Validated<PaymentState, TransitionError> =
    when (this) {
        is PaymentState.Authorized -> Validated.Valid(PaymentState.Captured(clock.instant()))
        PaymentState.Pending, is PaymentState.Captured, is PaymentState.Failed ->
            Validated.Invalid(TransitionError.INVALID_TRANSITION)
    }
// authorize / fail も同様（src/main/kotlin/com/example/payment/domain/PaymentTransitions.kt）
```

**ルール**

- `status: String` と `Boolean` の組合せで状態を表現しない（`isPaid && isCancelled` のような**矛盾状態を表現可能にしない**）。
- 状態ごとに必要なデータを各バリアントに持たせ、不正な組合せを型で排除する（`Captured` だけが `capturedAt` を持つ）。
- `when` の `else` 分岐を禁止する（sealed / enum 対象）。`else` は将来の状態追加を黙って飲み込む。
- sealed / enum を対象にした `when` は、式でも文でも非網羅だとコンパイルエラーになる（Kotlin 1.7 以降）。加えて `allWarningsAsErrors` を有効にし、冗長な `else` 等の警告も必ずビルドエラーにする（§0）。
- 外部から来る文字列（DB・JSON）は、境界で enum / sealed 型へ変換する。未知の値は `Invalid` として扱い、`else` で握りつぶさない。

### 1.4 型ガードレールのチェックリスト（レビュー観点）

- [ ] ドメイン概念にプリミティブ型が露出していないか（Konsist で自動検査: [02b §6](./02b-execution-and-mutation.md#6-ai向けガードレール設定実装の拠り所)）
- [ ] 不正な値・不正な状態の組合せが型として表現可能になっていないか
- [ ] sealed / enum の `when` が式で網羅され、`else` が置かれていないか（Konsist で自動検査: [02b §6](./02b-execution-and-mutation.md#6-ai向けガードレール設定実装の拠り所)）
- [ ] `!!` / 無根拠な `as` / `@Suppress` / `lateinit` が増えていないか
- [ ] 外部入力の検証が境界の1箇所に集約されているか

---

## 2. PBT（Property-Based Testing）標準

単一値の例示テスト（Example-Based）だけに依存しない。システムが**常に満たすべき不変条件（Property）**を記述し、多数のランダム入力で検証する。

### 2.1 PBT を必須とする対象

| 対象 | 例 |
|---|---|
| 入力空間が広い純粋関数 | 金額計算、日付計算、文字列正規化 |
| 変換の往復 | シリアライズ/デシリアライズ、エンコード/デコード |
| 状態遷移 | 任意の操作列の後でも不変条件が保たれること |
| ドメイン型のファクトリ | 受理した値は常に不変条件を満たす |

### 2.2 Property の型（定義基準）

Property は次の分類のいずれかで記述し、テスト名またはコメントに分類を明示する。

| 分類 | 内容 | 例 |
|---|---|---|
| ラウンドトリップ | `decode(encode(x)) == x` | JSON 変換、URL エンコード |
| 冪等性 | `f(f(x)) == f(x)` | 正規化、重複排除 |
| 不変量 | 操作後も保たれる性質 | 「在庫数は負にならない」「合計 = 明細の和」 |
| 交換則・結合則 | 順序や括りに依存しない | 金額の加算 |
| オラクル | 単純で明らかに正しい実装と一致 | 最適化版と素朴版の結果一致 |
| 単調性・境界 | 入力増に対する出力の方向 | 割引後価格 ≤ 割引前価格 |

Kotest Property（`io.kotest:kotest-property`）での記述例。ドメイン型とジェネレータ（`Arb`）を対にして管理する。

```kotlin
package com.example.order.domain

@JvmInline
value class Yen private constructor(val amount: Long) {
    operator fun plus(other: Yen): Yen = Yen(Math.addExact(amount, other.amount))

    companion object {
        const val MAX = 1_000_000_000L

        fun of(amount: Long): Validated<Yen, YenError> =
            if (amount in 0..MAX) Validated.Valid(Yen(amount)) else Validated.Invalid(YenError.OUT_OF_RANGE)
    }
}

enum class YenError { OUT_OF_RANGE }
```

```kotlin
package com.example.order.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

// ドメイン型ごとに Arb を 1 つ用意し、ファクトリの検証と同じ制約を満たす値だけを生成する
val arbYen: Arb<Yen> = Arb.long(0L..Yen.MAX / 2).map { (Yen.of(it) as Validated.Valid).value }

class YenPropertyTest : StringSpec({

    // 分類: 交換則・結合則
    "金額の加算は交換則を満たす" {
        checkAll(arbYen, arbYen) { a, b -> (a + b) shouldBe (b + a) }
    }

    "金額の加算は結合則を満たす" {
        checkAll(arbYen, arbYen, arbYen) { a, b, c -> ((a + b) + c) shouldBe (a + (b + c)) }
    }

    // 分類: オラクル（常に 0 を返す実装でも交換則・結合則は成り立つため、和そのものを検証する）
    "金額の加算は金額の和になる" {
        checkAll(arbYen, arbYen) { a, b -> (a + b).amount shouldBe (a.amount + b.amount) }
    }

    // 分類: 境界（ランダム入力では境界ちょうどの値に当たりにくいため、明示的に固定する）
    "境界値 0 と MAX は受理、-1 と MAX+1 は拒否" {
        listOf(0L, Yen.MAX).forEach { (Yen.of(it) as Validated.Valid).value.amount shouldBe it }
        listOf(-1L, Yen.MAX + 1).forEach { Yen.of(it) shouldBe Validated.Invalid(YenError.OUT_OF_RANGE) }
    }

    // 分類: 不変量（範囲外は必ず拒否される）
    "範囲外の金額は必ず Invalid になる" {
        checkAll(Arb.long().map { if (it in 0..Yen.MAX) -1L else it }) { n ->
            Yen.of(n).shouldBeInstanceOf<Validated.Invalid<*>>()
        }
    }
})
```

> `(Yen.of(it) as Validated.Valid).value` は **テストコード内のジェネレータに限り**許可する `as` の例外である（本番コードでは §1.1 のとおり禁止）。この例外は detekt の対象をテストソースから除外して実現する。

#### 操作列の PBT（状態遷移）

状態遷移の抜け（04 §2.2）は、**操作列を生成して遷移表と照合する Property** で検出する。期待値は TRS の遷移表（独立したオラクル）から作り、実装と同じ分岐で作らない。

```kotlin
// 分類: 不変量（許可された遷移だけが状態を変え、拒否された操作は状態を変えない）
"AC-02-07 任意の操作列で、許可された遷移だけが状態を変え、拒否された操作は状態を変えない" {
    checkAll(opSequences) { sequence ->
        var state: PaymentState = PaymentState.Pending
        sequence.forEach { op ->
            val result = state.apply(op, clock)
            val expected = allowed[kindOf(state) to op.kind] // TRS の遷移表。表に無い組合せは拒否される
            if (expected == null) {
                result shouldBe rejected
            } else {
                kindOf(result.valid()) shouldBe expected
                state = result.valid()
            }
        }
    }
}

// 分類: 不変量（終端状態からは、以降どんな操作列でも遷移できない）
"AC-02-08 終端状態に達した後は、以降どんな操作列でも状態が変わらない" {
    checkAll(opSequences, opSequences) { before, after ->
        var state: PaymentState = PaymentState.Pending
        before.forEach { op -> (state.apply(op, clock) as? Validated.Valid)?.let { state = it.value } }
        if (kindOf(state) in terminalKinds) {
            val terminal = state
            after.forEach { op -> terminal.apply(op, clock) shouldBe rejected }
        }
    }
}
```

操作列のジェネレータは `Arb.bind` で操作の種類と失敗理由を独立に生成し、種類に応じて `Op` を組み立てる（`Arb.list(ops, 0..20)`）。時刻は生成せず、固定した `Clock`（`Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)`）を `apply(op, clock)` に渡す。全体は `src/test/kotlin/com/example/payment/domain/PaymentTransitionsTest.kt`。

### 2.3 PBT 実装ルール

1. **ジェネレータはドメイン型に対して一元管理する。** 各型に `arbXxx`（Kotest の `Arb`）を1つ用意し、ファクトリの検証と同じ制約を満たす値を生成する。
2. 不正値の拒否も Property にする（「範囲外の入力は必ず `Validated.Invalid` を返す」）。
3. シード値を失敗時に出力し、**再現可能**にする。失敗した反例は回帰用の Example テストとして固定する。Kotest 6.2.5 は、失敗した Property のメッセージに `Repeat this test by using seed <値>` を**既定で出力する**（設定は不要）。そのシードを `PropTestConfig(seed = <値>)` に渡すと同じ失敗を再現できる（`PropTestConfig` は `@ExperimentalKotest` の opt-in が必要）。この挙動は `PropertyTestingConfigTest` が、わざと落ちる Property で検証している（Kotest の更新で変わったら落ちる）。

   **PBT が失敗したときの AI の手順**。恒久的に残すのは Example テストだけで、シード付きの Property はコミットしない。ジェネレータや Property を直すとシードの意味が変わり、同じ失敗を再現しなくなるためである。

   1. ログからシードと縮小後の反例を取り出す。Kotest 6.2.5 の失敗メッセージは、`Arb.int(0..1000)` に対し `check(it < 500)` で落ちる Property で、次の形式で出力されることを実測した。縮小後の反例は `Arg <引数番号>: <縮小後の値> (shrunk from <縮小前の値>)` の行、シードは `Repeat this test by using seed <値>` の行にある。この形式は `PropertyTestingConfigTest` が検証している。エッジケース（範囲の端や特別な値。`Arb.int(0..1000)` なら 0・1・1000 など）は、既定で約 2% の確率で混ざる。エッジケースで落ちた場合は縮小されず、`(shrunk from …)` が付かない `Arg 0: 1000` の形式で出力される。どちらの形式でも、`Arg` の行の値がそのまま反例である。

      ```text
      Property failed after 1 attempts

          Arg 0: 500 (shrunk from 988)

      Repeat this test by using seed -2729266846478331732

      Caused by IllegalStateException: boom
      ```

   2. `PropTestConfig(seed = <値>)` で同じ失敗が再現することを確認する（一時的な確認。コミットしない）。
      - 失敗シードの保存（`PropertyTesting.writeFailedSeed`）は無効化せず、既定のままにする。Kotest 6.2.5 で確認した挙動では、Property が失敗するとシードをローカルに保存する。保存先は、環境変数 `XDG_CACHE_HOME` が設定済み（空白でない）なら `$XDG_CACHE_HOME/.kotest/seeds`、未設定なら `~/.kotest/seeds` である。保存したシードは、次の条件をすべて満たす実行で再利用される。(a) `PropTestConfig(seed = …)` を指定しない実行である。(b) 同じテストパスの保存シードである。保存シードはそのテストが成功すると消える（わざと落とすテストでは成功しないため消えず、固定され続ける）。保存シードは PBT の失敗を自動で再現する手がかりになり、上の手順と矛盾しない。
      - 一方、確率的な失敗（実行ごとに出たり出なかったりする失敗）がローカルで再現するかを確かめたいときは、保存済みのシードが結果に影響しないよう、事前に上記の保存先のファイルを消してから実行する。保存先を丸ごと消すと他のテストの保存シードも消えるため、先に手順1でログからシードを控えておく。対象テストのファイルだけを消す方法もある。消すのはローカルの保存先だけで、リポジトリの設定やコードは変えない。Kotest を更新したときは、この挙動が変わっていないか確認する。
   3. 縮小後の反例を Example テストとして固定する（恒久）。AC に紐づく不具合の場合は、修正前に失敗することを確認し（`04` §2.5）、修正と同じ PR に含める。
   4. 反例が仕様上の正しい入力だったのか、Property またはジェネレータの前提が誤っていたのかを判断し、必要なら Property・ジェネレータ側を直す。

4. 実行回数は層1の速度要件（全体で数秒以内）を満たす範囲で設定し、CI の夜間ジョブでは回数を増やす。既定は 1000 回（`kotest.proptest.default.iteration.count`）。Gradle の `-D` はテスト JVM に渡らないため、`build.gradle.kts` で Gradle プロパティ `-Pproptest.iterations=N` をテスト JVM のシステムプロパティ `kotest.proptest.default.iteration.count` に転送している。夜間ジョブ（`.github/workflows/nightly-mutation.yml` の `pbt`）は `./gradlew test -Pproptest.iterations=10000` で実行する（全体テストが約 20 秒で完了することを実測した）。
5. Property が実装のコピーになっていないこと（実装と同じ計算式で期待値を作らない）。

### 2.4 PBT と Example テストの使い分け

| 用途 | 採用 |
|---|---|
| 仕様書に明記された代表例・AC の具体値 | Example テスト（仕様の証跡として残す） |
| 境界・組合せ・不変条件 | PBT |
| PBT の反例の固定 | Example テスト（回帰） |
