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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ets2.trucksim.gps.data.SavedConnectionConfig
import com.ets2.trucksim.gps.model.ConnectionState
import com.ets2.trucksim.gps.model.DeliveryStage
import com.ets2.trucksim.gps.model.MapPointSelection
import com.ets2.trucksim.gps.model.MapTileSourceMode
import com.ets2.trucksim.gps.model.NavRouteCandidate
import com.ets2.trucksim.gps.model.RouteWaypoint
import com.ets2.trucksim.gps.model.RoutingOptions
import com.ets2.trucksim.gps.model.ScsMapPackInfo
import com.ets2.trucksim.gps.model.SpeedUnit
import com.ets2.trucksim.gps.model.TargetFpsOption
import com.ets2.trucksim.gps.model.TruckTelemetryState
import com.ets2.trucksim.gps.model.VehicleIconType
import com.ets2.trucksim.gps.model.VoiceMappingEntry
import kotlin.math.roundToInt

/**
 * 1. 啟動與連線畫面（含 APK 內建 SCS_Map_Tiles 與導入 .zip 地圖檔入口）
 */
@Composable
fun StartupConnectionScreen(
    hostInput: String,
    portInput: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    connectionState: ConnectionState,
    activeStage: DeliveryStage,
    onStageChange: (DeliveryStage) -> Unit,
    scsMapPackInfo: ScsMapPackInfo,
    mapImportStatusMessage: String?,
    onPickMapZipFile: () -> Unit,
    onResetBuiltInMap: () -> Unit,
    onConnectClick: () -> Unit,
    onEnterOfflineBuiltInMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ETS2 TRUCKSIM GPS",
            color = Color(0xFFF8FAFC),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "電腦端遙測與內建 SCS_Map_Tiles 導航系統 (GPL v3)",
            color = Color(0xFF94A3B8),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 分階段驗收切換器 (2-1 ~ 2-4)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B26), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF262F40), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "分階段驗收模式切換：",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeliveryStage.values().forEach { stage ->
                    val selected = stage == activeStage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF3B82F6) else Color(0xFF0B0E14))
                            .clickable { onStageChange(stage) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stage.code,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = activeStage.subtitleZh,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 內建 SCS_Map_Tiles 與導入 .zip 地圖檔入口卡片
        ScsMapPackCard(
            scsMapPackInfo = scsMapPackInfo,
            mapImportStatusMessage = mapImportStatusMessage,
            onPickMapZipFile = onPickMapZipFile,
            onResetBuiltInMap = onResetBuiltInMap
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161B26), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF262F40), RoundedCornerShape(12.dp))
                .padding(18.dp)
        ) {
            Text(
                text = "請輸入電腦端 Node.js 伺服器 IP 與埠號",
                color = Color(0xFFF8FAFC),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = hostInput,
                onValueChange = onHostChange,
                label = { Text("電腦端 IP 位址 (例如 192.168.1.100)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = portInput,
                onValueChange = onPortChange,
                label = { Text("伺服器埠號 (預設 3000)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (connectionState) {
                is ConnectionState.Connecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF3B82F6),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "正在連線並等待電腦端伺服器回應 (${connectionState.host}:${connectionState.port})...",
                            color = Color(0xFF93C5FD),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is ConnectionState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3F1D24), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = connectionState.messageZh,
                            color = Color(0xFFFCA5A5),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                else -> Unit
            }

            Button(
                onClick = onConnectClick,
                enabled = connectionState !is ConnectionState.Connecting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (connectionState is ConnectionState.Error) "重試連線至電腦端" else "連線並驗證伺服器",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onEnterOfflineBuiltInMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = "直接使用 APK 內建 / 已導入 .zip 地圖進入主畫面",
                    color = Color(0xFF93C5FD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * SCS_Map_Tiles 內建與 .zip 地圖包導入管理卡片
 */
@Composable
fun ScsMapPackCard(
    scsMapPackInfo: ScsMapPackInfo,
    mapImportStatusMessage: String?,
    onPickMapZipFile: () -> Unit,
    onResetBuiltInMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B26), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🗺️ ETS2 地圖包管理 (Unicor-p/SCS_Map_Tiles)",
                color = Color(0xFF6EE7B7),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = if (scsMapPackInfo.isCustomZipLoaded) "自訂 .ZIP 索引中" else "APK 內建地圖",
                color = Color(0xFF10B981),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "目前圖磚包：${scsMapPackInfo.zipFileName}",
            color = Color(0xFFF8FAFC),
            fontSize = 12.sp
        )
        Text(
            text = "遊戲版本：${scsMapPackInfo.gameName} (${scsMapPackInfo.mapVersion}) • Zoom ${scsMapPackInfo.minZoom}~${scsMapPackInfo.maxZoom} (${scsMapPackInfo.tileSize}px) • 已索引 ${scsMapPackInfo.indexedTileCount} 張圖磚",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
        )

        if (!mapImportStatusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mapImportStatusMessage,
                color = Color(0xFFFDE68A),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPickMapZipFile,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "導入 .zip 地圖檔 (免解壓)",
                    color = Color(0xFF052E16),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            if (scsMapPackInfo.isCustomZipLoaded) {
                OutlinedButton(
                    onClick = onResetBuiltInMap
                ) {
                    Text(
                        text = "恢復內建地圖",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 2. 雙圓形時速與限速徽章（支援 km/h 與 mph 點擊切換、超速自動變色）
 */
@Composable
fun DualCircularSpeedCluster(
    speedKmh: Float,
    speedLimitKmh: Int,
    speedUnit: SpeedUnit,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val convertedSpeed = (speedKmh * speedUnit.multiplierFromKmh).roundToInt()
    val convertedLimit = (speedLimitKmh * speedUnit.multiplierFromKmh).roundToInt()
    val isOverspeed = speedLimitKmh > 0 && speedKmh > speedLimitKmh + 1.5f

    Row(
        modifier = modifier
            .clickable { onToggleUnit() }
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 限速圓形（白底紅圈標準歐陸標誌，超速時外框與底色閃耀警示紅）
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (isOverspeed) Color(0xFFEF4444) else Color(0xFFF8FAFC))
                .border(
                    width = 7.dp,
                    color = if (isOverspeed) Color(0xFFB91C1C) else Color(0xFFEF4444),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (speedLimitKmh > 0) "$convertedLimit" else "--",
                    color = if (isOverspeed) Color.White else Color(0xFF0F172A),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "限速",
                    color = if (isOverspeed) Color(0xFFFEE2E2) else Color(0xFF475569),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 目前時速圓形（深色高對比戰術儀表，超速時變為琥珀紅底色）
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(if (isOverspeed) Color(0xFF7F1D1D) else Color(0xE6161B26))
                .border(
                    width = 3.dp,
                    color = if (isOverspeed) Color(0xFFEF4444) else Color(0xFF3B82F6),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$convertedSpeed",
                    color = if (isOverspeed) Color(0xFFFCA5A5) else Color(0xFFF8FAFC),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = speedUnit.label,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 3. 點擊地圖任意位置或 POI 地標彈出小視窗
 * 選項：「新增路徑點」或「重新標點（改成唯一目的地）」
 */
@Composable
fun MapTapActionDialog(
    selection: MapPointSelection,
    onAddWaypoint: (MapPointSelection) -> Unit,
    onSetSingleDestination: (MapPointSelection) -> Unit,
    onSaveFavorite: (MapPointSelection) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF2161B26), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selection.type.labelZh,
                    color = Color(selection.type.badgeColorHex),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = selection.nameZh,
                    color = Color(0xFFF8FAFC),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format("座標：%.4f, %.4f", selection.latitude, selection.longitude),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            OutlinedButton(onClick = { onSaveFavorite(selection) }) {
                Text("收藏地點", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onAddWaypoint(selection) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                modifier = Modifier.weight(1f)
            ) {
                Text("新增路徑點", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onSetSingleDestination(selection) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                modifier = Modifier.weight(1f)
            ) {
                Text("重新標點 (唯一目的地)", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("關閉視窗", color = Color(0xFF94A3B8))
        }
    }
}

/**
 * 4. 2-4 遙測延伸功能橫幅（貨運任務一鍵導航 + 低油量/車損/疲勞智慧提示）
 */
@Composable
fun ExtendedTelemetryAlertsPanel(
    telemetry: TruckTelemetryState,
    onNavigateToJobDestination: () -> Unit,
    onHighlightNearestPoi: (com.ets2.trucksim.gps.model.PoiType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 貨運資訊與一鍵導航
        if (telemetry.job.active && telemetry.job.destinationCity.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6161B26), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "貨運任務：${telemetry.job.cargoName} (${telemetry.job.sourceCity} → ${telemetry.job.destinationCity})",
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "剩餘截止時間：${telemetry.job.remainingMinutes} 分鐘",
                        color = Color(0xFF93C5FD),
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = onNavigateToJobDestination,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("一鍵導航到目的地", fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // 油量過低警告 (< 20%)
        if (telemetry.fuelRatio < 0.20f) {
            AlertStrip(
                textZh = "油量過低 (${(telemetry.fuelRatio * 100).roundToInt()}%)！建議立即前往附近加油站",
                buttonTextZh = "標出加油站",
                borderColor = Color(0xFFF59E0B),
                onClick = { onHighlightNearestPoi(com.ets2.trucksim.gps.model.PoiType.GAS_STATION) }
            )
        }

        // 車損超過門檻警告 (> 15%)
        if (telemetry.truckDamageRatio > 0.15f) {
            AlertStrip(
                textZh = "車輛損壞達 ${(telemetry.truckDamageRatio * 100).roundToInt()}%！請前往維修廠檢修",
                buttonTextZh = "標出維修廠",
                borderColor = Color(0xFFEF4444),
                onClick = { onHighlightNearestPoi(com.ets2.trucksim.gps.model.PoiType.SERVICE_STATION) }
            )
        }

        // 疲勞休息時間不足警告 (< 60 分鐘)
        if (telemetry.nextRestStopMinutes in 1..60) {
            AlertStrip(
                textZh = "疲勞休息剩餘 ${telemetry.nextRestStopMinutes} 分鐘！請尋找休息站停車",
                buttonTextZh = "標出休息站",
                borderColor = Color(0xFFA855F7),
                onClick = { onHighlightNearestPoi(com.ets2.trucksim.gps.model.PoiType.REST_AREA) }
            )
        }
    }
}

@Composable
private fun AlertStrip(
    textZh: String,
    buttonTextZh: String,
    borderColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF21E1B2E), RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = textZh,
            color = Color(0xFFF8FAFC),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onClick) {
            Text(buttonTextZh, fontSize = 11.sp, color = borderColor)
        }
    }
}

/**
 * 5. 多路徑點排序清單、路線偏好與設定面板
 */
@Composable
fun WaypointAndRoutingSheet(
    waypoints: List<RouteWaypoint>,
    routingOptions: RoutingOptions,
    routeCandidates: List<NavRouteCandidate>,
    onMoveWaypointUp: (Int) -> Unit,
    onMoveWaypointDown: (Int) -> Unit,
    onRemoveWaypoint: (String) -> Unit,
    onToggleAvoidTolls: (Boolean) -> Unit,
    onToggleAvoidFerries: (Boolean) -> Unit,
    onSelectAlternativeRoute: (Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF5161B26), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "路徑點清單與路線規劃設定",
                color = Color(0xFFF8FAFC),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onClose) {
                Text("完成")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = routingOptions.avoidTolls,
                    onCheckedChange = onToggleAvoidTolls
                )
                Text("避開收費道路", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = routingOptions.avoidFerries,
                    onCheckedChange = onToggleAvoidFerries
                )
                Text("避開渡輪", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 替代路線選擇
        if (routeCandidates.isNotEmpty()) {
            Text("可用路線與替代方案：", color = Color(0xFF94A3B8), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                routeCandidates.forEachIndexed { idx, candidate ->
                    val active = idx == routingOptions.selectedAlternativeIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0xFF1E3A8A) else Color(0xFF0B0E14))
                            .border(
                                1.dp,
                                if (active) Color(0xFF3B82F6) else Color(0xFF334155),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectAlternativeRoute(idx) }
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = candidate.summaryZh,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${String.format("%.1f", candidate.distanceKm)} km • 約 ${candidate.durationMinutes} 分",
                                color = Color(0xFF93C5FD),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("途經路徑點（可上下排序或刪除）：", color = Color(0xFF94A3B8), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))

        if (waypoints.isEmpty()) {
            Text(
                text = "目前無設定路徑點，請直接點擊地圖任意位置或地標新增。",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
        } else {
            waypoints.forEachIndexed { index, wp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFF0B0E14), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${index + 1}. ${wp.titleZh}",
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = { onMoveWaypointUp(index) },
                            enabled = index > 0
                        ) {
                            Text("↑")
                        }
                        OutlinedButton(
                            onClick = { onMoveWaypointDown(index) },
                            enabled = index < waypoints.lastIndex
                        ) {
                            Text("↓")
                        }
                        OutlinedButton(onClick = { onRemoveWaypoint(wp.id) }) {
                            Text("刪除", color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 6. 幀數設定 (30/60/120/144/165/185/無限)、地圖包管理與台灣語音模組 2 (47音檔) 設定面板
 */
@Composable
fun SettingsAndVoiceSheet(
    config: SavedConnectionConfig,
    deviceMaxRefreshHz: Float,
    tileSourceMode: MapTileSourceMode,
    scsMapPackInfo: ScsMapPackInfo,
    mapImportStatusMessage: String?,
    voiceEntries: List<VoiceMappingEntry>,
    missingVoiceNotice: String?,
    onSelectTargetFps: (TargetFpsOption) -> Unit,
    onToggleFpsCounter: (Boolean) -> Unit,
    onToggleLowBatterySaver: (Boolean) -> Unit,
    onSelectVehicleIcon: (VehicleIconType) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSelectTileSourceMode: (MapTileSourceMode) -> Unit,
    onPickMapZipFile: () -> Unit,
    onResetBuiltInMap: () -> Unit,
    onVoiceVolumeChange: (Float) -> Unit,
    onToggleVoiceMuted: (Boolean) -> Unit,
    onTestPlayVoiceEvent: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(560.dp)
            .background(Color(0xF7161B26), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "系統設定、地圖包與台灣語音對照表",
                color = Color(0xFFF8FAFC),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onClose) {
                Text("關閉")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 地圖來源與 .zip 地圖包導入管理
        ScsMapPackCard(
            scsMapPackInfo = scsMapPackInfo,
            mapImportStatusMessage = mapImportStatusMessage,
            onPickMapZipFile = onPickMapZipFile,
            onResetBuiltInMap = onResetBuiltInMap
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapTileSourceMode.values().forEach { mode ->
                val selected = mode == tileSourceMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFF1E3A8A) else Color(0xFF0B0E14))
                        .border(
                            1.dp,
                            if (selected) Color(0xFF3B82F6) else Color(0xFF334155),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectTileSourceMode(mode) }
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = mode.labelZh,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mode.descZh,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "幀數設定 (裝置螢幕最高支援 ${deviceMaxRefreshHz.roundToInt()} Hz)",
            color = Color(0xFF93C5FD),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        TargetFpsOption.values().forEach { option ->
            val supported = option == TargetFpsOption.UNLIMITED ||
                option.fpsValue <= deviceMaxRefreshHz.roundToInt() + 1
            val selected = option == config.targetFps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0xFF1E3A8A) else Color(0xFF0B0E14))
                    .clickable { onSelectTargetFps(option) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.labelZh,
                    color = if (supported) Color.White else Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = if (supported) {
                        if (selected) "已啟用" else "支援"
                    } else {
                        "裝置不支援（自動以 ${deviceMaxRefreshHz.roundToInt()}Hz 運作）"
                    },
                    color = if (supported) Color(0xFF10B981) else Color(0xFFF59E0B),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("顯示即時 FPS 計數器", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Switch(checked = config.showFpsCounter, onCheckedChange = onToggleFpsCounter)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("低電量時自動降幀 (60 FPS)", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Switch(checked = config.lowBatteryFrameDrop, onCheckedChange = onToggleLowBatterySaver)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("導航時保持螢幕常亮", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Switch(checked = config.keepScreenOn, onCheckedChange = onToggleKeepScreenOn)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("自車標記圖示切換（依車頭方向旋轉）：", color = Color(0xFF93C5FD), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VehicleIconType.values().forEach { type ->
                val active = type == config.vehicleIcon
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Color(0xFF3B82F6) else Color(0xFF0B0E14))
                        .clickable { onSelectVehicleIcon(type) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(type.labelZh, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "語音導航（ETS2 Google Map 台灣語音模組 2 • 共 ${voiceEntries.size} 個音檔）",
            color = Color(0xFF93C5FD),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "音檔專屬目錄：app/src/main/assets/voice_tw/（已內建全部 47 個 .wav 實體音檔）",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
        )

        if (!missingVoiceNotice.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = missingVoiceNotice,
                color = Color(0xFFFBBF24),
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("語音靜音開關", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            Switch(checked = config.voiceMuted, onCheckedChange = onToggleVoiceMuted)
        }

        Text("語音音量：${(config.voiceVolume * 100).roundToInt()}%", color = Color(0xFFE2E8F0), fontSize = 12.sp)
        Slider(
            value = config.voiceVolume,
            onValueChange = onVoiceVolumeChange,
            valueRange = 0f..1f
        )

        Spacer(modifier = Modifier.height(6.dp))
        voiceEntries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(Color(0xFF0B0E14), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${entry.fileName} (${entry.originalFileName})",
                        color = Color(0xFFF8FAFC),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${entry.descriptionZh} [${entry.eventKey}]",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                OutlinedButton(onClick = { onTestPlayVoiceEvent(entry.eventKey) }) {
                    Text(
                        text = if (entry.fileExistsInAssets) "試聽" else "缺音檔",
                        fontSize = 11.sp,
                        color = if (entry.fileExistsInAssets) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}
