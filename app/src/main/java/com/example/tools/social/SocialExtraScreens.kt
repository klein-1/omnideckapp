package com.example.tools.social

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotMatrixReadout
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialExtraScreen(
    meta: ToolMeta,
    initialText: String,
    onLogNetworkAudit: (toolId: String, host: String, outBytes: Long, inBytes: Long, note: String) -> Unit,
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
            "soc-utm" -> {
                var baseUrl by remember(initialText) { mutableStateOf(if (initialText.startsWith("http")) initialText else "https://example.com/launch") }
                var source by remember { mutableStateOf("instagram") }
                var medium by remember { mutableStateOf("social") }
                var campaign by remember { mutableStateOf("spring_drop") }
                var term by remember { mutableStateOf("") }
                var content by remember { mutableStateOf("") }

                val builtUrl = remember(baseUrl, source, medium, campaign, term, content) {
                    val cleanBase = baseUrl.trim().ifBlank { "https://example.com" }
                    val params = buildList {
                        if (source.isNotBlank()) add("utm_source=${URLEncoder.encode(source.trim(), "UTF-8")}")
                        if (medium.isNotBlank()) add("utm_medium=${URLEncoder.encode(medium.trim(), "UTF-8")}")
                        if (campaign.isNotBlank()) add("utm_campaign=${URLEncoder.encode(campaign.trim(), "UTF-8")}")
                        if (term.isNotBlank()) add("utm_term=${URLEncoder.encode(term.trim(), "UTF-8")}")
                        if (content.isNotBlank()) add("utm_content=${URLEncoder.encode(content.trim(), "UTF-8")}")
                    }
                    if (params.isEmpty()) cleanBase else {
                        val sep = if ('?' in cleanBase) "&" else "?"
                        "$cleanBase$sep${params.joinToString("&")}"
                    }
                }
                outputText = builtUrl
                val qr = remember(builtUrl) { ShareMediaUtils.generateQrBitmap(builtUrl, 440) }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelTextField(baseUrl, { baseUrl = it }, "Destination URL (https://...)")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(source, { source = it }, "utm_source", modifier = Modifier.weight(1f))
                            PixelTextField(medium, { medium = it }, "utm_medium", modifier = Modifier.weight(1f))
                        }
                        PixelTextField(campaign, { campaign = it }, "utm_campaign")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(term, { term = it }, "utm_term (optional)", modifier = Modifier.weight(1f))
                            PixelTextField(content, { content = it }, "utm_content (optional)", modifier = Modifier.weight(1f))
                        }
                        if (qr != null) {
                            Image(qr.asImageBitmap(), "Campaign QR", modifier = Modifier.size(180.dp).border(1.dp, palette.hairline).padding(6.dp))
                        }
                    }
                }
            }

            "soc-og" -> {
                var title by remember { mutableStateOf("") }
                var desc by remember { mutableStateOf("") }
                var url by remember { mutableStateOf("") }
                var imageUrl by remember { mutableStateOf("") }
                var robotsAllow by remember { mutableStateOf(true) }

                val built = buildString {
                    appendLine("<!-- OpenGraph & Twitter Card Meta Tags -->")
                    appendLine("<meta property=\"og:type\" content=\"website\" />")
                    if (title.isNotBlank()) {
                        appendLine("<meta property=\"og:title\" content=\"${title.trim()}\" />")
                        appendLine("<meta name=\"twitter:title\" content=\"${title.trim()}\" />")
                    }
                    if (desc.isNotBlank()) {
                        appendLine("<meta name=\"description\" content=\"${desc.trim()}\" />")
                        appendLine("<meta property=\"og:description\" content=\"${desc.trim()}\" />")
                        appendLine("<meta name=\"twitter:description\" content=\"${desc.trim()}\" />")
                    }
                    if (url.isNotBlank()) appendLine("<meta property=\"og:url\" content=\"${url.trim()}\" />")
                    if (imageUrl.isNotBlank()) {
                        appendLine("<meta property=\"og:image\" content=\"${imageUrl.trim()}\" />")
                        appendLine("<meta name=\"twitter:card\" content=\"summary_large_image\" />")
                        appendLine("<meta name=\"twitter:image\" content=\"${imageUrl.trim()}\" />")
                    }
                    appendLine()
                    appendLine("# robots.txt")
                    appendLine("User-agent: *")
                    appendLine(if (robotsAllow) "Allow: /" else "Disallow: /")
                }.trim()
                outputText = built

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(title, { title = it }, "Page Title")
                        PixelTextField(desc, { desc = it }, "Meta Description", singleLine = false, minHeight = 72.dp)
                        PixelTextField(url, { url = it }, "Canonical URL (https://...)")
                        PixelTextField(imageUrl, { imageUrl = it }, "Preview Image URL (1200×630)")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip("Allow Indexing", selected = robotsAllow, chapterHue = hue, onClick = { robotsAllow = true })
                            PixelChip("Disallow Crawlers", selected = !robotsAllow, chapterHue = hue, onClick = { robotsAllow = false })
                        }
                    }
                }
            }

            "soc-vcard" -> {
                var fullName by remember { mutableStateOf("") }
                var org by remember { mutableStateOf("") }
                var phone by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var site by remember { mutableStateOf("") }

                val vcard = buildString {
                    appendLine("BEGIN:VCARD")
                    appendLine("VERSION:3.0")
                    appendLine("FN:${fullName.trim()}")
                    if (org.isNotBlank()) appendLine("ORG:${org.trim()}")
                    if (phone.isNotBlank()) appendLine("TEL;TYPE=CELL:${phone.trim()}")
                    if (email.isNotBlank()) appendLine("EMAIL:${email.trim()}")
                    if (site.isNotBlank()) appendLine("URL:${site.trim()}")
                    append("END:VCARD")
                }
                outputText = if (fullName.isNotBlank()) vcard else ""
                val qr = remember(vcard, fullName) { if (fullName.isNotBlank()) ShareMediaUtils.generateQrBitmap(vcard, 480) else null }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelTextField(fullName, { fullName = it }, "Full Name")
                        PixelTextField(org, { org = it }, "Company / Role")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelTextField(phone, { phone = it }, "Phone", modifier = Modifier.weight(1f))
                            PixelTextField(email, { email = it }, "Email", modifier = Modifier.weight(1f))
                        }
                        PixelTextField(site, { site = it }, "Website (https://...)")
                        if (qr != null) {
                            Image(qr.asImageBitmap(), "vCard QR", modifier = Modifier.size(190.dp).border(1.dp, palette.hairline).padding(6.dp))
                            PixelButton("Share .vcf Contact Card", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                                val f = File(context.cacheDir, "${fullName.trim().replace(' ', '_')}.vcf")
                                f.writeText(vcard)
                                ShareMediaUtils.shareFile(context, f, "text/vcard", "Share Contact vCard")
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            "soc-curl" -> {
                var method by remember { mutableStateOf("GET") }
                var endpoint by remember(initialText) { mutableStateOf(if (initialText.startsWith("http")) initialText else "https://httpbin.org/get") }
                var headersRaw by remember { mutableStateOf("Accept: application/json") }
                var bodyRaw by remember { mutableStateOf("") }
                var running by remember { mutableStateOf(false) }
                var statusLine by remember { mutableStateOf("IDLE") }

                val curlSnippet = buildString {
                    append("curl -X $method \"$endpoint\"")
                    headersRaw.lines().map { it.trim() }.filter { ':' in it }.forEach { h ->
                        append(" -H \"$h\"")
                    }
                    if (method in listOf("POST", "PUT", "PATCH") && bodyRaw.isNotBlank()) {
                        append(" -d '${bodyRaw.replace("'", "\\'")}'")
                    }
                }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("GET", "POST", "PUT", "DELETE", "HEAD").forEach { m ->
                                PixelChip(m, selected = method == m, chapterHue = hue, onClick = { method = m })
                            }
                        }
                        PixelTextField(endpoint, { endpoint = it }, "URL (https://...)")
                        PixelTextField(headersRaw, { headersRaw = it }, "Headers (Key: Value per line)", singleLine = false, minHeight = 64.dp)
                        if (method in listOf("POST", "PUT", "PATCH")) {
                            PixelTextField(bodyRaw, { bodyRaw = it }, "JSON / Raw Request Body", singleLine = false, minHeight = 84.dp)
                        }
                        DotMatrixReadout(statusLine, "LIVE HTTP PROBE", hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton(if (running) "Sending..." else "Send Request", enabled = !running && endpoint.isNotBlank(), chapterHue = hue, onClick = {
                                running = true
                                scope.launch {
                                    val t0 = System.nanoTime()
                                    val res = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
                                            val reqB = Request.Builder().url(endpoint.trim())
                                            headersRaw.lines().map { it.trim() }.filter { ':' in it }.forEach { line ->
                                                val idx = line.indexOf(':')
                                                reqB.addHeader(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
                                            }
                                            val reqBody = if (method in listOf("POST", "PUT", "PATCH")) bodyRaw.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()) else null
                                            reqB.method(method, reqBody)
                                            client.newCall(reqB.build()).execute().use { resp ->
                                                val txt = resp.body?.string().orEmpty()
                                                Triple(resp.code, resp.headers.toString(), txt)
                                            }
                                        }
                                    }
                                    val ms = (System.nanoTime() - t0) / 1_000_000L
                                    res.fold(
                                        onSuccess = { (code, hdrs, body) ->
                                            statusLine = "HTTP $code (${ms}ms)"
                                            outputText = "=== $method $endpoint → HTTP $code (${ms}ms) ===\n$hdrs\n${body.take(8000)}"
                                            val host = runCatching { Uri.parse(endpoint).host ?: endpoint }.getOrDefault(endpoint)
                                            onLogNetworkAudit("soc-curl", host, bodyRaw.length.toLong(), body.length.toLong(), "HTTP $code (${ms}ms)")
                                        },
                                        onFailure = { err ->
                                            statusLine = "ERROR (${ms}ms)"
                                            outputText = "Request failed: ${err.message}\n\ncURL:\n$curlSnippet"
                                        }
                                    )
                                    running = false
                                }
                            }, modifier = Modifier.weight(1f))
                            PixelButton("Copy cURL", variant = PixelButtonVariant.OUTLINE, onClick = { outputText = curlSnippet }, modifier = Modifier.weight(0.7f))
                        }
                    }
                }
            }

            "soc-deeplink" -> {
                var scheme by remember { mutableStateOf("mailto:") }
                var target by remember { mutableStateOf("") }
                var subject by remember { mutableStateOf("") }
                var body by remember { mutableStateOf("") }

                val builtUri = when (scheme) {
                    "mailto:" -> buildString {
                        append("mailto:${target.trim()}")
                        val qs = buildList {
                            if (subject.isNotBlank()) add("subject=${URLEncoder.encode(subject, "UTF-8")}")
                            if (body.isNotBlank()) add("body=${URLEncoder.encode(body, "UTF-8")}")
                        }
                        if (qs.isNotEmpty()) append("?${qs.joinToString("&")}")
                    }
                    "sms:" -> "sms:${target.trim()}" + if (body.isNotBlank()) "?body=${URLEncoder.encode(body, "UTF-8")}" else ""
                    "tel:" -> "tel:${target.trim()}"
                    "geo:" -> "geo:${target.trim()}" + if (subject.isNotBlank()) "?q=${URLEncoder.encode(subject, "UTF-8")}" else ""
                    else -> target.trim()
                }
                outputText = builtUri
                val qr = remember(builtUri) { if (target.isNotBlank()) ShareMediaUtils.generateQrBitmap(builtUri, 440) else null }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("mailto:", "tel:", "sms:", "geo:", "https://").forEach { s ->
                                PixelChip(s, selected = scheme == s, chapterHue = hue, onClick = { scheme = s })
                            }
                        }
                        PixelTextField(target, { target = it }, when (scheme) {
                            "mailto:" -> "Recipient email"
                            "geo:" -> "lat,lon (e.g. 28.6139,77.2090)"
                            "tel:", "sms:" -> "Phone number"
                            else -> "Deep link URI"
                        })
                        if (scheme in listOf("mailto:", "geo:")) {
                            PixelTextField(subject, { subject = it }, if (scheme == "geo:") "Pin Label" else "Email Subject")
                        }
                        if (scheme in listOf("mailto:", "sms:")) {
                            PixelTextField(body, { body = it }, "Message body", singleLine = false, minHeight = 72.dp)
                        }
                        if (qr != null) Image(qr.asImageBitmap(), "Deep Link QR", modifier = Modifier.size(180.dp).border(1.dp, palette.hairline).padding(6.dp))
                        PixelButton("Launch Deep Link Intent", enabled = target.isNotBlank(), chapterHue = hue, onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(builtUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { onStatusToast("No handler for URI: ${it.message}") }
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            else -> { // soc-biopage
                var displayName by remember { mutableStateOf("") }
                var bio by remember { mutableStateOf("") }
                var accentHex by remember { mutableStateOf("#FF4F00") }
                var linksRaw by remember { mutableStateOf("Website | https://example.com\nGitHub | https://github.com") }

                val htmlPage = remember(displayName, bio, accentHex, linksRaw) {
                    val linksHtml = linksRaw.lines().map { it.trim() }.filter { '|' in it }.joinToString("\n") { line ->
                        val (lbl, url) = line.split('|', limit = 2).map { it.trim() }
                        """      <a class="btn" href="$url" target="_blank" rel="noopener">$lbl</a>"""
                    }
                    """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${displayName.ifBlank { "My Links" }}</title>
<style>
  body{font-family:system-ui,sans-serif;background:#F7F7F5;color:#141413;margin:0;padding:40px 20px;display:flex;justify-content:center}
  main{max-width:460px;width:100%}
  h1{margin:0 0 8px;font-size:28px}
  p{color:#4E4E4A;margin:0 0 24px;line-height:1.5}
  .btn{display:block;padding:14px 18px;margin-bottom:12px;border-radius:12px;background:$accentHex;color:#fff;text-decoration:none;font-weight:600;text-align:center}
</style>
</head>
<body>
  <main>
    <h1>${displayName.ifBlank { "Your Name" }}</h1>
    <p>${bio.ifBlank { "Creator · Developer · Links" }}</p>
$linksHtml
  </main>
</body>
</html>"""
                }
                outputText = htmlPage

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(displayName, { displayName = it }, "Display Name / Handle")
                        PixelTextField(bio, { bio = it }, "Short Bio")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Orange" to "#FF4F00", "Cobalt" to "#0052FF", "Emerald" to "#0E9F6E", "Ink" to "#141413").forEach { (n, hx) ->
                                PixelChip(n, selected = accentHex == hx, chapterHue = hue, onClick = { accentHex = hx })
                            }
                        }
                        PixelTextField(linksRaw, { linksRaw = it }, "Label | https://url per line", singleLine = false, minHeight = 100.dp)
                        PixelButton("Export & Share index.html", chapterHue = hue, onClick = {
                            val f = File(context.cacheDir, "omni_bio.html")
                            f.writeText(htmlPage)
                            ShareMediaUtils.shareFile(context, f, "text/html", "Share Bio Micro-Page HTML")
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
