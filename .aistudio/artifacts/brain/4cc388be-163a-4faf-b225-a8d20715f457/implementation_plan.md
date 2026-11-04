# ETS2 TruckSim GPS — 修復「長按 UI 只閃一下無法拖動」問題計畫

## 一、問題根本原因定位 (`Components.kt` 中的 `DraggableHudWidget`)

您遇到的「長按 UI 後只會閃一下藍色框就恢復正常、無法拖動」現象，是由以下兩個 Jetpack Compose 手勢生命週期問題導致：

1. **`pointerInput` 因 `widgetSize` 改變而強制中斷重啟（最主要原因）**：
   - 在舊版 `DraggableHudWidget` 中，`.pointerInput(widgetId, safeParentW, safeParentH, widgetSize)` 把 `widgetSize` 列為了重啟鍵值 (Key)。
   - 當長按觸發 `onDragStart` 並將 `isDragging` 設為 `true` 時，元件內部動態插入了 `padding(2.dp)` 與 `Text("✥ 拖曳移動中...")`，導致元件尺寸 (`widgetSize`) 在下一幀改變。
   - `widgetSize` 一改變，Compose 就立刻**強制取消並重啟 `pointerInput` 手勢協程**，同時重置了 `remember(..., widgetSize.width)` 的 `dragX / dragY`，導致藍色外框只閃一下（1 幀）就立刻中斷拖曳！
2. **內部子按鈕 (`Button` / `OutlinedButton` / `clickable`) 搶奪觸控事件**：
   - 右下角快捷列與時速圓形內部含有 `clickable` 與 `OutlinedButton`，在預設的 `PointerEventPass.Main` 階段會攔截按下事件，干擾父容器的長按判定。

---

## 二、修正方案

1. **固定 `pointerInput(widgetId)` 鍵值，改用 `rememberUpdatedState` 讀取最新邊界**：
   - 將 `.pointerInput(widgetId)` 的 Key 鎖定為單一固定的 `widgetId`，**絕不將 `widgetSize`、`dragX`、`dragY` 放進 `pointerInput` 或 `remember` 的 Key 中**，確保手勢協程在整個拖曳過程中絕對不會被重啟或中斷。
2. **拖曳狀態視覺回饋改用「零尺寸變動 (Zero-Layout-Shift)」繪製**：
   - 當 `isDragging == true` 時，使用 `drawWithContent` 繪製高亮天藍色外框與半透明光暈，並以 `Popup` / 浮動疊層顯示拖曳狀態，**不改變元件本身的寬高尺寸**，徹底杜絕尺寸跳動。
3. **在 `PointerEventPass.Initial` 優先攔截長按拖曳手勢**：
   - 實作自訂的長按拖曳偵測器：當手指在元件任意位置（包含按鈕上方）按住超過長按門檻（300ms）後開始移動，立即由父容器在 `Initial` 階段接管並消費所有移動事件 (`change.consume()`)，同時取消子按鈕的誤觸；若只是短按點擊（<300ms），則正常觸發「切換單位」、「回到卡車」、「設定」等按鈕功能。
