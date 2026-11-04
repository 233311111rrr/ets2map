import React, { useState, useRef } from 'react';
import {
   Smartphone,
  Cpu,
  Volume2,
  FolderGit2,
  CheckCircle2,
  FileCode2,
  Gauge,
  Navigation,
  Layers,
  Terminal,
  ShieldCheck,
  Play,
  MapPin,
  Upload,
  Archive,
  Map as MapIcon,
  RotateCcw
} from 'lucide-react';
import { DeliveryStageId, VoiceMappingEntry } from '../types';

const INITIAL_VOICE_MAPPINGS: (VoiceMappingEntry & {
  prefixedFileName: string;
  originalFileName: string;
})[] = [
  { fileName: 'TURN_LEFT.wav', prefixedFileName: 'turn_left_TURN_LEFT.wav', originalFileName: 'turn_left.wav', eventKey: 'TURN_LEFT', descriptionZh: '向左轉', triggerDistanceMeters: 30, enabled: true },
  { fileName: 'KEEP_LEFT_AND_TAKE_EXIT_1.wav', prefixedFileName: 'compound_exit_left_KEEP_LEFT_AND_TAKE_EXIT.wav', originalFileName: 'compound_exit_left.wav', eventKey: 'KEEP_LEFT_AND_TAKE_EXIT_1', descriptionZh: '靠左下交流道', triggerDistanceMeters: 180, enabled: true },
  { fileName: 'ENTER_ROUNDABOUT_TAKE_1ST_EXIT.wav', prefixedFileName: 'roundabout_1_ENTER_ROUNDABOUT_TAKE_1ST_EXIT.wav', originalFileName: 'roundabout_1.wav', eventKey: 'ENTER_ROUNDABOUT_TAKE_1ST_EXIT', descriptionZh: '進入圓環，在圓環處，於第一個出口下交流道', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'KEEP_LEFT_AND_TAKE_EXIT_2.wav', prefixedFileName: 'and_then_exit_left_KEEP_LEFT_AND_TAKE_EXIT.wav', originalFileName: 'and_then_exit_left.wav', eventKey: 'KEEP_LEFT_AND_TAKE_EXIT_2', descriptionZh: '靠左下交流道', triggerDistanceMeters: 100, enabled: true },
  { fileName: 'KEEP_RIGHT_AND_TAKE_EXIT_1.wav', prefixedFileName: 'exit_right_KEEP_RIGHT_AND_TAKE_EXIT.wav', originalFileName: 'exit_right.wav', eventKey: 'KEEP_RIGHT_AND_TAKE_EXIT_1', descriptionZh: '靠右行下交流道', triggerDistanceMeters: 150, enabled: true },
  { fileName: 'KEEP_SLIGHT_RIGHT_1.wav', prefixedFileName: 'keep_right_KEEP_SLIGHT_RIGHT.wav', originalFileName: 'keep_right.wav', eventKey: 'KEEP_SLIGHT_RIGHT_1', descriptionZh: '微靠右行', triggerDistanceMeters: 150, enabled: true },
  { fileName: 'HEAD_SOUTHEAST.wav', prefixedFileName: 'start_6_SOUTHEAST.wav', originalFileName: 'start_6.wav', eventKey: 'HEAD_SOUTHEAST', descriptionZh: '往東南', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'HEAD_EAST.wav', prefixedFileName: 'start_1_EAST.wav', originalFileName: 'start_1.wav', eventKey: 'HEAD_EAST', descriptionZh: '往東', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'ENTER_ROUNDABOUT_TAKE_2ND_EXIT.wav', prefixedFileName: 'roundabout_2_ENTER_ROUNDABOUT_TAKE_2ND_EXIT.wav', originalFileName: 'roundabout_2.wav', eventKey: 'ENTER_ROUNDABOUT_TAKE_2ND_EXIT', descriptionZh: '進入圓環，在圓環處，於第二個出口下交流道', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'IN_100_METERS_TURN_LEFT.wav', prefixedFileName: 'and_then_turn_left_IN_100_METERS_TURN_LEFT.wav', originalFileName: 'and_then_turn_left.wav', eventKey: 'IN_100_METERS_TURN_LEFT', descriptionZh: '在 100 公尺處，向左轉', triggerDistanceMeters: 100, enabled: true },
  { fileName: 'KEEP_SLIGHT_LEFT_1.wav', prefixedFileName: 'and_then_keep_left_KEEP_SLIGHT_LEFT.wav', originalFileName: 'and_then_keep_left.wav', eventKey: 'KEEP_SLIGHT_LEFT_1', descriptionZh: '微靠左行', triggerDistanceMeters: 100, enabled: true },
  { fileName: 'KEEP_SLIGHT_LEFT_2.wav', prefixedFileName: 'compound_keep_left_KEEP_SLIGHT_LEFT.wav', originalFileName: 'compound_keep_left.wav', eventKey: 'KEEP_SLIGHT_LEFT_2', descriptionZh: '微靠左行', triggerDistanceMeters: 150, enabled: true },
  { fileName: 'YOU_HAVE_ARRIVED_AT_YOUR_DESTINATION.wav', prefixedFileName: 'finish_1_YOU_HAVE_ARRIVED_AT_YOUR_DESTINATION.wav', originalFileName: 'finish_1.wav', eventKey: 'YOU_HAVE_ARRIVED_AT_YOUR_DESTINATION', descriptionZh: '您已到達目的地', triggerDistanceMeters: 15, enabled: true },
  { fileName: 'SPEED_WARNING_BEEP_1.wav', prefixedFileName: 'speed_warning_SPEED_WARNING_BEEP.wav', originalFileName: 'speed_warning.wav', eventKey: 'SPEED_WARNING_BEEP_1', descriptionZh: '超速警示音（嘟嘟兩聲）', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'ENTER_ROUNDABOUT_TAKE_3RD_EXIT.wav', prefixedFileName: 'roundabout_3_ENTER_ROUNDABOUT_TAKE_3RD_EXIT.wav', originalFileName: 'roundabout_3.wav', eventKey: 'ENTER_ROUNDABOUT_TAKE_3RD_EXIT', descriptionZh: '進入圓環，在圓環處，於第三個出口下交流道', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'KEEP_RIGHT_AND_TAKE_EXIT_2.wav', prefixedFileName: 'and_then_exit_right_KEEP_RIGHT_AND_TAKE_EXIT.wav', originalFileName: 'and_then_exit_right.wav', eventKey: 'KEEP_RIGHT_AND_TAKE_EXIT_2', descriptionZh: '靠右行下交流道', triggerDistanceMeters: 100, enabled: true },
  { fileName: 'CONTINUE_STRAIGHT_1.wav', prefixedFileName: 'go_straight_1_CONTINUE_STRAIGHT.wav', originalFileName: 'go_straight_1.wav', eventKey: 'CONTINUE_STRAIGHT_1', descriptionZh: '繼續直行', triggerDistanceMeters: 400, enabled: true },
  { fileName: 'ENTER_ROUNDABOUT_TAKE_5TH_EXIT.wav', prefixedFileName: 'roundabout_5_ENTER_ROUNDABOUT_TAKE_5TH_EXIT.wav', originalFileName: 'roundabout_5.wav', eventKey: 'ENTER_ROUNDABOUT_TAKE_5TH_EXIT', descriptionZh: '進入圓環，在圓環處，於第五個出口下交流道', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'KEEP_SLIGHT_RIGHT_2.wav', prefixedFileName: 'and_then_keep_right_KEEP_SLIGHT_RIGHT.wav', originalFileName: 'and_then_keep_right.wav', eventKey: 'KEEP_SLIGHT_RIGHT_2', descriptionZh: '微靠右行', triggerDistanceMeters: 100, enabled: true },
  { fileName: 'KEEP_SLIGHT_LEFT_3.wav', prefixedFileName: 'keep_left_KEEP_SLIGHT_LEFT.wav', originalFileName: 'keep_left.wav', eventKey: 'KEEP_SLIGHT_LEFT_3', descriptionZh: '微靠左行', triggerDistanceMeters: 150, enabled: true },
  { fileName: 'HEAD_SOUTHWEST.wav', prefixedFileName: 'start_7_SOUTHWEST.wav', originalFileName: 'start_7.wav', eventKey: 'HEAD_SOUTHWEST', descriptionZh: '往西南', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'CONTINUE_STRAIGHT_2.wav', prefixedFileName: 'compound_go_straight_2_CONTINUE_STRAIGHT.wav', originalFileName: 'compound_go_straight_2.wav', eventKey: 'CONTINUE_STRAIGHT_2', descriptionZh: '繼續直行', triggerDistanceMeters: 250, enabled: true },
  { fileName: 'PLEASE_CONTINUE_STRAIGHT_1.wav', prefixedFileName: 'go_straight_2_PLEASE_CONTINUE_STRAIGHT.wav', originalFileName: 'go_straight_2.wav', eventKey: 'PLEASE_CONTINUE_STRAIGHT_1', descriptionZh: '請繼續直行', triggerDistanceMeters: 600, enabled: true },
  { fileName: 'SLIGHT_LEFT.wav', prefixedFileName: 'compound_turn_left_SLIGHT_LEFT.wav', originalFileName: 'compound_turn_left.wav', eventKey: 'SLIGHT_LEFT', descriptionZh: '微靠左', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'KEEP_SLIGHT_RIGHT_3.wav', prefixedFileName: 'compound_turn_right_KEEP_SLIGHT_RIGHT.wav', originalFileName: 'compound_turn_right.wav', eventKey: 'KEEP_SLIGHT_RIGHT_3', descriptionZh: '微靠右行', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'ENTER_ROUNDABOUT_TAKE_6TH_EXIT.wav', prefixedFileName: 'roundabout_6_ENTER_ROUNDABOUT_TAKE_6TH_EXIT.wav', originalFileName: 'roundabout_6.wav', eventKey: 'ENTER_ROUNDABOUT_TAKE_6TH_EXIT', descriptionZh: '進入圓環，在圓環處，於第六個出口下交流道', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'MAKE_A_U_TURN.wav', prefixedFileName: 'u_turn_1_MAKE_A_U_TURN.wav', originalFileName: 'u_turn_1.wav', eventKey: 'MAKE_A_U_TURN', descriptionZh: '迴轉', triggerDistanceMeters: 50, enabled: true },
  { fileName: 'HEAD_WEST.wav', prefixedFileName: 'start_8_WEST.wav', originalFileName: 'start_8.wav', eventKey: 'HEAD_WEST', descriptionZh: '往西', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'IN_300_METERS_TURN_RIGHT_1.wav', prefixedFileName: 'prepare_exit_right_IN_300_METERS_TURN_RIGHT.wav', originalFileName: 'prepare_exit_right.wav', eventKey: 'IN_300_METERS_TURN_RIGHT_1', descriptionZh: '在 300 公尺處，向右轉', triggerDistanceMeters: 300, enabled: true },
  { fileName: 'HEAD_NORTH.wav', prefixedFileName: 'start_2_NORTH.wav', originalFileName: 'start_2.wav', eventKey: 'HEAD_NORTH', descriptionZh: '往北', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'IN_300_METERS_TURN_RIGHT_2.wav', prefixedFileName: 'prepare_turn_right_IN_300_METERS_TURN_RIGHT.wav', originalFileName: 'prepare_turn_right.wav', eventKey: 'IN_300_METERS_TURN_RIGHT_2', descriptionZh: '在 300 公尺處，向右轉', triggerDistanceMeters: 300, enabled: true },
  { fileName: 'KEEP_RIGHT_AND_TAKE_EXIT_3.wav', prefixedFileName: 'compound_exit_right_KEEP_RIGHT_AND_TAKE_EXIT.wav', originalFileName: 'compound_exit_right.wav', eventKey: 'KEEP_RIGHT_AND_TAKE_EXIT_3', descriptionZh: '靠右行下交流道', triggerDistanceMeters: 180, enabled: true },
  { fileName: 'HEAD_NORTHWEST.wav', prefixedFileName: 'start_4_NORTHWEST.wav', originalFileName: 'start_4.wav', eventKey: 'HEAD_NORTHWEST', descriptionZh: '往西北', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'EXIT_ROUNDABOUT.wav', prefixedFileName: 'exit_now_EXIT_ROUNDABOUT.wav', originalFileName: 'exit_now.wav', eventKey: 'EXIT_ROUNDABOUT', descriptionZh: '駛出圓環', triggerDistanceMeters: 25, enabled: true },
  { fileName: 'ENTER_ROUNDABOUT_TAKE_4TH_EXIT.wav', prefixedFileName: 'roundabout_4_ENTER_ROUNDABOUT_TAKE_4TH_EXIT.wav', originalFileName: 'roundabout_4.wav', eventKey: 'ENTER_ROUNDABOUT_TAKE_4TH_EXIT', descriptionZh: '進入圓環，在圓環處，於第四個出口下交流道', triggerDistanceMeters: 120, enabled: true },
  { fileName: 'KEEP_LEFT_AND_TAKE_EXIT_3.wav', prefixedFileName: 'exit_left_KEEP_LEFT_AND_TAKE_EXIT.wav', originalFileName: 'exit_left.wav', eventKey: 'KEEP_LEFT_AND_TAKE_EXIT_3', descriptionZh: '靠左下交流道', triggerDistanceMeters: 150, enabled: true },
  { fileName: 'AND_THEN_CONTINUE_STRAIGHT.wav', prefixedFileName: 'and_then_go_straight_AND_THEN_CONTINUE_STRAIGHT.wav', originalFileName: 'and_then_go_straight.wav', eventKey: 'AND_THEN_CONTINUE_STRAIGHT', descriptionZh: '然後，繼續直行', triggerDistanceMeters: 100, enabled: true },
  { fileName: 'IN_300_METERS_TURN_LEFT_1.wav', prefixedFileName: 'prepare_exit_left_IN_300_METERS_TURN_LEFT.wav', originalFileName: 'prepare_exit_left.wav', eventKey: 'IN_300_METERS_TURN_LEFT_1', descriptionZh: '在 300 公尺處，向左轉', triggerDistanceMeters: 300, enabled: true },
  { fileName: 'IN_300_METERS_TURN_LEFT_2.wav', prefixedFileName: 'prepare_turn_left_IN_300_METERS_TURN_LEFT.wav', originalFileName: 'prepare_turn_left.wav', eventKey: 'IN_300_METERS_TURN_LEFT_2', descriptionZh: '在 300 公尺處，向左轉', triggerDistanceMeters: 300, enabled: true },
  { fileName: 'TURN_RIGHT.wav', prefixedFileName: 'turn_right_TURN_RIGHT.wav', originalFileName: 'turn_right.wav', eventKey: 'TURN_RIGHT', descriptionZh: '向右轉', triggerDistanceMeters: 30, enabled: true },
  { fileName: 'HEAD_SOUTH.wav', prefixedFileName: 'start_5_SOUTH.wav', originalFileName: 'start_5.wav', eventKey: 'HEAD_SOUTH', descriptionZh: '往南', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'SPEED_WARNING_BEEP_2.wav', prefixedFileName: 'speed_signal_SPEED_WARNING_BEEP.wav', originalFileName: 'speed_signal.wav', eventKey: 'SPEED_WARNING_BEEP_2', descriptionZh: '超速提示音（嘟嘟兩聲）', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'PLEASE_CONTINUE_STRAIGHT_2.wav', prefixedFileName: 'compound_go_straight_1_PLEASE_CONTINUE_STRAIGHT.wav', originalFileName: 'compound_go_straight_1.wav', eventKey: 'PLEASE_CONTINUE_STRAIGHT_2', descriptionZh: '請繼續直行', triggerDistanceMeters: 500, enabled: true },
  { fileName: 'KEEP_SLIGHT_RIGHT_4.wav', prefixedFileName: 'compound_keep_right_KEEP_SLIGHT_RIGHT.wav', originalFileName: 'compound_keep_right.wav', eventKey: 'KEEP_SLIGHT_RIGHT_4', descriptionZh: '微靠右行', triggerDistanceMeters: 150, enabled: true },
  { fileName: 'HEAD_NORTHEAST.wav', prefixedFileName: 'start_3_NORTHEAST.wav', originalFileName: 'start_3.wav', eventKey: 'HEAD_NORTHEAST', descriptionZh: '往東北', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'RECOMPUTING_CHIME.wav', prefixedFileName: 'recomputing_1_RECOMPUTING_CHIME.wav', originalFileName: 'recomputing_1.wav', eventKey: 'RECOMPUTING_CHIME', descriptionZh: '重新計算路線提示音（叮咚）', triggerDistanceMeters: 0, enabled: true },
  { fileName: 'IN_100_METERS_TURN_RIGHT.wav', prefixedFileName: 'and_then_turn_right_IN_100_METERS_TURN_RIGHT.wav', originalFileName: 'and_then_turn_right.wav', eventKey: 'IN_100_METERS_TURN_RIGHT', descriptionZh: '在 100 公尺處，向右轉', triggerDistanceMeters: 100, enabled: true }
];

