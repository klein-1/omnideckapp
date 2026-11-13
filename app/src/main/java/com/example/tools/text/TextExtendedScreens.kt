package com.example.tools.text

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelSteppedSlider
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.calc.ExtendedMathEngines
import com.example.lib.text.TextEncodersEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.VaultEntryEntity
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextExtendedScreen(
    meta: ToolMeta,
    initialText: String,
    vaultEntries: List<VaultEntryEntity>,
    onAddVaultEntry: (label: String, username: String, encPayload: String, iv: String, salt: String) -> Unit,
    onDeleteVaultEntry: (Long) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.VERMILION

    // AES Encrypt/Decrypt state
    var aesModeEncrypt by remember { mutableStateOf(true) }
    var aesInput by remember(initialText) { mutableStateOf(initialText) }
    var aesPassphrase by remember { mutableStateOf("") }
    var aesOutput by remember { mutableStateOf("") }

    // Password Vault state
    var masterPass by remember { mutableStateOf("") }
    var vaultLabel by remember { mutableStateOf("") }
    var vaultUser by remember { mutableStateOf("") }
    var vaultSecret by remember { mutableStateOf("") }
    var revealedMap by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    // Repeater + Stylizer + ASCII Art + Zalgo + Mirror + Mail Merge
    var repeatTab by remember { mutableStateOf("STYLE") } // REPEAT, STYLE, ASCII, ZALGO, MIRROR, MERGE
    var repeatText by remember(initialText) { mutableStateOf(initialText.ifBlank { "Omni Deck" }) }
    var repeatCount by remember { mutableIntStateOf(5) }
    var repeatNewLine by remember { mutableStateOf(true) }
    var styleMode by remember { mutableStateOf(TextEncodersEngine.StylishMode.BOLD_SERIF) }
    var zalgoIntensity by remember { mutableIntStateOf(2) }
    var mergeTemplate by remember { mutableStateOf("Hi {{name}}, your seat {{seat}} is ready!") }
    var mergeCsv by remember { mutableStateOf("name,seat\nAlex,A12\nSam,B04") }

    // Lorem Ipsum state
    var loremParagraphs by remember { mutableIntStateOf(2) }

    // Markdown state
    var mdInput by remember(initialText) {
        mutableStateOf(initialText.ifBlank { "# Heading\n\nClean **Swiss** typographic previewer.\n\n- Zero noise\n- 1dp hairlines\n\n`println(\"Omni Deck\")`" })
    }

    // TTS + Dictate + Teleprompter state
    var ttsInput by remember(initialText) { mutableStateOf(initialText) }
    var ttsRatePct by remember { mutableIntStateOf(100) }
    var ttsPitchPct by remember { mutableIntStateOf(100) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var teleprompterRunning by remember { mutableStateOf(false) }
    var teleprompterSpeed by remember { mutableIntStateOf(40) }
    val teleprompterScroll = rememberScrollState()

    val speechDictateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                ttsInput = if (ttsInput.isBlank()) spoken else "$ttsInput $spoken"
                onStatusToast("Dictated text appended.")
            }
        }
    }

    LaunchedEffect(teleprompterRunning, teleprompterSpeed) {
        while (teleprompterRunning) {
            delay(32L)
            val next = teleprompterScroll.value + (teleprompterSpeed / 12).coerceAtLeast(1)
            if (next >= teleprompterScroll.maxValue) {
                teleprompterScroll.scrollTo(teleprompterScroll.maxValue)
                teleprompterRunning = false
            } else {
                teleprompterScroll.scrollTo(next)
            }
        }
    }

    if (meta.id == "txt-tts") {
        DisposableEffect(Unit) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { st ->
                if (st == TextToSpeech.SUCCESS) tts?.language = Locale.US
            }
            ttsEngine = tts
            onDispose { tts.stop(); tts.shutdown(); ttsEngine = null }
        }
    }

    val saveWavLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        if (uri != null && ttsInput.isNotBlank()) {
            val tmp = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")
            ttsEngine?.setSpeechRate(ttsRatePct / 100f)
            ttsEngine?.setPitch(ttsPitchPct / 100f)
            ttsEngine?.synthesizeToFile(ttsInput, Bundle(), tmp, "tts_wav")
            onStatusToast("Synthesizing speech audio…")
            if (tmp.exists()) {
                context.contentResolver.openOutputStream(uri)?.use { out -> tmp.inputStream().copyTo(out) }
            }
        }
    }

    val outputText = when (meta.id) {
        "txt-aes" -> aesOutput
        "txt-repeat" -> when (repeatTab) {
            "REPEAT" -> List(repeatCount) { repeatText }.joinToString(if (repeatNewLine) "\n" else " ")
            "STYLE" -> TextEncodersEngine.stylizeText(repeatText, styleMode)
            "ZALGO" -> ExtendedMathEngines.toZalgo(repeatText, zalgoIntensity)
            "MIRROR" -> repeatText.lines().joinToString("\n") { it.reversed() }
            "MERGE" -> ExtendedMathEngines.applyMailMerge(mergeTemplate, mergeCsv)
            else -> TextEncodersEngine.generateAsciiBanner(repeatText)
        }
        "txt-lorem" -> TextEncodersEngine.generateLoremIpsum(loremParagraphs)
        "txt-markdown" -> mdInput
        "txt-tts" -> ttsInput
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
        showOutputBar = meta.id != "txt-vault" && outputText.isNotBlank(),
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "txt-aes" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelChip(label = "Encrypt", selected = aesModeEncrypt, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { aesModeEncrypt = true })
                            PixelChip(label = "Decrypt", selected = !aesModeEncrypt, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { aesModeEncrypt = false })
                        }
                        PixelTextField(value = aesInput, onValueChange = { aesInput = it }, placeholder = if (aesModeEncrypt) "Plain text secret…" else "Paste CIPHER|IV|SALT envelope…", singleLine = false, minHeight = 96.dp, chapterHue = hue)
                        PixelTextField(value = aesPassphrase, onValueChange = { aesPassphrase = it }, placeholder = "Passphrase (PBKDF2-HMAC-SHA256)…", chapterHue = hue)
                        PixelButton(text = if (aesModeEncrypt) "Encrypt AES-256-GCM" else "Decrypt", chapterHue = hue, enabled = aesInput.isNotBlank() && aesPassphrase.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                            if (aesModeEncrypt) {
                                val env = TextEncodersEngine.encryptAesGcm(aesInput, aesPassphrase)
                                aesOutput = "${env.cipherBase64}|${env.ivBase64}|${env.saltBase64}"
                            } else {
                                val parts = aesInput.trim().split("|")
                                if (parts.size != 3) {
                                    aesOutput = "Error: Expected format CIPHER|IV|SALT"
                                } else {
                                    val dec = TextEncodersEngine.decryptAesGcm(parts[0], parts[1], parts[2], aesPassphrase)
                                    aesOutput = dec.getOrElse { "Decryption failed (wrong passphrase or tampered cipher)." }
                                }
                            }
                        })
                        if (aesOutput.isNotBlank()) {
                            Text(aesOutput, style = DotkitType.monoData(12), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp))
                        }
                    }
                }
            }

            "txt-vault" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("MASTER PASSPHRASE (ZERO-KNOWLEDGE KEY)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(value = masterPass, onValueChange = { masterPass = it; revealedMap = emptyMap() }, placeholder = "Enter vault master passphrase…", chapterHue = hue)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("NEW SECRET ENTRY", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(value = vaultLabel, onValueChange = { vaultLabel = it }, placeholder = "Service / Label", chapterHue = hue)
                        PixelTextField(value = vaultUser, onValueChange = { vaultUser = it }, placeholder = "Username / Email", chapterHue = hue)
                        PixelTextField(value = vaultSecret, onValueChange = { vaultSecret = it }, placeholder = "Password / Secret Key", chapterHue = hue)
                        PixelButton(text = "Encrypt & Save to Vault", chapterHue = hue, enabled = masterPass.isNotEmpty() && vaultLabel.isNotBlank() && vaultSecret.isNotEmpty(), onClick = {
                            val env = TextEncodersEngine.encryptAesGcm(vaultSecret, masterPass)
                            onAddVaultEntry(vaultLabel.trim(), vaultUser.trim(), env.cipherBase64, env.ivBase64, env.saltBase64)
                            vaultLabel = ""; vaultUser = ""; vaultSecret = ""
                            onStatusToast("Encrypted entry stored.")
                        })
                    }
                }
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("VAULT ENTRIES (${vaultEntries.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (vaultEntries.isEmpty()) {
                            Text("Vault is empty. Add your first encrypted credential above.", style = DotkitType.bodyUi(12), color = palette.ink3)
                        } else {
                            vaultEntries.forEach { entry ->
                                val revealed = revealedMap[entry.id]
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(entry.label, style = DotkitType.bodyUi(14, bold = true), color = palette.ink)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            PixelChip(label = if (revealed != null) "Hide" else "Unlock", chapterHue = hue, onClick = {
                                                if (revealed != null) {
                                                    revealedMap = revealedMap - entry.id
                                                } else if (masterPass.isEmpty()) {
                                                    onStatusToast("Enter master passphrase first.")
                                                } else {
                                                    val dec = TextEncodersEngine.decryptAesGcm(entry.encryptedPayload, entry.ivBase64, entry.saltBase64, masterPass)
                                                    if (dec.isSuccess) revealedMap = revealedMap + (entry.id to dec.getOrThrow())
                                                    else onStatusToast("Wrong master passphrase.")
                                                }
                                            })
                                            PixelChip(label = "✕", onClick = { onDeleteVaultEntry(entry.id) })
                                        }
                                    }
                                    if (entry.username.isNotBlank()) Text(entry.username, style = DotkitType.monoLabel(10), color = palette.ink2)
                                    if (revealed != null) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(revealed, style = DotkitType.monoData(13), color = palette.ok)
                                            PixelChip(label = "Copy", onClick = { copyToClipboard(context, entry.label, revealed); onStatusToast("Copied secret.") })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "txt-repeat" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("STYLE" to "Stylish", "REPEAT" to "Repeater", "ASCII" to "ASCII Art", "ZALGO" to "Zalgo", "MIRROR" to "Mirror", "MERGE" to "Mail Merge").forEach { (id, lbl) ->
                                PixelChip(label = lbl, selected = repeatTab == id, chapterHue = hue, onClick = { repeatTab = id })
                            }
                        }
                        if (repeatTab == "MERGE") {
                            Text("TEMPLATE (USE {{col}})", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelTextField(value = mergeTemplate, onValueChange = { mergeTemplate = it }, placeholder = "Hi {{name}}…", singleLine = false, minHeight = 70.dp, chapterHue = hue)
                            Text("CSV DATA (FIRST ROW HEADERS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelTextField(value = mergeCsv, onValueChange = { mergeCsv = it }, placeholder = "name,seat\nAlex,A12", singleLine = false, minHeight = 80.dp, chapterHue = hue)
                        } else {
                            PixelTextField(value = repeatText, onValueChange = { repeatText = it }, placeholder = "Enter source text…", chapterHue = hue)
                        }
                        if (repeatTab == "REPEAT") {
                            PixelSteppedSlider("Repeat Count", repeatCount, 1..100, 5, "×", hue, { repeatCount = it })
                            PixelSwitch("New Line Between Copies", repeatNewLine, { repeatNewLine = it }, hue)
                        } else if (repeatTab == "STYLE") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextEncodersEngine.StylishMode.entries.forEach { m ->
                                    PixelChip(label = m.label, selected = styleMode == m, chapterHue = hue, onClick = { styleMode = m })
                                }
                            }
                        } else if (repeatTab == "ZALGO") {
                            PixelSteppedSlider("Zalgo Intensity", zalgoIntensity, 1..5, 1, " marks", hue, { zalgoIntensity = it })
                        }
                        Text(outputText, style = DotkitType.monoData(if (repeatTab == "ASCII") 10 else 14), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp))
                    }
                }
            }

            "txt-lorem" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelSteppedSlider("Paragraphs", loremParagraphs, 1..10, 1, "", hue, { loremParagraphs = it })
                        Text(outputText, style = DotkitType.bodyUi(13), color = palette.ink)
                    }
                }
            }

            "txt-markdown" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelTextField(value = mdInput, onValueChange = { mdInput = it }, placeholder = "Write Markdown…", singleLine = false, minHeight = 120.dp, chapterHue = hue)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("RENDERED PREVIEW", style = DotkitType.monoLabel(10), color = palette.ink2)
                        mdInput.lines().forEach { raw ->
                            val line = raw.trimEnd()
                            when {
                                line.startsWith("# ") -> Text(line.removePrefix("# "), style = DotkitType.editorialHeading(20), color = palette.ink)
                                line.startsWith("## ") -> Text(line.removePrefix("## "), style = DotkitType.editorialHeading(16), color = palette.ink)
                                line.startsWith("- ") || line.startsWith("* ") -> Text("• ${line.drop(2)}", style = DotkitType.bodyUi(13), color = palette.ink)
                                line.startsWith("`") -> Text(line.trim('`'), style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                                else -> Text(line.replace("**", "").replace("__", ""), style = DotkitType.bodyUi(13), color = palette.ink)
                            }
                        }
                    }
                }
            }

            "txt-tts" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelTextField(value = ttsInput, onValueChange = { ttsInput = it }, placeholder = "Type, dictate, or paste script…", singleLine = false, minHeight = 110.dp, chapterHue = hue)
                        PixelSteppedSlider("Speech Speed", ttsRatePct, 50..200, 10, "%", hue, { ttsRatePct = it })
                        PixelSteppedSlider("Voice Pitch", ttsPitchPct, 60..160, 10, "%", hue, { ttsPitchPct = it })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(text = "Speak Now", chapterHue = hue, enabled = ttsInput.isNotBlank(), onClick = {
                                ttsEngine?.setSpeechRate(ttsRatePct / 100f)
                                ttsEngine?.setPitch(ttsPitchPct / 100f)
                                ttsEngine?.speak(ttsInput, TextToSpeech.QUEUE_FLUSH, null, "speak")
                            })
                            PixelButton(text = "Stop", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { ttsEngine?.stop() })
                            PixelButton(text = "Dictate (Mic)", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                runCatching {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak text to transcribe")
                                    }
                                    speechDictateLauncher.launch(intent)
                                }.onFailure { onStatusToast("Speech recognition unavailable on this device.") }
                            })
                            PixelButton(text = "Save WAV", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, enabled = ttsInput.isNotBlank(), onClick = {
                                saveWavLauncher.launch("speech_${System.currentTimeMillis() / 1000}.wav")
                            })
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("TELEPROMPTER PREVIEW", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelSteppedSlider("Scroll Pace", teleprompterSpeed, 10..120, 10, " px/s", hue, { teleprompterSpeed = it })
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.paper)
                                .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                .verticalScroll(teleprompterScroll)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = ttsInput.ifBlank { "Add script lines above, then tap Start Teleprompter for smooth hands-free scrolling." },
                                style = DotkitType.editorialHeading(20),
                                color = palette.ink
                            )
                            Spacer(Modifier.height(100.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = if (teleprompterRunning) "Pause Scroll" else "Start Teleprompter",
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = { teleprompterRunning = !teleprompterRunning }
                            )
                            PixelButton(
                                text = "Reset Top",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = hue,
                                onClick = { teleprompterRunning = false }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
