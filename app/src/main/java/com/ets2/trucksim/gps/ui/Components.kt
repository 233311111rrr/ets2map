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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ets2.trucksim.gps.data.SavedConnectionConfig
import com.ets2.trucksim.gps.model.ConnectionState
import com.ets2.trucksim.gps.model.HudWidgetId
import com.ets2.trucksim.gps.model.HudWidgetOffset
import com.ets2.trucksim.gps.model.MapPointSelection
import com.ets2.trucksim.gps.model.MapTileSourceMode
import com.ets2.trucksim.gps.model.NavRouteCandidate
import com.ets2.trucksim.gps.model.PoiType
import com.ets2.trucksim.gps.model.RouteWaypoint
import com.ets2.trucksim.gps.model.RoutingOptions
import com.ets2.trucksim.gps.model.ScsMapPackInfo
import com.ets2.trucksim.gps.model.SpeedUnit
import com.ets2.trucksim.gps.model.TargetFpsOption
import com.ets2.trucksim.gps.model.TruckTelemetryState
import com.ets2.trucksim.gps.model.VehicleIconType
import com.ets2.trucksim.gps.model.VoiceMappingEntry
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 跨手機 / 摺疊機 / 平板螢幕的自適應尺寸參數
 */
data class AdaptiveScreenMetrics(
    val isCompactPhone: Boolean,
    val isTabletOrWide: Boolean,
    val uiScale: Float,
    val limitCircleSize: Dp,
    val speedCircleSize: Dp,
    val maxCardWidth: Dp
)

@Composable
fun rememberAdaptiveScreenMetrics(maxWidth: Dp, maxHeight: Dp): AdaptiveScreenMetrics {
    val minDim = minOf(maxWidth, maxHeight)
    val isCompact = minDim < 360.dp
    val isTabletOrWide = maxWidth >= 640.dp
    val scale = when {
        minDim >= 600.dp -> 1.20f
        isCompact -> 0.86f
        else -> 1.0f
    }
    return AdaptiveScreenMetrics(
        isCompactPhone = isCompact,
        isTabletOrWide = isTabletOrWide,
        uiScale = scale,
        limitCircleSize = (70f * scale).dp,
        speedCircleSize = (82f * scale).dp,
        maxCardWidth = if (isTabletOrWide) 540.dp else maxWidth
    )
}

/**
 * 穩定不中斷的可長按自由拖曳 HUD 容器 (`DraggableHudWidget`)
 *
 * 修正重點：
 * 1. `pointerInput(widgetId)` 僅以 `widgetId` 為唯一固定 Key，搭配 `rememberUpdatedState` 讀取父容器與元件尺寸，
 *    徹底杜絕拖曳開始時因狀態或尺寸更新導致 `pointerInput` 手勢協程被重啟（不會再閃一下就消失）。
 * 2. 使用 `PointerEventPass.Initial` 階段攔截長按（按住滿 320ms）：
 *    - 短按 (<320ms) 完全不攔截，讓內部按鈕（設定、路徑點、切換單位等）100% 正常點擊。
 *    - 長按 (>=320ms) 後立即在 `Initial` 階段接管並消費所有移動事件 (`change.consume()`)，即使手指按在按鈕上也能絲滑拖動。
 * 3. 使用 `drawWithContent` 與 `graphicsLayer` 繪製高亮天藍色拖曳外框與微幅放大效果，**不改變元件實際排版尺寸 (Zero Layout Shift)**。
 */
