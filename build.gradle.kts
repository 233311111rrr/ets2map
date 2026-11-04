// SPDX-License-Identifier: GPL-3.0-only
// ETS2 TruckSim GPS - 根目錄 Gradle 建置腳本

plugins {
    id("com.android.application") version "8.4.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
