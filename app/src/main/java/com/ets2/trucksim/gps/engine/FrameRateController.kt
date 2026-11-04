// SPDX-License-Identifier: GPL-3.0-only
package com.ets2.trucksim.gps.engine

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.Display
import android.view.Surface
import android.view.SurfaceView
import com.ets2.trucksim.gps.model.TargetFpsOption
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 螢幕高刷新率控制器與 FPS 監測器 (FrameRateController)
 *
 * 【2-2 階段核心實作】
 * 1. 支援 30、60、120、144、165、185、無限 七檔幀數設定。
 * 2. 自動偵測裝置螢幕支援的所有 Display.Mode 最高刷新率；若選項超過螢幕支援上限，
 *    回報「裝置不支援」並自動以裝置硬體最高刷新率運作。
 * 3. Android 11 (API 30) 以上透過 Surface.setFrameRate() 與 Window.attributes.preferredDisplayModeId
 *    雙重鎖定目標刷新率；並利用 Choreographer 執行幀率步進與真實 FPS / FrameTime (ms) 統計。
 * 4. 支援「低電量時自動降幀（<= 20% 或省電模式時降至 30 FPS）」且可自由開關。
 */
class FrameRateController(
    private val activity: Activity,
    private val onFrameUpdate: (frameTimeNanos: Long, currentFps: Int, frameTimeMs: Float) -> Unit
) : Choreographer.FrameCallback {

    private var choreographer: Choreographer = Choreographer.getInstance()
    private var isRunning = false

    private var selectedOption: TargetFpsOption = TargetFpsOption.FPS_120
    private var lowBatteryAutoDropEnabled: Boolean = true
    private var isBatteryLowOrPowerSave: Boolean = false

    // 裝置硬體支援之刷新率清單與上限
    var maxDeviceRefreshRate: Float = 60f
        private set
    var supportedRefreshRates: List<Int> = listOf(60)
        private set

    // FPS 統計緩衝（無每幀物件配置）
    private var lastFrameNanos: Long = 0L
    private var fpsWindowStartNanos: Long = 0L
    private var framesInWindow: Int = 0
    var currentMeasuredFps: Int = 60
        private set
    var currentFrameTimeMs: Float = 16.6f
        private set

    private var boundSurfaceView: SurfaceView? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSave = powerManager?.isPowerSaveMode == true

            val newLowState = (batteryPct <= 20) || isPowerSave
            if (newLowState != isBatteryLowOrPowerSave) {
                isBatteryLowOrPowerSave = newLowState
                applyHardwareFrameRate()
            }
        }
    }

    init {
        queryDeviceDisplayModes()
    }

    /**
     * 讀取手機螢幕支援的所有刷新率與最高上限
     */
    fun queryDeviceDisplayModes() {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        val modes = display?.supportedModes
        if (!modes.isNullOrEmpty()) {
            maxDeviceRefreshRate = modes.maxOf { it.refreshRate }
            supportedRefreshRates = modes.map { it.refreshRate.roundToInt() }.distinct().sorted()
        } else {
            maxDeviceRefreshRate = display?.refreshRate ?: 60f
            supportedRefreshRates = listOf(maxDeviceRefreshRate.roundToInt())
        }
    }

    /**
     * 綁定 MapLibre 底層 SurfaceView 以套用 Android 11+ Surface.setFrameRate
     */
    fun bindMapSurfaceView(surfaceView: SurfaceView?) {
        boundSurfaceView = surfaceView
        applyHardwareFrameRate()
    }

    /**
     * 設定目標幀數與低電量自動降幀開關
     */
    fun updateFrameRateSettings(
        option: TargetFpsOption,
        lowBatteryDrop: Boolean
    ) {
        selectedOption = option
        lowBatteryAutoDropEnabled = lowBatteryDrop
        applyHardwareFrameRate()
    }

    /**
     * 取得目前實際生效的目標刷新率 (考慮裝置上限與低電量自動降幀)
     */
    fun getEffectiveTargetHz(): Float {
        if (lowBatteryAutoDropEnabled && isBatteryLowOrPowerSave) {
            return 30f
        }
        if (selectedOption == TargetFpsOption.UNLIMITED) {
            return maxDeviceRefreshRate
        }
        return minOf(selectedOption.targetHz.toFloat(), maxDeviceRefreshRate)
    }

    /**
     * 回傳設定頁面顯示用的狀態描述（標示「裝置不支援」並說明自動以裝置上限運作）
     */
    fun getOptionStatusText(option: TargetFpsOption): String {
        if (option == TargetFpsOption.UNLIMITED) {
            return "不設上限，跟隨螢幕最高刷新率 (${maxDeviceRefreshRate.roundToInt()} Hz)"
        }
        val supported = option.isSupportedByDevice(maxDeviceRefreshRate)
        return if (supported) {
            "裝置支援 (${option.targetHz} Hz)"
        } else {
            "裝置不支援（螢幕上限 ${maxDeviceRefreshRate.roundToInt()} Hz，將自動以裝置上限運作）"
        }
    }

    /**
     * 套用 Window preferredDisplayModeId 與 Android 11+ Surface.setFrameRate
     */
    private fun applyHardwareFrameRate() {
        val effectiveHz = getEffectiveTargetHz()

        // 1. 設定 Window preferredDisplayModeId 切換硬體螢幕刷新率
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

        val modes = display?.supportedModes
        if (!modes.isNullOrEmpty()) {
            // 尋找最接近 effectiveHz 且解析度最高的 Display.Mode
            val bestMode = modes
                .sortedWith(
                    compareBy<Display.Mode> { abs(it.refreshRate - effectiveHz) }
                        .thenByDescending { it.physicalWidth * it.physicalHeight }
                )
                .firstOrNull()

            if (bestMode != null) {
                val params = activity.window.attributes
                if (params.preferredDisplayModeId != bestMode.modeId) {
                    params.preferredDisplayModeId = bestMode.modeId
                    activity.window.attributes = params
                }
            }
        }

        // 2. Android 11 (API 30) 以上呼叫 Surface.setFrameRate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val surface: Surface? = boundSurfaceView?.holder?.surface
            if (surface != null && surface.isValid) {
                val compatibility = if (selectedOption == TargetFpsOption.UNLIMITED) {
                    Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
                } else {
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                }
                surface.setFrameRate(effectiveHz, compatibility)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        lastFrameNanos = 0L
        fpsWindowStartNanos = 0L
        framesInWindow = 0
        activity.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        applyHardwareFrameRate()
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        choreographer.removeFrameCallback(this)
        runCatching { activity.unregisterReceiver(batteryReceiver) }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        if (lastFrameNanos != 0L) {
            val deltaNanos = frameTimeNanos - lastFrameNanos
            currentFrameTimeMs = (deltaNanos / 1_000_000.0f)
        }
        lastFrameNanos = frameTimeNanos

        if (fpsWindowStartNanos == 0L) {
            fpsWindowStartNanos = frameTimeNanos
        }
        framesInWindow++
        val windowElapsed = frameTimeNanos - fpsWindowStartNanos
        if (windowElapsed >= 500_000_000L) { // 每 0.5 秒更新一次 FPS 讀數
            currentMeasuredFps = ((framesInWindow * 1_000_000_000L) / windowElapsed).toInt()
            framesInWindow = 0
            fpsWindowStartNanos = frameTimeNanos
        }

        onFrameUpdate(frameTimeNanos, currentMeasuredFps, currentFrameTimeMs)
        choreographer.postFrameCallback(this)
    }
}