const SOURCE_FILES = [
  {
    path: 'app/src/main/java/com/ets2/trucksim/gps/map/ScsTileMapManager.kt',
    title: 'ScsTileMapManager.kt (SCS_Map_Tiles 內建與免解壓 .zip 地圖索引引擎)',
    desc: '支援 APK 內建 ETS2_Map_1.48.zip 與使用者手動導入任意 .zip 地圖包，透過 ZipFile 隨機讀取與 Localhost 18080 串流圖磚',
    highlights: [
      'ActivityResultContracts.OpenDocument() 導入任意 .zip 地圖檔至 filesDir/maps/',
      '使用 java.util.zip.ZipFile 建立 {z}/{x}/{y}.png 記憶體雜湊索引，零解壓縮等待時間且不佔雙倍空間',
      '自動解析 ZIP 內或 assets/scs_map/TileMapInfo.json 的 ETS2 座標邊界與縮放層級 (Zoom 0~8, 512px)',
      '內建 Localhost HTTP 圖磚伺服器 (127.0.0.1:18080/scs_tiles/{z}/{x}/{y}.png) 供 MapLibre 直接渲染'
    ]
  },
  {
    path: 'app/src/main/java/com/ets2/trucksim/gps/audio/TaiwanVoiceNavigator.kt',
    title: 'TaiwanVoiceNavigator.kt (47 個台灣導航語音檔完整對照與播放引擎)',
    desc: '內建 etsmod.zip 全部 47 個 .wav 實體音檔，支援三種檔名格式自動相容與暫態音訊焦點閃避',
    highlights: [
      '專屬音檔目錄：app/src/main/assets/voice_tw/（已放入全部 47 個 .wav 與完整對照表）',
      '自動相容「純英文大寫新檔名 (TURN_LEFT.wav)」、「保留前綴檔名」與「原檔名 (turn_left.wav)」',
      'AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 短暫壓低背景音樂，播畢立即釋放焦點',
      '缺音檔時僅提示或略過，絕不調用系統 TTS 合成語音取代'
    ]
  },
  {
    path: 'app/src/main/java/com/ets2/trucksim/gps/ui/MainActivity.kt',
    title: 'MainActivity.kt (主畫面、連線驗證、.zip 地圖選擇器與 2-1~2-4 階段狀態機)',
    desc: '整合啟動連線、離線內建地圖進入模式、OpenDocument .zip 導入器與每 5 秒位置保存',
    highlights: [
      '啟動畫面與主畫面皆可一鍵點擊「導入 .zip 地圖檔 (免解壓)」切換自訂地圖包',
      '提供「直接使用 APK 內建 / 已導入 .zip 地圖進入主畫面」離線瀏覽模式',
      '無遊戲座標時自動從柏林、慕尼黑、漢堡等 10 座德國城市隨機選取初始點',
      '每隔 5 秒與 onPause 離開時自動將卡車最後座標寫入 SharedPreferences'
    ]
  },
  {
    path: 'app/src/main/java/com/ets2/trucksim/gps/engine/FrameRateController.kt',
    title: 'FrameRateController.kt (30~185/無限 FPS 與 Surface 刷新率控制器)',
    desc: '實作 Display.supportedModes 偵測、preferredDisplayModeId 切換與 Choreographer FPS 計數器',
    highlights: [
      '支援 30 / 60 / 120 / 144 / 165 / 185 / 無限 七段幀率設定',
      '超過硬體螢幕上限時標示「裝置不支援」並自動以螢幕最高刷新率運作',
      '支援低電量 (<=20%) 自動降幀至 60 FPS 開關'
    ]
  },
  {
    path: '.github/workflows/build.yml',
    title: '.github/workflows/build.yml (自動下載 SCS_Map_Tiles 並編譯 APK)',
    desc: 'GitHub Actions 自動下載 Unicor-p/SCS_Map_Tiles 的 ETS2_Map_1.48.zip 寫入 APK 並產出 Debug APK',
    highlights: [
      '編譯前自動執行 curl 下載 ETS2_Map_1.48.zip 至 app/src/main/assets/scs_map/',
      '搭配 build.gradle.kts 的 noCompress += listOf("zip", "wav", "png") 確保高速隨機讀取',
      '固定使用 ubuntu-24.04、JDK 17、Gradle 8.7 與 MapLibre 11.0.0 確保 100% 穩定編譯'
    ]
  }
];

