import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pysanka: eSpeak NG (GPL-3.0-or-later, built from source, see prebuilt/VERSION) for the English
// voice's phonemes; module made after lib/whisper

plugins {
    alias(libs.plugins.agp.library)
    alias(libs.plugins.kotlin.android)
}

val projectMinSdk: String by project
val projectCompileSdk: String by project

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "ua.pysanka.espeak"
    compileSdk = projectCompileSdk.toInt()
    ndkVersion = tools.versions.ndk.get()

    defaultConfig {
        minSdk = projectMinSdk.toInt()
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                targets("pysanka_speech")
                arguments("-DCMAKE_ANDROID_API=$minSdk", "-DCMAKE_BUILD_TYPE=Release")
            }
        }

        ndk {
            // 64-bit ARM only, as the rest of Pysanka
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
        create("beta") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        maybeCreate("main").apply {
            java {
                srcDirs("src/main/kotlin")
            }
        }
    }

    externalNativeBuild {
        cmake {
            version = tools.versions.cmake.get()
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    // none
}
