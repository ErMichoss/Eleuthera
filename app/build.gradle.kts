import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.tuapp.eleuthera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tuapp.eleuthera"
        minSdk = 29
        targetSdk = 35
        // En GitHub Actions la versión sale de la etiqueta (v1.2.0 -> 1.2.0) y el código
        // del número de ejecución, que siempre crece: así cada release se instala encima.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
        // yt-dlp, Python y FFmpeg son binarios nativos: limita las ABI para no inflar el APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        // Claves de MusicBrainz: en local, de local.properties; en GitHub Actions, de los
        // secretos del repositorio. Nunca se escriben en el código.
        buildConfigField(
            "String",
            "MUSICBRAINZ_CLIENT_ID",
            "\"${localProperties.getProperty("MUSICBRAINZ_CLIENT_ID") ?: System.getenv("MUSICBRAINZ_CLIENT_ID") ?: ""}\""
        )
        buildConfigField(
            "String",
            "MUSICBRAINZ_CLIENT_SECRET",
            "\"${localProperties.getProperty("MUSICBRAINZ_CLIENT_SECRET") ?: System.getenv("MUSICBRAINZ_CLIENT_SECRET") ?: ""}\""
        )

        buildFeatures {
            buildConfig = true
        }
    }

    // Firma de release: solo se usa si existen las variables de entorno (las pone GitHub
    // Actions a partir de los secretos del repositorio). El almacén de claves nunca va al repo.
    // (En una ejecución manual del workflow llega vacía: entonces el APK sale sin firmar.)
    val ciKeystore = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (ciKeystore != null) {
            create("release") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    // Obligatorio: la biblioteca ejecuta los .so como procesos, deben extraerse al disco.
    packaging { jniLibs { useLegacyPackaging = true } }
}

// Antes era kotlinOptions { jvmTarget = "17" } dentro de android { }: Kotlin 2.2 lo prohíbe.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Código común con la versión de escritorio (modelos, tema, pantallas...)
    implementation(project(":shared"))

    // Motor de descarga
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Descargas en segundo plano
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Reproductor
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Base de datos (playlists): está en :shared (Room multiplataforma).

    // Ajustes (tema)
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Listas reordenables arrastrando
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Utilidades
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    //Llamadas de API
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
}