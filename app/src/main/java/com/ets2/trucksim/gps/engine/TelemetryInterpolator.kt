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

import com.ets2.trucksim.gps.model.TruckTelemetryState
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * 經 Choreographer 補間後的平滑卡車位置與姿態
 */
data class InterpolatedPose(
    val latitude: Double,
    val longitude: Double,
    val headingDeg: Float,
    val speedKmh: Float
)

/**
 * 卡車座標與車頭方位平滑內插器 (TelemetryInterpolator)
 *
 * 1. 執行緒安全無鎖讀取：背景 IO 執行緒收到 WebSocket 遙測時透過 AtomicReference 更新前後快照，
 *    UI 渲染執行緒於 Choreographer 每幀回呼時無鎖計算內插座標。
 * 2. 最短角度差旋轉內插：處理 359° -> 1° 跨正北方的平滑過渡，避免卡車圖示瞬間翻轉。
 */
class TelemetryInterpolator {

    private data class TimedSample(
        val latitude: Double,
        val longitude: Double,
        val headingDeg: Float,
        val speedKmh: Float,
        val timestampNanos: Long
    )

    private data class SampleWindow(
        val prev: TimedSample,
        val curr: TimedSample,
        val intervalNanos: Long
    )

    private val initialSample = TimedSample(
        latitude = 52.5200,
        longitude = 13.4050,
        headingDeg = 0f,
        speedKmh = 0f,
        timestampNanos = System.nanoTime()
    )

    private val windowRef = AtomicReference(
        SampleWindow(
            prev = initialSample,
            curr = initialSample,
            intervalNanos = 50_000_000L // 預設 20Hz (50ms)
        )
    )

    /**
     * 立即定位至指定座標（如首次載入本地保存座標或德國隨機城市）
     */
    fun snapTo(latitude: Double, longitude: Double, headingDeg: Float) {
        val now = System.nanoTime()
        val sample = TimedSample(
            latitude = latitude,
            longitude = longitude,
            headingDeg = headingDeg,
            speedKmh = 0f,
            timestampNanos = now
        )
        windowRef.set(SampleWindow(sample, sample, 50_000_000L))
    }

    /**
     * 當收到新遙測狀態時更新補間視窗
     */
    fun onTelemetrySampleReceived(telemetry: TruckTelemetryState) {
        val currentWindow = windowRef.get()
        val nowNanos = System.nanoTime()

        val targetLat = if (telemetry.hasGameData) telemetry.latitude else currentWindow.curr.latitude
        val targetLon = if (telemetry.hasGameData) telemetry.longitude else currentWindow.curr.longitude
        val targetHeading = if (telemetry.hasGameData) telemetry.headingDeg else currentWindow.curr.headingDeg

        val nextSample = TimedSample(
            latitude = targetLat,
            longitude = targetLon,
            headingDeg = targetHeading,
            speedKmh = telemetry.speedKmh,
            timestampNanos = nowNanos
        )

        val deltaNanos = (nowNanos - currentWindow.curr.timestampNanos).coerceIn(10_000_000L, 250_000_000L)

        // 若瞬間跨越距離過大（例如快速移動至其他城市車庫），直接瞬移不補間
        val latDiff = abs(nextSample.latitude - currentWindow.curr.latitude)
        val lonDiff = abs(nextSample.longitude - currentWindow.curr.longitude)
        val prevSample = if (latDiff > 0.05 || lonDiff > 0.05) nextSample else currentWindow.curr

        windowRef.set(
            SampleWindow(
                prev = prevSample,
                curr = nextSample,
                intervalNanos = deltaNanos
            )
        )
    }

    /**
     * 由 Choreographer 每幀呼叫，計算平滑內插位置與方位角
     */
    fun sampleAtFrame(frameTimeNanos: Long): InterpolatedPose {
        val win = windowRef.get()
        val elapsed = frameTimeNanos - win.curr.timestampNanos
        val t = (elapsed.toFloat() / win.intervalNanos.toFloat()).coerceIn(0f, 1f)

        val lat = win.prev.latitude + (win.curr.latitude - win.prev.latitude) * t.toDouble()
        val lon = win.prev.longitude + (win.curr.longitude - win.prev.longitude) * t.toDouble()
        val heading = lerpAngleDeg(win.prev.headingDeg, win.curr.headingDeg, t)
        val speed = win.prev.speedKmh + (win.curr.speedKmh - win.prev.speedKmh) * t

        return InterpolatedPose(
            latitude = lat,
            longitude = lon,
            headingDeg = heading,
            speedKmh = speed
        )
    }

    private fun lerpAngleDeg(startDeg: Float, endDeg: Float, fraction: Float): Float {
        var delta = (endDeg - startDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        val result = (startDeg + delta * fraction) % 360f
        return if (result < 0f) result + 360f else result
    }
}
