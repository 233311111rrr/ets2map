/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ets2.trucksim.gps.model

/**
 * 分階段交付模式切換器 (2-1 至 2-4)
 */
enum class DeliveryStage(val code: String, val titleZh: String, val subtitleZh: String) {
    STAGE_2_1("2-1", "階段 2-1：基礎連線與地圖", "連線記憶、德國隨機城市、卡車標記、時速與限速圓形、公美制切換"),
    STAGE_2_2("2-2", "階段 2-2：高幀率與流暢度", "30~185/無限 FPS、DisplayMode 鎖定、低電量降幀、位置內插與 FPS 計數器"),
    STAGE_2_3("2-3", "階段 2-3：路徑點與路網規劃", "點擊地圖/POI 彈窗、多路徑點排序、避開收費/渡輪、替代路線、車頭朝上"),
    STAGE_2_4("2-4", "階段 2-4：台灣語音與遙測延伸", "台灣語音模組 2 (47音檔)、SCS_Map_Tiles 內建與 .zip 導入、貨運導航、油量/車損/疲勞提示")
}

/**
 * SCS_Map_Tiles (TsMap2) 地圖包來源模式
 */
enum class MapTileSourceMode(val labelZh: String, val descZh: String) {
    SCS_BUILTIN_AND_ZIP("APK 內建 SCS 地圖 + 自訂 .zip 索引", "優先讀取導入的 .zip 地圖包與 APK 內建 Unicor-p/SCS_Map_Tiles 圖磚"),
    STAGE1_PC_SERVER("電腦端 PMTiles 伺服器", "使用第一階段電腦端 Node.js 提供的 PMTiles 向量圖磚")
}

/**
 * Unicor-p/SCS_Map_Tiles (TileMapInfo.json) 地圖包詮釋資料
 */
data class ScsMapPackInfo(
    val sourceLabel: String = "APK 內建 (Unicor-p/SCS_Map_Tiles)",
    val zipFileName: String = "ETS2_Map_1.48.zip (內建基底 + GitHub Release)",
    val gameId: String = "ets2",
    val gameName: String = "Euro Truck Simulator 2",
    val mapVersion: String = "1.48.1.6 / 1.49.2",
    val tileSize: Int = 512,
    val minZoom: Int = 0,
    val maxZoom: Int = 8,
    val x1: Double = -94621.8047,
    val x2: Double = 79370.13,
    val y1: Double = -80209.1641,
    val y2: Double = 93782.77,
    val indexedTileCount: Int = 21,
    val isCustomZipLoaded: Boolean = false
)

/**
 * 速度與距離單位制
 */
enum class SpeedUnit(val label: String, val multiplierFromKmh: Float) {
    KMH("km/h", 1.0f),
    MPH("mph", 0.621371f)
}

/**
 * 卡車圖示類型（依車頭方向旋轉）
 */
enum class VehicleIconType(val labelZh: String) {
    ARROW("導航箭頭"),
    CAR("小客車"),
    TRUCK("重型卡車")
}

/**
 * 地圖視角模式
 */
enum class CameraFollowMode(val labelZh: String) {
    HEADING_UP("車頭朝上"),
    NORTH_UP("北方朝上"),
    FREE_PAN("自由滑動（暫停跟隨）")
}

/**
 * 目標幀數設定選項
 */
enum class TargetFpsOption(val fpsValue: Int, val labelZh: String) {
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS"),
    FPS_120(120, "120 FPS"),
    FPS_144(144, "144 FPS"),
    FPS_165(165, "165 FPS"),
    FPS_185(185, "185 FPS"),
    UNLIMITED(0, "無限（跟隨螢幕最高刷新率）")
}

/**
 * 連線狀態
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val host: String, val port: Int, val attempt: Int = 1) : ConnectionState()
    data class Connected(val host: String, val port: Int, val serverVersion: String) : ConnectionState()
    data class Reconnecting(val host: String, val port: Int, val attempt: Int, val lastError: String) : ConnectionState()
    data class Error(val messageZh: String) : ConnectionState()
}

/**
 * 德國預設隨機城市清單（首次開啟無本地座標時使用）
 */
data class GermanCity(
    val nameZh: String,
    val nameDe: String,
    val latitude: Double,
    val longitude: Double
)

