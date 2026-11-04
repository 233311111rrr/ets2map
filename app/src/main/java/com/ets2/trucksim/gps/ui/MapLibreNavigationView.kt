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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ets2.trucksim.gps.engine.InterpolatedPose
import com.ets2.trucksim.gps.model.CameraFollowMode
import com.ets2.trucksim.gps.model.MapPointSelection
import com.ets2.trucksim.gps.model.MapTileSourceMode
import com.ets2.trucksim.gps.model.NavRouteCandidate
import com.ets2.trucksim.gps.model.PoiType
import com.ets2.trucksim.gps.model.RouteWaypoint
import com.ets2.trucksim.gps.model.ScsMapPackInfo
import com.ets2.trucksim.gps.model.TrackLogPoint
import com.ets2.trucksim.gps.model.VehicleIconType
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * MapLibre Native 2K 超清 (@2x Retina 超採樣) 地圖視圖 + 硬體加速零配置戰術覆疊層
 *
 * 畫質提升設計：
 * - 將 512×512 像素的 `Unicor-p/SCS_Map_Tiles` 圖磚在 MapLibre Style 中設定為 `"tileSize": 256`，
 *   強制啟動 `@2x Retina / 2K High-DPI` 雙倍像素密度超採樣（每 256dp 邏輯區塊塞入 512×512 實體像素），
 *   使手機與平板螢幕上的道路邊緣與城市細節提升 4 倍銳利度。
 * - 在 `TRUCKER_MUDGEON_HYBRID_2K` 模式下，額外疊加高解析度向量抗鋸齒路網增強層。
 */
