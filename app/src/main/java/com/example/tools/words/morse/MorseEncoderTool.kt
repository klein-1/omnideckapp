package com.example.tools.words.morse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelTextField
import com.example.lib.text.TextToolsEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.sound.PixelSoundEngine
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CodecMode(val label: String) {
    MORSE("Morse"),
    BASE64("Base64"),
    BINARY("Binary"),
    URL("URL Encode"),
    ROT13("ROT13")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MorseEncoderTool(
    meta: ToolMeta,
    initialText: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.VERMILION
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(CodecMode.MORSE) }
    var encodeDirection by remember { mutableStateOf(true) }
    var inputText by remember(initialText) { mutableStateOf(initialText.ifEmpty { "DOTKIT SOS" }) }
    var playingMorse by remember { mutableStateOf(false) }

    val outputResult = remember(inputText, mode, encodeDirection) {
        runCatching {
            when (mode) {
                CodecMode.MORSE -> if (encodeDirection) TextToolsEngine.encodeMorse(inputText) else TextToolsEngine.decodeMorse(inputText)
                CodecMode.BASE64 -> if (encodeDirection) TextToolsEngine.toBase64(inputText) else TextToolsEngine.fromBase64(inputText)
                CodecMode.BINARY -> if (encodeDirection) TextToolsEngine.toBinary(inputText) else TextToolsEngine.fromBinary(inputText)
                CodecMode.URL -> if (encodeDirection) TextToolsEngine.urlEncode(inputText) else TextToolsEngine.urlDecode(inputText)
                CodecMode.ROT13 -> TextToolsEngine.rot13(inputText)
            }
        }.getOrElse { "Error: ${it.message ?: "Invalid encoded input"}" }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputResult,
        outputFileName = "codec_${mode.name.lowercase()}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Encode and decode text across Morse code (with playable audio tone sequence), Base64, 8-bit Binary, URL percent-encoding, and ROT13."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CodecMode.entries.forEach { m ->
                PixelChip(label = m.label, selected = mode == m, chapterHue = hue, onClick = { mode = m })
            }
        }

        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode != CodecMode.ROT13) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelChip(
                            label = "Encode →",
                            selected = encodeDirection,
                            chapterHue = hue,
                            onClick = { encodeDirection = true }
                        )
                        PixelChip(
                            label = "← Decode",
                            selected = !encodeDirection,
                            chapterHue = hue,
                            onClick = { encodeDirection = false }
                        )
                        PixelChip(
                            label = "Use Output as Input",
                            chapterHue = hue,
                            onClick = {
                                inputText = outputResult
                                encodeDirection = !encodeDirection
                            }
                        )
                    }
                }

                PixelTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = "Enter text or encoded payload…",
                    singleLine = false,
                    minHeight = 84.dp,
                    chapterHue = hue
                )

                Text("RESULT", style = DotkitType.monoLabel(10), color = palette.ink2)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.paper)
                        .border(1.5.dp, palette.ink)
                        .padding(12.dp)
                ) {
                    Text(outputResult.ifEmpty { "—" }, style = DotkitType.monoData(14), color = palette.ink)
                }

                if (mode == CodecMode.MORSE) {
                    val morseToPlay = if (encodeDirection) outputResult else inputText
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton(
                            text = if (playingMorse) "Playing…" else "Play Morse Audio",
                            chapterHue = hue,
                            enabled = !playingMorse && morseToPlay.isNotBlank(),
                            onClick = {
                                playingMorse = true
                                scope.launch {
                                    for (ch in morseToPlay.take(40)) {
                                        if (!playingMorse) break
                                        when (ch) {
                                            '.' -> {
                                                PixelSoundEngine.playBlip(true, 740, 75)
                                                delay(145L)
                                            }
                                            '-' -> {
                                                PixelSoundEngine.playBlip(true, 740, 210)
                                                delay(280L)
                                            }
                                            '/' -> delay(350L)
                                            ' ' -> delay(160L)
                                        }
                                    }
                                    playingMorse = false
                                }
                            }
                        )
                        if (playingMorse) {
                            PixelButton(
                                text = "Stop",
                                variant = PixelButtonVariant.DESTRUCTIVE,
                                onClick = { playingMorse = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
