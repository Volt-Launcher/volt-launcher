plugins {
    kotlin("jvm") version "2.2.0"
    application
    kotlin("plugin.serialization") version "1.9.23"
    id("org.openjfx.javafxplugin") version "0.1.0"
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
    implementation("io.javalin:javalin:6.7.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
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
    modules("javafx.controls", "javafx.web")
}

tasks.processResources {
    exclude("dist/**")
    from("src/main/resources/dist") {
        into("dist")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
