import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
}

group   = "one.wabbit"
version = "1.0.0"

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")

    kotlin("plugin.serialization")

    id("maven-publish")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "one.wabbit"
            artifactId = "cc-lib-modules"
            version = "1.0.0"
            from(components["java"])
        }
    }
}

dependencies {
    implementation(project(":kotlin-data-need")) // 1.2.0
    implementation(project(":kotlin-data")) // 3.0.0
    implementation(project(":kotlin-extra-reflection")) // 1.0.1
    implementation(project(":kotlin-minilog")) // 1.0.2
    implementation(project(":kotlin-graph-toposort")) // 2.0.0
    implementation(project(":kotlin-levenshtein")) // 1.1.0

    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")

    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.0")
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

            freeCompilerArgs.add("-Xcontext-parameters")

        }
    }

    jar {
        setProperty("zip64", true)

    }
}

// Kover Configuration
kover {
    // useJacoco() // This is the default, can be specified if you want to be explicit
    // reports {
    //     // Configure reports for the default test task.
    //     // Kover tries to infer the variant for simple JVM projects.
    //     // If you have specific build types/flavors, you'd configure them here as variants.
    //     variant() { // Or remove "debug" for a default JVM setup unless you have variants
    //         html {
    //             // reportDir.set(layout.buildDirectory.dir("reports/kover/html")) // Uncomment to customize output
    //             // title.set("cc-lib-modules Code Coverage") // Uncomment to customize title
    //         }
    //         xml {
    //             // reportFile.set(layout.buildDirectory.file("reports/kover/coverage.xml")) // Uncomment to customize output
    //         }
    //     }
    // }
}

dokka {
    moduleName.set("cc-lib-modules")
    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        failOnWarning.set(true)
    }
    dokkaSourceSets.main {
        // includes.from("README.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://example.com/src")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        // customStyleSheets.from("styles.css")
        // customAssets.from("logo.png")
        footerMessage.set("(c) Wabbit Consulting Corporation")
    }
}
