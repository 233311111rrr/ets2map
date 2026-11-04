# SPDX-License-Identifier: GPL-3.0-only
# ETS2 TruckSim GPS ProGuard 規則

-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-keep class com.ets2.trucksim.gps.model.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
