package com.example.tools.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.example.design.PixelTextField
import com.example.lib.expansions.ImagePaletteEngine
import com.example.lib.expansions.PaletteSwatch
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageExpansionScreens(
    meta: ToolMeta,
    initialUri: Uri?,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.COBALT

    var sourceBmp by remember { mutableStateOf<Bitmap?>(null) }
    var appTitle by remember { mutableStateOf("OmniDeck") }
    var themeHex by remember { mutableStateOf("#2563EB") }
    var selectedIconSize by remember { mutableIntStateOf(192) }
    var swatches by remember { mutableStateOf<List<PaletteSwatch>>(emptyList()) }

    fun loadUri(uri: Uri?) {
        if (uri == null) return
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeStream(stream, null, opts)
                if (bmp != null) {
                    sourceBmp = bmp
                    val scaled = bmp.scale(96, 96)
                    val px = IntArray(96 * 96)
                    scaled.getPixels(px, 0, 96, 0, 0, 96, 96)
                    swatches = ImagePaletteEngine.extractTopSwatches(px, 6)
                }
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) loadUri(initialUri)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        loadUri(uri)
    }

    val outputText = if (meta.id == "img-favicon") {
        ImagePaletteEngine.faviconManifestHtml(appTitle, themeHex)
    } else {
        if (swatches.isEmpty()) "Pick an image to extract its dominant color palette & CSS tokens."
        else swatches.joinToString("\n") { "${it.hex}  (${String.format(Locale.US, "%.1f", it.percentage)}%)" } +
            "\n\n:root {\n" +
            swatches.mapIndexed { i, s -> "  --palette-${i + 1}: ${s.hex};" }.joinToString("\n") +
            "\n}"
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = outputText,
        outputFileName = if (meta.id == "img-favicon") "webmanifest.html" else "palette.css",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        helpParagraph = if (meta.id == "img-favicon") {
            "Pick an artwork image via Android Photo Picker to scale crisp PNG app/favicon sizes (16–512px) and generate HTML + site.webmanifest."
        } else {
            "Quantize dominant colors from any photo locally into HEX tokens & CSS variables with 1-tap swatch copy."
        },
        onStatusToast = onStatusToast
    ) {
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    text = if (sourceBmp == null) "Pick Photo from Gallery" else "Replace Source Photo",
                    chapterHue = hue,
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    tag = "${meta.id}_pick_btn"
                )
                sourceBmp?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected image preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.paper)
                    )
                }
            }
        }

        if (meta.id == "img-favicon") {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("APP ICON & WEB MANIFEST", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(value = appTitle, onValueChange = { appTitle = it }, placeholder = "App Name", chapterHue = hue)
                    PixelTextField(value = themeHex, onValueChange = { themeHex = it }, placeholder = "#2563EB", chapterHue = hue)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(16, 32, 48, 180, 192, 512).forEach { sz ->
                            PixelChip(
                                label = "${sz}×$sz",
                                selected = selectedIconSize == sz,
                                chapterHue = hue,
                                onClick = { selectedIconSize = sz }
                            )
                        }
                    }
                    if (sourceBmp != null) {
                        PixelButton(
                            text = "Export ${selectedIconSize}×$selectedIconSize PNG",
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = hue,
                            onClick = {
                                val scaled = sourceBmp!!.scale(selectedIconSize, selectedIconSize)
                                ShareMediaUtils.shareBitmapPng(context, scaled, "favicon_${selectedIconSize}")
                                onStatusToast("Exported ${selectedIconSize}×${selectedIconSize} PNG.")
                            }
                        )
                    }
                    Text(
                        text = outputText,
                        style = DotkitType.monoData(11),
                        color = palette.ink,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.paper).padding(10.dp)
                    )
                }
            }
        } else {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("DOMINANT PALETTE SWATCHES", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelStamp("${swatches.size} COLORS", palette.chapterColor(hue))
                    }
                    if (swatches.isEmpty()) {
                        Text("Tap 'Pick Photo from Gallery' above to extract harmonious color swatches.", style = DotkitType.bodyUi(13), color = palette.ink2)
                    } else {
                        swatches.forEach { sw ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.paper)
                                    .border(0.8.dp, palette.hairline, RoundedCornerShape(12.dp))
                                    .clickable {
                                        copyToClipboard(context, "Swatch", sw.hex)
                                        onStatusToast("Copied ${sw.hex}")
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(sw.colorArgb))
                                            .border(0.8.dp, palette.hairline, RoundedCornerShape(8.dp))
                                    )
                                    Text(sw.hex, style = DotkitType.monoData(14), color = palette.ink)
                                }
                                Text("${String.format(Locale.US, "%.1f", sw.percentage)}%  ·  Tap to copy", style = DotkitType.monoLabel(10), color = palette.ink2)
                            }
                        }
                    }
                }
            }
        }
    }
}
