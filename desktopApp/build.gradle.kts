import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // jpackage（.msi 作成）に JDK 17 以上が要る。この PC の JDK 21 を使う
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))

    // 画面・トレイ常駐・通知（Compose for Desktop）
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // スマホから記録を受け取る同期サーバー
    val ktor = "3.1.3"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-cio:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // MySQL（Exposed で SQL を組み立てる）
    val exposed = "0.61.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed")
    implementation("com.mysql:mysql-connector-j:8.4.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor")
    // テストは MySQL の代わりに H2（MySQL 互換モード）を使う
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.oashobi.musclehabit.desktop.MainKt"

        nativeDistributions {
            // PowerShell で .\gradlew :desktopApp:packageMsi
            targetFormats(TargetFormat.Msi)
            packageName = "MuscleHabit"
            packageVersion = "1.0.1"
            description = "ジム通いの通知と記録の閲覧"
            vendor = "oashobi"
            // MySQL ドライバーと同期サーバーが使う Java のモジュール
            modules("java.sql", "java.naming", "java.management", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "MuscleHabit"
                shortcut = true
                // 管理者権限なしで自分のユーザーにだけ入れる
                perUserInstall = true
                // 上書きインストールに使う ID（変えないこと）
                upgradeUuid = "38b07c6e-c958-4665-9813-5f64e4697628"
            }
        }
    }
}
