// SPDX-License-Identifier: GPL-3.0-only
// ETS2 TruckSim GPS - Android 原生衛星導航專案設定

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

rootProject.name = "ETS2TruckSimGPS"
include(":app")
