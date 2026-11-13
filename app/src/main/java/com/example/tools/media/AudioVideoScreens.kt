package com.example.tools.media

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.lib.media.AudioVideoEngine
import com.example.lib.media.MediaFileMetadata
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioVideoMediaScreen(
    meta: ToolMeta,
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
    val hue = ChapterHue.TEAL

    // Audio Recorder state
    var hasMicPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMicPerm = it }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recElapsedSec by remember { mutableLongStateOf(0L) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        recElapsedSec = 0L
        while (isRecording) {
            delay(1000L)
            recElapsedSec++
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop(); recorder?.release() }
            runCatching { player?.stop(); player?.release() }
        }
    }

    // Media Trim / Extract / Inspect state
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var mediaInfo by remember { mutableStateOf<MediaFileMetadata?>(null) }
    var startSecStr by remember { mutableStateOf("0") }
    var endSecStr by remember { mutableStateOf("15") }
    var processing by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            val info = AudioVideoEngine.inspectMedia(context, uri)
            mediaInfo = info
            val durSec = (info.durationMs / 1000L).coerceAtLeast(1L)
            endSecStr = durSec.toString()
        }
    }

    val saveSafLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(if (meta.id == "med-vidcomp") "video/mp4" else "audio/mp4")
    ) { uri ->
        val src = outputFile ?: recordedFile ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().copyTo(out) }
            onStatusToast("Exported media file.")
        }
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = "",
        outputFileName = "",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        showOutputBar = false,
        helpParagraph = meta.tagline
    ) {
        if (meta.id == "med-audiorec") {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!hasMicPerm) {
                        Text("Grant Microphone permission to record M4A audio clips locally.", style = DotkitType.bodyUi(13), color = palette.ink2)
                        PixelButton(text = "Enable Microphone", chapterHue = hue, onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) })
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("VOICE & FIELD RECORDER (AAC M4A)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp(if (isRecording) "REC ●" else "IDLE", if (isRecording) palette.err else palette.ink3)
                        }
                        DotMatrixReadout("%02d:%02d".format(recElapsedSec / 60, recElapsedSec % 60), sizeSp = 40, accentColor = palette.ink)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(
                                text = if (isRecording) "Stop Recording" else "Start Recording",
                                variant = if (isRecording) PixelButtonVariant.DESTRUCTIVE else PixelButtonVariant.SOLID,
                                chapterHue = hue,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isRecording) {
                                        runCatching { recorder?.stop(); recorder?.release() }
                                        recorder = null
                                        isRecording = false
                                        onStatusToast("Recording saved.")
                                    } else {
                                        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
                                        @Suppress("DEPRECATION")
                                        val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
                                        mr.apply {
                                            setAudioSource(MediaRecorder.AudioSource.MIC)
                                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            setAudioEncodingBitRate(128000)
                                            setAudioSamplingRate(44100)
                                            setOutputFile(file.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        recorder = mr
                                        recordedFile = file
                                        isRecording = true
                                    }
                                }
                            )
                        }
                        recordedFile?.takeIf { !isRecording && it.exists() }?.let { f ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = if (isPlaying) "Stop" else "Play", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    if (isPlaying) {
                                        runCatching { player?.stop(); player?.release() }
                                        player = null; isPlaying = false
                                    } else {
                                        val mp = MediaPlayer().apply {
                                            setDataSource(f.absolutePath)
                                            prepare()
                                            setOnCompletionListener { isPlaying = false }
                                            start()
                                        }
                                        player = mp; isPlaying = true
                                    }
                                })
                                PixelButton(text = "Save M4A", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { saveSafLauncher.launch("recording.m4a") })
                                PixelButton(text = "Share", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                    ShareMediaUtils.shareFile(context, f, "audio/mp4", "Share Audio Recording")
                                })
                            }
                        }
                    }
                }
            }
        } else {
            val isVideoInput = meta.id == "med-vid2aud" || meta.id == "med-vidcomp"
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelButton(
                        text = if (pickedUri == null) (if (isVideoInput) "Pick Video File" else "Pick Audio File") else "Change File",
                        chapterHue = hue,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { mediaPicker.launch(arrayOf(if (isVideoInput) "video/*" else "audio/*")) }
                    )
                    mediaInfo?.let { info ->
                        Text("MIME: ${info.mime} | Duration: ${info.durationMs / 1000}s | Bitrate: ${info.bitrateBps / 1000} kbps", style = DotkitType.monoData(12), color = palette.ink)
                        if (info.hasVideo) Text("Resolution: ${info.width}×${info.height}", style = DotkitType.monoLabel(10), color = palette.ink2)

                        if (meta.id == "med-audiotrim" || meta.id == "med-vidcomp") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelTextField(value = startSecStr, onValueChange = { startSecStr = it }, placeholder = "Start (sec)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), chapterHue = hue, modifier = Modifier.weight(1f))
                                PixelTextField(value = endSecStr, onValueChange = { endSecStr = it }, placeholder = "End (sec)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), chapterHue = hue, modifier = Modifier.weight(1f))
                            }
                        }

                        PixelButton(
                            text = when {
                                processing -> "Processing…"
                                meta.id == "med-vid2aud" -> "Extract Audio (.m4a)"
                                meta.id == "med-audiotrim" -> "Trim Audio Clip (.m4a)"
                                else -> "Trim & Remux Video (.mp4)"
                            },
                            chapterHue = hue,
                            enabled = !processing && pickedUri != null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val src = pickedUri ?: return@PixelButton
                                processing = true
                                scope.launch {
                                    val ext = if (meta.id == "med-vidcomp") "mp4" else "m4a"
                                    val out = File(context.cacheDir, "${meta.id}_${System.currentTimeMillis()}.$ext")
                                    val startMs = (startSecStr.toLongOrNull() ?: 0L) * 1000L
                                    val endMs = (endSecStr.toLongOrNull() ?: -1L).let { if (it > 0L) it * 1000L else -1L }
                                    val res = AudioVideoEngine.remuxOrTrimAudio(
                                        context = context,
                                        sourceUri = src,
                                        outputFile = out,
                                        startMs = if (meta.id == "med-vid2aud") 0L else startMs,
                                        endMs = if (meta.id == "med-vid2aud") -1L else endMs,
                                        audioOnly = meta.id != "med-vidcomp"
                                    )
                                    processing = false
                                    if (res.isSuccess) {
                                        outputFile = out
                                        onStatusToast("Done (${out.length() / 1024} KB).")
                                    } else {
                                        onStatusToast("Failed: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                        )

                        outputFile?.takeIf { it.exists() }?.let { f ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(text = "Save File", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { saveSafLauncher.launch(f.name) })
                                PixelButton(text = "Share", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                    ShareMediaUtils.shareFile(context, f, if (meta.id == "med-vidcomp") "video/mp4" else "audio/mp4", "Share Media")
                                })
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
