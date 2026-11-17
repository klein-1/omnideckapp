package com.example.tools.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.design.DotkitType
import com.example.design.HelpSheetCard
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.ToolBadgeIcon
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.registry.ToolRegistry

/**
 * Clean, minimalist Tool Workbench Shell optimized for phones and tablets.
 * Uses pure symbol triggers (←, ★/☆, ?) with a scroll-linked header divider.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolWorkbenchChrome(
    meta: ToolMeta,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    outputText: String,
    outputFileName: String,
    outputKind: DataKind,
    onSaveToFiles: (fileName: String, kind: DataKind, content: String) -> Unit,
    onSendToTool: (targetToolId: String, payload: String) -> Unit,
    helpParagraph: String,
    undoMessage: String? = null,
    onUndo: (() -> Unit)? = null,
    onStatusToast: (String) -> Unit,
    showOutputBar: Boolean = true,
    benchContent: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val ch = meta.chapter
    val chColor = palette.chapterColor(ch.hue)
    var showSendToSheet by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val headerDividerAlpha by remember {
        derivedStateOf { (scrollState.value / 48f).coerceIn(0f, 1f) }
    }

    val compatibleTargets = remember(meta.id, outputKind) {
        ToolRegistry.findCompatibleSendToTargets(meta.id, outputKind)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.paper),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize()
        ) {
            // Sticky Minimalist Top Bar (Symbols only: ←, ★/☆, ?)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.paper)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    PixelChip(
                        label = "←",
                        chapterHue = ch.hue,
                        onClick = onBack
                    )
                    ToolBadgeIcon(
                        meta = meta,
                        accentColor = chColor,
                        sizeDp = 38.dp,
                        iconSizeDp = 20.dp
                    )
                    Column {
                        Text(
                            text = meta.name,
                            style = DotkitType.editorialHeading(18),
                            color = palette.ink,
                            maxLines = 1
                        )
                        Text(
                            text = "${meta.category.label} · ${meta.tagline}",
                            style = DotkitType.bodyUi(11),
                            color = palette.ink2,
                            maxLines = 1
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelChip(
                        label = if (isPinned) "★" else "☆",
                        selected = isPinned,
                        chapterHue = ch.hue,
                        onClick = onTogglePin
                    )
                    PixelChip(
                        label = "?",
                        selected = showHelp,
                        chapterHue = ch.hue,
                        onClick = { showHelp = !showHelp }
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.8.dp,
                color = palette.hairline.copy(alpha = headerDividerAlpha)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(visible = showHelp) {
                    HelpSheetCard(
                        specimenCode = meta.specimenCode,
                        title = meta.name,
                        paragraph = helpParagraph
                    )
                }

            if (undoMessage != null && onUndo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.paper2)
                        .border(1.5.dp, palette.warn)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = undoMessage,
                        style = DotkitType.bodyUi(13, bold = true),
                        color = palette.ink,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    PixelChip(
                        label = "Undo",
                        selected = true,
                        chapterHue = ch.hue,
                        onClick = onUndo
                    )
                }
            }

            // Tool Work Area
            benchContent()

            // Clean Output Bar
            if (showOutputBar) {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OUTPUT ACTIONS",
                                style = DotkitType.monoLabel(10),
                                color = palette.ink2
                            )
                            Text(
                                text = "${outputText.encodeToByteArray().size} B",
                                style = DotkitType.monoData(11),
                                color = palette.ink3
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PixelButton(
                                text = "Copy",
                                chapterHue = ch.hue,
                                enabled = outputText.isNotEmpty(),
                                onClick = {
                                    copyToClipboard(context, meta.name, outputText)
                                    onStatusToast("Copied to clipboard.")
                                },
                                tag = "${meta.id}_copy_btn"
                            )
                            PixelButton(
                                text = "Save",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = ch.hue,
                                enabled = outputText.isNotEmpty(),
                                onClick = {
                                    onSaveToFiles(outputFileName, outputKind, outputText)
                                    onStatusToast("Saved $outputFileName.")
                                },
                                tag = "${meta.id}_save_btn"
                            )
                            PixelButton(
                                text = "Share",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = ch.hue,
                                enabled = outputText.isNotEmpty(),
                                onClick = { shareTextOutput(context, meta.name, outputText) },
                                tag = "${meta.id}_share_btn"
                            )
                            if (compatibleTargets.isNotEmpty()) {
                                PixelButton(
                                    text = if (showSendToSheet) "Close ✕" else "Send to…",
                                    variant = PixelButtonVariant.GHOST,
                                    chapterHue = ch.hue,
                                    enabled = outputText.isNotEmpty(),
                                    onClick = { showSendToSheet = !showSendToSheet },
                                    tag = "${meta.id}_sendto_btn"
                                )
                            }
                        }

                        if (showSendToSheet && compatibleTargets.isNotEmpty()) {
                            HorizontalDivider(thickness = 0.8.dp, color = palette.hairline)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                compatibleTargets.forEach { target ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(palette.paper)
                                            .border(0.8.dp, palette.hairline, RoundedCornerShape(10.dp))
                                            .clickable {
                                                showSendToSheet = false
                                                onSendToTool(target.id, outputText)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(target.name, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                        PixelStamp("→", palette.chapterColor(target.chapter.hue))
                                    }
                                }
                            }
                        }
                    }
                }
            }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareTextOutput(context: Context, subject: String, text: String) {
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share from $subject").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
