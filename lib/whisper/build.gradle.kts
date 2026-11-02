import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pysanka: whisper.cpp (MIT) for dictation on the phone; module made after lib/native
/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    namespace = "ua.pysanka.whisper"
    compileSdk = projectCompileSdk.toInt()
    ndkVersion = tools.versions.ndk.get()

    defaultConfig {
        minSdk = projectMinSdk.toInt()
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                targets("pysanka_whisper")
                arguments(
                    "-DCMAKE_ANDROID_API=$minSdk",
                    // Always optimised: a debug build of the model code is far too slow to try
                    "-DCMAKE_BUILD_TYPE=Release",
                )
            }
        }

        ndk {
            // Pysanka: 64-bit ARM only (every phone GrapheneOS supports, and nearly all current Android phones)
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
        create("beta") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
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
