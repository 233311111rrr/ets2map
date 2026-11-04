import React, { useState, useRef } from 'react';
import {
  Smartphone,
  Tablet,
  Volume2,
  FolderGit2,
  CheckCircle2,
  FileCode2,
  Navigation,
  Terminal,
  Play,
  MapPin,
  Upload,
  Archive,
  Map as MapIcon,
  RotateCcw,
  Move,
  Sparkles
} from 'lucide-react';
import { VoiceMappingEntry } from '../types';

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
    path: 'app/src/main/java/com/ets2/trucksim/gps/ui/Components.kt',
    title: 'Components.kt (長按自由移動 DraggableHudWidget + 跨手機/平板自適應 AdaptiveScreenMetrics)',
    desc: '移除分階段驗收切換器，使用 detectDragGesturesAfterLongPress 實作主畫面所有 UI 元件長按自由拖曳與螢幕比例記憶',
    highlights: [
      'DraggableHudWidget：支援長按震動回饋、藍色拖曳指示框、防超出螢幕邊界與相對座標 (xFraction, yFraction) 持久化',
      'AdaptiveScreenMetrics：透過 BoxWithConstraints 自動適配小手機 (<360dp)、標準手機、摺疊機與平板 (>=640dp)',
      '平板與橫向模式下，設定面板與路徑點抽屜自動限寬 540dp 並停靠側邊，不遮擋主導航視野'
    ]
  },
  {
    path: 'app/src/main/java/com/ets2/trucksim/gps/map/ScsTileMapManager.kt',
    title: 'ScsTileMapManager.kt (2K @2x 超清圖磚串流、Zoom 0~8 快取與免解壓 .zip 索引)',
    desc: '內建 Zoom 0~5 核心 512px 高清圖磚，並透過 OkHttp 直接抓取與快取 Zoom 3~8 街道級圖磚 (最高 131,072×131,072 px)',
    highlights: [
      '搭配 MapLibre tileSize: 256 啟動 @2x Retina / 2K High-DPI 雙倍像素密度超採樣，清晰度提升 400%',
      '解決跨協定 302 跳轉問題：Localhost:18080 直接由背景 OkHttp 下載並寫入 filesDir/tile_cache_2k/',
      '支援 ActivityResultContracts.OpenDocument() 免解壓縮直接建立 .zip 地圖索引'
    ]
  },
  {
    path: 'app/src/main/java/com/ets2/trucksim/gps/ui/MainActivity.kt',
    title: 'MainActivity.kt (全功能常駐主畫面 + 四大可長按拖曳 HUD 區塊)',
    desc: '將頂部導航橫幅、雙圓形時速/限速儀表、FPS 計數器與右下角快捷列全面封裝為可長按移動元件',
    highlights: [
      '已徹底移除 2-1 ~ 2-4 驗收模式切換按鈕，所有進階語音、路徑點與遙測延伸功能預設全開',
      '在設定頁提供「重置 UI 位置」按鈕，一鍵恢復四大 HUD 元件預設座標',
      '每隔 5 秒與離開 App 時自動將卡車最後座標寫入本地'
    ]
  },
  {
    path: 'app/src/main/java/com/ets2/trucksim/gps/audio/TaiwanVoiceNavigator.kt',
    title: 'TaiwanVoiceNavigator.kt (47 個台灣導航語音檔完整對照與播放引擎)',
    desc: '內建 etsmod.zip 全部 47 個 .wav 實體音檔，支援三種檔名格式自動相容與暫態音訊焦點閃避',
    highlights: [
      '專屬音檔目錄：app/src/main/assets/voice_tw/（已放入全部 47 個 .wav 與完整對照表）',
      '自動相容「純英文大寫新檔名 (TURN_LEFT.wav)」、「保留前綴檔名」與「原檔名 (turn_left.wav)」',
      'AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 短暫壓低背景音樂，播畢立即釋放焦點'
    ]
  }
];

type DevicePreset = 'compact_phone' | 'standard_phone' | 'foldable' | 'tablet_landscape';

