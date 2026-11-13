package com.example.tools.dev.hash

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.design.rememberErrorShakeOffset
import com.example.lib.crypto.HashAlgorithm
import com.example.lib.crypto.HashEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard

private enum class HashBenchMode(val label: String) {
    ALL_DIGESTS("14 Digests"),
    HMAC("HMAC"),
    PBKDF2("PBKDF2"),
    VERIFY_ID("Verify & ID")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HashGeneratorTool(
    meta: ToolMeta,
    initialText: String,
    rememberedAlgName: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onRememberAlgorithm: (String) -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.FOREST

    var mode by remember { mutableStateOf(HashBenchMode.ALL_DIGESTS) }
    var inputText by remember(initialText) {
        mutableStateOf(initialText.ifEmpty { "dotkit-specimen-003" })
    }
    var primaryAlg by remember(rememberedAlgName) {
        mutableStateOf(
            runCatching { HashAlgorithm.valueOf(rememberedAlgName) }.getOrDefault(HashAlgorithm.SHA256)
        )
    }
    var uppercaseHex by remember { mutableStateOf(false) }
    var showBase64 by remember { mutableStateOf(false) }

    // HMAC & PBKDF2 state
    var hmacKey by remember { mutableStateOf("dotkit-secret-key") }
    var pbkdf2Salt by remember { mutableStateOf("dotkit-salt-v1") }
    var pbkdf2Iterations by remember { mutableIntStateOf(10000) }
    var pbkdf2Bits by remember { mutableIntStateOf(256) }

    // Verify & Identify state
    var expectedHash by remember { mutableStateOf("") }
    var errorShakeTrigger by remember { mutableIntStateOf(0) }
    val shakeOffset = rememberErrorShakeOffset(errorShakeTrigger)

    val primaryDigestHex = remember(inputText, primaryAlg, uppercaseHex) {
        HashEngine.digestText(inputText, primaryAlg, uppercaseHex)
    }

    val allDigests = remember(inputText, uppercaseHex, showBase64) {
        HashAlgorithm.entries.map { alg ->
            val hex = HashEngine.digestText(inputText, alg, uppercaseHex)
            val formatted = if (showBase64) HashEngine.hexToBase64(hex) else hex
            alg to formatted
        }
    }

    val hmacOutput = remember(inputText, hmacKey, primaryAlg, uppercaseHex) {
        HashEngine.hmacHex(inputText, hmacKey, primaryAlg, uppercaseHex)
    }

    val pbkdf2Output = remember(inputText, pbkdf2Salt, pbkdf2Iterations, pbkdf2Bits, uppercaseHex) {
        HashEngine.derivePbkdf2Hex(inputText, pbkdf2Salt, pbkdf2Iterations, pbkdf2Bits, uppercaseHex)
    }

    val exportReport = remember(mode, inputText, allDigests, hmacOutput, pbkdf2Output) {
        when (mode) {
            HashBenchMode.ALL_DIGESTS -> buildString {
                appendLine("# Dotkit Specimen № 003 — Digest Report")
                appendLine("input: $inputText")
                allDigests.forEach { (alg, value) ->
                    appendLine("${alg.id}: $value")
                }
            }.trim()
            HashBenchMode.HMAC -> hmacOutput
            HashBenchMode.PBKDF2 -> pbkdf2Output
            HashBenchMode.VERIFY_ID -> primaryDigestHex
        }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportReport,
        outputFileName = "hash_${primaryAlg.id}_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.HASH,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Compute deterministic cryptographic digests (SHA-2, SHA-3, MD5, SHA-1), fast non-cryptographic hashes (MurmurHash3, FNV-1a, DJB2), and checksums (CRC-32, Adler-32). Includes HMAC, PBKDF2 key derivation, constant-time digest verification, and automatic hash identification."
    ) {
        // Bench Mode Selector
        PixelBox(cropMarks = true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SELECT CRYPTOGRAPHIC BENCH", style = DotkitType.monoLabel(11), color = palette.ink2)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HashBenchMode.entries.forEach { m ->
                        PixelChip(
                            label = m.label,
                            selected = mode == m,
                            chapterHue = hue,
                            onClick = { mode = m }
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = palette.ink)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("INPUT PAYLOAD (${inputText.encodeToByteArray().size} BYTES UTF-8)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelChip(
                            label = "Sample",
                            chapterHue = hue,
                            onClick = { inputText = "dotkit-specimen-003" }
                        )
                        PixelChip(
                            label = "Clear",
                            chapterHue = hue,
                            onClick = { inputText = "" }
                        )
                    }
                }

                PixelTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = "Enter text payload to hash (empty string computes zero-byte digest)…",
                    singleLine = false,
                    minHeight = 72.dp,
                    chapterHue = hue,
                    tag = "hash_input_field"
                )

