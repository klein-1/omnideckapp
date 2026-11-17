package com.example.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.design.APP_NAME
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.MotionMode
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelStamp
import com.example.design.PixelTextField
import com.example.design.SwissCardShape
import com.example.design.ToolBadgeIcon
import com.example.design.ToolCategory
import com.example.design.ToolIconResolver
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.design.collectSmoothedVelocity
import com.example.design.rememberScrollVelocityConnection
import com.example.design.scrollReactiveItem
import com.example.design.springPressScale
import com.example.lib.command.CommandEngine
import com.example.registry.ToolMeta
import com.example.registry.ToolRegistry
import com.example.tools.common.copyToClipboard
import kotlinx.coroutines.launch

private sealed class HomePagerTab(val id: String, val label: String, val hue: ChapterHue) {
    data object All : HomePagerTab("all", "All", ChapterHue.COBALT)
    data object Suites : HomePagerTab("suites", "Suites", ChapterHue.VERMILION)
    data class Category(val cat: ToolCategory) : HomePagerTab(cat.id, cat.label, cat.hue)
}

private val HOME_TABS: List<HomePagerTab> = buildList {
    add(HomePagerTab.All)
    add(HomePagerTab.Category(ToolCategory.AI))
    add(HomePagerTab.Suites)
    val priorityOrder = listOf(
        ToolCategory.DEV,
        ToolCategory.IMAGE,
        ToolCategory.PDF,
        ToolCategory.FINANCE,
        ToolCategory.CALC,
        ToolCategory.MEDIA,
        ToolCategory.DOWNLOAD,
        ToolCategory.URL,
        ToolCategory.SERVER,
        ToolCategory.TIME,
        ToolCategory.TEXT,
        ToolCategory.ESSENTIALS,
        ToolCategory.HEALTH,
        ToolCategory.GAMES,
        ToolCategory.BENCHMARK,
        ToolCategory.SOCIAL,
        ToolCategory.DEVICE,
        ToolCategory.MEASURE,
        ToolCategory.COMMON
    )
    priorityOrder.forEach { add(HomePagerTab.Category(it)) }
}

