package com.example.tools.urlcheck

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.net.UrlInspectionReport
import com.example.lib.net.UrlInspectorEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard
import kotlinx.coroutines.launch
import java.net.URI

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UrlSafetyScreen(
    meta: ToolMeta,
    initialUrl: String,
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
    val hue = ChapterHue.TEAL

    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var batchMode by remember { mutableStateOf(false) }
    var checkDns by remember { mutableStateOf(true) }
    var checkRdapGeo by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<UrlInspectionReport?>(null) }
    val batchReports = remember { mutableStateListOf<UrlInspectionReport>() }
    val watchlist = remember { mutableStateListOf<UrlInspectionReport>() }
    var extraDnsType by remember { mutableStateOf("A") }
    var extraDnsOutput by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }

    val quickStrip = remember(urlInput) {
        if (urlInput.isBlank() || batchMode) null else UrlInspectorEngine.stripTrackingParams(UrlInspectorEngine.normalizeUrl(urlInput))
    }

    val reportText = remember(report, batchReports.size, quickStrip) {
        if (batchReports.isNotEmpty()) {
            buildString {
                appendLine("url,final_url,risk_score,hops,findings")
                batchReports.forEach { r ->
                    appendLine("\"${r.originalUrl}\",\"${r.finalUrl}\",${r.riskScore},${r.hops.size},\"${r.riskFindings.joinToString("; ")}\"")
                }
            }.trim()
        } else {
            val r = report
            if (r != null) {
                buildString {
                    appendLine("Original: ${r.originalUrl}")
                    appendLine("Final Destination: ${r.finalUrl}")
                    appendLine("Clean (No Trackers): ${r.cleanedUrl}")
                    appendLine("Risk Score: ${r.riskScore}/100")
                    r.riskFindings.forEach { appendLine("- $it") }
                    appendLine("Hops:")
                    r.hops.forEach { appendLine("  [${it.step}] ${it.statusCode} (${it.latencyMs}ms) -> ${it.url}") }
                    if (r.securityHeaders.isNotEmpty()) {
                        appendLine("Security Headers: " + r.securityHeaders.joinToString(", ") { "${it.header}=${if (it.present) "OK" else "MISSING"}" })
                    }
                    r.tlsCert?.let { appendLine("TLS: ${it.protocol} | Issuer: ${it.issuer} | Valid: ${it.validUntil}") }
                    if (r.rdapSummary.isNotBlank()) appendLine(r.rdapSummary)
                    if (r.ipGeoSummary.isNotBlank()) appendLine(r.ipGeoSummary)
                    appendLine("DNS: ${r.dnsSummary}")
                }.trim()
            } else quickStrip?.first.orEmpty()
        }
    }

    ToolWorkbenchChrome(
        meta = meta, isPinned = isPinned, onTogglePin = onTogglePin, onBack = onBack,
        outputText = reportText, outputFileName = if (batchReports.isNotEmpty()) "url_batch_audit.csv" else "url_audit_${System.currentTimeMillis() / 1000}.txt",
        outputKind = if (batchReports.isNotEmpty()) DataKind.CSV else DataKind.TEXT,
        onSaveToFiles = onSaveToFiles, onSendToTool = onSendToTool, onStatusToast = onStatusToast,
        helpParagraph = "Trace redirects without executing page JS, audit TLS & security headers (HSTS/CSP/XFO), detect typosquatting/punycode/open-redirects, parse login forms & ad trackers, lookup RDAP/IP ASN, run Batch CSV scans & keep snapshot watchlists."
    ) {
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (batchMode) "BATCH URL SCANNER (1 PER LINE)" else "URL TO INSPECT & UNSHORTEN", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelChip(label = if (batchMode) "Single Mode" else "Batch CSV", selected = batchMode, chapterHue = hue, onClick = { batchMode = !batchMode })
                }
                PixelTextField(
                    value = urlInput, onValueChange = { urlInput = it },
                    placeholder = if (batchMode) "https://bit.ly/...\nhttps://example.com" else "https://bit.ly/... or paste any link",
                    singleLine = !batchMode, minHeight = if (batchMode) 100.dp else 48.dp, chapterHue = hue
                )
                PixelSwitch("DNS-over-HTTPS (DoH) lookup", checkDns, { checkDns = it }, hue)
                PixelSwitch("RDAP Domain Age & IP ASN / Geo lookup", checkRdapGeo, { checkRdapGeo = it }, hue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton(
                        text = if (loading) "Scanning…" else if (batchMode) "Batch Audit" else "Inspect Link",
                        chapterHue = hue, enabled = !loading && urlInput.isNotBlank(), modifier = Modifier.weight(1f),
                        onClick = {
                            loading = true
                            scope.launch {
                                if (batchMode) {
                                    batchReports.clear()
                                    val urls = urlInput.lines().map { it.trim() }.filter { it.isNotBlank() }.take(15)
                                    urls.forEach { u -> batchReports += UrlInspectorEngine.inspect(u, checkDns, checkRdapGeo) }
                                    report = batchReports.firstOrNull()
                                    onLogNetworkAudit(meta.id, "batch(${urls.size})", 256L * urls.size, batchReports.sumOf { it.bytesTransferred }, "Batch audited ${urls.size} URLs")
                                } else {
                                    batchReports.clear()
                                    val res = UrlInspectorEngine.inspect(urlInput, checkDns, checkRdapGeo)
                                    report = res; extraDnsOutput = res.dnsSummary
                                    val host = runCatching { URI(res.finalUrl).host ?: res.finalUrl }.getOrDefault(res.finalUrl)
                                    onLogNetworkAudit(meta.id, host, 128L, res.bytesTransferred, "Risk ${res.riskScore}/100 · ${res.hops.size} hop(s)")
                                }
                                loading = false
                            }
                        }
                    )
                    if (quickStrip != null && quickStrip.second.isNotEmpty()) {
                        PixelButton(text = "Copy Clean", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                            copyToClipboard(context, "Clean URL", quickStrip.first)
                            onStatusToast("Stripped ${quickStrip.second.size} tracker param(s) & copied.")
                        })
                    }
                }
            }
        }

        if (batchReports.isNotEmpty()) {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BATCH AUDIT SUMMARY (${batchReports.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                    batchReports.forEach { r ->
                        val riskCol = when { r.riskScore >= 50 -> palette.err; r.riskScore >= 20 -> palette.warn; else -> palette.ok }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.finalUrl, style = DotkitType.monoData(11), color = palette.ink, maxLines = 1)
                                Text("${r.hops.size} hop(s) · ${r.riskFindings.firstOrNull() ?: "Clean"}", style = DotkitType.monoLabel(9), color = palette.ink3, maxLines = 1)
                            }
                            PixelStamp("${r.riskScore}/100", riskCol)
                        }
                    }
                }
            }
        }

        report?.let { r ->
            val riskColor = when { r.riskScore >= 50 -> palette.err; r.riskScore >= 20 -> palette.warn; else -> palette.ok }
            val riskLabel = when { r.riskScore >= 50 -> "HIGH RISK (${r.riskScore}/100)"; r.riskScore >= 20 -> "CAUTION (${r.riskScore}/100)"; else -> "LOW RISK (${r.riskScore}/100)" }

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("DESTINATION & VERDICT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelStamp(riskLabel, riskColor)
                    }
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CLEANED FINAL URL", style = DotkitType.monoLabel(9), color = palette.ink3)
                        Text(r.cleanedUrl, style = DotkitType.monoData(12), color = palette.ink)
                        if (r.punycodeHost.isNotBlank()) Text("Host: ${r.punycodeHost}", style = DotkitType.monoLabel(10), color = if (r.hasHomographRisk) palette.err else palette.ink2)
                        if (r.rdapSummary.isNotBlank()) Text(r.rdapSummary, style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (r.ipGeoSummary.isNotBlank()) Text(r.ipGeoSummary, style = DotkitType.monoLabel(10), color = palette.ink2)
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelChip(label = "Copy Clean URL", chapterHue = hue, onClick = { copyToClipboard(context, "Clean URL", r.cleanedUrl); onStatusToast("Copied clean destination URL.") })
                        PixelChip(label = "+ Watchlist", chapterHue = hue, onClick = {
                            if (watchlist.none { it.finalUrl == r.finalUrl }) watchlist.add(0, r)
                            onStatusToast("Pinned snapshot to Watchlist.")
                        })
                        PixelChip(label = "Open in Browser", chapterHue = hue, onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.cleanedUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        })
                        PixelChip(label = if (showQr) "Hide QR" else "QR Code", selected = showQr, chapterHue = hue, onClick = { showQr = !showQr })
                    }

                    if (showQr) {
                        val qr = remember(r.cleanedUrl) { ShareMediaUtils.generateQrBitmap(r.cleanedUrl, 360) }
                        Image(bitmap = qr.asImageBitmap(), contentDescription = "QR Code for URL", modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally).border(1.dp, palette.hairline))
                    }

                    if (r.riskFindings.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("SECURITY & PRIVACY FINDINGS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        r.riskFindings.forEach { f -> Text("• $f", style = DotkitType.bodyUi(13), color = palette.ink) }
                    }

                    if (r.securityHeaders.isNotEmpty()) {
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("HTTP SECURITY HEADERS (${r.securityHeaders.count { it.present }}/${r.securityHeaders.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.securityHeaders.forEach { h ->
                                PixelStamp("${if (h.present) "✓" else "✕"} ${h.header}", if (h.present) palette.ok else palette.warn)
                            }
                        }
                    }

                    r.htmlScan?.let { html ->
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("SAFE HTML METADATA SCAN (NO JS EXECUTED)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (html.pageTitle.isNotBlank()) Text("Title: ${html.pageTitle}", style = DotkitType.bodyUi(12, bold = true), color = palette.ink)
                        if (html.metaDescription.isNotBlank()) Text(html.metaDescription, style = DotkitType.bodyUi(11), color = palette.ink2, maxLines = 2)
                        Text("Password Form: ${if (html.hasPasswordForm) "YES" else "No"} · Iframes: ${html.iframeCount} · External Scripts: ${html.externalScripts}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        if (html.trackerMatches.isNotEmpty()) Text("Trackers detected: ${html.trackerMatches.joinToString(", ")}", style = DotkitType.monoLabel(10), color = palette.warn)
                    }

                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Text("REDIRECT HOP TRACE (${r.hops.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                    r.hops.forEach { hop ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PixelStamp("#${hop.step} ${hop.statusCode}", if (hop.statusCode in 200..299) palette.ok else palette.chapterColor(hue))
                            Column(Modifier.weight(1f)) {
                                Text(hop.url, style = DotkitType.monoData(11), color = palette.ink, maxLines = 2)
                                Text("${hop.latencyMs}ms ${hop.serverHeader}", style = DotkitType.monoLabel(9), color = palette.ink3)
                            }
                        }
                    }

                    r.tlsCert?.let { tls ->
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("TLS / SSL CERTIFICATE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text("${tls.protocol} · ${tls.cipherSuite}", style = DotkitType.monoData(12), color = palette.ink)
                        Text("Subject: ${tls.subject}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text("Issuer: ${tls.issuer}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text("Valid: ${tls.validFrom} → ${tls.validUntil}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text("SHA-256: ${tls.sha256Fingerprint}", style = DotkitType.monoLabel(9), color = palette.ink3)
                    }

                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Text("DNS RECORDS (DoH)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("A", "AAAA", "MX", "TXT", "NS", "CNAME").forEach { rec ->
                            PixelChip(label = rec, selected = extraDnsType == rec, chapterHue = hue, onClick = {
                                extraDnsType = rec
                                scope.launch {
                                    val host = runCatching { URI(r.finalUrl).host.orEmpty() }.getOrDefault("")
                                    if (host.isNotBlank()) extraDnsOutput = UrlInspectorEngine.queryDoh(domain = host, recordType = rec)
                                }
                            })
                        }
                    }
                    Text(extraDnsOutput, style = DotkitType.monoData(11), color = palette.ink)
                }
            }
        }

        if (watchlist.isNotEmpty()) {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SAVED SNAPSHOT WATCHLIST (${watchlist.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                    watchlist.forEach { w ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${w.finalUrl} (${w.riskScore}/100)", style = DotkitType.monoData(11), color = palette.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            PixelChip(label = "Re-check", onClick = { urlInput = w.originalUrl; batchMode = false })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
