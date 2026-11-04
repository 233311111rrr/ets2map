/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ets2.trucksim.gps.engine

import android.app.Activity
import android.os.Build
import android.view.Choreographer
import android.view.Display
import com.ets2.trucksim.gps.model.TargetFpsOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * 螢幕高刷新率控制器與 FPS 計數器 (FrameRateController)
 *
 * 1. 支援 30、60、120、144、165、185、無限（跟隨螢幕最高刷新率）七段設定。
 * 2. 自動偵測裝置螢幕支援的最高刷新率 (`maxSupportedRefreshRateHz`)；超過上限時自動以裝置最高刷新率運作。
 * 3. 支援低電量時自動降幀（可關閉）。
 * 4. 透過 `Choreographer` 驅動每幀位置內插並計算即時 FPS (`measuredFps`)。
 */
class FrameRateController(
    private val activity: Activity
) : Choreographer.FrameCallback {

    private val choreographer: Choreographer = Choreographer.getInstance()
    private var isRunning = false
    private var frameCallbackAction: ((Long) -> Unit)? = null

    private val _measuredFps = MutableStateFlow(60)
    val measuredFps: StateFlow<Int> = _measuredFps.asStateFlow()

    var maxSupportedRefreshRateHz: Float = 60f
        private set

    private var activeOption: TargetFpsOption = TargetFpsOption.FPS_120
    private var lowBatteryDropEnabled: Boolean = true
    private var isLowBattery: Boolean = false

    private var windowStartNanos: Long = 0L
    private var frameCountInWindow: Int = 0
    private var lastDispatchedFrameNanos: Long = 0L

    init {
        detectDeviceRefreshCapabilities()
    }

    private fun detectDeviceRefreshCapabilities() {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { activity.display }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        val supportedModes = display?.supportedModes
        maxSupportedRefreshRateHz = if (!supportedModes.isNullOrEmpty()) {
            supportedModes.maxOf { it.refreshRate }
        } else {
            display?.refreshRate ?: 60f
        }
    }

    /**
     * 套用使用者選擇的目標幀數與低電量自動降幀設定
     */
    fun applyFrameRateOption(
        option: TargetFpsOption,
        lowBatteryDropEnabled: Boolean,
        isCurrentlyLowBattery: Boolean
    ) {
        this.activeOption = option
        this.lowBatteryDropEnabled = lowBatteryDropEnabled
        this.isLowBattery = isCurrentlyLowBattery

        val targetHz = resolveEffectiveTargetHz()
        applyWindowDisplayMode(targetHz)
    }

    private fun resolveEffectiveTargetHz(): Float {
        if (lowBatteryDropEnabled && isLowBattery) {
            return minOf(60f, maxSupportedRefreshRateHz)
        }
        if (activeOption == TargetFpsOption.UNLIMITED || activeOption.fpsValue <= 0) {
            return maxSupportedRefreshRateHz
        }
        return minOf(activeOption.fpsValue.toFloat(), maxSupportedRefreshRateHz)
    }

    private fun applyWindowDisplayMode(targetHz: Float) {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { activity.display }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        val modes = display?.supportedModes ?: return
        val bestMode = modes
            .sortedWith(
                compareBy<Display.Mode> { abs(it.refreshRate - targetHz) }
                    .thenByDescending { it.physicalWidth * it.physicalHeight }
            )
            .firstOrNull() ?: return

        val attrs = activity.window.attributes
        if (attrs.preferredDisplayModeId != bestMode.modeId) {
            attrs.preferredDisplayModeId = bestMode.modeId
            activity.window.attributes = attrs
        }
    }

    /**
     * 啟動 Choreographer 每幀回呼
     */
    fun start(onFrame: (Long) -> Unit) {
        this.frameCallbackAction = onFrame
        if (isRunning) return
        isRunning = true
        windowStartNanos = 0L
        frameCountInWindow = 0
        lastDispatchedFrameNanos = 0L
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        isRunning = false
        frameCallbackAction = null
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        val effectiveHz = resolveEffectiveTargetHz()
        val minIntervalNanos = if (activeOption == TargetFpsOption.UNLIMITED) {
            0L
        } else {
            (1_000_000_000f / effectiveHz).toLong() - 1_500_000L
        }

        if (lastDispatchedFrameNanos == 0L || (frameTimeNanos - lastDispatchedFrameNanos) >= minIntervalNanos) {
            lastDispatchedFrameNanos = frameTimeNanos

            if (windowStartNanos == 0L) {
                windowStartNanos = frameTimeNanos
            }
            frameCountInWindow++
            val elapsed = frameTimeNanos - windowStartNanos
            if (elapsed >= 500_000_000L) {
                _measuredFps.value = ((frameCountInWindow * 1_000_000_000L) / elapsed).toInt()
                frameCountInWindow = 0
                windowStartNanos = frameTimeNanos
            }

            frameCallbackAction?.invoke(frameTimeNanos)
        }

        choreographer.postFrameCallback(this)
    }
}