export const AndroidProjectExplorer: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'stages' | 'map_pack' | 'source' | 'voice' | 'build'>('map_pack');
  const [selectedStage, setSelectedStage] = useState<DeliveryStageId>('2-4');
  const [voiceMappings, setVoiceMappings] = useState(INITIAL_VOICE_MAPPINGS);
  const [unit, setUnit] = useState<'km/h' | 'mph'>('km/h');
  const [simSpeedKmh, setSimSpeedKmh] = useState<number>(88);
  const [simLimitKmh, setSimLimitKmh] = useState<number>(80);
  const [playingFile, setPlayingFile] = useState<string | null>(null);

  // 模擬或預覽使用者在網頁端測試上傳 .zip 地圖包
  const [customZipInfo, setCustomZipInfo] = useState<{
    name: string;
    sizeMb: string;
    status: string;
    isCustom: boolean;
  }>({
    name: 'ETS2_Map_1.48.zip (APK 內建 Unicor-p/SCS_Map_Tiles)',
    sizeMb: '154.79 MB (CI 自動封裝 + 內建 Zoom 0~2 基底)',
    status: '已載入 TileMapInfo.json • ETS2 v1.48.1.6 • 512px 圖磚 • Zoom 0~8',
    isCustom: false
  });
  const [previewTileCoord, setPreviewTileCoord] = useState<{ z: number; x: number; y: number }>({ z: 1, x: 1, y: 0 });
  const zipInputRef = useRef<HTMLInputElement | null>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  const isOverspeed = simSpeedKmh > simLimitKmh;
  const displaySpeed = unit === 'km/h' ? simSpeedKmh : Math.round(simSpeedKmh * 0.621371);
  const displayLimit = unit === 'km/h' ? simLimitKmh : Math.round(simLimitKmh * 0.621371);

  const playVoiceSample = (fileName: string) => {
    try {
      if (audioRef.current) {
        audioRef.current.pause();
      }
      const audio = new Audio(`/voice_tw/${fileName}`);
      audioRef.current = audio;
      setPlayingFile(fileName);
      audio.onended = () => setPlayingFile(null);
      audio.onerror = () => setPlayingFile(null);
      audio.play();
    } catch {
      setPlayingFile(null);
    }
  };

  const handleZipFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const sizeMb = (file.size / (1024 * 1024)).toFixed(2);
    setCustomZipInfo({
      name: file.name,
      sizeMb: `${sizeMb} MB`,
      status: `免解壓縮 ZipFile 索引建立完成！已掛載 ${file.name} 至 Localhost:18080 圖磚伺服器`,
      isCustom: true
    });
  };

  return (
    <div className="min-h-screen bg-[#0B0E14] text-[#F8FAFC] flex flex-col font-sans">
      {/* 頂部導覽列 */}
      <header className="border-b border-[#262F40] bg-[#161B26]/90 backdrop-blur px-6 py-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-blue-600 flex items-center justify-center shadow-lg shadow-blue-600/30">
            <Navigation className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-bold tracking-tight">ETS2 TruckSim GPS</h1>
              <span className="text-xs font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                SCS_Map_Tiles 內建 + .zip 免解壓導入 + 47 語音檔
              </span>
            </div>
            <p className="text-xs text-[#94A3B8]">
              原生 Android 專案總覽 • Unicor-p/SCS_Map_Tiles 整合 • Google_map_TW[0] 47 音檔完整設定
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 bg-[#0B0E14] p-1 rounded-lg border border-[#262F40] flex-wrap">
          <button
            onClick={() => setActiveTab('map_pack')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'map_pack' ? 'bg-emerald-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <MapIcon className="w-3.5 h-3.5" />
            SCS 地圖包與 .zip 導入入口
          </button>
          <button
            onClick={() => setActiveTab('voice')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'voice' ? 'bg-blue-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <Volume2 className="w-3.5 h-3.5" />
            台灣語音對照表 (47 音檔試聽)
          </button>
          <button
            onClick={() => setActiveTab('stages')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'stages' ? 'bg-blue-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <Layers className="w-3.5 h-3.5" />
            分階段交付與 HUD 預覽
          </button>
          <button
            onClick={() => setActiveTab('source')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'source' ? 'bg-blue-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <FileCode2 className="w-3.5 h-3.5" />
            Android 關鍵架構
          </button>
          <button
            onClick={() => setActiveTab('build')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'build' ? 'bg-blue-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <Terminal className="w-3.5 h-3.5" />
            APK 自動編譯與目錄指南
          </button>
        </div>
      </header>

      {/* 主內容區 */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6">
        {activeTab === 'map_pack' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* 左側：Unicor-p/SCS_Map_Tiles 內建與 .zip 導入模擬面板 */}
            <div className="lg:col-span-7 space-y-4">
              <div className="bg-[#161B26] border border-emerald-500/40 rounded-xl p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-xs font-mono text-emerald-400 uppercase">
                      Unicor-p/SCS_Map_Tiles + Custom .zip Importer
                    </span>
                    <h2 className="text-lg font-bold mt-0.5">
                      APK 內建 ETS2 地圖與「免解壓縮 .zip 地圖檔」導入入口
                    </h2>
                  </div>
                  <Archive className="w-6 h-6 text-emerald-400" />
                </div>

                <p className="text-xs text-[#94A3B8] leading-relaxed">
                  已將 <code className="text-emerald-300">https://github.com/Unicor-p/SCS_Map_Tiles</code> 的{' '}
                  <code className="text-emerald-300">TileMapInfo.json</code> 與基礎離線圖磚直接寫入專案{' '}
                  <code className="text-blue-300">app/src/main/assets/scs_map/</code>，並在 GitHub Actions 編譯 APK 時自動下載完整{' '}
                  <code className="text-emerald-300">ETS2_Map_1.48.zip (155MB)</code> 封裝進 APK。同時在 App 啟動畫面與設定頁提供下方「導入 .zip 地圖檔」入口：
                </p>

                {/* 目前掛載的地圖包狀態卡片 (仿 Android 端 ScsMapPackCard) */}
                <div className="bg-[#0B0E14] border border-[#262F40] rounded-xl p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-emerald-300">
                      🗺️ 目前作用中地圖包 (ScsTileMapManager)
                    </span>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300">
                      {customZipInfo.isCustom ? '自訂 .ZIP 免解壓索引中' : 'APK 內建預設地圖'}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                    <div className="bg-[#161B26] p-2.5 rounded-lg border border-[#262F40]">
                      <div className="text-[#94A3B8] text-[11px]">檔案名稱</div>
                      <div className="font-mono font-bold text-white mt-0.5 truncate">{customZipInfo.name}</div>
                    </div>
                    <div className="bg-[#161B26] p-2.5 rounded-lg border border-[#262F40]">
                      <div className="text-[#94A3B8] text-[11px]">檔案大小 / 讀取模式</div>
                      <div className="font-mono font-bold text-emerald-400 mt-0.5">{customZipInfo.sizeMb}</div>
                    </div>
                  </div>

                  <div className="text-xs text-amber-300 bg-amber-500/10 border border-amber-500/30 rounded-lg px-3 py-2">
                    {customZipInfo.status}
                  </div>

                  <input
                    ref={zipInputRef}
                    type="file"
                    accept=".zip,application/zip"
                    onChange={handleZipFileSelect}
                    className="hidden"
                  />

                  <div className="flex flex-wrap gap-2 pt-1">
                    <button
                      onClick={() => zipInputRef.current?.click()}
                      className="flex-1 py-2.5 px-4 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-[#052E16] font-bold text-xs flex items-center justify-center gap-2 transition"
                    >
                      <Upload className="w-4 h-4" />
                      導入 .zip 地圖檔 (免解壓縮直接建立索引)
                    </button>
                    {customZipInfo.isCustom && (
                      <button
                        onClick={() =>
                          setCustomZipInfo({
                            name: 'ETS2_Map_1.48.zip (APK 內建 Unicor-p/SCS_Map_Tiles)',
                            sizeMb: '154.79 MB (CI 自動封裝 + 內建 Zoom 0~2 基底)',
                            status: '已恢復為 APK 內建 Unicor-p/SCS_Map_Tiles 預設地圖',
                            isCustom: false
                          })
                        }
                        className="py-2.5 px-3 rounded-lg bg-[#161B26] hover:bg-[#1E293B] text-[#CBD5E1] border border-[#334155] text-xs font-semibold flex items-center gap-1.5 transition"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        恢復內建地圖
                      </button>
                    )}
                  </div>
                </div>

                {/* TileMapInfo.json 參數一覽 */}
                <div className="bg-[#0B0E14] border border-[#262F40] rounded-xl p-4">
                  <div className="text-xs font-bold text-blue-400 mb-2">
                    內建 `app/src/main/assets/scs_map/TileMapInfo.json` 座標與圖磚參數
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs font-mono">
                    <div className="p-2 rounded bg-[#161B26]">
                      <span className="text-[#94A3B8] block text-[10px]">遊戲版本</span>
                      ets2 (1.48.1.6)
                    </div>
                    <div className="p-2 rounded bg-[#161B26]">
                      <span className="text-[#94A3B8] block text-[10px]">圖磚解析度</span>
                      512 x 512 px
                    </div>
                    <div className="p-2 rounded bg-[#161B26]">
                      <span className="text-[#94A3B8] block text-[10px]">縮放層級</span>
                      Zoom 0 ~ 8
                    </div>
                    <div className="p-2 rounded bg-[#161B26]">
                      <span className="text-[#94A3B8] block text-[10px]">SCS X/Y 範圍</span>
                      -94621 ~ +93782
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* 右側：真實內建的 Unicor-p/SCS_Map_Tiles 圖磚檢視器 */}
            <div className="lg:col-span-5 bg-[#161B26] border border-[#262F40] rounded-xl p-5 flex flex-col justify-between space-y-4">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-bold flex items-center gap-2">
                    <MapPin className="w-4 h-4 text-emerald-400" />
                    APK 內建 `assets/scs_map/Tiles/` 實體圖磚預覽
                  </h3>
                  <span className="text-xs font-mono text-[#94A3B8]">
                    Tiles/{previewTileCoord.z}/{previewTileCoord.x}/{previewTileCoord.y}.png
                  </span>
                </div>
                <p className="text-xs text-[#94A3B8] mb-3">
                  以下直接渲染已寫入專案 <code className="text-emerald-300">app/src/main/assets/scs_map/Tiles/</code> 的{' '}
                  <code className="text-emerald-300">Unicor-p/SCS_Map_Tiles</code> 實體 PNG 圖磚：
                </p>

                <div className="flex gap-2 mb-3">
                  {[
                    { label: 'Zoom 0 全歐總覽 (0/0/0)', z: 0, x: 0, y: 0 },
                    { label: 'Zoom 1 西北歐 (1/0/0)', z: 1, x: 0, y: 0 },
                    { label: 'Zoom 1 中東歐 (1/1/0)', z: 1, x: 1, y: 0 },
                    { label: 'Zoom 2 德國核心區 (2/2/1)', z: 2, x: 2, y: 1 }
                  ].map((item) => (
                    <button
                      key={`${item.z}-${item.x}-${item.y}`}
                      onClick={() => setPreviewTileCoord({ z: item.z, x: item.x, y: item.y })}
                      className={`px-2.5 py-1.5 rounded text-[11px] font-semibold border transition ${
                        previewTileCoord.z === item.z &&
                        previewTileCoord.x === item.x &&
                        previewTileCoord.y === item.y
                          ? 'bg-emerald-600 text-white border-emerald-400'
                          : 'bg-[#0B0E14] text-[#94A3B8] border-[#262F40] hover:text-white'
                      }`}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>

                <div className="aspect-square w-full bg-[#0B0E14] border border-[#262F40] rounded-xl overflow-hidden flex items-center justify-center relative">
                  <img
                    src={`/scs_map/Tiles/${previewTileCoord.z}/${previewTileCoord.x}/${previewTileCoord.y}.png`}
                    alt="SCS Map Tile Preview"
                    className="w-full h-full object-contain"
                  />
                  <div className="absolute bottom-2 right-2 bg-[#0B0E14]/90 border border-[#262F40] px-2.5 py-1 rounded text-[11px] font-mono text-emerald-400">
                    512x512 PNG • SCS_Map_Tiles
                  </div>
                </div>
              </div>

              <div className="text-[11px] text-[#94A3B8] bg-[#0B0E14] p-3 rounded-lg border border-[#262F40]">
                💡 <strong>離線與自訂擴充說明</strong>：除了 GitHub Actions 自動將完整 155MB 的{' '}
                <code>ETS2_Map_1.48.zip</code> 打包進 APK 外，您隨時可在手機端點擊「導入 .zip 地圖檔」載入更新版或含 ProMods 的{' '}
                <code>.zip</code> 圖磚包。
              </div>
            </div>
          </div>
        )}

        {activeTab === 'voice' && (
          <div className="bg-[#161B26] border border-[#262F40] rounded-xl p-6 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-bold flex items-center gap-2">
                  <Volume2 className="w-5 h-5 text-blue-400" />
                  ETS2「Google Map 台灣語音模組 2」— 47 個實體音檔完整對照表 (`etsmod.zip`)
                </h2>
                <p className="text-xs text-[#94A3B8] mt-1">
                  音檔與對照表專屬放置目錄：<code className="text-emerald-300 font-mono">app/src/main/assets/voice_tw/</code>
                  （已將您上傳的 <strong>47 個 .wav 音檔</strong>、<code>Google_map_TW[0].txt</code> 與{' '}
                  <code>Google_map_TW[0]_完整對照表.txt</code> 全數寫入此目錄，點擊右側按鈕可直接線上試聽真實音檔）
                </p>
              </div>
              <span className="text-xs font-mono px-3 py-1 rounded-md bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                共 {voiceMappings.length} / 47 個 .wav 實體音檔已就緒
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-[#262F40] text-[#94A3B8]">
                    <th className="py-2.5 px-3">#</th>
                    <th className="py-2.5 px-3">新檔名（純英文大寫，zip 內實體檔）</th>
                    <th className="py-2.5 px-3">保留原前綴大寫檔名</th>
                    <th className="py-2.5 px-3">原檔名 (ETS2)</th>
                    <th className="py-2.5 px-3">中文語音說明</th>
                    <th className="py-2.5 px-3">試聽真實音檔</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#262F40]">
                  {voiceMappings.map((row, idx) => (
                    <tr key={row.fileName} className="hover:bg-[#0B0E14]/60">
                      <td className="py-2.5 px-3 font-mono text-[#64748B]">{idx + 1}</td>
                      <td className="py-2.5 px-3 font-mono text-emerald-300 font-semibold">{row.fileName}</td>
                      <td className="py-2.5 px-3 font-mono text-[#94A3B8] text-[11px]">{row.prefixedFileName}</td>
                      <td className="py-2.5 px-3 font-mono text-blue-300">{row.originalFileName}</td>
                      <td className="py-2.5 px-3">
                        <input
                          type="text"
                          value={row.descriptionZh}
                          onChange={(e) => {
                            const next = [...voiceMappings];
                            next[idx] = { ...row, descriptionZh: e.target.value };
                            setVoiceMappings(next);
                          }}
                          className="bg-[#0B0E14] border border-[#262F40] rounded px-2 py-1 text-xs text-white w-full max-w-xs"
                        />
                      </td>
                      <td className="py-2.5 px-3">
                        <button
                          onClick={() => playVoiceSample(row.fileName)}
                          className={`px-2.5 py-1 rounded text-xs font-semibold flex items-center gap-1 transition ${
                            playingFile === row.fileName
                              ? 'bg-emerald-500 text-[#052E16]'
                              : 'bg-blue-600/20 text-blue-300 border border-blue-500/40 hover:bg-blue-600 hover:text-white'
                          }`}
                        >
                          <Play className="w-3 h-3" />
                          {playingFile === row.fileName ? '播放中...' : '試聽'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {activeTab === 'stages' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* 左側：階段 2-1 ~ 2-4 驗收切換器 */}
            <div className="lg:col-span-7 space-y-4">
              <div className="bg-[#161B26] border border-[#262F40] rounded-xl p-5">
                <h2 className="text-base font-bold mb-3 flex items-center gap-2">
                  <ShieldCheck className="w-5 h-5 text-blue-400" />
                  分階段交付驗收清單 (2-1 至 2-4)
                </h2>
                <div className="grid grid-cols-4 gap-2 mb-4">
                  {(['2-1', '2-2', '2-3', '2-4'] as DeliveryStageId[]).map((st) => (
                    <button
                      key={st}
                      onClick={() => setSelectedStage(st)}
                      className={`py-2.5 px-3 rounded-lg text-xs font-bold border transition ${
                        selectedStage === st
                          ? 'bg-blue-600 border-blue-400 text-white'
                          : 'bg-[#0B0E14] border-[#262F40] text-[#94A3B8] hover:text-white'
                      }`}
                    >
                      階段 {st}
                    </button>
                  ))}
                </div>

                {selectedStage === '2-1' && (
                  <div className="space-y-2.5 text-sm text-[#CBD5E1]">
                    <div className="font-bold text-white">階段 2-1：基礎連線、最後座標保存與雙圓形時速/限速儀表</div>
                    <ul className="space-y-2 text-xs leading-relaxed">
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>開啟 App 先顯示連線畫面（記住上次輸入的 IP 與埠號），或直接點擊使用 APK 內建 SCS_Map_Tiles 地圖進入。</span>
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>無遊戲數據時讀取本地最後座標；首次開啟無保存座標時，自動於德國 10 座城市隨機選取初始點。</span>
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>角落雙圓形徽章（目前時速 + 當前路段限速），支援點擊切換 km/h 與 mph，超速時圓形自動變色警示。</span>
                      </li>
                    </ul>
                  </div>
                )}

                {selectedStage === '2-2' && (
                  <div className="space-y-2.5 text-sm text-[#CBD5E1]">
                    <div className="font-bold text-white">階段 2-2：30~185/無限 幀數設定與流暢度最佳化</div>
                    <ul className="space-y-2 text-xs leading-relaxed">
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>設定頁提供 30、60、120、144、165、185、無限（跟隨螢幕最高刷新率）；超過硬體上限時標示「裝置不支援」。</span>
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>使用 Window preferredDisplayModeId 與 Choreographer 實作，並提供即時 FPS 計數器與低電量自動降幀開關。</span>
                      </li>
                    </ul>
                  </div>
                )}

                {selectedStage === '2-3' && (
                  <div className="space-y-2.5 text-sm text-[#CBD5E1]">
                    <div className="font-bold text-white">階段 2-3：地圖互動標點、POI 視窗與多路徑點路線規劃</div>
                    <ul className="space-y-2 text-xs leading-relaxed">
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>手指滑動地圖自動暫停跟隨並浮現「回到卡車」按鈕；支援車頭朝上與北方朝上切換。</span>
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>點選任意位置或維修廠、加油站、休息站、城市時跳出小視窗：「新增路徑點」或「重新標點(改成唯一目的地)」。</span>
                      </li>
                    </ul>
                  </div>
                )}

                {selectedStage === '2-4' && (
                  <div className="space-y-2.5 text-sm text-[#CBD5E1]">
                    <div className="font-bold text-white">階段 2-4：台灣語音模組 2 (47音檔)、SCS_Map_Tiles 內建與延伸遙測</div>
                    <ul className="space-y-2 text-xs leading-relaxed">
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>內建 `etsmod.zip` 全部 47 個 `.wav` 台灣語音檔與完整對照表，支援三種檔名格式自動匹配與音訊焦點閃避。</span>
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>內建 `Unicor-p/SCS_Map_Tiles` 地圖並支援免解壓縮 `.zip` 地圖檔導入入口 (`ScsTileMapManager.kt`)。</span>
                      </li>
                      <li className="flex items-start gap-2">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>讀取貨運資訊並提供「一鍵導航到貨運目的地」；低油量、車損過高、疲勞不足時自動提示並標出附近站點。</span>
                      </li>
                    </ul>
                  </div>
                )}
              </div>
            </div>

            {/* 右側：Android 戰術 HUD 互動預覽 */}
            <div className="lg:col-span-5 bg-[#161B26] border border-[#262F40] rounded-xl p-5 flex flex-col justify-between space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#94A3B8] flex items-center gap-1.5">
                  <Smartphone className="w-4 h-4 text-blue-400" />
                  Android 端雙圓形時速/限速與超速警示預覽
                </span>
                <span className="text-xs font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  120 FPS
                </span>
              </div>

              <div className="relative h-64 rounded-xl bg-[#0B0E14] border border-[#262F40] overflow-hidden p-4 flex flex-col justify-between">
                <div className="bg-[#161B26]/95 border border-[#262F40] rounded-lg px-3 py-2 flex items-center justify-between">
                  <div>
                    <div className="text-[10px] text-emerald-400 font-bold">
                      已載入 ETS2_Map_1.48.zip • 德國 柏林 (Berlin)
                    </div>
                    <div className="text-xs font-bold text-white">
                      在 300 公尺處，向右轉 (IN_300_METERS_TURN_RIGHT_2.wav)
                    </div>
                  </div>
                  <button
                    onClick={() => playVoiceSample('IN_300_METERS_TURN_RIGHT_2.wav')}
                    className="px-2 py-1 rounded bg-blue-600 text-white text-[11px] font-bold flex items-center gap-1"
                  >
                    <Play className="w-3 h-3" />
                    播報
                  </button>
                </div>

                {/* 雙圓形時速與限速徽章 */}
                <div className="flex items-end justify-between">
                  <div
                    onClick={() => setUnit(unit === 'km/h' ? 'mph' : 'km/h')}
                    className="flex items-center gap-3 cursor-pointer select-none"
                    title="點擊切換 km/h 與 mph"
                  >
                    <div
                      className={`w-16 h-16 rounded-full flex flex-col items-center justify-center border-[5px] transition ${
                        isOverspeed
                          ? 'bg-red-600 border-red-800 text-white shadow-lg shadow-red-600/40'
                          : 'bg-slate-50 border-red-500 text-slate-900'
                      }`}
                    >
                      <span className="text-lg font-extrabold font-mono leading-none">{displayLimit}</span>
                      <span className="text-[9px] font-bold mt-0.5">限速</span>
                    </div>

                    <div
                      className={`w-20 h-20 rounded-full flex flex-col items-center justify-center border-2 transition ${
                        isOverspeed
                          ? 'bg-red-950/90 border-red-500 text-red-200 shadow-lg shadow-red-500/30'
                          : 'bg-[#161B26]/95 border-blue-500 text-white'
                      }`}
                    >
                      <span className="text-2xl font-extrabold font-mono leading-none">{displaySpeed}</span>
                      <span className="text-[10px] text-[#94A3B8] font-semibold mt-1">{unit}</span>
                    </div>
                  </div>

                  <div className="text-right space-y-1">
                    <div className="text-[11px] text-[#94A3B8]">點擊圓形切換 km/h • mph</div>
                    <div className="flex gap-2 justify-end">
                      <button
                        onClick={() => setSimSpeedKmh(72)}
                        className="px-2.5 py-1 rounded bg-[#161B26] border border-[#262F40] text-xs hover:border-blue-400"
                      >
                        正常 (72)
                      </button>
                      <button
                        onClick={() => {
                          setSimSpeedKmh(96);
                          playVoiceSample('SPEED_WARNING_BEEP_1.wav');
                        }}
                        className="px-2.5 py-1 rounded bg-red-950/60 border border-red-500/50 text-xs text-red-300 hover:bg-red-900/60"
                      >
                        超速 (96 + 嘟嘟聲)
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'source' && (
          <div className="space-y-4">
            {SOURCE_FILES.map((file) => (
              <div
                key={file.path}
                className="bg-[#161B26] border border-[#262F40] rounded-xl p-5 space-y-3"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="text-sm font-bold text-white">{file.title}</h3>
                  <span className="text-xs font-mono text-blue-400 bg-blue-950/50 px-2.5 py-1 rounded border border-blue-500/30">
                    {file.path}
                  </span>
                </div>
                <p className="text-xs text-[#94A3B8]">{file.desc}</p>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-2 pt-1">
                  {file.highlights.map((h, i) => (
                    <div
                      key={i}
                      className="text-xs bg-[#0B0E14] border border-[#262F40] rounded-lg px-3 py-2 text-[#CBD5E1] flex items-center gap-2"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                      <span>{h}</span>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {activeTab === 'build' && (
          <div className="bg-[#161B26] border border-[#262F40] rounded-xl p-6 space-y-5">
            <h2 className="text-base font-bold flex items-center gap-2">
              <FolderGit2 className="w-5 h-5 text-emerald-400" />
              檔案放置目錄總覽與 GitHub Actions 自動編譯 APK 指南
            </h2>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
              <div className="bg-[#0B0E14] border border-emerald-500/40 rounded-lg p-4 space-y-2">
                <div className="font-bold text-emerald-400">
                  1. 台灣語音檔與對照表專屬目錄（已放入全部 47 個 .wav）
                </div>
                <pre className="font-mono text-[11px] text-[#CBD5E1] overflow-x-auto leading-relaxed">
{`app/src/main/assets/voice_tw/
├── voice_mapping.json               (47 筆完整對照設定)
├── Google_map_TW[0].txt             (原始清單)
├── Google_map_TW[0]_完整對照表.txt   (47 筆中英對照文檔)
├── TURN_LEFT.wav                    (向左轉)
├── IN_300_METERS_TURN_LEFT_1.wav    (在 300 公尺處，向左轉)
└── ... (共 47 個 .wav 實體音檔)`}
                </pre>
              </div>

              <div className="bg-[#0B0E14] border border-blue-500/40 rounded-lg p-4 space-y-2">
                <div className="font-bold text-blue-400">
                  2. 內建 SCS_Map_Tiles 地圖目錄與 CI 自動封裝
                </div>
                <pre className="font-mono text-[11px] text-[#CBD5E1] overflow-x-auto leading-relaxed">
{`app/src/main/assets/scs_map/
├── TileMapInfo.json                 (Unicor-p/SCS_Map_Tiles 座標定義)
├── ETS2_Map_1.48.zip                (GitHub Actions 編譯時自動下載封裝)
└── Tiles/
    ├── 0/0/0.png                    (內建離線全歐底圖)
    ├── 1/{0..1}/{0..1}.png
    └── 2/{0..3}/{0..3}.png`}
                </pre>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
