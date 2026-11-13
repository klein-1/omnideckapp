package com.example.tools.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.design.SwissControlShape
import com.example.lib.text.CipherKind
import com.example.lib.text.TextExtraEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextExtraToolsScreen(
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

    // Cipher state
    var cipherKind by remember { mutableStateOf(CipherKind.CAESAR) }
    var cipherDecrypt by remember { mutableStateOf(false) }
    var cipherShift by remember { mutableIntStateOf(3) }
    var cipherKey by remember { mutableStateOf("OMNI") }
    var cipherInput by remember(initialText) { mutableStateOf(initialText) }
    var showBruteForce by remember { mutableStateOf(false) }

    // Stego state
    var stegoHideMode by remember { mutableStateOf(true) }
    var stegoCarrier by remember(initialText) { mutableStateOf(initialText) }
    var stegoSecret by remember { mutableStateOf("") }

    // Slug / HTML / Unicode / Punycode state
    var slugInput by remember(initialText) { mutableStateOf(initialText) }
    var slugMode by remember { mutableStateOf("SLUG") }

    // Line Master state
    var linesInput by remember(initialText) { mutableStateOf(initialText) }
    var linesMode by remember { mutableStateOf("SORT_AZ") }

    // Phonetic / Braille / Leet state
    var phoneInput by remember(initialText) { mutableStateOf(initialText) }
    var phoneMode by remember { mutableStateOf("NATO") }

    // CSV <-> JSON <-> MD Table state
    var dataInput by remember(initialText) { mutableStateOf(initialText) }
    var dataDir by remember { mutableStateOf("CSV_JSON") }

    val outputText = when (meta.id) {
        "txt-cipher" -> when (cipherKind) {
            CipherKind.CAESAR -> TextExtraEngine.caesar(cipherInput, cipherShift, cipherDecrypt)
            CipherKind.VIGENERE -> TextExtraEngine.vigenere(cipherInput, cipherKey, cipherDecrypt)
            CipherKind.ATBASH -> TextExtraEngine.atbash(cipherInput)
            CipherKind.RAIL_FENCE -> TextExtraEngine.railFence(cipherInput, cipherShift.coerceIn(2, 10), cipherDecrypt)
        }
        "txt-stego" -> if (stegoHideMode) TextExtraEngine.embedZeroWidth(stegoCarrier, stegoSecret)
        else TextExtraEngine.extractZeroWidth(stegoCarrier)
        "txt-slug" -> when (slugMode) {
            "SLUG" -> TextExtraEngine.toSlug(slugInput)
            "HTML_ENC" -> TextExtraEngine.encodeHtmlEntities(slugInput)
            "HTML_DEC" -> TextExtraEngine.decodeHtmlEntities(slugInput)
            "UNI_ESC" -> TextExtraEngine.escapeUnicode(slugInput)
            "UNI_UNESC" -> TextExtraEngine.unescapeUnicode(slugInput)
            "PUNY_ENC" -> TextExtraEngine.toPunycodeDomain(slugInput)
            else -> TextExtraEngine.fromPunycodeDomain(slugInput)
        }
        "txt-lines" -> TextExtraEngine.processLines(linesInput, linesMode)
        "txt-phonetic" -> when (phoneMode) {
            "NATO" -> TextExtraEngine.toNato(phoneInput)
            "BRAILLE" -> TextExtraEngine.toBraille(phoneInput)
            else -> TextExtraEngine.toLeetspeak(phoneInput)
        }
        "txt-csvjson" -> when (dataDir) {
            "CSV_JSON" -> TextExtraEngine.csvToJson(dataInput).getOrElse { "Error: ${it.message}" }
            "JSON_CSV" -> TextExtraEngine.jsonToCsv(dataInput).getOrElse { "Error: ${it.message}" }
            else -> TextExtraEngine.csvToMarkdownTable(dataInput).getOrElse { "Error: ${it.message}" }
        }
        else -> ""
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "${meta.id}_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "txt-cipher" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CipherKind.entries.forEach { k ->
                                PixelChip(k.label, selected = cipherKind == k, chapterHue = hue, onClick = { cipherKind = k })
                            }
                        }
                        if (cipherKind != CipherKind.ATBASH) {
                            PixelSwitch("Decrypt / Reverse Mode", cipherDecrypt, { cipherDecrypt = it }, hue)
                        }
                        if (cipherKind == CipherKind.CAESAR) {
                            PixelSteppedSlider("Caesar Shift", cipherShift, 1..25, 1, "", hue, { cipherShift = it })
                            PixelSwitch("Show All 25 Brute-Force Shifts", showBruteForce, { showBruteForce = it }, hue)
                        } else if (cipherKind == CipherKind.RAIL_FENCE) {
                            PixelSteppedSlider("Rail Count", cipherShift.coerceIn(2, 10), 2..10, 1, " rails", hue, { cipherShift = it })
                        } else if (cipherKind == CipherKind.VIGENERE) {
                            PixelTextField(cipherKey, { cipherKey = it }, "Cipher Keyword (A–Z)", chapterHue = hue)
                        }
                        PixelTextField(cipherInput, { cipherInput = it }, "Enter plaintext or ciphertext…", singleLine = false, minHeight = 96.dp, chapterHue = hue)
                        OutputPlate(outputText)
                        if (cipherKind == CipherKind.CAESAR && showBruteForce && cipherInput.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("BRUTE-FORCE CANDIDATES", style = DotkitType.monoLabel(10), color = palette.ink2)
                                TextExtraEngine.caesarBruteForce(cipherInput).forEach { (s, dec) ->
                                    Text("ROT-%02d: %s".format(s, dec.take(60)), style = DotkitType.monoData(11), color = palette.ink)
                                }
                            }
                        }
                    }
                }
            }

            "txt-stego" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelChip("Hide Secret", selected = stegoHideMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { stegoHideMode = true })
                            PixelChip("Extract Hidden", selected = !stegoHideMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { stegoHideMode = false })
                        }
                        PixelTextField(
                            value = stegoCarrier,
                            onValueChange = { stegoCarrier = it },
                            placeholder = if (stegoHideMode) "Carrier text (visible sentence)…" else "Paste text containing hidden Unicode…",
                            singleLine = false,
                            minHeight = 90.dp,
                            chapterHue = hue
                        )
                        if (stegoHideMode) {
                            PixelTextField(stegoSecret, { stegoSecret = it }, "Secret hidden payload…", chapterHue = hue)
                            PixelStamp("${stegoSecret.toByteArray().size * 8} ZERO-WIDTH BITS", palette.chapterColor(hue))
                        }
                        OutputPlate(
                            if (!stegoHideMode && outputText.isEmpty()) "No hidden zero-width payload found."
                            else if (stegoHideMode) "Carrier + invisible payload (${outputText.length} chars) — tap Copy below."
                            else "Decoded Secret: $outputText"
                        )
                    }
                }
            }

            "txt-slug" -> {
                val modes = listOf(
                    "SLUG" to "URL Slug",
                    "HTML_ENC" to "HTML Encode",
                    "HTML_DEC" to "HTML Decode",
                    "UNI_ESC" to "\\uXXXX Escape",
                    "UNI_UNESC" to "Unescape",
                    "PUNY_ENC" to "IDN Punycode",
                    "PUNY_DEC" to "IDN Unicode"
                )
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            modes.forEach { (id, lbl) ->
                                PixelChip(lbl, selected = slugMode == id, chapterHue = hue, onClick = { slugMode = id })
                            }
                        }
                        PixelTextField(slugInput, { slugInput = it }, "Enter title, HTML, Unicode or domain…", singleLine = false, minHeight = 90.dp, chapterHue = hue)
                        OutputPlate(outputText)
                    }
                }
            }

            "txt-lines" -> {
                val ops = listOf(
                    "SORT_AZ" to "Sort A–Z", "SORT_ZA" to "Sort Z–A", "SORT_LEN" to "By Length",
                    "DEDUPE" to "Dedupe", "TRIM" to "Trim Empty", "SHUFFLE" to "Shuffle",
                    "NUMBER" to "Number Lines", "REVERSE" to "Reverse",
                    "EMAILS" to "Extract Emails", "URLS" to "Extract URLs", "IPS" to "Extract IPs"
                )
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ops.forEach { (id, lbl) ->
                                PixelChip(lbl, selected = linesMode == id, chapterHue = hue, onClick = { linesMode = id })
                            }
                        }
                        PixelTextField(linesInput, { linesInput = it }, "Paste multi-line list or log…", singleLine = false, minHeight = 110.dp, chapterHue = hue)
                        Text("${linesInput.lines().size} input lines → ${outputText.lines().count { it.isNotEmpty() }} output lines", style = DotkitType.monoLabel(10), color = palette.ink2)
                        OutputPlate(outputText)
                    }
                }
            }

            "txt-phonetic" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("NATO" to "NATO Phonetic", "BRAILLE" to "Braille Dots", "LEET" to "1337Speak").forEach { (id, lbl) ->
                                PixelChip(lbl, selected = phoneMode == id, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { phoneMode = id })
                            }
                        }
                        PixelTextField(phoneInput, { phoneInput = it }, "Type callsign, code, or message…", singleLine = false, minHeight = 84.dp, chapterHue = hue)
                        OutputPlate(outputText)
                    }
                }
            }

            else -> { // txt-csvjson
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("CSV_JSON" to "CSV → JSON", "JSON_CSV" to "JSON → CSV", "CSV_MD" to "CSV → Markdown Table").forEach { (id, lbl) ->
                                PixelChip(lbl, selected = dataDir == id, chapterHue = hue, onClick = { dataDir = id })
                            }
                        }
                        PixelTextField(dataInput, { dataInput = it }, "Paste CSV rows or JSON array…", singleLine = false, minHeight = 120.dp, chapterHue = hue)
                        PixelButton("Load Sample Row", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                            dataInput = if (dataDir == "JSON_CSV") "[{\"name\":\"Ada\",\"role\":\"Lead\"},{\"name\":\"Alan\",\"role\":\"Crypto\"}]"
                            else "name,role,score\nAda,Lead,98\nAlan,Crypto,99"
                        })
                        OutputPlate(outputText)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun OutputPlate(text: String) {
    val palette = LocalDotkitLook.current.palette
    Text(
        text = text.ifBlank { "Output appears here…" },
        style = DotkitType.monoData(12),
        color = if (text.isBlank()) palette.ink3 else palette.ink,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SwissControlShape)
            .background(palette.paper)
            .border(1.dp, palette.hairline, SwissControlShape)
            .padding(12.dp)
    )
}
