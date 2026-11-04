/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ets2.trucksim.gps.data

import android.content.Context
import android.content.SharedPreferences
import com.ets2.trucksim.gps.model.DefaultGermanCities
import com.ets2.trucksim.gps.model.HudWidgetId
import com.ets2.trucksim.gps.model.HudWidgetOffset
import com.ets2.trucksim.gps.model.SpeedUnit
import com.ets2.trucksim.gps.model.TargetFpsOption
import com.ets2.trucksim.gps.model.VehicleIconType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 使用者連線設定與顯示偏好資料結構
 */
data class SavedConnectionConfig(
    val host: String = "192.168.1.100",
    val port: Int = 3000,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val targetFps: TargetFpsOption = TargetFpsOption.FPS_120,
    val showFpsCounter: Boolean = true,
    val lowBatteryFrameDrop: Boolean = true,
    val vehicleIcon: VehicleIconType = VehicleIconType.TRUCK,
    val keepScreenOn: Boolean = true,
    val voiceVolume: Float = 0.85f,
    val voiceMuted: Boolean = false
)

/**
 * 初始卡車座標結果（本地最後保存座標或首次開啟德國隨機城市）
 */
data class InitialCoordinate(
    val latitude: Double,
    val longitude: Double,
    val headingDeg: Float,
    val sourceCityZh: String,
    val isRandomInitialCity: Boolean
)

/**
 * 本地持久化儲存管理員 (PreferencesRepository)
 *
 * 負責：
 * 1. 記住上次輸入的電腦端 IP 與埠號。
 * 2. 保存卡車最後一次座標（每隔 5 秒與離開 App 時自動寫入本地）。
 * 3. 首次開啟（無保存座標）時，將位置放在德國隨機一座城市。
 * 4. 保存各個主畫面 UI 元件長按拖曳後的螢幕比例位置 (HudWidgetOffset)，並支援一鍵重置。
 */
