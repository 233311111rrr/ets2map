/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ets2.trucksim.gps.network

import com.ets2.trucksim.gps.model.ConnectionState
import com.ets2.trucksim.gps.model.JobTelemetry
import com.ets2.trucksim.gps.model.MapPointSelection
import com.ets2.trucksim.gps.model.NavRouteCandidate
import com.ets2.trucksim.gps.model.PoiType
import com.ets2.trucksim.gps.model.RoutingOptions
import com.ets2.trucksim.gps.model.TruckTelemetryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 第一階段電腦端 Node.js 伺服器通訊用戶端 (Stage1ServerClient)
 *
 * 1. 所有網路 IO、JSON 解析、路網規劃與 POI 讀取皆在 `Dispatchers.IO` 背景執行緒執行。
 * 2. 連線成功並收到伺服器回應後才允許進入主畫面；斷線時自動重連並更新 `connectionState`。
 * 3. 若電腦端尚未讀到遊戲數據，保留本地最後一次座標（或首次開啟的德國隨機城市座標）。
 */
class Stage1ServerClient(
    initialLat: Double,
    initialLon: Double,
    initialHeading: Float
) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var manualDisconnect: Boolean = false

    private var currentHost: String = "192.168.1.100"
    private var currentPort: Int = 3000

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetryState = MutableStateFlow(
        TruckTelemetryState(
            hasGameData = false,
            latitude = initialLat,
            longitude = initialLon,
            headingDeg = initialHeading
        )
    )
    val telemetryState: StateFlow<TruckTelemetryState> = _telemetryState.asStateFlow()

    fun getServerHttpBaseUrl(): String = "http://$currentHost:$currentPort"

    /**
     * 連線至電腦端並驗證伺服器回應
     */
    suspend fun verifyAndConnect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || port !in 1..65535) {
            _connectionState.value = ConnectionState.Error("IP 位址或埠號格式不正確（請輸入如 192.168.1.100 與 3000）")
            return@withContext false
        }

        manualDisconnect = false
        currentHost = cleanHost
        currentPort = port
        _connectionState.value = ConnectionState.Connecting(cleanHost, port, 1)

        val healthUrl = "http://$cleanHost:$port/api/health"
        val fallbackRootUrl = "http://$cleanHost:$port/"

        try {
            val req = Request.Builder().url(healthUrl).get().build()
            val response = runCatching { httpClient.newCall(req).execute() }.getOrElse {
                val rootReq = Request.Builder().url(fallbackRootUrl).get().build()
                httpClient.newCall(rootReq).execute()
            }

            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 404) {
                    _connectionState.value = ConnectionState.Error(
                        "伺服器回應異常狀態碼 (HTTP ${resp.code})，請確認電腦端 Node.js 伺服器已啟動"
                    )
                    return@withContext false
                }
            }

            openTelemetryWebSocket(cleanHost, port)
            _connectionState.value = ConnectionState.Connected(cleanHost, port, "Stage1-OK")
            true
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(
                "無法連線至電腦端 ($cleanHost:$port)：${e.localizedMessage ?: "連線逾時"}。請確認手機與電腦在同一個區網，或點擊下方按鈕直接使用 APK 內建地圖。"
            )
            false
        }
    }

    private fun openTelemetryWebSocket(host: String, port: Int) {
        activeWebSocket?.cancel()
        val wsUrl = "ws://$host:$port/ws"
        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.Connected(host, port, "WS-Live")
                reconnectJob?.cancel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                ioScope.launch {
                    parseIncomingTelemetryJson(text)?.let { updated ->
                        _telemetryState.value = updated
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!manualDisconnect) {
                    scheduleAutoReconnect(t.localizedMessage ?: "連線中斷")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!manualDisconnect) {
                    scheduleAutoReconnect(reason.ifBlank { "伺服器關閉連線" })
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        })
    }

    private fun scheduleAutoReconnect(lastError: String) {
        if (reconnectJob?.isActive == true || manualDisconnect) return
        reconnectJob = ioScope.launch {
            var attempt = 1
            while (!manualDisconnect && _connectionState.value !is ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Reconnecting(currentHost, currentPort, attempt, lastError)
                delay((attempt * 2000L).coerceAtMost(8000L))
                if (manualDisconnect) break
                openTelemetryWebSocket(currentHost, currentPort)
                attempt++
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun parseIncomingTelemetryJson(jsonText: String): TruckTelemetryState? {
        return runCatching {
            val root = JSONObject(jsonText)
            val prev = _telemetryState.value

            val rawLat = root.optDouble("lat", root.optDouble("latitude", Double.NaN))
            val rawLon = root.optDouble("lon", root.optDouble("longitude", Double.NaN))
            val hasValidCoords = !rawLat.isNaN() && !rawLon.isNaN() && (rawLat != 0.0 || rawLon != 0.0)

            val heading = root.optDouble("heading", root.optDouble("bearing", prev.headingDeg.toDouble())).toFloat()
            val speedKmh = root.optDouble("speedKmh", root.optDouble("speed", 0.0)).toFloat()
            val speedLimitKmh = root.optInt("speedLimitKmh", root.optInt("speedLimit", 80))
            val gameTimeMinutes = root.optInt("gameTimeMinutes", prev.gameTimeMinutes)

            val fuelObj = root.optJSONObject("fuel")
            val fuelRatio = when {
                fuelObj != null -> {
                    val cur = fuelObj.optDouble("fuel", 600.0).toFloat()
                    val cap = fuelObj.optDouble("fuelCapacity", 1000.0).toFloat().coerceAtLeast(1f)
                    (cur / cap).coerceIn(0f, 1f)
                }
                root.has("fuelRatio") -> root.optDouble("fuelRatio", 0.85).toFloat()
                else -> prev.fuelRatio
            }

            val dmgObj = root.optJSONObject("damage")
            val damageRatio = when {
                dmgObj != null -> {
                    maxOf(
                        dmgObj.optDouble("wearEngine", 0.0).toFloat(),
                        dmgObj.optDouble("wearTransmission", 0.0).toFloat(),
                        dmgObj.optDouble("wearCabin", 0.0).toFloat(),
                        dmgObj.optDouble("wearChassis", 0.0).toFloat()
                    )
                }
                root.has("truckDamageRatio") -> root.optDouble("truckDamageRatio", 0.02).toFloat()
                else -> prev.truckDamageRatio
            }

            val restObj = root.optJSONObject("rest")
            val nextRestMinutes = restObj?.optInt("nextRestStopMinutes", 340)
                ?: root.optInt("nextRestStopMinutes", prev.nextRestStopMinutes)

            val jobObj = root.optJSONObject("job")
            val jobTelemetry = if (jobObj != null) {
                val destCity = jobObj.optString("destinationCity", "")
                JobTelemetry(
                    active = jobObj.optBoolean("active", destCity.isNotBlank()),
                    cargoName = jobObj.optString("cargoName", "標準貨運"),
                    sourceCity = jobObj.optString("sourceCity", ""),
                    sourceCompany = jobObj.optString("sourceCompany", ""),
                    destinationCity = destCity,
                    destinationCompany = jobObj.optString("destinationCompany", ""),
                    destinationLat = jobObj.optDouble("destinationLat", 53.5511),
                    destinationLon = jobObj.optDouble("destinationLon", 9.9937),
                    remainingMinutes = jobObj.optInt("remainingTimeMinutes", jobObj.optInt("remainingMinutes", 180)),
                    incomeText = jobObj.optString("incomeText", "")
                )
            } else {
                prev.job
            }

            TruckTelemetryState(
                hasGameData = hasValidCoords,
                latitude = if (hasValidCoords) rawLat else prev.latitude,
                longitude = if (hasValidCoords) rawLon else prev.longitude,
                headingDeg = if (hasValidCoords) heading else prev.headingDeg,
                speedKmh = speedKmh,
                speedLimitKmh = speedLimitKmh,
                gameTimeMinutes = gameTimeMinutes,
                fuelRatio = fuelRatio,
                truckDamageRatio = damageRatio,
                nextRestStopMinutes = nextRestMinutes,
                job = jobTelemetry,
                timestampMs = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    /**
     * 讀取附近 POI 地標（加油站、維修廠、休息站、城市）
     */
    suspend fun fetchNearbyPois(lat: Double, lon: Double): List<MapPointSelection> = withContext(Dispatchers.IO) {
        val url = "http://$currentHost:$currentPort/api/pois?lat=$lat&lon=$lon"
        return@withContext runCatching {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use defaultGermanPois()
                val body = resp.body?.string() ?: return@use defaultGermanPois()
                val arr = JSONArray(body)
                val list = ArrayList<MapPointSelection>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawType = obj.optString("type", "CITY").uppercase()
                    val poiType = when {
                        rawType.contains("GAS") || rawType.contains("FUEL") -> PoiType.GAS_STATION
                        rawType.contains("SERVICE") || rawType.contains("REPAIR") -> PoiType.SERVICE_STATION
                        rawType.contains("REST") || rawType.contains("PARKING") -> PoiType.REST_AREA
                        else -> PoiType.CITY
                    }
                    list.add(
                        MapPointSelection(
                            id = obj.optString("id", "poi_$i"),
                            nameZh = obj.optString("name", poiType.labelZh),
                            type = poiType,
                            latitude = obj.optDouble("lat", 52.5200),
                            longitude = obj.optDouble("lon", 13.4050)
                        )
                    )
                }
                if (list.isEmpty()) defaultGermanPois() else list
            }
        }.getOrElse {
            defaultGermanPois()
        }
    }

    /**
     * 計算路線（支援多個路徑點、避開收費道路、避開渡輪與替代路線）
     */
    suspend fun calculateRoutes(
        points: List<Pair<Double, Double>>,
        options: RoutingOptions
    ): List<NavRouteCandidate> = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext emptyList()

        val pointsParam = points.joinToString(";") { "${it.first},${it.second}" }
        val url = "http://$currentHost:$currentPort/api/route" +
            "?points=$pointsParam" +
            "&avoidTolls=${options.avoidTolls}" +
            "&avoidFerries=${options.avoidFerries}"

        return@withContext runCatching {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use buildFallbackRoutes(points, options)
                val body = resp.body?.string() ?: return@use buildFallbackRoutes(points, options)
                val root = JSONObject(body)
                val routesArr = root.optJSONArray("routes") ?: return@use buildFallbackRoutes(points, options)

                val result = ArrayList<NavRouteCandidate>(routesArr.length())
                for (i in 0 until routesArr.length()) {
                    val rObj = routesArr.getJSONObject(i)
                    val coordsArr = rObj.optJSONArray("coordinates") ?: JSONArray()
                    val coords = ArrayList<Pair<Double, Double>>(coordsArr.length())
                    for (j in 0 until coordsArr.length()) {
                        val pt = coordsArr.getJSONArray(j)
                        coords.add(Pair(pt.getDouble(1), pt.getDouble(0)))
                    }
                    result.add(
                        NavRouteCandidate(
                            index = i,
                            summaryZh = rObj.optString("summary", if (i == 0) "主推最佳路線" else "替代路線 #$i"),
                            distanceKm = rObj.optDouble("distanceKm", 120.0).toFloat(),
                            durationMinutes = rObj.optInt("durationMinutes", 90),
                            hasTolls = rObj.optBoolean("hasToll", false),
                            hasFerry = rObj.optBoolean("hasFerry", false),
                            geometryCoordinates = if (coords.size >= 2) coords else points,
                            nextManeuverEventKey = rObj.optString("nextManeuverEventKey", "IN_300_METERS_TURN_RIGHT_2"),
                            nextManeuverDistanceMeters = rObj.optInt("nextTurnMeters", 300),
                            nextManeuverTextZh = rObj.optString("nextInstruction", "在 300 公尺處，向右轉")
                        )
                    )
                }
                if (result.isEmpty()) buildFallbackRoutes(points, options) else result
            }
        }.getOrElse {
            buildFallbackRoutes(points, options)
        }
    }

    private fun buildFallbackRoutes(
        points: List<Pair<Double, Double>>,
        options: RoutingOptions
    ): List<NavRouteCandidate> {
        var totalKm = 0f
        for (i in 0 until points.lastIndex) {
            totalKm += haversineKm(
                points[i].first,
                points[i].second,
                points[i + 1].first,
                points[i + 1].second
            )
        }

        val primary = NavRouteCandidate(
            index = 0,
            summaryZh = if (options.avoidTolls) "免收費國道主路線" else "高速公路最快路線",
            distanceKm = (totalKm * 1.18f).coerceAtLeast(5f),
            durationMinutes = ((totalKm * 1.18f) / 78f * 60f).roundToInt().coerceAtLeast(6),
            hasTolls = !options.avoidTolls,
            hasFerry = false,
            geometryCoordinates = points,
            nextManeuverEventKey = "IN_300_METERS_TURN_RIGHT_2",
            nextManeuverDistanceMeters = 300,
            nextManeuverTextZh = "在 300 公尺處，向右轉"
        )

        val origin = points.first()
        val dest = points.last()
        val midLat = (origin.first + dest.first) / 2.0 + 0.12
        val midLon = (origin.second + dest.second) / 2.0 - 0.15

        val alternative = NavRouteCandidate(
            index = 1,
            summaryZh = "替代路線 (省道幹線)",
            distanceKm = (totalKm * 1.32f).coerceAtLeast(8f),
            durationMinutes = ((totalKm * 1.32f) / 68f * 60f).roundToInt().coerceAtLeast(10),
            hasTolls = false,
            hasFerry = false,
            geometryCoordinates = listOf(origin, Pair(midLat, midLon), dest),
            nextManeuverEventKey = "KEEP_SLIGHT_LEFT_3",
            nextManeuverDistanceMeters = 150,
            nextManeuverTextZh = "微靠左行進入替代路線"
        )

        return listOf(primary, alternative)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }

    companion object {
        fun defaultGermanPois(): List<MapPointSelection> = listOf(
            MapPointSelection("poi_berlin_city", "柏林 (Berlin)", PoiType.CITY, 52.5200, 13.4050),
            MapPointSelection("poi_berlin_gas", "柏林北環 A10 加油站", PoiType.GAS_STATION, 52.5850, 13.3820),
            MapPointSelection("poi_berlin_service", "柏林 MAN 授權維修廠", PoiType.SERVICE_STATION, 52.4910, 13.4510),
            MapPointSelection("poi_berlin_rest", "柏林南區卡車休息站", PoiType.REST_AREA, 52.4320, 13.3900),
            MapPointSelection("poi_hamburg_city", "漢堡 (Hamburg)", PoiType.CITY, 53.5511, 9.9937),
            MapPointSelection("poi_hamburg_service", "漢堡港重車維修中心", PoiType.SERVICE_STATION, 53.5320, 9.9710),
            MapPointSelection("poi_munich_city", "慕尼黑 (München)", PoiType.CITY, 48.1351, 11.5820),
            MapPointSelection("poi_frankfurt_gas", "法蘭克福 A5 高速加油站", PoiType.GAS_STATION, 50.1250, 8.6610)
        )
    }
}
