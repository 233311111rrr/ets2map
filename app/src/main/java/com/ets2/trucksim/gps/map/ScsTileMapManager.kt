/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ets2.trucksim.gps.map

import android.content.Context
import android.net.Uri
import com.ets2.trucksim.gps.model.ScsMapPackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Unicor-p/SCS_Map_Tiles 2K 超清圖磚管理與免解壓縮 .zip 索引引擎
 *
 * 2K / 1080p 畫質強化特點：
 * 1. 內建 Zoom 0~5 核心高清圖磚 (`assets/scs_map/Tiles/`) 與完整 `ETS2_Map_1.48.zip` (Zoom 0~8，總像素達 131,072×131,072 px)。
 * 2. 免解壓縮 .zip 導入入口：透過 `java.util.zip.ZipFile` 直接建立 `{z}/{x}/{y}.png` 記憶體雜湊索引，秒速匯入且不佔雙倍空間。
 * 3. 原生 OkHttp 高清圖磚代理與本地磁碟快取 (`filesDir/tile_cache_2k/`)：
 *    當請求高倍縮放圖磚 (Zoom 3~8) 且不在本地 Assets 時，直接由背景執行緒透過 OkHttp 下載 512px PNG 圖磚並快取至手機儲存空間，
 *    徹底解決跨協定 (HTTP -> HTTPS) 302 跳轉被 MapLibre 忽略導致只能顯示模糊底圖的問題。
 * 4. 配合 MapLibre `"tileSize": 256` 實現 `@2x Retina / 2K High-DPI` 雙倍像素密度超採樣渲染。
 */
class ScsTileMapManager(private val context: Context) {

