import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.openjfx.javafxplugin")
}

group = "app.naviplayer"
version = System.getenv("NAVIPLAYER_VERSION") ?: "2.1.0"

kotlin {
    jvmToolchain(22)
}

javafx {
    // JavaFX 24 adds Linux support for FFmpeg/libavcodec 61 (Debian 13 and newer).
    version = "24.0.2"
    modules("javafx.media")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    implementation("com.github.hypfvieh:dbus-java-core:5.2.2")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.2")
}

tasks.processResources {
    filesMatching("naviplayer.properties") {
        expand("version" to project.version.toString())
    }
}

compose.desktop {
    application {
        mainClass = "app.naviplayer.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "NaviPlayer"
            includeAllModules = true
            packageVersion = project.version.toString()
            description = "A desktop Navidrome client"
            vendor = "NaviPlayer"
            linux {
                menuGroup = "Audio"
                appCategory = "Audio"
                shortcut = true
                iconFile.set(project.file("src/main/resources/naviplayer.png"))
            }
            windows {
                menuGroup = "NaviPlayer"
                shortcut = true
                menu = true
                perUserInstall = true
                iconFile.set(project.file("src/main/resources/naviplayer.ico"))
            }
        }
    }
}
