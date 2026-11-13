package com.example.lib.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

enum class DownloadFormatPreset(
    val id: String,
    val label: String,
    val isAudio: Boolean,
    val ext: String,
    val ytdlpFormat: String
) {
    BEST_VIDEO("best_video", "Max Quality Video", false, "mp4", "bv*+ba/b"),
    UHD_8K("4320p", "8K 4320p", false, "mp4", "bv*[height<=4320]+ba/b[height<=4320]"),
    UHD_4K("2160p", "4K 2160p", false, "mp4", "bv*[height<=2160]+ba/b[height<=2160]"),
    FHD_1080("1080p", "1080p Full HD", false, "mp4", "bv*[height<=1080]+ba/b[height<=1080]"),
    HD_720("720p", "720p HD", false, "mp4", "bv*[height<=720]+ba/b[height<=720]"),
    SD_480("480p", "480p Saver", false, "mp4", "bv*[height<=480]+ba/b[height<=480]"),
    SD_360("360p", "360p Mobile", false, "mp4", "bv*[height<=360]+ba/b[height<=360]"),
    AUDIO_MP3("mp3", "MP3 320 kbps", true, "mp3", "ba/b"),
    AUDIO_M4A("m4a", "M4A AAC Best", true, "m4a", "ba[ext=m4a]/ba/b"),
    AUDIO_FLAC("flac", "FLAC Lossless", true, "flac", "ba/b"),
    AUDIO_WAV("wav", "WAV Uncompressed", true, "wav", "ba/b"),
    AUDIO_AAC("aac", "AAC Audio", true, "aac", "ba/b"),
    AUDIO_OGG("ogg", "OGG Vorbis", true, "ogg", "ba/b"),
    AUDIO_OPUS("opus", "OPUS WebM", true, "opus", "ba/b")
}

data class MediaInspectInfo(
    val url: String,
    val platform: String,
    val title: String,
    val author: String,
    val durationText: String,
    val thumbnailUrl: String,
    val isPlaylist: Boolean,
    val playlistItems: List<PlaylistEntryInfo>,
    val directMediaUrl: String? = null,
    val note: String = ""
)

data class PlaylistEntryInfo(
    val index: Int,
    val title: String,
    val url: String,
    val duration: String,
    val selected: Boolean = true
)

data class DownloadExecutionResult(
    val success: Boolean,
    val message: String,
    val localFilePath: String = "",
    val bytesTransferred: Long = 0L
)

object MediaDownloaderEngine {

    fun detectPlatform(url: String): String {
        val u = url.lowercase()
        return when {
            "music.youtube" in u -> "YouTube Music"
            "youtube.com" in u || "youtu.be" in u -> "YouTube"
            "spotify.com" in u -> "Spotify"
            "instagram.com" in u -> "Instagram"
            "twitter.com" in u || "x.com" in u -> "X / Twitter"
            "tiktok.com" in u -> "TikTok"
            "reddit.com" in u || "redd.it" in u -> "Reddit"
            "vimeo.com" in u -> "Vimeo"
            "twitch.tv" in u -> "Twitch"
            "pinterest." in u || "pin.it" in u -> "Pinterest"
            "snapchat.com" in u -> "Snapchat"
            "t.me" in u || "telegram.me" in u -> "Telegram"
            "soundcloud.com" in u -> "SoundCloud"
            "bandcamp.com" in u -> "Bandcamp"
            else -> "Direct / Web"
        }
    }

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun buildCliCommandPreview(
        url: String,
        preset: DownloadFormatPreset,
        isMusicMode: Boolean,
        containerExt: String = "mp4",
        embedThumb: Boolean,
        embedSubs: Boolean,
        subLang: String,
        embedMetadata: Boolean,
        embedChapters: Boolean = false,
        sponsorBlock: Boolean,
        clipStart: String = "",
        clipEnd: String = "",
        lrcLyrics: Boolean = false,
        filenameTemplate: String,
        customArgs: String,
        proxyUrl: String
    ): String {
        if (isMusicMode && ("spotify.com" in url.lowercase())) {
            val parts = mutableListOf("spotdl", "download", "\"$url\"", "--format", preset.ext)
            if (lrcLyrics) parts += listOf("--lyrics", "synced")
            if (filenameTemplate.isNotBlank()) parts += listOf("--output", "\"$filenameTemplate\"")
            if (proxyUrl.isNotBlank()) parts += listOf("--proxy", proxyUrl)
            if (customArgs.isNotBlank()) parts += customArgs.trim()
            return parts.joinToString(" ")
        }
        val parts = mutableListOf("yt-dlp", "-f", "\"${preset.ytdlpFormat}\"")
        if (preset.isAudio) {
            parts += listOf("-x", "--audio-format", preset.ext, "--audio-quality", "0")
        } else {
            parts += listOf("--merge-output-format", containerExt.ifBlank { "mp4" })
        }
        if (embedThumb) parts += "--embed-thumbnail"
        if (embedMetadata) parts += "--embed-metadata"
        if (embedChapters) parts += "--embed-chapters"
        if (embedSubs && !preset.isAudio) {
            parts += listOf("--write-subs", "--embed-subs", "--sub-langs", subLang.ifBlank { "en" })
        }
        if (lrcLyrics) parts += listOf("--write-auto-subs", "--convert-subs", "lrc")
        if (clipStart.isNotBlank() && clipEnd.isNotBlank()) {
            parts += listOf("--download-sections", "\"*${clipStart.trim()}-${clipEnd.trim()}\"")
        }
        if (sponsorBlock) parts += listOf("--sponsorblock-remove", "sponsor")
        if (filenameTemplate.isNotBlank()) parts += listOf("-o", "\"$filenameTemplate\"")
        if (proxyUrl.isNotBlank()) parts += listOf("--proxy", proxyUrl)
        if (customArgs.isNotBlank()) parts += customArgs.trim()
        parts += "\"$url\""
        return parts.joinToString(" ")
    }

