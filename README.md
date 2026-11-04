# ETS2 TruckSim GPS — 原生 Android 導航系統 (Kotlin + Jetpack Compose + MapLibre Native)

**授權條款**：GNU General Public License v3.0 or later (`GPL-3.0-or-later`)

本專案為延續第一階段（電腦端 Node.js 伺服器提供 PMTiles 圖磚、POI、路網與 WebSocket 遙測）之**第二階段原生 Android 應用程式**，並完整內建 [`Unicor-p/SCS_Map_Tiles`](https://github.com/Unicor-p/SCS_Map_Tiles) 第三方導出之 ETS2 地圖、**免解壓縮 `.zip` 地圖包導入入口**，以及 **47 個台灣導航語音檔 (`etsmod.zip`)**。

---

## 一、核心技術與新功能亮點

### 1. 內建 `Unicor-p/SCS_Map_Tiles` 地圖與 `.zip` 免解壓導入入口 (`ScsTileMapManager.kt`)
- **專案內建基底與 GitHub Actions 自動封裝完整地圖進 APK**：
  - 專案 `app/src/main/assets/scs_map/` 內建 `TileMapInfo.json`（定義 ETS2 遊戲座標範圍 `x1: -94621.8 ~ x2: 79370.13`, `y1: -80209.16 ~ y2: 93782.77`, `tileSize: 512`, `minZoom: 0`, `maxZoom: 8`）與離線基礎圖磚（Zoom 0~2）。
  - `.github/workflows/build.yml` 在雲端編譯 APK 前，會自動從 `Unicor-p/SCS_Map_Tiles` Release 下載完整 `ETS2_Map_1.48.zip` (155MB) 寫入 `app/src/main/assets/scs_map/ETS2_Map_1.48.zip` 並封裝進 APK。
- **App 內「導入 .zip 地圖檔」入口（免解壓縮 `ZipFile` 索引讀取）**：
  - 啟動連線畫面與主畫面右下角/設定抽屜皆提供 **「導入 .zip 地圖檔 (免解壓)」** 按鈕。
  - 支援任意由 `TsMap2` 或 `Unicor-p/SCS_Map_Tiles` 導出的 `.zip` 地圖包（如 `ETS2_Map_1.48.zip`、`ATS_Map_1.48.zip`、ProMods 地圖包）。
  - 選取 `.zip` 後直接透過 `java.util.zip.ZipFile` 建立記憶體雜湊索引，並由內建 Localhost 圖磚串流伺服器 (`http://127.0.0.1:18080/scs_tiles/{z}/{x}/{y}.png`) 供 MapLibre Native 512px Raster 圖層即時渲染，**零解壓等待時間、不佔雙倍儲存空間**。

### 2. 「Google Map 台灣語音模組 2」(`etsmod.zip` 47 音檔完整整合)
- **專屬音檔目錄**：`app/src/main/assets/voice_tw/`
- 已完整放入您上傳的 **47 個 `.wav` 實體語音檔**、`Google_map_TW[0].txt`、`Google_map_TW[0]_完整對照表.txt` 與 `voice_mapping.json`。
- **三種檔名自動相容**：同時支援純英文大寫新檔名（如 `TURN_LEFT.wav`、`IN_300_METERS_TURN_LEFT_1.wav`）、保留原前綴大寫檔名（如 `turn_left_TURN_LEFT.wav`）與 ETS2 原始檔名（如 `turn_left.wav`）。

---

## 二、如何使用 GitHub Actions 自動編譯 APK

1. 將本專案推送至您的 GitHub 儲存庫（`main` 或 `master` 分支）。
2. GitHub Actions 會自動觸發 `.github/workflows/build.yml`：
   - 自動下載 `Unicor-p/SCS_Map_Tiles` 的 `ETS2_Map_1.48.zip` 至 `app/src/main/assets/scs_map/`
   - 將內建的 47 個台灣語音檔 (`app/src/main/assets/voice_tw/*.wav`) 一併打包編譯為 `app-debug.apk`。
3. 前往 GitHub 儲存庫的 **Actions** 頁面，點擊最新完成的 workflow run，在下方 **Artifacts** 區塊下載 `ets2-trucksim-gps-debug-apk` 即可安裝至 Android 手機。
