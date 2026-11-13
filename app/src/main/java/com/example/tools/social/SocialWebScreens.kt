package com.example.tools.social

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.lib.media.ImageStudioEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialWebScreens(
    meta: ToolMeta,
    initialText: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.TANGERINE
    var outputText by remember(meta.id) { mutableStateOf(initialText) }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = "${meta.id}_output.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = "${meta.name}: ${meta.tagline}",
        onStatusToast = onStatusToast
    ) {
        when (meta.id) {
            "soc-wa" -> {
                var countryCode by remember { mutableStateOf("91") }
                var phone by remember { mutableStateOf("") }
                var message by remember { mutableStateOf(initialText) }
                val cleanNum = (countryCode.filter(Char::isDigit) + phone.filter(Char::isDigit))
                val waUrl = "https://wa.me/$cleanNum" + if (message.isNotBlank()) "?text=${URLEncoder.encode(message, "UTF-8")}" else ""
                outputText = waUrl

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(countryCode, { countryCode = it.filter(Char::isDigit) }, "Code (e.g. 91)", modifier = Modifier.weight(1f))
                            PixelTextField(phone, { phone = it.filter(Char::isDigit) }, "Phone number", modifier = Modifier.weight(2.4f))
                        }
                        PixelTextField(message, { message = it }, "Optional prefilled message", singleLine = false, minHeight = 80.dp)
                        PixelButton(
                            text = "Open in WhatsApp Direct",
                            enabled = cleanNum.length >= 7,
                            chapterHue = hue,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { onStatusToast("Could not open link: ${it.message}") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            "soc-caption" -> {
                var caption by remember(initialText) { mutableStateOf(initialText) }
                var hashtags by remember { mutableStateOf("") }
                val invisibleDotSpacer = "\u2800"
                val formatted = remember(caption, hashtags) {
                    val cleanBody = caption.lines().joinToString("\n") { line -> if (line.isBlank()) invisibleDotSpacer else line }
                    val tags = hashtags.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.joinToString(" ") { t -> if (t.startsWith("#")) t else "#$t" }
                    if (tags.isBlank()) cleanBody else "$cleanBody\n$invisibleDotSpacer\n$tags"
                }
                outputText = formatted

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(caption, { caption = it }, "Write your Instagram / TikTok / X caption...", singleLine = false, minHeight = 110.dp)
                        PixelTextField(hashtags, { hashtags = it }, "Tags (space or comma separated)")
                        Text("IG Bio: ${formatted.length}/150 · X Post: ${formatted.length}/280 · IG Caption: ${formatted.length}/2200", style = DotkitType.monoData(11), color = palette.ink2)
                    }
                }
            }

            "soc-ytdesc" -> {
                var intro by remember { mutableStateOf("") }
                var chaptersRaw by remember { mutableStateOf("00:00 Intro\n01:30 Setup\n04:15 Demo") }
                var linksRaw by remember { mutableStateOf("") }
                val built = buildString {
                    if (intro.isNotBlank()) appendLine(intro.trim()).appendLine()
                    if (chaptersRaw.isNotBlank()) appendLine("Chapters:").appendLine(chaptersRaw.trim()).appendLine()
                    if (linksRaw.isNotBlank()) appendLine("Links:").appendLine(linksRaw.trim())
                }.trim()
                outputText = built

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(intro, { intro = it }, "Video summary intro...", singleLine = false, minHeight = 72.dp)
                        PixelTextField(chaptersRaw, { chaptersRaw = it }, "00:00 Chapter Title per line", singleLine = false, minHeight = 96.dp)
                        PixelTextField(linksRaw, { linksRaw = it }, "Reference links", singleLine = false, minHeight = 72.dp)
                    }
                }
            }

            "soc-status" -> {
                var folderName by remember { mutableStateOf("No folder chosen") }
                var mediaFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
                val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        val doc = DocumentFile.fromTreeUri(context, uri)
                        folderName = doc?.name ?: uri.lastPathSegment ?: "Picked Folder"
                        mediaFiles = doc?.listFiles()?.filter { it.isFile && (it.type?.startsWith("image/") == true || it.type?.startsWith("video/") == true) }.orEmpty()
                        outputText = mediaFiles.joinToString("\n") { it.name ?: "item" }
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Folder: $folderName · ${mediaFiles.size} media items", style = DotkitType.monoData(12), color = palette.ink2)
                        PixelButton("Pick Folder via SAF (Zero Storage Perm)", chapterHue = hue, onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth())
                        mediaFiles.take(30).forEach { df ->
                            Row(Modifier.fillMaxWidth().border(1.dp, palette.hairline).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(df.name ?: "media", style = DotkitType.bodyUi(13, bold = true), color = palette.ink, modifier = Modifier.weight(1f))
                                PixelChip("Share Copy", onClick = {
                                    scope.launch {
                                        val tmp = withContext(Dispatchers.IO) {
                                            val f = File(context.cacheDir, df.name ?: "status_item")
                                            context.contentResolver.openInputStream(df.uri)?.use { ins ->
                                                FileOutputStream(f).use { out -> ins.copyTo(out) }
                                            }
                                            f
                                        }
                                        ShareMediaUtils.shareFile(context, tmp, df.type ?: "*/*", df.name ?: "Share")
                                    }
                                })
                            }
                        }
                    }
                }
            }

            "soc-wifiqr" -> {
                var ssid by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }
                var secType by remember { mutableStateOf("WPA") }
                val payload = if (secType == "nopass") "WIFI:T:nopass;S:$ssid;;" else "WIFI:T:$secType;S:$ssid;P:$pass;;"
                outputText = payload
                val qr = remember(payload) { if (ssid.isNotBlank()) ShareMediaUtils.generateQrBitmap(payload, 520) else null }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelTextField(ssid, { ssid = it }, "Wi-Fi Network Name (SSID)")
                        if (secType != "nopass") PixelTextField(pass, { pass = it }, "Wi-Fi Password")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("WPA", "WEP", "nopass").forEach { s ->
                                PixelChip(if (s == "nopass") "Open" else s, selected = secType == s, chapterHue = hue, onClick = { secType = s })
                            }
                        }
                        if (qr != null) {
                            Image(qr.asImageBitmap(), "Wi-Fi QR Code", modifier = Modifier.size(210.dp).border(1.dp, palette.hairline).padding(6.dp))
                            PixelButton("Share Wi-Fi QR PNG", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                ShareMediaUtils.shareBitmapPng(context, qr, "wifi_qr")
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            else -> { // soc-pfp
                var srcBmp by remember { mutableStateOf<Bitmap?>(null) }
                var ringWidth by remember { mutableIntStateOf(24) }
                var ringColor by remember { mutableIntStateOf(0xFFFF4F00.toInt()) }
                val ringPresets = listOf("Orange" to 0xFFFF4F00.toInt(), "Cobalt" to 0xFF0052FF.toInt(), "Emerald" to 0xFF0E9F6E.toInt(), "Gold" to 0xFFFFB800.toInt(), "Black" to 0xFF141413.toInt(), "White" to 0xFFFFFFFF.toInt())

                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) srcBmp = ImageStudioEngine.decodeBitmap(context, uri, 960)
                }

                val previewBmp = remember(srcBmp, ringWidth, ringColor) {
                    srcBmp?.let { ImageStudioEngine.makeCircleBorderAvatar(it, borderWidthPx = ringWidth, borderColor = ringColor, sizePx = 720) }
                }
                outputText = if (previewBmp != null) "Avatar generated (${previewBmp.width}x${previewBmp.height} PNG)" else ""

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelButton("Pick Avatar Photo", chapterHue = hue, onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, modifier = Modifier.fillMaxWidth())
                        if (previewBmp != null) {
                            Image(previewBmp.asImageBitmap(), "Avatar Preview", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, palette.hairline, RoundedCornerShape(8.dp)))
                        }
                        PixelSteppedSlider("Ring Thickness", ringWidth, 4..60, step = 4, unitSuffix = "px", chapterHue = hue, onValueChange = { ringWidth = it })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ringPresets.forEach { (name, col) ->
                                PixelChip(name, selected = ringColor == col, chapterHue = hue, onClick = { ringColor = col })
                            }
                        }
                        if (previewBmp != null) {
                            PixelButton("Share Circular Avatar PNG", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                ShareMediaUtils.shareBitmapPng(context, previewBmp, "avatar_pfp")
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