    companion object {
        const val LOCAL_TILE_SERVER_PORT = 18080
        private const val ASSETS_SCS_DIR = "scs_map"
        private const val ASSETS_BUNDLED_ZIP_NAME = "ETS2_Map_1.48.zip"
        private const val CUSTOM_ZIP_FILE_NAME = "custom_imported_scs_map.zip"
        private const val CACHED_ASSET_ZIP_FILE_NAME = "apk_bundled_scs_map.zip"
        private const val GITHUB_RAW_TILE_BASE =
            "https://raw.githubusercontent.com/Unicor-p/SCS_Map_Tiles/master/ets2/latest/Tiles"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tileHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _mapPackInfo = MutableStateFlow(ScsMapPackInfo())
    val mapPackInfo: StateFlow<ScsMapPackInfo> = _mapPackInfo.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    @Volatile
    private var activeZipFile: ZipFile? = null
    private val zipTileEntryIndex = ConcurrentHashMap<String, String>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isServerRunning = false

    private val diskCacheDir: File by lazy {
        File(context.filesDir, "tile_cache_2k").apply { mkdirs() }
    }

    init {
        ioScope.launch {
            initializeMapSources()
            startLocalTileServer()
        }
    }

    /**
     * 初始化地圖來源：
     * 1. 若手機內部已有使用者導入的 `custom_imported_scs_map.zip`，優先掛載並建立 ZIP 索引。
     * 2. 否則若 APK `assets/scs_map/ETS2_Map_1.48.zip` 存在（由 GitHub Actions 自動打包進 APK），掛載內建完整 2K ZIP。
     * 3. 讀取 `assets/scs_map/TileMapInfo.json` 與內建 Zoom 0~5 2K 高清基底圖磚。
     */
    suspend fun initializeMapSources() = withContext(Dispatchers.IO) {
        val mapsDir = File(context.filesDir, "maps").apply { mkdirs() }
        val customZip = File(mapsDir, CUSTOM_ZIP_FILE_NAME)

        if (customZip.exists() && customZip.length() > 1024L) {
            val loaded = indexZipArchive(customZip, isCustomUserZip = true, displayZipName = customZip.name)
            if (loaded) return@withContext
        }

        val assetList = runCatching { context.assets.list(ASSETS_SCS_DIR)?.toList() ?: emptyList() }
            .getOrDefault(emptyList())

        if (assetList.contains(ASSETS_BUNDLED_ZIP_NAME)) {
            val cachedBundledZip = File(mapsDir, CACHED_ASSET_ZIP_FILE_NAME)
            if (!cachedBundledZip.exists() || cachedBundledZip.length() == 0L) {
                runCatching {
                    context.assets.open("$ASSETS_SCS_DIR/$ASSETS_BUNDLED_ZIP_NAME").use { input ->
                        FileOutputStream(cachedBundledZip).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                }
            }
            if (cachedBundledZip.exists() && cachedBundledZip.length() > 1024L) {
                val loaded = indexZipArchive(
                    cachedBundledZip,
                    isCustomUserZip = false,
                    displayZipName = "$ASSETS_BUNDLED_ZIP_NAME (APK 內建 2K 完整包)"
                )
                if (loaded) return@withContext
            }
        }

        loadBuiltInAssetTileMapInfo()
    }

    /**
     * 使用者從系統檔案選擇器導入任意 .zip 格式地圖包（免解壓縮直接建立索引）
     */
    fun importMapZipFromUri(uri: Uri, originalDisplayName: String? = null) {
        ioScope.launch {
            _importStatusMessage.value = "正在掛載並建立 2K .zip 地圖索引（免解壓縮）..."
            runCatching {
                val mapsDir = File(context.filesDir, "maps").apply { mkdirs() }
                val targetZipFile = File(mapsDir, CUSTOM_ZIP_FILE_NAME)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetZipFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("無法讀取所選的 .zip 檔案")

                val labelName = originalDisplayName ?: targetZipFile.name
                val success = indexZipArchive(targetZipFile, isCustomUserZip = true, displayZipName = labelName)
                if (success) {
                    _importStatusMessage.value =
                        "成功導入 2K 地圖包：$labelName（共索引 ${_mapPackInfo.value.indexedTileCount} 張高清圖磚，免解壓即刻生效）"
                } else {
                    _importStatusMessage.value =
                        "導入失敗：該 .zip 內未找到符合 SCS_Map_Tiles / TsMap2 結構的 Tiles/{z}/{x}/{y}.png 圖磚"
                }
            }.onFailure { err ->
                _importStatusMessage.value = "導入 .zip 地圖檔發生錯誤：${err.localizedMessage ?: "格式無效"}"
            }
        }
    }

    /**
     * 恢復使用 APK 內建的 Unicor-p/SCS_Map_Tiles 2K 預設地圖
     */
    fun resetToBuiltInMap() {
        ioScope.launch {
            val mapsDir = File(context.filesDir, "maps")
            val customZip = File(mapsDir, CUSTOM_ZIP_FILE_NAME)
            if (customZip.exists()) {
                customZip.delete()
            }
            runCatching { activeZipFile?.close() }
            activeZipFile = null
            zipTileEntryIndex.clear()
            initializeMapSources()
            _importStatusMessage.value = "已切換回 APK 內建 2K 超清 Unicor-p/SCS_Map_Tiles 地圖"
        }
    }

    /**
     * 對 .zip 檔案建立記憶體雜湊索引，不解壓縮數萬張 PNG 圖磚
     */
    private fun indexZipArchive(zipFileOnDisk: File, isCustomUserZip: Boolean, displayZipName: String): Boolean {
        return runCatching {
            val newZip = ZipFile(zipFileOnDisk)
            val newIndex = ConcurrentHashMap<String, String>()
            var tileMapInfoEntryName: String? = null

            val entries = newZip.entries()
            val tileRegex = Regex("""(?:^|.*/)(?:Tiles/)?(\d+)/(\d+)/(\d+)\.png$""", RegexOption.IGNORE_CASE)

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val entryName = entry.name.replace('\\', '/')
                if (entryName.endsWith("TileMapInfo.json", ignoreCase = true)) {
                    tileMapInfoEntryName = entry.name
                } else {
                    val match = tileRegex.find(entryName)
                    if (match != null) {
                        val z = match.groupValues[1]
                        val x = match.groupValues[2]
                        val y = match.groupValues[3]
                        newIndex["$z/$x/$y.png"] = entry.name
                    }
                }
            }

            if (newIndex.isEmpty()) {
                newZip.close()
                return false
            }

            var parsedInfo = parseTileMapInfoJsonFromStream(
                tileMapInfoEntryName?.let { newZip.getInputStream(newZip.getEntry(it)) }
            )
            if (parsedInfo == null) {
                parsedInfo = readAssetTileMapInfoOrDefault()
            }

            val oldZip = activeZipFile
            activeZipFile = newZip
            zipTileEntryIndex.clear()
            zipTileEntryIndex.putAll(newIndex)
            runCatching { oldZip?.close() }

            _mapPackInfo.value = parsedInfo.copy(
                sourceLabel = if (isCustomUserZip) {
                    "自訂導入 2K .zip 地圖包 (免解壓索引)"
                } else {
                    "APK 內建 2K 完整地圖包 (Unicor-p/SCS_Map_Tiles)"
                },
                zipFileName = displayZipName,
                resolutionQualityLabel = "2K UHD (@2x Retina 超採樣, Zoom ${parsedInfo.minZoom}~${parsedInfo.maxZoom})",
                indexedTileCount = newIndex.size,
                isCustomZipLoaded = isCustomUserZip
            )
            true
        }.getOrDefault(false)
    }

    private fun loadBuiltInAssetTileMapInfo() {
        val info = readAssetTileMapInfoOrDefault()
        _mapPackInfo.value = info.copy(
            sourceLabel = "APK 內建 2K 超清圖磚 + Zoom 0~8 自動快取 (Unicor-p/SCS_Map_Tiles)",
            zipFileName = "assets/scs_map/ (內建 Zoom 0~5 高清圖磚 + Zoom 6~8 直連快取)",
            resolutionQualityLabel = "2K UHD (@2x Retina, 131072×131072 px)",
            indexedTileCount = 105,
            isCustomZipLoaded = false
        )
    }

    private fun readAssetTileMapInfoOrDefault(): ScsMapPackInfo {
        return runCatching {
            context.assets.open("$ASSETS_SCS_DIR/TileMapInfo.json").use { stream ->
                parseTileMapInfoJsonFromStream(stream)
            }
        }.getOrNull() ?: ScsMapPackInfo()
    }

    private fun parseTileMapInfoJsonFromStream(stream: java.io.InputStream?): ScsMapPackInfo? {
        if (stream == null) return null
        return runCatching {
            val jsonText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            val root = JSONObject(jsonText)
            val mapObj = root.optJSONObject("map")
            val gameObj = root.optJSONObject("game")
            ScsMapPackInfo(
                gameId = gameObj?.optString("game", "ets2") ?: "ets2",
                gameName = gameObj?.optString("name", "Euro Truck Simulator 2") ?: "Euro Truck Simulator 2",
                mapVersion = gameObj?.optString("version", "1.48.1.6") ?: "1.48.1.6",
                tileSize = mapObj?.optInt("tileSize", 512) ?: 512,
                minZoom = mapObj?.optInt("minZoom", 0) ?: 0,
                maxZoom = mapObj?.optInt("maxZoom", 8) ?: 8,
                x1 = mapObj?.optDouble("x1", -94621.8047) ?: -94621.8047,
                x2 = mapObj?.optDouble("x2", 79370.13) ?: 79370.13,
                y1 = mapObj?.optDouble("y1", -80209.1641) ?: -80209.1641,
                y2 = mapObj?.optDouble("y2", 93782.77) ?: 93782.77
            )
        }.getOrNull()
    }

    /**
     * 啟動內建 Localhost 2K 圖磚串流伺服器 (`http://127.0.0.1:18080/scs_tiles/{z}/{x}/{y}.png`)
     */
    private fun startLocalTileServer() {
        if (isServerRunning) return
        ioScope.launch {
            runCatching {
                val server = ServerSocket(LOCAL_TILE_SERVER_PORT, 64, java.net.InetAddress.getByName("127.0.0.1"))
                serverSocket = server
                isServerRunning = true
                while (isServerRunning) {
                    val client = server.accept()
                    ioScope.launch { handleTileClient(client) }
                }
            }
        }
    }

    private fun handleTileClient(socket: Socket) {
        socket.use { client ->
            runCatching {
                client.soTimeout = 6000
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val path = requestLine.split(" ").getOrNull(1) ?: return
                val match = Regex("""/scs_tiles/(\d+)/(\d+)/(\d+)\.png""").find(path)

                val out = BufferedOutputStream(client.getOutputStream())
                if (match == null) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }

                val z = match.groupValues[1]
                val x = match.groupValues[2]
                val y = match.groupValues[3]
                val tileKey = "$z/$x/$y.png"

                // 1. 優先從已建立索引的 ZipFile 隨機讀取（免解壓縮）
                val currentZip = activeZipFile
                val entryName = zipTileEntryIndex[tileKey]
                if (currentZip != null && entryName != null) {
                    val zipEntry = currentZip.getEntry(entryName)
                    if (zipEntry != null) {
                        val bytes = currentZip.getInputStream(zipEntry).use { it.readBytes() }
                        writePngResponse(out, bytes)
                        return
                    }
                }

                // 2. 次優先從 APK 內建 assets/scs_map/Tiles/{z}/{x}/{y}.png 讀取 (含內建 Zoom 0~5 高清圖磚)
                val assetBytes = runCatching {
                    context.assets.open("$ASSETS_SCS_DIR/Tiles/$tileKey").use { it.readBytes() }
                }.getOrNull()

                if (assetBytes != null) {
                    writePngResponse(out, assetBytes)
                    return
                }

                // 3. 檢查本地磁碟快取 (filesDir/tile_cache_2k/)
                val cacheFile = File(diskCacheDir, "${z}_${x}_${y}.png")
                if (cacheFile.exists() && cacheFile.length() > 64L) {
                    writePngResponse(out, cacheFile.readBytes())
                    return
                }

                // 4. 直接透過 OkHttp 從 Unicor-p/SCS_Map_Tiles 抓取 Zoom 3~8 512px 高清圖磚並寫入本地快取
                val remoteUrl = "$GITHUB_RAW_TILE_BASE/$tileKey"
                val fetchedBytes = runCatching {
                    val req = Request.Builder().url(remoteUrl).get().build()
                    tileHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.bytes() else null
                    }
                }.getOrNull()

                if (fetchedBytes != null && fetchedBytes.isNotEmpty()) {
                    runCatching { cacheFile.writeBytes(fetchedBytes) }
                    writePngResponse(out, fetchedBytes)
                } else {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                }
            }
        }
    }

    private fun writePngResponse(out: BufferedOutputStream, bytes: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: image/png\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: max-age=604800\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    fun getLocalTileUrlTemplate(): String {
        return "http://127.0.0.1:$LOCAL_TILE_SERVER_PORT/scs_tiles/{z}/{x}/{y}.png"
    }

    fun release() {
        isServerRunning = false
        runCatching { serverSocket?.close() }
        runCatching { activeZipFile?.close() }
    }
}
