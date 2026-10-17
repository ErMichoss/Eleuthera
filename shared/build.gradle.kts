import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Código común de Android y escritorio. Lo que solo existe en una plataforma va en
// androidMain o desktopMain; el resto, en commonMain.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        // PlatformUri y similares: clase "expect" que en cada plataforma es otra clase.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(compose.materialIconsExtended)
            implementation(compose.components.resources)
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            // @Inject / @Singleton: Hilt los usa en Android; en escritorio se ignoran.
            api("javax.inject:javax.inject:1")
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
            }
        }
    }
}

android {
    namespace = "com.tuapp.eleuthera.shared"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Fuentes y demás recursos comunes: shared/src/commonMain/composeResources
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tuapp.eleuthera.shared.resources"
    generateResClass = always
}
