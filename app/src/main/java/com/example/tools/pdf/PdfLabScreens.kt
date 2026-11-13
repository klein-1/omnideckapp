package com.example.tools.pdf

import android.graphics.Bitmap
import android.graphics.RectF
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.lib.media.ImageStudioEngine
import com.example.lib.media.PdfDeepAudit
import com.example.lib.media.PdfInspectResult
import com.example.lib.media.PdfLabAdvanced
import com.example.lib.media.PdfLabEngine
import com.example.lib.media.PdfPageSizePreset
import com.example.lib.media.PdfPageTransform
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfLabWorkbench(
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
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.MARIGOLD

    val sourceUris = remember { mutableStateListOf<Uri>() }
    val pageThumbs = remember { mutableStateListOf<Pair<PdfPageTransform, Bitmap>>() }
    val selectedImages = remember { mutableStateListOf<Bitmap>() }

    var busy by remember { mutableStateOf(false) }
    var compressDpi by remember { mutableIntStateOf(120) }
    var compressQuality by remember { mutableIntStateOf(72) }
    var targetKbText by remember { mutableStateOf("0") }
    var sourceByteSize by remember { mutableStateOf(0L) }

    // Split / Organize / Booklet / N-up
    var splitRangeInput by remember { mutableStateOf("") }
    var splitEveryN by remember { mutableIntStateOf(2) }
    var nUpChoice by remember { mutableIntStateOf(1) }
    var pageSizePreset by remember { mutableStateOf(PdfPageSizePreset.ORIGINAL) }
    var marginPct by remember { mutableIntStateOf(0) }
    var skipBlankPages by remember { mutableStateOf(false) }

    // Convert Text/MD -> PDF mode
    var textDocTitle by remember { mutableStateOf("Document Draft") }
    var textDocBody by remember { mutableStateOf("") }

    // Annotate / Sign / Header / Bates / Whitewash
    var watermarkText by remember { mutableStateOf("") }
    var headerText by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("") }
    var batesPrefix by remember { mutableStateOf("") }
    var stampLabel by remember { mutableStateOf("") }
    var includePageNums by remember { mutableStateOf(true) }
    var whitewashBg by remember { mutableStateOf(false) }
    var redactTopPct by remember { mutableIntStateOf(0) }
    var redactHeightPct by remember { mutableIntStateOf(0) }
    var highlightTopPct by remember { mutableIntStateOf(0) }
    var includeSignature by remember { mutableStateOf(false) }
    val sigStrokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Inspect / Audit / Compare 2 PDFs
    var inspectRes by remember { mutableStateOf<PdfInspectResult?>(null) }
    var deepAudit by remember { mutableStateOf<PdfDeepAudit?>(null) }
    var compareTextDiff by remember { mutableStateOf("") }
    var lastExportedPdf by remember { mutableStateOf<File?>(null) }

    suspend fun reloadPages(uris: List<Uri>) {
        busy = true
        val loaded = withContext(Dispatchers.IO) {
            uris.firstOrNull()?.let { u ->
                sourceByteSize = context.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } ?: 0L
            }
            uris.flatMapIndexed { docIdx, uri ->
                val thumbs = PdfLabEngine.renderPdfPages(context, uri, maxThumbWidth = 480, limitPages = 40)
                thumbs.mapIndexed { pageIdx, bmp -> PdfPageTransform(docIdx, pageIdx, 0, false) to bmp }
            }
        }
        pageThumbs.clear(); pageThumbs.addAll(loaded); busy = false
    }

    LaunchedEffect(initialUri, meta.id) {
        if (initialUri != null && sourceUris.isEmpty()) {
            sourceUris.add(initialUri)
            if (meta.id == "pdf-text") {
                val res = withContext(Dispatchers.IO) { PdfLabEngine.inspectAndExtractText(context, initialUri) }
                inspectRes = res
                deepAudit = withContext(Dispatchers.IO) { PdfLabAdvanced.deepAuditAndExtract(context, initialUri, res) }
            } else reloadPages(sourceUris.toList())
        }
    }

    val singlePdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            sourceUris.clear(); sourceUris.add(uri)
            scope.launch {
                if (meta.id == "pdf-text") {
                    busy = true
                    val res = withContext(Dispatchers.IO) { PdfLabEngine.inspectAndExtractText(context, uri) }
                    inspectRes = res
                    deepAudit = withContext(Dispatchers.IO) { PdfLabAdvanced.deepAuditAndExtract(context, uri, res) }
                    busy = false
                } else reloadPages(sourceUris.toList())
            }
        }
    }

    val compareSecondPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri2 ->
        val firstRes = inspectRes ?: return@rememberLauncherForActivityResult
        if (uri2 != null) {
            scope.launch {
                val res2 = withContext(Dispatchers.IO) { PdfLabEngine.inspectAndExtractText(context, uri2) }
                compareTextDiff = PdfLabAdvanced.diffPdfTextStreams(firstRes.extractedText, res2.extractedText)
            }
        }
    }

    val multiPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { sourceUris.addAll(uris); scope.launch { reloadPages(sourceUris.toList()) } }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(24)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { uris.mapNotNull { ImageStudioEngine.decodeBitmap(context, it, 1240) } }
                selectedImages.addAll(decoded)
            }
        }
    }

    fun buildSigBitmap(): Bitmap? {
        if (!includeSignature || sigStrokes.isEmpty()) return null
        val bmp = Bitmap.createBitmap(480, 180, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; strokeWidth = 6f; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND
        }
        sigStrokes.forEach { pts -> for (i in 1 until pts.size) c.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, p) }
        return bmp
    }

    fun buildCurrentPlan(): List<PdfPageTransform> {
        val all = pageThumbs.map { it.first }
        if (meta.id == "pdf-split" && splitRangeInput.isNotBlank()) {
            val wanted = splitRangeInput.split(",").flatMap { part ->
                val t = part.trim()
                if ("-" in t) {
                    val a = t.substringBefore("-").toIntOrNull() ?: 1
                    val b = t.substringAfter("-").toIntOrNull() ?: a
                    (a..b).toList()
                } else listOfNotNull(t.toIntOrNull())
            }.map { it - 1 }.toSet()
            return all.filterIndexed { idx, p -> !p.deleted && idx in wanted }
        }
        return all.filter { !it.deleted }
    }

    fun writeTransformedPdf(outStream: OutputStream) {
        val plan = buildCurrentPlan()
        val width = if (meta.id == "pdf-compress") compressDpi * 8 else 1240
        val qual = if (meta.id == "pdf-compress") compressQuality else 90
        val tKb = targetKbText.toIntOrNull() ?: 0
        if (meta.id == "pdf-compress" && tKb > 0) {
            val (bytes, _, _) = PdfLabAdvanced.compressToTargetKb(context, sourceUris.toList(), plan, tKb)
            outStream.write(bytes)
            return
        }
        val redactions = if (meta.id == "pdf-annotate" && redactHeightPct > 0) {
            listOf(RectF(0.08f, redactTopPct / 100f, 0.92f, ((redactTopPct + redactHeightPct) / 100f).coerceAtMost(0.98f)))
        } else emptyList()
        val highlights = if (meta.id == "pdf-annotate" && highlightTopPct > 0) {
            listOf(RectF(0.08f, highlightTopPct / 100f, 0.92f, ((highlightTopPct + 6) / 100f).coerceAtMost(0.98f)))
        } else emptyList()
        PdfLabAdvanced.buildImposedOrStyledPdf(
            context = context, sourceUris = sourceUris.toList(), plan = plan, renderWidth = width, jpegQuality = qual,
            nUp = nUpChoice, pageSize = pageSizePreset, marginPct = marginPct, headerText = headerText, footerText = footerText,
            batesPrefix = batesPrefix, batesStart = 1, stampLabel = stampLabel, watermarkText = watermarkText,
            includePageNumbers = includePageNums, skipBlankPages = skipBlankPages, whitewashBg = whitewashBg,
            redactions = redactions, highlightBoxes = highlights, signatureBmp = if (meta.id == "pdf-annotate") buildSigBitmap() else null,
            out = outStream
        )
    }

    val savePdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        when {
                            meta.id == "pdf-convert" && selectedImages.isNotEmpty() -> PdfLabEngine.bitmapsToPdf(selectedImages.toList(), out, compressQuality)
                            meta.id == "pdf-convert" && textDocBody.isNotBlank() -> PdfLabAdvanced.textToPdf(textDocTitle, textDocBody, out)
                            else -> writeTransformedPdf(out)
                        }
                    }
                }
                busy = false; onStatusToast("Exported PDF successfully.")
            }
        }
    }

    val saveZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        if (meta.id == "pdf-split" && sourceUris.isNotEmpty()) {
                            PdfLabAdvanced.splitEveryNToZip(context, sourceUris.first(), splitEveryN, out)
                        } else if (pageThumbs.isNotEmpty()) {
                            PdfLabAdvanced.exportPagesToPngZip(pageThumbs.map { it.second }, out)
                        }
                    }
                }
                busy = false; onStatusToast("Exported ZIP archive.")
            }
        }
    }

    val outputSummary = buildString {
        if (compareTextDiff.isNotBlank()) appendLine(compareTextDiff)
        inspectRes?.let { appendLine(it.extractedText) }
        deepAudit?.let { a ->
            if (a.links.isNotEmpty()) appendLine("\nLinks Found (${a.links.size}):\n" + a.links.joinToString("\n"))
            if (a.bookmarks.isNotEmpty()) appendLine("\nBookmarks:\n" + a.bookmarks.joinToString("\n"))
        }
    }.trim()

    ToolWorkbenchChrome(
        meta = meta, isPinned = isPinned, onTogglePin = onTogglePin, onBack = onBack,
        outputText = outputSummary, outputFileName = "pdf_extract_${System.currentTimeMillis() / 1000}.txt",
        outputKind = DataKind.TEXT, onSaveToFiles = onSaveToFiles, onSendToTool = onSendToTool,
        onStatusToast = onStatusToast, showOutputBar = meta.id == "pdf-text" && outputSummary.isNotBlank(),
        helpParagraph = "PaperKnife-parity local PDF studio: merge PDFs, visual organize/duplicate/rotate/split-every-N-ZIP, N-up 2-up/4-up imposition, target-KB compression, images & Text/Markdown ↔ PDF, header/footer/Bates/stamps/highlight/redact/sign, PDF/A audit, 2-PDF diff & system Print."
    ) {
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DOCUMENT INPUT", style = DotkitType.monoLabel(10), color = palette.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (meta.id) {
                        "pdf-merge" -> {
                            PixelButton(text = "+ Add PDFs (${sourceUris.size})", chapterHue = hue, onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) })
                            if (sourceUris.isNotEmpty()) PixelChip(label = "Clear All", onClick = { sourceUris.clear(); pageThumbs.clear() })
                        }
                        "pdf-convert" -> {
                            PixelButton(text = "+ Pick Photos (${selectedImages.size})", chapterHue = hue, onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            PixelButton(text = "PDF → PNGs", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { selectedImages.clear(); singlePdfPicker.launch(arrayOf("application/pdf")) })
                        }
                        else -> {
                            PixelButton(text = if (sourceUris.isEmpty()) "Pick PDF" else "Change PDF", chapterHue = hue, onClick = { singlePdfPicker.launch(arrayOf("application/pdf")) })
                            if (meta.id == "pdf-text" && inspectRes != null) {
                                PixelButton(text = "Compare 2nd PDF", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = { compareSecondPdfPicker.launch(arrayOf("application/pdf")) })
                            }
                        }
                    }
                }
                if (busy) PixelStamp("PROCESSING PDF…", palette.chapterColor(hue))
            }
        }

        if (meta.id == "pdf-convert") {
            if (selectedImages.isNotEmpty()) {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("IMAGES QUEUED FOR PDF (${selectedImages.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(selectedImages) { idx, bmp ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Img ${idx + 1}", contentScale = ContentScale.Crop, modifier = Modifier.size(90.dp).border(1.dp, palette.hairline))
                                    Text("#${idx + 1}", style = DotkitType.monoLabel(10), color = palette.ink2)
                                }
                            }
                        }
                        PixelButton(text = "Create & Save PDF", chapterHue = hue, onClick = { savePdfLauncher.launch("images_to_pdf_${System.currentTimeMillis() / 1000}.pdf") })
                    }
                }
            }
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TEXT / MARKDOWN / CSV → PDF PRESS", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(value = textDocTitle, onValueChange = { textDocTitle = it }, placeholder = "PDF Document Title", chapterHue = hue)
                    PixelTextField(value = textDocBody, onValueChange = { textDocBody = it }, placeholder = "Paste prose, Markdown notes or CSV rows…", singleLine = false, minHeight = 110.dp, chapterHue = hue)
                    PixelButton(text = "Render Text to PDF", chapterHue = hue, enabled = textDocBody.isNotBlank(), onClick = { savePdfLauncher.launch("text_doc_${System.currentTimeMillis() / 1000}.pdf") })
                }
            }
        }

        if (pageThumbs.isNotEmpty()) {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PAGE STRIP (${pageThumbs.count { !it.first.deleted }} ACTIVE / ${pageThumbs.size} TOTAL)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(pageThumbs) { idx, (step, bmp) ->
                            Column(
                                modifier = Modifier.width(142.dp).clip(RoundedCornerShape(8.dp)).background(if (step.deleted) palette.paper3 else palette.paper).border(1.dp, if (step.deleted) palette.err else palette.hairline, RoundedCornerShape(8.dp)).padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Page ${idx + 1}", contentScale = ContentScale.Fit, modifier = Modifier.height(145.dp).fillMaxWidth())
                                Text("P.${idx + 1} (${step.rotationDeg}°)", style = DotkitType.monoLabel(10), color = palette.ink)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PixelChip(label = "↻", onClick = { pageThumbs[idx] = step.copy(rotationDeg = (step.rotationDeg + 90) % 360) to bmp })
                                    PixelChip(label = "Dup", onClick = { pageThumbs.add(idx + 1, step to bmp) })
                                    PixelChip(label = if (step.deleted) "Restore" else "✕", onClick = { pageThumbs[idx] = step.copy(deleted = !step.deleted) to bmp })
                                    if (idx > 0) PixelChip(label = "←", onClick = { val cur = pageThumbs.removeAt(idx); pageThumbs.add(idx - 1, cur) })
                                }
                                if (meta.id == "pdf-convert") PixelChip(label = "Share PNG", chapterHue = hue, onClick = { ShareMediaUtils.shareBitmapPng(context, bmp, "pdf_p${idx + 1}") })
                            }
                        }
                    }

                    if (meta.id == "pdf-convert") {
                        PixelButton(text = "Export All Pages as PNG ZIP", chapterHue = hue, onClick = { saveZipLauncher.launch("pdf_pages_png.zip") })
                    }

                    if (meta.id == "pdf-split") {
                        PixelTextField(value = splitRangeInput, onValueChange = { splitRangeInput = it }, placeholder = "Page filter e.g. 1-3, 5 (blank = keep active)", chapterHue = hue)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelChip(label = "Odd Pages", onClick = { splitRangeInput = (1..pageThumbs.size step 2).joinToString(",") })
                            PixelChip(label = "Even Pages", onClick = { splitRangeInput = (2..pageThumbs.size step 2).joinToString(",") })
                            PixelChip(label = "All", onClick = { splitRangeInput = "" })
                        }
                        PixelSteppedSlider("Split Every N Pages → ZIP", splitEveryN, 1..maxOf(2, pageThumbs.size), 1, " pg", hue, { splitEveryN = it })
                        PixelButton(text = "Split Every $splitEveryN Page(s) → ZIP", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                            saveZipLauncher.launch("split_every_${splitEveryN}.zip")
                        })
                    }

                    if (meta.id == "pdf-compress") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Screen 72" to (72 to 55), "Ebook 120" to (120 to 72), "Print 180" to (180 to 88)).forEach { (lbl, dq) ->
                                PixelChip(label = lbl, selected = compressDpi == dq.first, chapterHue = hue, onClick = { compressDpi = dq.first; compressQuality = dq.second; targetKbText = "0" })
                            }
                        }
                        PixelSteppedSlider("Render DPI", compressDpi, 72..220, 16, " DPI", hue, { compressDpi = it; targetKbText = "0" })
                        PixelSteppedSlider("JPEG Quality", compressQuality, 30..95, 5, "%", hue, { compressQuality = it; targetKbText = "0" })
                        PixelNumberField(value = targetKbText, onValueChange = { targetKbText = it }, placeholder = "Target Size KB (0 = manual DPI/Quality)", allowDecimal = false, chapterHue = hue)
                        if (sourceByteSize > 0) Text("Original Size: ${sourceByteSize / 1024} KB", style = DotkitType.monoLabel(10), color = palette.ink2)
                    }

                    if (meta.id == "pdf-annotate" || meta.id == "pdf-merge" || meta.id == "pdf-split") {
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("LAYOUT, IMPOSITION & MARGINS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1 to "1-Up", 2 to "2-Up", 4 to "4-Up Grid").forEach { (n, lbl) ->
                                PixelChip(label = lbl, selected = nUpChoice == n, chapterHue = hue, onClick = { nUpChoice = n })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PdfPageSizePreset.entries.forEach { ps ->
                                PixelChip(label = ps.label, selected = pageSizePreset == ps, chapterHue = hue, onClick = { pageSizePreset = ps })
                            }
                        }
                        PixelSteppedSlider("Margin / Crop Inset", marginPct, 0..20, 2, "%", hue, { marginPct = it })
                        PixelSwitch("Auto-Skip Blank Pages", skipBlankPages, { skipBlankPages = it }, hue)
                    }

                    if (meta.id == "pdf-annotate") {
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("STAMPS, HEADERS, BATES, HIGHLIGHT, REDACT & SIGN", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("" to "No Stamp", "APPROVED" to "APPROVED", "CONFIDENTIAL" to "CONFIDENTIAL", "DRAFT" to "DRAFT").forEach { (code, lbl) ->
                                PixelChip(label = lbl, selected = stampLabel == code, chapterHue = hue, onClick = { stampLabel = code })
                            }
                        }
                        PixelTextField(value = watermarkText, onValueChange = { watermarkText = it }, placeholder = "Diagonal watermark text (optional)", chapterHue = hue)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(value = headerText, onValueChange = { headerText = it }, placeholder = "Header", chapterHue = hue, modifier = Modifier.weight(1f))
                            PixelTextField(value = batesPrefix, onValueChange = { batesPrefix = it }, placeholder = "Bates e.g. DOC-", chapterHue = hue, modifier = Modifier.weight(1f))
                        }
                        PixelTextField(value = footerText, onValueChange = { footerText = it }, placeholder = "Footer text (optional)", chapterHue = hue)
                        PixelSwitch("Stamp Page Numbers", includePageNums, { includePageNums = it }, hue)
                        PixelSwitch("Whitewash / Scan Contrast Cleanup", whitewashBg, { whitewashBg = it }, hue)
                        PixelSteppedSlider("Highlight Band Y%", highlightTopPct, 0..90, 5, "%", hue, { highlightTopPct = it })
                        PixelSteppedSlider("Blackout Redaction Y%", redactTopPct, 0..85, 5, "%", hue, { redactTopPct = it })
                        PixelSteppedSlider("Redaction Height%", redactHeightPct, 0..40, 5, "%", hue, { redactHeightPct = it })
                        PixelSwitch("Attach Hand-Drawn Signature", includeSignature, { includeSignature = it }, hue)
                        if (includeSignature) {
                            Canvas(
                                modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { p -> activeStroke = listOf(p) },
                                            onDrag = { change, _ -> change.consume(); activeStroke = activeStroke + change.position },
                                            onDragEnd = { if (activeStroke.isNotEmpty()) sigStrokes.add(activeStroke); activeStroke = emptyList() }
                                        )
                                    }
                            ) {
                                fun drawPts(pts: List<Offset>) {
                                    if (pts.size < 2) return
                                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                                    drawPath(path, Color.Black, style = Stroke(width = 6f, cap = StrokeCap.Round))
                                }
                                sigStrokes.forEach { drawPts(it) }; drawPts(activeStroke)
                            }
                            PixelChip(label = "Clear Signature", onClick = { sigStrokes.clear() })
                        }
                    }

                    if (meta.id != "pdf-convert" && meta.id != "pdf-text") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(text = "Export PDF", chapterHue = hue, onClick = { savePdfLauncher.launch("${meta.id}_${System.currentTimeMillis() / 1000}.pdf") })
                            PixelButton(text = "Share PDF", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                scope.launch {
                                    busy = true
                                    val file = withContext(Dispatchers.IO) {
                                        val f = File(context.cacheDir, "omnideck_${meta.id}_${System.currentTimeMillis()}.pdf")
                                        FileOutputStream(f).use { writeTransformedPdf(it) }; f
                                    }
                                    busy = false; lastExportedPdf = file
                                    ShareMediaUtils.shareFile(context, file, "application/pdf", "Share PDF")
                                }
                            })
                            PixelButton(text = "Print PDF", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                scope.launch {
                                    busy = true
                                    val file = withContext(Dispatchers.IO) {
                                        val f = File(context.cacheDir, "omnideck_print_${System.currentTimeMillis()}.pdf")
                                        FileOutputStream(f).use { writeTransformedPdf(it) }; f
                                    }
                                    busy = false; lastExportedPdf = file
                                    PdfLabAdvanced.printPdfFile(context, file, meta.name)
                                }
                            })
                        }
                        lastExportedPdf?.let { f ->
                            val savedNote = if (sourceByteSize > 0) " (was ${sourceByteSize / 1024} KB)" else ""
                            Text("Built PDF: ${f.length() / 1024} KB$savedNote", style = DotkitType.monoLabel(10), color = palette.ink2)
                        }
                    }
                }
            }
        }

        inspectRes?.let { res ->
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PDF AUDIT, METADATA & EXTRACTED TEXT", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Text("Pages: ${res.pageCount} · Size: ${res.byteSize / 1024} KB · Words: ${deepAudit?.wordCount ?: 0}", style = DotkitType.monoData(12), color = palette.ink)
                    if (res.title.isNotBlank() || res.author.isNotBlank() || res.producer.isNotBlank()) {
                        Text("Title: ${res.title} | Author: ${res.author} | Producer: ${res.producer}", style = DotkitType.monoLabel(10), color = palette.ink2)
                    }
                    deepAudit?.checklist?.let { checks ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            checks.forEach { (lbl, ok) -> PixelStamp("${if (ok) "✓" else "✕"} $lbl", if (ok) palette.ok else palette.warn) }
                        }
                    }
                    if (sourceUris.isNotEmpty()) {
                        PixelButton(text = "Export Metadata-Scrubbed Clean Copy", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                            scope.launch {
                                busy = true
                                val plan = (0 until res.pageCount.coerceAtMost(40)).map { PdfPageTransform(0, it, 0, false) }
                                val file = withContext(Dispatchers.IO) {
                                    val f = File(context.cacheDir, "scrubbed_${System.currentTimeMillis()}.pdf")
                                    FileOutputStream(f).use { PdfLabEngine.buildTransformedPdf(context, sourceUris.toList(), plan, 1240, 90, out = it) }
                                    f
                                }
                                busy = false
                                ShareMediaUtils.shareFile(context, file, "application/pdf", "Share Scrubbed PDF")
                            }
                        })
                    }
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Text(outputSummary, style = DotkitType.monoData(11), color = palette.ink)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
