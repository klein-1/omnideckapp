package com.example.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
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

    val palette = lookState.palette

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

    CompositionLocalProvider(LocalDotkitLook provides lookState) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildMaterialTypography(lookState.dyslexiaFriendly, lookState.uiScale)
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
                        if (!isWideScreen) {
                            OmniDeckBottomBar(
                                activeTab = activeTab,
                                hasActiveTool = activeToolId != null,
                                onSelectTab = { tab ->
                                    PixelSoundEngine.playBlip(lookState.soundEnabled, 620, 16)
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
                                    PixelSoundEngine.playBlip(lookState.soundEnabled, 620, 16)
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
                                    if (lookState.motionMode == MotionMode.OFF) {
                                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    } else {
                                        val enteringTool = targetState !is ShellTab
                                        if (enteringTool) {
                                            (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) + slideInVertically(tween(220)) { it / 18 })
                                                .togetherWith(fadeOut(tween(150)))
                                        } else {
                                            (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 24 })
                                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 24 })
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
                                            onToast = { vm.showToast(it) }
                                        )
                                        ShellTab.TOOLS -> ToolsDirectoryScreen(
                                            searchQuery = searchQuery,
                                            onSearchQueryChange = { vm.updateSearchQuery(it) },
                                            selectedCategory = selectedCategory,
                                            onSelectCategory = { vm.selectCategory(it) },
                                            pinnedToolIds = pinnedToolIds,
                                            onOpenTool = { id, payload -> vm.openTool(id, payload) },
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
                                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 520.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(palette.ink)
                                        .border(1.dp, palette.accentColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
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
private fun OmniDeckBottomBar(
    activeTab: ShellTab,
    hasActiveTool: Boolean,
    onSelectTab: (ShellTab) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    Column(modifier = Modifier.fillMaxWidth().background(palette.paper2)) {
        HorizontalDivider(thickness = 1.dp, color = palette.hairline.copy(alpha = 0.7f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .springPressScale(interactionSource, 0.94f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.material3.ripple()
                        ) { onSelectTab(tab) }
                        .testTag("nav_tab_${tab.id}")
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = tabIcon(tab, selected),
                        contentDescription = tab.label,
                        tint = fg,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tab.label,
                        style = DotkitType.bodyUi(11, bold = selected),
                        color = fg,
                        maxLines = 1
                    )
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
                .width(134.dp)
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
                        .clip(RoundedCornerShape(14.dp))
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
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(palette.hairline))
    }
}
