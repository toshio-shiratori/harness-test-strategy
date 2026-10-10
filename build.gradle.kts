// docs/testing/02-test-architecture.md のサンプルコードを実際にコンパイル・実行して検証するための最小プロジェクト。
plugins {
    kotlin("jvm") version "2.4.20" // Spring Boot 4.1.1 の BOM（2.3 系）より新しい。kotlin-reflect / stdlib は 2.4.20 に揃うことを確認済み
    kotlin("plugin.spring") version "2.4.20"
    id("org.springframework.boot") version "4.1.1"
    id("info.solidsoft.pitest") version "1.19.0"
    id("dev.detekt") version "2.0.0-alpha.6"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "com.example"
version = "0.0.1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true) // 警告（非網羅 when・冗長な else 等）をビルドエラーにする
    }
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test") // @WebMvcTest / RestTestClient
    testImplementation("io.kotest:kotest-runner-junit5:6.2.5")
    testImplementation("io.kotest:kotest-assertions-core:6.2.5")
    testImplementation("io.kotest:kotest-property:6.2.5")
    testImplementation("com.lemonappdev:konsist:0.18.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

pitest {
    junit5PluginVersion.set("1.2.3")
    // 既定はドメイン層全体。PR の差分実行では scripts/pitest-targets.sh の出力を -Ppitest.targetClasses=... で渡す
    targetClasses.set(
        providers.gradleProperty("pitest.targetClasses")
            .map { it.split(",") }
            .orElse(listOf("com.example.order.domain.*", "com.example.payment.domain.*")),
    )
    // 未指定だと targetClasses と同じパターンが使われ、main 側のクラスをテストとして実行して失敗する
    targetTests.set(listOf("com.example.order.domain.*Test", "com.example.payment.domain.*Test"))
    // value class / data class のコンパイラ生成アクセサは、Kotlin からはフィールド直接参照になりテストから呼ばれない
    // （NO_COVERAGE のノイズ）。名前ベースの excludedMethods は全クラスに効いて将来のロジックを黙って見逃すため、
    // アクセサ以外にメンバを持たないクラスだけをクラス単位で除外する。検証ロジックは別クラスの companion（Quantity$Companion 等）にある。
    // 除外の追加・変更は人間の承認が必要（このファイルは CODEOWNERS 対象）。
    excludedClasses.set(
        listOf(
            "com.example.order.domain.Quantity",
            "com.example.order.domain.UserId",
            "com.example.order.domain.OrderId",
            "com.example.order.domain.Sku",
            "com.example.order.domain.Validated\$Invalid",
        ),
    )
    // 夜間の全体実行は 70%（-Ppitest.threshold=70）。01 の G3 / 02 §4.1
    mutationThreshold.set(providers.gradleProperty("pitest.threshold").map { it.toInt() }.orElse(80))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
}

detekt {
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    // 02 §2.2: テストコード内のジェネレータに限り `as` を許可するため、検査対象は本番ソースのみ
    source.setFrom("src/main/kotlin")
}

// UnsafeCast / UnsafeCallOnNullableType / ForbiddenAnnotation は型解決が必要なため detektMain で実行する。
// `./gradlew detekt` でも detektMain が走るようにして、コマンドを docs・CI・ローカルで一つにする。
tasks.named("detekt") { dependsOn("detektMain") }
// テストソースは `as` を許容する（02 §2.2）ため検査しない
tasks.matching { it.name == "detektTest" || it.name == "detektTestSourceSet" }.configureEach { enabled = false }

tasks.withType<Test> {
    useJUnitPlatform()
    // TraceabilityTest が TRS を読むため、変更時に再実行されるよう入力に宣言する（未宣言だと up-to-date 扱いで素通りする）
    inputs.dir("docs/testing/trs")
    // 夜間ジョブは -Pproptest.iterations=N で PBT の実行回数を増やす（02 §2.3）。未指定なら Kotest の既定（1000）。
    // Gradle の -D はテスト JVM に渡らないため、ここで転送する。
    providers.gradleProperty("proptest.iterations").orNull?.let {
        systemProperty("kotest.proptest.default.iteration.count", it)
    }
}