class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val PREFS_NAME = "ets2_trucksim_gps_prefs"

        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"
        private const val KEY_HAS_SAVED_COORD = "has_saved_coord"
        private const val KEY_LAST_LAT = "last_lat_bits"
        private const val KEY_LAST_LON = "last_lon_bits"
        private const val KEY_LAST_HEADING = "last_heading_deg"
        private const val KEY_LAST_CITY_LABEL = "last_city_label"

        private const val KEY_SPEED_UNIT = "speed_unit"
        private const val KEY_TARGET_FPS = "target_fps"
        private const val KEY_SHOW_FPS_COUNTER = "show_fps_counter"
        private const val KEY_LOW_BATTERY_DROP = "low_battery_drop"
        private const val KEY_VEHICLE_ICON = "vehicle_icon"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_VOICE_MUTED = "voice_muted"
    }

    /**
     * 讀取完整使用者設定
     */
    fun loadConfig(): SavedConnectionConfig {
        val host = prefs.getString(KEY_HOST, "192.168.1.100") ?: "192.168.1.100"
        val port = prefs.getInt(KEY_PORT, 3000)
        val unitRaw = prefs.getString(KEY_SPEED_UNIT, SpeedUnit.KMH.name) ?: SpeedUnit.KMH.name
        val fpsRaw = prefs.getString(KEY_TARGET_FPS, TargetFpsOption.FPS_120.name) ?: TargetFpsOption.FPS_120.name
        val iconRaw = prefs.getString(KEY_VEHICLE_ICON, VehicleIconType.TRUCK.name) ?: VehicleIconType.TRUCK.name

        return SavedConnectionConfig(
            host = host,
            port = port,
            speedUnit = runCatching { SpeedUnit.valueOf(unitRaw) }.getOrDefault(SpeedUnit.KMH),
            targetFps = runCatching { TargetFpsOption.valueOf(fpsRaw) }.getOrDefault(TargetFpsOption.FPS_120),
            showFpsCounter = prefs.getBoolean(KEY_SHOW_FPS_COUNTER, true),
            lowBatteryFrameDrop = prefs.getBoolean(KEY_LOW_BATTERY_DROP, true),
            vehicleIcon = runCatching { VehicleIconType.valueOf(iconRaw) }.getOrDefault(VehicleIconType.TRUCK),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            voiceVolume = prefs.getFloat(KEY_VOICE_VOLUME, 0.85f),
            voiceMuted = prefs.getBoolean(KEY_VOICE_MUTED, false)
        )
    }

    /**
     * 讀取所有主畫面可長按移動 UI 元件的自訂位置比例
     */
    fun loadHudOffsets(): Map<HudWidgetId, HudWidgetOffset> {
        val map = mutableMapOf<HudWidgetId, HudWidgetOffset>()
        for (widget in HudWidgetId.values()) {
            val x = prefs.getFloat("hud_x_${widget.name}", widget.defaultXFraction)
            val y = prefs.getFloat("hud_y_${widget.name}", widget.defaultYFraction)
            map[widget] = HudWidgetOffset(
                xFraction = x.coerceIn(0f, 0.92f),
                yFraction = y.coerceIn(0f, 0.92f)
            )
        }
        return map
    }

    /**
     * 保存單一 UI 元件長按移動後的螢幕相對比例位置
     */
    fun saveHudOffset(widgetId: HudWidgetId, offset: HudWidgetOffset) {
        ioScope.launch {
            prefs.edit()
                .putFloat("hud_x_${widgetId.name}", offset.xFraction.coerceIn(0f, 0.92f))
                .putFloat("hud_y_${widgetId.name}", offset.yFraction.coerceIn(0f, 0.92f))
                .apply()
        }
    }

    /**
     * 重置所有主畫面 UI 元件至預設位置
     */
    fun resetHudOffsets(): Map<HudWidgetId, HudWidgetOffset> {
        val defaults = mutableMapOf<HudWidgetId, HudWidgetOffset>()
        val editor = prefs.edit()
        for (widget in HudWidgetId.values()) {
            val def = HudWidgetOffset(widget.defaultXFraction, widget.defaultYFraction)
            defaults[widget] = def
            editor.remove("hud_x_${widget.name}")
            editor.remove("hud_y_${widget.name}")
        }
        editor.apply()
        return defaults
    }

    /**
     * 決定初始座標：
     * - 若已有保存座標，回傳上次保存的最後一次座標。
     * - 若首次開啟（無保存座標），隨機挑選一座德國城市並立即保存。
     */
    fun resolveInitialCoordinate(): InitialCoordinate {
        val hasSaved = prefs.getBoolean(KEY_HAS_SAVED_COORD, false)
        if (hasSaved) {
            val lat = Double.fromBits(prefs.getLong(KEY_LAST_LAT, (52.5200).toRawBits()))
            val lon = Double.fromBits(prefs.getLong(KEY_LAST_LON, (13.4050).toRawBits()))
            val heading = prefs.getFloat(KEY_LAST_HEADING, 0f)
            val label = prefs.getString(KEY_LAST_CITY_LABEL, "上次保存座標") ?: "上次保存座標"
            return InitialCoordinate(
                latitude = lat,
                longitude = lon,
                headingDeg = heading,
                sourceCityZh = label,
                isRandomInitialCity = false
            )
        } else {
            val city = DefaultGermanCities.pickRandom()
            prefs.edit()
                .putBoolean(KEY_HAS_SAVED_COORD, true)
                .putLong(KEY_LAST_LAT, city.latitude.toRawBits())
                .putLong(KEY_LAST_LON, city.longitude.toRawBits())
                .putFloat(KEY_LAST_HEADING, 0f)
                .putString(KEY_LAST_CITY_LABEL, city.nameZh)
                .apply()
            return InitialCoordinate(
                latitude = city.latitude,
                longitude = city.longitude,
                headingDeg = 0f,
                sourceCityZh = city.nameZh,
                isRandomInitialCity = true
            )
        }
    }

    /**
     * 每隔數秒與離開 App 時保存卡車最後座標
     */
    fun saveTruckCoordinate(latitude: Double, longitude: Double, headingDeg: Float) {
        ioScope.launch {
            prefs.edit()
                .putBoolean(KEY_HAS_SAVED_COORD, true)
                .putLong(KEY_LAST_LAT, latitude.toRawBits())
                .putLong(KEY_LAST_LON, longitude.toRawBits())
                .putFloat(KEY_LAST_HEADING, headingDeg)
                .apply()
        }
    }

    fun saveConnectionEndpoint(host: String, port: Int) {
        ioScope.launch {
            prefs.edit()
                .putString(KEY_HOST, host.trim())
                .putInt(KEY_PORT, port)
                .apply()
        }
    }

    fun saveSpeedUnit(unit: SpeedUnit) {
        ioScope.launch {
            prefs.edit().putString(KEY_SPEED_UNIT, unit.name).apply()
        }
    }

    fun saveTargetFps(option: TargetFpsOption) {
        ioScope.launch {
            prefs.edit().putString(KEY_TARGET_FPS, option.name).apply()
        }
    }

    fun saveFpsCounterVisibility(show: Boolean) {
        ioScope.launch {
            prefs.edit().putBoolean(KEY_SHOW_FPS_COUNTER, show).apply()
        }
    }

    fun saveLowBatteryFrameDrop(enabled: Boolean) {
        ioScope.launch {
            prefs.edit().putBoolean(KEY_LOW_BATTERY_DROP, enabled).apply()
        }
    }

    fun saveVehicleIcon(icon: VehicleIconType) {
        ioScope.launch {
            prefs.edit().putString(KEY_VEHICLE_ICON, icon.name).apply()
        }
    }

    fun saveKeepScreenOn(keepOn: Boolean) {
        ioScope.launch {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepOn).apply()
        }
    }

    fun saveVoiceSettings(volume: Float, muted: Boolean) {
        ioScope.launch {
            prefs.edit()
                .putFloat(KEY_VOICE_VOLUME, volume.coerceIn(0f, 1f))
                .putBoolean(KEY_VOICE_MUTED, muted)
                .apply()
        }
    }
}
