pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "muscle-habit"

// shared: スマホとPCで共有するロジック（達成率・連続週数・次の通知時刻など）
include(":shared")
// androidApp: スマホアプリ本体
include(":androidApp")
// desktopApp: PC 版（フェーズ2）。トレイ常駐の通知・記録の閲覧・スマホからの同期を受け取る
include(":desktopApp")
