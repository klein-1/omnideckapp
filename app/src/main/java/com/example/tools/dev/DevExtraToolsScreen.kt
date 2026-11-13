package com.example.tools.dev

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
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.design.SwissControlShape
import com.example.lib.text.AsymKeyBundle
import com.example.lib.text.TextExtraEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevExtraToolsScreen(
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
    val hue = ChapterHue.FOREST

    // Asymmetric Keys & Sign/Verify state
    var useEcdsa by remember { mutableStateOf(true) }
    var keyBundle by remember { mutableStateOf<AsymKeyBundle?>(null) }
    var signPayload by remember(initialText) { mutableStateOf(initialText) }
    var signatureOut by remember { mutableStateOf("") }
    var verifyStatus by remember { mutableStateOf<Boolean?>(null) }

    // Batch ID & Entropy state
    var idKind by remember { mutableStateOf("ULID") }
    var idCount by remember { mutableIntStateOf(8) }
    var generatedBatch by remember { mutableStateOf<List<String>>(emptyList()) }
    var entropySample by remember(initialText) { mutableStateOf(initialText) }

    val outputText = when (meta.id) {
        "dev-hmacsig" -> buildString {
            keyBundle?.let {
                appendLine("=== ${it.algorithm} ===")
                appendLine(it.publicKeyPem)
            }
            if (signatureOut.isNotBlank()) appendLine("Signature (Base64):\n$signatureOut")
        }.trim()
        "dev-batchid" -> generatedBatch.joinToString("\n")
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
        if (meta.id == "dev-hmacsig") {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelChip("ECDSA P-256", selected = useEcdsa, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { useEcdsa = true; keyBundle = null; signatureOut = ""; verifyStatus = null })
                        PixelChip("RSA-2048", selected = !useEcdsa, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { useEcdsa = false; keyBundle = null; signatureOut = ""; verifyStatus = null })
                    }
                    PixelButton(
                        text = if (keyBundle == null) "Generate ${if (useEcdsa) "ECDSA P-256" else "RSA-2048"} Keypair" else "Regenerate Keypair",
                        chapterHue = hue,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            keyBundle = TextExtraEngine.generateAsymKeyPair(useEcdsa)
                            signatureOut = ""
                            verifyStatus = null
                            onStatusToast("Generated hardware-random keypair.")
                        }
                    )
                    keyBundle?.let { kb ->
                        Text("PUBLIC KEY (${kb.algorithm})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(
                            text = kb.publicKeyPem.take(180) + "…",
                            style = DotkitType.monoData(11),
                            color = palette.ink,
                            modifier = Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(10.dp)
                        )
                        PixelTextField(signPayload, { signPayload = it; verifyStatus = null }, "Message or JSON payload to sign…", singleLine = false, minHeight = 84.dp, chapterHue = hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Sign Payload", chapterHue = hue, enabled = signPayload.isNotEmpty(), modifier = Modifier.weight(1f), onClick = {
                                signatureOut = TextExtraEngine.signPayload(signPayload, kb.privateKey, useEcdsa)
                                verifyStatus = null
                            })
                            PixelButton("Verify", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, enabled = signatureOut.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
                                verifyStatus = TextExtraEngine.verifyPayload(signPayload, signatureOut, kb.publicKey, useEcdsa)
                            })
                        }
                        if (signatureOut.isNotBlank()) {
                            PixelTextField(signatureOut, { signatureOut = it; verifyStatus = null }, "Base64 Signature", singleLine = false, minHeight = 64.dp, chapterHue = hue)
                        }
                        verifyStatus?.let { ok ->
                            PixelStamp(if (ok) "SIGNATURE VALID ✓" else "SIGNATURE INVALID ✕", if (ok) palette.ok else palette.err)
                        }
                    }
                }
            }
        } else {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ULID", "UUIDv4", "NanoID-21", "Hex-128", "Hex-256").forEach { k ->
                            PixelChip(k, selected = idKind == k, chapterHue = hue, onClick = { idKind = k })
                        }
                    }
                    PixelSteppedSlider("Batch Count", idCount, 1..50, 1, " IDs", hue, { idCount = it })
                    PixelButton("Generate $idCount × $idKind", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                        generatedBatch = List(idCount) {
                            when (idKind) {
                                "ULID" -> TextExtraEngine.generateUlid()
                                "UUIDv4" -> TextExtraEngine.generateUuidV4()
                                "NanoID-21" -> TextExtraEngine.generateNanoId(21)
                                "Hex-128" -> TextExtraEngine.generateHexToken(16)
                                else -> TextExtraEngine.generateHexToken(32)
                            }
                        }
                    })
                    if (generatedBatch.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            generatedBatch.take(20).forEach { id ->
                                Text(id, style = DotkitType.monoData(11), color = palette.ink)
                            }
                        }
                    }
                }
            }

            PixelBox {
                val ent = TextExtraEngine.shannonEntropyBitsPerChar(entropySample)
                val totalBits = ent * entropySample.length
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SHANNON ENTROPY ANALYZER", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(entropySample, { entropySample = it }, "Paste any token, secret, or string…", chapterHue = hue)
                    DotMatrixReadout("${"%.2f".format(totalBits)} bits", "${"%.2f".format(ent)} bits/char · ${entropySample.length} chars", hue)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
