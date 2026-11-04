/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ets2.trucksim.gps"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ets2.trucksim.gps"
        minSdk = 26
        targetSdk = 34
        versionCode = 204
        versionName = "2.4.0-gpl3"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 確保內建的 .zip 地圖包 (ETS2_Map_1.48.zip) 與 47 個 .wav 台灣語音檔不進行二次壓縮，支援高速隨機讀取
    androidResources {
        noCompress += listOf("zip", "wav", "png", "pmtiles")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose UI (Material3 + Material Icons Extended)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // MapLibre Native SDK for Android (Open-Source Vector & Raster Tile Engine, Kotlin 1.9 compatible)
    implementation("org.maplibre.gl:android-sdk:11.0.0")

    // OkHttp 4 for WebSocket Telemetry & HTTP REST Routing/POI
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Kotlin Coroutines for Background IO / Interpolation / Zero UI Blocking
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
