package com.example.tools.image

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.toColorInt
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
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
import com.example.lib.media.ExifSummary
import com.example.lib.media.ExportImageFormat
import com.example.lib.media.ImageFilterStep
import com.example.lib.media.ImageFilterType
import com.example.lib.media.ImageStudioAdvanced
import com.example.lib.media.ImageStudioEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageStudioExtrasScreen(
    meta: ToolMeta,
    initialUri: Uri?,
    initialPayload: String,
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
    val hue = ChapterHue.COBALT

    var sourceUri by remember { mutableStateOf(initialUri) }
    var sourceBmp by remember { mutableStateOf<Bitmap?>(null) }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }

    // GIF & Video->Frames state
    val gifFrames = remember { mutableStateListOf<Bitmap>() }
    var gifDelayMs by remember { mutableIntStateOf(300) }
    var exportedGif by remember { mutableStateOf<File?>(null) }

    // Watermark state
    var wmText by remember { mutableStateOf("© OMNI DECK") }
    var wmTiled by remember { mutableStateOf(true) }
    var wmOpacity by remember { mutableIntStateOf(40) }

    // EXIF + Batch Edit/Scrub state
    var exifSummary by remember { mutableStateOf<ExifSummary?>(null) }
    var exifArtist by remember { mutableStateOf("") }
    var exifCopyright by remember { mutableStateOf("") }

    // OCR Scan Enhance / Photo Restore / B&W Colorize state
    var scanThreshold by remember { mutableIntStateOf(55) }
    var docMode by remember { mutableIntStateOf(0) } // 0=OCR High Contrast, 1=Restore Denoise+Enhance, 2=Colorize B&W

    // Text to Image state
    var cardTitle by remember(initialPayload) { mutableStateOf(if (initialPayload.isNotBlank()) "Note" else "Swiss Typography") }
    var cardBody by remember(initialPayload) { mutableStateOf(initialPayload.ifBlank { "Form follows function. Grid discipline reveals clarity." }) }
    var cardThemeIdx by remember { mutableIntStateOf(0) }

    // Signature state
    val sigStrokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var sigColorBlack by remember { mutableStateOf(true) }

    // Color Picker + Top-6 Palette Extractor state
    var pickedHex by remember { mutableStateOf("#141413") }
    var pickedRgb by remember { mutableStateOf("RGB(20, 20, 19)") }
    var extractedSwatches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var imgBoxSize by remember { mutableStateOf(IntSize(1, 1)) }

    fun recomputeDocPreview(bmp: Bitmap?) {
        if (bmp == null) return
        previewBmp = when (meta.id) {
            "img-watermark" -> ImageStudioEngine.addWatermark(bmp, wmText, wmTiled, wmOpacity / 100f)
            "img-ocr" -> when (docMode) {
                1 -> ImageStudioEngine.applyFilterChain(bmp, listOf(ImageFilterStep(ImageFilterType.DENOISE, 0.55f), ImageFilterStep(ImageFilterType.AUTO_ENHANCE, 0.85f), ImageFilterStep(ImageFilterType.SHARPEN, 0.35f)))
                2 -> ImageStudioAdvanced.colorizeWarmPortrait(bmp, 0.9f)
                else -> ImageStudioEngine.applyFilterChain(bmp, listOf(ImageFilterStep(ImageFilterType.GRAYSCALE, 1f), ImageFilterStep(ImageFilterType.CONTRAST, 0.85f), ImageFilterStep(ImageFilterType.THRESHOLD, scanThreshold / 100f)))
            }
            else -> bmp
        }
    }

    fun loadSingleUri(uri: Uri) {
        sourceUri = uri
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageStudioEngine.decodeBitmap(context, uri, 1280) }
            sourceBmp = bmp
            if (meta.id == "img-exif") exifSummary = withContext(Dispatchers.IO) { ImageStudioEngine.readExifSummary(context, uri) }
            if (meta.id == "img-colorpick" && bmp != null) extractedSwatches = withContext(Dispatchers.Default) { ImageStudioAdvanced.extractPalette(bmp, 6) }
            withContext(Dispatchers.Default) { recomputeDocPreview(bmp) }
        }
    }

    LaunchedEffect(initialUri) { if (initialUri != null && sourceBmp == null) loadSingleUri(initialUri) }

    LaunchedEffect(sourceBmp, wmText, wmTiled, wmOpacity, scanThreshold, docMode, cardTitle, cardBody, cardThemeIdx) {
        if (meta.id == "img-textcard") {
            val themes = listOf(
                Triple(0xFFF4F0EA.toInt(), 0xFF141413.toInt(), 0xFFFF4F00.toInt()),
                Triple(0xFF121316.toInt(), 0xFFF2EFE9.toInt(), 0xFF00E676.toInt()),
                Triple(0xFF0F1926.toInt(), 0xFFE6F0FA.toInt(), 0xFF00B8D4.toInt())
            )
            val (bg, fg, acc) = themes[cardThemeIdx % themes.size]
            previewBmp = ImageStudioEngine.renderTextCard(cardTitle, cardBody, bg, fg, acc)
        } else if (sourceBmp != null) {
            withContext(Dispatchers.Default) { recomputeDocPreview(sourceBmp) }
        }
    }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) loadSingleUri(it) }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(24)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val list = withContext(Dispatchers.IO) { uris.mapNotNull { ImageStudioEngine.decodeBitmap(context, it, 480) } }
                gifFrames.addAll(list)
            }
        }
    }
    val videoFramePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val frames = withContext(Dispatchers.IO) { ImageStudioAdvanced.extractVideoFrames(context, uri, 10) }
                gifFrames.addAll(frames)
                onStatusToast("Extracted ${frames.size} video frames into GIF sequence.")
            }
        }
    }

    val savePngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bmp = previewBmp ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = ImageStudioEngine.compressToBytes(bmp, ExportImageFormat.PNG, 100)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            onStatusToast("Saved clean PNG.")
        }
    }

    val saveJpgWithExifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val bmp = previewBmp ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val tmp = File(context.cacheDir, "exif_edited_${System.currentTimeMillis()}.jpg")
                tmp.writeBytes(ImageStudioEngine.compressToBytes(bmp, ExportImageFormat.JPEG, 92))
                runCatching {
                    val exif = ExifInterface(tmp.absolutePath)
                    if (exifArtist.isNotBlank()) exif.setAttribute(ExifInterface.TAG_ARTIST, exifArtist.trim())
                    if (exifCopyright.isNotBlank()) exif.setAttribute(ExifInterface.TAG_COPYRIGHT, exifCopyright.trim())
                    exif.saveAttributes()
                }
                context.contentResolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                tmp.delete()
            }
            onStatusToast("Saved JPEG with updated Artist/Copyright EXIF tags.")
        }
    }

    val saveGifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/gif")) { uri ->
        if (uri != null && gifFrames.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { ImageStudioEngine.encodeAnimatedGif(gifFrames.toList(), gifDelayMs, it) }
            }
            onStatusToast("Exported GIF89a animation.")
        }
    }

    val outputSummary = when (meta.id) {
        "img-exif" -> exifSummary?.tags?.joinToString("\n") { "${it.first}: ${it.second}" }.orEmpty()
        "img-colorpick" -> buildString {
            appendLine("$pickedHex · $pickedRgb")
            if (extractedSwatches.isNotEmpty()) {
                append("Palette: " + extractedSwatches.joinToString(", ") { String.format("#%06X", 0xFFFFFF and it) })
            }
        }
        else -> ""
    }

    ToolWorkbenchChrome(
        meta = meta, isPinned = isPinned, onTogglePin = onTogglePin, onBack = onBack,
        outputText = outputSummary, outputFileName = "${meta.id}_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT, onSaveToFiles = onSaveToFiles, onSendToTool = onSendToTool,
        onStatusToast = onStatusToast, showOutputBar = outputSummary.isNotBlank(), helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "img-gif" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GIF89a & VIDEO-TO-FRAMES (${gifFrames.size} FRAMES)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(text = "+ Pick Photos", chapterHue = hue, onClick = {
                                multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            PixelButton(text = "Video → Frames", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                videoFramePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            })
                            if (gifFrames.isNotEmpty()) PixelChip(label = "Clear", onClick = { gifFrames.clear(); exportedGif = null })
                        }
                        PixelSteppedSlider("Frame Delay", gifDelayMs, 80..1200, 40, " ms", hue, { gifDelayMs = it })
                        if (gifFrames.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = "Save .GIF", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    saveGifLauncher.launch("omnideck_${System.currentTimeMillis() / 1000}.gif")
                                })
                                PixelButton(text = "Build & Share", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    scope.launch {
                                        val file = withContext(Dispatchers.IO) {
                                            val f = File(context.cacheDir, "omnideck_anim_${System.currentTimeMillis()}.gif")
                                            FileOutputStream(f).use { ImageStudioEngine.encodeAnimatedGif(gifFrames.toList(), gifDelayMs, it) }
                                            f
                                        }
                                        exportedGif = file
                                        ShareMediaUtils.shareFile(context, file, "image/gif", "Share GIF")
                                    }
                                })
                            }
                        }
                        exportedGif?.let { f -> Text("Built GIF: ${f.length() / 1024} KB", style = DotkitType.monoData(12), color = palette.ok) }
                    }
                }
            }

            "img-watermark", "img-ocr", "img-exif", "img-colorpick" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelButton(text = if (sourceBmp == null) "Pick Photo" else "Change Photo", chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })

                        if (meta.id == "img-colorpick" && sourceBmp != null) {
                            Text("TAP ANY PIXEL · TOP-6 PALETTE EXTRACTOR", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Image(
                                bitmap = sourceBmp!!.asImageBitmap(), contentDescription = "Color Sample Photo", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(250.dp).onSizeChanged { imgBoxSize = it }
                                    .pointerInput(sourceBmp) {
                                        detectTapGestures { offset ->
                                            val bmp = sourceBmp ?: return@detectTapGestures
                                            val px = ((offset.x / imgBoxSize.width.coerceAtLeast(1)) * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                                            val py = ((offset.y / imgBoxSize.height.coerceAtLeast(1)) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                                            val c = bmp[px, py]
                                            val r = android.graphics.Color.red(c); val g = android.graphics.Color.green(c); val b = android.graphics.Color.blue(c)
                                            pickedHex = String.format("#%02X%02X%02X", r, g, b); pickedRgb = "RGB($r, $g, $b)"
                                        }
                                    }
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color(pickedHex.toColorInt())).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(pickedHex, style = DotkitType.monoData(16), color = palette.ink)
                                    Text(pickedRgb, style = DotkitType.monoLabel(11), color = palette.ink2)
                                }
                                PixelChip(label = "Copy HEX", chapterHue = hue, onClick = { copyToClipboard(context, "HEX", pickedHex); onStatusToast("Copied $pickedHex") })
                            }
                            if (extractedSwatches.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    extractedSwatches.forEach { argb ->
                                        val hex = String.format("#%06X", 0xFFFFFF and argb)
                                        Row(
                                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, palette.hairline, RoundedCornerShape(8.dp)).clickable {
                                                pickedHex = hex; copyToClipboard(context, "Swatch", hex); onStatusToast("Copied $hex")
                                            }.padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(argb)))
                                            Text(hex, style = DotkitType.monoLabel(10), color = palette.ink)
                                        }
                                    }
                                }
                            }
                        } else if (previewBmp != null) {
                            Image(bitmap = previewBmp!!.asImageBitmap(), contentDescription = "Preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(240.dp).border(1.dp, palette.hairline))
                        }

                        if (meta.id == "img-watermark") {
                            PixelTextField(value = wmText, onValueChange = { wmText = it }, placeholder = "Watermark text…", chapterHue = hue)
                            PixelSwitch("Tiled Diagonal Overlay", wmTiled, { wmTiled = it }, hue)
                            PixelSteppedSlider("Opacity", wmOpacity, 10..95, 5, "%", hue, { wmOpacity = it })
                        }

                        if (meta.id == "img-ocr") {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("OCR B&W Scan", "Photo Restore", "Colorize B&W").forEachIndexed { idx, lbl ->
                                    PixelChip(label = lbl, selected = docMode == idx, chapterHue = hue, onClick = { docMode = idx })
                                }
                            }
                            if (docMode == 0) PixelSteppedSlider("Document Contrast Threshold", scanThreshold, 20..85, 5, "%", hue, { scanThreshold = it })
                        }

                        if (meta.id == "img-exif" && exifSummary != null) {
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("EXIF TAGS (${exifSummary!!.tags.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                                if (exifSummary!!.hasGps) PixelStamp("GPS FOUND", palette.warn)
                            }
                            exifSummary!!.tags.forEach { (k, v) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(k, style = DotkitType.monoLabel(10), color = palette.ink2)
                                    Text(v, style = DotkitType.monoData(11), color = palette.ink)
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text("WRITE NEW ARTIST / COPYRIGHT EXIF (OR SCRUB TO PNG)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelTextField(value = exifArtist, onValueChange = { exifArtist = it }, placeholder = "Artist / Author", chapterHue = hue, modifier = Modifier.weight(1f))
                                PixelTextField(value = exifCopyright, onValueChange = { exifCopyright = it }, placeholder = "Copyright ©", chapterHue = hue, modifier = Modifier.weight(1f))
                            }
                        }

                        if (previewBmp != null && meta.id != "img-colorpick") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = if (meta.id == "img-exif") "Scrub All → PNG" else "Save PNG", chapterHue = hue, onClick = {
                                    savePngLauncher.launch("${meta.id}_clean_${System.currentTimeMillis() / 1000}.png")
                                })
                                if (meta.id == "img-exif") {
                                    PixelButton(text = "Write EXIF → JPG", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                        saveJpgWithExifLauncher.launch("exif_edited_${System.currentTimeMillis() / 1000}.jpg")
                                    })
                                }
                                PixelButton(text = "Share PNG", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                    previewBmp?.let { ShareMediaUtils.shareBitmapPng(context, it, meta.id) }
                                })
                            }
                        }
                    }
                }
            }

            "img-textcard" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelTextField(value = cardTitle, onValueChange = { cardTitle = it }, placeholder = "Poster Headline…", chapterHue = hue)
                        PixelTextField(value = cardBody, onValueChange = { cardBody = it }, placeholder = "Body quote or copy…", singleLine = false, minHeight = 100.dp, chapterHue = hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Swiss Paper", "Carbon Dark", "Blueprint").forEachIndexed { idx, lbl ->
                                PixelChip(label = lbl, selected = cardThemeIdx == idx, chapterHue = hue, onClick = { cardThemeIdx = idx }, modifier = Modifier.weight(1f))
                            }
                        }
                        previewBmp?.let { bmp ->
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Text Card", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(250.dp).border(1.dp, palette.hairline))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = "Save PNG", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { savePngLauncher.launch("poster_${System.currentTimeMillis() / 1000}.png") })
                                PixelButton(text = "Share PNG", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { ShareMediaUtils.shareBitmapPng(context, bmp, "poster") })
                            }
                        }
                    }
                }
            }

            "img-sig" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("TRANSPARENT SIGNATURE PAD", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip(label = if (sigColorBlack) "Black Ink" else "Blue Ink", selected = true, chapterHue = hue, onClick = { sigColorBlack = !sigColorBlack })
                                PixelChip(label = "Clear", onClick = { sigStrokes.clear() })
                            }
                        }
                        Canvas(
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { activeStroke = listOf(it) },
                                        onDrag = { ch, _ -> ch.consume(); activeStroke = activeStroke + ch.position },
                                        onDragEnd = { if (activeStroke.isNotEmpty()) sigStrokes.add(activeStroke); activeStroke = emptyList() }
                                    )
                                }
                        ) {
                            val col = if (sigColorBlack) Color(0xFF141413) else Color(0xFF0052FF)
                            fun drawPts(pts: List<Offset>) {
                                if (pts.size < 2) return
                                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                                drawPath(path, col, style = Stroke(width = 8f, cap = StrokeCap.Round))
                            }
                            sigStrokes.forEach { drawPts(it) }
                            drawPts(activeStroke)
                        }
                        PixelButton(text = "Export Transparent Signature PNG", chapterHue = hue, enabled = sigStrokes.isNotEmpty(), onClick = {
                            val bmp = createBitmap(900, 420)
                            val c = android.graphics.Canvas(bmp)
                            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (sigColorBlack) 0xFF141413.toInt() else 0xFF0052FF.toInt()
                                strokeWidth = 10f; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            sigStrokes.forEach { pts -> for (i in 1 until pts.size) c.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, p) }
                            previewBmp = bmp
                            ShareMediaUtils.shareBitmapPng(context, bmp, "signature")
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
