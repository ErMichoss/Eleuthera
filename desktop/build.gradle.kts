import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Eleuthera para Windows, Linux y macOS.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

// Igual que en Android: la versión sale de la etiqueta (v1.2.0 -> 1.2.0). Los instaladores
// exigen MAYOR.MENOR.PARCHE con MAYOR >= 1, así que en local se usa 1.0.0.
val appVersion = System.getenv("VERSION_NAME")?.removePrefix("v") ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "com.tuapp.eleuthera.desktop.MainKt"
        // La app lee su versión de aquí (Ajustes > Acerca de).
        jvmArgs += "-Deleuthera.version=$appVersion"

        nativeDistributions {
            // .msi en Windows, .deb en Linux y .dmg en macOS (cada uno se genera en su sistema).
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Eleuthera"
            packageVersion = appVersion
            description = "Descarga y escucha tu música"
            vendor = "Eleuthera"

            windows {
                menu = true
                shortcut = true
                // Mismo identificador siempre: así cada versión se instala encima de la anterior.
                upgradeUuid = "8c4f7a0e-2d6b-4e1a-9b3f-5a7e1c9d2f40"
            }
            linux {
                shortcut = true
            }
            macOS {
                bundleID = "com.tuapp.eleuthera"
            }
        }
    }
}