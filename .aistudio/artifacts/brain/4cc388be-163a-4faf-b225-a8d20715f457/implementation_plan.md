# ETS2 TruckSim GPS — 內建 `SCS_Map_Tiles` 地圖、`.zip` 免解壓導入入口與 47 語音檔完整整合計畫

本計畫結合您指定的第三方開源圖磚專案 [`Unicor-p/SCS_Map_Tiles`](https://github.com/Unicor-p/SCS_Map_Tiles) 與上一輪上傳的 `etsmod.zip`（47 個台灣導航語音檔），將**地圖寫入 APK**、提供**免解壓縮 `.zip` 地圖包自訂導入入口**，並把 **47 個語音檔與完整對照表**一併打包進 APK。

---

## 一、`Unicor-p/SCS_Map_Tiles` 地圖寫入 APK 架構

### 1. 內建基礎底圖 + GitHub Actions 自動打包完整 `ETS2_Map_1.48.zip`
- **專案內建底圖 (`app/src/main/assets/scs_map/`)**：
  - 內建 `Unicor-p/SCS_Map_Tiles` 的 `TileMapInfo.json`（定義 ETS2 遊戲座標範圍 `x1: -94621.8047 ~ x2: 79370.13`, `y1: -80209.1641 ~ y2: 93782.77`, `tileSize: 512`, `minZoom: 0`, `maxZoom: 8`）以及基礎離線縮放層級圖磚（Zoom 0~2）。
- **GitHub Actions 自動封裝完整 155MB 地圖進 APK (`.github/workflows/build.yml`)**：
  - 在 GitHub Actions 執行 `gradle assembleDebug` 之前，自動從官方 Release 下載完整 [`ETS2_Map_1.48.zip`](https://github.com/Unicor-p/SCS_Map_Tiles/releases/download/1.48.2.6/ETS2_Map_1.48.zip) 並寫入 `app/src/main/assets/scs_map/ETS2_Map_1.48.zip`。
  - 在 `app/build.gradle.kts` 設定 `androidResources { noCompress += listOf("zip", "wav", "png", "pmtiles") }`，確保 APK 安裝後可對內建地圖包進行高速讀取。

### 2. 免解壓縮 `.zip` 地圖檔導入入口 (`ScsTileMapManager.kt`)
- **UI 操作入口**：
  - 在「啟動連線畫面」與主畫面「設定 / 地圖管理」面板中，新增 **「🗺️ 地圖包管理（導入 .zip 地圖檔）」** 卡片。
  - 點擊 **「導入 .zip 地圖包」** 按鈕即可透過 Android 系統檔案選擇器（`ActivityResultContracts.OpenDocument()`）選取手機內任意由 `TsMap2` 或 `Unicor-p/SCS_Map_Tiles` 導出的 `.zip` 檔案（例如 `ETS2_Map_1.48.zip`、`ATS_Map_1.48.zip`、ProMods 地圖包或未來 1.50+ 自製導出包）。
- **免解壓縮 `ZipFile` 高速索引引擎**：
  - 選取 `.zip` 後，系統將檔案掛載至 `context.filesDir/maps/custom_map.zip`（不解壓成數萬個零碎小檔，匯入僅需 1~2 秒，且節省一半以上儲存空間）。
  - 自動定位 ZIP 內的 `TileMapInfo.json`（支援根目錄或子資料夾結構如 `ets2/latest/TileMapInfo.json`），解析遊戲名稱（ETS2 / ATS）、版本號、最大縮放層級與座標邊界，並顯示於介面。
  - 內建輕量 **Localhost 圖磚伺服器 (`127.0.0.1:18080/scs_tiles/{z}/{x}/{y}.png`)**：當 MapLibre 請求 `{z}/{x}/{y}.png` 時，直接從開啟的 `ZipFile` 隨機讀取對應 `ZipEntry` 串流回傳，若無自訂 `.zip` 則依序讀取 APK 內建地圖與線上圖磚。

---

## 二、`etsmod.zip`（47 個台灣導航語音檔 + 完整對照表）寫入 APK

- **專屬目錄**：`app/src/main/assets/voice_tw/`
- **自動部署內容**：
  1. 將您上傳的 `/public/assets/aistudio/etsmod_887bc354.zip` 內全部 **47 個 `.wav` 語音檔**（`TURN_LEFT.wav`、`IN_300_METERS_TURN_LEFT_1.wav`、`HEAD_EAST.wav`、`ENTER_ROUNDABOUT_TAKE_1ST_EXIT.wav` 等）直接解壓放入 `app/src/main/assets/voice_tw/`。
  2. 將 `Google_map_TW[0].txt` 與 `Google_map_TW[0]_完整對照表.txt` 原文保存至 `app/src/main/assets/voice_tw/`。
  3. 更新 `app/src/main/assets/voice_tw/voice_mapping.json` 與 `TaiwanVoiceNavigator.kt`，一對一完整對應 47 筆語音事件，並同時相容「純英文大寫新檔名」、「保留前綴大寫檔名」與「ETS2 原始小寫檔名」。

---

## 三、預計新增與修改檔案清單

1. **Android 地圖包管理與本地圖磚引擎**：
   - `app/src/main/java/com/ets2/trucksim/gps/map/ScsTileMapManager.kt`（新增：免解壓 `.zip` 索引讀取、`TileMapInfo.json` 解析、Localhost 圖磚串流伺服器、SCS 座標投影轉換）
   - `app/src/main/assets/scs_map/TileMapInfo.json` & `app/src/main/assets/scs_map/Tiles/{0..2}/{x}/{y}.png`（新增：從 `Unicor-p/SCS_Map_Tiles` 抓取的內建 ETS2 基礎圖磚與設定）
2. **Android 介面與設定整合**：
   - `app/src/main/java/com/ets2/trucksim/gps/model/GpsModels.kt`（擴充 `ScsMapPackInfo` 地圖包資訊結構與 47 筆語音模型欄位）
   - `app/src/main/java/com/ets2/trucksim/gps/ui/MapLibreNavigationView.kt`（整合本地 `ScsTileMapManager` 512px 柵格圖層與電腦端 PMTiles 雙模式切換）
   - `app/src/main/java/com/ets2/trucksim/gps/ui/Components.kt` & `MainActivity.kt`（新增「導入 .zip 地圖檔」ActivityResultLauncher、目前載入地圖包資訊卡、切換內建/自訂地圖包按鈕）
3. **台灣語音模組 47 音檔部署**：
   - `app/src/main/assets/voice_tw/*.wav`（47 個實體音檔）、`voice_mapping.json`、`TaiwanVoiceNavigator.kt`
4. **CI 自動編譯與網頁預覽器更新**：
   - `.github/workflows/build.yml`（新增自動下載 `Unicor-p/SCS_Map_Tiles` 的 `ETS2_Map_1.48.zip` 寫入 APK 流程）
   - `app/build.gradle.kts`（鎖定相容 Kotlin 1.9.24 的 `org.maplibre.gl:android-sdk:11.0.0` 並配置 `noCompress`）
   - `src/components/AndroidProjectExplorer.tsx`（在網頁預覽器同步展示 `.zip` 地圖包導入介面、SCS 圖磚預覽與 47 筆台灣語音線上試聽/對照表）