    private fun buildClient(proxyUrl: String = ""): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
        val trimmedProxy = proxyUrl.trim()
        if (trimmedProxy.startsWith("http://") || trimmedProxy.startsWith("socks://")) {
            runCatching {
                val uri = java.net.URI(trimmedProxy)
                val type = if (uri.scheme.startsWith("socks")) Proxy.Type.SOCKS else Proxy.Type.HTTP
                val port = if (uri.port > 0) uri.port else 8080
                builder.proxy(Proxy(type, InetSocketAddress(uri.host, port)))
            }
        }
        return builder.build()
    }

    suspend fun inspectUrl(
        url: String,
        companionUrl: String,
        companionToken: String,
        proxyUrl: String = ""
    ): MediaInspectInfo = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val platform = detectPlatform(cleanUrl)
        val client = buildClient(proxyUrl)

        val compBase = companionUrl.trim().trimEnd('/')
        if (compBase.startsWith("http://") || compBase.startsWith("https://")) {
            runCatching {
                val payload = JSONObject().apply { put("url", cleanUrl) }.toString()
                val req = Request.Builder()
                    .url("$compBase/api/inspect")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .apply { if (companionToken.isNotBlank()) header("Authorization", "Bearer ${companionToken.trim()}") }
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val entriesArr = json.optJSONArray("entries") ?: JSONArray()
                        val entries = buildList {
                            for (i in 0 until entriesArr.length()) {
                                val obj = entriesArr.optJSONObject(i) ?: continue
                                add(PlaylistEntryInfo(i + 1, obj.optString("title", "Item ${i + 1}"), obj.optString("url", cleanUrl), obj.optString("duration", "")))
                            }
                        }
                        return@withContext MediaInspectInfo(
                            url = cleanUrl, platform = json.optString("extractor", platform),
                            title = json.optString("title", cleanUrl), author = json.optString("uploader", ""),
                            durationText = json.optString("duration_string", ""), thumbnailUrl = json.optString("thumbnail", ""),
                            isPlaylist = entries.size > 1, playlistItems = entries, note = "Inspected via Companion Server"
                        )
                    }
                }
            }
        }

        try {
            val req = Request.Builder().url(cleanUrl).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OmniDeck/1.0").build()
            client.newCall(req).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                if (contentType.startsWith("video/") || contentType.startsWith("audio/") || contentType == "application/octet-stream") {
                    val fileName = cleanUrl.substringAfterLast('/').substringBefore('?').ifBlank { "media_stream" }
                    return@withContext MediaInspectInfo(
                        url = cleanUrl, platform = platform, title = fileName, author = resp.request.url.host,
                        durationText = "${(resp.header("Content-Length")?.toLongOrNull() ?: 0L) / 1024} KB",
                        thumbnailUrl = "", isPlaylist = false, playlistItems = emptyList(),
                        directMediaUrl = cleanUrl, note = "Direct media stream detected ($contentType). Ready for native download."
                    )
                }

                val html = resp.body?.string().orEmpty().take(250_000)
                val title = extractMeta(html, "og:title")
                    ?: Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
                    ?: cleanUrl
                val author = extractMeta(html, "og:site_name") ?: resp.request.url.host
                val thumb = extractMeta(html, "og:image").orEmpty()
                val ogVideo = extractMeta(html, "og:video:secure_url") ?: extractMeta(html, "og:video:url") ?: extractMeta(html, "og:video")

                MediaInspectInfo(
                    url = cleanUrl, platform = platform, title = decodeHtmlEntities(title), author = decodeHtmlEntities(author),
                    durationText = "", thumbnailUrl = thumb, isPlaylist = "list=" in cleanUrl || "/playlist/" in cleanUrl || "/album/" in cleanUrl,
                    playlistItems = emptyList(), directMediaUrl = ogVideo,
                    note = when {
                        ogVideo != null -> "Direct media stream found in OpenGraph metadata."
                        compBase.isNotBlank() -> "Companion offline; OpenGraph metadata loaded. Start Companion for full yt-dlp/spotDL extraction."
                        else -> "OpenGraph metadata loaded. Configure Companion bridge in Settings for protected platform streams."
                    }
                )
            }
        } catch (e: Exception) {
            MediaInspectInfo(cleanUrl, platform, cleanUrl, "", "", "", false, emptyList(), note = "Inspection error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun downloadDirectOrCompanion(
        context: Context,
        url: String,
        fileNameHint: String,
        preset: DownloadFormatPreset,
        containerExt: String = preset.ext,
        wifiOnly: Boolean = false,
        companionUrl: String,
        companionToken: String,
        cookieHeader: String,
        proxyUrl: String,
        cliCommand: String,
        targetSafUri: Uri? = null,
        onProgress: (Int) -> Unit
    ): DownloadExecutionResult = withContext(Dispatchers.IO) {
        if (wifiOnly && !isOnWifi(context)) {
            return@withContext DownloadExecutionResult(false, "Blocked by Wi-Fi Only guard. Connect to Wi-Fi or turn off Wi-Fi Only.")
        }
        val client = buildClient(proxyUrl)
        val safeName = fileNameHint.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(48).ifBlank { "omni_media" }
        val finalExt = if (preset.isAudio) preset.ext else containerExt.ifBlank { preset.ext }
        val compBase = companionUrl.trim().trimEnd('/')

        if (compBase.startsWith("http://") || compBase.startsWith("https://")) {
            val compAttempt = runCatching {
                onProgress(15)
                val bodyJson = JSONObject().apply {
                    put("url", url); put("format", preset.id); put("ext", finalExt); put("command", cliCommand)
                    if (cookieHeader.isNotBlank()) put("cookies", cookieHeader)
                }.toString()
                val req = Request.Builder().url("$compBase/api/download")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .apply { if (companionToken.isNotBlank()) header("Authorization", "Bearer ${companionToken.trim()}") }
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val total = resp.body?.contentLength() ?: -1L
                        val destDir = File(context.filesDir, "downloads").apply { mkdirs() }
                        val outFile = File(destDir, "$safeName.$finalExt")
                        var readTotal = 0L
                        resp.body?.byteStream()?.use { input ->
                            val outStream = if (targetSafUri != null) context.contentResolver.openOutputStream(targetSafUri) ?: FileOutputStream(outFile) else FileOutputStream(outFile)
                            outStream.use { out ->
                                val buf = ByteArray(16 * 1024); var n: Int
                                while (input.read(buf).also { n = it } != -1) {
                                    out.write(buf, 0, n); readTotal += n
                                    if (total > 0) onProgress(((readTotal * 100) / total).toInt().coerceIn(20, 99))
                                }
                            }
                        }
                        onProgress(100)
                        DownloadExecutionResult(true, "Downloaded via Companion (${readTotal / 1024} KB).", targetSafUri?.toString() ?: outFile.absolutePath, readTotal)
                    } else null
                }
            }.getOrNull()
            if (compAttempt != null) return@withContext compAttempt
        }

        try {
            onProgress(10)
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OmniDeck/1.0")
                .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader.trim()) }.build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext DownloadExecutionResult(false, "HTTP ${resp.code}. Connect Companion server in Settings for YouTube/Spotify extraction.")
                }
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("text/html")) {
                    return@withContext DownloadExecutionResult(false, "URL returned HTML webpage (not direct media stream). Start Companion (yt-dlp/spotDL) or paste direct stream URL.")
                }
                val total = resp.body?.contentLength() ?: -1L
                val extFromMime = when {
                    "audio/mpeg" in contentType -> "mp3"
                    "audio/mp4" in contentType -> "m4a"
                    "audio/ogg" in contentType -> "ogg"
                    "video/webm" in contentType -> "webm"
                    else -> finalExt
                }
                val destDir = File(context.filesDir, "downloads").apply { mkdirs() }
                val outFile = File(destDir, "$safeName.$extFromMime")
                var readTotal = 0L
                resp.body?.byteStream()?.use { input ->
                    val outStream = if (targetSafUri != null) context.contentResolver.openOutputStream(targetSafUri) ?: FileOutputStream(outFile) else FileOutputStream(outFile)
                    outStream.use { out ->
                        val buf = ByteArray(16 * 1024); var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n); readTotal += n
                            if (total > 0) onProgress(((readTotal * 100) / total).toInt().coerceIn(15, 99))
                        }
                    }
                }
                onProgress(100)
                DownloadExecutionResult(true, "Saved $safeName.$extFromMime (${readTotal / 1024} KB).", targetSafUri?.toString() ?: outFile.absolutePath, readTotal)
            }
        } catch (e: Exception) {
            DownloadExecutionResult(false, "Download failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun extractMeta(html: String, prop: String): String? {
        val r1 = Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val r2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE)
        return r1.find(html)?.groupValues?.get(1)?.trim() ?: r2.find(html)?.groupValues?.get(1)?.trim()
    }

    private fun decodeHtmlEntities(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
