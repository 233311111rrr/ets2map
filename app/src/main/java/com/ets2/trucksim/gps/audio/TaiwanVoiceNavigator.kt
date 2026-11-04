/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 ETS2 TruckSim GPS Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ets2.trucksim.gps.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.ets2.trucksim.gps.model.VoiceMappingEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * ETS2「Google Map 台灣語音模組 2」(etsmod.zip 47 音檔完整對照版) 導航語音控制器
 *
 * 嚴格遵守需求規範：
 * 1. 僅播放 `app/src/main/assets/voice_tw/` 資料夾內實際存在的音檔（已內建全部 47 個 .wav 音檔）。
 * 2. 支援三種檔名自動相容匹配：
 *    - 新檔名（純英文大寫，如 `TURN_LEFT.wav`、`IN_300_METERS_TURN_LEFT_1.wav`）
 *    - 保留原前綴大寫檔名（如 `turn_left_TURN_LEFT.wav`）
 *    - 原檔名（如 `turn_left.wav`、`prepare_exit_left.wav`）
 * 3. 若某導航事件沒有對應音檔或檔案不存在，僅略過或於畫面提示缺少，絕不使用系統 TTS 或其他合成語音取代。
 * 4. 播放時申請短暫音訊焦點 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，播畢立即釋放焦點。
 */
class TaiwanVoiceNavigator(private val context: Context) {

    companion object {
        const val VOICE_ASSETS_FOLDER = "voice_tw"
        const val MAPPING_FILE_NAME = "voice_mapping.json"
        private const val CUSTOM_MAPPING_FILE = "custom_voice_mapping.json"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    private val _mappingEntries = MutableStateFlow<List<VoiceMappingEntry>>(emptyList())
    val mappingEntries: StateFlow<List<VoiceMappingEntry>> = _mappingEntries.asStateFlow()

    private val _missingVoiceNotice = MutableStateFlow<String?>(null)
    val missingVoiceNotice: StateFlow<String?> = _missingVoiceNotice.asStateFlow()

    private var lastSpokenKeyAndBucket: String = ""

    init {
        reloadVoiceDirectoryAndMapping()
    }

    /**
     * 掃描 `assets/voice_tw/` 內的實體音檔，並載入 47 筆完整對照表 JSON
     */
    fun reloadVoiceDirectoryAndMapping() {
        ioScope.launch {
            val existingAssetFiles = runCatching {
                context.assets.list(VOICE_ASSETS_FOLDER)?.toSet() ?: emptySet()
            }.getOrDefault(emptySet())

            val customFile = File(context.filesDir, CUSTOM_MAPPING_FILE)
            val jsonString = if (customFile.exists()) {
                runCatching { customFile.readText() }.getOrNull()
            } else {
                runCatching {
                    context.assets.open("$VOICE_ASSETS_FOLDER/$MAPPING_FILE_NAME").use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    }
                }.getOrNull()
            }

            val parsedList = if (!jsonString.isNullOrBlank()) {
                parseMappingJson(jsonString, existingAssetFiles)
            } else {
                defaultTaiwanVoiceEntries(existingAssetFiles)
            }

            _mappingEntries.value = parsedList
        }
    }

    /**
     * 儲存使用者在 App 內微調後的對照表 JSON
     */
    fun saveEditedMapping(updatedEntries: List<VoiceMappingEntry>) {
        ioScope.launch {
            _mappingEntries.value = updatedEntries
            runCatching {
                val root = JSONObject()
                root.put("packName", "Google Map 台灣語音模組 2 (ETS2 Google_map_TW[0])")
                root.put("version", "2.1.0")
                val arr = JSONArray()
                updatedEntries.forEach { item ->
                    val obj = JSONObject().apply {
                        put("fileName", item.fileName)
                        put("prefixedFileName", item.prefixedFileName)
                        put("originalFileName", item.originalFileName)
                        put("eventKey", item.eventKey)
                        put("descriptionZh", item.descriptionZh)
                        put("triggerDistanceMeters", item.triggerDistanceMeters)
                        put("enabled", item.enabled)
                    }
                    arr.put(obj)
                }
                root.put("entries", arr)
                File(context.filesDir, CUSTOM_MAPPING_FILE).writeText(root.toString(2))
            }
        }
    }