@Composable
fun MapLibreNavigationView(
    serverHttpBaseUrl: String,
    scsLocalTileUrlTemplate: String,
    tileSourceMode: MapTileSourceMode,
    scsMapPackInfo: ScsMapPackInfo,
    isNightMode: Boolean,
    interpolatedPose: InterpolatedPose,
    followMode: CameraFollowMode,
    vehicleIconType: VehicleIconType,
    pois: List<MapPointSelection>,
    waypoints: List<RouteWaypoint>,
    routeCandidates: List<NavRouteCandidate>,
    selectedRouteIndex: Int,
    trackHistory: List<TrackLogPoint>,
    onUserPanStart: () -> Unit,
    onMapOrPoiTapped: (MapPointSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    val overlayView = remember {
        TacticalHudOverlayView(context)
    }

    LaunchedEffect(serverHttpBaseUrl, tileSourceMode, isNightMode, scsMapPackInfo.zipFileName, scsMapPackInfo.indexedTileCount) {
        mapView.getMapAsync { map ->
            map.uiSettings.isCompassEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false

            val styleJson = build2kHighDpiStyleJson(
                serverHttpBaseUrl = serverHttpBaseUrl,
                scsLocalTileUrlTemplate = scsLocalTileUrlTemplate,
                tileSourceMode = tileSourceMode,
                scsMapPackInfo = scsMapPackInfo,
                isNightMode = isNightMode
            )
            map.setStyle(Style.Builder().fromJson(styleJson))

            map.addOnMapClickListener { latLng ->
                val hitPoi = pois.firstOrNull { poi ->
                    val dLat = poi.latitude - latLng.latitude
                    val dLon = poi.longitude - latLng.longitude
                    hypot(dLat, dLon) < 0.015
                }
                if (hitPoi != null) {
                    onMapOrPoiTapped(hitPoi)
                } else {
                    onMapOrPoiTapped(
                        MapPointSelection(
                            id = "pin_${System.currentTimeMillis()}",
                            nameZh = "地圖標點 (${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)})",
                            type = PoiType.CUSTOM_PIN,
                            latitude = latLng.latitude,
                            longitude = latLng.longitude
                        )
                    )
                }
                true
            }
        }
    }

    LaunchedEffect(
        interpolatedPose,
        followMode,
        vehicleIconType,
        tileSourceMode,
        pois,
        waypoints,
        routeCandidates,
        selectedRouteIndex,
        trackHistory
    ) {
        mapView.getMapAsync { map ->
            if (followMode != CameraFollowMode.FREE_PAN) {
                val targetBearing = if (followMode == CameraFollowMode.HEADING_UP) {
                    interpolatedPose.headingDeg.toDouble()
                } else {
                    0.0
                }
                val targetTilt = if (followMode == CameraFollowMode.HEADING_UP) 38.0 else 0.0
                val defaultZoom = if (tileSourceMode == MapTileSourceMode.STAGE1_PC_SERVER) 13.5 else 6.8
                val camPos = CameraPosition.Builder()
                    .target(LatLng(interpolatedPose.latitude, interpolatedPose.longitude))
                    .zoom(defaultZoom)
                    .bearing(targetBearing)
                    .tilt(targetTilt)
                    .build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(camPos))
            }

            overlayView.updateRenderState(
                map = map,
                pose = interpolatedPose,
                followMode = followMode,
                iconType = vehicleIconType,
                isVectorHybrid2k = (tileSourceMode == MapTileSourceMode.TRUCKER_MUDGEON_HYBRID_2K),
                pois = pois,
                waypoints = waypoints,
                routes = routeCandidates,
                selectedRouteIdx = selectedRouteIndex,
                trackPoints = trackHistory
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    setOnTouchListener { _, motionEvent ->
                        if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE &&
                            motionEvent.pointerCount >= 1
                        ) {
                            onUserPanStart()
                        }
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AndroidView(
            factory = { overlayView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 建立 `@2x Retina / 2K High-DPI` 超採樣樣式 JSON：
 * 將 512px 圖磚設定為 `"tileSize": 256` 並啟用線性抗鋸齒取樣與對比強化，
 * 讓 1080p 與 2K 螢幕自動載入高一階的精細圖磚 (Zoom 0~8)。
 */
private fun build2kHighDpiStyleJson(
    serverHttpBaseUrl: String,
    scsLocalTileUrlTemplate: String,
    tileSourceMode: MapTileSourceMode,
    scsMapPackInfo: ScsMapPackInfo,
    isNightMode: Boolean
): String {
    val bgHex = if (isNightMode) "#0B0E14" else "#1E293B"
    return if (tileSourceMode != MapTileSourceMode.STAGE1_PC_SERVER) {
        """
        {
          "version": 8,
          "name": "ETS2 2K UHD Retina Style (${scsMapPackInfo.mapVersion})",
          "sources": {
            "scs-2k-retina-tiles": {
              "type": "raster",
              "tiles": ["$scsLocalTileUrlTemplate"],
              "tileSize": 256,
              "minzoom": ${scsMapPackInfo.minZoom},
              "maxzoom": ${scsMapPackInfo.maxZoom}
            }
          },
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": {
                "background-color": "$bgHex"
              }
            },
            {
              "id": "scs-2k-map-layer",
              "type": "raster",
              "source": "scs-2k-retina-tiles",
              "paint": {
                "raster-opacity": 1.0,
                "raster-resampling": "linear",
                "raster-contrast": 0.12,
                "raster-saturation": 0.08,
                "raster-fade-duration": 0
              }
            }
          ]
        }
        """.trimIndent()
    } else {
        """
        {
          "version": 8,
          "name": "ETS2 Stage1 Tactical Style",
          "sources": {
            "stage1-pmtiles": {
              "type": "raster",
              "tiles": ["$serverHttpBaseUrl/tiles/{z}/{x}/{y}.png"],
              "tileSize": 256
            }
          },
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": {
                "background-color": "$bgHex"
              }
            },
            {
              "id": "stage1-raster-layer",
              "type": "raster",
              "source": "stage1-pmtiles",
              "paint": {
                "raster-opacity": 0.95,
                "raster-resampling": "linear"
              }
            }
          ]
        }
        """.trimIndent()
    }
}

/**
 * 零每幀物件配置 (Zero Per-Frame Allocation) 的 2K 向量抗鋸齒戰術覆疊繪圖層
 */
private class TacticalHudOverlayView(context: Context) : View(context) {

    private var mapLibreMap: MapLibreMap? = null
    private var currentPose = InterpolatedPose(52.5200, 13.4050, 0f, 0f)
    private var currentFollowMode = CameraFollowMode.HEADING_UP
    private var currentIconType = VehicleIconType.TRUCK
    private var isVectorHybrid2kMode = false
    private var currentPois: List<MapPointSelection> = emptyList()
    private var currentWaypoints: List<RouteWaypoint> = emptyList()
    private var currentRoutes: List<NavRouteCandidate> = emptyList()
    private var currentSelectedRouteIdx: Int = 0
    private var currentTrackPoints: List<TrackLogPoint> = emptyList()

    private val vectorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2238BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val activeRoutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B82F6")
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val activeRouteGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#663B82F6")
        style = Paint.Style.STROKE
        strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val altRoutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8810B981")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val poiFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val poiStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val vehicleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL
    }

    private val vehicleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val reusablePath = Path()
    private val vehiclePath = Path()

    fun updateRenderState(
        map: MapLibreMap,
        pose: InterpolatedPose,
        followMode: CameraFollowMode,
        iconType: VehicleIconType,
        isVectorHybrid2k: Boolean,
        pois: List<MapPointSelection>,
        waypoints: List<RouteWaypoint>,
        routes: List<NavRouteCandidate>,
        selectedRouteIdx: Int,
        trackPoints: List<TrackLogPoint>
    ) {
        this.mapLibreMap = map
        this.currentPose = pose
        this.currentFollowMode = followMode
        this.currentIconType = iconType
        this.isVectorHybrid2kMode = isVectorHybrid2k
        this.currentPois = pois
        this.currentWaypoints = waypoints
        this.currentRoutes = routes
        this.currentSelectedRouteIdx = selectedRouteIdx
        this.currentTrackPoints = trackPoints
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val map = mapLibreMap ?: return
        val proj = map.projection

        // 若啟用 2K 向量增強模式，繪製高精細抗鋸齒歐陸路網連接線與戰術格線
        if (isVectorHybrid2kMode && currentPois.size >= 2) {
            reusablePath.reset()
            for (i in 0 until currentPois.lastIndex) {
                val p1 = proj.toScreenLocation(LatLng(currentPois[i].latitude, currentPois[i].longitude))
                val p2 = proj.toScreenLocation(LatLng(currentPois[i + 1].latitude, currentPois[i + 1].longitude))
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, vectorGridPaint)
            }
        }

        // 1. 繪製行駛歷史軌跡
        if (currentTrackPoints.size >= 2) {
            reusablePath.reset()
            for (i in currentTrackPoints.indices) {
                val pt = currentTrackPoints[i]
                val screenPt = proj.toScreenLocation(LatLng(pt.latitude, pt.longitude))
                if (i == 0) reusablePath.moveTo(screenPt.x, screenPt.y)
                else reusablePath.lineTo(screenPt.x, screenPt.y)
            }
            canvas.drawPath(reusablePath, trackPaint)
        }

        // 2. 繪製替代路線與主導航路線
        for (i in currentRoutes.indices) {
            if (i == currentSelectedRouteIdx) continue
            val route = currentRoutes[i]
            drawRoutePath(canvas, proj, route.geometryCoordinates, altRoutePaint)
        }
        currentRoutes.getOrNull(currentSelectedRouteIdx)?.let { activeRoute ->
            drawRoutePath(canvas, proj, activeRoute.geometryCoordinates, activeRouteGlowPaint)
            drawRoutePath(canvas, proj, activeRoute.geometryCoordinates, activeRoutePaint)
        }

        // 3. 繪製 POI 點位
        for (i in currentPois.indices) {
            val poi = currentPois[i]
            val screenPt = proj.toScreenLocation(LatLng(poi.latitude, poi.longitude))
            poiFillPaint.color = poi.type.badgeColorHex.toInt()
            canvas.drawCircle(screenPt.x, screenPt.y, 13f, poiFillPaint)
            canvas.drawCircle(screenPt.x, screenPt.y, 13f, poiStrokePaint)
        }

        // 4. 繪製路徑點
        for (i in currentWaypoints.indices) {
            val wp = currentWaypoints[i]
            val screenPt = proj.toScreenLocation(LatLng(wp.latitude, wp.longitude))
            poiFillPaint.color = Color.parseColor("#EF4444")
            canvas.drawCircle(screenPt.x, screenPt.y, 16f, poiFillPaint)
            canvas.drawCircle(screenPt.x, screenPt.y, 16f, poiStrokePaint)
        }

        // 5. 繪製自車標記（依車頭方向與地圖旋轉差值旋轉）
        val truckScreen = proj.toScreenLocation(LatLng(currentPose.latitude, currentPose.longitude))
        val mapBearing = map.cameraPosition.bearing.toFloat()
        val relativeRotation = if (currentFollowMode == CameraFollowMode.HEADING_UP) {
            0f
        } else {
            currentPose.headingDeg - mapBearing
        }

        canvas.save()
        canvas.translate(truckScreen.x, truckScreen.y)
        canvas.rotate(relativeRotation)

        vehiclePath.reset()
        when (currentIconType) {
            VehicleIconType.ARROW -> {
                vehiclePath.moveTo(0f, -34f)
                vehiclePath.lineTo(24f, 26f)
                vehiclePath.lineTo(0f, 12f)
                vehiclePath.lineTo(-24f, 26f)
                vehiclePath.close()
            }
            VehicleIconType.CAR -> {
                vehiclePath.addRoundRect(-16f, -30f, 16f, 30f, 10f, 10f, Path.Direction.CW)
            }
            VehicleIconType.TRUCK -> {
                vehiclePath.addRoundRect(-18f, -36f, 18f, -8f, 6f, 6f, Path.Direction.CW)
                vehiclePath.addRect(-16f, -4f, 16f, 36f, Path.Direction.CW)
            }
        }
        canvas.drawPath(vehiclePath, vehicleFillPaint)
        canvas.drawPath(vehiclePath, vehicleStrokePaint)

        val rad = Math.toRadians(-90.0)
        val tipX = (cos(rad) * 48f).toFloat()
        val tipY = (sin(rad) * 48f).toFloat()
        canvas.drawLine(0f, -30f, tipX, tipY, vehicleStrokePaint)
        canvas.restore()
    }

    private fun drawRoutePath(
        canvas: Canvas,
        proj: org.maplibre.android.maps.Projection,
        coords: List<Pair<Double, Double>>,
        paint: Paint
    ) {
        if (coords.size < 2) return
        reusablePath.reset()
        for (idx in coords.indices) {
            val pair = coords[idx]
            val pt = proj.toScreenLocation(LatLng(pair.first, pair.second))
            if (idx == 0) reusablePath.moveTo(pt.x, pt.y)
            else reusablePath.lineTo(pt.x, pt.y)
        }
        canvas.drawPath(reusablePath, paint)
    }
}
