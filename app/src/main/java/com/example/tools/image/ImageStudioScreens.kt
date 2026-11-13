package com.example.tools.image

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.design.PixelNumberField
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.media.ExportImageFormat
import com.example.lib.media.ImageFilterStep
import com.example.lib.media.ImageFilterType
import com.example.lib.media.ImageStudioAdvanced
import com.example.lib.media.ImageStudioEngine
import com.example.lib.media.PdfLabEngine
import com.example.lib.media.StitchLayout
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageStudioCoreScreen(
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

    var sourceBmp by remember { mutableStateOf<Bitmap?>(null) }
    var previewBmp by remember { mutableStateOf<Bitmap?>(null) }
    val multiBmps = remember { mutableStateListOf<Bitmap>() }
    var compareSplit by remember { mutableStateOf(false) }

    // Filter chain + Undo/Redo
    val filterChain = remember(initialPayload) {
        mutableStateListOf<ImageFilterStep>().apply {
            if (initialPayload.startsWith("{")) addAll(ImageStudioEngine.deserializeFilterChain(initialPayload))
        }
    }
    val redoStack = remember { mutableStateListOf<ImageFilterStep>() }
    var selectedFilterType by remember { mutableStateOf(ImageFilterType.BRIGHTNESS) }
    var filterIntensity by remember { mutableIntStateOf(65) }
    var showChainQr by remember { mutableStateOf(false) }

    // Crop / Rotate / Straighten / Border / Passport
    var rotateDeg by remember { mutableIntStateOf(0) }
    var straightenDeg by remember { mutableIntStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var aspectChoice by remember { mutableStateOf<Pair<String, Float?>>("Free" to null) }
    var padWithBlur by remember { mutableStateOf(false) }
    var cornerPct by remember { mutableIntStateOf(0) }
    var borderPx by remember { mutableIntStateOf(0) }
    var expandPct by remember { mutableIntStateOf(0) }
    var circleCrop by remember { mutableStateOf(false) }
    var passportMode by remember { mutableStateOf(false) }

    // Resize / Upscale / Favicon / srcset
    var targetW by remember { mutableStateOf("0") }
    var targetH by remember { mutableStateOf("0") }
    var scalePct by remember { mutableIntStateOf(100) }
    var keepAspect by remember { mutableStateOf(true) }
    var upscale2x by remember { mutableStateOf(false) }

    // Compress & Batch Convert + Target KB + Rename
    var exportFmt by remember { mutableStateOf(ExportImageFormat.JPEG) }
    var exportQuality by remember { mutableIntStateOf(82) }
    var targetKbText by remember { mutableStateOf("0") }
    var renamePrefix by remember { mutableStateOf("omnideck_img") }
    var estimatedBytes by remember { mutableIntStateOf(0) }

    // Draw, Censor & Meme Text
    val drawStrokes = remember { mutableStateListOf<Pair<List<Offset>, Color>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushColor by remember { mutableStateOf(Color(0xFFFF4F00)) }
    var censorMode by remember { mutableStateOf(false) }
    var memeTop by remember { mutableStateOf("") }
    var memeBottom by remember { mutableStateOf("") }

    // BG Remove & Replace
    var bgTolerance by remember { mutableIntStateOf(48) }
    var bgMode by remember { mutableIntStateOf(0) } // 0=Transparent, 1=White, 2=BlurBokeh, 3=Paper

    // Collage, Grid Split & Duplicate dHash Similarity
    var stitchLayout by remember { mutableStateOf(StitchLayout.VERTICAL) }
    var collageMode by remember { mutableIntStateOf(0) } // 0=Collage, 1=Grid 3x3 Split, 2=Sprite, 3=Similarity dHash
    var similarityReport by remember { mutableStateOf("") }

    fun recomputePreview() {
        val src = sourceBmp
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                when (meta.id) {
                    "img-filters" -> src?.let { ImageStudioEngine.applyFilterChain(it, filterChain.toList()) }
                    "img-crop" -> src?.let {
                        if (passportMode) ImageStudioAdvanced.buildPassportSheet(it)
                        else {
                            val geo = ImageStudioEngine.transformGeometry(it, (rotateDeg + straightenDeg).toFloat(), flipH, flipV, aspectChoice.second, padWithBlur)
                            ImageStudioAdvanced.roundCornersBorderExpand(geo, cornerPct, borderPx, AColor.BLACK, expandPct, AColor.WHITE, circleCrop)
                        }
                    }
                    "img-resize" -> src?.let {
                        val base = if (upscale2x) ImageStudioAdvanced.upscale2xSharpened(it) else it
                        val wIn = targetW.toIntOrNull() ?: 0
                        val w = if (wIn > 0) wIn.coerceIn(16, 4096) else (base.width * scalePct / 100f).roundToInt().coerceIn(16, 4096)
                        val hIn = targetH.toIntOrNull() ?: 0
                        val h = if (keepAspect || hIn <= 0) (w * base.height / base.width.coerceAtLeast(1)).coerceIn(16, 4096) else hIn.coerceIn(16, 4096)
                        Bitmap.createScaledBitmap(base, w, h, true)
                    }
                    "img-compress" -> src?.also {
                        val tKb = targetKbText.toIntOrNull() ?: 0
                        estimatedBytes = if (tKb > 0) {
                            val (bytes, solvedQ) = ImageStudioAdvanced.compressToTargetKb(it, exportFmt, tKb)
                            exportQuality = solvedQ
                            bytes.size
                        } else ImageStudioEngine.compressToBytes(it, exportFmt, exportQuality).size
                    }
                    "img-bg" -> src?.let {
                        when (bgMode) {
                            1 -> ImageStudioAdvanced.replaceBackground(it, bgTolerance, AColor.WHITE, false)
                            2 -> ImageStudioAdvanced.replaceBackground(it, bgTolerance, null, true)
                            3 -> ImageStudioAdvanced.replaceBackground(it, bgTolerance, 0xFFF4F0EA.toInt(), false)
                            else -> ImageStudioEngine.removeBackgroundByCornerChroma(it, bgTolerance)
                        }
                    }
                    "img-collage" -> when (collageMode) {
                        1 -> src?.let { ImageStudioEngine.stitchCollage(ImageStudioAdvanced.splitIntoGrid(it, 3, 3), StitchLayout.GRID_2X2, gapPx = 12) }
                        2 -> if (multiBmps.isNotEmpty()) ImageStudioAdvanced.buildSpriteSheet(multiBmps.toList()) else null
                        else -> if (multiBmps.isNotEmpty()) ImageStudioEngine.stitchCollage(multiBmps.toList(), stitchLayout) else null
                    }
                    else -> src
                }
            }
            previewBmp = out
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && sourceBmp == null) {
            val decoded = withContext(Dispatchers.IO) { ImageStudioEngine.decodeBitmap(context, initialUri, 1280) }
            if (decoded != null) {
                sourceBmp = decoded; targetW = decoded.width.toString(); targetH = decoded.height.toString(); recomputePreview()
            }
        }
    }

    LaunchedEffect(
        sourceBmp, filterChain.size, rotateDeg, straightenDeg, flipH, flipV, aspectChoice, padWithBlur,
        cornerPct, borderPx, expandPct, circleCrop, passportMode, targetW, targetH, scalePct, keepAspect, upscale2x,
        exportFmt, exportQuality, targetKbText, bgTolerance, bgMode, stitchLayout, collageMode, multiBmps.size
    ) { recomputePreview() }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { ImageStudioEngine.decodeBitmap(context, uri, 1280) }
                if (bmp != null) {
                    sourceBmp = bmp; targetW = bmp.width.toString(); targetH = bmp.height.toString(); recomputePreview()
                }
            }
        }
    }

    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(16)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val list = withContext(Dispatchers.IO) { uris.mapNotNull { ImageStudioEngine.decodeBitmap(context, it, 900) } }
                multiBmps.addAll(list)
                if (meta.id == "img-collage" && collageMode == 3 && multiBmps.size >= 2) {
                    similarityReport = withContext(Dispatchers.Default) {
                        val hashes = multiBmps.map { ImageStudioAdvanced.computeDHash(it) }
                        buildString {
                            appendLine("PERCEPTUAL dHash SIMILARITY MATRIX (${hashes.size} images):")
                            for (i in hashes.indices) {
                                for (j in i + 1 until hashes.size) {
                                    val dist = ImageStudioAdvanced.hammingDistance(hashes[i], hashes[j])
                                    val simPct = ((64 - dist) * 100 / 64)
                                    val tag = when {
                                        dist <= 5 -> "DUPLICATE MATCH"
                                        dist <= 12 -> "NEAR-DUPLICATE"
                                        else -> "DISTINCT"
                                    }
                                    appendLine("• Img #${i + 1} ↔ Img #${j + 1}: $simPct% ($tag, Hamming=$dist)")
                                }
                            }
                        }.trim()
                    }
                }
                recomputePreview()
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(exportFmt.mime)) { uri ->
        val bmp = previewBmp ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = ImageStudioEngine.compressToBytes(bmp, exportFmt, exportQuality)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
            onStatusToast("Exported ${exportFmt.label} image.")
        }
    }

    val zipSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                when (meta.id) {
                    "img-resize" -> sourceBmp?.let { ImageStudioAdvanced.buildFaviconZip(it, out) }
                    "img-compress" -> {
                        val list = if (multiBmps.isNotEmpty()) multiBmps.toList() else listOfNotNull(previewBmp)
                        ImageStudioAdvanced.batchConvertToZip(list, exportFmt, exportQuality, renamePrefix, out)
                    }
                    "img-collage" -> sourceBmp?.let {
                        val slices = ImageStudioAdvanced.splitIntoGrid(it, 3, 3)
                        ImageStudioAdvanced.batchConvertToZip(slices, ExportImageFormat.PNG, 100, "grid_slice", out)
                    }
                    else -> previewBmp?.let { ImageStudioAdvanced.buildSrcsetZip(it, renamePrefix, exportFmt, exportQuality, out) }
                }
            }
        }
        onStatusToast("Exported ZIP archive.")
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val bmp = previewBmp ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { PdfLabEngine.bitmapsToPdf(listOf(bmp), it, 90) }
            }
            onStatusToast("Exported single-page PDF.")
        }
    }

    val chainJson = remember(filterChain.size, filterChain.toList()) {
        if (meta.id == "img-filters" && filterChain.isNotEmpty()) ImageStudioEngine.serializeFilterChain(filterChain.toList()) else ""
    }

    ToolWorkbenchChrome(
        meta = meta, isPinned = isPinned, onTogglePin = onTogglePin, onBack = onBack,
        outputText = similarityReport.ifBlank { chainJson },
        outputFileName = "img_studio_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT, onSaveToFiles = onSaveToFiles, onSendToTool = onSendToTool, onStatusToast = onStatusToast,
        showOutputBar = (meta.id == "img-filters" && chainJson.isNotEmpty()) || similarityReport.isNotBlank(),
        helpParagraph = "ImageToolbox workstation: 22-filter pipeline + presets + undo/redo + Before/After split slider, straighten/border/passport crop, 2× upscale/Favicon/srcset packs, target-KB & BMP/Batch ZIP compression, meme/censor draw, bokeh cutout replacement, grid splitter & perceptual dHash duplicate finder."
    ) {
        if (meta.id != "img-draw") {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("SOURCE & PREVIEW", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (sourceBmp != null && previewBmp != null && sourceBmp != previewBmp) {
                                PixelChip(label = if (compareSplit) "Split: ON" else "Compare", selected = compareSplit, chapterHue = hue, onClick = { compareSplit = !compareSplit })
                            }
                            if (previewBmp != null) PixelStamp("${previewBmp!!.width}×${previewBmp!!.height}", palette.chapterColor(hue))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (meta.id == "img-collage" && collageMode != 1) {
                            PixelButton(text = "+ Pick Photos (${multiBmps.size})", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            if (multiBmps.isNotEmpty()) PixelChip(label = "Clear", onClick = { multiBmps.clear(); previewBmp = null; similarityReport = "" })
                        } else {
                            PixelButton(text = if (sourceBmp == null) "Pick Image" else "Change Image", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                        if (meta.id == "img-compress") {
                            PixelChip(label = "+Batch (${multiBmps.size})", chapterHue = hue, onClick = {
                                multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                    }

                    if (compareSplit && sourceBmp != null && previewBmp != null) {
                        ImageBeforeAfterSlider(before = sourceBmp!!, after = previewBmp!!, chapterHue = hue)
                    } else {
                        previewBmp?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(), contentDescription = "Edited Preview", contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(8.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }

        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (meta.id) {
                    "img-filters" -> {
                        Text("QUICK PRESET PACKS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ImageStudioAdvanced.BUILTIN_PRESETS.forEach { pack ->
                                PixelChip(label = pack.name, chapterHue = hue, onClick = {
                                    filterChain.clear(); filterChain.addAll(pack.steps); redoStack.clear()
                                })
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("FILTER PIPELINE (22 FILTERS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ImageFilterType.entries.forEach { t ->
                                PixelChip(label = t.label, selected = selectedFilterType == t, chapterHue = hue, onClick = { selectedFilterType = t })
                            }
                        }
                        PixelSteppedSlider("Intensity", filterIntensity, 5..100, 5, "%", hue, { filterIntensity = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelButton(text = "+ Add", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                filterChain.add(ImageFilterStep(selectedFilterType, filterIntensity / 100f)); redoStack.clear()
                            })
                            PixelChip(label = "Undo", onClick = {
                                if (filterChain.isNotEmpty()) redoStack.add(filterChain.removeAt(filterChain.lastIndex))
                            })
                            PixelChip(label = "Redo", onClick = {
                                if (redoStack.isNotEmpty()) filterChain.add(redoStack.removeAt(redoStack.lastIndex))
                            })
                            if (filterChain.isNotEmpty()) {
                                PixelChip(label = if (showChainQr) "Hide QR" else "QR", selected = showChainQr, chapterHue = hue, onClick = { showChainQr = !showChainQr })
                            }
                        }
                        if (filterChain.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                filterChain.forEachIndexed { idx, step ->
                                    PixelChip(label = "${idx + 1}. ${step.type.label} ${(step.intensity * 100).toInt()}% ✕", chapterHue = hue, onClick = {
                                        filterChain.removeAt(idx)
                                    })
                                }
                            }
                        }
                        if (showChainQr && chainJson.isNotEmpty()) {
                            val qr = remember(chainJson) { ShareMediaUtils.generateQrBitmap(chainJson, 320) }
                            Image(bitmap = qr.asImageBitmap(), contentDescription = "Filter Preset QR", modifier = Modifier.size(150.dp).align(Alignment.CenterHorizontally))
                        }
                    }

                    "img-crop" -> {
                        Text("GEOMETRY, BORDER & PASSPORT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip(label = "Rotate 90° ($rotateDeg°)", chapterHue = hue, onClick = { rotateDeg = (rotateDeg + 90) % 360 })
                            PixelChip(label = "Flip H", selected = flipH, chapterHue = hue, onClick = { flipH = !flipH })
                            PixelChip(label = "Flip V", selected = flipV, chapterHue = hue, onClick = { flipV = !flipV })
                            PixelChip(label = "Circle", selected = circleCrop, chapterHue = hue, onClick = { circleCrop = !circleCrop })
                            PixelChip(label = "Passport 2×3 Sheet", selected = passportMode, chapterHue = hue, onClick = { passportMode = !passportMode })
                        }
                        if (!passportMode) {
                            PixelSteppedSlider("Straighten Angle", straightenDeg, -45..45, 1, "°", hue, { straightenDeg = it })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Free" to null, "1:1" to 1f, "4:3" to 4f / 3f, "16:9" to 16f / 9f, "9:16" to 9f / 16f).forEach { pair ->
                                    PixelChip(label = pair.first, selected = aspectChoice.first == pair.first, chapterHue = hue, onClick = { aspectChoice = pair })
                                }
                            }
                            PixelSwitch("Pad with Blurred Background", padWithBlur, { padWithBlur = it }, hue)
                            PixelSteppedSlider("Round Corners", cornerPct, 0..100, 5, "%", hue, { cornerPct = it })
                            PixelSteppedSlider("Frame Border Width", borderPx, 0..64, 4, "px", hue, { borderPx = it })
                            PixelSteppedSlider("Expand Canvas Padding", expandPct, 0..40, 5, "%", hue, { expandPct = it })
                        }
                    }

                    "img-resize" -> {
                        Text("DIMENSIONS, SOCIAL PRESETS & WEB PACKS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelSwitch("Lock Aspect Ratio", keepAspect, { keepAspect = it }, hue)
                        PixelSwitch("2× Sharpened Upscale", upscale2x, { upscale2x = it }, hue)
                        PixelSteppedSlider("Scale % (when Width = 0)", scalePct, 25..300, 25, "%", hue, { scalePct = it; targetW = "0" })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelNumberField(value = targetW, onValueChange = { targetW = it }, placeholder = "Width px", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(1f))
                            if (!keepAspect) PixelNumberField(value = targetH, onValueChange = { targetH = it }, placeholder = "Height px", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(1f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("IG 1080²" to (1080 to 1080), "Story 9:16" to (1080 to 1920), "YT 720p" to (1280 to 720), "X 1500×500" to (1500 to 500)).forEach { (lbl, wh) ->
                                PixelChip(label = lbl, chapterHue = hue, onClick = { keepAspect = false; targetW = wh.first.toString(); targetH = wh.second.toString() })
                            }
                        }
                        if (sourceBmp != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = "Favicon ZIP Pack", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    zipSaveLauncher.launch("favicons_pack_${System.currentTimeMillis() / 1000}.zip")
                                })
                                PixelButton(text = "Responsive srcset ZIP", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    val b = previewBmp ?: return@PixelButton
                                    scope.launch(Dispatchers.IO) {
                                        val baos = ByteArrayOutputStream()
                                        ImageStudioAdvanced.buildSrcsetZip(b, renamePrefix, exportFmt, exportQuality, baos)
                                        val file = ShareMediaUtils.writeTempFile(context, "srcset_pack.zip", baos.toByteArray())
                                        ShareMediaUtils.shareFile(context, file, "application/zip", "Share Responsive Image Set")
                                    }
                                })
                            }
                        }
                    }

                    "img-compress" -> {
                        Text("FORMAT, TARGET KB & BATCH ZIP", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExportImageFormat.entries.forEach { fmt ->
                                PixelChip(label = fmt.label, selected = exportFmt == fmt, chapterHue = hue, onClick = { exportFmt = fmt }, modifier = Modifier.weight(1f))
                            }
                        }
                        PixelSteppedSlider("Quality", exportQuality, 10..100, 5, "%", hue, { exportQuality = it; targetKbText = "0" })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelNumberField(value = targetKbText, onValueChange = { targetKbText = it }, placeholder = "Target KB (0=off)", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelTextField(value = renamePrefix, onValueChange = { renamePrefix = it }, placeholder = "Batch filename prefix", chapterHue = hue, modifier = Modifier.weight(1f))
                        }
                        if (estimatedBytes > 0) Text("Estimated Size: ${estimatedBytes / 1024} KB ($estimatedBytes B)", style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                        if (multiBmps.isNotEmpty() || previewBmp != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = if (multiBmps.isNotEmpty()) "Batch ZIP (${multiBmps.size})" else "Export ZIP", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    zipSaveLauncher.launch("${renamePrefix}_batch.zip")
                                })
                                if (previewBmp != null) {
                                    PixelButton(text = "Export to PDF", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                        pdfExportLauncher.launch("${renamePrefix}.pdf")
                                    })
                                }
                            }
                        }
                    }

                    "img-draw" -> {
                        Text("DRAWING, PRIVACY CENSOR & MEME MAKER", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelButton(text = "Load Photo", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                            PixelChip(label = "Undo", onClick = { if (drawStrokes.isNotEmpty()) drawStrokes.removeAt(drawStrokes.lastIndex) })
                            PixelChip(label = "Clear", onClick = { drawStrokes.clear() })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Color(0xFFFF4F00) to "Orange", Color(0xFF0052FF) to "Blue", Color(0xFF141413) to "Black", Color(0xFF0E9F6E) to "Green", Color.White to "Eraser").forEach { (col, lbl) ->
                                PixelChip(label = lbl, selected = brushColor == col, chapterHue = hue, onClick = { brushColor = col })
                            }
                        }
                        PixelSwitch("Thick Privacy Censor / Redact Brush", censorMode, { censorMode = it }, hue)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(value = memeTop, onValueChange = { memeTop = it }, placeholder = "Meme Top Text", chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelTextField(value = memeBottom, onValueChange = { memeBottom = it }, placeholder = "Meme Bottom Text", chapterHue = hue, modifier = Modifier.weight(1f))
                        }
                        Canvas(
                            modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, palette.hairline, RoundedCornerShape(8.dp))
                                .pointerInput(brushColor, censorMode) {
                                    detectDragGestures(
                                        onDragStart = { currentStroke = listOf(it) },
                                        onDrag = { ch, _ -> ch.consume(); currentStroke = currentStroke + ch.position },
                                        onDragEnd = { if (currentStroke.isNotEmpty()) drawStrokes.add(currentStroke to (if (censorMode) Color.Black else brushColor)); currentStroke = emptyList() }
                                    )
                                }
                        ) {
                            sourceBmp?.let { drawImage(it.asImageBitmap(), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())) }
                            fun drawOne(pts: List<Offset>, col: Color) {
                                if (pts.size < 2) return
                                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                                drawPath(path, col, style = Stroke(width = if (censorMode) 36f else 10f, cap = StrokeCap.Round))
                            }
                            drawStrokes.forEach { (pts, col) -> drawOne(pts, col) }
                            drawOne(currentStroke, if (censorMode) Color.Black else brushColor)
                        }
                        PixelButton(text = "Render Drawing / Meme & Share PNG", chapterHue = hue, onClick = {
                            val w = 1080; val h = 800
                            var out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val c = android.graphics.Canvas(out)
                            c.drawColor(AColor.WHITE)
                            sourceBmp?.let { c.drawBitmap(Bitmap.createScaledBitmap(it, w, h, true), 0f, 0f, null) }
                            drawStrokes.forEach { (pts, col) ->
                                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = col.toArgb(); strokeWidth = if (censorMode) 48f else 14f; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND
                                }
                                for (i in 1 until pts.size) c.drawLine(pts[i - 1].x * 1.2f, pts[i - 1].y * 1.2f, pts[i].x * 1.2f, pts[i].y * 1.2f, p)
                            }
                            if (memeTop.isNotBlank() || memeBottom.isNotBlank()) out = ImageStudioAdvanced.renderMemeOverlay(out, memeTop, memeBottom)
                            previewBmp = out
                            ShareMediaUtils.shareBitmapPng(context, out, "omnideck_meme")
                        })
                    }

                    "img-bg" -> {
                        Text("BACKGROUND REMOVE & REPLACE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Transparent", "Pure White", "Blur Bokeh", "Warm Paper").forEachIndexed { idx, lbl ->
                                PixelChip(label = lbl, selected = bgMode == idx, chapterHue = hue, onClick = { bgMode = idx })
                            }
                        }
                        PixelSteppedSlider("Edge Chroma Tolerance", bgTolerance, 10..120, 5, "", hue, { bgTolerance = it })
                        LaunchedEffect(Unit) { exportFmt = ExportImageFormat.PNG }
                    }

                    "img-collage" -> {
                        Text("COLLAGE, GRID SPLITTER, SPRITE SHEET & DUPLICATES", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Collage", "3×3 Grid Split", "Sprite Sheet", "Similarity dHash").forEachIndexed { idx, lbl ->
                                PixelChip(label = lbl, selected = collageMode == idx, chapterHue = hue, onClick = { collageMode = idx })
                            }
                        }
                        if (collageMode == 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StitchLayout.entries.forEach { l -> PixelChip(label = l.label, selected = stitchLayout == l, chapterHue = hue, onClick = { stitchLayout = l }, modifier = Modifier.weight(1f)) }
                            }
                        } else if (collageMode == 1 && sourceBmp != null) {
                            PixelButton(text = "Export 9 Grid Slices (ZIP)", chapterHue = hue, onClick = { zipSaveLauncher.launch("instagram_grid_3x3.zip") })
                        } else if (collageMode == 3) {
                            Text("Pick 2+ images above to compute perceptual 64-bit dHash similarity & detect duplicates.", style = DotkitType.bodyUi(12), color = palette.ink2)
                        }
                    }
                }

                if (previewBmp != null && meta.id != "img-draw") {
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton(text = "Save ${exportFmt.label}", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                            saveLauncher.launch("${meta.id}_${System.currentTimeMillis() / 1000}.${exportFmt.ext}")
                        })
                        PixelButton(text = "Share PNG", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                            previewBmp?.let { ShareMediaUtils.shareBitmapPng(context, it, meta.id) }
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
