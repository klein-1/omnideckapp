package com.example.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.design.springPressScale
import com.example.design.BlueprintPalette
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.HighContrastPalette
import com.example.design.InkPalette
import com.example.design.LocalDotkitLook
import com.example.design.MotionMode
import com.example.design.PaperPalette
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.design.RegionFormat
import com.example.design.SepiaPalette
import com.example.design.TerminalPalette
import com.example.design.ThemeMode
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.design.UiDensity
import com.example.lib.format.NumberFormatters
import com.example.services.capabilities.DeviceCapabilitySnapshot
import com.example.services.companion.CompanionProbeResult
import com.example.services.storage.NetworkLedgerEntity
import com.example.services.storage.SavedFileEntity
import com.example.tools.common.copyToClipboard
import com.example.tools.common.shareTextOutput

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilesAndLedgerScreen(
    savedFiles: List<SavedFileEntity>,
    networkLedger: List<NetworkLedgerEntity>,
    lastDeletedFile: SavedFileEntity?,
    onUndoDelete: () -> Unit,
    onDeleteFile: (SavedFileEntity) -> Unit,
    onSendToTool: (toolId: String, payload: String) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalDotkitLook.current.palette
    val hue = ChapterHue.CLAY
    var filterQuery by remember { mutableStateOf("") }

    val visibleFiles = remember(savedFiles, filterQuery) {
        val q = filterQuery.trim().lowercase()
        if (q.isEmpty()) savedFiles
        else savedFiles.filter {
            it.fileName.lowercase().contains(q) ||
                it.originToolName.lowercase().contains(q) ||
                it.contentText.lowercase().contains(q)
        }
    }
    val totalBytesOut = remember(networkLedger) { networkLedger.sumOf { it.bytesOut } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.paper),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Files", style = DotkitType.displayPixel(30), color = palette.ink)
                        Text("${savedFiles.size} saved outputs · local SQLite", style = DotkitType.bodyUi(13), color = palette.ink2)
                    }
                    PixelStamp("OUT: ${NumberFormatters.formatBytes(totalBytesOut)}", palette.ok)
                }
            }

            if (savedFiles.isNotEmpty()) {
                item {
                    PixelTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = "Filter saved files by name or content…",
                        chapterHue = hue
                    )
                }
            }

            if (lastDeletedFile != null) {
                item {
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
                            text = "Deleted '${lastDeletedFile.fileName}'.",
                            style = DotkitType.bodyUi(13, bold = true),
                            color = palette.ink,
                            modifier = Modifier.weight(1f)
                        )
                        PixelChip(
                            label = "Undo",
                            selected = true,
                            chapterHue = hue,
                            onClick = onUndoDelete
                        )
                    }
                }
            }

            if (visibleFiles.isEmpty()) {
                item {
                    ToolStateBanner(
                        kind = ToolStateKind.EMPTY,
                        title = if (savedFiles.isEmpty()) "No saved files yet" else "No matching files",
                        detail = "Tap Save inside any tool to store outputs locally for copying, sharing, or piping into other tools.",
                        chapterHue = hue
                    )
                }
            } else {
                items(visibleFiles, key = { it.id }) { file ->
                    PixelBox {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.fileName, style = DotkitType.bodyUi(15, bold = true), color = palette.ink)
                                    Text(
                                        text = "${file.originToolName} · ${NumberFormatters.formatBytes(file.sizeBytes)}",
                                        style = DotkitType.monoLabel(10),
                                        color = palette.ink2
                                    )
                                }
                                PixelStamp(file.dataKindId, palette.chapterColor(hue))
                            }
                            Text(
                                text = file.contentText.take(200),
                                style = DotkitType.monoData(12),
                                color = palette.ink,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.paper)
                                    .padding(10.dp)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PixelChip(label = "Copy", chapterHue = hue, onClick = {
                                    copyToClipboard(context, file.fileName, file.contentText)
                                    onToast("Copied ${file.fileName}.")
                                })
                                PixelChip(label = "Share", chapterHue = hue, onClick = {
                                    shareTextOutput(context, file.fileName, file.contentText)
                                })
                                PixelChip(label = "Hash →", chapterHue = ChapterHue.FOREST, onClick = {
                                    onSendToTool("dev-hash", file.contentText)
                                })
                                PixelChip(label = "Case →", chapterHue = ChapterHue.VERMILION, onClick = {
                                    onSendToTool("txt-case", file.contentText)
                                })
                                PixelChip(label = "Delete", chapterHue = ChapterHue.VERMILION, onClick = {
                                    onDeleteFile(file)
                                })
                            }
                        }
                    }
                }
            }

            item {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NETWORK AUDIT LOG", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp("${networkLedger.size} CALLS", palette.ok)
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        if (networkLedger.isEmpty()) {
                            Text(
                                text = "Zero outbound network calls recorded this session.",
                                style = DotkitType.monoData(12),
                                color = palette.ink2
                            )
                        } else {
                            networkLedger.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.paper)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${entry.toolId} → ${entry.host}", style = DotkitType.monoData(12), color = palette.ink)
                                        Text(entry.statusNote, style = DotkitType.monoLabel(10), color = palette.ink2)
                                    }
                                    Text("↑${entry.bytesOut}B ↓${entry.bytesIn}B", style = DotkitType.monoData(11), color = palette.ink2)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndYouScreen(
    companionUrl: String,
    companionToken: String,
    companionProbe: CompanionProbeResult?,
    geminiKeyOverride: String,
    capabilities: DeviceCapabilitySnapshot,
    onChangeTheme: (ThemeMode) -> Unit,
    onChangeAccent: (ChapterHue) -> Unit,
    onChangeMotion: (MotionMode) -> Unit,
    onChangeScale: (Float) -> Unit,
    onChangeDensity: (UiDensity) -> Unit,
    onChangeDyslexia: (Boolean) -> Unit,
    onChangeSound: (Boolean) -> Unit,
    onChangeHaptics: (Boolean) -> Unit,
    onChangeConfetti: (Boolean) -> Unit,
    onChangeRegion: (RegionFormat) -> Unit,
    onChangeOfflineOnlyAi: (Boolean) -> Unit,
    onUpdateCompanion: (String, String) -> Unit,
    onTestCompanion: () -> Unit,
    onUpdateGeminiKey: (String) -> Unit,
    onPanicWipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette

    var urlDraft by remember(companionUrl) { mutableStateOf(companionUrl) }
    var tokenDraft by remember(companionToken) { mutableStateOf(companionToken) }
    var geminiDraft by remember(geminiKeyOverride) { mutableStateOf(geminiKeyOverride) }
    var confirmPanic by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.paper),
        contentAlignment = Alignment.TopCenter
    ) {
        val swatchColumns = if (maxWidth >= 600.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text("Settings", style = DotkitType.displayPixel(28), color = palette.ink)
                    Text("Themes, typography, interaction, companion & credits", style = DotkitType.bodyUi(13), color = palette.ink2)
                }
            }

            // 1. Themes (6 curated palettes + System)
            item {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("APP THEME (6 PALETTES + AUTO)", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelStamp(look.themeMode.label.uppercase(), palette.accentColor)
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)

                        val rows = ThemeMode.entries.chunked(swatchColumns)
                        rows.forEach { rowModes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowModes.forEach { mode ->
                                    ThemeSwatchCard(
                                        mode = mode,
                                        selected = look.themeMode == mode,
                                        onClick = { onChangeTheme(mode) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(swatchColumns - rowModes.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text("ACCENT COLOR", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChapterHue.entries.forEach { h ->
                                PixelChip(
                                    label = h.label,
                                    selected = look.accentHue == h,
                                    chapterHue = h,
                                    onClick = { onChangeAccent(h) }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Display, Typography & Interaction
            item {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("DISPLAY & ACCESSIBILITY", style = DotkitType.monoLabel(10), color = palette.ink2)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)

                        Text("TEXT SCALE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.9f to "0.9×", 1.0f to "1.0×", 1.15f to "1.15×", 1.3f to "1.3×").forEach { (sc, lbl) ->
                                PixelChip(label = lbl, selected = look.uiScale == sc, chapterHue = ChapterHue.COBALT, onClick = { onChangeScale(sc) })
                            }
                        }

                        Text("SPACING DENSITY & MOTION", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            UiDensity.entries.forEach { d ->
                                PixelChip(label = d.label, selected = look.density == d, chapterHue = ChapterHue.TEAL, onClick = { onChangeDensity(d) })
                            }
                            MotionMode.entries.forEach { m ->
                                PixelChip(label = m.label, selected = look.motionMode == m, chapterHue = ChapterHue.SKY, onClick = { onChangeMotion(m) })
                            }
                        }

                        Text("NUMBER & CURRENCY FORMAT", style = DotkitType.monoLabel(10), color = palette.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RegionFormat.entries.forEach { r ->
                                PixelChip(label = r.label, selected = look.regionFormat == r, chapterHue = ChapterHue.LIME, onClick = { onChangeRegion(r) })
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        PixelSwitch("Dyslexia-Friendly Font (Atkinson)", look.dyslexiaFriendly, onChangeDyslexia, ChapterHue.MINT)
                        PixelSwitch("UI Sound Blips", look.soundEnabled, onChangeSound, ChapterHue.ROSE)
                        PixelSwitch("Haptic Tap Feedback", look.hapticsEnabled, onChangeHaptics, ChapterHue.TANGERINE)
                        PixelSwitch("Confetti on Goal Complete", look.confettiEnabled, onChangeConfetti, ChapterHue.MARIGOLD)
                    }
                }
            }

            // 3. Hardware & Capabilities Summary
            item {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DEVICE HARDWARE", style = DotkitType.monoLabel(10), color = palette.ink2)
                            Text(
                                text = "Android ${capabilities.androidRelease} · ${capabilities.cpuCores} Cores",
                                style = DotkitType.monoLabel(10),
                                color = palette.ink
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        capabilities.probes.forEach { probe ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.paper)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(probe.name, style = DotkitType.bodyUi(13, bold = true), color = palette.ink)
                                    Text(probe.detail, style = DotkitType.monoData(11), color = palette.ink2)
                                }
                                PixelStamp(if (probe.available) "OK" else "N/A", if (probe.available) palette.ok else palette.warn)
                            }
                        }
                    }
                }
            }

            // 4. Optional Network / Companion (yt-dlp & spotDL bridge)
            item {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("NETWORK & COMPANION BRIDGE", style = DotkitType.monoLabel(10), color = palette.ink2)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        PixelSwitch(
                            label = "Strict Offline Mode (Block all external requests)",
                            checked = look.offlineOnlyAi,
                            onCheckedChange = onChangeOfflineOnlyAi,
                            chapterHue = ChapterHue.SKY
                        )
                        Text("COMPANION SERVER URL (YT-DLP / SPOTDL BRIDGE)", style = DotkitType.monoLabel(10), color = palette.ink2)
                        PixelTextField(value = urlDraft, onValueChange = { urlDraft = it }, placeholder = "http://127.0.0.1:8787", chapterHue = ChapterHue.FOREST)
                        PixelTextField(value = tokenDraft, onValueChange = { tokenDraft = it }, placeholder = "Optional Bearer Token…", chapterHue = ChapterHue.FOREST)
                        PixelButton(
                            text = "Save & Test Server",
                            variant = PixelButtonVariant.OUTLINE,
                            chapterHue = ChapterHue.FOREST,
                            onClick = {
                                onUpdateCompanion(urlDraft, tokenDraft)
                                onTestCompanion()
                            }
                        )
                        if (companionProbe != null) {
                            Text(
                                text = companionProbe.statusMessage,
                                style = DotkitType.monoData(12),
                                color = if (companionProbe.reachable) palette.ok else palette.warn
                            )
                        }
                        if (!look.offlineOnlyAi) {
                            HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                            Text("OPTIONAL GEMINI KEY", style = DotkitType.monoLabel(10), color = palette.ink2)
                            PixelTextField(value = geminiDraft, onValueChange = { geminiDraft = it }, placeholder = "Optional Gemini API Key…", chapterHue = ChapterHue.SKY)
                            PixelButton(
                                text = "Save AI Key",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = ChapterHue.SKY,
                                onClick = { onUpdateGeminiKey(geminiDraft) }
                            )
                        }
                    }
                }
            }

            // 5. Data Reset
            item {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DATA & RESET", style = DotkitType.monoLabel(10), color = palette.err)
                        Text(
                            text = "Clear all saved files, notes, expenses, vault entries, health logs, scores, and reset preferences.",
                            style = DotkitType.bodyUi(13),
                            color = palette.ink2
                        )
                        if (!confirmPanic) {
                            PixelButton(
                                text = "Clear All Local Data",
                                variant = PixelButtonVariant.DESTRUCTIVE,
                                onClick = { confirmPanic = true }
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PixelButton(
                                    text = "Confirm Clear",
                                    variant = PixelButtonVariant.DESTRUCTIVE,
                                    onClick = {
                                        confirmPanic = false
                                        onPanicWipe()
                                    }
                                )
                                PixelButton(
                                    text = "Cancel",
                                    variant = PixelButtonVariant.OUTLINE,
                                    onClick = { confirmPanic = false }
                                )
                            }
                        }
                    }
                }
            }

            // 6. Credits (Exclusive location per specification)
            item {
                PixelBox {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CREDITS", style = DotkitType.monoLabel(10), color = palette.ink2)
                        HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                        Text(
                            text = "Omni Deck",
                            style = DotkitType.editorialHeading(18),
                            color = palette.ink
                        )
                        Text(
                            text = "Made by Hatim Mushtaq",
                            style = DotkitType.bodyUi(14, bold = true),
                            color = palette.accentColor
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ThemeSwatchCard(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePalette = LocalDotkitLook.current.palette
    val preview = when (mode) {
        ThemeMode.PAPER -> PaperPalette
        ThemeMode.INK -> InkPalette
        ThemeMode.BLUEPRINT -> BlueprintPalette
        ThemeMode.TERMINAL -> TerminalPalette
        ThemeMode.SEPIA -> SepiaPalette
        ThemeMode.HIGH_CONTRAST -> HighContrastPalette
        ThemeMode.AUTO -> if (activePalette.isDark) InkPalette else PaperPalette
    }

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .springPressScale(interactionSource, 0.96f)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(preview.paper)
            .border(
                width = if (selected) 1.6.dp else 0.8.dp,
                color = if (selected) activePalette.accentColor else activePalette.hairline,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.RadioButton,
                onClick = onClick
            )
            .testTag("theme_swatch_${mode.name.lowercase()}")
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(13.dp).clip(CircleShape).background(preview.paper2).border(1.dp, preview.ink.copy(alpha = 0.25f), CircleShape))
                Box(Modifier.size(13.dp).clip(CircleShape).background(preview.ink))
                Box(Modifier.size(13.dp).clip(CircleShape).background(preview.accentColor))
            }
            if (selected) {
                Text("✓", style = DotkitType.monoLabel(12), color = activePalette.accentColor)
            }
        }
        Text(mode.label, style = DotkitType.bodyUi(13, bold = true), color = preview.ink, maxLines = 1)
        Text(mode.subtitle, style = DotkitType.bodyUi(10), color = preview.ink2, maxLines = 1)
    }
}