    /**
     * 觸發指定導航語音事件（同時支援傳入 eventKey、新大寫檔名或原檔名）
     */
    fun triggerVoiceEvent(
        eventKey: String,
        distanceBucketMeters: Int = 0,
        volume: Float = 0.85f,
        muted: Boolean = false
    ) {
        if (muted || volume <= 0.01f) return

        val dedupToken = "$eventKey@$distanceBucketMeters"
        if (dedupToken == lastSpokenKeyAndBucket && eventKey != "SPEED_WARNING_BEEP_1" && eventKey != "SPEED_WARNING_BEEP_2") {
            return
        }

        val entry = _mappingEntries.value.firstOrNull {
            it.enabled && (
                it.eventKey.equals(eventKey, ignoreCase = true) ||
                it.fileName.removeSuffix(".wav").equals(eventKey, ignoreCase = true) ||
                it.originalFileName.removeSuffix(".wav").equals(eventKey, ignoreCase = true)
            )
        }
        if (entry == null) {
            _missingVoiceNotice.value = "語音事件 [$eventKey] 未設定對應音檔（已依規範略過，不使用合成語音）"
            return
        }

        val resolvedAssetFileName = resolveActualAssetFileName(entry)
        if (resolvedAssetFileName == null) {
            _missingVoiceNotice.value =
                "缺少音檔：請將 ${entry.fileName}（或 ${entry.originalFileName}）放入 assets/voice_tw/"
            return
        }

        lastSpokenKeyAndBucket = dedupToken
        _missingVoiceNotice.value = null
        playAssetAudioWithTransientDucking(resolvedAssetFileName, volume.coerceIn(0f, 1f))
    }

    /**
     * 依序檢查「純英文大寫新檔名」、「保留前綴大寫檔名」、「ETS2 原檔名」是否存在於 assets/voice_tw/
     */
    private fun resolveActualAssetFileName(entry: VoiceMappingEntry): String? {
        val assetFiles = runCatching {
            context.assets.list(VOICE_ASSETS_FOLDER)?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())

        val candidates = listOf(entry.fileName, entry.prefixedFileName, entry.originalFileName)
            .filter { it.isNotBlank() }

        return candidates.firstOrNull { assetFiles.contains(it) }
    }

    private fun playAssetAudioWithTransientDucking(fileName: String, volume: Float) {
        ioScope.launch(Dispatchers.Main) {
            runCatching {
                releaseMediaPlayer()
                requestShortAudioFocus()

                val afd = context.assets.openFd("$VOICE_ASSETS_FOLDER/$fileName")
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                player.setVolume(volume, volume)
                player.setOnCompletionListener {
                    releaseMediaPlayer()
                    abandonShortAudioFocus()
                }
                player.setOnErrorListener { _, _, _ ->
                    releaseMediaPlayer()
                    abandonShortAudioFocus()
                    true
                }
                player.prepare()
                player.start()
                mediaPlayer = player
            }.onFailure {
                _missingVoiceNotice.value = "無法讀取音檔 assets/$VOICE_ASSETS_FOLDER/$fileName（已略過播放）"
                abandonShortAudioFocus()
            }
        }
    }

    private fun requestShortAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setWillPauseWhenDucked(false)
                .build()
            activeFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonShortAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            activeFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    fun release() {
        releaseMediaPlayer()
        abandonShortAudioFocus()
    }

    private fun parseMappingJson(
        jsonString: String,
        existingAssetFiles: Set<String>
    ): List<VoiceMappingEntry> {
        return runCatching {
            val root = JSONObject(jsonString)
            val entriesArray = root.getJSONArray("entries")
            val result = ArrayList<VoiceMappingEntry>(entriesArray.length())
            for (i in 0 until entriesArray.length()) {
                val item = entriesArray.getJSONObject(i)
                val fileName = item.getString("fileName")
                val prefixedFileName = item.optString("prefixedFileName", "")
                val originalFileName = item.optString("originalFileName", "")
                val exists = existingAssetFiles.contains(fileName) ||
                    (prefixedFileName.isNotBlank() && existingAssetFiles.contains(prefixedFileName)) ||
                    (originalFileName.isNotBlank() && existingAssetFiles.contains(originalFileName))

                result.add(
                    VoiceMappingEntry(
                        fileName = fileName,
                        prefixedFileName = prefixedFileName,
                        originalFileName = originalFileName,
                        eventKey = item.getString("eventKey"),
                        descriptionZh = item.getString("descriptionZh"),
                        triggerDistanceMeters = item.optInt("triggerDistanceMeters", 0),
                        enabled = item.optBoolean("enabled", true),
                        fileExistsInAssets = exists
                    )
                )
            }
            result
        }.getOrElse {
            defaultTaiwanVoiceEntries(existingAssetFiles)
        }
    }

