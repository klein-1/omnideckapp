package com.example.registry

import androidx.compose.runtime.Immutable
import com.example.design.ChapterSpec
import com.example.design.ToolCategory
import com.example.design.chapterById

enum class CapabilityRequirement(val id: String, val label: String, val rationale: String) {
    CAMERA("camera", "Camera", "Used for torch, mirror, scanner, or framing."),
    MIC("mic", "Mic", "Used for audio recording or sound meter."),
    MOTION("motion", "Sensors", "Reads device accelerometer/gyro/magnetometer hardware."),
    GEO("geo", "Location", "Uses device GPS only while tool is active."),
    BLUETOOTH("bluetooth", "Bluetooth", "Scans user-requested BLE devices."),
    COMPANION("companion", "Companion", "Optional Companion Server for yt-dlp/spotDL."),
    AI("ai", "AI", "Optional AI request."),
    NETWORK("network", "Network", "Performs outbound network requests when invoked."),
    STORAGE("fs-access", "Storage", "Saves outputs locally in Omni Deck."),
    CLIPBOARD("clipboard", "Clipboard", "Reads clipboard only when you tap Paste.")
}

enum class DataKind(val mime: String, val label: String) {
    TEXT("text/plain", "Text"),
    CSV("text/csv", "CSV"),
    JSON("application/json", "JSON"),
    HASH("text/x-hash", "Hash"),
    URL("text/uri-list", "URL"),
    IMAGE("image/*", "Image"),
    PDF("application/pdf", "PDF"),
    AUDIO("audio/*", "Audio"),
    VIDEO("video/*", "Video"),
    FILE("application/octet-stream", "File")
}

@Immutable
data class ToolIO(
    val accepts: Set<DataKind> = emptySet(),
    val produces: Set<DataKind> = emptySet()
)

enum class ToolStatus(val label: String) {
    LIVE("LIVE"),
    PLANNED("PLANNED")
}

@Immutable
data class ToolMeta(
    val id: String,
    val number: Int,
    val chapterId: Int,
    val category: ToolCategory,
    val secondaryCategories: Set<ToolCategory> = emptySet(),
    val name: String,
    val tagline: String,
    val keywords: List<String>,
    val aliases: List<String> = emptyList(),
    val requires: Set<CapabilityRequirement> = emptySet(),
    val offline: Boolean = true,
    val io: ToolIO = ToolIO(),
    val status: ToolStatus = ToolStatus.LIVE,
    val glyphKey: String = "",
    val phaseNumber: Int = 1
) {
    val specimenCode: String get() = "#%03d".format(number)
    val chapter: ChapterSpec get() = chapterById(chapterId)
    val monogram: String
        get() = name.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "OD" }
}
