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
 * 主畫面可長按自由拖曳移動的 UI 元件識別碼
 */
enum class HudWidgetId(
    val labelZh: String,
    val defaultXFraction: Float,
    val defaultYFraction: Float
) {
    TOP_NAV_BANNER("頂部導航與遙測橫幅", 0.03f, 0.02f),
    SPEED_CLUSTER("雙圓形時速與限速儀表", 0.03f, 0.78f),
    QUICK_CONTROLS("右下角操作快捷列", 0.42f, 0.80f),
    FPS_BADGE("即時 FPS 計數器膠囊", 0.82f, 0.14f)
}

/**
 * UI 元件在螢幕上的相對比例座標 (0.0f ~ 1.0f)，確保跨手機/平板與橫直向自動適配
 */
data class HudWidgetOffset(
    val xFraction: Float,
    val yFraction: Float
)

/**
 * 2K / 1080p 高畫質地圖來源模式
 */
enum class MapTileSourceMode(val labelZh: String, val descZh: String) {
    SCS_2K_RETINA_AND_ZIP(
        "2K 超清 SCS 地圖 (@2x 超採樣 + 自訂 .zip)",
        "啟用 256dp @2x Retina 高像素密度渲染，結合 APK 內建 Zoom 0~5、雲端 Zoom 6~8 快取與自訂 .zip 索引"
    ),
    TRUCKER_MUDGEON_HYBRID_2K(
        "2K/4K 向量增強高畫質模式 (TruckerMudgeon + SCS)",
        "結合 2K 高清路網圖磚與向量道路抗鋸齒增強層，在平板與高解析手機上維持無損銳利度"
    ),
    STAGE1_PC_SERVER(
        "電腦端 PMTiles 伺服器",
        "使用第一階段電腦端 Node.js 提供的 PMTiles 圖磚"
    )
}

/**
 * Unicor-p/SCS_Map_Tiles (TileMapInfo.json) 地圖包詮釋資料
 */
data class ScsMapPackInfo(
    val sourceLabel: String = "APK 內建 2K 超清地圖 (Unicor-p/SCS_Map_Tiles)",
    val zipFileName: String = "ETS2_Map_1.48.zip (2K @2x High-DPI + Zoom 0~8)",
    val gameId: String = "ets2",
    val gameName: String = "Euro Truck Simulator 2",
    val mapVersion: String = "1.48.1.6 / 1.49.2 (2K Enhanced)",
    val resolutionQualityLabel: String = "2K UHD (@2x Retina, 最高 131072×131072 px)",
    val tileSize: Int = 512,
    val minZoom: Int = 0,
    val maxZoom: Int = 8,
    val x1: Double = -94621.8047,
    val x2: Double = 79370.13,
    val y1: Double = -80209.1641,
    val y2: Double = 93782.77,
    val indexedTileCount: Int = 105,
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
 * 貨運任務資訊
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
 * 完整卡車遙測訊號
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