object DefaultGermanCities {
    val list = listOf(
        GermanCity("柏林 (Berlin)", "Berlin", 52.5200, 13.4050),
        GermanCity("慕尼黑 (München)", "München", 48.1351, 11.5820),
        GermanCity("漢堡 (Hamburg)", "Hamburg", 53.5511, 9.9937),
        GermanCity("法蘭克福 (Frankfurt am Main)", "Frankfurt am Main", 50.1109, 8.6821),
        GermanCity("科隆 (Köln)", "Köln", 50.9375, 6.9603),
        GermanCity("斯圖加特 (Stuttgart)", "Stuttgart", 48.7758, 9.1829),
        GermanCity("杜塞道夫 (Düsseldorf)", "Düsseldorf", 51.2277, 6.7735),
        GermanCity("萊比錫 (Leipzig)", "Leipzig", 51.3397, 12.3731),
        GermanCity("多特蒙德 (Dortmund)", "Dortmund", 51.5136, 7.4653),
        GermanCity("不來梅 (Bremen)", "Bremen", 53.0793, 8.8017)
    )

    fun pickRandom(): GermanCity = list.random()
}

/**
 * 貨運任務資訊 (2-4 遙測延伸欄位)
 */
data class JobTelemetry(
    val active: Boolean = false,
    val cargoName: String = "",
    val sourceCity: String = "",
    val sourceCompany: String = "",
    val destinationCity: String = "",
    val destinationCompany: String = "",
    val destinationLat: Double = 0.0,
    val destinationLon: Double = 0.0,
    val remainingMinutes: Int = 0,
    val incomeText: String = ""
)

/**
 * 完整卡車遙測訊號（含第一階段基礎座標與 2-4 延伸欄位）
 */
data class TruckTelemetryState(
    val hasGameData: Boolean = false,
    val latitude: Double = 52.5200,
    val longitude: Double = 13.4050,
    val scsGameX: Float = 13450f,
    val scsGameY: Float = -12580f,
    val headingDeg: Float = 0f,
    val speedKmh: Float = 0f,
    val speedLimitKmh: Int = 80,
    val gameTimeMinutes: Int = 720,
    val fuelRatio: Float = 0.85f,
    val fuelRangeKm: Float = 980f,
    val truckDamageRatio: Float = 0.02f,
    val nextRestStopMinutes: Int = 340,
    val job: JobTelemetry = JobTelemetry(),
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * 地圖興趣點 (POI) 類型
 */
enum class PoiType(val labelZh: String, val badgeColorHex: Long) {
    GAS_STATION("加油站", 0xFF10B981),
    SERVICE_STATION("維修廠", 0xFFF59E0B),
    REST_AREA("休息站", 0xFF3B82F6),
    CITY("城市地標", 0xFFA855F7),
    CUSTOM_PIN("自訂標點", 0xFFEF4444)
}

/**
 * 興趣點或地圖點擊位置資料
 */
data class MapPointSelection(
    val id: String,
    val nameZh: String,
    val type: PoiType,
    val latitude: Double,
    val longitude: Double
)

/**
 * 導航路徑點
 */
data class RouteWaypoint(
    val id: String,
    val titleZh: String,
    val latitude: Double,
    val longitude: Double,
    val type: PoiType = PoiType.CUSTOM_PIN
)

/**
 * 路線規劃偏好設定
 */
data class RoutingOptions(
    val avoidTolls: Boolean = false,
    val avoidFerries: Boolean = false,
    val selectedAlternativeIndex: Int = 0
)

/**
 * 規劃後的單條路線資料
 */
data class NavRouteCandidate(
    val index: Int,
    val summaryZh: String,
    val distanceKm: Float,
    val durationMinutes: Int,
    val hasTolls: Boolean,
    val hasFerry: Boolean,
    val geometryCoordinates: List<Pair<Double, Double>>,
    val nextManeuverEventKey: String = "IN_300_METERS_TURN_RIGHT_2",
    val nextManeuverDistanceMeters: Int = 300,
    val nextManeuverTextZh: String = "在 300 公尺處，向右轉"
)

/**
 * 台灣語音模組 2 音檔對照表項目（完全對齊 Google_map_TW[0]_完整對照表.txt 47 筆格式）
 */
data class VoiceMappingEntry(
    val fileName: String,
    val prefixedFileName: String = "",
    val originalFileName: String = "",
    val eventKey: String,
    val descriptionZh: String,
    val triggerDistanceMeters: Int,
    val enabled: Boolean = true,
    val fileExistsInAssets: Boolean = true
)

/**
 * 行駛軌跡紀錄點與收藏地點
 */
data class TrackLogPoint(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val timestampMs: Long
)

data class SavedFavoritePlace(
    val id: String,
    val nameZh: String,
    val category: PoiType,
    val latitude: Double,
    val longitude: Double
)
