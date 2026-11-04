// SPDX-License-Identifier: GPL-3.0-only
package com.ets2.trucksim.gps.engine

import com.ets2.trucksim.gps.model.InterpolatedPose
import com.ets2.trucksim.gps.model.TelemetryFrame
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * 卡車座標與車頭方位平滑內插器 (TelemetryInterpolator)
 *
 * 【流暢度最佳化設計】
 * 1. 執行緒安全無鎖讀取：IO 執行緒收到 WebSocket 遙測時透過 AtomicReference 更新前後兩筆遙測快照 (prevFrame, currFrame)，
 *    UI 渲染執行緒於 Choreographer 每幀回呼時無鎖讀取並計算內插值。
 * 2. 依遙測更新頻率自適應補間：自動量測前後兩筆封包時間間隔 (expectedIntervalNanos)，
 *    使 10Hz~60Hz 的電腦端遙測在 120Hz/144Hz/165Hz/185Hz 手機螢幕上呈現絲滑連續移動。
 * 3. 最短角度差旋轉內插：處理 359° -> 1° 跨越正北方的平滑過渡，避免卡車圖示瞬間反轉 360°。
 * 4. 零每幀物件配置 (Zero Per-Frame Object Allocation)：重複使用單一 InterpolatedPose 實例供地圖相機與車標更新。
 */
class TelemetryInterpolator(
    initialLat: Double,
    initialLon: Double,
    initialHeading: Float
) {

    private class SnapshotPair(
        val prev: TelemetryFrame,
        val curr: TelemetryFrame,
        val intervalNanos: Long
    )

    private val initialFrame = TelemetryFrame(
        timestampNanos = System.nanoTime(),
        hasGameCoordinates = false,
        latitude = initialLat,
        longitude = initialLon,
        headingDegrees = initialHeading,
        speedKmh = 0f,
        speedLimitKmh = 80f
    )

    private val snapshotRef = AtomicReference(
        SnapshotPair(
            prev = initialFrame,
            curr = initialFrame,
            intervalNanos = 50_000_000L // 預設 20Hz (50ms)
        )
    )

    // 預配置輸出緩衝，避免在 185Hz Choreographer 迴圈內每幀 new 物件造成 GC 停頓
    private val reusablePose = InterpolatedPose(
        latitude = initialLat,
        longitude = initialLon,
        headingDegrees = initialHeading,
        speedKmh = 0f,
        speedLimitKmh = 80f
    )

    /**
     * 由 IO 執行緒呼叫：推入最新一筆遙測資料
     * 若電腦端尚未讀到遊戲數據 (hasGameCoordinates == false)，保留本地最後座標不跳動
     */
    fun onNewTelemetryFrame(incoming: TelemetryFrame) {
        val currentPair = snapshotRef.get()
        val effectiveFrame = if (incoming.hasGameCoordinates) {
            incoming
        } else {
            incoming.copy(
                latitude = currentPair.curr.latitude,
                longitude = currentPair.curr.longitude,
                headingDegrees = currentPair.curr.headingDegrees
            )
        }

        val rawDelta = effectiveFrame.timestampNanos - currentPair.curr.timestampNanos
        // 限制合理補間時間窗 (10ms ~ 250ms)，防止斷線重連後時間差過大
        val clampedInterval = rawDelta.coerceIn(10_000_000L, 250_000_000L)

        // 若兩點距離過遠（例如遊戲內快速移動/傳送至其他城市車庫 > 0.05 度），直接瞬移不內插
        val latJump = abs(effectiveFrame.latitude - currentPair.curr.latitude)
        val lonJump = abs(effectiveFrame.longitude - currentPair.curr.longitude)
        val prevForLerp = if (latJump > 0.05 || lonJump > 0.05) {
            effectiveFrame
        } else {
            currentPair.curr
        }

        snapshotRef.set(
            SnapshotPair(
                prev = prevForLerp,
                curr = effectiveFrame,
                intervalNanos = clampedInterval
            )
        )
    }

    /**
     * 當使用者在無遊戲數據時重置或切換預設城市座標
     */
    fun resetPosition(latitude: Double, longitude: Double, headingDegrees: Float) {
        val now = System.nanoTime()
        val resetFrame = TelemetryFrame(
            timestampNanos = now,
            hasGameCoordinates = false,
            latitude = latitude,
            longitude = longitude,
            headingDegrees = headingDegrees,
            speedKmh = 0f,
            speedLimitKmh = 80f
        )
        snapshotRef.set(SnapshotPair(resetFrame, resetFrame, 50_000_000L))
    }

    /**
     * 由 UI 渲染執行緒 (Choreographer.FrameCallback) 每幀呼叫：
     * 依據當前 frameTimeNanos 計算平滑內插後的卡車位置、車頭角度與時速
     */
    fun sampleInterpolatedPose(frameTimeNanos: Long): InterpolatedPose {
        val pair = snapshotRef.get()
        val elapsed = frameTimeNanos - pair.curr.timestampNanos
        val t = (elapsed.toFloat() / pair.intervalNanos.toFloat()).coerceIn(0f, 1.15f)

        reusablePose.latitude = lerpDouble(pair.prev.latitude, pair.curr.latitude, t.coerceAtMost(1f))
        reusablePose.longitude = lerpDouble(pair.prev.longitude, pair.curr.longitude, t.coerceAtMost(1f))
        reusablePose.headingDegrees = lerpAngleDegrees(pair.prev.headingDegrees, pair.curr.headingDegrees, t.coerceAtMost(1f))
        reusablePose.speedKmh = lerpFloat(pair.prev.speedKmh, pair.curr.speedKmh, t.coerceAtMost(1f))
        reusablePose.speedLimitKmh = pair.curr.speedLimitKmh

        return reusablePose
    }

    fun getLatestRawFrame(): TelemetryFrame = snapshotRef.get().curr

    private fun lerpDouble(start: Double, end: Double, fraction: Float): Double {
        return start + (end - start) * fraction.toDouble()
    }

    private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    /**
     * 360 度圓周最短路徑角度內插 (處理 355° -> 5° 跨零度平滑旋轉)
     */
    private fun lerpAngleDegrees(startDeg: Float, endDeg: Float, fraction: Float): Float {
        var delta = (endDeg - startDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        val result = (startDeg + delta * fraction) % 360f
        return if (result < 0f) result + 360f else result
    }
}