@Composable
fun DraggableHudWidget(
    widgetId: HudWidgetId,
    offsetFraction: HudWidgetOffset,
    parentWidthPx: Float,
    parentHeightPx: Float,
    onOffsetCommitted: (HudWidgetId, HudWidgetOffset) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var widgetSize by remember(widgetId) { mutableStateOf(IntSize.Zero) }
    var isDragging by remember(widgetId) { mutableStateOf(false) }

    val safeParentW = parentWidthPx.coerceAtLeast(1f)
    val safeParentH = parentHeightPx.coerceAtLeast(1f)

    var dragX by remember(widgetId) {
        mutableFloatStateOf(offsetFraction.xFraction * safeParentW)
    }
    var dragY by remember(widgetId) {
        mutableFloatStateOf(offsetFraction.yFraction * safeParentH)
    }

    // 僅在非拖曳狀態下（如外部按下「重置 UI 位置」或旋轉螢幕時）同步外部比例座標
    LaunchedEffect(offsetFraction.xFraction, offsetFraction.yFraction, safeParentW, safeParentH) {
        if (!isDragging) {
            val maxW = (safeParentW - widgetSize.width).coerceAtLeast(0f)
            val maxH = (safeParentH - widgetSize.height).coerceAtLeast(0f)
            dragX = (offsetFraction.xFraction * safeParentW).coerceIn(0f, if (maxW > 0f) maxW else safeParentW)
            dragY = (offsetFraction.yFraction * safeParentH).coerceIn(0f, if (maxH > 0f) maxH else safeParentH)
        }
    }

    val currentParentW by rememberUpdatedState(safeParentW)
    val currentParentH by rememberUpdatedState(safeParentH)
    val currentWidgetSize by rememberUpdatedState(widgetSize)
    val currentOnCommit by rememberUpdatedState(onOffsetCommitted)
    val currentHaptic by rememberUpdatedState(haptic)

    Box(
        modifier = modifier
            .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
            .onSizeChanged { newSize ->
                widgetSize = newSize
                if (!isDragging && newSize.width > 0 && newSize.height > 0) {
                    val maxW = (currentParentW - newSize.width).coerceAtLeast(0f)
                    val maxH = (currentParentH - newSize.height).coerceAtLeast(0f)
                    dragX = dragX.coerceIn(0f, maxW)
                    dragY = dragY.coerceIn(0f, maxH)
                }
            }
            .graphicsLayer {
                scaleX = if (isDragging) 1.04f else 1.0f
                scaleY = if (isDragging) 1.04f else 1.0f
                alpha = if (isDragging) 0.94f else 1.0f
            }
            .drawWithContent {
                drawContent()
                if (isDragging) {
                    drawRoundRect(
                        color = Color(0x3838BDF8),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFF38BDF8),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            .pointerInput(widgetId) {
                val touchSlopPx = viewConfiguration.touchSlop
                val longPressTimeoutMs = 320L

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val pointerId = down.id
                    val startPos = down.position
                    var cancelledBeforeLongPress = false

                    // 等待 320ms 判定是否為長按；若期間手指抬起或大幅滑動則視為一般點擊/地圖手勢
                    val releasedOrMovedEarly = withTimeoutOrNull(longPressTimeoutMs) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                cancelledBeforeLongPress = true
                                break
                            }
                            val movedDist = hypot(
                                change.position.x - startPos.x,
                                change.position.y - startPos.y
                            )
                            if (movedDist > touchSlopPx * 1.5f) {
                                cancelledBeforeLongPress = true
                                break
                            }
                        }
                        true
                    }

                    if (releasedOrMovedEarly != null || cancelledBeforeLongPress) {
                        return@awaitEachGesture
                    }

                    // 已滿 320ms 且手指仍按住：正式進入長按自由拖曳模式！
                    isDragging = true
                    currentHaptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val delta = change.positionChange()
                            if (delta.x != 0f || delta.y != 0f) {
                                val limitX = (currentParentW - currentWidgetSize.width).coerceAtLeast(0f)
                                val limitY = (currentParentH - currentWidgetSize.height).coerceAtLeast(0f)
                                dragX = (dragX + delta.x).coerceIn(0f, limitX)
                                dragY = (dragY + delta.y).coerceIn(0f, limitY)
                            }
                            // 在 Initial 階段消費事件，防止底下的子按鈕誤觸或中斷拖曳
                            event.changes.forEach { it.consume() }
                        }
                    } finally {
                        isDragging = false
                        val pW = currentParentW.coerceAtLeast(1f)
                        val pH = currentParentH.coerceAtLeast(1f)
                        currentOnCommit(
                            widgetId,
                            HudWidgetOffset(
                                xFraction = (dragX / pW).coerceIn(0f, 0.94f),
                                yFraction = (dragY / pH).coerceIn(0f, 0.94f)
                            )
                        )
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * 1. 啟動與連線畫面（已移除驗收切換，自動適配手機與平板寬度）
 */
@Composable
fun StartupConnectionScreen(
    hostInput: String,
    portInput: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    connectionState: ConnectionState,
    scsMapPackInfo: ScsMapPackInfo,
    mapImportStatusMessage: String?,
    onPickMapZipFile: () -> Unit,
    onResetBuiltInMap: () -> Unit,
    onConnectClick: () -> Unit,
    onEnterOfflineBuiltInMap: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14)),
        contentAlignment = Alignment.Center
    ) {
        val metrics = rememberAdaptiveScreenMetrics(maxWidth, maxHeight)

        Column(
            modifier = Modifier
                .widthIn(max = if (metrics.isTabletOrWide) 560.dp else maxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (metrics.isCompactPhone) 14.dp else 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ETS2 TRUCKSIM GPS",
                color = Color(0xFFF8FAFC),
                fontSize = (24 * metrics.uiScale).sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "2K 超清 SCS_Map_Tiles 導航 • 台灣語音模組 2 (GPL v3)",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 內建 2K SCS_Map_Tiles 與導入 .zip 地圖檔入口卡片
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
                        text = "直接使用 APK 內建 2K 地圖 / 已導入 .zip 進入主畫面",
                        color = Color(0xFF93C5FD),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * SCS_Map_Tiles 2K 內建與 .zip 地圖包導入管理卡片
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
                text = "🗺️ 2K 高清地圖包管理 (Unicor-p/SCS_Map_Tiles)",
                color = Color(0xFF6EE7B7),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = if (scsMapPackInfo.isCustomZipLoaded) "自訂 .ZIP 2K 索引" else "2K @2x 超採樣",
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
            text = "畫質規格：${scsMapPackInfo.resolutionQualityLabel} • 已索引 ${scsMapPackInfo.indexedTileCount} 張高清圖磚",
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
                        text = "恢復內建 2K 地圖",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 2. 雙圓形時速與限速徽章（自動適配手機/平板尺寸，短按切換 km/h 與 mph、長按 0.3 秒可隨意拖曳移動）
 */
@Composable
fun DualCircularSpeedCluster(
    speedKmh: Float,
    speedLimitKmh: Int,
    speedUnit: SpeedUnit,
    metrics: AdaptiveScreenMetrics,
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
        horizontalArrangement = Arrangement.spacedBy((10f * metrics.uiScale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 限速圓形
        Box(
            modifier = Modifier
                .size(metrics.limitCircleSize)
                .clip(CircleShape)
                .background(if (isOverspeed) Color(0xFFEF4444) else Color(0xFFF8FAFC))
                .border(
                    width = (6.5f * metrics.uiScale).dp,
                    color = if (isOverspeed) Color(0xFFB91C1C) else Color(0xFFEF4444),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (speedLimitKmh > 0) "$convertedLimit" else "--",
                    color = if (isOverspeed) Color.White else Color(0xFF0F172A),
                    fontSize = (22f * metrics.uiScale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "限速",
                    color = if (isOverspeed) Color(0xFFFEE2E2) else Color(0xFF475569),
                    fontSize = (10f * metrics.uiScale).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 目前時速圓形
        Box(
            modifier = Modifier
                .size(metrics.speedCircleSize)
                .clip(CircleShape)
                .background(if (isOverspeed) Color(0xFF7F1D1D) else Color(0xE6161B26))
                .border(
                    width = (3f * metrics.uiScale).dp,
                    color = if (isOverspeed) Color(0xFFEF4444) else Color(0xFF3B82F6),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$convertedSpeed",
                    color = if (isOverspeed) Color(0xFFFCA5A5) else Color(0xFFF8FAFC),
                    fontSize = (26f * metrics.uiScale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = speedUnit.label,
                    color = Color(0xFF94A3B8),
                    fontSize = (11f * metrics.uiScale).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 3. 點擊地圖任意位置或 POI 地標彈出小視窗
 */
@Composable
fun MapTapActionDialog(
    selection: MapPointSelection,
    maxCardWidth: Dp,
    onAddWaypoint: (MapPointSelection) -> Unit,
    onSetSingleDestination: (MapPointSelection) -> Unit,
    onSaveFavorite: (MapPointSelection) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = maxCardWidth)
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
 * 4. 遙測延伸功能橫幅（貨運任務一鍵導航 + 低油量/車損/疲勞智慧提示）
 */
@Composable
fun ExtendedTelemetryAlertsPanel(
    telemetry: TruckTelemetryState,
    onNavigateToJobDestination: () -> Unit,
    onHighlightNearestPoi: (PoiType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

        if (telemetry.fuelRatio < 0.20f) {
            AlertStrip(
                textZh = "油量過低 (${(telemetry.fuelRatio * 100).roundToInt()}%)！建議立即前往附近加油站",
                buttonTextZh = "標出加油站",
                borderColor = Color(0xFFF59E0B),
                onClick = { onHighlightNearestPoi(PoiType.GAS_STATION) }
            )
        }

        if (telemetry.truckDamageRatio > 0.15f) {
            AlertStrip(
                textZh = "車輛損壞達 ${(telemetry.truckDamageRatio * 100).roundToInt()}%！請前往維修廠檢修",
                buttonTextZh = "標出維修廠",
                borderColor = Color(0xFFEF4444),
                onClick = { onHighlightNearestPoi(PoiType.SERVICE_STATION) }
            )
        }

        if (telemetry.nextRestStopMinutes in 1..60) {
            AlertStrip(
                textZh = "疲勞休息剩餘 ${telemetry.nextRestStopMinutes} 分鐘！請尋找休息站停車",
                buttonTextZh = "標出休息站",
                borderColor = Color(0xFFA855F7),
                onClick = { onHighlightNearestPoi(PoiType.REST_AREA) }
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
 * 5. 多路徑點排序清單與路線偏好面板（平板與橫向螢幕自動限制最佳閱讀寬度）
 */
@Composable
fun WaypointAndRoutingSheet(
    waypoints: List<RouteWaypoint>,
    routingOptions: RoutingOptions,
    routeCandidates: List<NavRouteCandidate>,
    maxCardWidth: Dp,
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
            .widthIn(max = maxCardWidth)
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
 * 6. 系統設定、UI 位置重置、2K 地圖模式與台灣語音模組 2 (47音檔) 設定面板
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
    maxCardWidth: Dp,
    onSelectTargetFps: (TargetFpsOption) -> Unit,
    onToggleFpsCounter: (Boolean) -> Unit,
    onToggleLowBatterySaver: (Boolean) -> Unit,
    onSelectVehicleIcon: (VehicleIconType) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onSelectTileSourceMode: (MapTileSourceMode) -> Unit,
    onPickMapZipFile: () -> Unit,
    onResetBuiltInMap: () -> Unit,
    onResetHudPositions: () -> Unit,
    onVoiceVolumeChange: (Float) -> Unit,
    onToggleVoiceMuted: (Boolean) -> Unit,
    onTestPlayVoiceEvent: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = maxCardWidth)
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
                text = "系統設定、2K 地圖包與台灣語音",
                color = Color(0xFFF8FAFC),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onClose) {
                Text("關閉")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 長按移動 UI 說明與一鍵重置按鈕
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0E14), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "✥ 主畫面 UI 自由長按移動",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "按住主畫面的時速圓形、導航列、FPS 或按鈕列約 0.3 秒即可隨意拖曳位置並自動記憶",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onResetHudPositions) {
                Text("重置 UI 位置", fontSize = 11.sp, color = Color(0xFF38BDF8))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2K 地圖來源與 .zip 地圖包導入管理
        ScsMapPackCard(
            scsMapPackInfo = scsMapPackInfo,
            mapImportStatusMessage = mapImportStatusMessage,
            onPickMapZipFile = onPickMapZipFile,
            onResetBuiltInMap = onResetBuiltInMap
        )

        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MapTileSourceMode.values().forEach { mode ->
                val selected = mode == tileSourceMode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
