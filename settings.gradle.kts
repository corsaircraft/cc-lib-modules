rootProject.name = "cc-lib-modules"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.10"
        id("org.jetbrains.kotlin.js") version "2.3.10"
        id("org.jetbrains.kotlin.multiplatform") version "2.3.10"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
        id("com.android.application") version "8.13.2"
        id("com.android.library") version "8.13.2"
        id("com.android.kotlin.multiplatform.library") version "8.13.2"
        id("org.jetbrains.compose") version "1.9.1"
        id("com.gradleup.shadow") version "8.3.0"
        id("org.jetbrains.dokka") version "2.0.0"
        id("org.jetbrains.kotlinx.kover") version "0.9.3"
        id("org.jetbrains.intellij") version "1.17.2"
        id("io.papermc.paperweight.userdev") version "1.7.2"
        id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
    }
}

include(":kotlin-data-need")
project(":kotlin-data-need").projectDir = file("../kotlin-data-need")
include(":kotlin-data")
project(":kotlin-data").projectDir = file("../kotlin-data")
include(":kotlin-extra-reflection")
project(":kotlin-extra-reflection").projectDir = file("../kotlin-extra-reflection")
include(":kotlin-minilog")
project(":kotlin-minilog").projectDir = file("../kotlin-minilog")
include(":kotlin-graph-toposort")
project(":kotlin-graph-toposort").projectDir = file("../kotlin-graph-toposort")
include(":kotlin-levenshtein")
project(":kotlin-levenshtein").projectDir = file("../kotlin-levenshtein")
include(":kotlin-java-escape")
project(":kotlin-java-escape").projectDir = file("../kotlin-java-escape")
