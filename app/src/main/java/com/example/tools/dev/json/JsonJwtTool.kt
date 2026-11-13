package com.example.tools.dev.json

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.lib.dev.JsonJwtEngine
import com.example.lib.dev.JsonProcessResult
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome

private enum class DevFormatTab(val label: String) {
    JSON("JSON Formatter"),
    JWT("JWT Inspector")
}

private const val SAMPLE_JSON = """{"app":"Dotkit","version":2,"offline":true,"chapters":["Time","Calc","Words","Dev"],"meta":{"grid":"4dp","themes":6}}"""
private const val SAMPLE_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkb3RraXQtdXNlciIsIm5hbWUiOiJTYW1wbGUgVG9rZW4iLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTk5OTk5OTk5OX0.signature_placeholder"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JsonJwtTool(
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

    val initialLooksLikeJwt = initialText.trim().count { it == '.' } == 2 && initialText.trim().startsWith("eyJ")
    var tab by remember { mutableStateOf(if (initialLooksLikeJwt) DevFormatTab.JWT else DevFormatTab.JSON) }

    var jsonInput by remember(initialText) {
        mutableStateOf(if (!initialLooksLikeJwt && initialText.isNotEmpty()) initialText else SAMPLE_JSON)
    }
    var indentSpaces by remember { mutableIntStateOf(2) }
    var sortKeys by remember { mutableStateOf(false) }

    var jwtInput by remember(initialText) {
        mutableStateOf(if (initialLooksLikeJwt) initialText else SAMPLE_JWT)
    }

    val jsonResult = remember(jsonInput, indentSpaces, sortKeys) {
        JsonJwtEngine.formatJson(jsonInput, indentSpaces, sortKeys)
    }
    val jwtResult = remember(jwtInput) {
        JsonJwtEngine.decodeJwt(jwtInput)
    }

    val exportText = when (tab) {
        DevFormatTab.JSON -> (jsonResult as? JsonProcessResult.Valid)?.formatted ?: ""
        DevFormatTab.JWT -> jwtResult.getOrNull()?.let { "HEADER:\n${it.headerJson}\n\nPAYLOAD:\n${it.payloadJson}" } ?: ""
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportText,
        outputFileName = if (tab == DevFormatTab.JSON) "formatted.json" else "jwt_claims.txt",
        outputKind = if (tab == DevFormatTab.JSON) DataKind.JSON else DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = "Format, minify, sort keys, and validate JSON locally. Switch to JWT Inspector to decode Base64URL headers, claims, and human-readable iat/exp timestamps without sending tokens anywhere."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DevFormatTab.entries.forEach { t ->
                PixelChip(label = t.label, selected = tab == t, chapterHue = hue, onClick = { tab = t })
            }
        }

        if (tab == DevFormatTab.JSON) {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RAW JSON", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelChip(label = "Sample", chapterHue = hue, onClick = { jsonInput = SAMPLE_JSON })
                    }
                    PixelTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        placeholder = "Paste JSON object or array…",
                        singleLine = false,
                        minHeight = 96.dp,
                        chapterHue = hue
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelChip(label = "2 Spaces", selected = indentSpaces == 2, chapterHue = hue, onClick = { indentSpaces = 2 })
                        PixelChip(label = "4 Spaces", selected = indentSpaces == 4, chapterHue = hue, onClick = { indentSpaces = 4 })
                        PixelChip(label = "Minify", selected = indentSpaces == 0, chapterHue = hue, onClick = { indentSpaces = 0 })
                    }
                    PixelSwitch("Sort Object Keys A–Z", sortKeys, { sortKeys = it }, hue)

                    when (jsonResult) {
                        is JsonProcessResult.Valid -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                PixelStamp("VALID JSON", palette.ok)
                                Text("${jsonResult.keyCount} keys · depth ${jsonResult.depthEstimate}", style = DotkitType.monoLabel(10), color = palette.ink2)
                            }
                            Text(
                                text = jsonResult.formatted,
                                style = DotkitType.monoData(12),
                                color = palette.ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(palette.paper)
                                    .border(1.5.dp, palette.ink)
                                    .padding(10.dp)
                            )
                        }
                        is JsonProcessResult.Invalid -> {
                            PixelStamp("SYNTAX ERROR", palette.err)
                            Text(jsonResult.error, style = DotkitType.monoData(12), color = palette.err)
                        }
                    }
                }
            }
        } else {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("JWT TOKEN (DECODED LOCALLY)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelChip(label = "Sample", chapterHue = hue, onClick = { jwtInput = SAMPLE_JWT })
                    }
                    PixelTextField(
                        value = jwtInput,
                        onValueChange = { jwtInput = it },
                        placeholder = "eyJhbGciOi...",
                        singleLine = false,
                        minHeight = 80.dp,
                        chapterHue = hue
                    )
                    jwtResult.onSuccess { decoded ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelStamp("ALG: ${decoded.algorithm}", if (decoded.algNoneWarning) palette.err else palette.chapterColor(hue))
                            if (decoded.isExpired == true) PixelStamp("EXPIRED", palette.err)
                            else if (decoded.isExpired == false) PixelStamp("ACTIVE", palette.ok)
                        }
                        if (decoded.issuedAtText != null) Text("Issued: ${decoded.issuedAtText}", style = DotkitType.monoData(12), color = palette.ink2)
                        if (decoded.expiresAtText != null) Text("Expires: ${decoded.expiresAtText}", style = DotkitType.monoData(12), color = palette.ink2)

                        Text("HEADER", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(
                            text = decoded.headerJson,
                            style = DotkitType.monoData(12),
                            color = palette.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper)
                                .border(1.dp, palette.ink)
                                .padding(10.dp)
                        )
                        Text("PAYLOAD CLAIMS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Text(
                            text = decoded.payloadJson,
                            style = DotkitType.monoData(12),
                            color = palette.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.paper)
                                .border(1.dp, palette.ink)
                                .padding(10.dp)
                        )
                    }.onFailure { err ->
                        PixelStamp("INVALID JWT", palette.err)
                        Text(err.message ?: "Unable to decode token", style = DotkitType.monoData(12), color = palette.err)
                    }
                }
            }
        }
    }
}