    private fun defaultTaiwanVoiceEntries(existingAssetFiles: Set<String>): List<VoiceMappingEntry> {
        // 完整對齊 Google_map_TW[0]_完整對照表.txt 的 47 筆音檔
        val defaults = listOf(
            VoiceMappingEntry("TURN_LEFT.wav", "turn_left_TURN_LEFT.wav", "turn_left.wav", "TURN_LEFT", "向左轉", 30),
            VoiceMappingEntry("KEEP_LEFT_AND_TAKE_EXIT_1.wav", "compound_exit_left_KEEP_LEFT_AND_TAKE_EXIT.wav", "compound_exit_left.wav", "KEEP_LEFT_AND_TAKE_EXIT_1", "靠左下交流道", 180),
            VoiceMappingEntry("ENTER_ROUNDABOUT_TAKE_1ST_EXIT.wav", "roundabout_1_ENTER_ROUNDABOUT_TAKE_1ST_EXIT.wav", "roundabout_1.wav", "ENTER_ROUNDABOUT_TAKE_1ST_EXIT", "進入圓環，在圓環處，於第一個出口下交流道", 120),
            VoiceMappingEntry("KEEP_LEFT_AND_TAKE_EXIT_2.wav", "and_then_exit_left_KEEP_LEFT_AND_TAKE_EXIT.wav", "and_then_exit_left.wav", "KEEP_LEFT_AND_TAKE_EXIT_2", "靠左下交流道", 100),
            VoiceMappingEntry("KEEP_RIGHT_AND_TAKE_EXIT_1.wav", "exit_right_KEEP_RIGHT_AND_TAKE_EXIT.wav", "exit_right.wav", "KEEP_RIGHT_AND_TAKE_EXIT_1", "靠右行下交流道", 150),
            VoiceMappingEntry("KEEP_SLIGHT_RIGHT_1.wav", "keep_right_KEEP_SLIGHT_RIGHT.wav", "keep_right.wav", "KEEP_SLIGHT_RIGHT_1", "微靠右行", 150),
            VoiceMappingEntry("HEAD_SOUTHEAST.wav", "start_6_SOUTHEAST.wav", "start_6.wav", "HEAD_SOUTHEAST", "往東南", 0),
            VoiceMappingEntry("HEAD_EAST.wav", "start_1_EAST.wav", "start_1.wav", "HEAD_EAST", "往東", 0),
            VoiceMappingEntry("ENTER_ROUNDABOUT_TAKE_2ND_EXIT.wav", "roundabout_2_ENTER_ROUNDABOUT_TAKE_2ND_EXIT.wav", "roundabout_2.wav", "ENTER_ROUNDABOUT_TAKE_2ND_EXIT", "進入圓環，在圓環處，於第二個出口下交流道", 120),
            VoiceMappingEntry("IN_100_METERS_TURN_LEFT.wav", "and_then_turn_left_IN_100_METERS_TURN_LEFT.wav", "and_then_turn_left.wav", "IN_100_METERS_TURN_LEFT", "在 100 公尺處，向左轉", 100),
            VoiceMappingEntry("KEEP_SLIGHT_LEFT_1.wav", "and_then_keep_left_KEEP_SLIGHT_LEFT.wav", "and_then_keep_left.wav", "KEEP_SLIGHT_LEFT_1", "微靠左行", 100),
            VoiceMappingEntry("KEEP_SLIGHT_LEFT_2.wav", "compound_keep_left_KEEP_SLIGHT_LEFT.wav", "compound_keep_left.wav", "KEEP_SLIGHT_LEFT_2", "微靠左行", 150),
            VoiceMappingEntry("YOU_HAVE_ARRIVED_AT_YOUR_DESTINATION.wav", "finish_1_YOU_HAVE_ARRIVED_AT_YOUR_DESTINATION.wav", "finish_1.wav", "YOU_HAVE_ARRIVED_AT_YOUR_DESTINATION", "您已到達目的地", 15),
            VoiceMappingEntry("SPEED_WARNING_BEEP_1.wav", "speed_warning_SPEED_WARNING_BEEP.wav", "speed_warning.wav", "SPEED_WARNING_BEEP_1", "超速警示音（嘟嘟兩聲）", 0),
            VoiceMappingEntry("ENTER_ROUNDABOUT_TAKE_3RD_EXIT.wav", "roundabout_3_ENTER_ROUNDABOUT_TAKE_3RD_EXIT.wav", "roundabout_3.wav", "ENTER_ROUNDABOUT_TAKE_3RD_EXIT", "進入圓環，在圓環處，於第三個出口下交流道", 120),
            VoiceMappingEntry("KEEP_RIGHT_AND_TAKE_EXIT_2.wav", "and_then_exit_right_KEEP_RIGHT_AND_TAKE_EXIT.wav", "and_then_exit_right.wav", "KEEP_RIGHT_AND_TAKE_EXIT_2", "靠右行下交流道", 100),
            VoiceMappingEntry("CONTINUE_STRAIGHT_1.wav", "go_straight_1_CONTINUE_STRAIGHT.wav", "go_straight_1.wav", "CONTINUE_STRAIGHT_1", "繼續直行", 400),
            VoiceMappingEntry("ENTER_ROUNDABOUT_TAKE_5TH_EXIT.wav", "roundabout_5_ENTER_ROUNDABOUT_TAKE_5TH_EXIT.wav", "roundabout_5.wav", "ENTER_ROUNDABOUT_TAKE_5TH_EXIT", "進入圓環，在圓環處，於第五個出口下交流道", 120),
            VoiceMappingEntry("KEEP_SLIGHT_RIGHT_2.wav", "and_then_keep_right_KEEP_SLIGHT_RIGHT.wav", "and_then_keep_right.wav", "KEEP_SLIGHT_RIGHT_2", "微靠右行", 100),
            VoiceMappingEntry("KEEP_SLIGHT_LEFT_3.wav", "keep_left_KEEP_SLIGHT_LEFT.wav", "keep_left.wav", "KEEP_SLIGHT_LEFT_3", "微靠左行", 150),
            VoiceMappingEntry("HEAD_SOUTHWEST.wav", "start_7_SOUTHWEST.wav", "start_7.wav", "HEAD_SOUTHWEST", "往西南", 0),
            VoiceMappingEntry("CONTINUE_STRAIGHT_2.wav", "compound_go_straight_2_CONTINUE_STRAIGHT.wav", "compound_go_straight_2.wav", "CONTINUE_STRAIGHT_2", "繼續直行", 250),
            VoiceMappingEntry("PLEASE_CONTINUE_STRAIGHT_1.wav", "go_straight_2_PLEASE_CONTINUE_STRAIGHT.wav", "go_straight_2.wav", "PLEASE_CONTINUE_STRAIGHT_1", "請繼續直行", 600),
            VoiceMappingEntry("SLIGHT_LEFT.wav", "compound_turn_left_SLIGHT_LEFT.wav", "compound_turn_left.wav", "SLIGHT_LEFT", "微靠左", 120),
            VoiceMappingEntry("KEEP_SLIGHT_RIGHT_3.wav", "compound_turn_right_KEEP_SLIGHT_RIGHT.wav", "compound_turn_right.wav", "KEEP_SLIGHT_RIGHT_3", "微靠右行", 120),
            VoiceMappingEntry("ENTER_ROUNDABOUT_TAKE_6TH_EXIT.wav", "roundabout_6_ENTER_ROUNDABOUT_TAKE_6TH_EXIT.wav", "roundabout_6.wav", "ENTER_ROUNDABOUT_TAKE_6TH_EXIT", "進入圓環，在圓環處，於第六個出口下交流道", 120),
            VoiceMappingEntry("MAKE_A_U_TURN.wav", "u_turn_1_MAKE_A_U_TURN.wav", "u_turn_1.wav", "MAKE_A_U_TURN", "迴轉", 50),
            VoiceMappingEntry("HEAD_WEST.wav", "start_8_WEST.wav", "start_8.wav", "HEAD_WEST", "往西", 0),
            VoiceMappingEntry("IN_300_METERS_TURN_RIGHT_1.wav", "prepare_exit_right_IN_300_METERS_TURN_RIGHT.wav", "prepare_exit_right.wav", "IN_300_METERS_TURN_RIGHT_1", "在 300 公尺處，向右轉", 300),
            VoiceMappingEntry("HEAD_NORTH.wav", "start_2_NORTH.wav", "start_2.wav", "HEAD_NORTH", "往北", 0),
            VoiceMappingEntry("IN_300_METERS_TURN_RIGHT_2.wav", "prepare_turn_right_IN_300_METERS_TURN_RIGHT.wav", "prepare_turn_right.wav", "IN_300_METERS_TURN_RIGHT_2", "在 300 公尺處，向右轉", 300),
            VoiceMappingEntry("KEEP_RIGHT_AND_TAKE_EXIT_3.wav", "compound_exit_right_KEEP_RIGHT_AND_TAKE_EXIT.wav", "compound_exit_right.wav", "KEEP_RIGHT_AND_TAKE_EXIT_3", "靠右行下交流道", 180),
            VoiceMappingEntry("HEAD_NORTHWEST.wav", "start_4_NORTHWEST.wav", "start_4.wav", "HEAD_NORTHWEST", "往西北", 0),
            VoiceMappingEntry("EXIT_ROUNDABOUT.wav", "exit_now_EXIT_ROUNDABOUT.wav", "exit_now.wav", "EXIT_ROUNDABOUT", "駛出圓環", 25),
            VoiceMappingEntry("ENTER_ROUNDABOUT_TAKE_4TH_EXIT.wav", "roundabout_4_ENTER_ROUNDABOUT_TAKE_4TH_EXIT.wav", "roundabout_4.wav", "ENTER_ROUNDABOUT_TAKE_4TH_EXIT", "進入圓環，在圓環處，於第四個出口下交流道", 120),
            VoiceMappingEntry("KEEP_LEFT_AND_TAKE_EXIT_3.wav", "exit_left_KEEP_LEFT_AND_TAKE_EXIT.wav", "exit_left.wav", "KEEP_LEFT_AND_TAKE_EXIT_3", "靠左下交流道", 150),
            VoiceMappingEntry("AND_THEN_CONTINUE_STRAIGHT.wav", "and_then_go_straight_AND_THEN_CONTINUE_STRAIGHT.wav", "and_then_go_straight.wav", "AND_THEN_CONTINUE_STRAIGHT", "然後，繼續直行", 100),
            VoiceMappingEntry("IN_300_METERS_TURN_LEFT_1.wav", "prepare_exit_left_IN_300_METERS_TURN_LEFT.wav", "prepare_exit_left.wav", "IN_300_METERS_TURN_LEFT_1", "在 300 公尺處，向左轉", 300),
            VoiceMappingEntry("IN_300_METERS_TURN_LEFT_2.wav", "prepare_turn_left_IN_300_METERS_TURN_LEFT.wav", "prepare_turn_left.wav", "IN_300_METERS_TURN_LEFT_2", "在 300 公尺處，向左轉", 300),
            VoiceMappingEntry("TURN_RIGHT.wav", "turn_right_TURN_RIGHT.wav", "turn_right.wav", "TURN_RIGHT", "向右轉", 30),
            VoiceMappingEntry("HEAD_SOUTH.wav", "start_5_SOUTH.wav", "start_5.wav", "HEAD_SOUTH", "往南", 0),
            VoiceMappingEntry("SPEED_WARNING_BEEP_2.wav", "speed_signal_SPEED_WARNING_BEEP.wav", "speed_signal.wav", "SPEED_WARNING_BEEP_2", "超速提示音（嘟嘟兩聲）", 0),
            VoiceMappingEntry("PLEASE_CONTINUE_STRAIGHT_2.wav", "compound_go_straight_1_PLEASE_CONTINUE_STRAIGHT.wav", "compound_go_straight_1.wav", "PLEASE_CONTINUE_STRAIGHT_2", "請繼續直行", 500),
            VoiceMappingEntry("KEEP_SLIGHT_RIGHT_4.wav", "compound_keep_right_KEEP_SLIGHT_RIGHT.wav", "compound_keep_right.wav", "KEEP_SLIGHT_RIGHT_4", "微靠右行", 150),
            VoiceMappingEntry("HEAD_NORTHEAST.wav", "start_3_NORTHEAST.wav", "start_3.wav", "HEAD_NORTHEAST", "往東北", 0),
            VoiceMappingEntry("RECOMPUTING_CHIME.wav", "recomputing_1_RECOMPUTING_CHIME.wav", "recomputing_1.wav", "RECOMPUTING_CHIME", "重新計算路線提示音（叮咚）", 0),
            VoiceMappingEntry("IN_100_METERS_TURN_RIGHT.wav", "and_then_turn_right_IN_100_METERS_TURN_RIGHT.wav", "and_then_turn_right.wav", "IN_100_METERS_TURN_RIGHT", "在 100 公尺處，向右轉", 100)
        )
        return defaults.map {
            val exists = existingAssetFiles.contains(it.fileName) ||
                existingAssetFiles.contains(it.prefixedFileName) ||
                existingAssetFiles.contains(it.originalFileName)
            it.copy(fileExistsInAssets = exists)
        }
    }
}
