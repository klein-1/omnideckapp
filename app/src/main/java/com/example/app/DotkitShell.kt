package com.example.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.design.APP_NAME
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.MotionMode
import com.example.design.buildMaterialTypography
import com.example.design.springPressScale
import com.example.registry.ToolRegistry
import com.example.services.sound.PixelSoundEngine

@Composable
fun DotkitShell(
    initialSharedText: String? = null,
    initialSharedUri: Uri? = null,
    initialSharedToolId: String? = null,
    vm: DotkitViewModel = viewModel()
) {
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(systemDark) {
        vm.syncSystemDarkTheme(systemDark)
    }

    LaunchedEffect(initialSharedText, initialSharedUri, initialSharedToolId) {
        val targetId = initialSharedToolId ?: when {
            initialSharedUri != null -> "img-filters"
            !initialSharedText.isNullOrBlank() -> "txt-case"
            else -> null
        }
        if (targetId != null) {
            vm.openTool(targetId, initialSharedText.orEmpty(), initialSharedUri)
        }
    }

    val lookState by vm.lookState.collectAsStateWithLifecycle()
    val activeTab by vm.activeTab.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val activeToolId by vm.activeToolId.collectAsStateWithLifecycle()
    val incomingPayload by vm.incomingPayload.collectAsStateWithLifecycle()
    val incomingUri by vm.incomingUri.collectAsStateWithLifecycle()
    val pinnedToolIds by vm.pinnedToolIds.collectAsStateWithLifecycle()
    val recentToolIds by vm.recentToolIds.collectAsStateWithLifecycle()
    val counters by vm.counters.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val vaultEntries by vm.vaultEntries.collectAsStateWithLifecycle()
    val healthLogs by vm.healthLogs.collectAsStateWithLifecycle()
    val benchmarkScores by vm.benchmarkScores.collectAsStateWithLifecycle()
    val downloadJobs by vm.downloadJobs.collectAsStateWithLifecycle()
    val savedFiles by vm.savedFiles.collectAsStateWithLifecycle()
    val networkLedger by vm.networkLedger.collectAsStateWithLifecycle()
    val lastDeletedFile by vm.lastDeletedFile.collectAsStateWithLifecycle()
    val companionUrl by vm.companionUrl.collectAsStateWithLifecycle()
    val companionToken by vm.companionToken.collectAsStateWithLifecycle()
    val companionProbe by vm.companionProbe.collectAsStateWithLifecycle()
    val geminiKeyOverride by vm.geminiKeyOverride.collectAsStateWithLifecycle()
    val capabilities by vm.capabilities.collectAsStateWithLifecycle()
    val lastCaseStyle by vm.lastCaseStyle.collectAsStateWithLifecycle()
    val lastHashAlgorithm by vm.lastHashAlgorithm.collectAsStateWithLifecycle()
    val toastMessage by vm.toastMessage.collectAsStateWithLifecycle()

    val animDuration = if (lookState.motionMode == MotionMode.OFF) 0 else 240
    val animPaper by animateColorAsState(lookState.palette.paper, tween(animDuration), label = "theme_paper")
    val animPaper2 by animateColorAsState(lookState.palette.paper2, tween(animDuration), label = "theme_paper2")
    val animPaper3 by animateColorAsState(lookState.palette.paper3, tween(animDuration), label = "theme_paper3")
    val animInk by animateColorAsState(lookState.palette.ink, tween(animDuration), label = "theme_ink")
    val animInk2 by animateColorAsState(lookState.palette.ink2, tween(animDuration), label = "theme_ink2")
    val animInk3 by animateColorAsState(lookState.palette.ink3, tween(animDuration), label = "theme_ink3")

    val smoothLook = remember(lookState, animPaper, animPaper2, animPaper3, animInk, animInk2, animInk3) {
        lookState.copy(
            palette = lookState.palette.copy(
                paper = animPaper,
                paper2 = animPaper2,
                paper3 = animPaper3,
                ink = animInk,
                ink2 = animInk2,
                ink3 = animInk3
            )
        )
    }
    val palette = smoothLook.palette

    BackHandler(
        enabled = activeToolId != null ||
            searchQuery.isNotBlank() ||
            (activeTab == ShellTab.TOOLS && selectedCategory != null) ||
            activeTab != ShellTab.HOME
    ) {
        when {
            activeToolId != null -> vm.closeTool()
            searchQuery.isNotBlank() -> vm.updateSearchQuery("")
            activeTab == ShellTab.TOOLS && selectedCategory != null -> vm.selectCategory(null)
            else -> vm.selectTab(ShellTab.HOME)
        }
    }

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accentColor,
            background = palette.paper,
            surface = palette.paper2,
            surfaceVariant = palette.paper3,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.ink2,
            outline = palette.hairline
        )
    } else {
        lightColorScheme(
            primary = palette.accentColor,
            background = palette.paper,
            surface = palette.paper2,
            surfaceVariant = palette.paper3,
            onBackground = palette.ink,
            onSurface = palette.ink,
            onSurfaceVariant = palette.ink2,
            outline = palette.hairline
        )
    }

    CompositionLocalProvider(LocalDotkitLook provides smoothLook) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildMaterialTypography(smoothLook.dyslexiaFriendly, smoothLook.uiScale)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.paper)
            ) {
                val isWideScreen = maxWidth >= 720.dp

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = palette.paper,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!isWideScreen && activeToolId == null) {
                            OmniDeckFloatingCapsuleBar(
                                activeTab = activeTab,
                                hasActiveTool = false,
                                onSelectTab = { tab ->
                                    PixelSoundEngine.playBlip(smoothLook.soundEnabled, 620, 14)
                                    vm.selectTab(tab)
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideScreen) {
                            OmniDeckSideRail(
                                activeTab = activeTab,
                                hasActiveTool = activeToolId != null,
                                onSelectTab = { tab ->
                                    PixelSoundEngine.playBlip(smoothLook.soundEnabled, 620, 14)
                                    vm.selectTab(tab)
                                }
                            )
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val activeMeta = activeToolId?.let { ToolRegistry.getToolById(it) }
                            val navKey: Any = activeMeta ?: activeTab

                            AnimatedContent(
                                targetState = navKey,
                                transitionSpec = {
                                    if (smoothLook.motionMode == MotionMode.OFF) {
                                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    } else {
                                        val enteringTool = targetState !is ShellTab
                                        if (enteringTool) {
                                            (fadeIn(tween(200)) +
                                                scaleIn(initialScale = 0.965f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) +
                                                slideInVertically(tween(200)) { it / 20 })
                                                .togetherWith(fadeOut(tween(130)))
                                        } else {
                                            (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 28 })
                                                .togetherWith(fadeOut(tween(130)) + slideOutHorizontally(tween(130)) { -it / 28 })
                                        }
                                    }
                                },
                                label = "shell_nav_transition"
                            ) { stateKey ->
                                if (stateKey !is ShellTab) {
                                    val toolMeta = activeMeta ?: ToolRegistry.ALL_TOOLS.first()
                                    OmniDeckToolRouter(
                                        meta = toolMeta,
                                        vm = vm,
                                        incomingPayload = incomingPayload,
                                        incomingUri = incomingUri,
                                        pinnedToolIds = pinnedToolIds,
                                        counters = counters,
                                        notes = notes,
                                        expenses = expenses,
                                        vaultEntries = vaultEntries,
                                        healthLogs = healthLogs,
                                        benchmarkScores = benchmarkScores,
                                        downloadJobs = downloadJobs,
                                        companionUrl = companionUrl,
                                        companionToken = companionToken,
                                        lastCaseStyle = lastCaseStyle,
                                        lastHashAlgorithm = lastHashAlgorithm
                                    )
                                } else {
                                    when (stateKey) {
                                        ShellTab.HOME -> HomeScreen(
                                            searchQuery = searchQuery,
                                            onSearchQueryChange = { vm.updateSearchQuery(it) },
                                            pinnedToolIds = pinnedToolIds,
                                            recentToolIds = recentToolIds,
                                            onOpenTool = { id, payload -> vm.openTool(id, payload) },
                                            onBrowseCategory = { vm.browseCategory(it) },
                                            onOpenToolsDirectory = {
                                                vm.selectCategory(null)
                                                vm.selectTab(ShellTab.TOOLS)
                                            },
                                            onToggleLightDark = { vm.toggleLightDarkQuick() },
                                            onToast = { vm.showToast(it) }
                                        )
                                        ShellTab.TOOLS -> ToolsDirectoryScreen(
                                            searchQuery = searchQuery,
                                            onSearchQueryChange = { vm.updateSearchQuery(it) },
                                            selectedCategory = selectedCategory,
                                            onSelectCategory = { vm.selectCategory(it) },
                                            pinnedToolIds = pinnedToolIds,
                                            onOpenTool = { id, payload -> vm.openTool(id, payload) },
                                            onToggleLightDark = { vm.toggleLightDarkQuick() },
                                            onToast = { vm.showToast(it) }
                                        )
                                        ShellTab.FILES -> FilesAndLedgerScreen(
                                            savedFiles = savedFiles,
                                            networkLedger = networkLedger,
                                            lastDeletedFile = lastDeletedFile,
                                            onUndoDelete = { vm.undoDeleteSavedFile() },
                                            onDeleteFile = { vm.deleteSavedFileWithUndo(it) },
                                            onSendToTool = { id, payload -> vm.openTool(id, payload) },
                                            onToast = { vm.showToast(it) }
                                        )
                                        ShellTab.SETTINGS -> SettingsAndYouScreen(
                                            companionUrl = companionUrl,
                                            companionToken = companionToken,
                                            companionProbe = companionProbe,
                                            geminiKeyOverride = geminiKeyOverride,
                                            capabilities = capabilities,
                                            onChangeTheme = { vm.updateThemeMode(it) },
                                            onChangeAccent = { vm.updateAccentHue(it) },
                                            onChangeMotion = { vm.updateMotionMode(it) },
                                            onChangeScale = { vm.updateUiScale(it) },
                                            onChangeDensity = { vm.updateDensity(it) },
                                            onChangeDyslexia = { vm.updateDyslexiaFriendly(it) },
                                            onChangeSound = { vm.updateSoundEnabled(it) },
                                            onChangeHaptics = { vm.updateHapticsEnabled(it) },
                                            onChangeConfetti = { vm.updateConfettiEnabled(it) },
                                            onChangeRegion = { vm.updateRegionFormat(it) },
                                            onChangeOfflineOnlyAi = { vm.updateOfflineOnlyAi(it) },
                                            onUpdateCompanion = { u, t -> vm.updateCompanionConfig(u, t) },
                                            onTestCompanion = { vm.testCompanionConnection() },
                                            onUpdateGeminiKey = { vm.updateGeminiKeyOverride(it) },
                                            onPanicWipe = { vm.panicWipeAllData() }
                                        )
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = toastMessage != null,
                                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
                                exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 520.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(palette.ink)
                                        .border(0.8.dp, palette.accentColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = toastMessage ?: "",
                                        style = DotkitType.monoData(12),
                                        color = palette.paper
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: ShellTab, selected: Boolean): ImageVector = when (tab) {
    ShellTab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    ShellTab.TOOLS -> if (selected) Icons.Rounded.GridView else Icons.Outlined.GridView
    ShellTab.FILES -> if (selected) Icons.Rounded.Folder else Icons.Outlined.Folder
    ShellTab.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
}

@Composable
private fun OmniDeckFloatingCapsuleBar(
    activeTab: ShellTab,
    hasActiveTool: Boolean,
    onSelectTab: (ShellTab) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val capsuleShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (palette.isDark) 0.dp else 8.dp,
                    shape = capsuleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(capsuleShape)
                .background(palette.paper2.copy(alpha = 0.94f))
                .border(0.8.dp, palette.hairline, capsuleShape)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShellTab.entries.forEach { tab ->
                val selected = !hasActiveTool && activeTab == tab
                val interactionSource = remember { MutableInteractionSource() }
                val pillBg by animateColorAsState(
                    targetValue = if (selected) palette.accentColor.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 180),
                    label = "nav_pill_${tab.id}"
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) palette.accentColor else palette.ink2,
                    animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 180),
                    label = "nav_fg_${tab.id}"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected && look.motionMode != MotionMode.OFF) 1.08f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "nav_scale_${tab.id}"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .springPressScale(interactionSource, 0.93f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(pillBg)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple()
                        ) { onSelectTab(tab) }
                        .testTag("nav_tab_${tab.id}")
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tabIcon(tab, selected),
                        contentDescription = tab.label,
                        tint = fg,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                    if (selected) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = tab.label,
                            style = DotkitType.bodyUi(12, bold = true),
                            color = fg,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OmniDeckSideRail(
    activeTab: ShellTab,
    hasActiveTool: Boolean,
    onSelectTab: (ShellTab) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    Row(modifier = Modifier.fillMaxHeight().background(palette.paper2)) {
        Column(
            modifier = Modifier
                .width(138.dp)
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(APP_NAME, style = DotkitType.editorialHeading(18), color = palette.ink)
            Spacer(Modifier.height(4.dp))
            ShellTab.entries.forEach { tab ->
                val selected = !hasActiveTool && activeTab == tab
                val interactionSource = remember { MutableInteractionSource() }
                val bg by animateColorAsState(
                    targetValue = if (selected) palette.accentColor.copy(alpha = 0.16f) else Color.Transparent,
                    label = "rail_bg_${tab.id}"
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) palette.accentColor else palette.ink2,
                    label = "rail_fg_${tab.id}"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPressScale(interactionSource, 0.95f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple()
                        ) { onSelectTab(tab) }
                        .testTag("rail_tab_${tab.id}")
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tabIcon(tab, selected),
                        contentDescription = tab.label,
                        tint = fg,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tab.label,
                        style = DotkitType.bodyUi(13, bold = selected),
                        color = fg
                    )
                }
            }
        }
        Box(modifier = Modifier.width(0.8.dp).fillMaxHeight().background(palette.hairline))
    }
}
