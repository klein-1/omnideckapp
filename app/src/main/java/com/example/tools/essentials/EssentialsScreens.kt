package com.example.tools.essentials

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSteppedSlider
import com.example.design.PixelTextField
import com.example.lib.media.ImageStudioEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EssentialsExtendedScreen(
    meta: ToolMeta,
    initialText: String,
    onLogNetworkAudit: (toolId: String, host: String, outB: Long, inB: Long, note: String) -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalDotkitLook.current.palette
    val scope = rememberCoroutineScope()
    val hue = ChapterHue.TANGERINE

    // Flashlight state
    var torchMode by remember { mutableStateOf("OFF") } // OFF, STEADY, SOS, STROBE
    var strobeHz by remember { mutableIntStateOf(6) }
    var screenLightFull by remember { mutableStateOf(false) }

    // QR Scanner state
    var hasCameraPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var scannedCode by remember { mutableStateOf("") }
    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPerm = granted
    }
    val qrPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bmp = ImageStudioEngine.decodeBitmap(context, uri, 1024) ?: return@launch
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
                val decoded = runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (decoded != null) scannedCode = decoded else onStatusToast("No QR or barcode detected in image.")
                }
            }
        }
    }

    // Dictionary state
    var dictWord by remember(initialText) { mutableStateOf(initialText.ifBlank { "precision" }) }
    var dictLoading by remember { mutableStateOf(false) }
    var dictResult by remember { mutableStateOf("") }

    // Translator state
    var transText by remember(initialText) { mutableStateOf(initialText) }
    var sourceLang by remember { mutableStateOf("en") }
    var targetLang by remember { mutableStateOf("es") }
    var transOutput by remember { mutableStateOf("") }
    var transBusy by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    val sttLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) transText = spoken
        }
    }

    if (meta.id == "ess-translate") {
        DisposableEffect(Unit) {
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale(targetLang)
                }
            }
            ttsEngine = tts
            onDispose {
                tts.stop()
                tts.shutdown()
                ttsEngine = null
            }
        }
    }

    // Flashlight CameraManager Torch controller
    if (meta.id == "ess-flash") {
        val cm = remember { context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager }
        val torchCamId = remember(cm) {
            cm?.cameraIdList?.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }
        LaunchedEffect(torchMode, strobeHz, torchCamId) {
            if (torchCamId == null || cm == null) return@LaunchedEffect
            try {
                when (torchMode) {
                    "OFF" -> cm.setTorchMode(torchCamId, false)
                    "STEADY" -> cm.setTorchMode(torchCamId, true)
                    "STROBE" -> {
                        val halfPeriod = (500L / strobeHz.coerceIn(1, 20)).coerceAtLeast(25L)
                        while (torchMode == "STROBE") {
                            cm.setTorchMode(torchCamId, true); delay(halfPeriod)
                            cm.setTorchMode(torchCamId, false); delay(halfPeriod)
                        }
                    }
                    "SOS" -> {
                        val unit = 160L
                        val pattern = listOf(1, 1, 1, 3, 3, 3, 1, 1, 1)
                        while (torchMode == "SOS") {
                            for (p in pattern) {
                                if (torchMode != "SOS") break
                                cm.setTorchMode(torchCamId, true); delay(unit * p)
                                cm.setTorchMode(torchCamId, false); delay(unit)
                            }
                            delay(unit * 4)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        DisposableEffect(torchCamId) {
            onDispose {
                if (torchCamId != null) runCatching { cm?.setTorchMode(torchCamId, false) }
            }
        }
    }

    val outputText = when (meta.id) {
        "ess-qrscan" -> scannedCode
        "ess-dict" -> dictResult
        "ess-translate" -> transOutput
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
        showOutputBar = meta.id != "ess-flash" && outputText.isNotBlank(),
        helpParagraph = meta.tagline
    ) {
        when (meta.id) {
            "ess-flash" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("FLASHLIGHT & BEACON MODES", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp(torchMode, if (torchMode == "OFF") palette.ink3 else palette.chapterColor(hue))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("OFF" to "Off", "STEADY" to "Steady Torch", "SOS" to "SOS Morse", "STROBE" to "Strobe").forEach { (id, lbl) ->
                                PixelChip(label = lbl, selected = torchMode == id, chapterHue = hue, onClick = { torchMode = id })
                            }
                        }
                        if (torchMode == "STROBE") {
                            PixelSteppedSlider("Strobe Frequency", strobeHz, 1..20, 1, " Hz", hue, { strobeHz = it })
                        }
                        PixelButton(text = "Fullscreen White Screen Light", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                            screenLightFull = true
                        })
                    }
                }
                if (screenLightFull) {
                    Dialog(onDismissRequest = { screenLightFull = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp), contentAlignment = Alignment.BottomCenter) {
                            PixelButton(text = "Close Screen Light", chapterHue = hue, onClick = { screenLightFull = false })
                        }
                    }
                }
            }

            "ess-qrscan" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!hasCameraPerm) {
                            Text("Camera permission enables live barcode/QR scanning. Or pick a screenshot/photo from gallery without camera permission.", style = DotkitType.bodyUi(13), color = palette.ink2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = "Enable Camera", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { camPermLauncher.launch(Manifest.permission.CAMERA) })
                                PixelButton(text = "Scan Image", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    qrPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                            }
                        } else {
                            val executor = remember { Executors.newSingleThreadExecutor() }
                            DisposableEffect(Unit) { onDispose { executor.shutdown() } }
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val provider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                        val reader = MultiFormatReader()
                                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { ia ->
                                            ia.setAnalyzer(executor) { image ->
                                                try {
                                                    val plane = image.planes[0]
                                                    val buf = plane.buffer
                                                    val bytes = ByteArray(buf.remaining())
                                                    buf.get(bytes)
                                                    val src = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                                                    val res = reader.decode(BinaryBitmap(HybridBinarizer(src)))
                                                    scannedCode = res.text
                                                } catch (_: Exception) {
                                                } finally {
                                                    image.close()
                                                }
                                            }
                                        }
                                        runCatching {
                                            provider.unbindAll()
                                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                            )
                            PixelButton(text = "Or Scan From Photo Gallery", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.fillMaxWidth(), onClick = {
                                qrPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }

                        if (scannedCode.isNotBlank()) {
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text("DECODED PAYLOAD", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text(scannedCode, style = DotkitType.monoData(13), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip(label = "Copy", chapterHue = hue, onClick = { copyToClipboard(context, "QR", scannedCode); onStatusToast("Copied.") })
                                if (scannedCode.startsWith("http://") || scannedCode.startsWith("https://")) {
                                    PixelChip(label = "Check URL Safety →", chapterHue = hue, onClick = { onSendToTool("url-safety", scannedCode) })
                                    PixelChip(label = "Open URL", onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(scannedCode)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    })
                                }
                            }
                        }
                    }
                }
            }

            "ess-dict" -> {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PixelTextField(value = dictWord, onValueChange = { dictWord = it }, placeholder = "Enter English word…", chapterHue = hue)
                        PixelButton(text = if (dictLoading) "Looking up…" else "Define Word", chapterHue = hue, enabled = !dictLoading && dictWord.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                            dictLoading = true
                            scope.launch(Dispatchers.IO) {
                                val res = runCatching {
                                    val w = URLEncoder.encode(dictWord.trim(), "UTF-8")
                                    val req = Request.Builder().url("https://api.dictionaryapi.dev/api/v2/entries/en/$w").build()
                                    OkHttpClient().newCall(req).execute().use { resp ->
                                        val body = resp.body?.string().orEmpty()
                                        if (!resp.isSuccessful) return@use "No dictionary entry found for '$dictWord'."
                                        val entry = JSONArray(body).getJSONObject(0)
                                        val phonetic = entry.optString("phonetic", "")
                                        val meanings = entry.getJSONArray("meanings")
                                        buildString {
                                            appendLine("${entry.optString("word")}  $phonetic")
                                            for (i in 0 until minOf(meanings.length(), 4)) {
                                                val m = meanings.getJSONObject(i)
                                                appendLine("\n[${m.optString("partOfSpeech")}]")
                                                val defs = m.getJSONArray("definitions")
                                                for (j in 0 until minOf(defs.length(), 3)) {
                                                    appendLine("  • ${defs.getJSONObject(j).optString("definition")}")
                                                }
                                                val syns = m.optJSONArray("synonyms")
                                                if (syns != null && syns.length() > 0) {
                                                    val synList = (0 until minOf(syns.length(), 6)).map { syns.getString(it) }
                                                    appendLine("  Synonyms: ${synList.joinToString(", ")}")
                                                }
                                            }
                                        }.trim()
                                    }
                                }.getOrElse { "Lookup requires internet access (${it.message})." }
                                withContext(Dispatchers.Main) {
                                    dictResult = res
                                    dictLoading = false
                                    onLogNetworkAudit(meta.id, "api.dictionaryapi.dev", 64L, res.length.toLong(), "Defined '$dictWord'")
                                }
                            }
                        })
                        if (dictResult.isNotBlank()) {
                            Text(dictResult, style = DotkitType.bodyUi(13), color = palette.ink)
                        }
                    }
                }
            }

            "ess-translate" -> {
                val langs = listOf("en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "hi" to "Hindi", "ar" to "Arabic", "ja" to "Japanese")
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SOURCE → TARGET", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            langs.forEach { (code, name) ->
                                PixelChip(label = "From $name", selected = sourceLang == code, chapterHue = hue, onClick = { sourceLang = code })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            langs.forEach { (code, name) ->
                                PixelChip(label = "To $name", selected = targetLang == code, chapterHue = ChapterHue.COBALT, onClick = { targetLang = code })
                            }
                        }
                        PixelTextField(value = transText, onValueChange = { transText = it }, placeholder = "Enter text or tap Voice Dictate…", singleLine = false, minHeight = 88.dp, chapterHue = hue)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(text = if (transBusy) "Translating…" else "Translate", chapterHue = hue, enabled = !transBusy && transText.isNotBlank(), onClick = {
                                transBusy = true
                                scope.launch(Dispatchers.IO) {
                                    val res = runCatching {
                                        val q = URLEncoder.encode(transText.trim(), "UTF-8")
                                        val req = Request.Builder().url("https://api.mymemory.translated.net/get?q=$q&langpair=$sourceLang|$targetLang").build()
                                        OkHttpClient().newCall(req).execute().use { resp ->
                                            val json = JSONObject(resp.body?.string().orEmpty())
                                            json.getJSONObject("responseData").optString("translatedText", "")
                                        }
                                    }.getOrElse { "Translation offline error: ${it.message}" }
                                    withContext(Dispatchers.Main) {
                                        transOutput = res
                                        transBusy = false
                                        onLogNetworkAudit(meta.id, "api.mymemory.translated.net", transText.length.toLong(), res.length.toLong(), "$sourceLang->$targetLang")
                                    }
                                }
                            })
                            PixelButton(text = "Voice Dictate", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLang)
                                }
                                runCatching { sttLauncher.launch(intent) }.onFailure { onStatusToast("Voice recognizer not available on device.") }
                            })
                            PixelButton(text = "Speak", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, enabled = transOutput.isNotBlank(), onClick = {
                                ttsEngine?.language = Locale(targetLang)
                                ttsEngine?.speak(transOutput, TextToSpeech.QUEUE_FLUSH, null, "tr")
                            })
                        }
                        if (transOutput.isNotBlank()) {
                            Text(transOutput, style = DotkitType.bodyUi(15, bold = true), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(12.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
