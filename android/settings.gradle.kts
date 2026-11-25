pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "808Synth"

// The Android app. The sound engine is the shared C++ in ../Engine, built by CMake
// inside this module (see app/src/main/cpp/CMakeLists.txt), so there is no separate
// "rules" module as in Sevens and Skate: the engine *is* the shared rules.
include(":app")
