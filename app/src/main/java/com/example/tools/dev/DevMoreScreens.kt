package com.example.tools.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelTextField
import com.example.design.SwissControlShape
import com.example.lib.dev.Base32HexEngine
import com.example.lib.dev.SqlFormatterEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevMoreScreen(
    meta: ToolMeta,
    initialText: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.FOREST
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
            "dev-sqlfmt" -> {
                var sqlInput by remember(initialText) {
                    mutableStateOf(initialText.ifBlank { "select u.id, u.email, count(o.id) from users u left join orders o on u.id = o.user_id where u.active = 1 group by u.id order by count(o.id) desc limit 20;" })
                }
                val formatted = remember(sqlInput) { SqlFormatterEngine.formatSql(sqlInput) }
                val lints = remember(sqlInput) { SqlFormatterEngine.lintSql(sqlInput) }
                outputText = formatted

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SQL QUERY / DDL INPUT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(sqlInput, { sqlInput = it }, "Paste SQL query or CREATE TABLE…", singleLine = false, minHeight = 100.dp, chapterHue = hue)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Format SQL", chapterHue = hue, modifier = Modifier.weight(1f), onClick = { sqlInput = formatted })
                            PixelButton("Minify", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                sqlInput = sqlInput.trim().replace(Regex("\\s+"), " ")
                            })
                        }
                        Text("SYNTAX & SCHEMA LINT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        lints.forEach { msg ->
                            val col = if (msg.startsWith("OK:")) palette.ok else if (msg.startsWith("Warning") || msg.startsWith("Unbalanced") || msg.startsWith("Unclosed")) palette.err else palette.chapterColor(hue)
                            Text("• $msg", style = DotkitType.monoData(11), color = col)
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("FORMATTED PREVIEW", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(formatted, style = DotkitType.monoData(12), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(10.dp))
                    }
                }
            }

            "dev-cert" -> {
                var tab by remember { mutableStateOf("HEX") } // HEX, B32, PEM
                var input by remember(initialText) { mutableStateOf(initialText.ifBlank { "Omni Deck 2026" }) }
                var b32DecodeMode by remember { mutableStateOf(false) }

                val computed = remember(tab, input, b32DecodeMode) {
                    when (tab) {
                        "HEX" -> Base32HexEngine.hexDump(input.toByteArray(Charsets.UTF_8))
                        "B32" -> if (!b32DecodeMode) Base32HexEngine.encodeBase32(input.toByteArray(Charsets.UTF_8))
                        else runCatching { Base32HexEngine.decodeBase32(input).toString(Charsets.UTF_8) }.getOrElse { "Invalid Base32: ${it.message}" }
                        else -> Base32HexEngine.inspectPemCertificate(input).getOrElse { "Paste a valid -----BEGIN CERTIFICATE----- X.509 PEM block.\n(${it.message})" }
                    }
                }
                outputText = computed

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("HEX" to "Hex Dump", "B32" to "Base32", "PEM" to "X.509 PEM").forEach { (id, lbl) ->
                                PixelChip(lbl, selected = tab == id, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { tab = id })
                            }
                        }
                        if (tab == "B32") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelChip("Encode -> Base32", selected = !b32DecodeMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { b32DecodeMode = false })
                                PixelChip("Decode -> UTF-8", selected = b32DecodeMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { b32DecodeMode = true })
                            }
                        }
                        PixelTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = if (tab == "PEM") "-----BEGIN CERTIFICATE-----\n..." else "Enter text or encoded Base32…",
                            singleLine = false,
                            minHeight = 110.dp,
                            chapterHue = hue
                        )
                        Text(computed, style = DotkitType.monoData(11), color = palette.ink, modifier = Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(10.dp))
                    }
                }
            }

            else -> { // dev-httpref
                var refGroup by remember { mutableStateOf("HTTP") } // HTTP, MIME, PORTS
                var query by remember { mutableStateOf("") }

                val entries = remember(refGroup) {
                    when (refGroup) {
                        "HTTP" -> listOf(
                            "200 OK" to "Standard success response",
                            "201 Created" to "Resource created via POST/PUT",
                            "204 No Content" to "Success with empty response body",
                            "301 Moved Permanently" to "Permanent URL redirect (cached)",
                            "304 Not Modified" to "Cached ETag/Last-Modified valid",
                            "307 / 308 Redirect" to "Preserve original HTTP method",
                            "400 Bad Request" to "Malformed syntax or schema error",
                            "401 Unauthorized" to "Missing or invalid auth token",
                            "403 Forbidden" to "Authenticated but lacking permissions",
                            "404 Not Found" to "Requested resource does not exist",
                            "409 Conflict" to "State / version conflict",
                            "413 Payload Too Large" to "Body exceeds server limit",
                            "422 Unprocessable" to "Semantic validation failure",
                            "429 Too Many Requests" to "Rate limit exceeded (Retry-After)",
                            "500 Internal Server Error" to "Unhandled backend exception",
                            "502 Bad Gateway" to "Invalid upstream proxy response",
                            "503 Service Unavailable" to "Overloaded or maintenance mode",
                            "504 Gateway Timeout" to "Upstream server timed out"
                        )
                        "MIME" -> listOf(
                            "application/json" to ".json — JSON payload",
                            "application/pdf" to ".pdf — Portable Document Format",
                            "application/zip" to ".zip — ZIP archive",
                            "application/octet-stream" to "Arbitrary binary stream",
                            "multipart/form-data" to "HTML file upload form",
                            "text/plain; charset=utf-8" to ".txt — UTF-8 plain text",
                            "text/html; charset=utf-8" to ".html — HyperText document",
                            "text/csv" to ".csv — Comma-separated values",
                            "image/png" to ".png — Lossless raster",
                            "image/jpeg" to ".jpg — Lossy photo",
                            "image/webp" to ".webp — Modern web raster",
                            "image/svg+xml" to ".svg — Vector graphics",
                            "audio/mpeg" to ".mp3 — MPEG audio",
                            "video/mp4" to ".mp4 — MPEG-4 video"
                        )
                        else -> listOf(
                            "22 TCP" to "SSH — Secure Shell",
                            "53 TCP/UDP" to "DNS — Domain Name System",
                            "80 TCP" to "HTTP — HyperText Transfer",
                            "443 TCP/UDP" to "HTTPS / HTTP3 QUIC",
                            "587 TCP" to "SMTP Submission (TLS)",
                            "993 TCP" to "IMAPS — Secure Mail",
                            "1883 / 8883 TCP" to "MQTT IoT Broker",
                            "3306 TCP" to "MySQL / MariaDB",
                            "5432 TCP" to "PostgreSQL",
                            "6379 TCP" to "Redis In-Memory Store",
                            "8080 / 8443 TCP" to "Alt HTTP / HTTPS App Server",
                            "27017 TCP" to "MongoDB"
                        )
                    }
                }
                val filtered = remember(entries, query) {
                    val q = query.trim().lowercase()
                    if (q.isEmpty()) entries else entries.filter { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
                }
                outputText = filtered.joinToString("\n") { "${it.first}: ${it.second}" }

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("HTTP" to "HTTP Codes", "MIME" to "MIME Types", "PORTS" to "TCP/UDP Ports").forEach { (id, lbl) ->
                                PixelChip(lbl, selected = refGroup == id, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { refGroup = id })
                            }
                        }
                        PixelTextField(query, { query = it }, "Filter codes, extensions, or ports…", chapterHue = hue)
                        filtered.forEach { (k, v) ->
                            Row(
                                Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(k, style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                                Text(v, style = DotkitType.bodyUi(12), color = palette.ink, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