@Composable
fun ThemeQuickToggleButton(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val interactionSource = remember { MutableInteractionSource() }
    val rotation by animateFloatAsState(
        targetValue = if (isDark) 0f else 180f,
        animationSpec = if (look.motionMode == MotionMode.OFF) tween(0) else spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "theme_toggle_rot"
    )
    Box(
        modifier = modifier
            .springPressScale(interactionSource, 0.90f)
            .minimumInteractiveComponentSize()
            .size(42.dp)
            .clip(CircleShape)
            .background(palette.paper2)
            .border(0.8.dp, palette.hairline, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Button,
                onClick = onToggle
            )
            .testTag("quick_theme_toggle"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            contentDescription = if (isDark) "Switch to Light theme" else "Switch to Obsidian Dark theme",
            tint = palette.ink,
            modifier = Modifier
                .size(19.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
fun TopSearchAndSolverBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenTool: (toolId: String, payload: String) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val look = LocalDotkitLook.current
    val palette = look.palette

    val inlineAnswer = remember(query, look.regionFormat) {
        CommandEngine.evaluateInline(query, look.regionFormat)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.paper2)
                .border(0.8.dp, palette.hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = palette.accentColor,
                modifier = Modifier.size(19.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                PixelTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search ${ToolRegistry.ALL_TOOLS.size} tools or type math (18% of 2400)…",
                    chapterHue = look.accentHue,
                    minHeight = 42.dp,
                    tag = "top_search_input"
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = palette.ink
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
                        if (clip.isNotBlank()) {
                            onQueryChange(clip)
                        } else {
                            onToast("Clipboard is empty.")
                        }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = "Paste clipboard",
                        tint = palette.ink2
                    )
                }
            }
        }

        if (inlineAnswer != null) {
            PixelBox(backgroundColor = palette.paper2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelStamp(inlineAnswer.badge, palette.ok)
                        Text("INSTANT SOLVER", style = DotkitType.monoLabel(9), color = palette.ink2)
                    }
                    Text(
                        text = inlineAnswer.headline,
                        style = DotkitType.readoutDoto(26),
                        color = palette.ink
                    )
                    Text(
                        text = inlineAnswer.detail,
                        style = DotkitType.monoData(12),
                        color = palette.ink2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton(
                            text = "Copy",
                            chapterHue = ChapterHue.COBALT,
                            onClick = {
                                copyToClipboard(context, inlineAnswer.badge, inlineAnswer.copyValue)
                                onToast("Copied '${inlineAnswer.copyValue}'.")
                            }
                        )
                        if (inlineAnswer.targetToolId != null) {
                            PixelButton(
                                text = "Open Tool →",
                                variant = PixelButtonVariant.OUTLINE,
                                chapterHue = look.accentHue,
                                onClick = { onOpenTool(inlineAnswer.targetToolId, inlineAnswer.copyValue) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    pinnedToolIds: List<String>,
    recentToolIds: List<String>,
    onOpenTool: (toolId: String, payload: String) -> Unit,
    onBrowseCategory: (ToolCategory) -> Unit,
    onOpenToolsDirectory: () -> Unit,
    onToggleLightDark: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val scope = rememberCoroutineScope()

    val recentTools = remember(recentToolIds) {
        recentToolIds.mapNotNull { ToolRegistry.getToolById(it) }
    }
    val pinnedTools = remember(pinnedToolIds) {
        pinnedToolIds.mapNotNull { ToolRegistry.getToolById(it) }
    }
    val flagshipSuites = remember {
        listOfNotNull(
            ToolRegistry.getToolById("ai-writer"),
            ToolRegistry.getToolById("dl-video"),
            ToolRegistry.getToolById("dl-music"),
            ToolRegistry.getToolById("url-safety"),
            ToolRegistry.getToolById("srv-shttps"),
            ToolRegistry.getToolById("img-filters"),
            ToolRegistry.getToolById("pdf-merge")
        )
    }
    val searchMatches = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else ToolRegistry.searchTools(searchQuery, ToolCategory.ALL)
    }

    val pagerState = rememberPagerState(initialPage = 0) { HOME_TABS.size }
    val tabRowState = rememberLazyListState()

    LaunchedEffect(pagerState.currentPage) {
        if (searchQuery.isBlank()) {
            tabRowState.animateScrollToItem(
                index = (pagerState.currentPage - 1).coerceAtLeast(0)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.paper),
        contentAlignment = Alignment.TopCenter
    ) {
        val columns = if (maxWidth >= 600.dp) 2 else 1

        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxSize()
        ) {
            // Ultra-minimal Top Bar (Brand + Live Tool Count + Light/Dark Switch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = APP_NAME,
                        style = DotkitType.displayPixel(24),
                        color = palette.ink
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(palette.accentColor.copy(alpha = 0.14f))
                            .border(0.8.dp, palette.accentColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${ToolRegistry.ALL_TOOLS.size}",
                            style = DotkitType.monoLabel(10),
                            color = palette.accentColor
                        )
                    }
                }
                ThemeQuickToggleButton(
                    isDark = palette.isDark,
                    onToggle = onToggleLightDark
                )
            }

            // Minimal Search + Instant Solver Bar
            TopSearchAndSolverBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onOpenTool = onOpenTool,
                onToast = onToast,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(10.dp))

            if (searchQuery.isNotBlank()) {
                val searchScrollVel = rememberScrollVelocityConnection()
                val vel = searchScrollVel.collectSmoothedVelocity()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(searchScrollVel.nestedScrollConnection),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "RESULTS (${searchMatches.size})",
                            style = DotkitType.monoLabel(10),
                            color = palette.ink2
                        )
                    }
                    if (searchMatches.isEmpty()) {
                        item {
                            ToolStateBanner(
                                kind = ToolStateKind.EMPTY,
                                title = "No matching tools found",
                                detail = "Try keywords like ai, diff, totp, svg, pdf, tuner, fasting, emi, or math like sqrt(144) + sin(30).",
                                chapterHue = look.accentHue,
                                actionLabel = "Clear Search",
                                onAction = { onSearchQueryChange("") }
                            )
                        }
                    } else {
                        items(
                            items = searchMatches.chunked(columns),
                            key = { row -> "s_${row.joinToString("_") { it.id }}" }
                        ) { rowTools ->
                            ToolRowPair(
                                tools = rowTools,
                                columns = columns,
                                pinnedToolIds = pinnedToolIds,
                                onOpenTool = onOpenTool,
                                modifier = Modifier.scrollReactiveItem(vel)
                            )
                        }
                    }
                }
            } else {
                // Recent Tools Quick-Launch Pills
                if (recentTools.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        items(recentTools, key = { "recent_pill_${it.id}" }) { tool ->
                            RecentToolPill(
                                tool = tool,
                                onClick = { onOpenTool(tool.id, "") }
                            )
                        }
                    }
                }

                // Magnetic Spring Category Pill Strip synced to HorizontalPager
                LazyRow(
                    state = tabRowState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(HOME_TABS.size, key = { idx -> "htab_${HOME_TABS[idx].id}" }) { idx ->
                        val tab = HOME_TABS[idx]
                        val selected = pagerState.currentPage == idx
                        HomeCategoryPill(
                            label = tab.label,
                            selected = selected,
                            hue = tab.hue,
                            onClick = {
                                scope.launch {
                                    if (look.motionMode == MotionMode.OFF) {
                                        pagerState.scrollToPage(idx)
                                    } else {
                                        pagerState.animateScrollToPage(
                                            page = idx,
                                            animationSpec = spring(
                                                dampingRatio = 0.85f,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("home_tab_${tab.id}")
                        )
                    }
                }

                // Swipeable HorizontalPager feeds (120Hz zero-delay scrolling in both directions)
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    val tab = HOME_TABS[pageIndex]
                    val pageScrollVel = rememberScrollVelocityConnection()
                    val vel = pageScrollVel.collectSmoothedVelocity()

                    when (tab) {
                        HomePagerTab.All -> {
                            val categories = remember { ToolCategory.entries.filter { it != ToolCategory.ALL } }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(pageScrollVel.nestedScrollConnection),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (pinnedTools.isNotEmpty()) {
                                    item(key = "hdr_pinned") {
                                        SectionHeaderRow(title = "PINNED")
                                    }
                                    items(
                                        items = pinnedTools.chunked(columns),
                                        key = { row -> "pin_${row.joinToString("_") { it.id }}" }
                                    ) { rowTools ->
                                        ToolRowPair(
                                            tools = rowTools,
                                            columns = columns,
                                            pinnedToolIds = pinnedToolIds,
                                            onOpenTool = onOpenTool,
                                            modifier = Modifier.scrollReactiveItem(vel)
                                        )
                                    }
                                }

                                item(key = "hdr_suites") {
                                    SectionHeaderRow(
                                        title = "POWER SUITES & AI STUDIO",
                                        actionLabel = "All ${ToolCategory.entries.size - 1} →",
                                        onAction = onOpenToolsDirectory
                                    )
                                }
                                items(
                                    items = flagshipSuites.chunked(columns),
                                    key = { row -> "flag_${row.joinToString("_") { it.id }}" }
                                ) { rowTools ->
                                    ToolRowPair(
                                        tools = rowTools,
                                        columns = columns,
                                        pinnedToolIds = pinnedToolIds,
                                        onOpenTool = onOpenTool,
                                        modifier = Modifier.scrollReactiveItem(vel)
                                    )
                                }

                                item(key = "hdr_cats") {
                                    SectionHeaderRow(
                                        title = "CATEGORIES (${categories.size})",
                                        actionLabel = "Directory →",
                                        onAction = onOpenToolsDirectory
                                    )
                                }
                                items(
                                    items = categories.chunked(columns),
                                    key = { row -> "cat_${row.joinToString("_") { it.id }}" }
                                ) { rowCats ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .scrollReactiveItem(vel),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowCats.forEach { cat ->
                                            val count = remember(cat) { ToolRegistry.getToolsByCategory(cat).size }
                                            CategoryTile(
                                                category = cat,
                                                count = count,
                                                onClick = { onBrowseCategory(cat) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowCats.size < columns) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        HomePagerTab.Suites -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(pageScrollVel.nestedScrollConnection),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(key = "suites_intro") {
                                    SectionHeaderRow(
                                        title = "FLAGSHIP POWER SUITES",
                                        actionLabel = "Full Directory →",
                                        onAction = onOpenToolsDirectory
                                    )
                                }
                                items(
                                    items = flagshipSuites.chunked(columns),
                                    key = { row -> "suite_${row.joinToString("_") { it.id }}" }
                                ) { rowTools ->
                                    ToolRowPair(
                                        tools = rowTools,
                                        columns = columns,
                                        pinnedToolIds = pinnedToolIds,
                                        onOpenTool = onOpenTool,
                                        modifier = Modifier.scrollReactiveItem(vel)
                                    )
                                }
                            }
                        }

                        is HomePagerTab.Category -> {
                            val catTools = remember(tab.cat) { ToolRegistry.getToolsByCategory(tab.cat) }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(pageScrollVel.nestedScrollConnection),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item(key = "cat_hdr_${tab.cat.id}") {
                                    SectionHeaderRow(
                                        title = "${tab.cat.label.uppercase()} · ${catTools.size} TOOLS",
                                        actionLabel = "Open Grid →",
                                        onAction = { onBrowseCategory(tab.cat) }
                                    )
                                }
                                items(
                                    items = catTools.chunked(columns),
                                    key = { row -> "pg_${tab.cat.id}_${row.joinToString("_") { it.id }}" }
                                ) { rowTools ->
                                    ToolRowPair(
                                        tools = rowTools,
                                        columns = columns,
                                        pinnedToolIds = pinnedToolIds,
                                        onOpenTool = onOpenTool,
                                        modifier = Modifier.scrollReactiveItem(vel)
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

@Composable
private fun HomeCategoryPill(
    label: String,
    selected: Boolean,
    hue: ChapterHue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val accent = palette.chapterColor(hue)
    val interactionSource = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        targetValue = if (selected) accent else palette.paper2,
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 160),
        label = "hpill_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) hue.onColor else palette.ink2,
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 160),
        label = "hpill_fg"
    )
    val borderCol by animateColorAsState(
        targetValue = if (selected) accent else palette.hairline,
        animationSpec = tween(if (look.motionMode == MotionMode.OFF) 0 else 160),
        label = "hpill_border"
    )
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .springPressScale(interactionSource, 0.94f)
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(bg)
            .border(0.8.dp, borderCol, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = DotkitType.bodyUi(13, bold = selected),
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun RecentToolPill(
    tool: ToolMeta,
    onClick: () -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val accent = palette.chapterColor(tool.category.hue)
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .springPressScale(interactionSource, 0.94f)
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(palette.paper2)
            .border(0.8.dp, palette.hairline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .testTag("recent_card_${tool.id}")
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolBadgeIcon(
            meta = tool,
            accentColor = accent,
            sizeDp = 28.dp,
            iconSizeDp = 15.dp
        )
        Text(
            text = tool.name,
            style = DotkitType.bodyUi(12, bold = true),
            color = palette.ink,
            maxLines = 1
        )
    }
}

@Composable
fun ToolsDirectoryScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: ToolCategory?,
    onSelectCategory: (ToolCategory?) -> Unit,
    pinnedToolIds: List<String>,
    onOpenTool: (toolId: String, payload: String) -> Unit,
    onToggleLightDark: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette

    val viewMode = when {
        searchQuery.isNotBlank() -> "SEARCH"
        selectedCategory == null -> "CATEGORIES"
        else -> "CATEGORY_${selectedCategory.id}"
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.paper),
        contentAlignment = Alignment.TopCenter
    ) {
        val catColumns = if (maxWidth >= 680.dp) 3 else 2
        val toolColumns = if (maxWidth >= 600.dp) 2 else 1

        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedCategory != null && searchQuery.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val backInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .springPressScale(backInteraction, 0.92f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.paper2)
                                .border(0.8.dp, palette.hairline, RoundedCornerShape(12.dp))
                                .clickable(interactionSource = backInteraction, indication = androidx.compose.material3.ripple()) {
                                    onSelectCategory(null)
                                }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Categories",
                                tint = palette.ink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        ToolBadgeIcon(
                            icon = ToolIconResolver.categoryIcon(selectedCategory),
                            accentColor = palette.chapterColor(selectedCategory.hue),
                            sizeDp = 40.dp,
                            iconSizeDp = 21.dp
                        )
                        Column {
                            Text(
                                text = selectedCategory.label,
                                style = DotkitType.displayPixel(22),
                                color = palette.ink
                            )
                            val cnt = ToolRegistry.getToolsByCategory(selectedCategory).size
                            Text(
                                text = "$cnt tools ready",
                                style = DotkitType.bodyUi(12),
                                color = palette.ink2
                            )
                        }
                    }
                    ThemeQuickToggleButton(isDark = palette.isDark, onToggle = onToggleLightDark)
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Search Tools" else "Directory",
                            style = DotkitType.displayPixel(24),
                            color = palette.ink
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "Matching across all ${ToolRegistry.ALL_TOOLS.size} modules"
                            else "${ToolRegistry.ALL_TOOLS.size} tools in ${ToolCategory.entries.size - 1} categories",
                            style = DotkitType.bodyUi(12),
                            color = palette.ink2
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedCategory == null && searchQuery.isBlank()) {
                            PixelChip(
                                label = "All (${ToolRegistry.ALL_TOOLS.size})",
                                chapterHue = look.accentHue,
                                onClick = { onSelectCategory(ToolCategory.ALL) }
                            )
                        }
                        ThemeQuickToggleButton(isDark = palette.isDark, onToggle = onToggleLightDark)
                    }
                }
            }

            TopSearchAndSolverBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onOpenTool = onOpenTool,
                onToast = onToast
            )

            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    if (look.motionMode == MotionMode.OFF) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val enteringCat = targetState.startsWith("CATEGORY_") && initialState == "CATEGORIES"
                        if (enteringCat) {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 8 })
                                .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 12 })
                        } else {
                            (fadeIn(tween(170)) + slideInHorizontally(tween(170)) { -it / 10 })
                                .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { it / 10 })
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = "tools_category_switch"
            ) { mode ->
                val scrollVel = rememberScrollVelocityConnection()
                val vel = scrollVel.collectSmoothedVelocity()

                when {
                    mode == "CATEGORIES" -> {
                        val categories = remember { ToolCategory.entries.filter { it != ToolCategory.ALL } }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollVel.nestedScrollConnection),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 92.dp)
                        ) {
                            items(
                                items = categories.chunked(catColumns),
                                key = { row -> "catgrid_${row.joinToString("_") { it.id }}" }
                            ) { rowCats ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scrollReactiveItem(vel),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowCats.forEach { cat ->
                                        val count = remember(cat) { ToolRegistry.getToolsByCategory(cat).size }
                                        CategoryGridCard(
                                            category = cat,
                                            count = count,
                                            onClick = { onSelectCategory(cat) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("cat_card_${cat.id}")
                                        )
                                    }
                                    repeat(catColumns - rowCats.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    mode == "SEARCH" -> {
                        val matches = remember(searchQuery) { ToolRegistry.searchTools(searchQuery, ToolCategory.ALL) }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollVel.nestedScrollConnection),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 92.dp)
                        ) {
                            if (matches.isEmpty()) {
                                item {
                                    ToolStateBanner(
                                        kind = ToolStateKind.EMPTY,
                                        title = "No tools match \"$searchQuery\"",
                                        detail = "Try another keyword such as ai, diff, totp, tuner, c4, ruler, fasting, or invoice.",
                                        chapterHue = look.accentHue,
                                        actionLabel = "Clear Search",
                                        onAction = { onSearchQueryChange("") }
                                    )
                                }
                            } else {
                                items(
                                    items = matches.chunked(toolColumns),
                                    key = { row -> row.joinToString("_") { it.id } }
                                ) { rowTools ->
                                    ToolRowPair(
                                        tools = rowTools,
                                        columns = toolColumns,
                                        pinnedToolIds = pinnedToolIds,
                                        onOpenTool = onOpenTool,
                                        modifier = Modifier.scrollReactiveItem(vel)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        val cat = selectedCategory ?: ToolCategory.ALL
                        val catTools = remember(cat) { ToolRegistry.getToolsByCategory(cat) }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollVel.nestedScrollConnection),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 92.dp)
                        ) {
                            items(
                                items = catTools.chunked(toolColumns),
                                key = { row -> row.joinToString("_") { it.id } }
                            ) { rowTools ->
                                ToolRowPair(
                                    tools = rowTools,
                                    columns = toolColumns,
                                    pinnedToolIds = pinnedToolIds,
                                    onOpenTool = onOpenTool,
                                    modifier = Modifier.scrollReactiveItem(vel)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridCard(
    category: ToolCategory,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    val col = palette.chapterColor(category.hue)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .springPressScale(interactionSource, 0.96f)
            .minimumInteractiveComponentSize()
            .clip(SwissCardShape)
            .background(palette.paper2)
            .border(0.8.dp, palette.hairline, SwissCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolBadgeIcon(
                icon = ToolIconResolver.categoryIcon(category),
                accentColor = col,
                contentDescription = category.label,
                sizeDp = 40.dp,
                iconSizeDp = 21.dp
            )
            if (category.isFlagship) {
                PixelStamp("SUITE", col)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.ink3,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = category.label,
                style = DotkitType.editorialHeading(15),
                color = palette.ink,
                maxLines = 1
            )
            Text(
                text = "$count tools",
                style = DotkitType.monoLabel(10),
                color = col
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val palette = LocalDotkitLook.current.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = DotkitType.monoLabel(10), color = palette.ink2)
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = DotkitType.bodyUi(12, bold = true),
                color = palette.accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun CategoryTile(
    category: ToolCategory,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    val col = palette.chapterColor(category.hue)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .springPressScale(interactionSource, 0.97f)
            .minimumInteractiveComponentSize()
            .clip(SwissCardShape)
            .background(palette.paper2)
            .border(0.8.dp, palette.hairline, SwissCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            ToolBadgeIcon(
                icon = ToolIconResolver.categoryIcon(category),
                accentColor = col,
                sizeDp = 38.dp,
                iconSizeDp = 20.dp
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(category.label, style = DotkitType.bodyUi(15, bold = true), color = palette.ink)
                    if (category.isFlagship) {
                        PixelStamp("SUITE", col)
                    }
                }
                Text("$count tools", style = DotkitType.monoLabel(10), color = palette.ink2)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = col,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ToolRowPair(
    tools: List<ToolMeta>,
    columns: Int,
    pinnedToolIds: List<String>,
    onOpenTool: (toolId: String, payload: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tools.forEach { tool ->
            MinimalToolCard(
                tool = tool,
                isPinned = tool.id in pinnedToolIds,
                onClick = { onOpenTool(tool.id, "") },
                modifier = Modifier.weight(1f)
            )
        }
        if (tools.size < columns) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MinimalToolCard(
    tool: ToolMeta,
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDotkitLook.current.palette
    val accent = palette.chapterColor(tool.category.hue)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .springPressScale(interactionSource, 0.97f)
            .minimumInteractiveComponentSize()
            .clip(SwissCardShape)
            .background(palette.paper2)
            .border(0.8.dp, palette.hairline, SwissCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .testTag("tool_card_${tool.id}")
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolBadgeIcon(
            meta = tool,
            accentColor = accent,
            sizeDp = 42.dp,
            iconSizeDp = 21.dp
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tool.name,
                    style = DotkitType.editorialHeading(15),
                    color = palette.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Pinned",
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(
                text = tool.tagline,
                style = DotkitType.bodyUi(12),
                color = palette.ink2,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.ink3,
            modifier = Modifier.size(18.dp)
        )
    }
}