export const AndroidProjectExplorer: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'hud_sim' | 'map_pack' | 'voice' | 'source' | 'build'>('hud_sim');
  const [voiceMappings, setVoiceMappings] = useState(INITIAL_VOICE_MAPPINGS);
  const [unit, setUnit] = useState<'km/h' | 'mph'>('km/h');
  const [simSpeedKmh, setSimSpeedKmh] = useState<number>(86);
  const [simLimitKmh] = useState<number>(80);
  const [playingFile, setPlayingFile] = useState<string | null>(null);
  const [devicePreset, setDevicePreset] = useState<DevicePreset>('tablet_landscape');

  // 模擬長按自由移動的四個 HUD 元件座標 (百分比)
  const defaultOffsets = {
    topNav: { x: 3, y: 4 },
    fpsBadge: { x: 82, y: 5 },
    speedCluster: { x: 4, y: 68 },
    quickControls: { x: 45, y: 76 }
  };
  const [hudPos, setHudPos] = useState(defaultOffsets);
  const [draggingKey, setDraggingKey] = useState<keyof typeof defaultOffsets | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const [customZipInfo, setCustomZipInfo] = useState<{
    name: string;
    sizeMb: string;
    status: string;
    isCustom: boolean;
  }>({
    name: 'ETS2_Map_1.48.zip (APK 內建 2K @2x High-DPI 圖磚包)',
    sizeMb: '154.79 MB (CI 自動封裝 + 內建 105 張 Zoom 0~5 2K 高清圖磚)',
    status: '已啟用 2K Retina 超採樣 (tileSize: 256) • 最高解析度 131,072 × 131,072 px',
    isCustom: false
  });
  const [previewResolutionMode, setPreviewResolutionMode] = useState<'2k_germany_z4' | '4k_europe_z3' | 'berlin_z5'>('2k_germany_z4');
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
      status: `免解壓縮 2K ZipFile 索引建立完成！已掛載 ${file.name} 至 Localhost:18080 圖磚伺服器`,
      isCustom: true
    });
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!draggingKey || !containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    const relX = ((e.clientX - rect.left - 60) / rect.width) * 100;
    const relY = ((e.clientY - rect.top - 24) / rect.height) * 100;
    setHudPos((prev) => ({
      ...prev,
      [draggingKey]: {
        x: Math.max(1, Math.min(82, relX)),
        y: Math.max(2, Math.min(82, relY))
      }
    }));
  };

  const presetDimensions: Record<DevicePreset, { label: string; widthClass: string; heightClass: string; scale: number }> = {
    compact_phone: { label: '小尺寸手機 (360dp 直向)', widthClass: 'max-w-[360px]', heightClass: 'h-[540px]', scale: 0.85 },
    standard_phone: { label: '標準手機 (412dp 直向)', widthClass: 'max-w-[420px]', heightClass: 'h-[580px]', scale: 0.95 },
    foldable: { label: '摺疊機展開 (768dp)', widthClass: 'max-w-[680px]', heightClass: 'h-[520px]', scale: 1.05 },
    tablet_landscape: { label: '平板 / 車機橫向 (1024dp 2K)', widthClass: 'max-w-full', heightClass: 'h-[520px]', scale: 1.12 }
  };

  const activePreset = presetDimensions[devicePreset];

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
                2K 超清地圖 • 長按自由移動 UI • 手機/平板自適應
              </span>
            </div>
            <p className="text-xs text-[#94A3B8]">
              已移除分階段驗收切換 • 全功能預設開啟 • 內建 105 張 Zoom 0~5 2K 高清圖磚與 47 語音檔
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 bg-[#0B0E14] p-1 rounded-lg border border-[#262F40] flex-wrap">
          <button
            onClick={() => setActiveTab('hud_sim')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'hud_sim' ? 'bg-blue-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <Move className="w-3.5 h-3.5" />
            長按自由移動 UI & 手機/平板自適應預覽
          </button>
          <button
            onClick={() => setActiveTab('map_pack')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'map_pack' ? 'bg-emerald-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <MapIcon className="w-3.5 h-3.5" />
            2K 高清地圖素材 & .zip 導入
          </button>
          <button
            onClick={() => setActiveTab('voice')}
            className={`px-3 py-1.5 rounded-md text-xs font-semibold transition flex items-center gap-1.5 ${
              activeTab === 'voice' ? 'bg-blue-600 text-white' : 'text-[#94A3B8] hover:text-white'
            }`}
          >
            <Volume2 className="w-3.5 h-3.5" />
            台灣語音對照表 (47 音檔)
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
            APK 編譯指南
          </button>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto p-6">
        {activeTab === 'hud_sim' && (
          <div className="space-y-4">
            {/* 螢幕尺寸切換與重置按鈕列 */}
            <div className="bg-[#161B26] border border-[#262F40] rounded-xl p-4 flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-xs font-bold text-[#94A3B8] flex items-center gap-1.5 mr-2">
                  <Tablet className="w-4 h-4 text-blue-400" />
                  切換手機 / 平板螢幕尺寸：
                </span>
                {(Object.keys(presetDimensions) as DevicePreset[]).map((key) => (
                  <button
                    key={key}
                    onClick={() => setDevicePreset(key)}
                    className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition ${
                      devicePreset === key
                        ? 'bg-blue-600 text-white border-blue-400'
                        : 'bg-[#0B0E14] text-[#94A3B8] border-[#262F40] hover:text-white'
                    }`}
                  >
                    {presetDimensions[key].label}
                  </button>
                ))}
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => setHudPos(defaultOffsets)}
                  className="px-3 py-1.5 rounded-lg bg-[#0B0E14] hover:bg-[#1E293B] text-sky-400 border border-sky-500/40 text-xs font-semibold flex items-center gap-1.5 transition"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  重置 UI 元件位置
                </button>
              </div>
            </div>

            {/* 互動式 2K 地圖與可拖曳 HUD 畫布 */}
            <div className="flex justify-center">
              <div
                ref={containerRef}
                onPointerMove={handlePointerMove}
                onPointerUp={() => setDraggingKey(null)}
                onPointerLeave={() => setDraggingKey(null)}
                className={`w-full ${activePreset.widthClass} ${activePreset.heightClass} relative rounded-2xl border-2 border-[#334155] bg-[#0B0E14] overflow-hidden shadow-2xl select-none transition-all duration-300`}
              >
                {/* 底層：2K (2048x2048) 4x4 無縫拼接的德國核心區 Zoom 4 真實高清圖磚 */}
                <div className="absolute inset-0 grid grid-cols-4 grid-rows-4 opacity-95 pointer-events-none">
                  {[5, 6, 7, 8].map((y) =>
                    [6, 7, 8, 9].map((x) => (
                      <img
                        key={`z4-${x}-${y}`}
                        src={`/scs_map/Tiles/4/${x}/${y}.png`}
                        alt={`2K Tile 4/${x}/${y}`}
                        className="w-full h-full object-cover"
                      />
                    ))
                  )}
                </div>

                {/* 1. 可拖曳：頂部導航與貨運狀態橫幅 */}
                <div
                  onPointerDown={() => setDraggingKey('topNav')}
                  style={{ left: `${hudPos.topNav.x}%`, top: `${hudPos.topNav.y}%` }}
                  className={`absolute z-20 cursor-grab active:cursor-grabbing max-w-md w-[86%] sm:w-[420px] rounded-xl bg-[#161B26]/95 border p-3 shadow-xl transition-shadow ${
                    draggingKey === 'topNav' ? 'border-sky-400 ring-2 ring-sky-400/40' : 'border-[#262F40]'
                  }`}
                >
                  <div className="flex items-center justify-between text-[10px] text-sky-400 font-bold mb-1">
                    <span>✥ 按住可隨意拖曳移動 • 2K @2x 超清導航中</span>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        playVoiceSample('IN_300_METERS_TURN_RIGHT_2.wav');
                      }}
                      className="px-2 py-0.5 rounded bg-blue-600 text-white flex items-center gap-1"
                    >
                      <Play className="w-2.5 h-2.5" />
                      語音播報
                    </button>
                  </div>
                  <div className="text-sm font-bold text-white">
                    在 300 公尺處，向右轉 (前往 漢堡 Hamburg)
                  </div>
                  <div className="text-[11px] text-blue-300 mt-1 flex items-center justify-between">
                    <span>貨運任務：精密機械 (柏林 → 漢堡)</span>
                    <span className="font-mono text-emerald-400">剩餘 165 分</span>
                  </div>
                </div>

                {/* 2. 可拖曳：FPS 計數器膠囊 */}
                <div
                  onPointerDown={() => setDraggingKey('fpsBadge')}
                  style={{ left: `${hudPos.fpsBadge.x}%`, top: `${hudPos.fpsBadge.y}%` }}
                  className={`absolute z-20 cursor-grab active:cursor-grabbing rounded-lg bg-[#0B0E14]/95 border px-3 py-1.5 font-mono text-xs font-bold text-emerald-400 shadow-lg ${
                    draggingKey === 'fpsBadge' ? 'border-sky-400 ring-2 ring-sky-400/40' : 'border-emerald-500/60'
                  }`}
                >
                  120 FPS • 2K
                </div>

                {/* 3. 可拖曳：雙圓形時速與限速徽章 */}
                <div
                  onPointerDown={() => setDraggingKey('speedCluster')}
                  style={{
                    left: `${hudPos.speedCluster.x}%`,
                    top: `${hudPos.speedCluster.y}%`,
                    transform: `scale(${activePreset.scale})`,
                    transformOrigin: 'top left'
                  }}
                  className={`absolute z-20 cursor-grab active:cursor-grabbing flex items-center gap-3 p-2 rounded-2xl ${
                    draggingKey === 'speedCluster' ? 'bg-sky-500/20 border-2 border-sky-400' : ''
                  }`}
                >
                  <div
                    onClick={() => setUnit(unit === 'km/h' ? 'mph' : 'km/h')}
                    className={`w-16 h-16 rounded-full flex flex-col items-center justify-center border-[5px] shadow-xl ${
                      isOverspeed
                        ? 'bg-red-600 border-red-800 text-white'
                        : 'bg-slate-50 border-red-500 text-slate-900'
                    }`}
                  >
                    <span className="text-lg font-extrabold font-mono leading-none">{displayLimit}</span>
                    <span className="text-[9px] font-bold mt-0.5">限速</span>
                  </div>

                  <div
                    onClick={() => setUnit(unit === 'km/h' ? 'mph' : 'km/h')}
                    className={`w-20 h-20 rounded-full flex flex-col items-center justify-center border-2 shadow-xl ${
                      isOverspeed
                        ? 'bg-red-950/95 border-red-500 text-red-200'
                        : 'bg-[#161B26]/95 border-blue-500 text-white'
                    }`}
                  >
                    <span className="text-2xl font-extrabold font-mono leading-none">{displaySpeed}</span>
                    <span className="text-[10px] text-[#94A3B8] font-semibold mt-1">{unit}</span>
                  </div>
                </div>

                {/* 4. 可拖曳：右下角快捷控制列 */}
                <div
                  onPointerDown={() => setDraggingKey('quickControls')}
                  style={{ left: `${hudPos.quickControls.x}%`, top: `${hudPos.quickControls.y}%` }}
                  className={`absolute z-20 cursor-grab active:cursor-grabbing flex flex-wrap items-center gap-2 p-2 rounded-xl bg-[#161B26]/90 border shadow-xl ${
                    draggingKey === 'quickControls' ? 'border-sky-400 ring-2 ring-sky-400/40' : 'border-[#334155]'
                  }`}
                >
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setSimSpeedKmh(simSpeedKmh > 80 ? 74 : 95);
                      if (simSpeedKmh <= 80) playVoiceSample('SPEED_WARNING_BEEP_1.wav');
                    }}
                    className="px-2.5 py-1.5 rounded-full bg-amber-500/20 border border-amber-500/40 text-amber-300 text-xs font-semibold"
                  >
                    {isOverspeed ? '恢復正常車速 (74)' : '模擬超速 (95 + 嘟嘟聲)'}
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      zipInputRef.current?.click();
                    }}
                    className="px-2.5 py-1.5 rounded-full bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-xs font-semibold"
                  >
                    導入.zip地圖
                  </button>
                  <span className="px-2.5 py-1.5 rounded-full bg-[#0B0E14] text-xs text-slate-300 border border-[#262F40]">
                    車頭朝上
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'map_pack' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* 左側：2K 高畫質升級說明與 .zip 導入 */}
            <div className="lg:col-span-6 space-y-4">
              <div className="bg-[#161B26] border border-emerald-500/40 rounded-xl p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-xs font-mono text-emerald-400 uppercase flex items-center gap-1">
                      <Sparkles className="w-3.5 h-3.5" />
                      2K / 1080p High-DPI Map Engine
                    </span>
                    <h2 className="text-lg font-bold mt-0.5">
                      2K 超清地圖素材升級與免解壓 `.zip` 導入入口
                    </h2>
                  </div>
                  <Archive className="w-6 h-6 text-emerald-400" />
                </div>

                <div className="bg-[#0B0E14] border border-[#262F40] rounded-xl p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-emerald-300">
                      🗺️ 目前作用中地圖包 (ScsTileMapManager)
                    </span>
                    <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300">
                      {customZipInfo.isCustom ? '自訂 .ZIP 2K 索引中' : '2K @2x Retina 超採樣'}
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                    <div className="bg-[#161B26] p-2.5 rounded-lg border border-[#262F40]">
                      <div className="text-[#94A3B8] text-[11px]">檔案名稱</div>
                      <div className="font-mono font-bold text-white mt-0.5 truncate">{customZipInfo.name}</div>
                    </div>
                    <div className="bg-[#161B26] p-2.5 rounded-lg border border-[#262F40]">
                      <div className="text-[#94A3B8] text-[11px]">內建高清圖磚 / 讀取模式</div>
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
                  </div>
                </div>

                <div className="space-y-2 text-xs text-[#CBD5E1]">
                  <div className="font-bold text-white">為什麼現在畫質達到 2K / 1080p 超清等級？</div>
                  <ul className="space-y-1.5 text-[#94A3B8]">
                    <li>
                      1. <strong>內建 Zoom 3 (4096×4096 4K) 與 Zoom 4~5 (2048×2048 2K) 實體圖磚</strong>：已從{' '}
                      <code>Unicor-p/SCS_Map_Tiles</code> 抓取 105 張高階縮放圖磚直接寫入{' '}
                      <code>app/src/main/assets/scs_map/Tiles/</code>。
                    </li>
                    <li>
                      2. <strong>啟用 MapLibre `@2x Retina` 雙倍像素密度超採樣</strong>：將 512×512 圖磚以{' '}
                      <code>tileSize: 256</code> 渲染，每個邏輯區塊擁有 4 倍像素密度。
                    </li>
                    <li>
                      3. <strong>OkHttp 原生高階圖磚代理快取</strong>：自動下載並快取 Zoom 6~8 超清街道級圖磚至{' '}
                      <code>filesDir/tile_cache_2k/</code>。
                    </li>
                  </ul>
                </div>
              </div>
            </div>

            {/* 右側：2K (2048x2048) 4x4 拼接高清實機圖磚預覽 */}
            <div className="lg:col-span-6 bg-[#161B26] border border-[#262F40] rounded-xl p-5 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-sm font-bold flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-emerald-400" />
                  內建 2K (2048×2048 px) 4×4 高清拼接圖磚實測預覽
                </h3>
                <div className="flex gap-1.5">
                  <button
                    onClick={() => setPreviewResolutionMode('2k_germany_z4')}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border ${
                      previewResolutionMode === '2k_germany_z4'
                        ? 'bg-emerald-600 text-white border-emerald-400'
                        : 'bg-[#0B0E14] text-[#94A3B8] border-[#262F40]'
                    }`}
                  >
                    Zoom 4 德國公路網 (2048×2048 2K)
                  </button>
                  <button
                    onClick={() => setPreviewResolutionMode('berlin_z5')}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border ${
                      previewResolutionMode === 'berlin_z5'
                        ? 'bg-emerald-600 text-white border-emerald-400'
                        : 'bg-[#0B0E14] text-[#94A3B8] border-[#262F40]'
                    }`}
                  >
                    Zoom 5 近景細節 (1024×1024 FHD)
                  </button>
                  <button
                    onClick={() => setPreviewResolutionMode('4k_europe_z3')}
                    className={`px-2.5 py-1 rounded text-[11px] font-semibold border ${
                      previewResolutionMode === '4k_europe_z3'
                        ? 'bg-emerald-600 text-white border-emerald-400'
                        : 'bg-[#0B0E14] text-[#94A3B8] border-[#262F40]'
                    }`}
                  >
                    Zoom 3 中歐全覽 (2048×2048 2K)
                  </button>
                </div>
              </div>

              <div className="aspect-square w-full bg-[#0B0E14] border border-[#262F40] rounded-xl overflow-hidden relative">
                {previewResolutionMode === '2k_germany_z4' && (
                  <div className="w-full h-full grid grid-cols-4 grid-rows-4">
                    {[5, 6, 7, 8].map((y) =>
                      [6, 7, 8, 9].map((x) => (
                        <img
                          key={`z4-${x}-${y}`}
                          src={`/scs_map/Tiles/4/${x}/${y}.png`}
                          alt={`Tile 4/${x}/${y}`}
                          className="w-full h-full object-cover"
                        />
                      ))
                    )}
                  </div>
                )}

                {previewResolutionMode === 'berlin_z5' && (
                  <div className="w-full h-full grid grid-cols-2 grid-rows-2">
                    {[12, 13].map((y) =>
                      [15, 16].map((x) => (
                        <img
                          key={`z5-${x}-${y}`}
                          src={`/scs_map/Tiles/5/${x}/${y}.png`}
                          alt={`Tile 5/${x}/${y}`}
                          className="w-full h-full object-cover"
                        />
                      ))
                    )}
                  </div>
                )}

                {previewResolutionMode === '4k_europe_z3' && (
                  <div className="w-full h-full grid grid-cols-4 grid-rows-4">
                    {[2, 3, 4, 5].map((y) =>
                      [2, 3, 4, 5].map((x) => (
                        <img
                          key={`z3-${x}-${y}`}
                          src={`/scs_map/Tiles/3/${x}/${y}.png`}
                          alt={`Tile 3/${x}/${y}`}
                          className="w-full h-full object-cover"
                        />
                      ))
                    )}
                  </div>
                )}

                <div className="absolute bottom-2 right-2 bg-[#0B0E14]/90 border border-emerald-500/40 px-3 py-1 rounded text-[11px] font-mono text-emerald-400">
                  2048 × 2048 (2K UHD) • @2x High-DPI Native Assets
                </div>
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
                  音檔專屬放置目錄：<code className="text-emerald-300 font-mono">app/src/main/assets/voice_tw/</code>
                </p>
              </div>
              <span className="text-xs font-mono px-3 py-1 rounded-md bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                共 {voiceMappings.length} / 47 個 .wav 實體音檔已內建
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-[#262F40] text-[#94A3B8]">
                    <th className="py-2.5 px-3">#</th>
                    <th className="py-2.5 px-3">新檔名（純英文大寫）</th>
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
              2K 地圖與語音檔案目錄結構總覽
            </h2>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
              <div className="bg-[#0B0E14] border border-emerald-500/40 rounded-lg p-4 space-y-2">
                <div className="font-bold text-emerald-400">
                  1. 台灣語音檔與對照表專屬目錄（已內建 47 個 .wav）
                </div>
                <pre className="font-mono text-[11px] text-[#CBD5E1] overflow-x-auto leading-relaxed">
{`app/src/main/assets/voice_tw/
├── voice_mapping.json               (47 筆完整對照設定)
├── Google_map_TW[0].txt             (原始清單)
├── Google_map_TW[0]_完整對照表.txt   (47 筆中英對照文檔)
└── *.wav                            (共 47 個實體音檔)`}
                </pre>
              </div>

              <div className="bg-[#0B0E14] border border-blue-500/40 rounded-lg p-4 space-y-2">
                <div className="font-bold text-blue-400">
                  2. 2K 超清 SCS_Map_Tiles 地圖目錄（含 Zoom 0~5 105 張高清圖磚 + CI 完整包）
                </div>
                <pre className="font-mono text-[11px] text-[#CBD5E1] overflow-x-auto leading-relaxed">
{`app/src/main/assets/scs_map/
├── TileMapInfo.json                 (Unicor-p/SCS_Map_Tiles 座標定義)
├── ETS2_Map_1.48.zip                (GitHub Actions 自動下載封裝 Zoom 0~8)
└── Tiles/
    ├── 0..2/                        (基礎全歐圖磚)
    ├── 3/{0..7}/{0..7}.png          (4096×4096 4K 歐洲路網圖磚)
    ├── 4/{6..9}/{5..8}.png          (2048×2048 2K 德國核心高速公路圖磚)
    └── 5/{15..16}/{12..13}.png      (1080p 城市近景圖磚)`}
                </pre>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
