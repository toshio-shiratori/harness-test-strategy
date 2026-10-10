// docs/testing/02-test-architecture.md のサンプルコードを実際にコンパイル・実行して検証するための最小プロジェクト。
plugins {
    kotlin("jvm") version "2.3.21" // Spring Boot 4.1.1 の BOM が採用する Kotlin に合わせる
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
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

tasks.withType<Test> {
    useJUnitPlatform()
}
