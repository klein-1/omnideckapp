package com.example.tools.server

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.server.CustomMockRoute
import com.example.services.server.LocalHttpServerEngine
import com.example.tools.common.ShareMediaUtils
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalHttpServerScreen(
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
    val hue = ChapterHue.FOREST

    val status by LocalHttpServerEngine.status.collectAsStateWithLifecycle()
    val logs by LocalHttpServerEngine.logs.collectAsStateWithLifecycle()
    val mockRoutes by LocalHttpServerEngine.routes.collectAsStateWithLifecycle()

    var portText by remember { mutableStateOf("8080") }
    var useHttps by remember { mutableStateOf(false) }
    var pickedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var enableAuth by remember { mutableStateOf(false) }
    var authUser by remember { mutableStateOf("admin") }
    var authPass by remember { mutableStateOf("") }
    var corsEnabled by remember { mutableStateOf(true) }
    var wakeLockEnabled by remember { mutableStateOf(false) }
    var rateLimitRpm by remember { mutableStateOf("0") }
    var ipAllowCsv by remember { mutableStateOf("") }
    var ipBlockCsv by remember { mutableStateOf("") }
    var showSecurityPanel by remember { mutableStateOf(false) }
    var showQrForUrl by remember { mutableStateOf<String?>(null) }

    // Mock API Builder state
    var routeMethod by remember { mutableStateOf("GET") }
    var routePath by remember { mutableStateOf("/api/mock") }
    var routeStatus by remember { mutableStateOf("200") }
    var routeDelayMs by remember { mutableStateOf("0") }
    var routeJson by remember { mutableStateOf("""{"ok":true,"source":"omnideck"}""") }
    var logFilter by remember { mutableStateOf("ALL") } // ALL, ERR, API

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            pickedFolderUri = uri
            onStatusToast("Selected shared folder.")
        }
    }

    val filteredLogs = remember(logs, logFilter) {
        when (logFilter) {
            "ERR" -> logs.filter { it.status >= 400 }
            "API" -> logs.filter { it.path.startsWith("/api/") || it.path.startsWith("/webhook/") }
            else -> logs
        }
    }

    val exportLogText = remember(status, filteredLogs) {
        buildString {
            appendLine("timestamp,client_ip,method,path,status,bytes,payload")
            filteredLogs.take(60).forEach { l ->
                appendLine("${l.timestamp},${l.clientIp},${l.method},\"${l.path}\",${l.status},${l.bytesOut},\"${l.payloadPreview.replace("\"", "'")}\"")
            }
        }.trim()
    }

    ToolWorkbenchChrome(
        meta = meta, isPinned = isPinned, onTogglePin = onTogglePin, onBack = onBack,
        outputText = exportLogText, outputFileName = "shttps_logs_${System.currentTimeMillis() / 1000}.csv",
        outputKind = DataKind.CSV, onSaveToFiles = onSaveToFiles, onSendToTool = onSendToTool, onStatusToast = onStatusToast,
        helpParagraph = "Host any device folder over LAN Wi-Fi using HTTP or self-signed HTTPS (TLS) with browser file manager, /__zip download archive, custom Mock REST API routes, IP allow/block & rate limiter, WakeLock keep-alive & live webhook inspector."
    ) {
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("SERVER CONFIGURATION", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelStamp(if (status.running) "ONLINE" else "STOPPED", if (status.running) palette.ok else palette.ink3)
                }

                PixelSwitch("Use HTTPS (Auto X.509 v3 TLS Cert)", useHttps, { useHttps = it }, hue)
                PixelSwitch("Enable CORS Headers (*)", corsEnabled, { corsEnabled = it }, hue)
                PixelSwitch("Keep CPU Awake (WakeLock)", wakeLockEnabled, { wakeLockEnabled = it }, hue)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("PORT", style = DotkitType.monoLabel(11), color = palette.ink2)
                    PixelNumberField(value = portText, onValueChange = { portText = it }, placeholder = "8080", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(1f))
                    PixelChip(label = "Auto Free", chapterHue = hue, onClick = { portText = LocalHttpServerEngine.findFreePort().toString() })
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ROOT DIRECTORY", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(pickedFolderUri?.lastPathSegment ?: "App Sandbox WebRoot (default)", style = DotkitType.bodyUi(13), color = palette.ink, maxLines = 1)
                    }
                    PixelChip(label = "Pick Folder", chapterHue = hue, onClick = { folderPickerLauncher.launch(null) })
                    if (pickedFolderUri != null) PixelChip(label = "Reset", onClick = { pickedFolderUri = null })
                }

                PixelSwitch("Require Basic Auth", enableAuth, { enableAuth = it }, hue)
                if (enableAuth) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelTextField(value = authUser, onValueChange = { authUser = it }, placeholder = "Username", chapterHue = hue, modifier = Modifier.weight(1f))
                        PixelTextField(value = authPass, onValueChange = { authPass = it }, placeholder = "Password", chapterHue = hue, modifier = Modifier.weight(1f))
                    }
                }

                Row(Modifier.fillMaxWidth().clickable { showSecurityPanel = !showSecurityPanel }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("IP FILTER & RATE LIMITER", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Text(if (showSecurityPanel) "−" else "+", style = DotkitType.monoData(14), color = palette.ink)
                }
                if (showSecurityPanel) {
                    PixelNumberField(value = rateLimitRpm, onValueChange = { rateLimitRpm = it }, placeholder = "Max reqs/min/IP (0 = unlimited)", allowDecimal = false, chapterHue = hue)
                    PixelTextField(value = ipAllowCsv, onValueChange = { ipAllowCsv = it }, placeholder = "IP Allowlist CSV (blank = allow all)", chapterHue = hue)
                    PixelTextField(value = ipBlockCsv, onValueChange = { ipBlockCsv = it }, placeholder = "IP Blocklist CSV (optional)", chapterHue = hue)
                }

                if (status.running) {
                    PixelButton(text = "Stop Server", variant = PixelButtonVariant.DESTRUCTIVE, modifier = Modifier.fillMaxWidth(), onClick = { LocalHttpServerEngine.stop() })
                } else {
                    PixelButton(
                        text = if (useHttps) "Start HTTPS Server" else "Start HTTP Server",
                        chapterHue = hue, modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val port = portText.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080
                            portText = port.toString()
                            LocalHttpServerEngine.start(
                                context = context, port = port, useHttps = useHttps, safTreeUri = pickedFolderUri,
                                enableAuth = enableAuth, username = authUser, password = authPass,
                                corsEnabled = corsEnabled, rateLimitRpm = rateLimitRpm.toIntOrNull() ?: 0,
                                ipAllowList = ipAllowCsv, ipBlockList = ipBlockCsv, keepWakeLock = wakeLockEnabled
                            )
                        }
                    )
                }
                status.error?.let { Text(it, style = DotkitType.bodyUi(12), color = palette.err) }
            }
        }

        // Dynamic Mock REST API Builder
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MOCK REST API & WEBHOOK ROUTES", style = DotkitType.monoLabel(10), color = palette.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                        PixelChip(label = m, selected = routeMethod == m, chapterHue = hue, onClick = { routeMethod = m })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelTextField(value = routePath, onValueChange = { routePath = it }, placeholder = "/api/custom", chapterHue = hue, modifier = Modifier.weight(1.4f))
                    PixelNumberField(value = routeStatus, onValueChange = { routeStatus = it }, placeholder = "200", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(0.8f))
                    PixelNumberField(value = routeDelayMs, onValueChange = { routeDelayMs = it }, placeholder = "Delay ms", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(0.8f))
                }
                PixelTextField(value = routeJson, onValueChange = { routeJson = it }, placeholder = "{\"ok\":true}", chapterHue = hue)
                PixelButton(text = "+ Register Mock Endpoint", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, onClick = {
                    val p = if (routePath.startsWith("/")) routePath else "/$routePath"
                    LocalHttpServerEngine.addMockRoute(
                        CustomMockRoute(routeMethod, p, routeStatus.toIntOrNull()?.coerceIn(100, 599) ?: 200, routeDelayMs.toIntOrNull() ?: 0, routeJson)
                    )
                    onStatusToast("Registered $routeMethod $p")
                })
                mockRoutes.forEach { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.method} ${r.path} → ${r.statusCode} (${r.delayMs}ms)", style = DotkitType.monoData(11), color = palette.ink, modifier = Modifier.weight(1f))
                        PixelChip(label = "✕", onClick = { LocalHttpServerEngine.removeMockRoute(r.method, r.path) })
                    }
                }
            }
        }

        if (status.running) {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ACTIVE LAN ENDPOINTS (${status.activeRequests} reqs)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    status.localUrls.forEach { url ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(palette.paper).border(1.dp, palette.hairline, RoundedCornerShape(6.dp)).padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(url, style = DotkitType.monoData(13), color = palette.chapterColor(hue), modifier = Modifier.weight(1f).clickable {
                                copyToClipboard(context, "Server URL", url); onStatusToast("Copied $url")
                            })
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PixelChip(label = "QR", chapterHue = hue, onClick = { showQrForUrl = if (showQrForUrl == url) null else url })
                                PixelChip(label = "Open", onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
                            }
                        }
                    }
                    Text("Built-in: /api/health · /api/echo · /webhook/<id> · /__zip", style = DotkitType.monoLabel(10), color = palette.ink2)
                    showQrForUrl?.let { qrUrl ->
                        val bmp = remember(qrUrl) { ShareMediaUtils.generateQrBitmap(qrUrl, 360) }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Server QR", modifier = Modifier.size(160.dp).border(1.dp, palette.hairline))
                            Text(qrUrl, style = DotkitType.monoLabel(10), color = palette.ink2)
                        }
                    }
                    if (status.tlsSha256Fingerprint.isNotBlank()) Text("TLS SHA-256: ${status.tlsSha256Fingerprint}", style = DotkitType.monoLabel(9), color = palette.ink3)
                }
            }
        }

        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ACCESS & WEBHOOK LOGS (${filteredLogs.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("ALL", "ERR", "API").forEach { f -> PixelChip(label = f, selected = logFilter == f, chapterHue = hue, onClick = { logFilter = f }) }
                        if (logs.isNotEmpty()) PixelChip(label = "Clear", onClick = { LocalHttpServerEngine.clearLogs() })
                    }
                }
                if (filteredLogs.isEmpty()) {
                    Text("No matching requests yet.", style = DotkitType.bodyUi(12), color = palette.ink3)
                } else {
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    filteredLogs.take(25).forEach { entry ->
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${entry.timestamp} ${entry.method} ${entry.path}", style = DotkitType.monoData(11), color = palette.ink, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${entry.status} · ${entry.bytesOut}B", style = DotkitType.monoLabel(10), color = if (entry.status < 400) palette.ok else palette.err)
                            }
                            if (entry.payloadPreview.isNotBlank()) {
                                Text("↳ ${entry.payloadPreview}", style = DotkitType.monoLabel(9), color = palette.ink3, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
