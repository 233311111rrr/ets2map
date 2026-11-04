// SPDX-License-Identifier: GPL-3.0-only
package com.ets2.trucksim.gps.data

import android.content.Context
import android.content.SharedPreferences
import com.ets2.trucksim.gps.model.CameraFollowMode
import com.ets2.trucksim.gps.model.CityCoordinate
import com.ets2.trucksim.gps.model.DayNightMode
import com.ets2.trucksim.gps.model.DeliveryStage
import com.ets2.trucksim.gps.model.GermanFallbackCities
import com.ets2.trucksim.gps.model.RouteOptions
import com.ets2.trucksim.gps.model.SavedFavoritePlace
import com.ets2.trucksim.gps.model.SpeedUnit
import com.ets2.trucksim.gps.model.TargetFpsOption
import com.ets2.trucksim.gps.model.VehicleIconType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地持久化儲存管理員 (PreferencesRepository)
 * 負責：
 * 1. 記住上次輸入的電腦端 IP 與 Port。
 * 2. 保存卡車最後一次座標（每隔數秒與離開 App 時非同步寫入本地）。
 * 3. 首次開啟無保存座標時，自動從德國城市清單隨機選取一座城市作為初始座標。
 * 4. 保存時速單位 (km/h 或 mph)、目標幀數、低電量自動降幀、FPS 顯示、卡車圖示、螢幕常亮與收藏地點。
 */
