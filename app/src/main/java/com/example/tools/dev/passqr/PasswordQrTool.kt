package com.example.tools.dev.passqr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.dev.PasswordQrEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import java.util.UUID

private enum class PassQrTab(val label: String) {
    PASSWORD("Password"),
    UUID_GEN("UUID v4"),
    QR_CODE("QR Studio")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasswordQrTool(
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
    val hue = ChapterHue.CLAY

    var tab by remember {
        mutableStateOf(if (initialText.startsWith("http")) PassQrTab.QR_CODE else PassQrTab.PASSWORD)
    }

    // Password state
    var usePassphrase by remember { mutableStateOf(false) }
    var length by remember { mutableIntStateOf(16) }
    var wordCount by remember { mutableIntStateOf(5) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var regenTick by remember { mutableIntStateOf(0) }

    val pwResult = remember(usePassphrase, length, wordCount, useUpper, useLower, useDigits, useSymbols, regenTick) {
        if (usePassphrase) PasswordQrEngine.generatePassphrase(wordCount)
        else PasswordQrEngine.generatePassword(length, useUpper, useLower, useDigits, useSymbols)
    }

    // UUID state
    var uuidCount by remember { mutableIntStateOf(5) }
    var uuidTick by remember { mutableIntStateOf(0) }
    val uuidList = remember(uuidCount, uuidTick) {
        (1..uuidCount).map { UUID.randomUUID().toString() }
    }

    // QR state
    var qrPayload by remember(initialText) {
        mutableStateOf(initialText.ifEmpty { "https://dotkit.local/specimen" })
    }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    val qrMatrix = remember(qrPayload) {
        if (qrPayload.isBlank()) null else PasswordQrEngine.encodeQrMatrix(qrPayload)
    }

    val exportText = when (tab) {
        PassQrTab.PASSWORD -> pwResult.password
        PassQrTab.UUID_GEN -> uuidList.joinToString("\n")
        PassQrTab.QR_CODE -> qrPayload
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportText,
        outputFileName = "secret_qr_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Generate high-entropy passwords or EFF-style passphrases using SecureRandom, create batches of RFC 4122 UUIDs, or render crisp offline QR codes for text, URLs, and Wi-Fi credentials."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PassQrTab.entries.forEach { t ->
                PixelChip(label = t.label, selected = tab == t, chapterHue = hue, onClick = { tab = t })
            }
        }

        when (tab) {
            PassQrTab.PASSWORD -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelChip(label = "Random Chars", selected = !usePassphrase, chapterHue = hue, onClick = { usePassphrase = false })
                            PixelChip(label = "Word Passphrase", selected = usePassphrase, chapterHue = hue, onClick = { usePassphrase = true })
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper)
                                .border(1.5.dp, palette.ink)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(pwResult.password, style = DotkitType.monoData(16), color = palette.ink)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${pwResult.entropyBits.toInt()} bits entropy", style = DotkitType.monoLabel(10), color = palette.ink2)
                                PixelStamp(pwResult.strengthLabel, if (pwResult.entropyBits >= 65) palette.ok else palette.warn)
                            }
                            PixelProgressBar(progress = (pwResult.entropyBits / 110.0).toFloat().coerceIn(0.1f, 1f), chapterHue = hue)
                        }

                        PixelButton(text = "Generate New", chapterHue = hue, onClick = { regenTick++ })

                        if (usePassphrase) {
                            PixelSteppedSlider("Word Count", wordCount, 3..10, 1, " words", hue, { wordCount = it })
                        } else {
                            PixelSteppedSlider("Length", length, 8..48, 2, " chars", hue, { length = it })
                            PixelSwitch("Uppercase (A–Z)", useUpper, { useUpper = it }, hue)
                            PixelSwitch("Lowercase (a–z)", useLower, { useLower = it }, hue)
                            PixelSwitch("Digits (2–9)", useDigits, { useDigits = it }, hue)
                            PixelSwitch("Symbols (!@#$...)", useSymbols, { useSymbols = it }, hue)
                        }
                    }
                }
            }

            PassQrTab.UUID_GEN -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelSteppedSlider("Batch Size", uuidCount, 1..20, 1, " IDs", hue, { uuidCount = it })
                        PixelButton(text = "Regenerate Batch", chapterHue = hue, onClick = { uuidTick++ })
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper)
                                .border(1.5.dp, palette.ink)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uuidList.forEach { id ->
                                Text(id, style = DotkitType.monoData(12), color = palette.ink)
                            }
                        }
                    }
                }
            }

            PassQrTab.QR_CODE -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("QR CONTENT (UP TO ~104 BYTES)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = qrPayload,
                            onValueChange = { qrPayload = it },
                            placeholder = "URL, plain text, or WIFI string…",
                            chapterHue = hue
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(
                                value = wifiSsid,
                                onValueChange = { wifiSsid = it },
                                placeholder = "Wi-Fi SSID",
                                chapterHue = hue,
                                modifier = Modifier.weight(1f)
                            )
                            PixelTextField(
                                value = wifiPass,
                                onValueChange = { wifiPass = it },
                                placeholder = "Password",
                                chapterHue = hue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        PixelButton(
                            text = "Build Wi-Fi QR Payload",
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            enabled = wifiSsid.isNotBlank(),
                            onClick = { qrPayload = "WIFI:T:WPA;S:$wifiSsid;P:$wifiPass;;" }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .border(2.dp, palette.ink)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrMatrix != null) {
                                val modSize = qrMatrix.size
                                Canvas(modifier = Modifier.size(216.dp)) {
                                    val cell = size.width / modSize.toFloat()
                                    for (r in 0 until modSize) {
                                        for (c in 0 until modSize) {
                                            if (qrMatrix[r][c]) {
                                                drawRect(
                                                    color = Color(0xFF17150F),
                                                    topLeft = Offset(c * cell, r * cell),
                                                    size = Size(cell + 0.5f, cell + 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Enter 1–104 characters to render QR matrix.",
                                    style = DotkitType.monoData(12),
                                    color = Color(0xFF17150F)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
