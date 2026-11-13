package com.example.tools.social

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.design.PixelNumberField
import com.example.design.PixelProgressBar
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.design.SwissControlShape
import com.example.lib.dev.SerpPreviewEngine
import com.example.lib.dev.ThreadSplitterEngine
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.tools.common.ToolWorkbenchChrome
import com.example.tools.common.copyToClipboard
import java.security.SecureRandom

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialMoreScreen(
    meta: ToolMeta,
    initialText: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
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
            "soc-thread" -> {
                var draft by remember(initialText) {
                    mutableStateOf(
                        initialText.ifBlank {
                            "Building clean offline mobile utilities requires strict attention to latency, memory footprints, and predictable ergonomics. Here is how Omni Deck decomposes complex engineering tasks into tactile single-purpose benches."
                        }
                    )
                }
                var platformLimit by remember { mutableIntStateOf(280) } // 280 X, 300 Bluesky, 500 Threads
                var includeNumbers by remember { mutableStateOf(true) }
                val posts = remember(draft, platformLimit, includeNumbers) {
                    ThreadSplitterEngine.splitThread(draft, platformLimit, includeNumbers)
                }
                outputText = posts.joinToString("\n\n---\n\n")

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PLATFORM CHARACTER LIMIT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(280 to "X (280)", 300 to "Bluesky (300)", 500 to "Threads (500)", 2200 to "IG (2200)").forEach { (lim, lbl) ->
                                PixelChip(lbl, selected = platformLimit == lim, chapterHue = hue, onClick = { platformLimit = lim })
                            }
                        }
                        PixelSwitch("Add (1/N) Thread Numbers", includeNumbers, { includeNumbers = it }, hue)
                        PixelTextField(draft, { draft = it }, "Paste long draft or essay…", singleLine = false, minHeight = 120.dp, chapterHue = hue)
                        Text("THREAD PREVIEW (${posts.size} POSTS)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        posts.forEachIndexed { idx, post ->
                            Column(
                                Modifier.fillMaxWidth().clip(SwissControlShape).background(palette.paper).border(1.dp, palette.hairline, SwissControlShape).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("POST #${idx + 1} · ${post.length}/$platformLimit", style = DotkitType.monoLabel(9), color = palette.chapterColor(hue))
                                    PixelChip("Copy", chapterHue = hue, onClick = {
                                        copyToClipboard(context, "Thread ${idx + 1}", post)
                                        onStatusToast("Copied post #${idx + 1}")
                                    })
                                }
                                Text(post, style = DotkitType.bodyUi(13), color = palette.ink)
                            }
                        }
                    }
                }
            }

            "soc-giveaway" -> {
                var rawEntries by remember(initialText) {
                    mutableStateOf(initialText.ifBlank { "@alice\n@bob\n@charlie\n@dana\n@evan\n@frank\n@grace" })
                }
                var winnerCountStr by remember { mutableStateOf("0") }
                var dedupeHandles by remember { mutableStateOf(true) }
                var winners by remember { mutableStateOf<List<String>>(emptyList()) }
                var auditSeedHex by remember { mutableStateOf("") }

                val pool = remember(rawEntries, dedupeHandles) {
                    val items = rawEntries.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (dedupeHandles) items.distinct() else items
                }

                outputText = if (winners.isEmpty()) "" else "Winners (${winners.size}):\n" + winners.mapIndexed { i, w -> "${i + 1}. $w" }.joinToString("\n") + "\nCSPRNG Audit Seed: $auditSeedHex"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PARTICIPANTS (ONE PER LINE · ${pool.size} ELIGIBLE)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(rawEntries, { rawEntries = it }, "Paste handles or names…", singleLine = false, minHeight = 120.dp, chapterHue = hue)
                        PixelSwitch("Deduplicate Repeated Handles", dedupeHandles, { dedupeHandles = it }, hue)
                        Text("NUMBER OF WINNERS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelNumberField(winnerCountStr, { winnerCountStr = it }, "Winners (default 1)", allowDecimal = false, chapterHue = hue)
                        PixelButton(
                            text = "Draw Cryptographic Winners",
                            chapterHue = hue,
                            enabled = pool.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val count = (winnerCountStr.toIntOrNull()?.takeIf { it > 0 } ?: 1).coerceIn(1, pool.size)
                                val srng = SecureRandom()
                                val seedBytes = ByteArray(8)
                                srng.nextBytes(seedBytes)
                                auditSeedHex = seedBytes.joinToString("") { "%02X".format(it) }
                                val mutable = pool.toMutableList()
                                // Fisher-Yates using SecureRandom
                                for (i in mutable.lastIndex downTo 1) {
                                    val j = srng.nextInt(i + 1)
                                    val tmp = mutable[i]; mutable[i] = mutable[j]; mutable[j] = tmp
                                }
                                winners = mutable.take(count)
                                onStatusToast("Selected $count verified winner(s).")
                            }
                        )
                        if (winners.isNotEmpty()) {
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text("SELECTED WINNERS (SEED $auditSeedHex)", style = DotkitType.monoLabel(10), color = palette.ok)
                            winners.forEachIndexed { idx, w ->
                                DotMatrixReadout("#${idx + 1} $w", "CRYPTOGRAPHIC SECURERANDOM DRAW", ChapterHue.LIME)
                            }
                        }
                    }
                }
            }

            else -> { // soc-serp
                var siteName by remember { mutableStateOf("Omni Deck") }
                var pageUrl by remember { mutableStateOf("https://omnideck.app/tools/serp-preview") }
                var seoTitle by remember { mutableStateOf("Omni Deck — Offline Swiss Engineering & Utility Studio") }
                var metaDesc by remember { mutableStateOf("Explore 190+ offline developer, image, PDF, finance, and hardware diagnostics benches built cleanly for Android phones and tablets.") }
                var mobileMode by remember { mutableStateOf(false) }

                val titlePx = SerpPreviewEngine.estimateTitlePx(seoTitle)
                val descPx = SerpPreviewEngine.estimateMetaPx(metaDesc)
                val titleMaxPx = if (mobileMode) 520 else 580
                val descMaxPx = if (mobileMode) 680 else 920
                outputText = "Title (${seoTitle.length} ch, ~${titlePx}/${titleMaxPx}px): $seoTitle\nURL: $pageUrl\nMeta (${metaDesc.length} ch, ~${descPx}/${descMaxPx}px): $metaDesc"

                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelChip("Desktop SERP (580px)", selected = !mobileMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { mobileMode = false })
                            PixelChip("Mobile SERP (520px)", selected = mobileMode, chapterHue = hue, modifier = Modifier.weight(1f), onClick = { mobileMode = true })
                        }
                        PixelTextField(siteName, { siteName = it }, "Site Name", chapterHue = hue)
                        PixelTextField(pageUrl, { pageUrl = it }, "Canonical URL", chapterHue = hue)
                        PixelTextField(seoTitle, { seoTitle = it }, "Page Title (<title>)", chapterHue = hue)
                        PixelProgressBar((titlePx.toFloat() / titleMaxPx).coerceIn(0f, 1f), if (titlePx > titleMaxPx) ChapterHue.ROSE else hue)
                        Text("Title Width: ~${titlePx}px / ${titleMaxPx}px (${seoTitle.length} chars)", style = DotkitType.monoLabel(10), color = if (titlePx > titleMaxPx) palette.err else palette.ink2)

                        PixelTextField(metaDesc, { metaDesc = it }, "Meta Description", singleLine = false, minHeight = 84.dp, chapterHue = hue)
                        PixelProgressBar((descPx.toFloat() / descMaxPx).coerceIn(0f, 1f), if (descPx > descMaxPx) ChapterHue.ROSE else hue)
                        Text("Meta Width: ~${descPx}px / ${descMaxPx}px (${metaDesc.length} chars)", style = DotkitType.monoLabel(10), color = if (descPx > descMaxPx) palette.err else palette.ink2)

                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("LIVE SEARCH SNIPPET", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Column(
                            Modifier.fillMaxWidth().clip(SwissControlShape).background(Color.White).border(1.dp, palette.hairline, SwissControlShape).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(siteName.ifBlank { "Site" }, style = DotkitType.bodyUi(12, bold = true), color = Color(0xFF202124))
                            Text(pageUrl.ifBlank { "https://example.com" }, style = DotkitType.monoData(11), color = Color(0xFF4D5156), maxLines = 1)
                            Text(
                                text = if (titlePx > titleMaxPx) seoTitle.take(54) + "…" else seoTitle,
                                style = DotkitType.editorialHeading(17),
                                color = Color(0xFF1A0DAB)
                            )
                            Text(
                                text = if (descPx > descMaxPx) metaDesc.take(152) + "…" else metaDesc,
                                style = DotkitType.bodyUi(13),
                                color = Color(0xFF4D5156)
                            )
                        }
                    }
                }
            }
        }
    }
}
