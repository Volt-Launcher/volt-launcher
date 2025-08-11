plugins {
    kotlin("jvm") version "2.2.0"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    kotlin("plugin.serialization") version "1.9.23"
}

group = "de.eztxm"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

application {
    // Auf die Kotlin-Top-Level main-Funktion zeigen
    mainClass.set("de.eztxm.thelauncherproject.TheLauncherProjectKt")
}

kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21.0.5"
    modules("javafx.controls", "javafx.fxml", "javafx.web")
}