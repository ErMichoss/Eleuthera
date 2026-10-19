// build.gradle.kts (raíz del proyecto)
//
// Kotlin 2.2.21 es la primera rama que funciona con Java 25. Las versiones de KSP, Hilt y
// Compose Multiplatform van ligadas a la de Kotlin: si se sube una, revisar las demás.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.2.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
    id("com.google.devtools.ksp") version "2.3.0" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
}