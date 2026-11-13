package com.example.app

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.design.StaggeredEntrance
import com.example.design.SwissCardShape
import com.example.design.ToolBadgeIcon
import com.example.design.ToolCategory
import com.example.design.ToolIconResolver
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.design.springPressScale
import com.example.lib.command.CommandEngine
import com.example.registry.ToolMeta
import com.example.registry.ToolRegistry
import com.example.tools.common.copyToClipboard

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
                .border(1.dp, palette.hairline.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = palette.accentColor,
                modifier = Modifier.size(20.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                PixelTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search ${ToolRegistry.ALL_TOOLS.size} tools, keywords (pdf, tuner, fast, c4)…",
                    chapterHue = look.accentHue,
                    minHeight = 44.dp,
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
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val look = LocalDotkitLook.current
    val palette = look.palette

    val recentTools = remember(recentToolIds) {
        recentToolIds.mapNotNull { ToolRegistry.getToolById(it) }
    }
    val pinnedTools = remember(pinnedToolIds) {
        pinnedToolIds.mapNotNull { ToolRegistry.getToolById(it) }
    }
    val flagshipSuites = remember {
        listOfNotNull(
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.paper),
        contentAlignment = Alignment.TopCenter
    ) {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 940.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    palette.accentColor.copy(alpha = 0.12f),
                                    palette.paper2
                                )
                            )
                        )
                        .border(1.dp, palette.accentColor.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = APP_NAME,
                        style = DotkitType.displayPixel(28),
                        color = palette.ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${ToolRegistry.ALL_TOOLS.size} active utilities across ${ToolCategory.entries.size - 1} categories",
                        style = DotkitType.bodyUi(13),
                        color = palette.ink2
                    )
                }
            }

            item {
                TopSearchAndSolverBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onOpenTool = onOpenTool,
                    onToast = onToast
                )
            }

            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "SEARCH RESULTS (${searchMatches.size})",
                        style = DotkitType.monoLabel(10),
                        color = palette.ink2
                    )
                }
                if (searchMatches.isEmpty()) {
                    item {
                        ToolStateBanner(
                            kind = ToolStateKind.EMPTY,
                            title = "No matching tools found",
                            detail = "Try keywords like seal, shttps, pdf, tuner, c4, fasting, emi, torch, or math like sqrt(144) + sin(30).",
                            chapterHue = look.accentHue,
                            actionLabel = "Clear Search",
                            onAction = { onSearchQueryChange("") }
                        )
                    }
                } else {
                    itemsIndexed(searchMatches.chunked(columns)) { idx, rowTools ->
                        StaggeredEntrance(idx) {
                            ToolRowPair(rowTools, columns, pinnedToolIds, onOpenTool)
                        }
                    }
                }
            } else {
                item {
                    SectionHeaderRow(
                        title = "RECENT TOOLS",
                        actionLabel = "Categories →",
                        onAction = onOpenToolsDirectory
                    )
                }
                if (recentTools.isEmpty()) {
                    item {
                        PixelBox(backgroundColor = palette.paper2) {
                            Text(
                                text = "Tools you open appear here automatically for quick 1-tap relaunch.",
                                style = DotkitType.bodyUi(13),
                                color = palette.ink2
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentTools, key = { "recent_${it.id}" }) { tool ->
                                RecentToolTile(
                                    tool = tool,
                                    onClick = { onOpenTool(tool.id, "") }
                                )
                            }
                        }
                    }
                }

                if (pinnedTools.isNotEmpty()) {
                    item {
                        SectionHeaderRow(title = "PINNED FAVORITES")
                    }
                    itemsIndexed(
                        pinnedTools.chunked(columns),
                        key = { _, row -> "pin_${row.joinToString { it.id }}" }
                    ) { idx, rowTools ->
                        StaggeredEntrance(idx) {
                            ToolRowPair(rowTools, columns, pinnedToolIds, onOpenTool)
                        }
                    }
                }

                item {
                    SectionHeaderRow(
                        title = "FLAGSHIP SUITES",
                        actionLabel = "Image & PDF →",
                        onAction = { onBrowseCategory(ToolCategory.IMAGE) }
                    )
                }
                itemsIndexed(
                    flagshipSuites.chunked(columns),
                    key = { _, row -> "flag_${row.joinToString { it.id }}" }
                ) { idx, rowTools ->
                    StaggeredEntrance(idx) {
                        ToolRowPair(rowTools, columns, pinnedToolIds, onOpenTool)
                    }
                }

                item {
                    SectionHeaderRow(
                        title = "BROWSE CATEGORIES (${ToolCategory.entries.size - 1})",
                        actionLabel = "Open Grid →",
                        onAction = onOpenToolsDirectory
                    )
                }
                val categories = ToolCategory.entries.filter { it != ToolCategory.ALL }
                itemsIndexed(
                    categories.chunked(columns),
                    key = { _, row -> "cat_${row.joinToString { it.id }}" }
                ) { idx, rowCats ->
                    StaggeredEntrance(idx) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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

            item { Spacer(Modifier.height(16.dp)) }
        }
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
                .widthIn(max = 940.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dynamic Header
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
                                .border(1.dp, palette.hairline, RoundedCornerShape(12.dp))
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
                            sizeDp = 42.dp,
                            iconSizeDp = 22.dp
                        )
                        Column {
                            Text(
                                text = selectedCategory.label,
                                style = DotkitType.displayPixel(24),
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
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Search Tools" else "Categories",
                            style = DotkitType.displayPixel(28),
                            color = palette.ink
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "Matching across all modules"
                            else "Choose a category to open its tools",
                            style = DotkitType.bodyUi(13),
                            color = palette.ink2
                        )
                    }
                    if (selectedCategory == null && searchQuery.isBlank()) {
                        PixelChip(
                            label = "All (${ToolRegistry.ALL_TOOLS.size})",
                            chapterHue = look.accentHue,
                            onClick = { onSelectCategory(ToolCategory.ALL) }
                        )
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
                            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 6 })
                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 10 })
                        } else {
                            (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 8 })
                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { it / 8 })
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = "tools_category_switch"
            ) { mode ->
                when {
                    mode == "CATEGORIES" -> {
                        val categories = remember { ToolCategory.entries.filter { it != ToolCategory.ALL } }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 18.dp)
                        ) {
                            itemsIndexed(
                                categories.chunked(catColumns),
                                key = { _, row -> "catgrid_${row.joinToString { it.id }}" }
                            ) { idx, rowCats ->
                                StaggeredEntrance(idx) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
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
                    }

                    mode == "SEARCH" -> {
                        val matches = remember(searchQuery) { ToolRegistry.searchTools(searchQuery, ToolCategory.ALL) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 18.dp)
                        ) {
                            if (matches.isEmpty()) {
                                item {
                                    ToolStateBanner(
                                        kind = ToolStateKind.EMPTY,
                                        title = "No tools match \"$searchQuery\"",
                                        detail = "Try another keyword such as tuner, c4, ruler, fasting, or invoice.",
                                        chapterHue = look.accentHue,
                                        actionLabel = "Clear Search",
                                        onAction = { onSearchQueryChange("") }
                                    )
                                }
                            } else {
                                itemsIndexed(matches.chunked(toolColumns), key = { _, row -> row.joinToString { it.id } }) { idx, rowTools ->
                                    StaggeredEntrance(idx) {
                                        ToolRowPair(rowTools, toolColumns, pinnedToolIds, onOpenTool)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val cat = selectedCategory ?: ToolCategory.ALL
                        val catTools = remember(cat) { ToolRegistry.getToolsByCategory(cat) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 18.dp)
                        ) {
                            itemsIndexed(catTools.chunked(toolColumns), key = { _, row -> row.joinToString { it.id } }) { idx, rowTools ->
                                StaggeredEntrance(idx) {
                                    ToolRowPair(rowTools, toolColumns, pinnedToolIds, onOpenTool)
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
            .border(1.dp, col.copy(alpha = 0.25f), SwissCardShape)
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
                sizeDp = 42.dp,
                iconSizeDp = 22.dp
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
                style = DotkitType.editorialHeading(16),
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
        modifier = Modifier.fillMaxWidth(),
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
private fun RecentToolTile(
    tool: ToolMeta,
    onClick: () -> Unit
) {
    val palette = LocalDotkitLook.current.palette
    val catColor = palette.chapterColor(tool.category.hue)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(172.dp)
            .springPressScale(interactionSource, 0.96f)
            .minimumInteractiveComponentSize()
            .clip(SwissCardShape)
            .background(palette.paper2)
            .border(1.dp, palette.hairline.copy(alpha = 0.7f), SwissCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .testTag("recent_card_${tool.id}")
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolBadgeIcon(meta = tool, accentColor = catColor, sizeDp = 36.dp, iconSizeDp = 19.dp)
            PixelStamp(tool.category.label.uppercase(), catColor)
        }
        Text(
            text = tool.name,
            style = DotkitType.bodyUi(14, bold = true),
            color = palette.ink,
            maxLines = 1
        )
        Text(
            text = tool.tagline,
            style = DotkitType.bodyUi(11),
            color = palette.ink2,
            maxLines = 2
        )
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
            .border(1.dp, palette.hairline.copy(alpha = 0.65f), SwissCardShape)
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
    onOpenTool: (toolId: String, payload: String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            .border(1.dp, palette.hairline.copy(alpha = 0.65f), SwissCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .testTag("tool_card_${tool.id}")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolBadgeIcon(
            meta = tool,
            accentColor = accent,
            sizeDp = 44.dp,
            iconSizeDp = 22.dp
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tool.name,
                    style = DotkitType.editorialHeading(16),
                    color = palette.ink,
                    modifier = Modifier.weight(1f)
                )
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "Pinned",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = tool.tagline,
                style = DotkitType.bodyUi(12),
                color = palette.ink2,
                maxLines = 2
            )
        }
    }
}
