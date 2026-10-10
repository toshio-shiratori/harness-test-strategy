// docs/testing/02-test-architecture.md のサンプルコードを実際にコンパイル・実行して検証するための最小プロジェクト。
plugins {
    kotlin("jvm") version "2.3.21" // Spring Boot 4.1.1 の BOM が採用する Kotlin に合わせる
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("info.solidsoft.pitest") version "1.19.0"
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
    testImplementation("io.kotest:kotest-runner-junit5:6.2.5")
    testImplementation("io.kotest:kotest-assertions-core:6.2.5")
    testImplementation("io.kotest:kotest-property:6.2.5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

pitest {
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(listOf("com.example.order.domain.*", "com.example.payment.domain.*")) // ドメイン層に限定
    // 未指定だと targetClasses と同じパターンが使われ、main 側のクラスをテストとして実行して失敗する
    targetTests.set(listOf("com.example.order.domain.*Test", "com.example.payment.domain.*Test"))
    // value class / data class のコンパイラ生成アクセサは、Kotlin からはフィールド直接参照になりテストから呼ばれない。
    // これらのミュータントは検証対象の振る舞いを持たない（NO_COVERAGE のノイズ）ため除外する。
    excludedMethods.set(listOf("getValue", "getAmount", "getError"))
    mutationThreshold.set(80)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
