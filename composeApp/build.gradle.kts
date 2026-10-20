import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm("desktop")
    jvmToolchain(21)
    val ktorVersion = "3.1.1"

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("net.java.dev.jna:jna-platform:5.16.0")
                implementation("org.xerial:sqlite-jdbc:3.49.1.0")
                implementation("org.slf4j:slf4j-nop:2.0.17")
                implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(compose.desktop.uiTestJUnit4)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "app.stickerkaleidoscope.resources"
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // A different opt-in sample directory must not reuse a previous test result.
    inputs.property("sampleDirectory", providers.environmentVariable("STICKER_SAMPLE_DIR").orElse(""))
    inputs.property("sampleOutput", providers.environmentVariable("STICKER_SAMPLE_OUTPUT").orElse(""))
}

compose.desktop {
    application {
        mainClass = "app.stickerkaleidoscope.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        providers.gradleProperty("packagingJdkHome").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Sticker Kaleidoscope"
            packageVersion = "0.1.0"
            modules("java.sql", "java.naming", "jdk.unsupported")
            windows {
                iconFile.set(project.file("src/desktopMain/resources/app-icon.ico"))
            }
        }
    }
}
