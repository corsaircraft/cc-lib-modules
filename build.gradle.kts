import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://jitpack.io")
}

group   = "one.wabbit"
version = "0.0.1"

plugins {
    kotlin("jvm") version "2.1.20"

    kotlin("plugin.serialization") version "2.1.20"

    id("maven-publish")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "one.wabbit"
            artifactId = "cc-lib-modules"
            version = "0.0.1"
            from(components["java"])
        }
    }
}

dependencies {
    implementation("com.github.wabbit-corp:kotlin-data-need:1.2.0")
    implementation("com.github.wabbit-corp:kotlin-data:1.2.0")
    implementation("com.github.wabbit-corp:kotlin-extra-reflection:1.0.1")
    implementation("com.github.wabbit-corp:kotlin-minilog:1.0.2")
    implementation("com.github.wabbit-corp:kotlin-graph-toposort:1.1.0")
    implementation("com.github.wabbit-corp:kotlin-levenshtein:1.1.0")

    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")

    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
    implementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

java {
    targetCompatibility = JavaVersion.toVersion(21)
    sourceCompatibility = JavaVersion.toVersion(21)
}

tasks {
    withType<Test> {
        jvmArgs("-ea")

    }
    withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
    }
    withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }

    jar {
        setProperty("zip64", true)

    }
}
