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
// exigen MAYOR.MENOR.PARCHE con MAYOR >= 1, así que se quita cualquier sufijo (v1.2.0-beta)
// y en local se usa 1.0.0.
val appVersion = System.getenv("VERSION_NAME")
    ?.let { Regex("""\d+\.\d+\.\d+""").find(it)?.value }
    ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "com.tuapp.eleuthera.desktop.MainKt"
        // La app lee su versión de aquí (Ajustes > Acerca de).
        jvmArgs += listOf("-Deleuthera.version=$appVersion", "-Dfile.encoding=UTF-8")

        // JDK con el que se empaqueta (se mete dentro del instalador). En CI se usa un JDK 21;
        // en local, el mismo con el que corre Gradle.
        System.getenv("PACKAGING_JAVA_HOME")?.takeIf { it.isNotBlank() }?.let { javaHome = it }

        nativeDistributions {
            // .msi en Windows, .deb en Linux y .dmg en macOS (cada uno se genera en su sistema).
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "Eleuthera"
            packageVersion = appVersion
            description = "Descarga y escucha tu música"
            vendor = "Eleuthera"
            copyright = "© Eleuthera"

            // Partes de Java que usa la app; el resto no se mete en el instalador.
            //  java.net.http -> descargas y GitHub · java.sql / java.naming -> librerías de datos
            //  jdk.unsupported -> corrutinas, Skia · jdk.charsets / jdk.localedata -> UTF-8 y español
            //  java.management -> diagnóstico de corrutinas. (HTTPS ya lo añade Compose por defecto.)
            modules(
                "java.net.http",
                "java.sql",
                "java.naming",
                "java.management",
                "jdk.unsupported",
                "jdk.charsets",
                "jdk.localedata",
                "jdk.accessibility",
            )

            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menu = true
                menuGroup = "Eleuthera"
                shortcut = true
                // Se instala para el usuario, sin pedir permisos de administrador.
                perUserInstall = true
                dirChooser = true
                // Mismo identificador siempre: así cada versión se instala encima de la anterior.
                upgradeUuid = "8c4f7a0e-2d6b-4e1a-9b3f-5a7e1c9d2f40"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
                packageName = "eleuthera"
                menuGroup = "AudioVideo;Audio;Player"
                appCategory = "sound"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "com.tuapp.eleuthera"
                dockName = "Eleuthera"
                appCategory = "public.app-category.music"
            }
        }
    }
}