                PixelSwitch(
                    label = "Uppercase Hex Output (A–F)",
                    checked = uppercaseHex,
                    onCheckedChange = { uppercaseHex = it },
                    chapterHue = hue
                )
            }
        }

        when (mode) {
            HashBenchMode.ALL_DIGESTS -> {
                PixelBox(cropMarks = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("LIVE 14-ALGORITHM DIGEST TABLE", style = DotkitType.monoLabel(11), color = palette.ink2)
                            PixelChip(
                                label = if (showBase64) "Encoding: Base64" else "Encoding: Hex",
                                selected = showBase64,
                                chapterHue = hue,
                                onClick = { showBase64 = !showBase64 }
                            )
                        }

                        allDigests.forEach { (alg, digestValue) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (alg == primaryAlg) palette.paper else palette.paper3)
                                    .border(if (alg == primaryAlg) 2.dp else 1.dp, palette.ink)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(alg.label, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                        Text("${alg.bits}-BIT", style = DotkitType.monoLabel(9), color = palette.ink2)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PixelChip(
                                            label = if (alg == primaryAlg) "★ Primary" else "Pin",
                                            selected = alg == primaryAlg,
                                            chapterHue = hue,
                                            onClick = {
                                                primaryAlg = alg
                                                onRememberAlgorithm(alg.name)
                                            }
                                        )
                                        PixelChip(
                                            label = "Copy",
                                            chapterHue = hue,
                                            onClick = {
                                                copyToClipboard(context, alg.label, digestValue)
                                                onStatusToast("Copied ${alg.label} digest.")
                                            }
                                        )
                                    }
                                }
                                Text(
                                    text = digestValue,
                                    style = DotkitType.monoData(12),
                                    color = palette.ink
                                )
                            }
                        }
                    }
                }
            }

            HashBenchMode.HMAC -> {
                PixelBox(cropMarks = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("HMAC KEYED-HASH MESSAGE AUTHENTICATION", style = DotkitType.monoLabel(11), color = palette.ink2)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(HashAlgorithm.SHA256, HashAlgorithm.SHA512, HashAlgorithm.SHA384, HashAlgorithm.SHA1, HashAlgorithm.MD5).forEach { alg ->
                                PixelChip(
                                    label = "HMAC-${alg.label}",
                                    selected = primaryAlg == alg,
                                    chapterHue = hue,
                                    onClick = { primaryAlg = alg }
                                )
                            }
                        }
                        Text("SECRET KEY", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = hmacKey,
                            onValueChange = { hmacKey = it },
                            placeholder = "Enter HMAC secret key…",
                            chapterHue = hue,
                            tag = "hmac_key_field"
                        )
                        Text("COMPUTED HMAC-${primaryAlg.label}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper3)
                                .border(2.dp, palette.ink)
                                .padding(12.dp)
                        ) {
                            Text(hmacOutput, style = DotkitType.monoData(13), color = palette.ink)
                        }
                    }
                }
            }

            HashBenchMode.PBKDF2 -> {
                PixelBox(cropMarks = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PBKDF2-HMAC-SHA256 KEY DERIVATION", style = DotkitType.monoLabel(11), color = palette.ink2)
                        Text("SALT STRING", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(
                            value = pbkdf2Salt,
                            onValueChange = { pbkdf2Salt = it },
                            placeholder = "Enter cryptographic salt…",
                            chapterHue = hue
                        )
                        PixelSteppedSlider(
                            label = "PBKDF2 Iterations",
                            value = pbkdf2Iterations,
                            valueRange = 1000..100000,
                            step = 5000,
                            chapterHue = hue,
                            onValueChange = { pbkdf2Iterations = it }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(128, 256, 512).forEach { bits ->
                                PixelChip(
                                    label = "$bits-bit Key",
                                    selected = pbkdf2Bits == bits,
                                    chapterHue = hue,
                                    onClick = { pbkdf2Bits = bits }
                                )
                            }
                        }
                        Text("DERIVED KEY HEX ($pbkdf2Bits BITS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper3)
                                .border(2.dp, palette.ink)
                                .padding(12.dp)
                        ) {
                            Text(pbkdf2Output, style = DotkitType.monoData(13), color = palette.ink)
                        }
                    }
                }
            }

            HashBenchMode.VERIFY_ID -> {
                val idResult = remember(expectedHash) { HashEngine.identifyHash(expectedHash) }
                val matchesPrimary = remember(expectedHash, allDigests) {
                    if (expectedHash.isBlank()) null
                    else allDigests.firstOrNull { (_, hex) -> HashEngine.constantTimeEquals(expectedHash, hex) }?.first
                }

                PixelBox(
                    cropMarks = true,
                    modifier = Modifier.offset { shakeOffset }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CONSTANT-TIME VERIFY & HASH IDENTIFIER", style = DotkitType.monoLabel(11), color = palette.ink2)
                        PixelTextField(
                            value = expectedHash,
                            onValueChange = { expectedHash = it },
                            placeholder = "Paste any digest to identify its algorithm & compare against input…",
                            chapterHue = hue,
                            tag = "hash_verify_input"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = "Paste Current SHA-256",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = {
                                    expectedHash = HashEngine.digestText(inputText, HashAlgorithm.SHA256, uppercaseHex)
                                }
                            )
                            PixelButton(
                                text = "Check Match",
                                chapterHue = hue,
                                onClick = {
                                    if (expectedHash.isNotBlank() && matchesPrimary == null) {
                                        errorShakeTrigger++
                                    }
                                }
                            )
                        }

                        if (expectedHash.isNotBlank()) {
                            if (matchesPrimary != null) {
                                ToolStateBanner(
                                    kind = ToolStateKind.SUCCESS,
                                    title = "MATCH VERIFIED · ${matchesPrimary.label}",
                                    detail = "Constant-time comparison succeeded against input payload.",
                                    chapterHue = hue
                                )
                            } else {
                                ToolStateBanner(
                                    kind = ToolStateKind.ERROR,
                                    title = "MISMATCH AGAINST CURRENT INPUT",
                                    detail = "Pasted digest does not match any of the 14 computed digests for '$inputText'.",
                                    chapterHue = ChapterHue.VERMILION
                                )
                            }

                            HorizontalDivider(thickness = 1.dp, color = palette.ink)
                            Text("HASH IDENTIFIER ANALYSIS", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text(
                                text = "Length: ${idResult.charLength} chars (~${idResult.bitLengthEstimate} bits) · Hex: ${if (idResult.isHex) "YES" else "NO"}",
                                style = DotkitType.monoData(12),
                                color = palette.ink
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                idResult.candidates.forEach { cand ->
                                    PixelStamp(cand, palette.chapterColor(hue))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
