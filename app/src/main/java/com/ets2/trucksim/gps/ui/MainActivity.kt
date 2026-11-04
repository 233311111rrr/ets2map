/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ets2.trucksim.gps.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ets2.trucksim.gps.audio.TaiwanVoiceNavigator
import com.ets2.trucksim.gps.data.PreferencesRepository
import com.ets2.trucksim.gps.engine.FrameRateController
import com.ets2.trucksim.gps.engine.InterpolatedPose
import com.ets2.trucksim.gps.engine.TelemetryInterpolator
import com.ets2.trucksim.gps.map.ScsTileMapManager
import com.ets2.trucksim.gps.model.CameraFollowMode
import com.ets2.trucksim.gps.model.ConnectionState
import com.ets2.trucksim.gps.model.HudWidgetId
import com.ets2.trucksim.gps.model.HudWidgetOffset
import com.ets2.trucksim.gps.model.MapPointSelection
import com.ets2.trucksim.gps.model.MapTileSourceMode
import com.ets2.trucksim.gps.model.NavRouteCandidate
import com.ets2.trucksim.gps.model.PoiType
import com.ets2.trucksim.gps.model.RouteWaypoint
import com.ets2.trucksim.gps.model.RoutingOptions
import com.ets2.trucksim.gps.model.SavedFavoritePlace
import com.ets2.trucksim.gps.model.SpeedUnit
import com.ets2.trucksim.gps.model.TargetFpsOption
import com.ets2.trucksim.gps.model.TrackLogPoint
import com.ets2.trucksim.gps.model.VehicleIconType
import com.ets2.trucksim.gps.network.Stage1ServerClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ETS2 TruckSim GPS 主畫面 Activity
 *
 * - 全功能預設常駐開啟（已移除驗收階段切換器）
 * - 主畫面所有浮動 UI 元件皆支援「長按後隨意拖曳移動」並自動依螢幕比例保存位置
 * - 自動適配各種尺寸的手機、摺疊機與平板螢幕 (BoxWithConstraints 自適應縮放)
 * - 內建 2K 超清 (@2x Retina) `Unicor-p/SCS_Map_Tiles` 地圖、免解壓 `.zip` 導入與 47 個台灣語音檔
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefsRepo: PreferencesRepository
    private lateinit var serverClient: Stage1ServerClient
    private lateinit var frameRateController: FrameRateController
    private lateinit var voiceNavigator: TaiwanVoiceNavigator
    private lateinit var scsTileMapManager: ScsTileMapManager
    private val interpolator = TelemetryInterpolator()

    private var isLowBatteryState by mutableStateOf(false)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                val pct = (level * 100) / scale
                isLowBatteryState = pct <= 20
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsRepo = PreferencesRepository(applicationContext)
        val initialCoord = prefsRepo.resolveInitialCoordinate()

        serverClient = Stage1ServerClient(
            initialLat = initialCoord.latitude,
            initialLon = initialCoord.longitude,
            initialHeading = initialCoord.headingDeg
        )
        interpolator.snapTo(initialCoord.latitude, initialCoord.longitude, initialCoord.headingDeg)

        frameRateController = FrameRateController(this)
        voiceNavigator = TaiwanVoiceNavigator(applicationContext)
        scsTileMapManager = ScsTileMapManager(applicationContext)

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    background = Color(0xFF0B0E14),
                    surface = Color(0xFF161B26)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B0E14)) {
                    TruckSimGpsApp(
                        activity = this,
                        prefsRepo = prefsRepo,
                        serverClient = serverClient,
                        frameRateController = frameRateController,
                        voiceNavigator = voiceNavigator,
                        scsTileMapManager = scsTileMapManager,
                        interpolator = interpolator,
                        isLowBattery = isLowBatteryState
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val snap = serverClient.telemetryState.value
        prefsRepo.saveTruckCoordinate(snap.latitude, snap.longitude, snap.headingDeg)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(batteryReceiver) }
        frameRateController.stop()
        voiceNavigator.release()
        scsTileMapManager.release()
        serverClient.disconnect()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TruckSimGpsApp(
    activity: ComponentActivity,
    prefsRepo: PreferencesRepository,
    serverClient: Stage1ServerClient,
    frameRateController: FrameRateController,
    voiceNavigator: TaiwanVoiceNavigator,
    scsTileMapManager: ScsTileMapManager,
    interpolator: TelemetryInterpolator,
    isLowBattery: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedConfig by remember { mutableStateOf(prefsRepo.loadConfig()) }
    var hostInput by remember { mutableStateOf(savedConfig.host) }
    var portInput by remember { mutableStateOf(savedConfig.port.toString()) }
    var hasEnteredMapScreen by remember { mutableStateOf(false) }
    var tileSourceMode by remember { mutableStateOf(MapTileSourceMode.SCS_2K_RETINA_AND_ZIP) }

    // 主畫面各個可長按移動 UI 元件的位置比例狀態
    val hudOffsets = remember {
        mutableStateMapOf<HudWidgetId, HudWidgetOffset>().apply {
            putAll(prefsRepo.loadHudOffsets())
        }
    }

    val connectionState by serverClient.connectionState.collectAsState()
    val telemetry by serverClient.telemetryState.collectAsState()
    val measuredFps by frameRateController.measuredFps.collectAsState()
    val voiceEntries by voiceNavigator.mappingEntries.collectAsState()
    val missingVoiceNotice by voiceNavigator.missingVoiceNotice.collectAsState()
    val scsMapPackInfo by scsTileMapManager.mapPackInfo.collectAsState()
    val mapImportStatusMessage by scsTileMapManager.importStatusMessage.collectAsState()

    var followMode by remember { mutableStateOf(CameraFollowMode.HEADING_UP) }
    var interpolatedPose by remember {
        mutableStateOf(
            InterpolatedPose(
                telemetry.latitude,
                telemetry.longitude,
                telemetry.headingDeg,
                telemetry.speedKmh
            )
        )
    }

    val pois = remember { mutableStateListOf<MapPointSelection>() }
    val waypoints = remember { mutableStateListOf<RouteWaypoint>() }
    var routingOptions by remember { mutableStateOf(RoutingOptions()) }
    val routeCandidates = remember { mutableStateListOf<NavRouteCandidate>() }
    val trackHistory = remember { mutableStateListOf<TrackLogPoint>() }
    val savedFavorites = remember { mutableStateListOf<SavedFavoritePlace>() }

    var activeMapSelection by remember { mutableStateOf<MapPointSelection?>(null) }
    var showWaypointSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // 系統檔案選擇器：導入任意 .zip 格式地圖包（免解壓縮直接建立索引）
    val mapZipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else null
                }
            }.getOrNull()
            scsTileMapManager.importMapZipFromUri(uri, fileName)
            tileSourceMode = MapTileSourceMode.SCS_2K_RETINA_AND_ZIP
        }
    }

    val launchZipPicker = {
        mapZipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }

    LaunchedEffect(savedConfig.keepScreenOn) {
        if (savedConfig.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(savedConfig.targetFps, savedConfig.lowBatteryFrameDrop, isLowBattery) {
        frameRateController.applyFrameRateOption(
            option = savedConfig.targetFps,
            lowBatteryDropEnabled = savedConfig.lowBatteryFrameDrop,
            isCurrentlyLowBattery = isLowBattery
        )
        frameRateController.start { frameTimeNanos ->
            interpolatedPose = interpolator.sampleAtFrame(frameTimeNanos)
        }
        onDispose {
            frameRateController.stop()
        }
    }

    LaunchedEffect(telemetry) {
        interpolator.onTelemetrySampleReceived(telemetry)
        if (hasEnteredMapScreen) {
            if (trackHistory.size > 400) trackHistory.removeAt(0)
            trackHistory.add(
                TrackLogPoint(
                    latitude = telemetry.latitude,
                    longitude = telemetry.longitude,
                    speedKmh = telemetry.speedKmh,
                    timestampMs = telemetry.timestampMs
                )
            )
            if (telemetry.speedLimitKmh > 0 && telemetry.speedKmh > telemetry.speedLimitKmh + 5f) {
                voiceNavigator.triggerVoiceEvent(
                    eventKey = "SPEED_WARNING_BEEP_1",
                    distanceBucketMeters = 0,
                    volume = savedConfig.voiceVolume,
                    muted = savedConfig.voiceMuted
                )
            }
        }
    }

    LaunchedEffect(hasEnteredMapScreen) {
        while (hasEnteredMapScreen) {
            delay(5000L)
            val snap = serverClient.telemetryState.value
            prefsRepo.saveTruckCoordinate(snap.latitude, snap.longitude, snap.headingDeg)
        }
    }

    fun recalculateRoutes() {
        if (waypoints.isEmpty()) {
            routeCandidates.clear()
            return
        }
        scope.launch {
            val pts = buildList<Pair<Double, Double>> {
                add(Pair(interpolatedPose.latitude, interpolatedPose.longitude))
                waypoints.forEach { add(Pair(it.latitude, it.longitude)) }
            }
            val routes = serverClient.calculateRoutes(pts, routingOptions)
            routeCandidates.clear()
            routeCandidates.addAll(routes)
            routes.firstOrNull()?.let { primary ->
                voiceNavigator.triggerVoiceEvent(
                    eventKey = primary.nextManeuverEventKey,
                    distanceBucketMeters = primary.nextManeuverDistanceMeters,
                    volume = savedConfig.voiceVolume,
                    muted = savedConfig.voiceMuted
                )
            }
        }
    }

    if (!hasEnteredMapScreen) {
        StartupConnectionScreen(
            hostInput = hostInput,
            portInput = portInput,
            onHostChange = { hostInput = it },
            onPortChange = { portInput = it },
            connectionState = connectionState,
            scsMapPackInfo = scsMapPackInfo,
            mapImportStatusMessage = mapImportStatusMessage,
            onPickMapZipFile = launchZipPicker,
            onResetBuiltInMap = { scsTileMapManager.resetToBuiltInMap() },
            onConnectClick = {
                val parsedPort = portInput.toIntOrNull() ?: 3000
                prefsRepo.saveConnectionEndpoint(hostInput.trim(), parsedPort)
                scope.launch {
                    val ok = serverClient.verifyAndConnect(hostInput.trim(), parsedPort)
                    if (ok) {
                        hasEnteredMapScreen = true
                        val fetchedPois = serverClient.fetchNearbyPois(
                            interpolatedPose.latitude,
                            interpolatedPose.longitude
                        )
                        pois.clear()
                        pois.addAll(fetchedPois)
                    }
                }
            },
            onEnterOfflineBuiltInMap = {
                hasEnteredMapScreen = true
                scope.launch {
                    val fetchedPois = serverClient.fetchNearbyPois(
                        interpolatedPose.latitude,
                        interpolatedPose.longitude
                    )
                    pois.clear()
                    pois.addAll(fetchedPois)
                }
            }
        )
        return
    }

    val hourOfDay = (telemetry.gameTimeMinutes / 60) % 24
    val isNightMode = hourOfDay < 6 || hourOfDay >= 19

    // 主地圖畫面：使用 BoxWithConstraints 自動適配各種手機/平板尺寸，且所有 UI 元件皆可長按自由拖曳移動
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parentWidthPx = with(density) { maxWidth.toPx() }
        val parentHeightPx = with(density) { maxHeight.toPx() }
        val metrics = rememberAdaptiveScreenMetrics(maxWidth, maxHeight)

        // 底層：MapLibre Native 2K 超清 (@2x Retina) 地圖
        MapLibreNavigationView(
            serverHttpBaseUrl = serverClient.getServerHttpBaseUrl(),
            scsLocalTileUrlTemplate = scsTileMapManager.getLocalTileUrlTemplate(),
            tileSourceMode = tileSourceMode,
            scsMapPackInfo = scsMapPackInfo,
            isNightMode = isNightMode,
            interpolatedPose = interpolatedPose,
            followMode = followMode,
            vehicleIconType = savedConfig.vehicleIcon,
            pois = pois,
            waypoints = waypoints,
            routeCandidates = routeCandidates,
            selectedRouteIndex = routingOptions.selectedAlternativeIndex,
            trackHistory = trackHistory,
            onUserPanStart = {
                if (followMode != CameraFollowMode.FREE_PAN) {
                    followMode = CameraFollowMode.FREE_PAN
                }
            },
            onMapOrPoiTapped = { selection ->
                activeMapSelection = selection
            }
        )

        // 可長按自由移動元件 1：頂部導航狀態列與延伸遙測提示卡
        val topNavOffset = hudOffsets[HudWidgetId.TOP_NAV_BANNER]
            ?: HudWidgetOffset(HudWidgetId.TOP_NAV_BANNER.defaultXFraction, HudWidgetId.TOP_NAV_BANNER.defaultYFraction)

        DraggableHudWidget(
            widgetId = HudWidgetId.TOP_NAV_BANNER,
            offsetFraction = topNavOffset,
            parentWidthPx = parentWidthPx,
            parentHeightPx = parentHeightPx,
            onOffsetCommitted = { id, newOffset ->
                hudOffsets[id] = newOffset
                prefsRepo.saveHudOffset(id, newOffset)
            },
            modifier = Modifier.widthIn(max = metrics.maxCardWidth)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (metrics.isTabletOrWide) 0.56f else 0.94f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xE6161B26), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF262F40), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val statusText = when (val st = connectionState) {
                            is ConnectionState.Connected -> "已連線 (${st.host}:${st.port}) • 2K @2x 超清"
                            is ConnectionState.Reconnecting -> "斷線自動重連中 (第 ${st.attempt} 次)..."
                            else -> "2K 內建地圖模式 • 長按任意 UI 可自由移動"
                        }
                        Text(
                            text = statusText,
                            color = if (connectionState is ConnectionState.Connected) Color(0xFF10B981) else Color(0xFF38BDF8),
                            fontSize = (11f * metrics.uiScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                        val activeRoute = routeCandidates.getOrNull(routingOptions.selectedAlternativeIndex)
                        Text(
                            text = activeRoute?.nextManeuverTextZh
                                ?: if (telemetry.hasGameData) "即時遙測導航中" else "等待遊戲數據（顯示本地保存/德國預設座標）",
                            color = Color(0xFFF8FAFC),
                            fontSize = (15f * metrics.uiScale).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                ExtendedTelemetryAlertsPanel(
                    telemetry = telemetry,
                    onNavigateToJobDestination = {
                        if (telemetry.job.destinationLat != 0.0) {
                            waypoints.clear()
                            waypoints.add(
                                RouteWaypoint(
                                    id = "job_dest",
                                    titleZh = "貨運終點：${telemetry.job.destinationCity}",
                                    latitude = telemetry.job.destinationLat,
                                    longitude = telemetry.job.destinationLon,
                                    type = PoiType.CITY
                                )
                            )
                            recalculateRoutes()
                        }
                    },
                    onHighlightNearestPoi = { poiType ->
                        val nearest = pois.firstOrNull { it.type == poiType }
                        if (nearest != null) {
                            activeMapSelection = nearest
                        }
                    }
                )
            }
        }

        // 可長按自由移動元件 2：獨立 FPS 計數器膠囊
        if (savedConfig.showFpsCounter) {
            val fpsOffset = hudOffsets[HudWidgetId.FPS_BADGE]
                ?: HudWidgetOffset(HudWidgetId.FPS_BADGE.defaultXFraction, HudWidgetId.FPS_BADGE.defaultYFraction)

            DraggableHudWidget(
                widgetId = HudWidgetId.FPS_BADGE,
                offsetFraction = fpsOffset,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                onOffsetCommitted = { id, newOffset ->
                    hudOffsets[id] = newOffset
                    prefsRepo.saveHudOffset(id, newOffset)
                }
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE60B0E14))
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$measuredFps FPS • 2K",
                        color = Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = (12f * metrics.uiScale).sp
                    )
                }
            }
        }

        // 可長按自由移動元件 3：雙圓形時速與限速儀表
        val speedOffset = hudOffsets[HudWidgetId.SPEED_CLUSTER]
            ?: HudWidgetOffset(HudWidgetId.SPEED_CLUSTER.defaultXFraction, HudWidgetId.SPEED_CLUSTER.defaultYFraction)

        DraggableHudWidget(
            widgetId = HudWidgetId.SPEED_CLUSTER,
            offsetFraction = speedOffset,
            parentWidthPx = parentWidthPx,
            parentHeightPx = parentHeightPx,
            onOffsetCommitted = { id, newOffset ->
                hudOffsets[id] = newOffset
                prefsRepo.saveHudOffset(id, newOffset)
            }
        ) {
            DualCircularSpeedCluster(
                speedKmh = interpolatedPose.speedKmh,
                speedLimitKmh = telemetry.speedLimitKmh,
                speedUnit = savedConfig.speedUnit,
                metrics = metrics,
                onToggleUnit = {
                    val nextUnit = if (savedConfig.speedUnit == SpeedUnit.KMH) SpeedUnit.MPH else SpeedUnit.KMH
                    savedConfig = savedConfig.copy(speedUnit = nextUnit)
                    prefsRepo.saveSpeedUnit(nextUnit)
                }
            )
        }

        // 可長按自由移動元件 4：右下角操作快捷列（回到卡車、視角、導入.zip、路徑點、設定）
        val controlsOffset = hudOffsets[HudWidgetId.QUICK_CONTROLS]
            ?: HudWidgetOffset(HudWidgetId.QUICK_CONTROLS.defaultXFraction, HudWidgetId.QUICK_CONTROLS.defaultYFraction)

        DraggableHudWidget(
            widgetId = HudWidgetId.QUICK_CONTROLS,
            offsetFraction = controlsOffset,
            parentWidthPx = parentWidthPx,
            parentHeightPx = parentHeightPx,
            onOffsetCommitted = { id, newOffset ->
                hudOffsets[id] = newOffset
                prefsRepo.saveHudOffset(id, newOffset)
            }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (followMode == CameraFollowMode.FREE_PAN) {
                    Button(
                        onClick = { followMode = CameraFollowMode.HEADING_UP },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Text("回到卡車", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            followMode = if (followMode == CameraFollowMode.HEADING_UP) {
                                CameraFollowMode.NORTH_UP
                            } else {
                                CameraFollowMode.HEADING_UP
                            }
                        },
                        modifier = Modifier.background(Color(0xD9161B26), RoundedCornerShape(20.dp))
                    ) {
                        Text(followMode.labelZh, color = Color.White, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = launchZipPicker,
                        modifier = Modifier.background(Color(0xD9161B26), RoundedCornerShape(20.dp))
                    ) {
                        Text("導入.zip地圖", color = Color(0xFF6EE7B7), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showWaypointSheet = !showWaypointSheet },
                        modifier = Modifier.background(Color(0xD9161B26), RoundedCornerShape(20.dp))
                    ) {
                        Text("路徑點 (${waypoints.size})", color = Color.White, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showSettingsSheet = !showSettingsSheet },
                        modifier = Modifier.background(Color(0xD9161B26), RoundedCornerShape(20.dp))
                    ) {
                        Text("設定", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // 點擊地圖或地標彈出的小視窗（平板與手機自適應置中）
        activeMapSelection?.let { sel ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 88.dp)
            ) {
                MapTapActionDialog(
                    selection = sel,
                    maxCardWidth = metrics.maxCardWidth,
                    onAddWaypoint = { pt ->
                        waypoints.add(
                            RouteWaypoint(
                                id = pt.id,
                                titleZh = pt.nameZh,
                                latitude = pt.latitude,
                                longitude = pt.longitude,
                                type = pt.type
                            )
                        )
                        activeMapSelection = null
                        recalculateRoutes()
                    },
                    onSetSingleDestination = { pt ->
                        waypoints.clear()
                        waypoints.add(
                            RouteWaypoint(
                                id = pt.id,
                                titleZh = pt.nameZh,
                                latitude = pt.latitude,
                                longitude = pt.longitude,
                                type = pt.type
                            )
                        )
                        activeMapSelection = null
                        recalculateRoutes()
                    },
                    onSaveFavorite = { pt ->
                        savedFavorites.add(
                            SavedFavoritePlace(
                                id = pt.id,
                                nameZh = pt.nameZh,
                                category = pt.type,
                                latitude = pt.latitude,
                                longitude = pt.longitude
                            )
                        )
                        activeMapSelection = null
                    },
                    onDismiss = { activeMapSelection = null }
                )
            }
        }

        // 路徑點排序與路線規劃抽屜（平板/橫向自動靠右或置中不滿版）
        if (showWaypointSheet) {
            Box(
                modifier = Modifier.align(
                    if (metrics.isTabletOrWide) Alignment.BottomEnd else Alignment.BottomCenter
                )
            ) {
                WaypointAndRoutingSheet(
                    waypoints = waypoints,
                    routingOptions = routingOptions,
                    routeCandidates = routeCandidates,
                    maxCardWidth = metrics.maxCardWidth,
                    onMoveWaypointUp = { index ->
                        if (index > 0) {
                            val item = waypoints.removeAt(index)
                            waypoints.add(index - 1, item)
                            recalculateRoutes()
                        }
                    },
                    onMoveWaypointDown = { index ->
                        if (index < waypoints.lastIndex) {
                            val item = waypoints.removeAt(index)
                            waypoints.add(index + 1, item)
                            recalculateRoutes()
                        }
                    },
                    onRemoveWaypoint = { id ->
                        waypoints.removeAll { it.id == id }
                        recalculateRoutes()
                    },
                    onToggleAvoidTolls = { checked ->
                        routingOptions = routingOptions.copy(avoidTolls = checked)
                        recalculateRoutes()
                    },
                    onToggleAvoidFerries = { checked ->
                        routingOptions = routingOptions.copy(avoidFerries = checked)
                        recalculateRoutes()
                    },
                    onSelectAlternativeRoute = { idx ->
                        routingOptions = routingOptions.copy(selectedAlternativeIndex = idx)
                    },
                    onClose = { showWaypointSheet = false }
                )
            }
        }

        // 系統設定、UI 位置重置、2K 地圖包與台灣語音模組 2 對照表面板
        if (showSettingsSheet) {
            Box(
                modifier = Modifier.align(
                    if (metrics.isTabletOrWide) Alignment.BottomEnd else Alignment.BottomCenter
                )
            ) {
                SettingsAndVoiceSheet(
                    config = savedConfig,
                    deviceMaxRefreshHz = frameRateController.maxSupportedRefreshRateHz,
                    tileSourceMode = tileSourceMode,
                    scsMapPackInfo = scsMapPackInfo,
                    mapImportStatusMessage = mapImportStatusMessage,
                    voiceEntries = voiceEntries,
                    missingVoiceNotice = missingVoiceNotice,
                    maxCardWidth = metrics.maxCardWidth,
                    onSelectTargetFps = { fpsOpt ->
                        savedConfig = savedConfig.copy(targetFps = fpsOpt)
                        prefsRepo.saveTargetFps(fpsOpt)
                    },
                    onToggleFpsCounter = { show ->
                        savedConfig = savedConfig.copy(showFpsCounter = show)
                        prefsRepo.saveFpsCounterVisibility(show)
                    },
                    onToggleLowBatterySaver = { enabled ->
                        savedConfig = savedConfig.copy(lowBatteryFrameDrop = enabled)
                        prefsRepo.saveLowBatteryFrameDrop(enabled)
                    },
                    onSelectVehicleIcon = { icon ->
                        savedConfig = savedConfig.copy(vehicleIcon = icon)
                        prefsRepo.saveVehicleIcon(icon)
                    },
                    onToggleKeepScreenOn = { keepOn ->
                        savedConfig = savedConfig.copy(keepScreenOn = keepOn)
                        prefsRepo.saveKeepScreenOn(keepOn)
                    },
                    onSelectTileSourceMode = { mode ->
                        tileSourceMode = mode
                    },
                    onPickMapZipFile = launchZipPicker,
                    onResetBuiltInMap = { scsTileMapManager.resetToBuiltInMap() },
                    onResetHudPositions = {
                        val resetMap = prefsRepo.resetHudOffsets()
                        hudOffsets.clear()
                        hudOffsets.putAll(resetMap)
                    },
                    onVoiceVolumeChange = { vol ->
                        savedConfig = savedConfig.copy(voiceVolume = vol)
                        prefsRepo.saveVoiceSettings(vol, savedConfig.voiceMuted)
                    },
                    onToggleVoiceMuted = { muted ->
                        savedConfig = savedConfig.copy(voiceMuted = muted)
                        prefsRepo.saveVoiceSettings(savedConfig.voiceVolume, muted)
                    },
                    onTestPlayVoiceEvent = { eventKey ->
                        voiceNavigator.triggerVoiceEvent(
                            eventKey = eventKey,
                            distanceBucketMeters = 0,
                            volume = savedConfig.voiceVolume,
                            muted = false
                        )
                    },
                    onClose = { showSettingsSheet = false }
                )
            }
        }
    }
}
