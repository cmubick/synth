// The Android app: Jetpack Compose screens over the shared C++ sound engine.
// Kotlin support is built into the Android Gradle plugin, so only the Compose
// compiler plugin is applied alongside it.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tuesdaygames.synth"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        // The same ID as the iOS bundle ID. It cannot be changed once the app is on Google Play.
        applicationId = "com.tuesdaygames.synth"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O2")
            }
        }
        ndk {
            // Phones and Chromebooks; the emulator on Apple-silicon Macs is arm64 too.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Builds ../Engine (the iOS app's engine, unchanged) plus the Android audio and JNI glue.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    // Google Play's in-app purchases.
    implementation(libs.google.play.billing)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
}