class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val PREFS_NAME = "ets2_trucksim_gps_prefs"

        private const val KEY_SERVER_IP = "server_ip"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_HAS_SAVED_COORDS = "has_saved_coords"
        private const val KEY_LAST_LAT = "last_lat_bits"
        private const val KEY_LAST_LON = "last_lon_bits"
        private const val KEY_LAST_HEADING = "last_heading"
        private const val KEY_INITIAL_CITY_NAME = "initial_city_name"

        private const val KEY_SPEED_UNIT = "speed_unit"
        private const val KEY_VEHICLE_ICON = "vehicle_icon"
        private const val KEY_FOLLOW_MODE = "follow_mode"
        private const val KEY_DAY_NIGHT_MODE = "day_night_mode"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

        private const val KEY_TARGET_FPS = "target_fps"
        private const val KEY_SHOW_FPS_COUNTER = "show_fps_counter"
        private const val KEY_LOW_BATTERY_DROP = "low_battery_frame_drop"

        private const val KEY_AVOID_TOLLS = "route_avoid_tolls"
        private const val KEY_AVOID_FERRIES = "route_avoid_ferries"
        private const val KEY_SHOW_ALTERNATIVES = "route_show_alternatives"

        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_VOICE_MUTED = "voice_muted"
        private const val KEY_SAVED_PLACES_JSON = "saved_places_json"
        private const val KEY_DELIVERY_STAGE = "delivery_stage"
    }

    /**
     * 讀取上次輸入的電腦端 IP（預設 192.168.1.100）與埠號（預設 3000）
     */
    fun getLastServerEndpoint(): Pair<String, Int> {
        val ip = prefs.getString(KEY_SERVER_IP, "192.168.1.100") ?: "192.168.1.100"
        val port = prefs.getInt(KEY_SERVER_PORT, 3000)
        return Pair(ip, port)
    }

    fun saveServerEndpoint(ip: String, port: Int) {
        ioScope.launch {
            prefs.edit()
                .putString(KEY_SERVER_IP, ip.trim())
                .putInt(KEY_SERVER_PORT, port)
                .apply()
        }
    }

    /**
     * 取得初始卡車位置：
     * - 若本地已有保存的最後一次座標，直接回傳該座標與角度。
     * - 若首次開啟（無保存座標），將位置放在德國隨機一座城市並寫入本地。
     */
    fun getOrInitTruckPosition(): InitialPositionResult {
        val hasSaved = prefs.getBoolean(KEY_HAS_SAVED_COORDS, false)
        if (hasSaved) {
            val lat = Double.fromBits(prefs.getLong(KEY_LAST_LAT, (52.5200).toRawBits()))
            val lon = Double.fromBits(prefs.getLong(KEY_LAST_LON, (13.4050).toRawBits()))
            val heading = prefs.getFloat(KEY_LAST_HEADING, 0f)
            val cityNote = prefs.getString(KEY_INITIAL_CITY_NAME, "上次保存座標") ?: "上次保存座標"
            return InitialPositionResult(
                latitude = lat,
                longitude = lon,
                headingDegrees = heading,
                sourceDescription = "本地保存最後座標 ($cityNote)",
                isFirstLaunchRandomGermanCity = false
            )
        } else {
            val randomCity: CityCoordinate = GermanFallbackCities.pickRandomCity()
            saveLastTruckPositionSync(
                latitude = randomCity.latitude,
                longitude = randomCity.longitude,
                headingDegrees = 0f,
                locationLabel = "德國隨機初始城市：${randomCity.nameZh} (${randomCity.nameDe})"
            )
            return InitialPositionResult(
                latitude = randomCity.latitude,
                longitude = randomCity.longitude,
                headingDegrees = 0f,
                sourceDescription = "首次啟動德國隨機城市：${randomCity.nameZh} (${randomCity.nameDe})",
                isFirstLaunchRandomGermanCity = true
            )
        }
    }

    /**
     * 每隔數秒於背景執行緒寫入卡車最新座標
     */
    fun saveLastTruckPositionAsync(latitude: Double, longitude: Double, headingDegrees: Float) {
        ioScope.launch {
            saveLastTruckPositionSync(latitude, longitude, headingDegrees, "行駛自動保存")
        }
    }

    /**
     * 離開 App (onStop) 時立即同步保存最新座標
     */
    fun saveLastTruckPositionSync(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float,
        locationLabel: String = "離開 App 保存"
    ) {
        prefs.edit()
            .putBoolean(KEY_HAS_SAVED_COORDS, true)
            .putLong(KEY_LAST_LAT, latitude.toRawBits())
            .putLong(KEY_LAST_LON, longitude.toRawBits())
            .putFloat(KEY_LAST_HEADING, headingDegrees)
            .putString(KEY_INITIAL_CITY_NAME, locationLabel)
            .apply()
    }

    fun getSpeedUnit(): SpeedUnit {
        val raw = prefs.getString(KEY_SPEED_UNIT, SpeedUnit.KMH.name) ?: SpeedUnit.KMH.name
        return runCatching { SpeedUnit.valueOf(raw) }.getOrDefault(SpeedUnit.KMH)
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        prefs.edit().putString(KEY_SPEED_UNIT, unit.name).apply()
    }

    fun getVehicleIconType(): VehicleIconType {
        val raw = prefs.getString(KEY_VEHICLE_ICON, VehicleIconType.TRUCK.name) ?: VehicleIconType.TRUCK.name
        return runCatching { VehicleIconType.valueOf(raw) }.getOrDefault(VehicleIconType.TRUCK)
    }

    fun setVehicleIconType(type: VehicleIconType) {
        prefs.edit().putString(KEY_VEHICLE_ICON, type.name).apply()
    }

    fun getCameraFollowMode(): CameraFollowMode {
        val raw = prefs.getString(KEY_FOLLOW_MODE, CameraFollowMode.HEADING_UP.name)
            ?: CameraFollowMode.HEADING_UP.name
        return runCatching { CameraFollowMode.valueOf(raw) }.getOrDefault(CameraFollowMode.HEADING_UP)
    }

    fun setCameraFollowMode(mode: CameraFollowMode) {
        prefs.edit().putString(KEY_FOLLOW_MODE, mode.name).apply()
    }

    fun getDayNightMode(): DayNightMode {
        val raw = prefs.getString(KEY_DAY_NIGHT_MODE, DayNightMode.AUTO.name) ?: DayNightMode.AUTO.name
        return runCatching { DayNightMode.valueOf(raw) }.getOrDefault(DayNightMode.AUTO)
    }

    fun setDayNightMode(mode: DayNightMode) {
        prefs.edit().putString(KEY_DAY_NIGHT_MODE, mode.name).apply()
    }

    fun getKeepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun getTargetFpsOption(): TargetFpsOption {
        val raw = prefs.getString(KEY_TARGET_FPS, TargetFpsOption.FPS_120.name) ?: TargetFpsOption.FPS_120.name
        return runCatching { TargetFpsOption.valueOf(raw) }.getOrDefault(TargetFpsOption.FPS_120)
    }

    fun setTargetFpsOption(option: TargetFpsOption) {
        prefs.edit().putString(KEY_TARGET_FPS, option.name).apply()
    }

    fun getShowFpsCounter(): Boolean = prefs.getBoolean(KEY_SHOW_FPS_COUNTER, true)

    fun setShowFpsCounter(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FPS_COUNTER, show).apply()
    }

    fun getLowBatteryFrameDrop(): Boolean = prefs.getBoolean(KEY_LOW_BATTERY_DROP, true)

    fun setLowBatteryFrameDrop(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_BATTERY_DROP, enabled).apply()
    }

    fun getRouteOptions(): RouteOptions {
        return RouteOptions(
            showAlternatives = prefs.getBoolean(KEY_SHOW_ALTERNATIVES, true),
            avoidTolls = prefs.getBoolean(KEY_AVOID_TOLLS, false),
            avoidFerries = prefs.getBoolean(KEY_AVOID_FERRIES, false)
        )
    }

    fun setRouteOptions(options: RouteOptions) {
        prefs.edit()
            .putBoolean(KEY_SHOW_ALTERNATIVES, options.showAlternatives)
            .putBoolean(KEY_AVOID_TOLLS, options.avoidTolls)
            .putBoolean(KEY_AVOID_FERRIES, options.avoidFerries)
            .apply()
    }

    fun getVoiceVolume(): Float = prefs.getFloat(KEY_VOICE_VOLUME, 0.9f)

    fun setVoiceVolume(volume: Float) {
        prefs.edit().putFloat(KEY_VOICE_VOLUME, volume.coerceIn(0f, 1f)).apply()
    }

    fun getVoiceMuted(): Boolean = prefs.getBoolean(KEY_VOICE_MUTED, false)

    fun setVoiceMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_MUTED, muted).apply()
    }

    fun getDeliveryStage(): DeliveryStage {
        val raw = prefs.getString(KEY_DELIVERY_STAGE, DeliveryStage.STAGE_2_4.name) ?: DeliveryStage.STAGE_2_4.name
        return runCatching { DeliveryStage.valueOf(raw) }.getOrDefault(DeliveryStage.STAGE_2_4)
    }

    fun setDeliveryStage(stage: DeliveryStage) {
        prefs.edit().putString(KEY_DELIVERY_STAGE, stage.name).apply()
    }

    fun getSavedFavoritePlaces(): List<SavedFavoritePlace> {
        val raw = prefs.getString(KEY_SAVED_PLACES_JSON, null) ?: return defaultFavoritePlaces()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<SavedFavoritePlace>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedFavoritePlace(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        category = obj.optString("category", "收藏地點"),
                        latitude = obj.optDouble("latitude", 52.52),
                        longitude = obj.optDouble("longitude", 13.405)
                    )
                )
            }
            list
        }.getOrDefault(defaultFavoritePlaces())
    }

    fun saveFavoritePlaces(places: List<SavedFavoritePlace>) {
        ioScope.launch {
            val arr = JSONArray()
            for (place in places) {
                val obj = JSONObject().apply {
                    put("id", place.id)
                    put("title", place.title)
                    put("category", place.category)
                    put("latitude", place.latitude)
                    put("longitude", place.longitude)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_SAVED_PLACES_JSON, arr.toString()).apply()
        }
    }

    private fun defaultFavoritePlaces(): List<SavedFavoritePlace> = listOf(
        SavedFavoritePlace("fav_berlin_garage", "柏林車庫總部", "自有車庫", 52.5200, 13.4050),
        SavedFavoritePlace("fav_hamburg_port", "漢堡港貨櫃碼頭", "常去貨場", 53.5413, 9.9841),
        SavedFavoritePlace("fav_frankfurt_rest", "法蘭克福 A5 大型休息站", "休息與加油", 50.1109, 8.6821)
    )
}

data class InitialPositionResult(
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Float,
    val sourceDescription: String,
    val isFirstLaunchRandomGermanCity: Boolean
)
