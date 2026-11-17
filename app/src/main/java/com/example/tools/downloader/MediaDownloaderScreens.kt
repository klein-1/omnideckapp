package com.example.tools.downloader

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.net.DownloadFormatPreset
import com.example.lib.net.MediaDownloaderEngine
import com.example.lib.net.MediaInspectInfo
import com.example.lib.net.PlaylistEntryInfo
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.DownloadJobEntity
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaDownloaderTool(
    meta: ToolMeta,
    isMusicMode: Boolean,
    initialUrl: String,
    companionUrl: String,
    companionToken: String,
    jobs: List<DownloadJobEntity>,
    onInsertJobSync: suspend (DownloadJobEntity) -> Long,
    onUpdateJob: (DownloadJobEntity) -> Unit,
    onDeleteJob: (Long) -> Unit,
    onLogNetworkAudit: (toolId: String, host: String, outB: Long, inB: Long, note: String) -> Unit,
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
    val hue = if (isMusicMode) ChapterHue.ROSE else ChapterHue.VERMILION

    val availablePresets = remember(isMusicMode) {
        if (isMusicMode) DownloadFormatPreset.entries.filter { it.isAudio }
        else DownloadFormatPreset.entries
    }

    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var batchMode by remember { mutableStateOf(false) }
    var selectedPreset by remember(isMusicMode) { mutableStateOf(availablePresets.first()) }
    var videoContainer by remember { mutableStateOf("mp4") }
    var inspecting by remember { mutableStateOf(false) }
    var inspectInfo by remember { mutableStateOf<MediaInspectInfo?>(null) }
    val playlistSelection = remember { mutableStateListOf<PlaylistEntryInfo>() }

    // Seal / spotDL parity QoL controls
    var showAdvanced by remember { mutableStateOf(false) }
    var wifiOnly by remember { mutableStateOf(false) }
    var embedThumb by remember { mutableStateOf(true) }
    var embedMetadata by remember { mutableStateOf(true) }
    var embedChapters by remember { mutableStateOf(false) }
    var embedSubs by remember { mutableStateOf(false) }
    var lrcLyrics by remember { mutableStateOf(isMusicMode) }
    var subLang by remember { mutableStateOf("en") }
    var sponsorBlock by remember { mutableStateOf(false) }
    var clipStart by remember { mutableStateOf("") }
    var clipEnd by remember { mutableStateOf("") }
    var filenameTemplate by remember(isMusicMode) {
        mutableStateOf(if (isMusicMode) "{artist} - {title}.{output-ext}" else "%(title)s [%(id)s].%(ext)s")
    }
    var cookieHeader by remember { mutableStateOf("") }
    var proxyUrl by remember { mutableStateOf("") }
    var customArgs by remember { mutableStateOf("") }
    var queueFilter by remember { mutableStateOf("ALL") } // ALL, DONE, ERROR

    var pendingTargetUrl by remember { mutableStateOf("") }
    var pendingTitleHint by remember { mutableStateOf("omni_media") }

    val cliPreview = remember(
        urlInput, selectedPreset, isMusicMode, videoContainer, embedThumb, embedSubs,
        subLang, embedMetadata, embedChapters, sponsorBlock, clipStart, clipEnd, lrcLyrics, filenameTemplate, customArgs, proxyUrl
    ) {
        val sampleUrl = urlInput.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifBlank { "https://..." }
        MediaDownloaderEngine.buildCliCommandPreview(
            url = sampleUrl, preset = selectedPreset, isMusicMode = isMusicMode, containerExt = videoContainer,
            embedThumb = embedThumb, embedSubs = embedSubs, subLang = subLang, embedMetadata = embedMetadata,
            embedChapters = embedChapters, sponsorBlock = sponsorBlock, clipStart = clipStart, clipEnd = clipEnd,
            lrcLyrics = lrcLyrics, filenameTemplate = filenameTemplate, customArgs = customArgs, proxyUrl = proxyUrl
        )
    }

    fun launchDownload(targetUrl: String, titleHint: String, safUri: android.net.Uri? = null) {
        val cleanUrl = targetUrl.trim()
        if (cleanUrl.isBlank()) { onStatusToast("Paste a URL first."); return }
        val platform = MediaDownloaderEngine.detectPlatform(cleanUrl)
        scope.launch {
            val rowId = onInsertJobSync(
                DownloadJobEntity(url = cleanUrl, title = titleHint.ifBlank { cleanUrl.take(40) }, platform = platform, formatSpec = selectedPreset.label, status = "RUNNING", progress = 5)
            )
            val result = MediaDownloaderEngine.downloadDirectOrCompanion(
                context = context, url = cleanUrl, fileNameHint = titleHint, preset = selectedPreset,
                containerExt = videoContainer, wifiOnly = wifiOnly, companionUrl = companionUrl, companionToken = companionToken,
                cookieHeader = cookieHeader, proxyUrl = proxyUrl, cliCommand = cliPreview, targetSafUri = safUri,
                onProgress = { pct ->
                    onUpdateJob(DownloadJobEntity(id = rowId, url = cleanUrl, title = titleHint.ifBlank { cleanUrl.take(40) }, platform = platform, formatSpec = selectedPreset.label, status = "RUNNING", progress = pct))
                }
            )
            onUpdateJob(
                DownloadJobEntity(
                    id = rowId, url = cleanUrl, title = titleHint.ifBlank { cleanUrl.take(40) }, platform = platform,
                    formatSpec = selectedPreset.label, status = if (result.success) "DONE" else "ERROR",
                    progress = if (result.success) 100 else 0, outputPath = result.localFilePath,
                    errorMsg = if (result.success) "" else result.message
                )
            )
            val host = runCatching { java.net.URI(cleanUrl).host ?: cleanUrl }.getOrDefault(cleanUrl)
            onLogNetworkAudit(meta.id, host, 256L, result.bytesTransferred, result.message.take(80))
            onStatusToast(result.message)
        }
    }

    val createMediaFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null && pendingTargetUrl.isNotBlank()) launchDownload(pendingTargetUrl, pendingTitleHint, uri)
    }

    ToolWorkbenchChrome(
        meta = meta, isPinned = isPinned, onTogglePin = onTogglePin, onBack = onBack,
        outputText = cliPreview, outputFileName = if (isMusicMode) "spotdl_command.txt" else "ytdlp_command.txt",
        outputKind = DataKind.TEXT, onSaveToFiles = onSaveToFiles, onSendToTool = onSendToTool, onStatusToast = onStatusToast,
        helpParagraph = if (isMusicMode) "Spotify, YT Music, SoundCloud & Bandcamp audio/playlist downloader (MP3/M4A/FLAC/WAV/OPUS/AAC) with LRC lyrics, batch URL queue & Companion spotDL/yt-dlp bridge."
        else "Seal-compatible downloader for 15 platforms up to 8K/4K, MP4/MKV/WEBM containers, clip trimming, SponsorBlock, subtitles, chapters, Wi-Fi guard & batch queue."
    ) {
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (batchMode) "BATCH URL QUEUE (1 PER LINE)" else if (isMusicMode) "MUSIC / PLAYLIST URL" else "VIDEO / PLAYLIST URL", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PixelChip(label = if (batchMode) "Single" else "Batch", selected = batchMode, chapterHue = hue, onClick = { batchMode = !batchMode })
                        if (!batchMode && urlInput.isNotBlank()) PixelStamp(MediaDownloaderEngine.detectPlatform(urlInput), palette.chapterColor(hue))
                    }
                }

                PixelTextField(
                    value = urlInput, onValueChange = { urlInput = it },
                    placeholder = if (batchMode) "https://...\nhttps://..." else if (isMusicMode) "https://open.spotify.com/track/..." else "https://youtube.com/watch?v=...",
                    singleLine = !batchMode, minHeight = if (batchMode) 100.dp else 48.dp, chapterHue = hue
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!batchMode) {
                        PixelButton(
                            text = if (inspecting) "Inspecting…" else "Inspect", variant = PixelButtonVariant.OUTLINE, chapterHue = hue,
                            enabled = !inspecting && urlInput.isNotBlank(),
                            onClick = {
                                inspecting = true
                                scope.launch {
                                    val res = MediaDownloaderEngine.inspectUrl(urlInput, companionUrl, companionToken, proxyUrl)
                                    inspectInfo = res; playlistSelection.clear(); playlistSelection.addAll(res.playlistItems)
                                    inspecting = false
                                    onLogNetworkAudit(meta.id, res.platform, 128L, 2048L, "Inspected ${res.title.take(40)}")
                                }
                            }
                        )
                    }
                    PixelButton(
                        text = if (batchMode) "Enqueue All" else "Download", chapterHue = hue, enabled = urlInput.isNotBlank(),
                        onClick = {
                            if (batchMode) {
                                val urls = urlInput.lines().map { it.trim() }.filter { it.startsWith("http") }
                                urls.forEachIndexed { idx, u -> launchDownload(u, "batch_${idx + 1}") }
                                onStatusToast("Queued ${urls.size} downloads.")
                            } else {
                                val selectedEntries = playlistSelection.filter { it.selected }
                                if (selectedEntries.isNotEmpty()) selectedEntries.forEach { item -> launchDownload(item.url, item.title) }
                                else launchDownload(inspectInfo?.directMediaUrl ?: urlInput, inspectInfo?.title ?: "omni_media")
                            }
                        }
                    )
                    if (!batchMode) {
                        PixelChip(label = "Save As…", chapterHue = hue, onClick = {
                            pendingTargetUrl = inspectInfo?.directMediaUrl ?: urlInput
                            pendingTitleHint = inspectInfo?.title ?: "omni_media"
                            if (pendingTargetUrl.isNotBlank()) createMediaFileLauncher.launch("omni_media.${selectedPreset.ext}") else onStatusToast("Enter URL first.")
                        })
                    }
                }
            }
        }

        inspectInfo?.let { info ->
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (info.thumbnailUrl.isNotBlank()) {
                            Image(painter = rememberAsyncImagePainter(info.thumbnailUrl), contentDescription = info.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(info.title, style = DotkitType.bodyUi(14, bold = true), color = palette.ink, maxLines = 2)
                            if (info.author.isNotBlank()) Text("${info.author} ${info.durationText}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        }
                    }
                    Text(info.note, style = DotkitType.bodyUi(12), color = palette.ink2)
                    if (playlistSelection.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PLAYLIST (${playlistSelection.count { it.selected }}/${playlistSelection.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelChip(label = "Toggle All", onClick = {
                                val allOn = playlistSelection.all { it.selected }
                                for (i in playlistSelection.indices) playlistSelection[i] = playlistSelection[i].copy(selected = !allOn)
                            })
                        }
                        playlistSelection.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (item.selected) palette.paper else palette.paper3)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).clickable { playlistSelection[idx] = item.copy(selected = !item.selected) }.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (item.selected) "✓" else "○", style = DotkitType.monoData(13), color = palette.chapterColor(hue))
                                Text("${item.index}. ${item.title}", style = DotkitType.bodyUi(13), color = palette.ink, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(item.duration, style = DotkitType.monoLabel(10), color = palette.ink3)
                            }
                        }
                    }
                }
            }
        }

        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("QUALITY & FORMAT PRESET", style = DotkitType.monoLabel(10), color = palette.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    availablePresets.forEach { preset ->
                        PixelChip(label = preset.label, selected = selectedPreset == preset, chapterHue = hue, onClick = { selectedPreset = preset })
                    }
                }
                if (!selectedPreset.isAudio) {
                    Text("CONTAINER FORMAT", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("mp4", "mkv", "webm", "mov").forEach { c ->
                            PixelChip(label = c.uppercase(), selected = videoContainer == c, chapterHue = hue, onClick = { videoContainer = c })
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                Row(Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ADVANCED ENGINE (CLIP RANGE, LRC, WIFI, PROXY)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Text(if (showAdvanced) "−" else "+", style = DotkitType.monoData(14), color = palette.ink)
                }

                if (showAdvanced) {
                    PixelSwitch("Wi-Fi Only Download Guard", wifiOnly, { wifiOnly = it }, hue)
                    PixelSwitch("Embed Cover Thumbnail", embedThumb, { embedThumb = it }, hue)
                    PixelSwitch("Embed Metadata / ID3 Tags", embedMetadata, { embedMetadata = it }, hue)
                    PixelSwitch("Embed Chapters", embedChapters, { embedChapters = it }, hue)
                    PixelSwitch("Synced .LRC Lyrics", lrcLyrics, { lrcLyrics = it }, hue)
                    if (!isMusicMode) {
                        PixelSwitch("Embed Subtitles", embedSubs, { embedSubs = it }, hue)
                        if (embedSubs) PixelTextField(value = subLang, onValueChange = { subLang = it }, placeholder = "Subtitle langs (en,es)", chapterHue = hue)
                        PixelSwitch("SponsorBlock Skip Sponsors", sponsorBlock, { sponsorBlock = it }, hue)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(value = clipStart, onValueChange = { clipStart = it }, placeholder = "Clip start 00:15", chapterHue = hue, modifier = Modifier.weight(1f))
                        PixelTextField(value = clipEnd, onValueChange = { clipEnd = it }, placeholder = "Clip end 01:00", chapterHue = hue, modifier = Modifier.weight(1f))
                    }
                    PixelTextField(value = filenameTemplate, onValueChange = { filenameTemplate = it }, placeholder = "Output filename template", chapterHue = hue)
                    PixelTextField(value = cookieHeader, onValueChange = { cookieHeader = it }, placeholder = "Cookie header (optional)", chapterHue = hue)
                    PixelTextField(value = proxyUrl, onValueChange = { proxyUrl = it }, placeholder = "Proxy http://host:port (optional)", chapterHue = hue)
                    PixelTextField(value = customArgs, onValueChange = { customArgs = it }, placeholder = "Extra CLI flags (optional)", chapterHue = hue)
                }

                Text("GENERATED CLI COMMAND", style = DotkitType.monoLabel(10), color = palette.ink3)
                Text(cliPreview, style = DotkitType.monoData(11), color = palette.ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp))
            }
        }

        if (jobs.isNotEmpty()) {
            val filteredJobs = remember(jobs, queueFilter) {
                when (queueFilter) {
                    "DONE" -> jobs.filter { it.status == "DONE" }
                    "ERROR" -> jobs.filter { it.status == "ERROR" }
                    else -> jobs
                }
            }
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("QUEUE (${filteredJobs.size}/${jobs.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("ALL", "DONE", "ERROR").forEach { f ->
                                PixelChip(label = f, selected = queueFilter == f, chapterHue = hue, onClick = { queueFilter = f })
                            }
                        }
                    }
                    filteredJobs.take(20).forEach { job ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(job.title, style = DotkitType.bodyUi(13, bold = true), color = palette.ink, modifier = Modifier.weight(1f), maxLines = 1)
                                PixelStamp(job.status, when (job.status) { "DONE" -> palette.ok; "ERROR" -> palette.err; else -> palette.chapterColor(hue) })
                            }
                            Text("${job.platform} · ${job.formatSpec}", style = DotkitType.monoLabel(10), color = palette.ink2)
                            if (job.status == "RUNNING") PixelProgressBar(job.progress / 100f, hue)
                            if (job.errorMsg.isNotBlank()) Text(job.errorMsg, style = DotkitType.bodyUi(11), color = palette.err)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (job.status == "ERROR") {
                                    PixelChip(label = "Retry", chapterHue = hue, onClick = { onDeleteJob(job.id); launchDownload(job.url, job.title) })
                                }
                                if (job.outputPath.isNotBlank() && !job.outputPath.startsWith("content://")) {
                                    val f = File(job.outputPath)
                                    if (f.exists()) PixelChip(label = "Share", chapterHue = hue, onClick = { ShareMediaUtils.shareFile(context, f, "*/*", "Share ${job.title}") })
                                }
                                if (job.outputPath.startsWith("content://")) {
                                    PixelChip(label = "Open", chapterHue = hue, onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setData(job.outputPath.toUri()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                    })
                                }
                                PixelChip(label = "✕", onClick = { onDeleteJob(job.id) })
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
