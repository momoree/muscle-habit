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
